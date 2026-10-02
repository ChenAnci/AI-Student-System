package com.example.sms.service; // 声明包名：本测试类位于 service 包，与被测 EnrollService 同包

// import 区：引入课程/学生实体与各 Mapper 接口，用于构造测试数据
import com.example.sms.entity.Course;
import com.example.sms.entity.Student;
import com.example.sms.mapper.CourseGradeAuditMapper;
import com.example.sms.mapper.CourseMapper;
import com.example.sms.mapper.StaffMapper;
import com.example.sms.mapper.StudentCourseMapper;
import com.example.sms.mapper.StudentMapper;

// import 区：引入 JUnit5 测试注解与 Mockito 注解（Mock 造桩、InjectMocks 注入）
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// import 区：引入 List 集合
import java.util.List;

// import 区：静态导入 Mockito 打桩/校验方法（any/anyLong/eq/verify/when）
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 选课自动通知单元测试：选课成功后发送 ENROLL 通知
 */
@ExtendWith(MockitoExtension.class) // 启用 Mockito 的 JUnit5 扩展：自动初始化 @Mock/@InjectMocks
class EnrollServiceTest { // 测试类声明：所有 Mapper 均用 Mock，不依赖真实数据库

    @Mock // 声明选课关系 Mapper 的 Mock 对象
    private StudentCourseMapper studentCourseMapper;
    @Mock // 声明课程 Mapper 的 Mock 对象
    private CourseMapper courseMapper;
    @Mock // 声明教职工 Mapper 的 Mock 对象
    private StaffMapper staffMapper;
    @Mock // 声明学生 Mapper 的 Mock 对象
    private StudentMapper studentMapper;
    @Mock // 声明成绩审核 Mapper 的 Mock 对象
    private CourseGradeAuditMapper auditMapper;
    @Mock // 声明课程服务的 Mock 对象（选课成功后可能需要联动课程相关操作）
    private CourseService courseService;
    @Mock // 声明通知服务的 Mock 对象（用于校验是否发送了 ENROLL 通知）
    private NotificationService notificationService;

    @InjectMocks // 把上述 Mock 自动注入到被测的 EnrollService 实例中
    private EnrollService enrollService;

    /** 构造一个已发布、未满员（30 人中已选 5 人）的课程 */
    private Course publishedCourse() { // 工具方法：构造一个可正常选课的课程实体
        Course c = new Course(); // 创建课程实体对象
        c.setId(1L); // 设置课程 ID 为 1
        c.setCourseCode("CS101"); // 设置课程编号
        c.setCourseName("Java程序设计"); // 设置课程名称
        c.setSchedule("周一 1-2节"); // 设置上课时间
        c.setStatus("PUBLISHED"); // 设置课程状态为已发布（可被选课）
        c.setCapacity(30); // 设置课程容量为 30 人
        c.setCurrentEnrolled(5); // 设置当前已选人数为 5（未满员）
        return c; // 返回构造好的课程
    }

    /** 选课成功 → 发送 ENROLL 通知给该学生 */
    @Test // 标记这是一个测试方法
    void enroll_shouldNotifyStudent() { // 用例1：选课成功后向该学生发送 ENROLL 通知
        Course course = publishedCourse(); // 构造未满员的已发布课程
        when(courseMapper.selectByIdForUpdate(1L)).thenReturn(course); // 打桩：按 ID 1 行级锁查询该课程（模拟并发安全查课）
        when(auditMapper.selectOne(any())).thenReturn(null); // 打桩：查询成绩审核记录返回 null（该课程无审核锁定限制）
        Student student = new Student(); // 创建学生实体
        student.setId(10L); // 设置学生 ID 为 10（当前选课的学生）
        student.setStatus("ENABLED"); // 设置学生状态为启用（ENABLED）
        when(studentMapper.selectById(10L)).thenReturn(student); // 打桩：按 ID 10 查到该学生
        when(studentCourseMapper.selectCount(any())).thenReturn(0L); // 打桩：该学生尚未选过这门课（防重复选课校验通过）
        when(courseMapper.selectList(any())).thenReturn(List.of()); // 无时间冲突 // 打桩：查询选课时间冲突课程为空（无时间冲突）

        enrollService.enroll(10L, 1L); // 调用被测方法：学生 10 选课程 1

        verify(studentCourseMapper).insert(any()); // 断言：选课关系 insert 被调用 1 次（选课记录落库）
        verify(courseMapper).updateById(course); // 断言：课程已选人数被更新（+1）
        verify(notificationService).sendSystem(eq("ENROLL"), eq("选课成功"), any(), eq(List.of(10L))); // 断言：向学生 10 发送了 ENROLL 类型选课成功通知
    }

