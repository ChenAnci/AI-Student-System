package com.example.sms.service; // 声明包名：本测试类位于 service 包，与被测 AccountService 同包

// import 区：引入 EasyExcel（Excel 读写）、业务异常、实体类、Excel 行模型与 Mapper 接口，用于准备测试数据与断言
import com.alibaba.excel.EasyExcel;
import com.example.sms.common.BusinessException;
import com.example.sms.entity.Staff;
import com.example.sms.entity.Student;
import com.example.sms.excel.StaffExcelRow;
import com.example.sms.excel.StudentExcelRow;
import com.example.sms.mapper.StaffMapper;
import com.example.sms.mapper.StudentMapper;
import com.example.sms.util.UserContext;
import com.example.sms.vo.AccountImportResultVO;

// import 区：引入 JUnit5 生命周期注解（@BeforeEach/@AfterEach）与测试注解
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

// import 区：引入 Mockito 核心注解与工具类（@Mock 造桩、@InjectMocks 注入、ArgumentCaptor 捕获入参）
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// import 区：引入 Spring 的 Mock 响应对象与 Mock 文件上传对象（模拟浏览器导出下载与文件上传）
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;

// import 区：引入 BCrypt 密码加密器（用于验证密码密文是否正确匹配）
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

// import 区：引入 IO 流工具类与集合类（内存中生成/读取 Excel 字节流）
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

// import 区：静态导入 AssertJ 断言与 Mockito 打桩/校验方法（使测试代码更简洁）
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 账号管理导入导出单元测试（Mockito，不依赖数据库）
 */
@ExtendWith(MockitoExtension.class) // 启用 Mockito 的 JUnit5 扩展：自动初始化 @Mock/@InjectMocks，并严格校验打桩使用情况
class AccountServiceTest { // 测试类声明：全部用例使用 Mock 的 Mapper，不连接真实数据库

    @Mock // 声明学生 Mapper 的 Mock 对象：拦截所有 studentMapper 调用并返回预设结果
    private StudentMapper studentMapper;

    @Mock // 声明教职工 Mapper 的 Mock 对象：同上，用于教职工相关的数据访问
    private StaffMapper staffMapper;

    @InjectMocks // 把上面两个 Mock 自动注入到被测的 AccountService 实例中（按类型匹配构造器/字段）
    private AccountService accountService;

    @BeforeEach // JUnit5 生命周期注解：每个测试方法执行前都会运行一次
    void setUp() { // 每个用例执行前的公共初始化
        // 默认以管理员身份执行用例；需要其他身份的方法会先 clear 再自行切换
        UserContext.set(adminUser()); // 向全局用户上下文写入管理员身份（默认身份，保证用例可操作管理功能）
    }

    @AfterEach // JUnit5 生命周期注解：每个测试方法执行后都会运行一次
    void tearDown() { // 每个用例执行后的清理
        UserContext.clear(); // 清理全局用户上下文，避免身份信息在用例间相互污染
    }

    private UserContext.CurrentUser adminUser() { // 工具方法：构造一个管理员身份对象
        UserContext.CurrentUser user = new UserContext.CurrentUser(); // 创建当前用户对象
        user.setUserId(1L); // 设置用户 ID 为 1
        user.setRoleType("ADMIN"); // 设置角色为管理员（教学秘书）
        return user; // 返回构造好的管理员身份
    }

    private UserContext.CurrentUser teacherUser() { // 工具方法：构造一个教师身份对象
        UserContext.CurrentUser user = new UserContext.CurrentUser(); // 创建当前用户对象
        user.setUserId(2L); // 设置用户 ID 为 2
        user.setRoleType("TEACHER"); // 设置角色为教师
        return user; // 返回构造好的教师身份
    }

    private UserContext.CurrentUser studentUser() { // 工具方法：构造一个学生身份对象
        UserContext.CurrentUser user = new UserContext.CurrentUser(); // 创建当前用户对象
        user.setUserId(64L); // 设置用户 ID 为 64（与后面 studentWithPassword 的 id 对应）
        user.setRoleType("STUDENT"); // 设置角色为学生
        return user; // 返回构造好的学生身份
    }

    private MockMultipartFile studentFile(StudentExcelRow... rows) { // 工具方法：把学生 Excel 行数据打包成上传文件
        return file(rows, StudentExcelRow.class, "students.xlsx"); // 调用通用方法，生成名为 students.xlsx 的 Mock 上传文件
    }

