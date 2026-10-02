package com.example.sms.service; // 声明包名：本测试类位于 service 包，与被测 CourseService 同包

// import 区：引入业务异常、课程相关 DTO/实体、Mapper 接口与用户上下文工具类，用于准备测试数据与断言
import com.example.sms.common.BusinessException;
import com.example.sms.dto.CourseFormDTO;
import com.example.sms.entity.Course;
import com.example.sms.entity.StudentCourse;
import com.example.sms.mapper.CourseGradeAuditMapper;
import com.example.sms.mapper.CourseMapper;
import com.example.sms.mapper.StaffMapper;
import com.example.sms.mapper.StudentCourseMapper;
import com.example.sms.util.UserContext;

// import 区：引入 JUnit5 生命周期/测试注解与 Mockito 注解（Mock 造桩、InjectMocks 注入）
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// import 区：引入 BigDecimal（学分比较）与 List 集合
import java.math.BigDecimal;
import java.util.List;

// import 区：静态导入 JUnit 断言（assertEquals/assertThrows）与 Mockito 打桩/校验方法
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
@ExtendWith(MockitoExtension.class) // 启用 Mockito 的 JUnit5 扩展：自动初始化 @Mock/@InjectMocks
class CourseServiceTest { // 测试类声明：所有 Mapper 均用 Mock，不依赖真实数据库

    @Mock // 声明课程 Mapper 的 Mock 对象
    private CourseMapper courseMapper;
    @Mock // 声明教职工 Mapper 的 Mock 对象
    private StaffMapper staffMapper;
    @Mock // 声明成绩审核 Mapper 的 Mock 对象
    private CourseGradeAuditMapper auditMapper;
    @Mock // 声明选课关系 Mapper 的 Mock 对象（用于查询选了某门课的学生）
    private StudentCourseMapper studentCourseMapper;
    @Mock // 声明通知服务的 Mock 对象（用于校验是否发送了 COURSE_CHANGE 通知）
    private NotificationService notificationService;

    @InjectMocks // 把上述 Mock 自动注入到被测的 CourseService 实例中
    private CourseService courseService;

    @BeforeEach // JUnit5 生命周期注解：每个测试方法执行前运行
    void setUp() { // 每个用例执行前的公共初始化
        adminContext(); // 默认切换到管理员登录上下文（大多数用例以管理员身份操作）
    }

    @AfterEach // JUnit5 生命周期注解：每个测试方法执行后运行
    void tearDown() { // 每个用例执行后的清理
        UserContext.clear(); // 清理全局用户上下文，避免身份信息残留污染后续用例
    }

    /** 切换到管理员登录上下文 */
    private void adminContext() { // 工具方法：构造管理员身份并写入全局上下文
        UserContext.CurrentUser u = new UserContext.CurrentUser(); // 创建当前用户对象
        u.setUserId(2L); // 设置用户 ID 为 2
        u.setUserNo("admin"); // 设置账号为 admin
        u.setRealName("系统管理员"); // 设置真实姓名为系统管理员
        u.setRoleType("ADMIN"); // 设置角色为管理员
        UserContext.set(u); // 写入全局用户上下文
    }

    /** 切换到指定 userId 的教师登录上下文 */
    private void teacherContext(Long userId) { // 工具方法：构造指定 ID 的教师身份并写入全局上下文
        UserContext.CurrentUser u = new UserContext.CurrentUser(); // 创建当前用户对象
        u.setUserId(userId); // 设置用户 ID（由调用方传入）
        u.setUserNo("T1001"); // 设置工号为 T1001
        u.setRealName("王老师"); // 设置真实姓名
        u.setRoleType("TEACHER"); // 设置角色为教师
        UserContext.set(u); // 写入全局用户上下文
    }

