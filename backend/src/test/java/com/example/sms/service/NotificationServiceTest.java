package com.example.sms.service; // 声明包名：本测试类位于 service 包，与被测 NotificationService 同包

// import 区：引入业务异常、通知相关 DTO/实体、Mapper 接口与用户上下文工具类
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

// import 区：引入 WebSocket 会话注册表（用于 WebSocket 实时推送）与 JSON 序列化工具
import com.example.sms.websocket.WsSessionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;

// import 区：引入 JUnit5 生命周期/测试注解与 Mockito 注解（Mock 造桩、InjectMocks 注入）
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// import 区：引入 List 集合
import java.util.List;

// import 区：静态导入 JUnit 断言（assertThrows）与 Mockito 打桩/校验方法（lenient 用于宽松打桩）
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 通知服务单元测试：权限边界（教师仅可发本课程）、接收人解析（管理员）、系统自动通知、已读标记
 */
@ExtendWith(MockitoExtension.class) // 启用 Mockito 的 JUnit5 扩展：自动初始化 @Mock/@InjectMocks
class NotificationServiceTest { // 测试类声明：所有依赖均用 Mock，不依赖真实数据库

    @Mock // 声明通知主表 Mapper 的 Mock 对象
    private NotificationMapper notificationMapper;
    @Mock // 声明通知接收人 Mapper 的 Mock 对象
    private NotificationReceiverMapper receiverMapper;
    @Mock // 声明学生 Mapper 的 Mock 对象
    private StudentMapper studentMapper;
    @Mock // 声明课程 Mapper 的 Mock 对象
    private CourseMapper courseMapper;
    @Mock // 声明选课关系 Mapper 的 Mock 对象
    private StudentCourseMapper studentCourseMapper;
    @Mock // 声明 WebSocket 会话注册表的 Mock 对象（用于实时推送校验）
    private WsSessionRegistry wsSessionRegistry;
    @Mock // 声明 JSON 序列化器的 Mock 对象（用于 WebSocket 推送内容序列化）
    private ObjectMapper objectMapper;

    @InjectMocks // 把上述 Mock 自动注入到被测的 NotificationService 实例中
    private NotificationService notificationService;

    @BeforeEach // JUnit5 生命周期注解：每个测试方法执行前运行
    void setUp() { // 每个用例执行前的公共初始化
        teacherContext(); // 默认以教师身份执行用例（教师权限相关用例居多）
    }

    @AfterEach // JUnit5 生命周期注解：每个测试方法执行后运行
    void tearDown() { // 每个用例执行后的清理
        UserContext.clear(); // 清理全局用户上下文，避免身份残留污染
    }

    /** 切换到教师登录上下文（userId=1L） */
    private void teacherContext() { // 工具方法：构造教师身份并写入全局上下文
        UserContext.CurrentUser u = new UserContext.CurrentUser(); // 创建当前用户对象
        u.setUserId(1L); // 设置用户 ID 为 1
        u.setUserNo("T001"); // 设置工号为 T001
        u.setRealName("张老师"); // 设置真实姓名
        u.setRoleType("TEACHER"); // 设置角色为教师
        UserContext.set(u); // 写入全局用户上下文
    }

    /** 切换到管理员登录上下文 */
    private void adminContext() { // 工具方法：构造管理员身份并写入全局上下文
        UserContext.CurrentUser u = new UserContext.CurrentUser(); // 创建当前用户对象
        u.setUserId(2L); // 设置用户 ID 为 2
        u.setUserNo("admin"); // 设置账号为 admin
        u.setRealName("系统管理员"); // 设置真实姓名
        u.setRoleType("ADMIN"); // 设置角色为管理员
        UserContext.set(u); // 写入全局用户上下文
    }

