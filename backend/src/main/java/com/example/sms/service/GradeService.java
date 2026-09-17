package com.example.sms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.sms.common.BusinessException;
import com.example.sms.dto.AuditDTO;
import com.example.sms.dto.GradeEntryDTO;
import com.example.sms.dto.GradeItemDTO;
import com.example.sms.entity.Course;
import com.example.sms.entity.CourseGradeAudit;
import com.example.sms.entity.Staff;
import com.example.sms.entity.Student;
import com.example.sms.entity.StudentCourse;
import com.example.sms.excel.GradeExcelRow;
import com.example.sms.mapper.CourseGradeAuditMapper;
import com.example.sms.mapper.CourseMapper;
import com.example.sms.mapper.StaffMapper;
import com.example.sms.mapper.StudentCourseMapper;
import com.example.sms.mapper.StudentMapper;
import com.example.sms.util.ExcelUtil;
import com.example.sms.util.UserContext;
import com.example.sms.vo.AuditVO;
import com.example.sms.vo.GradeVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 成绩管理服务：录入 -> 提交 -> 审核 -> 发布 -> 锁定
 */
@Service
public class GradeService {

    @Autowired
    private StudentCourseMapper studentCourseMapper;

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private StudentMapper studentMapper;

    @Autowired
    private StaffMapper staffMapper;

    @Autowired
    private CourseGradeAuditMapper auditMapper;

    @Autowired
    private NotificationService notificationService;

    /** 教师：查看课程选课名单（含成绩） */
    public List<Map<String, Object>> listCourseStudents(Long courseId) {
        Course course = checkTeacherCourse(courseId);
        List<StudentCourse> scs = studentCourseMapper.selectList(new LambdaQueryWrapper<StudentCourse>()
                .eq(StudentCourse::getCourseId, courseId));
        if (scs.isEmpty()) return Collections.emptyList();
        List<Long> studentIds = scs.stream().map(StudentCourse::getStudentId).collect(Collectors.toList());
        Map<Long, Student> studentMap = studentMapper.selectBatchIds(studentIds).stream()
                .collect(Collectors.toMap(Student::getId, Function.identity()));
        return scs.stream().map(sc -> {
            Map<String, Object> item = new HashMap<>();
            Student stu = studentMap.get(sc.getStudentId());
            item.put("studentId", sc.getStudentId());
            item.put("studentNo", stu != null ? stu.getStudentNo() : null);
            item.put("realName", stu != null ? stu.getRealName() : null);
            item.put("major", stu != null ? stu.getMajor() : null);
            item.put("className", stu != null ? stu.getClassName() : null);
            item.put("score", sc.getScore());
            item.put("mark", sc.getMark());
            item.put("locked", isScoreLocked(courseId));
            return item;
        }).collect(Collectors.toList());
    }

    /** 教师：导出课程成绩名单 */
    public void exportCourseStudents(Long courseId, HttpServletResponse response) {
        Course course = checkTeacherCourse(courseId);
        List<GradeExcelRow> rows = toGradeExcelRows(courseId);
        ExcelUtil.write(response, "成绩_" + course.getCourseName(), GradeExcelRow.class, rows);
    }

    /** 教师：下载成绩导入模板（预填选课学生学号/姓名） */
    public void downloadGradeTemplate(Long courseId, HttpServletResponse response) {
        Course course = checkTeacherCourse(courseId);
        List<GradeExcelRow> rows = toGradeExcelRows(courseId);
        for (GradeExcelRow row : rows) {
            row.setScore(null);
            row.setMark("NORMAL");
        }
        ExcelUtil.write(response, "成绩导入模板_" + course.getCourseName(), GradeExcelRow.class, rows);
    }

