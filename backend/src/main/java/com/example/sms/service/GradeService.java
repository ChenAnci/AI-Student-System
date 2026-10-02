package com.example.sms.service; // 声明当前类所在的包：service 服务层

// ===== import 区域：引入 MyBatis-Plus 查询构造器、业务异常、DTO、实体、Excel 行对象、Mapper、工具类、VO、Spring 相关注解与 Java 工具 =====
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper; // MyBatis-Plus 的 Lambda 查询条件构造器
import com.example.sms.common.BusinessException; // 自定义业务异常类
import com.example.sms.dto.AuditDTO; // 审核操作 DTO（是否通过、退回原因）
import com.example.sms.dto.GradeEntryDTO; // 成绩录入 DTO（含成绩明细列表）
import com.example.sms.dto.GradeItemDTO; // 单条成绩明细 DTO（学生 id、分数、标记）
import com.example.sms.entity.Course; // 课程实体类
import com.example.sms.entity.CourseGradeAudit; // 课程成绩审核记录实体类
import com.example.sms.entity.Staff; // 教职工实体类
import com.example.sms.entity.Student; // 学生实体类
import com.example.sms.entity.StudentCourse; // 学生选课关系实体类
import com.example.sms.excel.GradeExcelRow; // 成绩 Excel 行对象（导入/导出载体）
import com.example.sms.mapper.CourseGradeAuditMapper; // 成绩审核记录表 Mapper 接口
import com.example.sms.mapper.CourseMapper; // 课程表 Mapper 接口
import com.example.sms.mapper.StaffMapper; // 教职工表 Mapper 接口
import com.example.sms.mapper.StudentCourseMapper; // 学生选课表 Mapper 接口
import com.example.sms.mapper.StudentMapper; // 学生表 Mapper 接口
import com.example.sms.util.ExcelUtil; // Excel 读写工具类
import com.example.sms.util.UserContext; // 用户上下文工具：读取当前登录用户信息
import com.example.sms.vo.AuditVO; // 成绩审核流程视图对象
import com.example.sms.vo.GradeVO; // 学生成绩单视图对象
import org.springframework.beans.factory.annotation.Autowired; // Spring 依赖注入注解
import org.springframework.stereotype.Service; // Spring 服务层注解
import org.springframework.transaction.annotation.Transactional; // Spring 声明式事务注解
import org.springframework.web.multipart.MultipartFile; // Spring 文件上传对象（接收成绩 Excel）
import javax.servlet.http.HttpServletResponse; // Servlet 响应对象（导出 Excel 用）
import java.math.BigDecimal; // 高精度十进制数：用于学分/成绩数值运算
import java.math.RoundingMode; // 四舍五入模式枚举
import java.time.LocalDateTime; // 本地日期时间：记录提交/审核/发布时间
import java.util.ArrayList; // 动态数组集合
import java.util.Collections; // 集合工具类
import java.util.HashMap; // 哈希表
import java.util.HashSet; // 哈希集合
import java.util.List; // 列表接口
import java.util.Map; // 键值映射接口
import java.util.Set; // 集合接口
import java.util.function.Function; // 函数式接口
import java.util.stream.Collectors; // Stream 收集器

/**
 * 成绩管理服务：录入 -> 提交 -> 审核 -> 发布 -> 锁定
 */
@Service // 声明为 Spring 服务组件，交由容器管理
public class GradeService { // 成绩管理服务类：成绩录入、提交、审核、发布、学分/GPA 重算及成绩单查询

    @Autowired // Spring 自动注入 StudentCourseMapper
    private StudentCourseMapper studentCourseMapper; // 学生选课表 Mapper：存储每个学生的成绩明细

    @Autowired // Spring 自动注入 CourseMapper
    private CourseMapper courseMapper; // 课程表 Mapper

    @Autowired // Spring 自动注入 StudentMapper
    private StudentMapper studentMapper; // 学生表 Mapper

    @Autowired // Spring 自动注入 StaffMapper
    private StaffMapper staffMapper; // 教职工表 Mapper

    @Autowired // Spring 自动注入 CourseGradeAuditMapper
    private CourseGradeAuditMapper auditMapper; // 成绩审核记录表 Mapper：驱动成绩状态机

    @Autowired // Spring 自动注入 NotificationService
    private NotificationService notificationService; // 通知服务：成绩发布后通知选课学生

