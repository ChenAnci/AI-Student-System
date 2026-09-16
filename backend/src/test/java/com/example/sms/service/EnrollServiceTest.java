package com.example.sms.service;

import com.example.sms.entity.Course;
import com.example.sms.entity.Student;
import com.example.sms.mapper.CourseGradeAuditMapper;
import com.example.sms.mapper.CourseMapper;
import com.example.sms.mapper.StaffMapper;
import com.example.sms.mapper.StudentCourseMapper;
import com.example.sms.mapper.StudentMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 选课自动通知单元测试：选课成功后发送 ENROLL 通知
 */
@ExtendWith(MockitoExtension.class)
class EnrollServiceTest {

    @Mock
    private StudentCourseMapper studentCourseMapper;
    @Mock
    private CourseMapper courseMapper;
    @Mock
    private StaffMapper staffMapper;
    @Mock
    private StudentMapper studentMapper;
    @Mock
    private CourseGradeAuditMapper auditMapper;
    @Mock
    private CourseService courseService;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private EnrollService enrollService;

    private Course publishedCourse() {
        Course c = new Course();
        c.setId(1L);
        c.setCourseCode("CS101");
        c.setCourseName("Java程序设计");
        c.setSchedule("周一 1-2节");
        c.setStatus("PUBLISHED");
        c.setCapacity(30);
        c.setCurrentEnrolled(5);
        return c;
    }

    /** 选课成功 → 发送 ENROLL 通知给该学生 */
    @Test
    void enroll_shouldNotifyStudent() {
        Course course = publishedCourse();
        when(courseMapper.selectByIdForUpdate(1L)).thenReturn(course);
        when(auditMapper.selectOne(any())).thenReturn(null);
        Student student = new Student();
        student.setId(10L);
        student.setStatus("ENABLED");
        when(studentMapper.selectById(10L)).thenReturn(student);
        when(studentCourseMapper.selectCount(any())).thenReturn(0L);
        when(courseMapper.selectList(any())).thenReturn(List.of()); // 无时间冲突

        enrollService.enroll(10L, 1L);

        verify(studentCourseMapper).insert(any());
        verify(courseMapper).updateById(course);
        verify(notificationService).sendSystem(eq("ENROLL"), eq("选课成功"), any(), eq(List.of(10L)));
    }

    /** 课程未发布 → 不可选课，不发送通知 */
    @Test
    void enroll_shouldRejectUnpublishedCourse() {
        Course course = publishedCourse();
        course.setStatus("UNPUBLISHED");
        when(courseMapper.selectByIdForUpdate(1L)).thenReturn(course);

        org.junit.jupiter.api.Assertions.assertThrows(
                com.example.sms.common.BusinessException.class,
                () -> enrollService.enroll(10L, 1L));
        org.mockito.Mockito.verify(notificationService,
                org.mockito.Mockito.never()).sendSystem(any(), any(), any(), any());
    }

    /** 课程满员 → 不可选课，不发送通知 */
    @Test
    void enroll_shouldRejectFullCourse() {
        Course course = publishedCourse();
        course.setCurrentEnrolled(30);
        when(courseMapper.selectByIdForUpdate(1L)).thenReturn(course);

        org.junit.jupiter.api.Assertions.assertThrows(
                com.example.sms.common.BusinessException.class,
                () -> enrollService.enroll(10L, 1L));
        org.mockito.Mockito.verify(notificationService,
                org.mockito.Mockito.never()).sendSystem(any(), any(), any(), any());
    }

    /** 代学生选课（教秘）→ 仅发送一条 ENROLL 通知（复用 enroll，避免重复） */
    @Test
    void adminEnroll_shouldNotifyStudentOnce() {
        Course course = publishedCourse();
        when(courseMapper.selectByIdForUpdate(1L)).thenReturn(course);
        when(auditMapper.selectOne(any())).thenReturn(null);
        Student student = new Student();
        student.setId(10L);
        student.setStatus("ENABLED");
        when(studentMapper.selectById(10L)).thenReturn(student);
        when(studentCourseMapper.selectCount(any())).thenReturn(0L);
        when(courseMapper.selectList(any())).thenReturn(List.of());

        com.example.sms.util.UserContext.CurrentUser u = new com.example.sms.util.UserContext.CurrentUser();
        u.setUserId(2L);
        u.setRoleType("ADMIN");
        com.example.sms.util.UserContext.set(u);
        try {
            enrollService.adminEnroll(10L, 1L);
        } finally {
            com.example.sms.util.UserContext.clear();
        }

        verify(notificationService).sendSystem(eq("ENROLL"), eq("选课成功"), any(), eq(List.of(10L)));
    }
}
