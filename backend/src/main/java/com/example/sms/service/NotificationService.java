package com.example.sms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.sms.common.BusinessException;
import com.example.sms.dto.SendNotificationDTO;
import com.example.sms.entity.Course;
import com.example.sms.entity.Notification;
import com.example.sms.entity.NotificationReceiver;
import com.example.sms.entity.Student;
import com.example.sms.entity.StudentCourse;
import com.example.sms.mapper.CourseMapper;
import com.example.sms.mapper.NotificationMapper;
import com.example.sms.mapper.NotificationReceiverMapper;
import com.example.sms.mapper.StudentCourseMapper;
import com.example.sms.mapper.StudentMapper;
import com.example.sms.util.UserContext;
import com.example.sms.vo.NotificationVO;
import com.example.sms.websocket.WsSessionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 站内通知服务：发送（手动/系统）、收件箱、未读数、已读、发件箱
 */
@Slf4j
@Service
public class NotificationService {

    @Autowired
    private NotificationMapper notificationMapper;

    @Autowired
    private NotificationReceiverMapper receiverMapper;

    @Autowired
    private StudentMapper studentMapper;

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private StudentCourseMapper studentCourseMapper;

    @Autowired
    private WsSessionRegistry wsSessionRegistry;

    @Autowired
    private ObjectMapper objectMapper;

    // ===== 发送 =====

    /** 手动发送（发送者取自 UserContext，老师仅可发给自己的课程学生） */
    @Transactional
    public void send(SendNotificationDTO dto) {
        List<Long> studentIds = resolveTargets(dto.getTarget());
        if (studentIds.isEmpty()) {
            throw new BusinessException("未匹配到任何学生");
        }
        UserContext.CurrentUser user = UserContext.get();
        doSend("MANUAL", dto.getTitle().trim(), dto.getContent().trim(),
                user.getRoleType(), user.getUserId(), user.getRealName(), studentIds);
    }

    /** 系统自动触发（成绩发布/调课/选课成功）；接收人为空时静默跳过 */
    @Transactional
    public void sendSystem(String type, String title, String content, List<Long> studentIds) {
        if (studentIds == null || studentIds.isEmpty()) return;
        doSend(type, title, content, "SYSTEM", null, "系统", studentIds);
    }