    /** 教师：查看课程选课名单（含成绩） */
    public List<Map<String, Object>> listCourseStudents(Long courseId) { // 教师查看某课程选课学生名单（含成绩与锁定状态）
        Course course = checkTeacherCourse(courseId); // 校验课程存在且属于当前教师（教秘可操作全部）
        List<StudentCourse> scs = studentCourseMapper.selectList(new LambdaQueryWrapper<StudentCourse>() // 查询该课程全部选课记录
                .eq(StudentCourse::getCourseId, courseId)); // 等值条件：course_id = 课程 id
        if (scs.isEmpty()) return Collections.emptyList(); // 无学生选课返回空列表
        List<Long> studentIds = scs.stream().map(StudentCourse::getStudentId).collect(Collectors.toList()); // 提取选课学生 ID 列表
        Map<Long, Student> studentMap = studentMapper.selectBatchIds(studentIds).stream() // 批量查询学生信息
                .collect(Collectors.toMap(Student::getId, Function.identity())); // 转成"学生 ID -> 学生实体"映射
        return scs.stream().map(sc -> { // 逐条选课记录组装名单行
            Map<String, Object> item = new HashMap<>(); // 创建名单行 Map（字段名与前端约定）
            Student stu = studentMap.get(sc.getStudentId()); // 取该选课记录对应的学生
            item.put("studentId", sc.getStudentId()); // 填入学生 id
            item.put("studentNo", stu != null ? stu.getStudentNo() : null); // 填入学号（学生不存在则为 null）
            item.put("realName", stu != null ? stu.getRealName() : null); // 填入姓名
            item.put("major", stu != null ? stu.getMajor() : null); // 填入专业
            item.put("className", stu != null ? stu.getClassName() : null); // 填入班级
            item.put("score", sc.getScore()); // 填入成绩（可能为 null）
            item.put("mark", sc.getMark()); // 填入成绩标记（NORMAL/DEFER 等）
            item.put("locked", isScoreLocked(courseId)); // 填入成绩是否已锁定（非 DRAFT 即锁定，教师不可再改）
            return item; // 返回组装好的名单行
        }).collect(Collectors.toList()); // 收集为名单列表
    }

    /** 教师：导出课程成绩名单 */
    public void exportCourseStudents(Long courseId, HttpServletResponse response) { // 把课程选课名单导出为 Excel
        Course course = checkTeacherCourse(courseId); // 校验课程归属
        List<GradeExcelRow> rows = toGradeExcelRows(courseId); // 把名单转为 Excel 行对象（含当前成绩）
        ExcelUtil.write(response, "成绩_" + course.getCourseName(), GradeExcelRow.class, rows); // 写出 Excel（文件名带课程名）
    }

    /** 教师：下载成绩导入模板（预填选课学生学号/姓名） */
    public void downloadGradeTemplate(Long courseId, HttpServletResponse response) { // 下载成绩导入模板：预填学生学号/姓名
        Course course = checkTeacherCourse(courseId); // 校验课程归属
        List<GradeExcelRow> rows = toGradeExcelRows(courseId); // 生成含选课学生的行对象
        for (GradeExcelRow row : rows) { // 逐行清空成绩字段
            row.setScore(null); // 分数清空，让教师填写
            row.setMark("NORMAL"); // 标记预填为 NORMAL
        }
        ExcelUtil.write(response, "成绩导入模板_" + course.getCourseName(), GradeExcelRow.class, rows); // 写出模板文件
    }

    /** 教师：批量导入成绩（仅 DRAFT 阶段可导入） */
    @Transactional // 声明式事务：全部校验通过后统一更新，任一行失败整批回滚
    public int importGrades(Long courseId, MultipartFile file) { // 批量导入成绩：返回成功更新的行数
        Course course = checkTeacherCourse(courseId); // 校验课程归属
        CourseGradeAudit audit = getAudit(course); // 获取（或懒创建）该课程的成绩审核记录
        // 成绩流程状态机：DRAFT(录入/导入) -> SUBMITTED(提交) -> APPROVED(审核通过) -> PUBLISHED(发布锁定)。
        // 只有 DRAFT 阶段允许批量导入/修改，一旦提交便进入审批流，防止已审核的成绩被事后篡改。
        if (!"DRAFT".equals(audit.getStatus())) { // 当前不是草稿阶段（已提交/审核/发布）
            throw new BusinessException("成绩已提交/审核/发布，当前不可导入"); // 拒绝导入
        }
        List<StudentCourse> scs = studentCourseMapper.selectList(new LambdaQueryWrapper<StudentCourse>() // 查询该课程全部选课记录
                .eq(StudentCourse::getCourseId, courseId)); // 等值条件：course_id = 课程 id
        if (scs.isEmpty()) { // 没有学生选课
            throw new BusinessException("该课程暂无学生选课，无法导入成绩"); // 提示先让学生选课
        }
        // 先把"学号 -> 选课记录"建成内存映射，逐行导入时 O(1) 匹配，避免每行都查一次库
        Map<String, StudentCourse> scMap = buildStudentNoMap(scs); // 建立学号到选课记录的映射

        List<ExcelUtil.RowItem<GradeExcelRow>> rows = ExcelUtil.readWithRowNumbers(file, GradeExcelRow.class); // 读取成绩 Excel（带原始行号）
        if (rows.isEmpty()) { // 没有有效数据行
            throw new BusinessException("未读取到任何有效数据，请使用模板填写后导入"); // 提示使用模板
        }
        List<String> errors = new ArrayList<>(); // 收集校验错误
        List<StudentCourse> toUpdate = new ArrayList<>(); // 待更新的选课记录（全部校验通过后统一更新）
        for (ExcelUtil.RowItem<GradeExcelRow> item : rows) { // 逐行处理
            GradeExcelRow row = item.getData(); // 取当前行数据
            int rowNum = item.getRowNum(); // 取原始行号（用于报错）
            String no = trimToNull(row.getStudentNo()); // 学号去空白
            if (no == null) { // 学号为空
                errors.add("第" + rowNum + "行：学号不能为空"); // 记录错误
                continue; // 跳过本行
            }
            StudentCourse sc = scMap.get(no); // 用学号从映射取选课记录（O(1) 匹配）
            if (sc == null) { // 该学号未选修本课程
                errors.add("第" + rowNum + "行：学号 " + no + " 未选修本课程"); // 记录错误
                continue; // 跳过本行
            }
            // 标记校验：NORMAL(正常)/DEFER(缓考)/ABSENT(缺考)/CHEAT(作弊)。
            // 只有 NORMAL 才要求并保存 0-100 的分数；其余标记无有效成绩，统一清空 score（保留标记本身）
            String mark = trimToNull(row.getMark()); // 标记去空白
            if (mark == null) mark = "NORMAL"; // 标记未填默认 NORMAL
            if (!VALID_MARKS.contains(mark)) { // 标记不在合法集合中
                errors.add("第" + rowNum + "行：标记 " + row.getMark() + " 非法，仅支持 NORMAL/DEFER/ABSENT/CHEAT"); // 记录错误
                continue; // 跳过本行
            }
            if ("NORMAL".equals(mark)) { // 标记为正常考试：必须有 0-100 的分数
                BigDecimal score = row.getScore(); // 取分数
                if (score == null) { // 分数为空
                    errors.add("第" + rowNum + "行：标记为正常时必须填写总评成绩"); // 记录错误
                    continue; // 跳过本行
                }
                if (score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(new BigDecimal("100")) > 0) { // 分数不在 0-100 区间
                    errors.add("第" + rowNum + "行：成绩须在 0-100 之间"); // 记录错误
                    continue; // 跳过本行
                }
                sc.setScore(score); // 保存有效分数
            } else { // 非正常标记（缓考/缺考/作弊）
                sc.setScore(null); // 无有效成绩，清空分数（保留标记本身）
            }
            sc.setMark(mark); // 保存成绩标记
            toUpdate.add(sc); // 加入待更新列表
        }
        // 全表校验通过后才统一落库（先整体校验、有错整批拒绝），避免出现"部分行已更新、部分行报错"的中间态
        if (!errors.isEmpty()) { // 存在校验错误
            throw new BusinessException("导入失败（共 " + errors.size() + " 处错误），请修正后重新导入：\n" // 汇总错误数量
                    + String.join("\n", errors.subList(0, Math.min(errors.size(), 10)))); // 最多展示前 10 条错误
        }
        for (StudentCourse sc : toUpdate) { // 全部校验通过后统一更新
            studentCourseMapper.updateById(sc); // 更新选课记录的成绩与标记
        }
        return toUpdate.size(); // 返回更新成功的行数
    }