    private MockMultipartFile staffFile(StaffExcelRow... rows) { // 工具方法：把教职工 Excel 行数据打包成上传文件
        return file(rows, StaffExcelRow.class, "staffs.xlsx"); // 调用通用方法，生成名为 staffs.xlsx 的 Mock 上传文件
    }

    private <T> MockMultipartFile file(T[] rows, Class<T> clazz, String filename) { // 通用工具方法：用 EasyExcel 把行数据写入内存并包装为上传文件
        ByteArrayOutputStream out = new ByteArrayOutputStream(); // 创建内存输出流，用于承载 Excel 字节内容
        EasyExcel.write(out, clazz).sheet("Sheet1").doWrite(Arrays.asList(rows)); // 用 EasyExcel 把行数据按指定类型写入内存中的 Sheet1
        return new MockMultipartFile("file", filename, // 构造 Mock 文件上传对象：表单字段名 file、文件名 filename
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray()); // 指定 MIME 类型为 xlsx 并携带 Excel 字节内容
    }

    private StudentExcelRow studentRow(String name, String no) { // 工具方法：构造一行学生导入数据（姓名 + 学号）
        StudentExcelRow row = new StudentExcelRow(); // 创建学生 Excel 行对象
        row.setRealName(name); // 设置真实姓名
        row.setStudentNo(no); // 设置学号（可为 null，用于测试自动生成学号）
        return row; // 返回该行数据
    }

    private StaffExcelRow staffRow(String name, String no, String role) { // 工具方法：构造一行教职工导入数据（姓名 + 工号 + 角色）
        StaffExcelRow row = new StaffExcelRow(); // 创建教职工 Excel 行对象
        row.setRealName(name); // 设置真实姓名
        row.setStaffNo(no); // 设置工号（可为 null，用于测试自动生成工号）
        row.setRoleType(role); // 设置角色类型（可为 null，用于测试默认角色）
        return row; // 返回该行数据
    }

    private Student student(String no, String name) { // 工具方法：构造学生实体（模拟数据库查出的记录）
        Student s = new Student(); // 创建学生实体对象
        s.setStudentNo(no); // 设置学号
        s.setRealName(name); // 设置姓名
        return s; // 返回该学生实体
    }

    // ==================== 学生导入 ====================

    @Test // 标记这是一个测试方法
    @DisplayName("学生导入：学号留空自动生成并递增") // 定义测试的显示名称（测试报告中展示）
    /** 验证场景：学生导入时学号留空，自动按当前最大学号递增生成（S20230004、S20230005），且默认状态/学分/初始密码正确 */
    void importStudents_shouldAutoGenerateStudentNo() { // 用例：学号留空时自动生成并递增
        // 连续两次查询最大学号：首次为 S20230003，插入后第二次应看到 S20230004
        when(studentMapper.selectList(any())) // 对"查询学生列表"打桩：第一次调用返回 [S20230003 王五]
                .thenReturn(Collections.singletonList(student("S20230003", "王五"))) // 第二次调用返回 [S20230004 测试学生甲]（模拟插入后的最新记录）
                .thenReturn(Collections.singletonList(student("S20230004", "测试学生甲")));

        List<AccountImportResultVO> result = accountService.importStudents(studentFile( // 调用被测方法：导入两名学号留空的学生（触发自动生成学号）
                studentRow("测试学生甲", null), // 第一条数据：测试学生甲，学号留空
                studentRow("测试学生乙", null))); // 第二条数据：测试学生乙，学号留空
        assertThat(result).hasSize(2); // 断言：返回结果共 2 条
        assertThat(result.get(0).getUserNo()).isEqualTo("S20230004"); // 断言：第一条结果学号为自动生成的首个值 S20230004
        assertThat(result.get(0).getInitPassword()).isNotBlank(); // 断言：自动生成的初始密码不为空
        ArgumentCaptor<Student> captor = ArgumentCaptor.forClass(Student.class); // 创建参数捕获器，用来捕获 insert 方法被调用时传入的学生对象
        verify(studentMapper, times(2)).insert(captor.capture()); // 断言：insert 恰好被调用 2 次，并捕获每次插入的学生对象
        List<Student> inserted = captor.getAllValues(); // 取出两次插入的学生对象列表
        assertThat(inserted.get(0).getStudentNo()).isEqualTo("S20230004"); // 断言：第一条插入的学号为 S20230004（最大学号 +1）
        assertThat(inserted.get(1).getStudentNo()).isEqualTo("S20230005"); // 断言：第二条插入的学号为 S20230005（继续递增，不重复）
        assertThat(inserted.get(0).getStatus()).isEqualTo("ENABLED"); // 断言：新导入学生默认状态为启用（ENABLED）
        assertThat(inserted.get(0).getRequiredCredits()).isEqualByComparingTo("160.00"); // 断言：默认要求学分 160（字符串比较数值相等）
        assertThat(inserted.get(0).getTotalEarnedCredits()).isEqualByComparingTo("0"); // 断言：默认已获得学分为 0
        assertThat(inserted.get(0).getPasswordHash()).isNotBlank(); // 断言：初始密码已加密存储，密文非空
    }