    /** 构造指定发送类型（kind）的基础通知 DTO */
    private SendNotificationDTO dto(String kind) { // 工具方法：构造一个指定目标类型的通知发送 DTO
        SendNotificationDTO d = new SendNotificationDTO(); // 创建通知发送 DTO 对象
        d.setTitle("测试"); // 设置通知标题
        d.setContent("内容"); // 设置通知内容
        SendNotificationDTO.Target t = new SendNotificationDTO.Target(); // 创建目标对象（指定接收范围）
        t.setKind(kind); // 设置目标类型（ALL/CLASS/COURSE/STUDENT_IDS 等）
        d.setTarget(t); // 把目标对象挂到 DTO 上
        return d; // 返回构造好的 DTO
    }

    // ===== 权限边界 =====

    /** 老师向非本人课程发送（COURSE）→ 403 */
    @Test // 标记这是一个测试方法
    void teacherSendingOthersCourseRejected() { // 用例1：教师向非本人课程发通知被拒绝
        Course course = new Course(); // 创建课程实体
        course.setId(10L); // 设置课程 ID 为 10
        course.setTeacherId(99L); // 设置授课教师为 99L（不是当前登录教师 1L）
        when(courseMapper.selectById(10L)).thenReturn(course); // 打桩：按 ID 10 查到该课程

        SendNotificationDTO dto = dto("COURSE"); // 构造按课程发送的通知 DTO
        dto.getTarget().setCourseId(10L); // 指定目标课程为 10
        assertThrows(BusinessException.class, () -> notificationService.send(dto)); // 断言：向非本人课程发通知会抛出业务异常（403 语义）
    }

    /** 老师用 ALL 方式发送 → 403（只能按课程，防绕过） */
    @Test // 标记这是一个测试方法
    void teacherSendingAllRejected() { // 用例2：教师用 ALL（全员）方式发送被拒绝
        assertThrows(BusinessException.class, () -> notificationService.send(dto("ALL"))); // 断言：教师以 ALL 全员方式发通知会抛出业务异常（防止绕过课程权限）
    }

    /** 老师用 STUDENT_IDS 方式发送 → 403（只能按课程，防绕过） */
    @Test // 标记这是一个测试方法
    void teacherSendingStudentIdsRejected() { // 用例3：教师用 STUDENT_IDS（指定学生）方式发送被拒绝
        SendNotificationDTO dto = dto("STUDENT_IDS"); // 构造指定学生列表发送的通知 DTO
        dto.getTarget().setStudentIds(List.of(101L)); // 指定接收学生为 101
        assertThrows(BusinessException.class, () -> notificationService.send(dto)); // 断言：教师以指定学生方式发通知会抛出业务异常（只能按课程发）
    }

    /** 老师按自己课程发送（COURSE）→ 允许 */
    @Test // 标记这是一个测试方法
    void teacherSendingOwnCourseAllowed() throws Exception { // 用例4：教师向自己授课的课程发通知被允许
        Course course = new Course(); // 创建课程实体
        course.setId(10L); // 设置课程 ID 为 10
        course.setTeacherId(1L); // 设置授课教师为 1L（与当前登录教师一致）
        when(courseMapper.selectById(10L)).thenReturn(course); // 打桩：按 ID 10 查到该课程
        when(studentCourseMapper.selectList(any())).thenReturn(List.of(studentCourse(10L, 101L))); // 打桩：该课程有 1 名选课学生（101）
        when(notificationMapper.insert(any())).thenReturn(1); // 打桩：通知主表插入成功（返回影响行数 1）
        when(receiverMapper.batchInsert(any())).thenReturn(1); // 打桩：接收人批量插入成功（返回影响行数 1）
        lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{}"); // 宽松打桩：序列化返回 {}（该用例可能不走到推送，避免未使用打桩报错）

        SendNotificationDTO dto = dto("COURSE"); // 构造按课程发送的通知 DTO
        dto.getTarget().setCourseId(10L); // 指定目标课程为 10（自己的课）
        notificationService.send(dto); // 调用被测方法：教师向自己的课程发通知
        verify(notificationMapper).insert(any()); // 断言：通知主表 insert 被调用 1 次（通知落库）
    }