    /** 合法成绩标记：NORMAL(正常)/DEFER(缓考)/ABSENT(缺考)/CHEAT(作弊)；仅 NORMAL 记录分数 */
    private static final Set<String> VALID_MARKS = new HashSet<>(java.util.Arrays.asList( // 合法成绩标记集合（不可变语义）
            "NORMAL", "DEFER", "ABSENT", "CHEAT")); // 四个合法标记值

    /** 课程选课名单 -> Excel 行（含当前成绩） */
    private List<GradeExcelRow> toGradeExcelRows(Long courseId) { // 把课程名单转换为 Excel 行对象列表
        List<Map<String, Object>> items = listCourseStudents(courseId); // 复用名单查询逻辑
        return items.stream().map(m -> { // 逐行转换
            GradeExcelRow r = new GradeExcelRow(); // 创建 Excel 行对象
            r.setStudentNo((String) m.get("studentNo")); // 填充学号
            r.setRealName((String) m.get("realName")); // 填充姓名
            r.setScore((BigDecimal) m.get("score")); // 填充成绩
            r.setMark((String) m.get("mark")); // 填充标记
            return r; // 返回行对象
        }).collect(Collectors.toList()); // 收集为列表
    }

    /** 学号 -> 选课记录 */
    private Map<String, StudentCourse> buildStudentNoMap(List<StudentCourse> scs) { // 建立"学号 -> 选课记录"映射（供导入时 O(1) 匹配）
        List<Long> studentIds = scs.stream().map(StudentCourse::getStudentId).collect(Collectors.toList()); // 提取学生 ID 列表
        Map<Long, Student> studentMap = studentMapper.selectBatchIds(studentIds).stream() // 批量查询学生
                .collect(Collectors.toMap(Student::getId, Function.identity())); // 转成"学生 ID -> 学生实体"映射
        Map<String, StudentCourse> map = new HashMap<>(); // 结果映射
        for (StudentCourse sc : scs) { // 遍历选课记录
            Student stu = studentMap.get(sc.getStudentId()); // 取该记录的学生
            if (stu != null) { // 学生存在
                map.put(stu.getStudentNo(), sc); // 用学号作为 key 存入映射
            }
        }
        return map; // 返回映射
    }

    private static String trimToNull(String value) { // 字符串去空白工具：空白字符串统一转为 null
        if (value == null) return null; // 原值为 null 直接返回
        String trimmed = value.trim(); // 去掉首尾空白
        return trimmed.isEmpty() ? null : trimmed; // 去空白后为空则视为 null
    }