    private void doSend(String type, String title, String content, String senderType,
                        Long senderId, String senderName, List<Long> studentIds) {
        Notification n = new Notification();
        n.setType(type);
        n.setTitle(title);
        n.setContent(content);
        n.setSenderType(senderType);
        n.setSenderId(senderId);
        n.setSenderName(senderName);
        notificationMapper.insert(n);

        List<Long> distinctIds = studentIds.stream().distinct().collect(Collectors.toList());
        for (Long sid : distinctIds) {
            NotificationReceiver r = new NotificationReceiver();
            r.setNotificationId(n.getId());
            r.setStudentId(sid);
            r.setRead(false);
            receiverMapper.insert(r);
        }

        // 事务提交后推送（保证落库可见后再通知前端）
        Runnable push = () -> pushToOnline(n, distinctIds);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    push.run();
                }
            });
        } else {
            push.run();
        }
    }

    private void pushToOnline(Notification n, List<Long> studentIds) {
        try {
            Map<Long, Long> sid2Rid = receiverMapper.selectList(new LambdaQueryWrapper<NotificationReceiver>()
                            .eq(NotificationReceiver::getNotificationId, n.getId()))
                    .stream().collect(Collectors.toMap(NotificationReceiver::getStudentId, NotificationReceiver::getId));
            for (Long sid : studentIds) {
                Long receiverId = sid2Rid.get(sid);
                if (receiverId == null) continue;
                String json = objectMapper.writeValueAsString(Map.of(
                        "type", "NOTIFICATION",
                        "data", Map.of(
                                "receiverId", receiverId,
                                "notificationId", n.getId(),
                                "title", n.getTitle(),
                                "content", n.getContent(),
                                "type", n.getType(),
                                "createdAt", n.getCreatedAt() == null ? LocalDateTime.now().toString() : n.getCreatedAt().toString())));
                wsSessionRegistry.sendToUser(sid, json);
            }
        } catch (Exception e) {
            log.warn("通知推送失败（消息已落库）：{}", e.getMessage());
        }
    }

    // ===== 接收人解析 =====

    private List<Long> resolveTargets(SendNotificationDTO.Target target) {
        String kind = target.getKind();
        switch (kind) {
            case "STUDENT_IDS":
                if (target.getStudentIds() == null || target.getStudentIds().isEmpty()) {
                    throw new BusinessException("请选择接收学生");
                }
                return target.getStudentIds();
            case "CLASS":
                return studentMapper.selectList(new LambdaQueryWrapper<Student>()
                                .eq(Student::getClassName, target.getClassName()))
                        .stream().map(Student::getId).collect(Collectors.toList());
            case "MAJOR":
                return studentMapper.selectList(new LambdaQueryWrapper<Student>()
                                .eq(Student::getMajor, target.getMajor()))
                        .stream().map(Student::getId).collect(Collectors.toList());
            case "DEPARTMENT":
                return studentMapper.selectList(new LambdaQueryWrapper<Student>()
                                .eq(Student::getDepartment, target.getDepartment()))
                        .stream().map(Student::getId).collect(Collectors.toList());
            case "COURSE": {
                if (target.getCourseId() == null) throw new BusinessException("请选择课程");
                Course course = courseMapper.selectById(target.getCourseId());
                if (course == null) throw new BusinessException("课程不存在");
                if ("TEACHER".equals(UserContext.getRole())
                        && !Objects.equals(course.getTeacherId(), UserContext.getUserId())) {
                    throw new BusinessException(403, "只能向自己授课课程的学生发送通知");
                }
                return studentCourseMapper.selectList(new LambdaQueryWrapper<StudentCourse>()
                                .eq(StudentCourse::getCourseId, target.getCourseId()))
                        .stream().map(StudentCourse::getStudentId).collect(Collectors.toList());
            }
            case "ALL":
                return studentMapper.selectList(new LambdaQueryWrapper<Student>()
                                .eq(Student::getStatus, "ENABLED"))
                        .stream().map(Student::getId).collect(Collectors.toList());
            default:
                throw new BusinessException("不支持的接收方式：" + kind);
        }
    }

    // ===== 学生收件箱 =====

    public Page<NotificationVO> inbox(int page, int size) {
        Long userId = UserContext.getUserId();
        Page<NotificationReceiver> p = receiverMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<NotificationReceiver>()
                        .eq(NotificationReceiver::getStudentId, userId)
                        .orderByDesc(NotificationReceiver::getId));
        return toVoPage(p);
    }

    public long unreadCount() {
        return receiverMapper.selectCount(new LambdaQueryWrapper<NotificationReceiver>()
                .eq(NotificationReceiver::getStudentId, UserContext.getUserId())
                .eq(NotificationReceiver::getRead, false));
    }

    public void markRead(Long receiverId) {
        Long userId = UserContext.getUserId();
        NotificationReceiver r = receiverMapper.selectById(receiverId);
        if (r == null || !Objects.equals(r.getStudentId(), userId)) {
            throw new BusinessException(403, "无权操作该通知");
        }
        if (!Boolean.TRUE.equals(r.getRead())) {
            r.setRead(true);
            r.setReadAt(LocalDateTime.now());
            receiverMapper.updateById(r);
        }
    }

    public void readAll() {
        receiverMapper.update(null, new LambdaUpdateWrapper<NotificationReceiver>()
                .eq(NotificationReceiver::getStudentId, UserContext.getUserId())
                .eq(NotificationReceiver::getRead, false)
                .set(NotificationReceiver::getRead, true)
                .set(NotificationReceiver::getReadAt, LocalDateTime.now()));
    }

    // ===== 老师/管理员发件箱 =====

    public Page<NotificationVO> sent(int page, int size) {
        Long userId = UserContext.getUserId();
        Page<Notification> p = notificationMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getSenderId, userId)
                        .orderByDesc(Notification::getId));
        Page<NotificationVO> vo = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        vo.setRecords(p.getRecords().stream().map(n -> {
            NotificationVO v = new NotificationVO();
            v.setId(n.getId());
            v.setType(n.getType());
            v.setTitle(n.getTitle());
            v.setContent(n.getContent());
            v.setSenderName(n.getSenderName());
            v.setCreatedAt(n.getCreatedAt());
            return v;
        }).collect(Collectors.toList()));
        return vo;
    }

    private Page<NotificationVO> toVoPage(Page<NotificationReceiver> p) {
        Page<NotificationVO> vo = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        if (p.getRecords().isEmpty()) {
            vo.setRecords(List.of());
            return vo;
        }
        List<Long> nids = p.getRecords().stream()
                .map(NotificationReceiver::getNotificationId).distinct().collect(Collectors.toList());
        Map<Long, Notification> nMap = notificationMapper.selectBatchIds(nids).stream()
                .collect(Collectors.toMap(Notification::getId, Function.identity()));
        vo.setRecords(p.getRecords().stream().map(r -> {
            NotificationVO v = new NotificationVO();
            Notification n = nMap.get(r.getNotificationId());
            if (n == null) return null;
            v.setId(n.getId());
            v.setReceiverId(r.getId());
            v.setType(n.getType());
            v.setTitle(n.getTitle());
            v.setContent(n.getContent());
            v.setSenderName(n.getSenderName());
            v.setRead(r.getRead());
            v.setCreatedAt(n.getCreatedAt());
            return v;
        }).filter(Objects::nonNull).collect(Collectors.toList()));
        return vo;
    }
}
