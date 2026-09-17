package com.example.sms.service;

import com.example.sms.common.BusinessException;
import com.example.sms.dto.SendNotificationDTO;
import com.example.sms.entity.Course;
import com.example.sms.entity.NotificationReceiver;
import com.example.sms.entity.Student;
import com.example.sms.mapper.CourseMapper;
import com.example.sms.mapper.NotificationMapper;
import com.example.sms.mapper.NotificationReceiverMapper;
import com.example.sms.mapper.StudentCourseMapper;
import com.example.sms.mapper.StudentMapper;
import com.example.sms.util.UserContext;
import com.example.sms.websocket.WsSessionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationMapper notificationMapper;
    @Mock
    private NotificationReceiverMapper receiverMapper;
    @Mock
    private StudentMapper studentMapper;
    @Mock
    private CourseMapper courseMapper;
    @Mock
    private StudentCourseMapper studentCourseMapper;
    @Mock
    private WsSessionRegistry wsSessionRegistry;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        teacherContext();
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private void teacherContext() {
        UserContext.CurrentUser u = new UserContext.CurrentUser();
        u.setUserId(1L);
        u.setUserNo("T001");
        u.setRealName("张老师");
        u.setRoleType("TEACHER");
        UserContext.set(u);
    }

    private void adminContext() {
        UserContext.CurrentUser u = new UserContext.CurrentUser();
        u.setUserId(2L);
        u.setUserNo("admin");
        u.setRealName("系统管理员");
        u.setRoleType("ADMIN");
        UserContext.set(u);
    }

    private SendNotificationDTO dto(String kind) {
        SendNotificationDTO d = new SendNotificationDTO();
        d.setTitle("测试");
        d.setContent("内容");
        SendNotificationDTO.Target t = new SendNotificationDTO.Target();
        t.setKind(kind);
        d.setTarget(t);
        return d;
    }

    // ===== 权限边界 =====

    /** 老师向非本人课程发送（COURSE）→ 403 */
    @Test
    void teacherSendingOthersCourseRejected() {
        Course course = new Course();
        course.setId(10L);
        course.setTeacherId(99L);
        when(courseMapper.selectById(10L)).thenReturn(course);

        SendNotificationDTO dto = dto("COURSE");
        dto.getTarget().setCourseId(10L);
        assertThrows(BusinessException.class, () -> notificationService.send(dto));
    }

    /** 老师用 ALL 方式发送 → 403（只能按课程，防绕过） */
    @Test
    void teacherSendingAllRejected() {
        assertThrows(BusinessException.class, () -> notificationService.send(dto("ALL")));
    }

    /** 老师用 STUDENT_IDS 方式发送 → 403（只能按课程，防绕过） */
    @Test
    void teacherSendingStudentIdsRejected() {
        SendNotificationDTO dto = dto("STUDENT_IDS");
        dto.getTarget().setStudentIds(List.of(101L));
        assertThrows(BusinessException.class, () -> notificationService.send(dto));
    }

    /** 老师按自己课程发送（COURSE）→ 允许 */
    @Test
    void teacherSendingOwnCourseAllowed() throws Exception {
        Course course = new Course();
        course.setId(10L);
        course.setTeacherId(1L);
        when(courseMapper.selectById(10L)).thenReturn(course);
        when(studentCourseMapper.selectList(any())).thenReturn(List.of(studentCourse(10L, 101L)));
        when(notificationMapper.insert(any())).thenReturn(1);
        when(receiverMapper.batchInsert(any())).thenReturn(1);
        lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        SendNotificationDTO dto = dto("COURSE");
        dto.getTarget().setCourseId(10L);
        notificationService.send(dto);
        verify(notificationMapper).insert(any());
    }

    // ===== 接收人解析（管理员） =====

    /** 管理员 ALL：全部启用学生 */
    @Test
    void adminSendToAllEnabledStudents() throws Exception {
        adminContext();
        Student s1 = new Student();
        s1.setId(101L);
        Student s2 = new Student();
        s2.setId(102L);
        when(studentMapper.selectList(any())).thenReturn(List.of(s1, s2));
        when(notificationMapper.insert(any())).thenReturn(1);
        when(receiverMapper.batchInsert(any())).thenReturn(1);
        lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        notificationService.send(dto("ALL"));
        verify(notificationMapper).insert(any());
        verify(receiverMapper).batchInsert(any());
    }

    /** 管理员按班级：解析该班级学生 */
    @Test
    void adminSendByClass() throws Exception {
        adminContext();
        Student s = new Student();
        s.setId(201L);
        when(studentMapper.selectList(any())).thenReturn(List.of(s));
        when(notificationMapper.insert(any())).thenReturn(1);
        when(receiverMapper.batchInsert(any())).thenReturn(1);
        lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        SendNotificationDTO dto = dto("CLASS");
        dto.getTarget().setClassName("软工2301");
        notificationService.send(dto);
        verify(receiverMapper).batchInsert(any());
    }

    /** 精确选人但列表为空 → 报错 */
    @Test
    void studentIdsEmptyRejected() {
        adminContext();
        SendNotificationDTO dto = dto("STUDENT_IDS");
        dto.getTarget().setStudentIds(List.of());
        assertThrows(BusinessException.class, () -> notificationService.send(dto));
    }

    // ===== 系统自动通知 =====

    /** 系统通知接收人为空 → 静默跳过，不落库 */
    @Test
    void sendSystemEmptyReceiversSkipped() {
        notificationService.sendSystem("ENROLL", "选课成功", "内容", List.of());
        verify(notificationMapper, never()).insert(any());
        verify(receiverMapper, never()).batchInsert(any());
    }

    /** 系统通知正常发送并落库 */
    @Test
    void sendSystemPersists() throws Exception {
        when(notificationMapper.insert(any())).thenReturn(1);
        when(receiverMapper.batchInsert(any())).thenReturn(1);
        lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        notificationService.sendSystem("ENROLL", "选课成功", "内容", List.of(301L));
        verify(notificationMapper).insert(any());
        verify(receiverMapper).batchInsert(any());
    }

    // ===== 已读 =====

    /** 已读他人通知 → 403 */
    @Test
    void markReadOthersRejected() {
        NotificationReceiver r = new NotificationReceiver();
        r.setId(5L);
        r.setStudentId(999L);
        r.setNotificationId(1L);
        r.setIsRead(false);
        when(receiverMapper.selectById(5L)).thenReturn(r);
        assertThrows(BusinessException.class, () -> notificationService.markRead(5L));
    }

    /** 已读自己未读通知 → 更新 */
    @Test
    void markReadOwnSuccess() {
        NotificationReceiver r = new NotificationReceiver();
        r.setId(7L);
        r.setStudentId(1L);
        r.setNotificationId(2L);
        r.setIsRead(false);
        when(receiverMapper.selectById(7L)).thenReturn(r);
        notificationService.markRead(7L);
        verify(receiverMapper).updateById(r);
    }

    /** 已读幂等：已读通知不重复更新 */
    @Test
    void markReadAlreadyReadNoUpdate() {
        NotificationReceiver r = new NotificationReceiver();
        r.setId(8L);
        r.setStudentId(1L);
        r.setNotificationId(2L);
        r.setIsRead(true);
        when(receiverMapper.selectById(8L)).thenReturn(r);
        notificationService.markRead(8L);
        verify(receiverMapper, never()).updateById(any());
    }

    private com.example.sms.entity.StudentCourse studentCourse(Long courseId, Long studentId) {
        com.example.sms.entity.StudentCourse sc = new com.example.sms.entity.StudentCourse();
        sc.setCourseId(courseId);
        sc.setStudentId(studentId);
        return sc;
    }
}