    /**
     * 教师：录入/更新成绩（仅 DRAFT 阶段可改）。
     * 调用逻辑：GradeController.entry → gradeService.entryGrades：教师在成绩录入页保存成绩明细（逐条校验选课关系/标记/分数范围），仅 DRAFT 状态可改，更新 student_course 的 score/mark。
     * 为什么：状态机第一环——成绩只在 DRAFT 阶段可录入修改，一旦提交进入审批流即锁定，防止已审核成绩被事后篡改。
     */
    @Transactional // 声明式事务：多条成绩明细更新原子提交
    public void entryGrades(GradeEntryDTO dto) { // 教师录入/更新成绩明细（仅草稿阶段）
        if (dto.getItems() == null || dto.getItems().isEmpty()) { // 成绩明细列表为空
            throw new BusinessException("成绩列表不能为空"); // 提示至少一条成绩
        }
        Course course = checkTeacherCourse(dto.getCourseId()); // 校验课程归属
        CourseGradeAudit audit = getAudit(course); // 获取（或懒创建）审核记录
        if (!"DRAFT".equals(audit.getStatus())) { // 非草稿阶段不可修改
            throw new BusinessException("成绩已提交/审核/发布，当前不可修改"); // 拒绝修改
        }
        for (GradeItemDTO item : dto.getItems()) { // 逐条处理成绩明细
            StudentCourse sc = studentCourseMapper.selectOne(new LambdaQueryWrapper<StudentCourse>() // 查询该学生的选课记录
                    .eq(StudentCourse::getCourseId, dto.getCourseId()) // 条件一：课程 id
                    .eq(StudentCourse::getStudentId, item.getStudentId())); // 条件二：学生 id
            if (sc == null) throw new BusinessException("学生未选修该课程"); // 未选课则抛异常
            String mark = item.getMark() == null || item.getMark().isBlank() ? "NORMAL" : item.getMark(); // 标记未填默认 NORMAL
            if (!VALID_MARKS.contains(mark)) { // 标记不合法
                throw new BusinessException("成绩标记非法，仅支持 NORMAL/DEFER/ABSENT/CHEAT"); // 拒绝非法标记
            }
            if ("NORMAL".equals(mark)) { // 正常考试必须填写分数
                if (item.getScore() == null) { // 分数为空
                    throw new BusinessException("正常考试必须填写成绩"); // 提示必填
                }
                if (item.getScore().compareTo(BigDecimal.ZERO) < 0 // 分数小于 0
                        || item.getScore().compareTo(new BigDecimal("100")) > 0) { // 或分数大于 100
                    throw new BusinessException("成绩必须在 0-100 之间"); // 提示范围
                }
            }
            sc.setMark(mark); // 保存成绩标记
            sc.setScore("NORMAL".equals(mark) ? item.getScore() : null); // 正常标记存分数，其余标记清空分数
            studentCourseMapper.updateById(sc); // 更新选课记录
        }
        auditMapper.updateById(audit); // 兜底更新审核记录（保持与最新状态一致，实际未改字段）
    }

    /**
     * 教师：提交成绩（SUBMITTED，锁定 score）。
     * 调用逻辑：GradeController.submit → gradeService.submitGrades：教师确认成绩无误后点击提交，审核记录 DRAFT → SUBMITTED 并记录 submittedAt，前端刷新审核状态。
     * 为什么：提交即把成绩锁定送审（score 不再可改），DRAFT → SUBMITTED 单向推进，保证教秘审核的是教师最终确认的成绩，防止审核前被反复改动。
     */
    @Transactional // 声明式事务：审核状态更新原子提交
    public void submitGrades(Long courseId) { // 教师提交成绩：DRAFT -> SUBMITTED（锁定送审）
        Course course = checkTeacherCourse(courseId); // 校验课程归属
        CourseGradeAudit audit = getAudit(course); // 获取审核记录
        if (!"DRAFT".equals(audit.getStatus())) { // 非草稿阶段不可提交
            throw new BusinessException("当前状态不可提交"); // 拒绝提交
        }
        audit.setStatus("SUBMITTED"); // 状态改为已提交
        audit.setSubmittedAt(LocalDateTime.now()); // 记录提交时间
        audit.setRejectReason(null); // 清空之前的退回原因（重新提交）
        auditMapper.updateById(audit); // 落库更新
    }

    /** 教师：查看本人成绩流程 */
    public List<AuditVO> listMyAudits() { // 教师查看自己课程的审核流程列表
        List<CourseGradeAudit> audits = auditMapper.selectList(new LambdaQueryWrapper<CourseGradeAudit>() // 查询审核记录
                .eq(CourseGradeAudit::getTeacherId, UserContext.getUserId()) // 条件：授课教师 = 当前登录教师
                .orderByDesc(CourseGradeAudit::getUpdatedAt)); // 按更新时间倒序
        return toAuditVO(audits); // 转换为视图对象
    }

    /** 教秘：待审核列表（SUBMITTED） */
    public List<AuditVO> listPendingAudits() { // 教秘查看待审核的成绩列表（已提交未审核）
        List<CourseGradeAudit> audits = auditMapper.selectList(new LambdaQueryWrapper<CourseGradeAudit>() // 查询审核记录
                .eq(CourseGradeAudit::getStatus, "SUBMITTED") // 条件：状态为已提交
                .orderByAsc(CourseGradeAudit::getSubmittedAt)); // 按提交时间升序（先提交先处理）
        return toAuditVO(audits); // 转换为视图对象
    }