    /** 构造一个已发布、由教师 2L 授课的课程 */
    private Course publishedCourse() { // 工具方法：构造一个"已发布、授课教师为 2L"的课程实体
        Course c = new Course(); // 创建课程实体对象
        c.setId(1L); // 设置课程 ID 为 1
        c.setCourseCode("CS101"); // 设置课程编号
        c.setCourseName("Java程序设计"); // 设置课程名称
        c.setCredit(new BigDecimal("3.00")); // 设置学分为 3.00
        c.setHours(48); // 设置课时为 48
        c.setSchedule("周一 1-2节"); // 设置上课时间为"周一 1-2节"
        c.setLocation("教学楼A101"); // 设置上课地点
        c.setCapacity(30); // 设置容量为 30 人
        c.setTeacherId(2L); // 设置授课教师 ID 为 2（与管理员用例中的教师身份对应）
        c.setStatus("PUBLISHED"); // 设置课程状态为已发布（PUBLISHED）
        return c; // 返回构造好的课程
    }

    /** 构造调课表单 DTO：传入新的上课时间与地点，其余字段故意设为与原课程不同的值（用于校验强制保留） */
    private CourseFormDTO dto(String schedule, String location) { // 工具方法：构造调课表单 DTO
        CourseFormDTO d = new CourseFormDTO(); // 创建课程表单 DTO 对象
        d.setCourseCode("OTHER"); // 故意把课程编号改成 OTHER（预期被忽略，强制保留原值）
        d.setCourseName("改名"); // 故意把课程名称改成"改名"（预期被忽略）
        d.setCredit(new BigDecimal("5.00")); // 故意把学分改成 5.00（预期被忽略）
        d.setHours(60); // 故意把课时改成 60（预期被忽略）
        d.setCapacity(50); // 故意把容量改成 50（预期被忽略）
        d.setSchedule(schedule); // 设置新的上课时间（这是允许修改的调课字段）
        d.setLocation(location); // 设置新的上课地点（这是允许修改的调课字段）
        return d; // 返回构造好的 DTO
    }

    /** 已发布课程调课：仅更新 schedule/location，其余字段强制保留原值 */
    @Test // 标记这是一个测试方法
    void publishedCourseOnlyScheduleLocationUpdated() { // 用例1：已发布课程调课时只更新时间/地点字段
        Course course = publishedCourse(); // 构造已发布的课程（作为数据库中的原记录）
        when(courseMapper.selectById(1L)).thenReturn(course); // 打桩：按 ID 1 查到该课程
        when(studentCourseMapper.selectList(any())).thenReturn(List.of()); // 打桩：该课程没有选课学生（避免发送通知）

        courseService.updateCourse(1L, dto("周三 3-4节", "教学楼B202")); // 调用被测方法：提交调课表单（新时间周三 3-4节、新地点教学楼B202）

        ArgumentCaptor<Course> captor = ArgumentCaptor.forClass(Course.class); // 创建参数捕获器，捕获更新调用中的课程对象
        verify(courseMapper).updateById(captor.capture()); // 断言：updateById 被调用 1 次，并捕获入参
        Course saved = captor.getValue(); // 取出最终被更新的课程对象
        // 调课字段被更新
        assertEquals("周三 3-4节", saved.getSchedule()); // 断言：上课时间已更新为新值
        assertEquals("教学楼B202", saved.getLocation()); // 断言：上课地点已更新为新值
        // 其余字段保持原值（即使 DTO 传入不同值也被强制保留）
        assertEquals("CS101", saved.getCourseCode()); // 断言：课程编号仍是原值（DTO 中的 OTHER 被忽略）
        assertEquals("Java程序设计", saved.getCourseName()); // 断言：课程名称仍是原值（DTO 中的"改名"被忽略）
        assertEquals(48, saved.getHours()); // 断言：课时仍是原值 48（DTO 中的 60 被忽略）
        assertEquals(new BigDecimal("3.00"), saved.getCredit()); // 断言：学分仍是原值 3.00（DTO 中的 5.00 被忽略）
        assertEquals(30, saved.getCapacity()); // 断言：容量仍是原值 30（DTO 中的 50 被忽略）
    }

