package com.example.sms.service;

import com.example.sms.common.BusinessException;
import com.example.sms.dto.CourseFormDTO;
import com.example.sms.entity.Course;
import com.example.sms.entity.StudentCourse;
import com.example.sms.mapper.CourseGradeAuditMapper;
import com.example.sms.mapper.CourseMapper;
import com.example.sms.mapper.StaffMapper;
import com.example.sms.mapper.StudentCourseMapper;
import com.example.sms.util.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 课程调课业务与自动通知单元测试（重点：已发布课程仅允许调课字段 + COURSE_CHANGE 通知）
 */
@ExtendWith(MockitoExtension.class)
class CourseServiceTest {

    @Mock
    private CourseMapper courseMapper;
    @Mock
    private StaffMapper staffMapper;
    @Mock
    private CourseGradeAuditMapper auditMapper;
    @Mock
    private StudentCourseMapper studentCourseMapper;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private CourseService courseService;

    @BeforeEach
    void setUp() {
        adminContext();
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private void adminContext() {
        UserContext.CurrentUser u = new UserContext.CurrentUser();
        u.setUserId(2L);
        u.setUserNo("admin");
        u.setRealName("系统管理员");
        u.setRoleType("ADMIN");
        UserContext.set(u);
    }

    private void teacherContext(Long userId) {
        UserContext.CurrentUser u = new UserContext.CurrentUser();
        u.setUserId(userId);
        u.setUserNo("T1001");
        u.setRealName("王老师");
        u.setRoleType("TEACHER");
        UserContext.set(u);
    }

    private Course publishedCourse() {
        Course c = new Course();
        c.setId(1L);
        c.setCourseCode("CS101");
        c.setCourseName("Java程序设计");
        c.setCredit(new BigDecimal("3.00"));
        c.setHours(48);
        c.setSchedule("周一 1-2节");
        c.setLocation("教学楼A101");
        c.setCapacity(30);
        c.setTeacherId(2L);
        c.setStatus("PUBLISHED");
        return c;
    }

    private CourseFormDTO dto(String schedule, String location) {
        CourseFormDTO d = new CourseFormDTO();
        d.setCourseCode("OTHER");
        d.setCourseName("改名");
        d.setCredit(new BigDecimal("5.00"));
        d.setHours(60);
        d.setCapacity(50);
        d.setSchedule(schedule);
        d.setLocation(location);
        return d;
    }

    /** 已发布课程调课：仅更新 schedule/location，其余字段强制保留原值 */
    @Test
    void publishedCourseOnlyScheduleLocationUpdated() {
        Course course = publishedCourse();
        when(courseMapper.selectById(1L)).thenReturn(course);
        when(studentCourseMapper.selectList(any())).thenReturn(List.of());

        courseService.updateCourse(1L, dto("周三 3-4节", "教学楼B202"));

        ArgumentCaptor<Course> captor = ArgumentCaptor.forClass(Course.class);
        verify(courseMapper).updateById(captor.capture());
        Course saved = captor.getValue();
        // 调课字段被更新
        assertEquals("周三 3-4节", saved.getSchedule());
        assertEquals("教学楼B202", saved.getLocation());
        // 其余字段保持原值（即使 DTO 传入不同值也被强制保留）
        assertEquals("CS101", saved.getCourseCode());
        assertEquals("Java程序设计", saved.getCourseName());
        assertEquals(48, saved.getHours());
        assertEquals(new BigDecimal("3.00"), saved.getCredit());
        assertEquals(30, saved.getCapacity());
    }

    /** 调课字段变化 → 自动发送 COURSE_CHANGE 通知给选课学生 */
    @Test
    void scheduleChangeTriggersNotification() {
        Course course = publishedCourse();
        when(courseMapper.selectById(1L)).thenReturn(course);
        StudentCourse sc = new StudentCourse();
        sc.setStudentId(10L);
        when(studentCourseMapper.selectList(any())).thenReturn(List.of(sc));

        courseService.updateCourse(1L, dto("周三 3-4节", "教学楼A101"));

        verify(notificationService).sendSystem(eq("COURSE_CHANGE"), eq("调课通知"), any(), eq(List.of(10L)));
    }

    /** 调课字段无变化 → 不发送通知 */
    @Test
    void noScheduleChangeNoNotification() {
        Course course = publishedCourse();
        when(courseMapper.selectById(1L)).thenReturn(course);

        courseService.updateCourse(1L, dto("周一 1-2节", "教学楼A101"));

        verify(notificationService, never()).sendSystem(any(), any(), any(), any());
    }

    /** 已发布课程不可删除 */
    @Test
    void publishedCourseCannotDelete() {
        Course course = publishedCourse();
        when(courseMapper.selectById(1L)).thenReturn(course);
        assertThrows(BusinessException.class, () -> courseService.deleteCourse(1L));
    }

    /** 已发布课程不可重复发布 */
    @Test
    void publishCourseAlreadyPublishedRejected() {
        Course course = publishedCourse();
        when(courseMapper.selectById(1L)).thenReturn(course);
        assertThrows(BusinessException.class, () -> courseService.publishCourse(1L));
    }

    /** 教师操作他人课程（含调课）→ 403 */
    @Test
    void teacherEditingOthersCourseRejected() {
        teacherContext(1L);
        Course course = publishedCourse();
        course.setTeacherId(99L);
        when(courseMapper.selectById(1L)).thenReturn(course);
        assertThrows(BusinessException.class, () -> courseService.updateCourse(1L, dto("周三 3-4节", "教学楼A101")));
    }

    /** 教师调自己的已发布课程 → 允许并通知 */
    @Test
    void teacherAdjustOwnPublishedCourseAllowed() {
        teacherContext(2L);
        Course course = publishedCourse();
        when(courseMapper.selectById(1L)).thenReturn(course);
        StudentCourse sc = new StudentCourse();
        sc.setStudentId(10L);
        when(studentCourseMapper.selectList(any())).thenReturn(List.of(sc));

        courseService.updateCourse(1L, dto("周三 3-4节", "教学楼A101"));
        verify(notificationService).sendSystem(eq("COURSE_CHANGE"), eq("调课通知"), any(), any());
    }
}