    /** 教秘：全部成绩流程 */
    public List<AuditVO> listAllAudits() { // 教秘查看全部成绩流程列表
        List<CourseGradeAudit> audits = auditMapper.selectList(new LambdaQueryWrapper<CourseGradeAudit>() // 查询全部审核记录
                .orderByDesc(CourseGradeAudit::getUpdatedAt)); // 按更新时间倒序
        return toAuditVO(audits); // 转换为视图对象
    }

    /** 审核记录 -> 视图：批量带出课程信息与授课教师姓名，避免逐条查库 */
    private List<AuditVO> toAuditVO(List<CourseGradeAudit> audits) { // 批量转换审核记录为视图对象
        if (audits.isEmpty()) return Collections.emptyList(); // 无记录返回空列表
        List<Long> courseIds = audits.stream().map(CourseGradeAudit::getCourseId).collect(Collectors.toList()); // 提取课程 ID 列表
        Map<Long, Course> courseMap = courseMapper.selectBatchIds(courseIds).stream() // 批量查询课程
                .collect(Collectors.toMap(Course::getId, Function.identity())); // 转成"课程 ID -> 课程"映射
        List<Long> teacherIds = audits.stream().map(CourseGradeAudit::getTeacherId).distinct().collect(Collectors.toList()); // 提取教师 ID 并去重
        Map<Long, Staff> teacherMap = staffMapper.selectBatchIds(teacherIds).stream() // 批量查询教职工
                .collect(Collectors.toMap(Staff::getId, Function.identity())); // 转成"教师 ID -> 教师"映射
        return audits.stream().map(a -> { // 逐条组装视图
            AuditVO vo = new AuditVO(); // 创建审核视图对象
            vo.setCourseId(a.getCourseId()); // 填充课程 id
            vo.setStatus(a.getStatus()); // 填充审核状态
            vo.setSubmittedAt(a.getSubmittedAt()); // 填充提交时间
            vo.setApprovedAt(a.getApprovedAt()); // 填充审核通过时间
            vo.setPublishedAt(a.getPublishedAt()); // 填充发布时间
            vo.setRejectReason(a.getRejectReason()); // 填充退回原因
            Course course = courseMap.get(a.getCourseId()); // 取课程
            if (course != null) { // 课程存在
                vo.setCourseCode(course.getCourseCode()); // 填充课程代码
                vo.setCourseName(course.getCourseName()); // 填充课程名
            }
            Staff teacher = teacherMap.get(a.getTeacherId()); // 取教师
            vo.setTeacherName(teacher != null ? teacher.getRealName() : "未知"); // 填充教师姓名
            return vo; // 返回视图
        }).collect(Collectors.toList()); // 收集为列表
    }

    /**
     * 教秘：审核通过/退回。
     * 调用逻辑：GradeController.audit → gradeService.audit：教秘在待审核列表对 SUBMITTED 成绩审核——通过置 APPROVED 并记录 approvedAt；退回回置 DRAFT 且必须填写原因，教师修正后可重新提交；前端刷新待审列表。
     * 为什么：SUBMITTED → APPROVED / → DRAFT 的状态迁移强制审核结论留痕（approvedAt / rejectReason），退回原因必填保证教师可定位修改；非 SUBMITTED 状态不可审核，防止重复处理。
     */
    @Transactional // 声明式事务：审核状态更新原子提交
    public void audit(AuditDTO dto) { // 教秘审核成绩：通过或退回
        if (!"ADMIN".equals(UserContext.getRole())) { // 当前用户不是教学秘书
            throw new BusinessException(403, "无权限，仅教学秘书可操作"); // 抛出 403 越权提示
        }
        CourseGradeAudit audit = auditMapper.selectOne(new LambdaQueryWrapper<CourseGradeAudit>() // 查询该课程审核记录
                .eq(CourseGradeAudit::getCourseId, dto.getCourseId())); // 等值条件：course_id = 课程 id
        if (audit == null || !"SUBMITTED".equals(audit.getStatus())) { // 无记录或状态不是已提交
            throw new BusinessException("该课程没有待审核的成绩"); // 拒绝非待审状态的处理
        }
        if (Boolean.TRUE.equals(dto.getApproved())) { // 审核通过
            audit.setStatus("APPROVED"); // 状态改为审核通过
            audit.setApprovedAt(LocalDateTime.now()); // 记录审核通过时间
            audit.setRejectReason(null); // 清空退回原因
        } else { // 退回
            if (dto.getRejectReason() == null || dto.getRejectReason().isBlank()) { // 退回原因为空
                throw new BusinessException("退回时必须填写原因"); // 退回原因必填
            }
            audit.setStatus("DRAFT"); // 状态回到草稿，供教师修改后重新提交
            audit.setRejectReason(dto.getRejectReason()); // 记录退回原因
        }
        auditMapper.updateById(audit); // 落库更新
    }