    @Test
    @DisplayName("学生导入：指定学号且不冲突时按原学号入库")
    /** 验证场景：学生导入时指定学号且库中不存在该学号，则按原学号与姓名入库 */
    void importStudents_shouldKeepProvidedStudentNo() { // 用例：指定学号且不冲突时按原学号入库
        when(studentMapper.selectCount(any())).thenReturn(0L); // 打桩：查询学号数量返回 0（表示该学号库中不存在）

        List<AccountImportResultVO> result = accountService.importStudents(studentFile(studentRow("张三", "S20230099"))); // 调用被测方法：导入指定学号 S20230099 的学生张三

        assertThat(result).hasSize(1); // 断言：结果只有 1 条
        assertThat(result.get(0).getUserNo()).isEqualTo("S20230099"); // 断言：返回的学号保持原值不变
        ArgumentCaptor<Student> captor = ArgumentCaptor.forClass(Student.class); // 创建参数捕获器，捕获插入的学生对象
        verify(studentMapper).insert(captor.capture()); // 断言：insert 被调用 1 次，并捕获入参
        assertThat(captor.getValue().getStudentNo()).isEqualTo("S20230099"); // 断言：实际入库学号与导入的一致
        assertThat(captor.getValue().getRealName()).isEqualTo("张三"); // 断言：实际入库姓名与导入的一致
    }

    @Test
    @DisplayName("学生导入：文件内学号重复报错且不落库")
    /** 验证场景：同一导入文件内学号重复时抛业务异常，且任何记录都不落库 */
    void importStudents_shouldRejectDuplicateNoInFile() { // 用例：文件内学号重复时拒绝导入
        when(studentMapper.selectCount(any())).thenReturn(0L); // 打桩：学号数量返回 0（先通过"库中不存在"校验，走到文件内查重逻辑）

        assertThatThrownBy(() -> accountService.importStudents(studentFile( // 断言：导入"同文件内学号重复"的数据时抛出异常
                studentRow("张三", "S20230099"), // 第一条数据：张三，学号 S20230099
                studentRow("李四", "S20230099")))) // 第二条数据：李四，学号同样是 S20230099（重复）
                .isInstanceOf(BusinessException.class) // 断言：异常类型为业务异常 BusinessException
                .hasMessageContaining("在文件中重复"); // 断言：异常信息包含"在文件中重复"提示

        verify(studentMapper, never()).insert(any()); // 断言：insert 从未被调用（整批数据都不落库）
    }

    @Test
    @DisplayName("学生导入：学号已存在于数据库时报错")
    /** 验证场景：学号已存在于数据库时抛业务异常，且不执行插入 */
    void importStudents_shouldRejectExistingNo() { // 用例：学号在数据库中已存在时拒绝导入
        when(studentMapper.selectCount(any())).thenReturn(1L); // 打桩：查询学号数量返回 1（表示库中已存在该学号）

        assertThatThrownBy(() -> accountService.importStudents( // 断言：导入已存在学号的数据时抛出异常
                studentFile(studentRow("张三", "S20230001")))) // 导入学号为 S20230001 的学生（该学号已存在）
                .isInstanceOf(BusinessException.class) // 断言：异常类型为 BusinessException
                .hasMessageContaining("已存在"); // 断言：异常信息包含"已存在"提示

        verify(studentMapper, never()).insert(any()); // 断言：insert 从未被调用（不重复落库）
    }