    // ===== 接收人解析（管理员） =====

    /** 管理员 ALL：全部启用学生 */
    @Test // 标记这是一个测试方法
    void adminSendToAllEnabledStudents() throws Exception { // 用例5：管理员 ALL 方式发送给全部启用学生
        adminContext(); // 切换为管理员身份
        Student s1 = new Student(); // 创建学生1
        s1.setId(101L); // 设置学生1 ID
        Student s2 = new Student(); // 创建学生2
        s2.setId(102L); // 设置学生2 ID
        when(studentMapper.selectList(any())).thenReturn(List.of(s1, s2)); // 打桩：查出 2 名启用学生（101、102）
        when(notificationMapper.insert(any())).thenReturn(1); // 打桩：通知主表插入成功
        when(receiverMapper.batchInsert(any())).thenReturn(1); // 打桩：接收人批量插入成功
        lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{}"); // 宽松打桩：序列化返回 {}（推送内容）

        notificationService.send(dto("ALL")); // 调用被测方法：管理员向全部学生发送通知
        verify(notificationMapper).insert(any()); // 断言：通知主表 insert 被调用 1 次
        verify(receiverMapper).batchInsert(any()); // 断言：接收人批量插入被调用 1 次（解析出 2 名接收人）
    }

    /** 管理员按班级：解析该班级学生 */
    @Test // 标记这是一个测试方法
    void adminSendByClass() throws Exception { // 用例6：管理员按班级发送，解析出该班级学生
        adminContext(); // 切换为管理员身份
        Student s = new Student(); // 创建学生实体
        s.setId(201L); // 设置学生 ID
        when(studentMapper.selectList(any())).thenReturn(List.of(s)); // 打桩：按班级查出 1 名学生（201）
        when(notificationMapper.insert(any())).thenReturn(1); // 打桩：通知主表插入成功
        when(receiverMapper.batchInsert(any())).thenReturn(1); // 打桩：接收人批量插入成功
        lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{}"); // 宽松打桩：序列化返回 {}

        SendNotificationDTO dto = dto("CLASS"); // 构造按班级发送的通知 DTO
        dto.getTarget().setClassName("软工2301"); // 指定目标班级为"软工2301"
        notificationService.send(dto); // 调用被测方法：管理员按班级发送通知
        verify(receiverMapper).batchInsert(any()); // 断言：接收人批量插入被调用（成功解析出该班级学生）
    }

    /** 精确选人但列表为空 → 报错 */
    @Test // 标记这是一个测试方法
    void studentIdsEmptyRejected() { // 用例7：指定学生列表为空时被拒绝
        adminContext(); // 切换为管理员身份
        SendNotificationDTO dto = dto("STUDENT_IDS"); // 构造指定学生列表发送的通知 DTO
        dto.getTarget().setStudentIds(List.of()); // 指定接收学生列表为空
        assertThrows(BusinessException.class, () -> notificationService.send(dto)); // 断言：接收人列表为空时发送会抛出业务异常
    }

    // ===== 系统自动通知 =====

    /** 系统通知接收人为空 → 静默跳过，不落库 */
    @Test // 标记这是一个测试方法
    void sendSystemEmptyReceiversSkipped() { // 用例8：系统通知接收人为空时静默跳过
        notificationService.sendSystem("ENROLL", "选课成功", "内容", List.of()); // 调用被测方法：发送一条接收人为空的系统通知
        verify(notificationMapper, never()).insert(any()); // 断言：通知主表 insert 从未被调用（不落库）
        verify(receiverMapper, never()).batchInsert(any()); // 断言：接收人批量插入从未被调用
    }