    /**
     * 教秘：发布成绩（核心事务）
     * 1. 审核表状态 -> PUBLISHED，记录 published_at
     * 2. 遍历选课记录：score >= 60 且 NORMAL 累加学分
     * 3. 重算学生 GPA
     * 调用逻辑：GradeController.publish → gradeService.publish：教秘对 APPROVED 成绩点击发布，事务内置审核表 PUBLISHED → 逐学生重算已修学分与 GPA → 发送成绩发布通知；前端刷新后成绩对学生可见、选退课被锁定。
     * 为什么：仅 APPROVED 可发布（跳过审核直接发布视为越权），发布后成绩定论并计入学分/GPA；按学生维度整体重算保证多门课先后发布互不影响、口径一致；整个发布在同一事务内原子完成。
     */
    @Transactional // 声明式事务：发布状态、学分/GPA 重算原子完成
    public void publish(Long courseId) { // 教秘发布成绩（成绩流程的最后一步，不可逆）
        if (!"ADMIN".equals(UserContext.getRole())) { // 当前用户不是教学秘书
            throw new BusinessException(403, "无权限，仅教学秘书可操作"); // 抛出 403 越权提示
        }
        Course course = courseMapper.selectById(courseId); // 查询课程
        if (course == null) throw new BusinessException("课程不存在"); // 课程不存在则抛异常
        CourseGradeAudit audit = auditMapper.selectOne(new LambdaQueryWrapper<CourseGradeAudit>() // 查询审核记录
                .eq(CourseGradeAudit::getCourseId, courseId)); // 等值条件：course_id = 课程 id
        // 发布是成绩流程的最后一步且不可逆：仅 APPROVED 可发布（跳过审核直接发布视为越权），
        // 发布后成绩对外可见并计入学生学分/GPA，同时选课/退课也被锁定，因此必须先有审核通过结论
        if (audit == null || !"APPROVED".equals(audit.getStatus())) { // 无记录或状态不是审核通过
            throw new BusinessException("仅审核通过的课程可以发布"); // 拒绝未审核通过的发布
        }
        audit.setStatus("PUBLISHED"); // 状态改为已发布
        audit.setPublishedAt(LocalDateTime.now()); // 记录发布时间
        auditMapper.updateById(audit); // 落库更新

        // 逐学生重算学分/GPA：同一学生可能同时受多门课发布影响，
        // 因此按学生维度整体重算（而非在本课程基础上累加），保证口径一致
        List<StudentCourse> scs = studentCourseMapper.selectList(new LambdaQueryWrapper<StudentCourse>() // 查询该课程全部选课记录
                .eq(StudentCourse::getCourseId, courseId)); // 等值条件：course_id = 课程 id
        for (StudentCourse sc : scs) { // 对每个选课学生
            recalcStudentCredits(sc.getStudentId()); // 重算该学生的已修学分与 GPA
        }

        // 成绩发布自动通知（接收人=该课程选课学生）
        notificationService.sendSystem("GRADE_PUBLISH", // 发送系统通知：类型 GRADE_PUBLISH
                "成绩已发布", // 通知标题
                "「" + course.getCourseName() + "」成绩已发布，可登录系统查询。", // 通知内容
                scs.stream().map(StudentCourse::getStudentId).collect(Collectors.toList())); // 接收人：该课程全部选课学生
    }

    /** 重算某学生已修学分与 GPA（只统计已发布课程） */
    private void recalcStudentCredits(Long studentId) { // 重算指定学生的已修学分与 GPA（仅统计已发布课程的成绩）
        Student student = studentMapper.selectById(studentId); // 查询学生
        if (student == null) return; // 学生不存在直接返回

        // 只统计"已发布"课程的成绩：未发布/审核中的成绩尚未定论，不得计入已修学分与 GPA，
        // 否则学生仪表盘会随教师改分而波动，且与成绩单口径不一致
        List<Long> publishedCourseIds = auditMapper.selectList(new LambdaQueryWrapper<CourseGradeAudit>() // 查询全部已发布审核记录
                        .eq(CourseGradeAudit::getStatus, "PUBLISHED")) // 条件：状态为已发布
                .stream().map(CourseGradeAudit::getCourseId).collect(Collectors.toList()); // 取出课程 ID 列表
        if (publishedCourseIds.isEmpty()) { // 当前没有任何已发布课程
            resetCredits(student, BigDecimal.ZERO, BigDecimal.ZERO); // 已修学分与 GPA 清零
            return; // 提前返回
        }
        // 通过条件：标记 NORMAL 且总评 >= 60（缓考/缺考/作弊不获得学分）；
        // 在此基础上按"学分加权"计算 GPA（每门课绩点 × 学分 求和 ÷ 总学分）
        List<StudentCourse> passed = studentCourseMapper.selectList(new LambdaQueryWrapper<StudentCourse>() // 查询该学生已发布且及格的选课记录
                .eq(StudentCourse::getStudentId, studentId) // 条件一：学生 id
                .in(StudentCourse::getCourseId, publishedCourseIds) // 条件二：课程属于已发布集合
                .eq(StudentCourse::getMark, "NORMAL") // 条件三：标记正常
                .isNotNull(StudentCourse::getScore) // 条件四：有分数
                .ge(StudentCourse::getScore, 60)); // 条件五：分数 >= 60（及格线）
        if (passed.isEmpty()) { // 没有任何已发布且及格的课程
            resetCredits(student, BigDecimal.ZERO, BigDecimal.ZERO); // 已修学分与 GPA 清零
            return; // 提前返回
        }
        Map<Long, Course> courseMap = courseMapper.selectBatchIds( // 批量查询涉及的课程
                        passed.stream().map(StudentCourse::getCourseId).collect(Collectors.toList())) // 课程 ID 列表
                .stream().collect(Collectors.toMap(Course::getId, Function.identity())); // 转成"课程 ID -> 课程"映射

        BigDecimal totalCredits = BigDecimal.ZERO; // 已修学分累计
        BigDecimal weightSum = BigDecimal.ZERO; // 总学分（权重和）
        BigDecimal pointSum = BigDecimal.ZERO; // 绩点×学分的加权和
        for (StudentCourse sc : passed) { // 遍历每个及格课程
            Course c = courseMap.get(sc.getCourseId()); // 取课程（获取学分）
            if (c == null) continue; // 课程缺失则跳过（防御性）
            BigDecimal credit = c.getCredit(); // 取课程学分
            BigDecimal point = toGradePoint(sc.getScore()); // 按分数换算绩点
            totalCredits = totalCredits.add(credit); // 累加已修学分
            pointSum = pointSum.add(point.multiply(credit)); // 累加绩点×学分
            weightSum = weightSum.add(credit); // 累加总学分（权重）
        }
        BigDecimal gpa = weightSum.compareTo(BigDecimal.ZERO) > 0 // 总学分大于 0 时
                ? pointSum.divide(weightSum, 2, RoundingMode.HALF_UP) : BigDecimal.ZERO; // 计算加权 GPA（保留 2 位小数，四舍五入）；总学分为 0 则 GPA 为 0
        resetCredits(student, totalCredits, gpa); // 把重算结果写回学生
    }

