package com.example.sms.service;

import com.example.sms.common.BusinessException;
import com.example.sms.dto.SendNotificationDTO;
import com.example.sms.entity.Course;
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
        UserContext.CurrentUser user = new UserContext.CurrentUser();
        user.setUserId(1L);
        user.setUserNo("T001");
        user.setRealName("张老师");
        user.setRoleType("TEACHER");
        UserContext.set(user);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    /** 老师发送非本人课程 → 403 拒绝 */
    @Test
    void teacherSendingOthersCourseRejected() {
        Course course = new Course();
        course.setId(10L);
        course.setTeacherId(99L);
        when(courseMapper.selectById(10L)).thenReturn(course);

        SendNotificationDTO dto = new SendNotificationDTO();
        dto.setTitle("测试");
        dto.setContent("内容");
        SendNotificationDTO.Target target = new SendNotificationDTO.Target();
        target.setKind("COURSE");
        target.setCourseId(10L);
        dto.setTarget(target);

        assertThrows(BusinessException.class, () -> notificationService.send(dto));
    }

    /** 已读他人通知 → 403 拒绝 */
    @Test
    void markReadOthersRejected() {
        com.example.sms.entity.NotificationReceiver r = new com.example.sms.entity.NotificationReceiver();
        r.setId(5L);
        r.setStudentId(999L);
        r.setNotificationId(1L);
        r.setIsRead(false);
        when(receiverMapper.selectById(5L)).thenReturn(r);

        assertThrows(BusinessException.class, () -> notificationService.markRead(5L));
    }

    /** ALL 接收方式：解析全部启用学生并生成通知+接收明细 */
    @Test
    void sendToAllEnabledStudents() throws Exception {
        Student s1 = new Student();
        s1.setId(101L);
        Student s2 = new Student();
        s2.setId(102L);
        when(studentMapper.selectList(any())).thenReturn(List.of(s1, s2));
        when(notificationMapper.insert(any())).thenReturn(1);
        when(receiverMapper.insert(any())).thenReturn(1);
        lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        SendNotificationDTO dto = new SendNotificationDTO();
        dto.setTitle("全体通知");
        dto.setContent("内容");
        SendNotificationDTO.Target target = new SendNotificationDTO.Target();
        target.setKind("ALL");
        dto.setTarget(target);

        notificationService.send(dto);
        verify(notificationMapper).insert(any());
        verify(receiverMapper, times(2)).insert(any());
    }
}
