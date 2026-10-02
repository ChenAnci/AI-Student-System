package com.example.sms.service; // 声明包名：本测试类位于 service 包，与被测 GradeService 同包

// import 区：引入 EasyExcel（Excel 读写）、业务异常、课程/成绩/学生实体与 Mapper 接口，用于构造测试数据
import com.alibaba.excel.EasyExcel;
import com.example.sms.common.BusinessException;
import com.example.sms.entity.Course;
import com.example.sms.entity.CourseGradeAudit;
import com.example.sms.entity.Student;
import com.example.sms.entity.StudentCourse;
import com.example.sms.excel.GradeExcelRow;
import com.example.sms.mapper.CourseGradeAuditMapper;
import com.example.sms.mapper.CourseMapper;
import com.example.sms.mapper.StaffMapper;
import com.example.sms.mapper.StudentCourseMapper;
import com.example.sms.mapper.StudentMapper;
import com.example.sms.util.UserContext;
import com.example.sms.vo.GradeVO;

// import 区：引入 JUnit5 生命周期/测试注解与 Mockito 注解（Mock 造桩、InjectMocks 注入）
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// import 区：引入 Spring 的 Mock 响应对象与 Mock 文件上传对象（模拟导出下载与文件上传）
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;

// import 区：引入 IO 流工具类与集合类（内存中生成/读取 Excel 字节流）
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

// import 区：静态导入 AssertJ 断言与 Mockito 打桩/校验方法
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 成绩导入导出单元测试（Mockito，不依赖数据库）
 */
@ExtendWith(MockitoExtension.class) // 启用 Mockito 的 JUnit5 扩展：自动初始化 @Mock/@InjectMocks
class GradeServiceTest { // 测试类声明：所有 Mapper 均用 Mock，不依赖真实数据库

    @Mock // 声明选课关系 Mapper 的 Mock 对象（用于查询课程的选课学生）
    private StudentCourseMapper studentCourseMapper;

    @Mock // 声明课程 Mapper 的 Mock 对象
    private CourseMapper courseMapper;

    @Mock // 声明学生 Mapper 的 Mock 对象
    private StudentMapper studentMapper;

    @Mock // 声明教职工 Mapper 的 Mock 对象
    private StaffMapper staffMapper;

    @Mock // 声明成绩审核 Mapper 的 Mock 对象（用于控制成绩审核状态）
    private CourseGradeAuditMapper auditMapper;

    @Mock // 声明通知服务的 Mock 对象（用于校验成绩发布时是否发送通知）
    private NotificationService notificationService;

    @InjectMocks // 把上述 Mock 自动注入到被测的 GradeService 实例中
    private GradeService gradeService;

    private final Course course = buildCourse(1L); // 预构造课程实体：ID=1（作为公共测试数据）
    private final StudentCourse sc1 = buildSc(10L, 1L); // 预构造选课记录1：ID=10，学生 1L（课程 1 的学生甲）
    private final StudentCourse sc2 = buildSc(11L, 2L); // 预构造选课记录2：ID=11，学生 2L（课程 1 的学生乙）
    private final Student stu1 = buildStudent(1L, "S20230001", "张三"); // 预构造学生1：学号 S20230001，姓名张三
    private final Student stu2 = buildStudent(2L, "S20230002", "李四"); // 预构造学生2：学号 S20230002，姓名李四

    @BeforeEach // JUnit5 生命周期注解：每个测试方法执行前运行
    void setUp() { // 每个用例执行前的公共初始化
        UserContext.set(adminUser()); // 默认以管理员身份执行用例
        // 课程授课教师固定为 2L，便于配合管理员/教师身份用例
        course.setTeacherId(2L); // 设置课程授课教师 ID 为 2（与身份用例对齐）
    }

    @AfterEach // JUnit5 生命周期注解：每个测试方法执行后运行
    void tearDown() { // 每个用例执行后的清理
        UserContext.clear(); // 清理全局用户上下文，避免身份残留污染
    }

    private UserContext.CurrentUser adminUser() { // 工具方法：构造管理员身份对象
        UserContext.CurrentUser user = new UserContext.CurrentUser(); // 创建当前用户对象
        user.setUserId(1L); // 设置用户 ID 为 1
        user.setRoleType("ADMIN"); // 设置角色为管理员
        return user; // 返回管理员身份
    }