    /** 系统通知正常发送并落库 */
    @Test // 标记这是一个测试方法
    void sendSystemPersists() throws Exception { // 用例9：系统通知正常发送并落库
        when(notificationMapper.insert(any())).thenReturn(1); // 打桩：通知主表插入成功
        when(receiverMapper.batchInsert(any())).thenReturn(1); // 打桩：接收人批量插入成功
        lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{}"); // 宽松打桩：序列化返回 {}

        notificationService.sendSystem("ENROLL", "选课成功", "内容", List.of(301L)); // 调用被测方法：向学生 301 发送一条系统通知
        verify(notificationMapper).insert(any()); // 断言：通知主表 insert 被调用 1 次（通知落库）
        verify(receiverMapper).batchInsert(any()); // 断言：接收人批量插入被调用 1 次（接收人落库）
    }

    // ===== 已读 =====

    /** 已读他人通知 → 403 */
    @Test // 标记这是一个测试方法
    void markReadOthersRejected() { // 用例10：标记他人通知为已读被拒绝
        NotificationReceiver r = new NotificationReceiver(); // 创建通知接收记录
        r.setId(5L); // 设置记录 ID
        r.setStudentId(999L); // 设置接收学生为 999（不是当前登录用户 1L）
        r.setNotificationId(1L); // 设置所属通知 ID
        r.setIsRead(false); // 设置未读状态
        when(receiverMapper.selectById(5L)).thenReturn(r); // 打桩：按 ID 5 查到该接收记录
        assertThrows(BusinessException.class, () -> notificationService.markRead(5L)); // 断言：已读他人通知会抛出业务异常（403 语义）
    }

    /** 已读自己未读通知 → 更新 */
    @Test // 标记这是一个测试方法
    void markReadOwnSuccess() { // 用例11：已读自己的未读通知成功
        NotificationReceiver r = new NotificationReceiver(); // 创建通知接收记录
        r.setId(7L); // 设置记录 ID
        // 接收人 studentId 与当前登录用户（teacherContext 的 userId=1L）一致，属于"自己的通知"
        r.setStudentId(1L); // 设置接收学生为 1（与当前登录用户一致，是自己的通知）
        r.setNotificationId(2L); // 设置所属通知 ID
        r.setIsRead(false); // 设置未读状态
        when(receiverMapper.selectById(7L)).thenReturn(r); // 打桩：按 ID 7 查到该接收记录
        notificationService.markRead(7L); // 调用被测方法：标记自己的通知为已读
        verify(receiverMapper).updateById(r); // 断言：updateById 被调用 1 次（把该记录更新为已读）
    }

    /** 已读幂等：已读通知不重复更新 */
    @Test // 标记这是一个测试方法
    void markReadAlreadyReadNoUpdate() { // 用例12：已读通知重复标记不更新（幂等）
        NotificationReceiver r = new NotificationReceiver(); // 创建通知接收记录
        r.setId(8L); // 设置记录 ID
        r.setStudentId(1L); // 设置接收学生为 1（自己的通知）
        r.setNotificationId(2L); // 设置所属通知 ID
        r.setIsRead(true); // 设置已读状态（已经是已读）
        when(receiverMapper.selectById(8L)).thenReturn(r); // 打桩：按 ID 8 查到该接收记录
        notificationService.markRead(8L); // 调用被测方法：再次标记已读
        verify(receiverMapper, never()).updateById(any()); // 断言：updateById 从未被调用（已读状态不变，避免无谓更新）
    }

    /** 构造一条学生选课记录（courseId + studentId） */
    private com.example.sms.entity.StudentCourse studentCourse(Long courseId, Long studentId) { // 工具方法：构造选课关系实体（用全限定名）
        com.example.sms.entity.StudentCourse sc = new com.example.sms.entity.StudentCourse(); // 创建选课关系对象
        sc.setCourseId(courseId); // 设置课程 ID
        sc.setStudentId(studentId); // 设置学生 ID
        return sc; // 返回选课关系实体
    }
}