    /** 教师：批量导入成绩（仅 DRAFT 阶段可导入） */
    @Transactional
    public int importGrades(Long courseId, MultipartFile file) {
        Course course = checkTeacherCourse(courseId);
        CourseGradeAudit audit = getAudit(course);
        // 成绩流程状态机：DRAFT(录入/导入) -> SUBMITTED(提交) -> APPROVED(审核通过) -> PUBLISHED(发布锁定)。
        // 只有 DRAFT 阶段允许批量导入/修改，一旦提交便进入审批流，防止已审核的成绩被事后篡改。
        if (!"DRAFT".equals(audit.getStatus())) {
            throw new BusinessException("成绩已提交/审核/发布，当前不可导入");
        }
        List<StudentCourse> scs = studentCourseMapper.selectList(new LambdaQueryWrapper<StudentCourse>()
                .eq(StudentCourse::getCourseId, courseId));
        if (scs.isEmpty()) {
            throw new BusinessException("该课程暂无学生选课，无法导入成绩");
        }
        // 先把"学号 -> 选课记录"建成内存映射，逐行导入时 O(1) 匹配，避免每行都查一次库
        Map<String, StudentCourse> scMap = buildStudentNoMap(scs);

        List<ExcelUtil.RowItem<GradeExcelRow>> rows = ExcelUtil.readWithRowNumbers(file, GradeExcelRow.class);
        if (rows.isEmpty()) {
            throw new BusinessException("未读取到任何有效数据，请使用模板填写后导入");
        }
        List<String> errors = new ArrayList<>();
        List<StudentCourse> toUpdate = new ArrayList<>();
        for (ExcelUtil.RowItem<GradeExcelRow> item : rows) {
            GradeExcelRow row = item.getData();
            int rowNum = item.getRowNum();
            String no = trimToNull(row.getStudentNo());
            if (no == null) {
                errors.add("第" + rowNum + "行：学号不能为空");
                continue;
            }
            StudentCourse sc = scMap.get(no);
            if (sc == null) {
                errors.add("第" + rowNum + "行：学号 " + no + " 未选修本课程");
                continue;
            }
            // 标记校验：NORMAL(正常)/DEFER(缓考)/ABSENT(缺考)/CHEAT(作弊)。
            // 只有 NORMAL 才要求并保存 0-100 的分数；其余标记无有效成绩，统一清空 score（保留标记本身）
            String mark = trimToNull(row.getMark());
            if (mark == null) mark = "NORMAL";
            if (!VALID_MARKS.contains(mark)) {
                errors.add("第" + rowNum + "行：标记 " + row.getMark() + " 非法，仅支持 NORMAL/DEFER/ABSENT/CHEAT");
                continue;
            }
            if ("NORMAL".equals(mark)) {
                BigDecimal score = row.getScore();
                if (score == null) {
                    errors.add("第" + rowNum + "行：标记为正常时必须填写总评成绩");
                    continue;
                }
                if (score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(new BigDecimal("100")) > 0) {
                    errors.add("第" + rowNum + "行：成绩须在 0-100 之间");
                    continue;
                }
                sc.setScore(score);
            } else {
                sc.setScore(null);
            }
            sc.setMark(mark);
            toUpdate.add(sc);
        }
        // 全表校验通过后才统一落库（先整体校验、有错整批拒绝），避免出现"部分行已更新、部分行报错"的中间态
        if (!errors.isEmpty()) {
            throw new BusinessException("导入失败（共 " + errors.size() + " 处错误），请修正后重新导入：\n"
                    + String.join("\n", errors.subList(0, Math.min(errors.size(), 10))));
        }
        for (StudentCourse sc : toUpdate) {
            studentCourseMapper.updateById(sc);
        }
        return toUpdate.size();
    }

    private static final Set<String> VALID_MARKS = new HashSet<>(java.util.Arrays.asList(
            "NORMAL", "DEFER", "ABSENT", "CHEAT"));

    /** 课程选课名单 -> Excel 行（含当前成绩） */
    private List<GradeExcelRow> toGradeExcelRows(Long courseId) {
        List<Map<String, Object>> items = listCourseStudents(courseId);
        return items.stream().map(m -> {
            GradeExcelRow r = new GradeExcelRow();
            r.setStudentNo((String) m.get("studentNo"));
            r.setRealName((String) m.get("realName"));
            r.setScore((BigDecimal) m.get("score"));
            r.setMark((String) m.get("mark"));
            return r;
        }).collect(Collectors.toList());
    }