    private Course buildCourse(Long id) { // 工具方法：构造课程实体
        Course c = new Course(); // 创建课程对象
        c.setId(id); // 设置课程 ID
        c.setCourseCode("CS103"); // 设置课程编号
        c.setCourseName("Web前端开发"); // 设置课程名称
        return c; // 返回课程实体
    }

    private StudentCourse buildSc(Long id, Long studentId) { // 工具方法：构造选课关系实体
        StudentCourse sc = new StudentCourse(); // 创建选课关系对象
        sc.setId(id); // 设置选课记录 ID
        sc.setCourseId(1L); // 设置课程 ID 为 1（属于同一门课）
        sc.setStudentId(studentId); // 设置学生 ID
        sc.setMark("NORMAL"); // 设置成绩标记默认为 NORMAL（正常）
        return sc; // 返回选课关系实体
    }

    private Student buildStudent(Long id, String no, String name) { // 工具方法：构造学生实体
        Student s = new Student(); // 创建学生对象
        s.setId(id); // 设置学生 ID
        s.setStudentNo(no); // 设置学号
        s.setRealName(name); // 设置姓名
        return s; // 返回学生实体
    }

    /** 用 EasyExcel 把成绩行数组写成 multipart 上传文件 */
    private MockMultipartFile gradeFile(GradeExcelRow... rows) { // 工具方法：把成绩行数据生成 Excel 并包装为上传文件
        ByteArrayOutputStream out = new ByteArrayOutputStream(); // 创建内存输出流，用于承载 Excel 字节
        EasyExcel.write(out, GradeExcelRow.class).sheet("Sheet1").doWrite(Arrays.asList(rows)); // 用 EasyExcel 把成绩行写入内存 Sheet1
        return new MockMultipartFile("file", "grades.xlsx", // 构造 Mock 文件上传对象：表单字段 file、文件名 grades.xlsx
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray()); // 指定 xlsx MIME 类型并携带字节内容
    }

    /** 构造一行成绩导入数据：学号 + 分数（可空）+ 标记 */
    private GradeExcelRow gradeRow(String no, String score, String mark) { // 工具方法：构造一行成绩导入数据
        GradeExcelRow row = new GradeExcelRow(); // 创建成绩行对象
        row.setStudentNo(no); // 设置学号
        if (score != null) row.setScore(new BigDecimal(score)); // 分数非空时才设置分数（为空则留空，用于测试缺分场景）
        row.setMark(mark); // 设置成绩标记（NORMAL/DEFER 等）
        return row; // 返回成绩行数据
    }

    /** 默认选中课学生（2人）的 mock 装配 */
    private void mockCourseWithEnrolledStudents() { // 工具方法：统一打桩"课程 1 + 两名选课学生 + 两名学生信息"
        when(courseMapper.selectById(1L)).thenReturn(course); // 打桩：按 ID 1 查到课程
        when(studentCourseMapper.selectList(any())).thenReturn(Arrays.asList(sc1, sc2)); // 打桩：查出该课程 2 条选课记录
        when(studentMapper.selectBatchIds(any())).thenReturn(Arrays.asList(stu1, stu2)); // 打桩：按 ID 批量查出 2 名学生
    }

    // ==================== 成绩导入 ====================