    @Test
    @DisplayName("学生导入：姓名为空报错")
    /** 验证场景：学生姓名为空（空白字符）时抛业务异常 */
    void importStudents_shouldRejectBlankName() { // 用例：姓名为空白字符时拒绝导入
        assertThatThrownBy(() -> accountService.importStudents( // 断言：导入姓名为空白字符的学生数据时抛出异常
                studentFile(studentRow("   ", "S20230099")))) // 姓名是三个空格（视为空）
                .isInstanceOf(BusinessException.class) // 断言：异常类型为 BusinessException
                .hasMessageContaining("姓名不能为空"); // 断言：异常信息包含"姓名不能为空"提示
    }

    @Test
    @DisplayName("学生导入：教师无权限（403）")
    /** 验证场景：非管理员（教师身份）执行学生导入被拒绝，提示仅教学秘书可操作 */
    void importStudents_shouldRejectNonAdmin() { // 用例：非管理员执行学生导入被拒绝
        UserContext.clear(); // 清空默认的管理员登录上下文
        UserContext.set(teacherUser()); // 切换为教师身份登录（无导入权限）

        assertThatThrownBy(() -> accountService.importStudents( // 断言：教师身份执行导入时抛出异常
                studentFile(studentRow("张三", null)))) // 导入一条学生数据（学号留空）
                .isInstanceOf(BusinessException.class) // 断言：异常类型为 BusinessException
                .hasMessageContaining("仅教学秘书可操作"); // 断言：异常信息提示只有教学秘书（管理员）可操作
    }

    // ==================== 教职工导入 ====================

    @Test
    @DisplayName("教职工导入：角色留空默认教师，工号自动生成")
    /** 验证场景：教职工导入时角色留空默认 TEACHER，工号按当前最大学号自动递增（T1003），默认启用 */
    void importStaffs_shouldDefaultRoleAndAutoGenerateNo() { // 用例：教职工角色留空默认教师、工号自动生成
        Staff max = new Staff(); // 创建教职工实体，用于模拟库中的最大工号记录
        max.setStaffNo("T1002"); // 设置当前最大学号为 T1002
        when(staffMapper.selectList(any())).thenReturn(Collections.singletonList(max)); // 打桩：查询教职工列表返回 [T1002]

        List<AccountImportResultVO> result = accountService.importStaffs(staffFile(staffRow("测试教师甲", null, null))); // 调用被测方法：导入一名工号、角色都留空的教职工

        assertThat(result).hasSize(1); // 断言：结果只有 1 条
        assertThat(result.get(0).getUserNo()).isEqualTo("T1003"); // 断言：自动生成工号为 T1003（最大学号 +1）
        assertThat(result.get(0).getInitPassword()).isNotBlank(); // 断言：自动生成的初始密码非空
        ArgumentCaptor<Staff> captor = ArgumentCaptor.forClass(Staff.class); // 创建参数捕获器，捕获插入的教职工对象
        verify(staffMapper).insert(captor.capture()); // 断言：insert 被调用 1 次，并捕获入参
        assertThat(captor.getValue().getStaffNo()).isEqualTo("T1003"); // 断言：实际入库工号为 T1003
        assertThat(captor.getValue().getRoleType()).isEqualTo("TEACHER"); // 断言：角色留空时默认填充为 TEACHER（教师）
        assertThat(captor.getValue().getStatus()).isEqualTo("ENABLED"); // 断言：默认状态为启用（ENABLED）
    }

    @Test
    @DisplayName("教职工导入：非法角色报错")
    /** 验证场景：教职工导入时填入了非法的角色值（如 BOSS）被拒绝 */
    void importStaffs_shouldRejectInvalidRole() { // 用例：非法角色值被拒绝
        assertThatThrownBy(() -> accountService.importStaffs( // 断言：导入角色为非法的 BOSS 时抛出异常
                staffFile(staffRow("测试教师甲", null, "BOSS")))) // 导入一名角色填 BOSS 的教职工
                .isInstanceOf(BusinessException.class) // 断言：异常类型为 BusinessException
                .hasMessageContaining("角色仅支持 TEACHER"); // 断言：异常信息提示角色仅支持 TEACHER
    }

    @Test
    @DisplayName("教职工导入：禁止通过导入创建管理员")
    /** 验证场景：导入文件中出现 ADMIN 角色时被拒绝，防止通过导入提权 */
    void importStaffs_shouldRejectAdminRole() { // 用例：禁止通过导入创建管理员（防止越权提权）
        assertThatThrownBy(() -> accountService.importStaffs( // 断言：导入角色为 ADMIN 的数据时抛出异常
                staffFile(staffRow("管理员甲", null, "ADMIN")))) // 试图通过导入创建一个管理员
                .isInstanceOf(BusinessException.class) // 断言：异常类型为 BusinessException
                .hasMessageContaining("角色仅支持 TEACHER"); // 断言：异常信息提示角色仅支持 TEACHER（管理员必须走专门流程）
    }