    /** 调课字段变化 → 自动发送 COURSE_CHANGE 通知给选课学生 */
    @Test // 标记这是一个测试方法
    void scheduleChangeTriggersNotification() { // 用例2：调课字段变化时自动发通知给选课学生
        Course course = publishedCourse(); // 构造已发布的课程
        when(courseMapper.selectById(1L)).thenReturn(course); // 打桩：按 ID 1 查到该课程
        StudentCourse sc = new StudentCourse(); // 创建一条选课关系记录
        sc.setStudentId(10L); // 设置学生 ID 为 10（代表一名选了该课的学生）
        when(studentCourseMapper.selectList(any())).thenReturn(List.of(sc)); // 打桩：查出该课程有 1 名选课学生（id=10）

        courseService.updateCourse(1L, dto("周三 3-4节", "教学楼A101")); // 调用被测方法：调课（时间变化，地点不变）

        verify(notificationService).sendSystem(eq("COURSE_CHANGE"), eq("调课通知"), any(), eq(List.of(10L))); // 断言：向学生 10 发送了 COURSE_CHANGE 类型通知
    }

    /** 调课字段无变化 → 不发送通知 */
    @Test // 标记这是一个测试方法
    void noScheduleChangeNoNotification() { // 用例3：调课字段无变化时不发通知
        Course course = publishedCourse(); // 构造已发布的课程（原时间周一 1-2节、原地点教学楼A101）
        when(courseMapper.selectById(1L)).thenReturn(course); // 打桩：按 ID 1 查到该课程

        courseService.updateCourse(1L, dto("周一 1-2节", "教学楼A101")); // 调用被测方法：提交的调课表单与课程原值完全相同（无实际变化）

        verify(notificationService, never()).sendSystem(any(), any(), any(), any()); // 断言：未发送任何系统通知
    }

    /** 已发布课程不可删除 */
    @Test // 标记这是一个测试方法
    void publishedCourseCannotDelete() { // 用例4：已发布课程不可删除
        Course course = publishedCourse(); // 构造已发布的课程
        when(courseMapper.selectById(1L)).thenReturn(course); // 打桩：按 ID 1 查到该课程
        assertThrows(BusinessException.class, () -> courseService.deleteCourse(1L)); // 断言：删除已发布课程会抛出业务异常
    }

    /** 已发布课程不可重复发布 */
    @Test // 标记这是一个测试方法
    void publishCourseAlreadyPublishedRejected() { // 用例5：已发布课程不可重复发布
        Course course = publishedCourse(); // 构造已发布的课程
        when(courseMapper.selectById(1L)).thenReturn(course); // 打桩：按 ID 1 查到该课程
        assertThrows(BusinessException.class, () -> courseService.publishCourse(1L)); // 断言：重复发布会抛出业务异常
    }

    /** 教师操作他人课程（含调课）→ 403 */
    @Test // 标记这是一个测试方法
    void teacherEditingOthersCourseRejected() { // 用例6：教师操作他人课程被拒绝
        teacherContext(1L); // 切换为教师身份（userId=1L）
        Course course = publishedCourse(); // 构造已发布的课程
        course.setTeacherId(99L); // 把该课程的授课教师改成 99L（即不是当前教师 1L 的课程）
        when(courseMapper.selectById(1L)).thenReturn(course); // 打桩：按 ID 1 查到该课程
        assertThrows(BusinessException.class, () -> courseService.updateCourse(1L, dto("周三 3-4节", "教学楼A101"))); // 断言：非授课教师调课会抛出业务异常（403 语义）
    }

    /** 教师调自己的已发布课程 → 允许并通知 */
    @Test // 标记这是一个测试方法
    void teacherAdjustOwnPublishedCourseAllowed() { // 用例7：教师调自己授课的已发布课程被允许且发通知
        teacherContext(2L); // 切换为教师身份（userId=2L，与课程的授课教师一致）
        Course course = publishedCourse(); // 构造已发布的课程（授课教师为 2L）
        when(courseMapper.selectById(1L)).thenReturn(course); // 打桩：按 ID 1 查到该课程
        StudentCourse sc = new StudentCourse(); // 创建一条选课关系记录
        sc.setStudentId(10L); // 设置学生 ID 为 10（该课程的选课学生）
        when(studentCourseMapper.selectList(any())).thenReturn(List.of(sc)); // 打桩：查出该课程有 1 名选课学生

        courseService.updateCourse(1L, dto("周三 3-4节", "教学楼A101")); // 调用被测方法：教师对自己授课的课程调课（时间变化）
        verify(notificationService).sendSystem(eq("COURSE_CHANGE"), eq("调课通知"), any(), any()); // 断言：发送了 COURSE_CHANGE 类型调课通知
    }
}