    @Test // 标记这是一个测试方法
    @DisplayName("成绩导入：NORMAL 写分数，DEFER 清空分数") // 定义测试显示名称
    /** 验证场景：成绩导入时 NORMAL 标记写入分数，DEFER 标记清空分数，两条选课记录均被更新 */
    void importGrades_shouldApplyScoreAndMark() { // 用例1：NORMAL 写分数、DEFER 清空分数
        mockCourseWithEnrolledStudents(); // 装配课程与选课学生的 mock 数据
        CourseGradeAudit audit = new CourseGradeAudit(); // 创建成绩审核记录
        audit.setStatus("DRAFT"); // 设置审核状态为 DRAFT（草稿，允许导入成绩）
        when(auditMapper.selectOne(any())).thenReturn(audit); // 打桩：查出该课程审核状态为草稿（可通过导入检查）

        int count = gradeService.importGrades(1L, gradeFile( // 调用被测方法：向课程 1 导入成绩
                gradeRow("S20230001", "88", "NORMAL"), // 学生甲：分数 88，标记 NORMAL（正常计分）
                gradeRow("S20230002", null, "DEFER"))); // 学生乙：分数为空，标记 DEFER（缓考，清空分数）

        assertThat(count).isEqualTo(2); // 断言：成功处理 2 条成绩记录
        assertThat(sc1.getScore()).isEqualByComparingTo("88"); // 断言：学生甲分数被写入 88
        assertThat(sc1.getMark()).isEqualTo("NORMAL"); // 断言：学生甲标记为 NORMAL
        // DEFER 标记的记录分数被清空
        assertThat(sc2.getScore()).isNull(); // 断言：学生乙分数被清空（DEFER 场景分数为空）
        assertThat(sc2.getMark()).isEqualTo("DEFER"); // 断言：学生乙标记为 DEFER
        verify(studentCourseMapper, times(2)).updateById(any(StudentCourse.class)); // 断言：两条选课记录均被 updateById 更新
    }

    @Test
    @DisplayName("成绩导入：未选修该课程的学号报错")
    /** 验证场景：导入的学号不在该课程选课名单中时抛业务异常 */
    void importGrades_shouldRejectStudentNotEnrolled() { // 用例2：未选修该课程的学生学号被拒绝
        mockCourseWithEnrolledStudents(); // 装配课程与选课学生的 mock 数据
        CourseGradeAudit audit = new CourseGradeAudit(); // 创建成绩审核记录
        audit.setStatus("DRAFT"); // 设置审核状态为草稿
        when(auditMapper.selectOne(any())).thenReturn(audit); // 打桩：审核状态为草稿（先通过阶段检查，走到学号匹配检查）

        assertThatThrownBy(() -> gradeService.importGrades(1L, gradeFile( // 断言：导入不在选课名单中的学号时抛出异常
                gradeRow("S20230099", "88", "NORMAL")))) // 学号 S20230099 不在该课程选课名单中
                .isInstanceOf(BusinessException.class) // 断言：异常类型为 BusinessException
                .hasMessageContaining("未选修本课程"); // 断言：异常信息提示未选修本课程
    }

    @Test
    @DisplayName("成绩导入：非法标记报错")
    /** 验证场景：成绩标记不是 NORMAL/DEFER（如 FOO）时抛业务异常 */
    void importGrades_shouldRejectInvalidMark() { // 用例3：非法的成绩标记被拒绝
        mockCourseWithEnrolledStudents(); // 装配课程与选课学生的 mock 数据
        CourseGradeAudit audit = new CourseGradeAudit(); // 创建成绩审核记录
        audit.setStatus("DRAFT"); // 设置审核状态为草稿
        when(auditMapper.selectOne(any())).thenReturn(audit); // 打桩：审核状态为草稿

        assertThatThrownBy(() -> gradeService.importGrades(1L, gradeFile( // 断言：导入非法标记 FOO 的成绩时抛出异常
                gradeRow("S20230001", "88", "FOO")))) // 标记 FOO 不在 NORMAL/DEFER 允许范围内
                .isInstanceOf(BusinessException.class) // 断言：异常类型为 BusinessException
                .hasMessageContaining("标记 FOO 非法"); // 断言：异常信息提示标记非法
    }

    @Test
    @DisplayName("成绩导入：分数超出 0-100 报错")
    /** 验证场景：成绩分数超出 0-100 范围时抛业务异常 */
    void importGrades_shouldRejectScoreOutOfRange() { // 用例4：分数超出 0-100 被拒绝
        mockCourseWithEnrolledStudents(); // 装配课程与选课学生的 mock 数据
        CourseGradeAudit audit = new CourseGradeAudit(); // 创建成绩审核记录
        audit.setStatus("DRAFT"); // 设置审核状态为草稿
        when(auditMapper.selectOne(any())).thenReturn(audit); // 打桩：审核状态为草稿

        assertThatThrownBy(() -> gradeService.importGrades(1L, gradeFile( // 断言：导入超范围分数 150 时抛出异常
                gradeRow("S20230001", "150", "NORMAL")))) // 分数 150 超出 0-100 合法范围
                .isInstanceOf(BusinessException.class) // 断言：异常类型为 BusinessException
                .hasMessageContaining("成绩须在 0-100 之间"); // 断言：异常信息提示成绩须在 0-100 之间
    }

