package com.example.sms.service;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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
@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private StudentMapper studentMapper;

    @Mock
    private StaffMapper staffMapper;

    @InjectMocks
    private AccountService accountService;

    @BeforeEach
    void setUp() {
        // 默认以管理员身份执行用例；需要其他身份的方法会先 clear 再自行切换
        UserContext.set(adminUser());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private UserContext.CurrentUser adminUser() {
        UserContext.CurrentUser user = new UserContext.CurrentUser();
        user.setUserId(1L);
        user.setRoleType("ADMIN");
        return user;
    }

    private UserContext.CurrentUser teacherUser() {
        UserContext.CurrentUser user = new UserContext.CurrentUser();
        user.setUserId(2L);
        user.setRoleType("TEACHER");
        return user;
    }

    private UserContext.CurrentUser studentUser() {
        UserContext.CurrentUser user = new UserContext.CurrentUser();
        user.setUserId(64L);
        user.setRoleType("STUDENT");
        return user;
    }

    private MockMultipartFile studentFile(StudentExcelRow... rows) {
        return file(rows, StudentExcelRow.class, "students.xlsx");
    }

    private MockMultipartFile staffFile(StaffExcelRow... rows) {
        return file(rows, StaffExcelRow.class, "staffs.xlsx");
    }

    private <T> MockMultipartFile file(T[] rows, Class<T> clazz, String filename) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        EasyExcel.write(out, clazz).sheet("Sheet1").doWrite(Arrays.asList(rows));
        return new MockMultipartFile("file", filename,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
    }

    private StudentExcelRow studentRow(String name, String no) {
        StudentExcelRow row = new StudentExcelRow();
        row.setRealName(name);
        row.setStudentNo(no);
        return row;
    }

    private StaffExcelRow staffRow(String name, String no, String role) {
        StaffExcelRow row = new StaffExcelRow();
        row.setRealName(name);
        row.setStaffNo(no);
        row.setRoleType(role);
        return row;
    }

    private Student student(String no, String name) {
        Student s = new Student();
        s.setStudentNo(no);
        s.setRealName(name);
        return s;
    }

    // ==================== 学生导入 ====================

    @Test
    @DisplayName("学生导入：学号留空自动生成并递增")
    /** 验证场景：学生导入时学号留空，自动按当前最大学号递增生成（S20230004、S20230005），且默认状态/学分/初始密码正确 */
    void importStudents_shouldAutoGenerateStudentNo() {
        // 连续两次查询最大学号：首次为 S20230003，插入后第二次应看到 S20230004
        when(studentMapper.selectList(any()))
                .thenReturn(Collections.singletonList(student("S20230003", "王五")))
                .thenReturn(Collections.singletonList(student("S20230004", "测试学生甲")));

        List<AccountImportResultVO> result = accountService.importStudents(studentFile(
                studentRow("测试学生甲", null),
                studentRow("测试学生乙", null)));
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getUserNo()).isEqualTo("S20230004");
        assertThat(result.get(0).getInitPassword()).isNotBlank();
        ArgumentCaptor<Student> captor = ArgumentCaptor.forClass(Student.class);
        verify(studentMapper, times(2)).insert(captor.capture());
        List<Student> inserted = captor.getAllValues();
        assertThat(inserted.get(0).getStudentNo()).isEqualTo("S20230004");
        assertThat(inserted.get(1).getStudentNo()).isEqualTo("S20230005");
        assertThat(inserted.get(0).getStatus()).isEqualTo("ENABLED");
        assertThat(inserted.get(0).getRequiredCredits()).isEqualByComparingTo("160.00");
        assertThat(inserted.get(0).getTotalEarnedCredits()).isEqualByComparingTo("0");
        assertThat(inserted.get(0).getPasswordHash()).isNotBlank();
    }

    @Test
    @DisplayName("学生导入：指定学号且不冲突时按原学号入库")
    /** 验证场景：学生导入时指定学号且库中不存在该学号，则按原学号与姓名入库 */
    void importStudents_shouldKeepProvidedStudentNo() {
        when(studentMapper.selectCount(any())).thenReturn(0L);

        List<AccountImportResultVO> result = accountService.importStudents(studentFile(studentRow("张三", "S20230099")));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUserNo()).isEqualTo("S20230099");
        ArgumentCaptor<Student> captor = ArgumentCaptor.forClass(Student.class);
        verify(studentMapper).insert(captor.capture());
        assertThat(captor.getValue().getStudentNo()).isEqualTo("S20230099");
        assertThat(captor.getValue().getRealName()).isEqualTo("张三");
    }

    @Test
    @DisplayName("学生导入：文件内学号重复报错且不落库")
    /** 验证场景：同一导入文件内学号重复时抛业务异常，且任何记录都不落库 */
    void importStudents_shouldRejectDuplicateNoInFile() {
        when(studentMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> accountService.importStudents(studentFile(
                studentRow("张三", "S20230099"),
                studentRow("李四", "S20230099"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("在文件中重复");

        verify(studentMapper, never()).insert(any());
    }

    @Test
    @DisplayName("学生导入：学号已存在于数据库时报错")
    /** 验证场景：学号已存在于数据库时抛业务异常，且不执行插入 */
    void importStudents_shouldRejectExistingNo() {
        when(studentMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> accountService.importStudents(
                studentFile(studentRow("张三", "S20230001"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已存在");

        verify(studentMapper, never()).insert(any());
    }

    @Test
    @DisplayName("学生导入：姓名为空报错")
    /** 验证场景：学生姓名为空（空白字符）时抛业务异常 */
    void importStudents_shouldRejectBlankName() {
        assertThatThrownBy(() -> accountService.importStudents(
                studentFile(studentRow("   ", "S20230099"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("姓名不能为空");
    }

    @Test
    @DisplayName("学生导入：教师无权限（403）")
    /** 验证场景：非管理员（教师身份）执行学生导入被拒绝，提示仅教学秘书可操作 */
    void importStudents_shouldRejectNonAdmin() {
        UserContext.clear();
        UserContext.set(teacherUser());

        assertThatThrownBy(() -> accountService.importStudents(
                studentFile(studentRow("张三", null))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅教学秘书可操作");
    }

    // ==================== 教职工导入 ====================

    @Test
    @DisplayName("教职工导入：角色留空默认教师，工号自动生成")
    /** 验证场景：教职工导入时角色留空默认 TEACHER，工号按当前最大学号自动递增（T1003），默认启用 */
    void importStaffs_shouldDefaultRoleAndAutoGenerateNo() {
        Staff max = new Staff();
        max.setStaffNo("T1002");
        when(staffMapper.selectList(any())).thenReturn(Collections.singletonList(max));

        List<AccountImportResultVO> result = accountService.importStaffs(staffFile(staffRow("测试教师甲", null, null)));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUserNo()).isEqualTo("T1003");
        assertThat(result.get(0).getInitPassword()).isNotBlank();
        ArgumentCaptor<Staff> captor = ArgumentCaptor.forClass(Staff.class);
        verify(staffMapper).insert(captor.capture());
        assertThat(captor.getValue().getStaffNo()).isEqualTo("T1003");
        assertThat(captor.getValue().getRoleType()).isEqualTo("TEACHER");
        assertThat(captor.getValue().getStatus()).isEqualTo("ENABLED");
    }

    @Test
    @DisplayName("教职工导入：非法角色报错")
    /** 验证场景：教职工导入时填入了非法的角色值（如 BOSS）被拒绝 */
    void importStaffs_shouldRejectInvalidRole() {
        assertThatThrownBy(() -> accountService.importStaffs(
                staffFile(staffRow("测试教师甲", null, "BOSS"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("角色仅支持 TEACHER");
    }

    @Test
    @DisplayName("教职工导入：禁止通过导入创建管理员")
    /** 验证场景：导入文件中出现 ADMIN 角色时被拒绝，防止通过导入提权 */
    void importStaffs_shouldRejectAdminRole() {
        assertThatThrownBy(() -> accountService.importStaffs(
                staffFile(staffRow("管理员甲", null, "ADMIN"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("角色仅支持 TEACHER");
    }

    @Test
    @DisplayName("教职工导入：同批次多个空工号依次递增不重复")
    /** 验证场景：同批次多个空工号依次递增生成（T1003、T1004），互不重复 */
    void importStaffs_shouldGenerateDistinctNosInBatch() {
        Staff max = new Staff();
        max.setStaffNo("T1002");
        when(staffMapper.selectList(any())).thenReturn(Collections.singletonList(max));

        List<AccountImportResultVO> result = accountService.importStaffs(staffFile(
                staffRow("测试教师甲", null, null),
                staffRow("测试教师乙", null, null)));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getUserNo()).isEqualTo("T1003");
        assertThat(result.get(1).getUserNo()).isEqualTo("T1004");
        verify(staffMapper, times(2)).insert(any());
    }

    @Test
    @DisplayName("教职工导入：工号已存在报错")
    /** 验证场景：教职工工号已存在于数据库时抛业务异常 */
    void importStaffs_shouldRejectExistingNo() {
        when(staffMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> accountService.importStaffs(
                staffFile(staffRow("测试教师甲", "T1001", "TEACHER"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已存在");
    }

    // ==================== 导出 ====================

    @Test
    @DisplayName("导出学生：响应头正确且内容可解析")
    /** 验证场景：导出学生生成合法 Excel，响应头含下载文件名，内容可回读解析 */
    void exportStudents_shouldWriteValidExcel() {
        when(studentMapper.selectList(any())).thenReturn(Arrays.asList(
                student("S20230001", "张三"),
                student("S20230002", "李四")));
        MockHttpServletResponse response = new MockHttpServletResponse();

        accountService.exportStudents(response);

        assertThat(response.getHeader("Content-Disposition")).contains("attachment;filename=");
        List<StudentExcelRow> rows = EasyExcel.read(new ByteArrayInputStream(response.getContentAsByteArray()))
                .head(StudentExcelRow.class).sheet().doReadSync();
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).getStudentNo()).isEqualTo("S20230001");
        assertThat(rows.get(1).getRealName()).isEqualTo("李四");
    }

    @Test
    @DisplayName("导出教职工：内容可解析")
    /** 验证场景：导出教职工生成可解析的 Excel，包含工号/姓名/角色等信息 */
    void exportStaffs_shouldWriteValidExcel() {
        Staff s = new Staff();
        s.setStaffNo("T1001");
        s.setRealName("刘建国");
        s.setRoleType("TEACHER");
        s.setDepartment("计算机学院");
        when(staffMapper.selectList(any())).thenReturn(Collections.singletonList(s));
        MockHttpServletResponse response = new MockHttpServletResponse();

        accountService.exportStaffs(response);

        List<StaffExcelRow> rows = EasyExcel.read(new ByteArrayInputStream(response.getContentAsByteArray()))
                .head(StaffExcelRow.class).sheet().doReadSync();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getStaffNo()).isEqualTo("T1001");
        assertThat(rows.get(0).getRoleType()).isEqualTo("TEACHER");
    }

    @Test
    @DisplayName("学生模板：仅表头无数据行")
    /** 验证场景：下载学生导入模板仅含表头，不含任何数据行 */
    void downloadStudentTemplate_shouldWriteHeaderOnly() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        accountService.downloadStudentTemplate(response);

        List<StudentExcelRow> rows = EasyExcel.read(new ByteArrayInputStream(response.getContentAsByteArray()))
                .head(StudentExcelRow.class).sheet().doReadSync();
        assertThat(rows).isEmpty();
    }

    // ==================== 自助修改密码 ====================

    /** 构造带 BCrypt 密码密文与令牌版本的学生实体（id=64L，对应学生身份用例） */
    private Student studentWithPassword(String password) {
        Student s = new Student();
        s.setId(64L);
        s.setStudentNo("S20240051");
        s.setPasswordHash(new BCryptPasswordEncoder().encode(password));
        s.setTokenVersion(1);
        return s;
    }

    /** 构造带 BCrypt 密码密文与令牌版本的教职工实体（id=2L，对应教师身份用例） */
    private Staff staffWithPassword(String password) {
        Staff s = new Staff();
        s.setId(2L);
        s.setStaffNo("T1001");
        s.setPasswordHash(new BCryptPasswordEncoder().encode(password));
        s.setTokenVersion(1);
        return s;
    }

    @Test
    @DisplayName("修改密码：学生旧密码正确时更新为新密码密文并递增令牌版本")
    /** 验证场景：学生旧密码校验通过后更新为新密码密文，令牌版本递增使旧 token 立即失效 */
    void changePassword_shouldUpdateStudentHashWhenOldPasswordCorrect() {
        UserContext.clear();
        UserContext.set(studentUser());
        String oldHash = new BCryptPasswordEncoder().encode("oldPass123");
        Student stu = studentWithPassword("oldPass123");
        when(studentMapper.selectById(64L)).thenReturn(stu);

        accountService.changePassword("oldPass123", "newPass456");

        ArgumentCaptor<Student> captor = ArgumentCaptor.forClass(Student.class);
        verify(studentMapper).updateById(captor.capture());
        String newHash = captor.getValue().getPasswordHash();
        // 密码确实被更新：新密文能匹配新密码，且不再匹配旧密码
        assertThat(new BCryptPasswordEncoder().matches("newPass456", newHash)).isTrue();
        assertThat(new BCryptPasswordEncoder().matches("oldPass123", newHash)).isFalse();
        assertThat(newHash).isNotEqualTo(oldHash);
        // 令牌版本号递增：改密后旧 token 全部失效
        assertThat(captor.getValue().getTokenVersion()).isEqualTo(2);
    }

    @Test
    @DisplayName("修改密码：教师旧密码正确时更新为新密码密文并递增令牌版本")
    /** 验证场景：教师旧密码校验通过后更新为新密码密文，令牌版本递增使旧 token 立即失效 */
    void changePassword_shouldUpdateStaffHashWhenOldPasswordCorrect() {
        UserContext.clear();
        UserContext.set(teacherUser());
        Staff st = staffWithPassword("oldPass123");
        when(staffMapper.selectById(2L)).thenReturn(st);

        accountService.changePassword("oldPass123", "newPass456");

        ArgumentCaptor<Staff> captor = ArgumentCaptor.forClass(Staff.class);
        verify(staffMapper).updateById(captor.capture());
        assertThat(new BCryptPasswordEncoder().matches("newPass456", captor.getValue().getPasswordHash())).isTrue();
        assertThat(captor.getValue().getTokenVersion()).isEqualTo(2);
    }

    @Test
    @DisplayName("管理员重置密码：学生令牌版本递增，旧 token 失效")
    /** 验证场景：管理员重置学生密码后令牌版本递增，学生旧 token 全部失效 */
    void resetPassword_shouldIncrementStudentTokenVersion() {
        Student stu = studentWithPassword("oldPass123");
        when(studentMapper.selectById(64L)).thenReturn(stu);

        accountService.resetPassword("STUDENT", 64L);

        ArgumentCaptor<Student> captor = ArgumentCaptor.forClass(Student.class);
        verify(studentMapper).updateById(captor.capture());
        assertThat(captor.getValue().getTokenVersion()).isEqualTo(2);
    }

    @Test
    @DisplayName("冻结/启用账号：令牌版本递增，旧 token 失效")
    /** 验证场景：管理员冻结账号时状态更新为 FROZEN，且令牌版本递增使旧 token 立即失效 */
    void toggleStatus_shouldIncrementTokenVersion() {
        UserContext.clear();
        UserContext.set(adminUser());
        Student stu = studentWithPassword("oldPass123");
        when(studentMapper.selectById(64L)).thenReturn(stu);

        accountService.toggleStatus("STUDENT", 64L, "FROZEN");

        ArgumentCaptor<Student> captor = ArgumentCaptor.forClass(Student.class);
        verify(studentMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("FROZEN");
        assertThat(captor.getValue().getTokenVersion()).isEqualTo(2);
    }

    @Test
    @DisplayName("修改密码：旧密码错误时拒绝且不更新")
    /** 验证场景：旧密码错误时拒绝改密并抛出业务异常，数据库不做任何更新 */
    void changePassword_shouldRejectWhenOldPasswordWrong() {
        UserContext.clear();
        UserContext.set(studentUser());
        when(studentMapper.selectById(64L)).thenReturn(studentWithPassword("oldPass123"));

        assertThatThrownBy(() -> accountService.changePassword("wrongPass", "newPass456"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("旧密码");

        verify(studentMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("修改密码：账号不存在时拒绝")
    /** 验证场景：当前账号在数据库中不存在时拒绝改密，且不执行更新 */
    void changePassword_shouldRejectWhenAccountMissing() {
        UserContext.clear();
        UserContext.set(studentUser());
        when(studentMapper.selectById(64L)).thenReturn(null);

        assertThatThrownBy(() -> accountService.changePassword("oldPass123", "newPass456"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("账号不存在");

        verify(studentMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("修改密码：新密码与旧密码相同时拒绝")
    /** 验证场景：新密码与旧密码相同（未发生变化）时拒绝改密 */
    void changePassword_shouldRejectWhenNewEqualsOld() {
        UserContext.clear();
        UserContext.set(studentUser());
        when(studentMapper.selectById(64L)).thenReturn(studentWithPassword("samePass123"));

        assertThatThrownBy(() -> accountService.changePassword("samePass123", "samePass123"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("相同");

        verify(studentMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("修改密码：新密码强度不足（过短）时拒绝")
    /** 验证场景：新密码强度不足（长度过短）时拒绝改密，强度校验先于查库执行 */
    void changePassword_shouldRejectWeakNewPassword() {
        UserContext.clear();
        UserContext.set(studentUser());

        // 强度校验先于查库，故无需 stub selectById
        assertThatThrownBy(() -> accountService.changePassword("oldPass123", "short1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("新密码");

        verify(studentMapper, never()).updateById(any());
    }
}