    @Test
    @DisplayName("教职工导入：同批次多个空工号依次递增不重复")
    /** 验证场景：同批次多个空工号依次递增生成（T1003、T1004），互不重复 */
    void importStaffs_shouldGenerateDistinctNosInBatch() { // 用例：同批次多个空工号依次递增且不重复
        Staff max = new Staff(); // 创建教职工实体，模拟库中的最大工号记录
        max.setStaffNo("T1002"); // 设置当前最大学号为 T1002
        when(staffMapper.selectList(any())).thenReturn(Collections.singletonList(max)); // 打桩：查询教职工列表返回 [T1002]

        List<AccountImportResultVO> result = accountService.importStaffs(staffFile( // 调用被测方法：一次导入两名工号留空的教职工
                staffRow("测试教师甲", null, null), // 第一名：工号留空
                staffRow("测试教师乙", null, null))); // 第二名：工号留空

        assertThat(result).hasSize(2); // 断言：结果共 2 条
        assertThat(result.get(0).getUserNo()).isEqualTo("T1003"); // 断言：第一个工号为 T1003
        assertThat(result.get(1).getUserNo()).isEqualTo("T1004"); // 断言：第二个工号为 T1004（在 T1003 基础上继续递增，不重复）
        verify(staffMapper, times(2)).insert(any()); // 断言：insert 恰好被调用 2 次（两名教职工均入库）
    }

    @Test
    @DisplayName("教职工导入：工号已存在报错")
    /** 验证场景：教职工工号已存在于数据库时抛业务异常 */
    void importStaffs_shouldRejectExistingNo() { // 用例：工号已存在时拒绝导入
        when(staffMapper.selectCount(any())).thenReturn(1L); // 打桩：查询工号数量返回 1（表示库中已存在该工号）

        assertThatThrownBy(() -> accountService.importStaffs( // 断言：导入已存在工号的数据时抛出异常
                staffFile(staffRow("测试教师甲", "T1001", "TEACHER")))) // 导入工号为 T1001 的教职工（该工号已存在）
                .isInstanceOf(BusinessException.class) // 断言：异常类型为 BusinessException
                .hasMessageContaining("已存在"); // 断言：异常信息包含"已存在"提示
    }

    // ==================== 导出 ====================

    @Test
    @DisplayName("导出学生：响应头正确且内容可解析")
    /** 验证场景：导出学生生成合法 Excel，响应头含下载文件名，内容可回读解析 */
    void exportStudents_shouldWriteValidExcel() { // 用例：导出学生应生成合法 Excel
        when(studentMapper.selectList(any())).thenReturn(Arrays.asList( // 打桩：查询学生列表返回两名学生（导出数据源）
                student("S20230001", "张三"), // 学生1：学号 S20230001，姓名张三
                student("S20230002", "李四"))); // 学生2：学号 S20230002，姓名李四
        MockHttpServletResponse response = new MockHttpServletResponse(); // 创建 Mock 响应对象，捕获导出写出的字节与响应头

        accountService.exportStudents(response); // 调用被测方法：把学生数据导出到响应对象中

        assertThat(response.getHeader("Content-Disposition")).contains("attachment;filename="); // 断言：响应头包含附件下载文件名标记
        List<StudentExcelRow> rows = EasyExcel.read(new ByteArrayInputStream(response.getContentAsByteArray())) // 用 EasyExcel 回读响应体中的 Excel 字节流
                .head(StudentExcelRow.class).sheet().doReadSync(); // 指定表头类型并同步读取所有行
        assertThat(rows).hasSize(2); // 断言：导出的 Excel 有 2 行数据
        assertThat(rows.get(0).getStudentNo()).isEqualTo("S20230001"); // 断言：第一行学号为 S20230001
        assertThat(rows.get(1).getRealName()).isEqualTo("李四"); // 断言：第二行姓名为李四
    }