    @Test
    @DisplayName("成绩导入：正常标记但缺分数报错")
    /** 验证场景：标记为 NORMAL 但未填写分数时抛业务异常 */
    void importGrades_shouldRequireScoreWhenNormal() { // 用例5：NORMAL 标记但缺分数被拒绝
        mockCourseWithEnrolledStudents(); // 装配课程与选课学生的 mock 数据
        CourseGradeAudit audit = new CourseGradeAudit(); // 创建成绩审核记录
        audit.setStatus("DRAFT"); // 设置审核状态为草稿
        when(auditMapper.selectOne(any())).thenReturn(audit); // 打桩：审核状态为草稿

        assertThatThrownBy(() -> gradeService.importGrades(1L, gradeFile( // 断言：NORMAL 标记但分数为空时抛出异常
                gradeRow("S20230001", null, "NORMAL")))) // 标记为 NORMAL（正常计分）却未填分数
                .isInstanceOf(BusinessException.class) // 断言：异常类型为 BusinessException
                .hasMessageContaining("必须填写总评成绩"); // 断言：异常信息提示必须填写总评成绩
    }

    @Test
    @DisplayName("成绩导入：非 DRAFT 阶段拒绝导入")
    /** 验证场景：成绩审核状态不是 DRAFT（如已提交）时拒绝导入 */
    void importGrades_shouldRejectWhenLocked() { // 用例6：审核状态非草稿时拒绝导入
        when(courseMapper.selectById(1L)).thenReturn(course); // 打桩：按 ID 1 查到课程（此处只打课程桩，模拟已进入阶段判断）
        CourseGradeAudit audit = new CourseGradeAudit(); // 创建成绩审核记录
        audit.setStatus("SUBMITTED"); // 设置审核状态为已提交（SUBMITTED，不可再导入）
        when(auditMapper.selectOne(any())).thenReturn(audit); // 打桩：查出审核状态为已提交

        assertThatThrownBy(() -> gradeService.importGrades(1L, gradeFile( // 断言：审核已提交时导入成绩抛出异常
                gradeRow("S20230001", "88", "NORMAL")))) // 尝试导入一条正常成绩
                .isInstanceOf(BusinessException.class) // 断言：异常类型为 BusinessException
                .hasMessageContaining("当前不可导入"); // 断言：异常信息提示当前不可导入
    }

    // ==================== 模板 / 导出 ====================

    @Test
    @DisplayName("成绩模板：预填选课学生，分数清空标记为 NORMAL")
    /** 验证场景：下载成绩模板时预填该课程选课学生，分数留空、标记默认 NORMAL */
    void downloadGradeTemplate_shouldPreFillStudents() { // 用例7：下载成绩模板时预填选课学生
        mockCourseWithEnrolledStudents(); // 装配课程与选课学生的 mock 数据
        MockHttpServletResponse response = new MockHttpServletResponse(); // 创建 Mock 响应对象

        gradeService.downloadGradeTemplate(1L, response); // 调用被测方法：下载课程 1 的成绩模板

        List<GradeExcelRow> rows = EasyExcel.read(new ByteArrayInputStream(response.getContentAsByteArray())) // 回读响应体中的 Excel 字节流
                .head(GradeExcelRow.class).sheet().doReadSync(); // 指定表头类型并同步读取所有行
        assertThat(rows).hasSize(2); // 断言：模板预填了 2 名学生
        assertThat(rows.get(0).getStudentNo()).isEqualTo("S20230001"); // 断言：第一行学号为 S20230001
        assertThat(rows.get(0).getScore()).isNull(); // 断言：第一行分数为空（待填）
        assertThat(rows.get(0).getMark()).isEqualTo("NORMAL"); // 断言：第一行标记默认为 NORMAL
        assertThat(rows.get(1).getStudentNo()).isEqualTo("S20230002"); // 断言：第二行学号为 S20230002
    }

