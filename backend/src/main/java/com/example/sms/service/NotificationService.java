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
        UserContext.CurrentUser user = UserContext.get();
        // 权限边界：教师只能按课程发送且必须为自己的课程（防止绕过前端隐藏选项，直接调用接口用 STUDENT_IDS/ALL 等发给任意学生）
        if ("TEACHER".equals(user.getRoleType())
                && !"COURSE".equals(dto.getTarget().getKind())) {
            throw new BusinessException(403, "教师只能向自己授课课程的学生发送通知");
        }
        List<Long> studentIds = resolveTargets(dto.getTarget());
        if (studentIds.isEmpty()) {
            throw new BusinessException("未匹配到任何学生");
        }
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
        // 1) 先写通知主表：插入后拿到自增 id，作为接收明细的外键
        Notification n = new Notification();
        n.setType(type);
        n.setTitle(title);
        n.setContent(content);
        n.setSenderType(senderType);
        n.setSenderId(senderId);
        n.setSenderName(senderName);
        notificationMapper.insert(n);

        // 2) 写接收明细（一人一条）：先 distinct 去重，避免同一学生命中多种接收方式（如既在班级又在课程）产生重复通知；
        //    批量 INSERT（F-3）：一次拼接多行 VALUES 替代逐条 insert，群发数百人时避免 N+1 写放大
        List<Long> distinctIds = studentIds.stream().distinct().collect(Collectors.toList());
        List<NotificationReceiver> receivers = distinctIds.stream().map(sid -> {
            NotificationReceiver r = new NotificationReceiver();
            r.setNotificationId(n.getId());
            r.setStudentId(sid);
            r.setIsRead(false);
            return r;
        }).collect(Collectors.toList());
        receiverMapper.batchInsert(receivers);

        // 事务提交后推送（保证落库可见后再通知前端）
        // 为什么必须提交后再推：若在事务内推送，接收方此刻查询收件箱尚看不到记录，会产生"收到推送但列表为空"的窗口期；
        // 且 WebSocket 推送属于网络 IO，失败不应回滚已落库的通知——消息可靠性以落库为准，推送只是"实时加速"手段
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
            // 推送前重新查接收明细，拿到每条接收记录的 id（receiverId）下发给前端——前端"标记已读"需要该 id 定位记录
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
            // 推送是"尽力而为"（best-effort）：任何异常只记日志不向上抛，避免把推送失败扩散成发送接口报错；
            // 消息已落库，学生下次刷新/进入收件箱即可拉到，实时性丢失可接受
            log.warn("通知推送失败（消息已落库）：{}", e.getMessage());
        }
    }

    // ===== 接收人解析 =====

    private List<Long> resolveTargets(SendNotificationDTO.Target target) {
        String kind = target.getKind();
        switch (kind) {
            case "STUDENT_IDS":
                // 按学生 id 白名单直接发送（仅管理员可用，教师入口已被 send() 拦截）
                if (target.getStudentIds() == null || target.getStudentIds().isEmpty()) {
                    throw new BusinessException("请选择接收学生");
                }
                return target.getStudentIds();
            case "CLASS":
                // 按班级名精确匹配：查出该班全部学生再取 id
                return studentMapper.selectList(new LambdaQueryWrapper<Student>()
                                .eq(Student::getClassName, target.getClassName()))
                        .stream().map(Student::getId).collect(Collectors.toList());
            case "MAJOR":
                // 按专业匹配：一个专业下可能跨多个班/多个年级
                return studentMapper.selectList(new LambdaQueryWrapper<Student>()
                                .eq(Student::getMajor, target.getMajor()))
                        .stream().map(Student::getId).collect(Collectors.toList());
            case "DEPARTMENT":
                // 按院系匹配：粒度最粗，适合院级通知
                return studentMapper.selectList(new LambdaQueryWrapper<Student>()
                                .eq(Student::getDepartment, target.getDepartment()))
                        .stream().map(Student::getId).collect(Collectors.toList());
            case "COURSE": {
                // 按课程发送：通过选课关系表 student_course 找到选修该课的学生
                if (target.getCourseId() == null) throw new BusinessException("请选择课程");
                Course course = courseMapper.selectById(target.getCourseId());
                if (course == null) throw new BusinessException("课程不存在");
                // 教师越权校验：即使前端/调用方伪造 courseId，也要确认课程归属自己（fail-closed，防止给他人课程学生群发）
                if ("TEACHER".equals(UserContext.getRole())
                        && !Objects.equals(course.getTeacherId(), UserContext.getUserId())) {
                    throw new BusinessException(403, "只能向自己授课课程的学生发送通知");
                }
                return studentCourseMapper.selectList(new LambdaQueryWrapper<StudentCourse>()
                                .eq(StudentCourse::getCourseId, target.getCourseId()))
                        .stream().map(StudentCourse::getStudentId).collect(Collectors.toList());
            }
            case "ALL":
                // 全校广播：只发给正常状态（ENABLED）学生，停用/毕业学生不接收，避免打扰无效账号
                return studentMapper.selectList(new LambdaQueryWrapper<Student>()
                                .eq(Student::getStatus, "ENABLED"))
                        .stream().map(Student::getId).collect(Collectors.toList());
            default:
                // fail-closed：未列出的 kind 一律拒绝，防止未来新增类型时静默漏发
                throw new BusinessException("不支持的接收方式：" + kind);
        }
    }

    // ===== 学生收件箱 =====

    public Page<NotificationVO> inbox(int page, int size) {
        // 收件箱查的是"接收明细"表：当前登录学生（userId）作为 studentId 过滤，天然只有自己的通知，无需额外鉴权
        Long userId = UserContext.getUserId();
        Page<NotificationReceiver> p = receiverMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<NotificationReceiver>()
                        .eq(NotificationReceiver::getStudentId, userId)
                        .orderByDesc(NotificationReceiver::getId));
        return toVoPage(p);
    }

    public long unreadCount() {
        // 未读数只统计 is_read = false 的记录，供前端角标/红点展示
        return receiverMapper.selectCount(new LambdaQueryWrapper<NotificationReceiver>()
                .eq(NotificationReceiver::getStudentId, UserContext.getUserId())
                .eq(NotificationReceiver::getIsRead, false));
    }

    public void markRead(Long receiverId) {
        Long userId = UserContext.getUserId();
        // 属主校验：接收记录必须属于当前登录学生，否则 403——防止越权把别人的通知标记为已读（接收记录 id 可被猜测/遍历）
        NotificationReceiver r = receiverMapper.selectById(receiverId);
        if (r == null || !Objects.equals(r.getStudentId(), userId)) {
            throw new BusinessException(403, "无权操作该通知");
        }
        // 幂等：已读则直接跳过，不重复更新 readAt，避免无谓的写库
        if (!Boolean.TRUE.equals(r.getIsRead())) {
            r.setIsRead(true);
            r.setReadAt(LocalDateTime.now());
            receiverMapper.updateById(r);
        }
    }

    public void readAll() {
        // 一条 UPDATE 批量置已读：只更新当前学生的未读记录，条件里带 studentId 即完成了属主隔离
        receiverMapper.update(null, new LambdaUpdateWrapper<NotificationReceiver>()
                .eq(NotificationReceiver::getStudentId, UserContext.getUserId())
                .eq(NotificationReceiver::getIsRead, false)
                .set(NotificationReceiver::getIsRead, true)
                .set(NotificationReceiver::getReadAt, LocalDateTime.now()));
    }

    // ===== 老师/管理员发件箱 =====

    public Page<NotificationVO> sent(int page, int size) {
        // 发件箱查的是"通知主表"：按 sender_id 过滤当前登录用户，只能看到自己发出的通知
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
        // 批量查出本页涉及的通知主表记录，按 id 建 Map——避免每条明细都查一次主表（N+1 查询问题）
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
            // 是否已读取"接收明细"的字段，而不是主表（主表不存已读状态）
            v.setRead(r.getIsRead());
            v.setCreatedAt(n.getCreatedAt());
            return v;
        }).filter(Objects::nonNull).collect(Collectors.toList()));
        // 过滤 null：极端情况下主表记录被删除时，避免把空 VO 传给前端
        return vo;
    }
}