    @Test
    @DisplayName("导出教职工：内容可解析")
    /** 验证场景：导出教职工生成可解析的 Excel，包含工号/姓名/角色等信息 */
    void exportStaffs_shouldWriteValidExcel() { // 用例：导出教职工应生成可解析的 Excel
        Staff s = new Staff(); // 创建教职工实体（模拟数据库记录）
        s.setStaffNo("T1001"); // 设置工号 T1001
        s.setRealName("刘建国"); // 设置姓名刘建国
        s.setRoleType("TEACHER"); // 设置角色为教师
        s.setDepartment("计算机学院"); // 设置所属院系
        when(staffMapper.selectList(any())).thenReturn(Collections.singletonList(s)); // 打桩：查询教职工列表返回 [刘建国]
        MockHttpServletResponse response = new MockHttpServletResponse(); // 创建 Mock 响应对象

        accountService.exportStaffs(response); // 调用被测方法：把教职工数据导出到响应对象中

        List<StaffExcelRow> rows = EasyExcel.read(new ByteArrayInputStream(response.getContentAsByteArray())) // 回读响应体中的 Excel 字节流
                .head(StaffExcelRow.class).sheet().doReadSync(); // 指定表头类型并同步读取所有行
        assertThat(rows).hasSize(1); // 断言：导出的 Excel 有 1 行数据
        assertThat(rows.get(0).getStaffNo()).isEqualTo("T1001"); // 断言：第一行工号为 T1001
        assertThat(rows.get(0).getRoleType()).isEqualTo("TEACHER"); // 断言：第一行角色为 TEACHER
    }

    @Test
    @DisplayName("学生模板：仅表头无数据行")
    /** 验证场景：下载学生导入模板仅含表头，不含任何数据行 */
    void downloadStudentTemplate_shouldWriteHeaderOnly() { // 用例：学生模板应仅含表头
        MockHttpServletResponse response = new MockHttpServletResponse(); // 创建 Mock 响应对象

        accountService.downloadStudentTemplate(response); // 调用被测方法：生成学生导入模板并写入响应

        List<StudentExcelRow> rows = EasyExcel.read(new ByteArrayInputStream(response.getContentAsByteArray())) // 回读模板 Excel 字节流
                .head(StudentExcelRow.class).sheet().doReadSync(); // 指定表头类型并同步读取所有行
        assertThat(rows).isEmpty(); // 断言：模板没有数据行（只有表头）
    }

    // ==================== 自助修改密码 ====================

    /** 构造带 BCrypt 密码密文与令牌版本的学生实体（id=64L，对应学生身份用例） */
    private Student studentWithPassword(String password) { // 工具方法：构造带密码密文的学生实体
        Student s = new Student(); // 创建学生实体对象
        s.setId(64L); // 设置 ID 为 64（与学生身份 studentUser 的 userId 对应）
        s.setStudentNo("S20240051"); // 设置学号
        s.setPasswordHash(new BCryptPasswordEncoder().encode(password)); // 把明文密码用 BCrypt 加密后存入密码字段
        s.setTokenVersion(1); // 设置令牌版本号为 1（改密后应递增为 2）
        return s; // 返回构造好的学生实体
    }

    /** 构造带 BCrypt 密码密文与令牌版本的教职工实体（id=2L，对应教师身份用例） */
    private Staff staffWithPassword(String password) { // 工具方法：构造带密码密文的教职工实体
        Staff s = new Staff(); // 创建教职工实体对象
        s.setId(2L); // 设置 ID 为 2（与教师身份 teacherUser 的 userId 对应）
        s.setStaffNo("T1001"); // 设置工号
        s.setPasswordHash(new BCryptPasswordEncoder().encode(password)); // 把明文密码用 BCrypt 加密后存入密码字段
        s.setTokenVersion(1); // 设置令牌版本号为 1
        return s; // 返回构造好的教职工实体
    }