    @Test
    @DisplayName("成绩导出：包含当前已录成绩")
    /** 验证场景：导出课程学生成绩时包含当前已录入的分数与标记 */
    void exportCourseStudents_shouldContainScores() { // 用例8：导出成绩时包含已录入分数
        sc1.setScore(new BigDecimal("88")); // 给学生甲设成绩 88
        sc2.setScore(new BigDecimal("45")); // 给学生乙设成绩 45
        mockCourseWithEnrolledStudents(); // 装配课程与选课学生的 mock 数据（此时选课记录已带分数）
        MockHttpServletResponse response = new MockHttpServletResponse(); // 创建 Mock 响应对象

        gradeService.exportCourseStudents(1L, response); // 调用被测方法：导出课程 1 的学生成绩

        List<GradeExcelRow> rows = EasyExcel.read(new ByteArrayInputStream(response.getContentAsByteArray())) // 回读响应体中的 Excel 字节流
                .head(GradeExcelRow.class).sheet().doReadSync(); // 指定表头类型并同步读取所有行
        assertThat(rows).hasSize(2); // 断言：导出了 2 名学生
        assertThat(rows.get(0).getScore()).isEqualByComparingTo("88"); // 断言：第一行分数为 88
        assertThat(rows.get(1).getScore()).isEqualByComparingTo("45"); // 断言：第二行分数为 45
        assertThat(rows.get(1).getMark()).isEqualTo("NORMAL"); // 断言：第二行标记为 NORMAL
    }

    // ==================== 成绩发布自动通知 ====================

    @Test
    @DisplayName("成绩发布：自动发送 GRADE_PUBLISH 通知给该课程选课学生")
    /** 验证场景：成绩发布时自动向该课程全部选课学生发送 GRADE_PUBLISH 通知，并更新审核状态 */
    void publish_shouldNotifyEnrolledStudents() { // 用例9：成绩发布时自动通知选课学生
        course.setId(1L); // 确保课程 ID 为 1
        course.setCourseName("Web前端开发"); // 确保课程名称为 Web前端开发
        when(courseMapper.selectById(1L)).thenReturn(course); // 打桩：按 ID 1 查到课程
        CourseGradeAudit audit = new CourseGradeAudit(); // 创建成绩审核记录
        audit.setCourseId(1L); // 设置审核记录所属课程 ID
        audit.setStatus("APPROVED"); // 设置审核状态为已通过（APPROVED，允许发布）
        when(auditMapper.selectOne(any())).thenReturn(audit); // 打桩：查出审核状态为已通过
        when(studentCourseMapper.selectList(any())).thenReturn(Arrays.asList(sc1, sc2)); // 打桩：该课程有 2 名选课学生
        // recalcStudentCredits：无其他已发布课程 → 重置学分/GPA（sc1.studentId=1L, sc2.studentId=2L）
        when(studentMapper.selectById(1L)).thenReturn(stu1); // 打桩：按 ID 1 查到学生甲（用于重算学分/GPA）
        when(studentMapper.selectById(2L)).thenReturn(stu2); // 打桩：按 ID 2 查到学生乙（用于重算学分/GPA）
        when(auditMapper.selectList(any())).thenReturn(java.util.Collections.emptyList()); // 打桩：学生没有其他已发布课程（学分按本课程重算）

        gradeService.publish(1L); // 调用被测方法：发布课程 1 的成绩

        verify(notificationService).sendSystem(eq("GRADE_PUBLISH"), eq("成绩已发布"), any(), any()); // 断言：发送了 GRADE_PUBLISH 类型成绩发布通知
        verify(auditMapper).updateById(any(CourseGradeAudit.class)); // 断言：审核记录状态被更新（发布后置为已发布等）
    }