    /** 重算结果的统一落库入口：更新学生已修学分与 GPA */
    private void resetCredits(Student student, BigDecimal credits, BigDecimal gpa) { // 把重算出的学分与 GPA 落库
        student.setTotalEarnedCredits(credits); // 更新已修学分
        student.setGpa(gpa); // 更新 GPA
        studentMapper.updateById(student); // 更新学生表
    }

    /** 百分制 -> 4 分制绩点 */
    private BigDecimal toGradePoint(BigDecimal score) { // 把百分制分数换算为 4 分制绩点
        // 4 分制绩点换算：90+ → 4.0，80-89 → 3.0，70-79 → 2.0，60-69 → 1.0，60 以下 → 0
        double s = score.doubleValue(); // 转成 double 便于区间比较
        if (s >= 90) return new BigDecimal("4.0"); // 90 分及以上 = 4.0
        if (s >= 80) return new BigDecimal("3.0"); // 80-89 分 = 3.0
        if (s >= 70) return new BigDecimal("2.0"); // 70-79 分 = 2.0
        if (s >= 60) return new BigDecimal("1.0"); // 60-69 分 = 1.0
        return BigDecimal.ZERO; // 60 分以下 = 0（虽然 passed 已过滤，但兜底）
    }

    /**
     * 学生：成绩单。
     * 调用逻辑：GradeController.myGrades → gradeService.myGrades：学生在成绩单页加载，按本人选课记录关联课程与审核状态，仅 PUBLISHED 的成绩返回 score/mark，前端渲染成绩单与单科绩点。
     * 为什么：未发布/审核中的成绩不向学生泄露（score、mark、绩点置空），防止教师改分期间学生看到波动数据；已发布才展示通过与否与单科绩点，与学业仪表盘口径一致。
     */
    public List<GradeVO> myGrades(Long studentId) { // 查询学生本人的成绩单
        List<StudentCourse> scs = studentCourseMapper.selectList(new LambdaQueryWrapper<StudentCourse>() // 查询该学生全部选课记录
                .eq(StudentCourse::getStudentId, studentId) // 条件：学生 id
                .orderByDesc(StudentCourse::getCreatedAt)); // 按选课时间倒序
        if (scs.isEmpty()) return Collections.emptyList(); // 无选课记录返回空列表
        List<Long> courseIds = scs.stream().map(StudentCourse::getCourseId).collect(Collectors.toList()); // 提取课程 ID 列表
        Map<Long, Course> courseMap = courseMapper.selectBatchIds(courseIds).stream() // 批量查询课程
                .collect(Collectors.toMap(Course::getId, Function.identity())); // 转成"课程 ID -> 课程"映射
        Map<Long, String> auditMap = auditMapper.selectList(new LambdaQueryWrapper<CourseGradeAudit>() // 批量查询涉及课程的审核记录
                        .in(CourseGradeAudit::getCourseId, courseIds)) // IN 条件：课程 id 集合
                .stream().collect(Collectors.toMap(CourseGradeAudit::getCourseId, CourseGradeAudit::getStatus)); // 按课程 id 映射审核状态
        return scs.stream().map(sc -> { // 逐条组装成绩单视图
            GradeVO vo = new GradeVO(); // 创建成绩视图对象
            Course c = courseMap.get(sc.getCourseId()); // 取课程
            vo.setCourseId(sc.getCourseId()); // 填充课程 id
            vo.setCourseName(c != null ? c.getCourseName() : "未知课程"); // 填充课程名
            vo.setCredit(c != null ? c.getCredit() : BigDecimal.ZERO); // 填充学分
            String auditStatus = auditMap.get(sc.getCourseId()); // 取课程审核状态
            vo.setAuditStatus(auditStatus); // 填充审核状态
            boolean published = "PUBLISHED".equals(auditStatus); // 判断成绩是否已发布
            // 未发布的成绩不向学生泄露：分数/mark 只在已发布后返回，绩点与是否通过随之置空
            if (published) { // 成绩已发布
                vo.setScore(sc.getScore()); // 填充分数
                vo.setMark(sc.getMark()); // 填充标记
            }
            vo.setPassed(published && sc.getScore() != null // 是否通过：已发布且有分数
                    && sc.getScore().compareTo(new BigDecimal("60")) >= 0 // 且分数 >= 60
                    && "NORMAL".equals(sc.getMark())); // 且标记正常
            // 单科绩点：仅已发布且已有成绩时展示（未发布成绩不向学生泄露绩点）
            vo.setGradePoint(published && sc.getScore() != null // 已发布且有分数时
                    ? toGradePoint(sc.getScore()) : null); // 展示单科绩点，否则为 null
            return vo; // 返回成绩视图
        }).collect(Collectors.toList()); // 收集为列表
    }