    @Test
    @DisplayName("修改密码：学生旧密码正确时更新为新密码密文并递增令牌版本")
    /** 验证场景：学生旧密码校验通过后更新为新密码密文，令牌版本递增使旧 token 立即失效 */
    void changePassword_shouldUpdateStudentHashWhenOldPasswordCorrect() { // 用例：学生旧密码正确时成功改密
        UserContext.clear(); // 清空默认身份
        UserContext.set(studentUser()); // 切换为学生身份（当前改密人）
        String oldHash = new BCryptPasswordEncoder().encode("oldPass123"); // 预生成旧密码的 BCrypt 密文（用于对比新密文不同）
        Student stu = studentWithPassword("oldPass123"); // 构造库中已存在该学生的记录（密码为 oldPass123）
        when(studentMapper.selectById(64L)).thenReturn(stu); // 打桩：按 ID 64 查到该学生

        accountService.changePassword("oldPass123", "newPass456"); // 调用被测方法：使用正确的旧密码修改为新密码

        ArgumentCaptor<Student> captor = ArgumentCaptor.forClass(Student.class); // 创建参数捕获器，捕获更新调用中的学生对象
        verify(studentMapper).updateById(captor.capture()); // 断言：updateById 被调用 1 次，并捕获入参
        String newHash = captor.getValue().getPasswordHash(); // 取出更新后的密码密文
        // 密码确实被更新：新密文能匹配新密码，且不再匹配旧密码
        assertThat(new BCryptPasswordEncoder().matches("newPass456", newHash)).isTrue(); // 断言：新密文能匹配新密码（改密成功）
        assertThat(new BCryptPasswordEncoder().matches("oldPass123", newHash)).isFalse(); // 断言：新密文不再匹配旧密码
        assertThat(newHash).isNotEqualTo(oldHash); // 断言：新旧密文内容不同（确实重新加密）
        // 令牌版本号递增：改密后旧 token 全部失效
        assertThat(captor.getValue().getTokenVersion()).isEqualTo(2); // 断言：令牌版本由 1 递增为 2（旧 token 全部失效）
    }

    @Test
    @DisplayName("修改密码：教师旧密码正确时更新为新密码密文并递增令牌版本")
    /** 验证场景：教师旧密码校验通过后更新为新密码密文，令牌版本递增使旧 token 立即失效 */
    void changePassword_shouldUpdateStaffHashWhenOldPasswordCorrect() { // 用例：教师旧密码正确时成功改密
        UserContext.clear(); // 清空默认身份
        UserContext.set(teacherUser()); // 切换为教师身份（当前改密人）
        Staff st = staffWithPassword("oldPass123"); // 构造库中已存在的教师记录（密码为 oldPass123）
        when(staffMapper.selectById(2L)).thenReturn(st); // 打桩：按 ID 2 查到该教师

        accountService.changePassword("oldPass123", "newPass456"); // 调用被测方法：教师使用正确旧密码修改为新密码

        ArgumentCaptor<Staff> captor = ArgumentCaptor.forClass(Staff.class); // 创建参数捕获器，捕获更新调用中的教职工对象
        verify(staffMapper).updateById(captor.capture()); // 断言：updateById 被调用 1 次，并捕获入参
        assertThat(new BCryptPasswordEncoder().matches("newPass456", captor.getValue().getPasswordHash())).isTrue(); // 断言：新密文能匹配新密码（改密成功）
        assertThat(captor.getValue().getTokenVersion()).isEqualTo(2); // 断言：令牌版本由 1 递增为 2（旧 token 失效）
    }

    @Test
    @DisplayName("管理员重置密码：学生令牌版本递增，旧 token 失效")
    /** 验证场景：管理员重置学生密码后令牌版本递增，学生旧 token 全部失效 */
    void resetPassword_shouldIncrementStudentTokenVersion() { // 用例：管理员重置密码后学生令牌版本递增
        Student stu = studentWithPassword("oldPass123"); // 构造库中已存在的学生记录
        when(studentMapper.selectById(64L)).thenReturn(stu); // 打桩：按 ID 64 查到该学生

        accountService.resetPassword("STUDENT", 64L); // 调用被测方法：管理员重置该学生密码

        ArgumentCaptor<Student> captor = ArgumentCaptor.forClass(Student.class); // 创建参数捕获器，捕获更新调用中的学生对象
        verify(studentMapper).updateById(captor.capture()); // 断言：updateById 被调用 1 次，并捕获入参
        assertThat(captor.getValue().getTokenVersion()).isEqualTo(2); // 断言：令牌版本递增为 2（重置密码后旧 token 立即失效）
    }

    @Test
    @DisplayName("冻结/启用账号：令牌版本递增，旧 token 失效")
    /** 验证场景：管理员冻结账号时状态更新为 FROZEN，且令牌版本递增使旧 token 立即失效 */
    void toggleStatus_shouldIncrementTokenVersion() { // 用例：冻结账号时令牌版本递增
        UserContext.clear(); // 清空默认身份
        UserContext.set(adminUser()); // 显式切换为管理员身份（冻结操作仅管理员可执行）
        Student stu = studentWithPassword("oldPass123"); // 构造库中已存在的学生记录
        when(studentMapper.selectById(64L)).thenReturn(stu); // 打桩：按 ID 64 查到该学生

        accountService.toggleStatus("STUDENT", 64L, "FROZEN"); // 调用被测方法：管理员把该学生账号冻结

        ArgumentCaptor<Student> captor = ArgumentCaptor.forClass(Student.class); // 创建参数捕获器，捕获更新调用中的学生对象
        verify(studentMapper).updateById(captor.capture()); // 断言：updateById 被调用 1 次，并捕获入参
        assertThat(captor.getValue().getStatus()).isEqualTo("FROZEN"); // 断言：账号状态已更新为 FROZEN（冻结）
        assertThat(captor.getValue().getTokenVersion()).isEqualTo(2); // 断言：令牌版本递增为 2（冻结后旧 token 立即失效）
    }