    /** 课程未发布 → 不可选课，不发送通知 */
    @Test // 标记这是一个测试方法
    void enroll_shouldRejectUnpublishedCourse() { // 用例2：课程未发布时不可选课
        Course course = publishedCourse(); // 构造课程实体
        course.setStatus("UNPUBLISHED"); // 把课程状态改为未发布（UNPUBLISHED）
        when(courseMapper.selectByIdForUpdate(1L)).thenReturn(course); // 打桩：按 ID 1 查到该未发布课程

        org.junit.jupiter.api.Assertions.assertThrows( // 断言：选未发布课程会抛出异常（此处用全限定名调用 JUnit 断言，避免依赖冲突）
                com.example.sms.common.BusinessException.class, // 期望的异常类型为业务异常 BusinessException
                () -> enrollService.enroll(10L, 1L)); // 触发选课操作
        org.mockito.Mockito.verify(notificationService, // 校验通知服务的调用情况（使用全限定名，与上方风格保持一致）
                org.mockito.Mockito.never()).sendSystem(any(), any(), any(), any()); // 断言：未发送任何系统通知（选课失败不发通知）
    }

    /** 课程满员 → 不可选课，不发送通知 */
    @Test // 标记这是一个测试方法
    void enroll_shouldRejectFullCourse() { // 用例3：课程满员时不可选课
        Course course = publishedCourse(); // 构造课程实体
        course.setCurrentEnrolled(30); // 把已选人数设为 30（等于容量，已满员）
        when(courseMapper.selectByIdForUpdate(1L)).thenReturn(course); // 打桩：按 ID 1 查到该已满员课程

        org.junit.jupiter.api.Assertions.assertThrows( // 断言：选满员课程会抛出异常
                com.example.sms.common.BusinessException.class, // 期望的异常类型为业务异常 BusinessException
                () -> enrollService.enroll(10L, 1L)); // 触发选课操作
        org.mockito.Mockito.verify(notificationService, // 校验通知服务的调用情况
                org.mockito.Mockito.never()).sendSystem(any(), any(), any(), any()); // 断言：未发送任何系统通知（满员选课失败不发通知）
    }

    /** 代学生选课（教秘）→ 仅发送一条 ENROLL 通知（复用 enroll，避免重复） */
    @Test // 标记这是一个测试方法
    void adminEnroll_shouldNotifyStudentOnce() { // 用例4：教学秘书代选课只发一条 ENROLL 通知
        Course course = publishedCourse(); // 构造未满员的已发布课程
        when(courseMapper.selectByIdForUpdate(1L)).thenReturn(course); // 打桩：按 ID 1 行级锁查询该课程
        when(auditMapper.selectOne(any())).thenReturn(null); // 打桩：无审核锁定限制
        Student student = new Student(); // 创建学生实体
        student.setId(10L); // 设置学生 ID 为 10（被代选课的学生）
        student.setStatus("ENABLED"); // 设置学生状态为启用
        when(studentMapper.selectById(10L)).thenReturn(student); // 打桩：按 ID 10 查到该学生
        when(studentCourseMapper.selectCount(any())).thenReturn(0L); // 打桩：该学生未选过这门课
        when(courseMapper.selectList(any())).thenReturn(List.of()); // 打桩：无时间冲突

        com.example.sms.util.UserContext.CurrentUser u = new com.example.sms.util.UserContext.CurrentUser(); // 构造当前用户对象（用全限定名）
        u.setUserId(2L); // 设置用户 ID 为 2（管理员身份）
        u.setRoleType("ADMIN"); // 设置角色为管理员（教学秘书）
        // 以管理员（教学秘书）身份代学生选课，结束后清理登录上下文
        com.example.sms.util.UserContext.set(u); // 把管理员身份写入全局用户上下文
        try {
            enrollService.adminEnroll(10L, 1L); // 调用被测方法：管理员代学生 10 选课程 1
        } finally {
            com.example.sms.util.UserContext.clear(); // 无论成功与否都清理登录上下文，防止污染其他用例
        }

        verify(notificationService).sendSystem(eq("ENROLL"), eq("选课成功"), any(), eq(List.of(10L))); // 断言：仅向学生 10 发送一条 ENROLL 通知（无重复通知）
    }
}
