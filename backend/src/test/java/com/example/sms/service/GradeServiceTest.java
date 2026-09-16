package com.example.sms.service;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

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
@ExtendWith(MockitoExtension.class)
class GradeServiceTest {

    @Mock
    private StudentCourseMapper studentCourseMapper;

    @Mock
    private CourseMapper courseMapper;

    @Mock
    private StudentMapper studentMapper;

    @Mock
    private StaffMapper staffMapper;

    @Mock
    private CourseGradeAuditMapper auditMapper;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private GradeService gradeService;

    private final Course course = buildCourse(1L);
    private final StudentCourse sc1 = buildSc(10L, 1L);
    private final StudentCourse sc2 = buildSc(11L, 2L);
    private final Student stu1 = buildStudent(1L, "S20230001", "张三");
    private final Student stu2 = buildStudent(2L, "S20230002", "李四");

    @BeforeEach
    void setUp() {
        UserContext.set(adminUser());
        course.setTeacherId(2L);
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

    private Course buildCourse(Long id) {
        Course c = new Course();
        c.setId(id);
        c.setCourseCode("CS103");
        c.setCourseName("Web前端开发");
        return c;
    }

    private StudentCourse buildSc(Long id, Long studentId) {
        StudentCourse sc = new StudentCourse();
        sc.setId(id);
        sc.setCourseId(1L);
        sc.setStudentId(studentId);
        sc.setMark("NORMAL");
        return sc;
    }

    private Student buildStudent(Long id, String no, String name) {
        Student s = new Student();
        s.setId(id);
        s.setStudentNo(no);
        s.setRealName(name);
        return s;
    }

    private MockMultipartFile gradeFile(GradeExcelRow... rows) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        EasyExcel.write(out, GradeExcelRow.class).sheet("Sheet1").doWrite(Arrays.asList(rows));
        return new MockMultipartFile("file", "grades.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
    }

    private GradeExcelRow gradeRow(String no, String score, String mark) {
        GradeExcelRow row = new GradeExcelRow();
        row.setStudentNo(no);
        if (score != null) row.setScore(new BigDecimal(score));
        row.setMark(mark);
        return row;
    }

    /** 默认选中课学生（2人）的 mock 装配 */
    private void mockCourseWithEnrolledStudents() {
        when(courseMapper.selectById(1L)).thenReturn(course);
        when(studentCourseMapper.selectList(any())).thenReturn(Arrays.asList(sc1, sc2));
        when(studentMapper.selectBatchIds(any())).thenReturn(Arrays.asList(stu1, stu2));
    }

    // ==================== 成绩导入 ====================

    @Test
    @DisplayName("成绩导入：NORMAL 写分数，DEFER 清空分数")
    void importGrades_shouldApplyScoreAndMark() {
        mockCourseWithEnrolledStudents();
        CourseGradeAudit audit = new CourseGradeAudit();
        audit.setStatus("DRAFT");
        when(auditMapper.selectOne(any())).thenReturn(audit);

        int count = gradeService.importGrades(1L, gradeFile(
                gradeRow("S20230001", "88", "NORMAL"),
                gradeRow("S20230002", null, "DEFER")));

        assertThat(count).isEqualTo(2);
        assertThat(sc1.getScore()).isEqualByComparingTo("88");
        assertThat(sc1.getMark()).isEqualTo("NORMAL");
        assertThat(sc2.getScore()).isNull();
        assertThat(sc2.getMark()).isEqualTo("DEFER");
        verify(studentCourseMapper, times(2)).updateById(any(StudentCourse.class));
    }

    @Test
    @DisplayName("成绩导入：未选修该课程的学号报错")
    void importGrades_shouldRejectStudentNotEnrolled() {
        mockCourseWithEnrolledStudents();
        CourseGradeAudit audit = new CourseGradeAudit();
        audit.setStatus("DRAFT");
        when(auditMapper.selectOne(any())).thenReturn(audit);

        assertThatThrownBy(() -> gradeService.importGrades(1L, gradeFile(
                gradeRow("S20230099", "88", "NORMAL"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未选修本课程");
    }

    @Test
    @DisplayName("成绩导入：非法标记报错")
    void importGrades_shouldRejectInvalidMark() {
        mockCourseWithEnrolledStudents();
        CourseGradeAudit audit = new CourseGradeAudit();
        audit.setStatus("DRAFT");
        when(auditMapper.selectOne(any())).thenReturn(audit);

        assertThatThrownBy(() -> gradeService.importGrades(1L, gradeFile(
                gradeRow("S20230001", "88", "FOO"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("标记 FOO 非法");
    }

    @Test
    @DisplayName("成绩导入：分数超出 0-100 报错")
    void importGrades_shouldRejectScoreOutOfRange() {
        mockCourseWithEnrolledStudents();
        CourseGradeAudit audit = new CourseGradeAudit();
        audit.setStatus("DRAFT");
        when(auditMapper.selectOne(any())).thenReturn(audit);

        assertThatThrownBy(() -> gradeService.importGrades(1L, gradeFile(
                gradeRow("S20230001", "150", "NORMAL"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("成绩须在 0-100 之间");
    }

    @Test
    @DisplayName("成绩导入：正常标记但缺分数报错")
    void importGrades_shouldRequireScoreWhenNormal() {
        mockCourseWithEnrolledStudents();
        CourseGradeAudit audit = new CourseGradeAudit();
        audit.setStatus("DRAFT");
        when(auditMapper.selectOne(any())).thenReturn(audit);

        assertThatThrownBy(() -> gradeService.importGrades(1L, gradeFile(
                gradeRow("S20230001", null, "NORMAL"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须填写总评成绩");
    }

    @Test
    @DisplayName("成绩导入：非 DRAFT 阶段拒绝导入")
    void importGrades_shouldRejectWhenLocked() {
        when(courseMapper.selectById(1L)).thenReturn(course);
        CourseGradeAudit audit = new CourseGradeAudit();
        audit.setStatus("SUBMITTED");
        when(auditMapper.selectOne(any())).thenReturn(audit);

        assertThatThrownBy(() -> gradeService.importGrades(1L, gradeFile(
                gradeRow("S20230001", "88", "NORMAL"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前不可导入");
    }

    // ==================== 模板 / 导出 ====================

    @Test
    @DisplayName("成绩模板：预填选课学生，分数清空标记为 NORMAL")
    void downloadGradeTemplate_shouldPreFillStudents() {
        mockCourseWithEnrolledStudents();
        MockHttpServletResponse response = new MockHttpServletResponse();

        gradeService.downloadGradeTemplate(1L, response);

        List<GradeExcelRow> rows = EasyExcel.read(new ByteArrayInputStream(response.getContentAsByteArray()))
                .head(GradeExcelRow.class).sheet().doReadSync();
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).getStudentNo()).isEqualTo("S20230001");
        assertThat(rows.get(0).getScore()).isNull();
        assertThat(rows.get(0).getMark()).isEqualTo("NORMAL");
        assertThat(rows.get(1).getStudentNo()).isEqualTo("S20230002");
    }

    @Test
    @DisplayName("成绩导出：包含当前已录成绩")
    void exportCourseStudents_shouldContainScores() {
        sc1.setScore(new BigDecimal("88"));
        sc2.setScore(new BigDecimal("45"));
        mockCourseWithEnrolledStudents();
        MockHttpServletResponse response = new MockHttpServletResponse();

        gradeService.exportCourseStudents(1L, response);

        List<GradeExcelRow> rows = EasyExcel.read(new ByteArrayInputStream(response.getContentAsByteArray()))
                .head(GradeExcelRow.class).sheet().doReadSync();
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).getScore()).isEqualByComparingTo("88");
        assertThat(rows.get(1).getScore()).isEqualByComparingTo("45");
        assertThat(rows.get(1).getMark()).isEqualTo("NORMAL");
    }

    // ==================== 成绩发布自动通知 ====================

    @Test
    @DisplayName("成绩发布：自动发送 GRADE_PUBLISH 通知给该课程选课学生")
    void publish_shouldNotifyEnrolledStudents() {
        course.setId(1L);
        course.setCourseName("Web前端开发");
        when(courseMapper.selectById(1L)).thenReturn(course);
        CourseGradeAudit audit = new CourseGradeAudit();
        audit.setCourseId(1L);
        audit.setStatus("APPROVED");
        when(auditMapper.selectOne(any())).thenReturn(audit);
        when(studentCourseMapper.selectList(any())).thenReturn(Arrays.asList(sc1, sc2));
        // recalcStudentCredits：无其他已发布课程 → 重置学分/GPA（sc1.studentId=1L, sc2.studentId=2L）
        when(studentMapper.selectById(1L)).thenReturn(stu1);
        when(studentMapper.selectById(2L)).thenReturn(stu2);
        when(auditMapper.selectList(any())).thenReturn(java.util.Collections.emptyList());

        gradeService.publish(1L);

        verify(notificationService).sendSystem(eq("GRADE_PUBLISH"), eq("成绩已发布"), any(), any());
        verify(auditMapper).updateById(any(CourseGradeAudit.class));
    }

    @Test
    @DisplayName("成绩发布：未审核通过（非 APPROVED）不可发布，不发送通知")
    void publish_shouldRejectWhenNotApproved() {
        course.setId(1L);
        when(courseMapper.selectById(1L)).thenReturn(course);
        CourseGradeAudit audit = new CourseGradeAudit();
        audit.setStatus("SUBMITTED");
        when(auditMapper.selectOne(any())).thenReturn(audit);

        assertThatThrownBy(() -> gradeService.publish(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅审核通过的课程可以发布");
    }
}