    @Test
    @DisplayName("成绩发布：未审核通过（非 APPROVED）不可发布，不发送通知")
    /** 验证场景：审核状态非 APPROVED 时不可发布成绩，且不发送任何通知 */
    void publish_shouldRejectWhenNotApproved() { // 用例10：审核未通过时不可发布成绩
        course.setId(1L); // 确保课程 ID 为 1
        when(courseMapper.selectById(1L)).thenReturn(course); // 打桩：按 ID 1 查到课程
        CourseGradeAudit audit = new CourseGradeAudit(); // 创建成绩审核记录
        audit.setStatus("SUBMITTED"); // 设置审核状态为已提交（未通过，不可发布）
        when(auditMapper.selectOne(any())).thenReturn(audit); // 打桩：查出审核状态为已提交

        assertThatThrownBy(() -> gradeService.publish(1L)) // 断言：发布未审核通过的成绩时抛出异常
                .isInstanceOf(BusinessException.class) // 断言：异常类型为 BusinessException
                .hasMessageContaining("仅审核通过的课程可以发布"); // 断言：异常信息提示仅审核通过的课程可发布
    }

    // ==================== 成绩单（未发布成绩不泄露） ====================

    /** 装配"我的成绩单"查询：一条选课记录 + 对应课程 + 指定审核状态 */
    private void mockMyGrades(StudentCourse sc, String auditStatus) { // 工具方法：统一打桩"我的成绩单"查询所需数据
        when(studentCourseMapper.selectList(any())).thenReturn(List.of(sc)); // 打桩：当前学生有 1 条选课记录
        when(courseMapper.selectBatchIds(any())).thenReturn(List.of(course)); // 打桩：按 ID 批量查出该课程
        CourseGradeAudit audit = new CourseGradeAudit(); // 创建成绩审核记录
        audit.setCourseId(course.getId()); // 设置审核记录所属课程 ID
        audit.setStatus(auditStatus); // 设置审核状态（由调用方传入，用于测试发布/未发布两种情况）
        when(auditMapper.selectList(any())).thenReturn(List.of(audit)); // 打桩：查出该课程的审核状态
    }

    @Test
    @DisplayName("成绩单：已发布课程正常返回分数与绩点")
    /** 验证场景：已发布课程的成绩单正常返回分数、绩点与是否通过 */
    void myGrades_shouldReturnPublishedScore() { // 用例11：已发布课程的成绩单返回分数与绩点
        StudentCourse sc = buildSc(10L, 1L); // 构造一条选课记录（学生 1L）
        sc.setScore(new BigDecimal("88")); // 设置成绩 88 分
        sc.setMark("NORMAL"); // 设置标记为 NORMAL
        mockMyGrades(sc, "PUBLISHED"); // 装配数据：审核状态为已发布（PUBLISHED）

        List<GradeVO> result = gradeService.myGrades(1L); // 调用被测方法：查询学生 1L 的成绩单

        assertThat(result).hasSize(1); // 断言：成绩单有 1 条记录
        assertThat(result.get(0).getScore()).isEqualByComparingTo("88"); // 断言：分数为 88
        // 88 分对应绩点 3.0，判定为通过
        assertThat(result.get(0).getGradePoint()).isEqualByComparingTo("3.0"); // 断言：88 分对应绩点为 3.0
        assertThat(result.get(0).getPassed()).isTrue(); // 断言：该课程判定为通过
    }

    @Test
    @DisplayName("成绩单：未发布课程分数/绩点置空，不向学生泄露")
    /** 验证场景：未发布课程的成绩单隐藏分数与绩点（置空），防止提前泄露 */
    void myGrades_shouldMaskScoreWhenNotPublished() { // 用例12：未发布课程的成绩单隐藏分数与绩点
        StudentCourse sc = buildSc(10L, 1L); // 构造一条选课记录（学生 1L）
        sc.setScore(new BigDecimal("88")); // 设置成绩 88 分（库中已录入）
        sc.setMark("NORMAL"); // 设置标记为 NORMAL
        mockMyGrades(sc, "SUBMITTED"); // 装配数据：审核状态为已提交（成绩尚未发布）

        List<GradeVO> result = gradeService.myGrades(1L); // 调用被测方法：查询学生 1L 的成绩单

        assertThat(result).hasSize(1); // 断言：成绩单有 1 条记录
        assertThat(result.get(0).getScore()).isNull(); // 断言：分数被置空（未发布不泄露）
        assertThat(result.get(0).getGradePoint()).isNull(); // 断言：绩点被置空（未发布不泄露）
        assertThat(result.get(0).getPassed()).isFalse(); // 断言：通过状态为 false（未发布不判定）
    }
}