    /** 学号 -> 选课记录 */
    private Map<String, StudentCourse> buildStudentNoMap(List<StudentCourse> scs) {
        List<Long> studentIds = scs.stream().map(StudentCourse::getStudentId).collect(Collectors.toList());
        Map<Long, Student> studentMap = studentMapper.selectBatchIds(studentIds).stream()
                .collect(Collectors.toMap(Student::getId, Function.identity()));
        Map<String, StudentCourse> map = new HashMap<>();
        for (StudentCourse sc : scs) {
            Student stu = studentMap.get(sc.getStudentId());
            if (stu != null) {
                map.put(stu.getStudentNo(), sc);
            }
        }
        return map;
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 教师：录入/更新成绩（仅 DRAFT 阶段可改） */
    @Transactional
    public void entryGrades(GradeEntryDTO dto) {
        if (dto.getItems() == null || dto.getItems().isEmpty()) {
            throw new BusinessException("成绩列表不能为空");
        }
        Course course = checkTeacherCourse(dto.getCourseId());
        CourseGradeAudit audit = getAudit(course);
        if (!"DRAFT".equals(audit.getStatus())) {
            throw new BusinessException("成绩已提交/审核/发布，当前不可修改");
        }
        for (GradeItemDTO item : dto.getItems()) {
            StudentCourse sc = studentCourseMapper.selectOne(new LambdaQueryWrapper<StudentCourse>()
                    .eq(StudentCourse::getCourseId, dto.getCourseId())
                    .eq(StudentCourse::getStudentId, item.getStudentId()));
            if (sc == null) throw new BusinessException("学生未选修该课程");
            String mark = item.getMark() == null || item.getMark().isBlank() ? "NORMAL" : item.getMark();
            if (!VALID_MARKS.contains(mark)) {
                throw new BusinessException("成绩标记非法，仅支持 NORMAL/DEFER/ABSENT/CHEAT");
            }
            if ("NORMAL".equals(mark)) {
                if (item.getScore() == null) {
                    throw new BusinessException("正常考试必须填写成绩");
                }
                if (item.getScore().compareTo(BigDecimal.ZERO) < 0
                        || item.getScore().compareTo(new BigDecimal("100")) > 0) {
                    throw new BusinessException("成绩必须在 0-100 之间");
                }
            }
            sc.setMark(mark);
            sc.setScore("NORMAL".equals(mark) ? item.getScore() : null);
            studentCourseMapper.updateById(sc);
        }
        auditMapper.updateById(audit);
    }

    /** 教师：提交成绩（SUBMITTED，锁定 score） */
    @Transactional
    public void submitGrades(Long courseId) {
        Course course = checkTeacherCourse(courseId);
        CourseGradeAudit audit = getAudit(course);
        if (!"DRAFT".equals(audit.getStatus())) {
            throw new BusinessException("当前状态不可提交");
        }
        audit.setStatus("SUBMITTED");
        audit.setSubmittedAt(LocalDateTime.now());
        audit.setRejectReason(null);
        auditMapper.updateById(audit);
    }

    /** 教师：查看本人成绩流程 */
    public List<AuditVO> listMyAudits() {
        List<CourseGradeAudit> audits = auditMapper.selectList(new LambdaQueryWrapper<CourseGradeAudit>()
                .eq(CourseGradeAudit::getTeacherId, UserContext.getUserId())
                .orderByDesc(CourseGradeAudit::getUpdatedAt));
        return toAuditVO(audits);
    }

    /** 教秘：待审核列表（SUBMITTED） */
    public List<AuditVO> listPendingAudits() {
        List<CourseGradeAudit> audits = auditMapper.selectList(new LambdaQueryWrapper<CourseGradeAudit>()
                .eq(CourseGradeAudit::getStatus, "SUBMITTED")
                .orderByAsc(CourseGradeAudit::getSubmittedAt));
        return toAuditVO(audits);
    }

    /** 教秘：全部成绩流程 */
    public List<AuditVO> listAllAudits() {
        List<CourseGradeAudit> audits = auditMapper.selectList(new LambdaQueryWrapper<CourseGradeAudit>()
                .orderByDesc(CourseGradeAudit::getUpdatedAt));
        return toAuditVO(audits);
    }

    private List<AuditVO> toAuditVO(List<CourseGradeAudit> audits) {
        if (audits.isEmpty()) return Collections.emptyList();
        List<Long> courseIds = audits.stream().map(CourseGradeAudit::getCourseId).collect(Collectors.toList());
        Map<Long, Course> courseMap = courseMapper.selectBatchIds(courseIds).stream()
                .collect(Collectors.toMap(Course::getId, Function.identity()));
        List<Long> teacherIds = audits.stream().map(CourseGradeAudit::getTeacherId).distinct().collect(Collectors.toList());
        Map<Long, Staff> teacherMap = staffMapper.selectBatchIds(teacherIds).stream()
                .collect(Collectors.toMap(Staff::getId, Function.identity()));
        return audits.stream().map(a -> {
            AuditVO vo = new AuditVO();
            vo.setCourseId(a.getCourseId());
            vo.setStatus(a.getStatus());
            vo.setSubmittedAt(a.getSubmittedAt());
            vo.setApprovedAt(a.getApprovedAt());
            vo.setPublishedAt(a.getPublishedAt());
            vo.setRejectReason(a.getRejectReason());
            Course course = courseMap.get(a.getCourseId());
            if (course != null) {
                vo.setCourseCode(course.getCourseCode());
                vo.setCourseName(course.getCourseName());
            }
            Staff teacher = teacherMap.get(a.getTeacherId());
            vo.setTeacherName(teacher != null ? teacher.getRealName() : "未知");
            return vo;
        }).collect(Collectors.toList());
    }

    /** 教秘：审核通过/退回 */
    @Transactional
    public void audit(AuditDTO dto) {
        if (!"ADMIN".equals(UserContext.getRole())) {
            throw new BusinessException(403, "无权限，仅教学秘书可操作");
        }
        CourseGradeAudit audit = auditMapper.selectOne(new LambdaQueryWrapper<CourseGradeAudit>()
                .eq(CourseGradeAudit::getCourseId, dto.getCourseId()));
        if (audit == null || !"SUBMITTED".equals(audit.getStatus())) {
            throw new BusinessException("该课程没有待审核的成绩");
        }
        if (Boolean.TRUE.equals(dto.getApproved())) {
            audit.setStatus("APPROVED");
            audit.setApprovedAt(LocalDateTime.now());
            audit.setRejectReason(null);
        } else {
            if (dto.getRejectReason() == null || dto.getRejectReason().isBlank()) {
                throw new BusinessException("退回时必须填写原因");
            }
            audit.setStatus("DRAFT");
            audit.setRejectReason(dto.getRejectReason());
        }
        auditMapper.updateById(audit);
    }

    /**
     * 教秘：发布成绩（核心事务）
     * 1. 审核表状态 -> PUBLISHED，记录 published_at
     * 2. 遍历选课记录：score >= 60 且 NORMAL 累加学分
     * 3. 重算学生 GPA
     */
    @Transactional
    public void publish(Long courseId) {
        if (!"ADMIN".equals(UserContext.getRole())) {
            throw new BusinessException(403, "无权限，仅教学秘书可操作");
        }
        Course course = courseMapper.selectById(courseId);
        if (course == null) throw new BusinessException("课程不存在");
        CourseGradeAudit audit = auditMapper.selectOne(new LambdaQueryWrapper<CourseGradeAudit>()
                .eq(CourseGradeAudit::getCourseId, courseId));
        // 发布是成绩流程的最后一步且不可逆：仅 APPROVED 可发布（跳过审核直接发布视为越权），
        // 发布后成绩对外可见并计入学生学分/GPA，同时选课/退课也被锁定，因此必须先有审核通过结论
        if (audit == null || !"APPROVED".equals(audit.getStatus())) {
            throw new BusinessException("仅审核通过的课程可以发布");
        }
        audit.setStatus("PUBLISHED");
        audit.setPublishedAt(LocalDateTime.now());
        auditMapper.updateById(audit);

        // 逐学生重算学分/GPA：同一学生可能同时受多门课发布影响，
        // 因此按学生维度整体重算（而非在本课程基础上累加），保证口径一致
        List<StudentCourse> scs = studentCourseMapper.selectList(new LambdaQueryWrapper<StudentCourse>()
                .eq(StudentCourse::getCourseId, courseId));
        for (StudentCourse sc : scs) {
            recalcStudentCredits(sc.getStudentId());
        }

        // 成绩发布自动通知（接收人=该课程选课学生）
        notificationService.sendSystem("GRADE_PUBLISH",
                "成绩已发布",
                "「" + course.getCourseName() + "」成绩已发布，可登录系统查询。",
                scs.stream().map(StudentCourse::getStudentId).collect(Collectors.toList()));
    }

    /** 重算某学生已修学分与 GPA（只统计已发布课程） */
    private void recalcStudentCredits(Long studentId) {
        Student student = studentMapper.selectById(studentId);
        if (student == null) return;

        // 只统计"已发布"课程的成绩：未发布/审核中的成绩尚未定论，不得计入已修学分与 GPA，
        // 否则学生仪表盘会随教师改分而波动，且与成绩单口径不一致
        List<Long> publishedCourseIds = auditMapper.selectList(new LambdaQueryWrapper<CourseGradeAudit>()
                        .eq(CourseGradeAudit::getStatus, "PUBLISHED"))
                .stream().map(CourseGradeAudit::getCourseId).collect(Collectors.toList());
        if (publishedCourseIds.isEmpty()) {
            resetCredits(student, BigDecimal.ZERO, BigDecimal.ZERO);
            return;
        }
        // 通过条件：标记 NORMAL 且总评 >= 60（缓考/缺考/作弊不获得学分）；
        // 在此基础上按"学分加权"计算 GPA（每门课绩点 × 学分 求和 ÷ 总学分）
        List<StudentCourse> passed = studentCourseMapper.selectList(new LambdaQueryWrapper<StudentCourse>()
                .eq(StudentCourse::getStudentId, studentId)
                .in(StudentCourse::getCourseId, publishedCourseIds)
                .eq(StudentCourse::getMark, "NORMAL")
                .isNotNull(StudentCourse::getScore)
                .ge(StudentCourse::getScore, 60));
        if (passed.isEmpty()) {
            resetCredits(student, BigDecimal.ZERO, BigDecimal.ZERO);
            return;
        }
        Map<Long, Course> courseMap = courseMapper.selectBatchIds(
                        passed.stream().map(StudentCourse::getCourseId).collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(Course::getId, Function.identity()));

        BigDecimal totalCredits = BigDecimal.ZERO;
        BigDecimal weightSum = BigDecimal.ZERO;
        BigDecimal pointSum = BigDecimal.ZERO;
        for (StudentCourse sc : passed) {
            Course c = courseMap.get(sc.getCourseId());
            if (c == null) continue;
            BigDecimal credit = c.getCredit();
            BigDecimal point = toGradePoint(sc.getScore());
            totalCredits = totalCredits.add(credit);
            pointSum = pointSum.add(point.multiply(credit));
            weightSum = weightSum.add(credit);
        }
        BigDecimal gpa = weightSum.compareTo(BigDecimal.ZERO) > 0
                ? pointSum.divide(weightSum, 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        resetCredits(student, totalCredits, gpa);
    }

    private void resetCredits(Student student, BigDecimal credits, BigDecimal gpa) {
        student.setTotalEarnedCredits(credits);
        student.setGpa(gpa);
        studentMapper.updateById(student);
    }

    /** 百分制 -> 4 分制绩点 */
    private BigDecimal toGradePoint(BigDecimal score) {
        // 4 分制绩点换算：90+ → 4.0，80-89 → 3.0，70-79 → 2.0，60-69 → 1.0，60 以下 → 0
        double s = score.doubleValue();
        if (s >= 90) return new BigDecimal("4.0");
        if (s >= 80) return new BigDecimal("3.0");
        if (s >= 70) return new BigDecimal("2.0");
        if (s >= 60) return new BigDecimal("1.0");
        return BigDecimal.ZERO;
    }

    /** 学生：成绩单 */
    public List<GradeVO> myGrades(Long studentId) {
        List<StudentCourse> scs = studentCourseMapper.selectList(new LambdaQueryWrapper<StudentCourse>()
                .eq(StudentCourse::getStudentId, studentId)
                .orderByDesc(StudentCourse::getCreatedAt));
        if (scs.isEmpty()) return Collections.emptyList();
        List<Long> courseIds = scs.stream().map(StudentCourse::getCourseId).collect(Collectors.toList());
        Map<Long, Course> courseMap = courseMapper.selectBatchIds(courseIds).stream()
                .collect(Collectors.toMap(Course::getId, Function.identity()));
        Map<Long, String> auditMap = auditMapper.selectList(new LambdaQueryWrapper<CourseGradeAudit>()
                        .in(CourseGradeAudit::getCourseId, courseIds))
                .stream().collect(Collectors.toMap(CourseGradeAudit::getCourseId, CourseGradeAudit::getStatus));
        return scs.stream().map(sc -> {
            GradeVO vo = new GradeVO();
            Course c = courseMap.get(sc.getCourseId());
            vo.setCourseId(sc.getCourseId());
            vo.setCourseName(c != null ? c.getCourseName() : "未知课程");
            vo.setCredit(c != null ? c.getCredit() : BigDecimal.ZERO);
            String auditStatus = auditMap.get(sc.getCourseId());
            vo.setAuditStatus(auditStatus);
            boolean published = "PUBLISHED".equals(auditStatus);
            // 未发布的成绩不向学生泄露：分数/mark 只在已发布后返回，绩点与是否通过随之置空
            if (published) {
                vo.setScore(sc.getScore());
                vo.setMark(sc.getMark());
            }
            vo.setPassed(published && sc.getScore() != null
                    && sc.getScore().compareTo(new BigDecimal("60")) >= 0
                    && "NORMAL".equals(sc.getMark()));
            // 单科绩点：仅已发布且已有成绩时展示（未发布成绩不向学生泄露绩点）
            vo.setGradePoint(published && sc.getScore() != null
                    ? toGradePoint(sc.getScore()) : null);
            return vo;
        }).collect(Collectors.toList());
    }

    /** 学生：学业仪表盘 */
    public Map<String, Object> dashboard(Long studentId) {
        Student student = studentMapper.selectById(studentId);
        List<GradeVO> gradeList = myGrades(studentId);
        BigDecimal required = student != null && student.getRequiredCredits() != null
                ? student.getRequiredCredits() : BigDecimal.ZERO;
        BigDecimal earned = student != null && student.getTotalEarnedCredits() != null
                ? student.getTotalEarnedCredits() : BigDecimal.ZERO;
        BigDecimal progress = required.compareTo(BigDecimal.ZERO) > 0
                ? earned.divide(required, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"))
                    .setScale(1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        Map<String, Object> result = new HashMap<>();
        result.put("totalEarnedCredits", earned);
        result.put("requiredCredits", required);
        result.put("progressPercent", progress);
        result.put("gpa", student != null && student.getGpa() != null ? student.getGpa() : BigDecimal.ZERO);
        result.put("gradeList", gradeList);
        return result;
    }

    /** 校验课程存在且属于当前教师（教秘可操作全部） */
    private Course checkTeacherCourse(Long courseId) {
        Course course = courseMapper.selectById(courseId);
        if (course == null) throw new BusinessException("课程不存在");
        if (!"ADMIN".equals(UserContext.getRole()) && !course.getTeacherId().equals(UserContext.getUserId())) {
            throw new BusinessException(403, "无权限操作他人课程");
        }
        return course;
    }

    private boolean isScoreLocked(Long courseId) {
        CourseGradeAudit audit = auditMapper.selectOne(new LambdaQueryWrapper<CourseGradeAudit>()
                .eq(CourseGradeAudit::getCourseId, courseId));
        return audit != null && !"DRAFT".equals(audit.getStatus());
    }

    private CourseGradeAudit getAudit(Course course) {
        // 首次接触成绩（录入/导入）时懒创建审核记录：初始 DRAFT 状态、归属该课程授课教师，
        // 后续提交/审核/发布都围绕这条记录推进状态机
        CourseGradeAudit audit = auditMapper.selectOne(new LambdaQueryWrapper<CourseGradeAudit>()
                .eq(CourseGradeAudit::getCourseId, course.getId()));
        if (audit == null) {
            audit = new CourseGradeAudit();
            audit.setCourseId(course.getId());
            audit.setTeacherId(course.getTeacherId());
            audit.setStatus("DRAFT");
            auditMapper.insert(audit);
        }
        return audit;
    }
}