    /** 学生：学业仪表盘 */
    public Map<String, Object> dashboard(Long studentId) { // 学生学业仪表盘：汇总学分、进度、GPA 与成绩列表
        Student student = studentMapper.selectById(studentId); // 查询学生
        List<GradeVO> gradeList = myGrades(studentId); // 查询成绩单列表
        BigDecimal required = student != null && student.getRequiredCredits() != null // 毕业要求学分（空则取 0）
                ? student.getRequiredCredits() : BigDecimal.ZERO; // 取要求学分
        BigDecimal earned = student != null && student.getTotalEarnedCredits() != null // 已修学分（空则取 0）
                ? student.getTotalEarnedCredits() : BigDecimal.ZERO; // 取已修学分
        // 学业进度 = 已修学分 / 毕业要求学分 × 100%，保留 1 位小数；要求学分为 0 时进度按 0 处理
        BigDecimal progress = required.compareTo(BigDecimal.ZERO) > 0 // 要求学分大于 0 时
                ? earned.divide(required, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100")) // 求百分比（先除保留 4 位再乘 100）
                    .setScale(1, RoundingMode.HALF_UP) // 结果保留 1 位小数
                : BigDecimal.ZERO; // 要求学分为 0 时进度按 0 处理
        Map<String, Object> result = new HashMap<>(); // 组装仪表盘数据
        result.put("totalEarnedCredits", earned); // 已修学分
        result.put("requiredCredits", required); // 毕业要求学分
        result.put("progressPercent", progress); // 学业进度百分比
        result.put("gpa", student != null && student.getGpa() != null ? student.getGpa() : BigDecimal.ZERO); // GPA（空取 0）
        result.put("gradeList", gradeList); // 成绩列表
        return result; // 返回仪表盘数据
    }

    /** 校验课程存在且属于当前教师（教秘可操作全部） */
    private Course checkTeacherCourse(Long courseId) { // 校验课程存在与操作权限（教秘可操作全部，教师仅限自己名下）
        Course course = courseMapper.selectById(courseId); // 按主键查询课程
        if (course == null) throw new BusinessException("课程不存在"); // 课程不存在则抛异常
        if (!"ADMIN".equals(UserContext.getRole()) && !course.getTeacherId().equals(UserContext.getUserId())) { // 非教秘且课程不属于当前教师
            throw new BusinessException(403, "无权限操作他人课程"); // 抛出 403 越权提示
        }
        return course; // 返回通过校验的课程
    }

    /** 成绩是否已锁定：非 DRAFT（已提交/审核/发布）即锁定，教师不可再改分 */
    private boolean isScoreLocked(Long courseId) { // 判断该课程成绩是否已锁定（教师不可再改）
        CourseGradeAudit audit = auditMapper.selectOne(new LambdaQueryWrapper<CourseGradeAudit>() // 查询审核记录
                .eq(CourseGradeAudit::getCourseId, courseId)); // 等值条件：course_id = 课程 id
        return audit != null && !"DRAFT".equals(audit.getStatus()); // 有记录且状态非草稿即视为锁定
    }

    private CourseGradeAudit getAudit(Course course) { // 获取课程的成绩审核记录（不存在则懒创建）
        // 首次接触成绩（录入/导入）时懒创建审核记录：初始 DRAFT 状态、归属该课程授课教师，
        // 后续提交/审核/发布都围绕这条记录推进状态机
        CourseGradeAudit audit = auditMapper.selectOne(new LambdaQueryWrapper<CourseGradeAudit>() // 查询审核记录
                .eq(CourseGradeAudit::getCourseId, course.getId())); // 等值条件：course_id = 课程 id
        if (audit == null) { // 不存在则创建
            audit = new CourseGradeAudit(); // 新建审核记录实体
            audit.setCourseId(course.getId()); // 关联课程 id
            audit.setTeacherId(course.getTeacherId()); // 归属授课教师
            audit.setStatus("DRAFT"); // 初始状态为草稿
            auditMapper.insert(audit); // 插入审核记录表
        }
        return audit; // 返回审核记录
    }
}