    @Test
    @DisplayName("修改密码：旧密码错误时拒绝且不更新")
    /** 验证场景：旧密码错误时拒绝改密并抛出业务异常，数据库不做任何更新 */
    void changePassword_shouldRejectWhenOldPasswordWrong() { // 用例：旧密码错误时拒绝改密
        UserContext.clear(); // 清空默认身份
        UserContext.set(studentUser()); // 切换为学生身份
        when(studentMapper.selectById(64L)).thenReturn(studentWithPassword("oldPass123")); // 打桩：查到的学生密码为 oldPass123

        assertThatThrownBy(() -> accountService.changePassword("wrongPass", "newPass456")) // 断言：用错误的旧密码改密时抛出异常
                .isInstanceOf(BusinessException.class) // 断言：异常类型为 BusinessException
                .hasMessageContaining("旧密码"); // 断言：异常信息与"旧密码"相关

        verify(studentMapper, never()).updateById(any()); // 断言：updateById 从未被调用（改密失败不落库）
    }

    @Test
    @DisplayName("修改密码：账号不存在时拒绝")
    /** 验证场景：当前账号在数据库中不存在时拒绝改密，且不执行更新 */
    void changePassword_shouldRejectWhenAccountMissing() { // 用例：账号不存在时拒绝改密
        UserContext.clear(); // 清空默认身份
        UserContext.set(studentUser()); // 切换为学生身份
        when(studentMapper.selectById(64L)).thenReturn(null); // 打桩：按 ID 64 查不到任何学生（账号不存在）

        assertThatThrownBy(() -> accountService.changePassword("oldPass123", "newPass456")) // 断言：改密时抛出异常
                .isInstanceOf(BusinessException.class) // 断言：异常类型为 BusinessException
                .hasMessageContaining("账号不存在"); // 断言：异常信息提示账号不存在

        verify(studentMapper, never()).updateById(any()); // 断言：updateById 从未被调用
    }

    @Test
    @DisplayName("修改密码：新密码与旧密码相同时拒绝")
    /** 验证场景：新密码与旧密码相同（未发生变化）时拒绝改密 */
    void changePassword_shouldRejectWhenNewEqualsOld() { // 用例：新密码与旧密码相同时拒绝改密
        UserContext.clear(); // 清空默认身份
        UserContext.set(studentUser()); // 切换为学生身份
        when(studentMapper.selectById(64L)).thenReturn(studentWithPassword("samePass123")); // 打桩：学生原密码为 samePass123

        assertThatThrownBy(() -> accountService.changePassword("samePass123", "samePass123")) // 断言：新旧密码相同时改密抛出异常
                .isInstanceOf(BusinessException.class) // 断言：异常类型为 BusinessException
                .hasMessageContaining("相同"); // 断言：异常信息提示新旧密码相同

        verify(studentMapper, never()).updateById(any()); // 断言：updateById 从未被调用
    }

    @Test
    @DisplayName("修改密码：新密码强度不足（过短）时拒绝")
    /** 验证场景：新密码强度不足（长度过短）时拒绝改密，强度校验先于查库执行 */
    void changePassword_shouldRejectWeakNewPassword() { // 用例：新密码强度不足时拒绝改密
        UserContext.clear(); // 清空默认身份
        UserContext.set(studentUser()); // 切换为学生身份

        // 强度校验先于查库，故无需 stub selectById
        assertThatThrownBy(() -> accountService.changePassword("oldPass123", "short1")) // 断言：新密码过短时改密抛出异常
                .isInstanceOf(BusinessException.class) // 断言：异常类型为 BusinessException
                .hasMessageContaining("新密码"); // 断言：异常信息与"新密码"强度相关

        verify(studentMapper, never()).updateById(any()); // 断言：updateById 从未被调用
    }
}
