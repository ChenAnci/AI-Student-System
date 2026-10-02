package com.example.sms.service; // 声明当前类所在的包：service 服务层

// ===== import 区域：引入 MyBatis-Plus 查询构造器、业务异常、实体、Mapper、用户上下文、VO、Spring 相关注解与 Java 工具 =====
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper; // MyBatis-Plus 的 Lambda 查询条件构造器
import com.example.sms.common.BusinessException; // 自定义业务异常类
import com.example.sms.entity.Course; // 课程实体类
import com.example.sms.entity.CourseGradeAudit; // 课程成绩审核记录实体类
import com.example.sms.entity.Staff; // 教职工实体类
import com.example.sms.entity.Student; // 学生实体类
import com.example.sms.entity.StudentCourse; // 学生选课关系实体类
import com.example.sms.mapper.CourseGradeAuditMapper; // 成绩审核记录表 Mapper 接口
import com.example.sms.mapper.CourseMapper; // 课程表 Mapper 接口
import com.example.sms.mapper.StaffMapper; // 教职工表 Mapper 接口
import com.example.sms.mapper.StudentCourseMapper; // 学生选课表 Mapper 接口
import com.example.sms.mapper.StudentMapper; // 学生表 Mapper 接口
import com.example.sms.util.UserContext; // 用户上下文工具：读取当前登录用户信息
import com.example.sms.vo.CourseCardVO; // 课程卡片视图对象
import com.example.sms.vo.EnrollMonitorVO; // 选课监控视图对象
import org.springframework.beans.BeanUtils; // Spring 属性拷贝工具
import org.springframework.beans.factory.annotation.Autowired; // Spring 依赖注入注解
import org.springframework.stereotype.Service; // Spring 服务层注解
import org.springframework.transaction.annotation.Transactional; // Spring 声明式事务注解
import java.util.ArrayList; // 动态数组集合
import java.util.Collections; // 集合工具类
import java.util.List; // 列表接口
import java.util.Map; // 键值映射接口
import java.util.function.Function; // 函数式接口
import java.util.regex.Matcher; // 正则匹配器：用于解析排课文本
import java.util.regex.Pattern; // 正则表达式类
import java.util.stream.Collectors; // Stream 收集器

/**
 * 选课/退课服务：学生选课/退课（含 FOR UPDATE 并发容量控制、成绩发布锁定、时间冲突校验）、
 * 我的课表、教秘选课监控与代选/代退
 */
@Service // 声明为 Spring 服务组件，交由容器管理
public class EnrollService { // 选课/退课服务类：学生选退课、时间冲突校验、我的课表、教秘选课监控与代选代退

    @Autowired // Spring 自动注入 StudentCourseMapper
    private StudentCourseMapper studentCourseMapper; // 学生选课表 Mapper

    @Autowired // Spring 自动注入 CourseMapper
    private CourseMapper courseMapper; // 课程表 Mapper

    @Autowired // Spring 自动注入 StaffMapper
    private StaffMapper staffMapper; // 教职工表 Mapper

    @Autowired // Spring 自动注入 StudentMapper
    private StudentMapper studentMapper; // 学生表 Mapper

    @Autowired // Spring 自动注入 CourseGradeAuditMapper
    private CourseGradeAuditMapper auditMapper; // 成绩审核记录表 Mapper：用于判断成绩是否已发布

    @Autowired // Spring 自动注入 CourseService
    private CourseService courseService; // 课程服务：复用其批量加载教师信息的方法

    @Autowired // Spring 自动注入 NotificationService
    private NotificationService notificationService; // 通知服务：选课成功后发送系统通知

    /**
     * 学生选课。
     * 调用逻辑：EnrollController.enroll → enrollService.enroll：学生在选课中心点击选课，流程为 FOR UPDATE 行锁 → 依次校验课程存在/已发布/未满员/成绩未发布/学生状态正常/未重复选课/无时间冲突 → 插入选课记录 + 人数+1 → 自动发送选课成功通知；教秘代选 adminEnroll 复用本方法。
     * 为什么：SELECT ... FOR UPDATE 行级锁把"容量检查 → 写选课记录 → 计数+1"串行化，防止并发选课读到相同 currentEnrolled 导致超卖；成绩已发布的课程禁止选课，避免"选了退不掉"的卡死状态。
     */
    @Transactional // 声明式事务：选课记录插入与人数递增原子提交
    public void enroll(Long studentId, Long courseId) { // 学生选课：校验通过后写入选课记录并更新人数
        // 选课校验顺序设计（先锁后查，环环相扣）：
        // 1) 先对课程行加 FOR UPDATE 行级锁，把"容量检查 → 写入选课记录 → 计数+1"做成原子串行操作，
        //    否则并发选课时多个请求会读到相同的 currentEnrolled 并同时通过容量校验，导致实际人数超容量（超卖）；
        // 2) 锁定后依次校验：课程存在 → 已发布 → 未满员 → 成绩未发布 → 学生存在且状态正常 → 未重复选课 → 无时间冲突。
        // 行级锁（SELECT ... FOR UPDATE）：串行化同课程的容量检查与计数更新，防止并发超选
        Course course = courseMapper.selectByIdForUpdate(courseId); // 加行级锁读取课程（其他线程须等锁释放后才能读写该行）
        if (course == null) throw new BusinessException("课程不存在"); // 课程不存在则抛异常
        if (!"PUBLISHED".equals(course.getStatus())) throw new BusinessException("课程未发布，无法选课"); // 课程未发布不可选
        if (course.getCurrentEnrolled() >= course.getCapacity()) throw new BusinessException("课程已满员"); // 已选人数达到容量即满员，拒绝选课
        // 成绩已发布的课程不可选课（否则选了退不掉，形成卡死状态）
        CourseGradeAudit audit = auditMapper.selectOne(new LambdaQueryWrapper<CourseGradeAudit>() // 查询该课程的成绩审核记录
                .eq(CourseGradeAudit::getCourseId, courseId)); // 等值条件：course_id = 课程 id
        if (audit != null && "PUBLISHED".equals(audit.getStatus())) { // 存在审核记录且成绩已发布
            throw new BusinessException("该课程成绩已发布，不可选课"); // 拒绝选课，避免"选了退不掉"
        }

        Student student = studentMapper.selectById(studentId); // 查询选课学生
        if (student == null) throw new BusinessException("学生不存在"); // 学生不存在则抛异常
        if ("SUSPENDED".equals(student.getStatus())) throw new BusinessException("休学状态不可选课"); // 休学学生不可选课
        if ("FROZEN".equals(student.getStatus())) throw new BusinessException("账号已冻结，不可选课"); // 冻结账号不可选课

        Long count = studentCourseMapper.selectCount(new LambdaQueryWrapper<StudentCourse>() // 统计该学生是否已选该课程
                .eq(StudentCourse::getStudentId, studentId) // 条件一：学生 id
                .eq(StudentCourse::getCourseId, courseId)); // 条件二：课程 id
        if (count > 0) throw new BusinessException("已选修该课程，不可重复选课"); // 已存在选课记录则拒绝重复选课

        checkScheduleConflict(studentId, course); // 校验新课与已选课程是否存在上课时间冲突

        StudentCourse sc = new StudentCourse(); // 创建选课关系实体
        sc.setStudentId(studentId); // 设置学生 id
        sc.setCourseId(courseId); // 设置课程 id
        studentCourseMapper.insert(sc); // 插入选课关系表

        course.setCurrentEnrolled(course.getCurrentEnrolled() + 1); // 课程已选人数 +1
        courseMapper.updateById(course); // 更新课程人数

        // 选课成功自动通知
        notificationService.sendSystem("ENROLL", "选课成功", // 发送系统通知：类型 ENROLL、标题"选课成功"
                "「" + course.getCourseName() + "」选课成功，可在“我的课表”中查看。", // 通知内容：课程名与提示
                List.of(studentId)); // 接收人：当前选课学生
    }

    /**
     * 学生退课。
     * 调用逻辑：EnrollController.drop → enrollService.drop：学生在"我的课表"点击退课，先锁课程行 → 校验已选且成绩未发布 → 删除选课记录 + 人数-1（下限 0）；教秘代退 adminDrop 复用本方法。
     * 为什么：退课同样先加 FOR UPDATE 行锁，防止并发退课对同一 currentEnrolled 同时 -1 造成计数丢失更新；成绩已发布后成绩/学分已定论且计入 GPA，不可退课。
     */
    @Transactional // 声明式事务：删除选课记录与人数递减原子提交
    public void drop(Long studentId, Long courseId) { // 学生退课：删除选课记录并更新人数
        // 退课同样先锁课程行：并发退课时若不加锁，两个请求可能同时对同一 currentEnrolled 做 -1，造成计数丢失更新
        // 先锁课程行（SELECT ... FOR UPDATE），串行化退课时的容量计数更新，防止并发退课丢失更新
        Course course = courseMapper.selectByIdForUpdate(courseId); // 加行级锁读取课程（串行化计数更新）
        if (course == null) throw new BusinessException("课程不存在"); // 课程不存在则抛异常

        StudentCourse sc = studentCourseMapper.selectOne(new LambdaQueryWrapper<StudentCourse>() // 查询该学生的选课记录
                .eq(StudentCourse::getStudentId, studentId) // 条件一：学生 id
                .eq(StudentCourse::getCourseId, courseId)); // 条件二：课程 id
        if (sc == null) throw new BusinessException("未选修该课程"); // 未选该课程则抛异常

        // 成绩已发布则不可退课
        CourseGradeAudit audit = auditMapper.selectOne(new LambdaQueryWrapper<CourseGradeAudit>() // 查询该课程的成绩审核记录
                .eq(CourseGradeAudit::getCourseId, courseId)); // 等值条件：course_id = 课程 id
        if (audit != null && "PUBLISHED".equals(audit.getStatus())) { // 成绩已发布
            throw new BusinessException("成绩已发布，不可退课"); // 成绩定论后不可退课（已计入 GPA）
        }

        studentCourseMapper.deleteById(sc.getId()); // 删除选课记录

        course.setCurrentEnrolled(Math.max(0, course.getCurrentEnrolled() - 1)); // 已选人数 -1（下限 0，防止计数变负数）
        courseMapper.updateById(course); // 更新课程人数
    }

    /** 校验上课时间冲突（支持"周一 1-2节"、多段以分号分隔） */
    private void checkScheduleConflict(Long studentId, Course newCourse) { // 检查新课与该学生已选课程是否时间冲突
        // 新课未排课则无需冲突检查；否则一次子查询取出该学生已选的全部课程（仅取 course_id），
        // 逐门与新课做排课时间片比较，任一时间重叠即拒绝选课，防止学生同一时段上两门课。
        if (newCourse.getSchedule() == null || newCourse.getSchedule().isBlank()) return; // 新课未排课则无需检查
        List<Course> myCourses = courseMapper.selectList(new LambdaQueryWrapper<Course>() // 一次子查询取出该学生已选课程
                .inSql(Course::getId, // 使用原生 SQL 子查询：id 属于该学生的选课课程
                        "SELECT course_id FROM student_course WHERE student_id = " + studentId)); // 子查询：从选课表取该学生的课程 id
        for (Course mine : myCourses) { // 逐门已选课程与新课程比较
            if (mine.getSchedule() == null || mine.getSchedule().isBlank()) continue; // 已选课程未排课则跳过
            if (hasConflict(mine.getSchedule(), newCourse.getSchedule())) { // 两门课排课时间存在重叠
                throw new BusinessException("与课程[" + mine.getCourseName() + "]上课时间冲突"); // 拒绝选课并提示冲突课程名
            }
        }
    }

    /** 解析并比较两段排课时间是否有重叠 */
    private boolean hasConflict(String s1, String s2) { // 判断两段排课时间是否重叠
        // 两段排课各自解析为时间片集合后两两比较；同一星期且区间互相穿插（左闭右开 start < end）即判冲突
        for (TimeSlot t1 : parseSlots(s1)) { // 遍历第一段排课解析出的时间片
            for (TimeSlot t2 : parseSlots(s2)) { // 遍历第二段排课解析出的时间片
                if (t1.day == t2.day && t1.start < t2.end && t2.start < t1.end) { // 同星期且两个左闭右开区间有交集
                    return true; // 判定为冲突
                }
            }
        }
        return false; // 无任何时间片重叠，不冲突
    }

    private static final Pattern DAY_PATTERN = Pattern.compile("周[一二三四五六日天]"); // 匹配"周X"星期文本的正则
    private static final Pattern SLOT_PATTERN = Pattern.compile("(\\d+)\\s*[-~—至]\\s*(\\d+)节"); // 匹配"起始-结束节"的正则（支持 - ~ — 至 等分隔符）

    private List<TimeSlot> parseSlots(String schedule) { // 把排课文本解析为时间片列表
        // 解析排课文本，如"周一 1-2节;周三 3-4节"：按 ;；，, 分号/逗号切成多段，
        // 每段分别用正则提取"周X"和"起始-结束节"；end 取"末节+1"转成左闭右开区间 [start, end)，
        // 这样"1-2节"=[1,3)、"3-4节"=[3,5) 首尾相接不会误判为重叠。
        List<TimeSlot> result = new ArrayList<>(); // 存放解析出的时间片
        for (String seg : schedule.split("[;；，,]")) { // 按分号/逗号把排课文本切成多段
            Matcher dayMatcher = DAY_PATTERN.matcher(seg); // 在段落中匹配"周X"
            Matcher slotMatcher = SLOT_PATTERN.matcher(seg); // 在段落中匹配节次区间
            if (dayMatcher.find() && slotMatcher.find()) { // 星期与节次都能匹配到，说明该段有效
                TimeSlot t = new TimeSlot(); // 创建时间片对象
                t.day = dayOfWeek(dayMatcher.group().charAt(1)); // 取"周X"中的 X 字符映射为星期数字
                t.start = Integer.parseInt(slotMatcher.group(1)); // 起始节：正则第一组
                t.end = Integer.parseInt(slotMatcher.group(2)) + 1; // 结束节 +1：转成左闭右开区间
                result.add(t); // 加入时间片列表
            }
        }
        return result; // 返回解析结果
    }

    private int dayOfWeek(char c) { // 把星期汉字映射为 1-7 数字
        // 星期映射为 1-7 数字（日=7），便于后续区间比较
        switch (c) { // 按汉字分支返回数字
            case '一': return 1; // 周一 = 1
            case '二': return 2; // 周二 = 2
            case '三': return 3; // 周三 = 3
            case '四': return 4; // 周四 = 4
            case '五': return 5; // 周五 = 5
            case '六': return 6; // 周六 = 6
            default: return 7; // 周日/天 = 7（其余字符兜底）
        }
    }

    /** 排课时间片：day 为星期（1-7，日=7），[start, end) 左闭右开节次区间 */
    private static class TimeSlot { // 排课时间片内部类
        int day; // 星期数字（1-7，周日为 7）
        int start; // 起始节（含）
        int end; // 结束节（不含，左闭右开）
    }

    /** 学生：我的课表 */
    public List<CourseCardVO> myCourses(Long studentId) { // 查询当前学生的已选课程列表（我的课表）
        List<Long> courseIds = studentCourseMapper.selectList(new LambdaQueryWrapper<StudentCourse>() // 查询该学生的选课记录
                        .eq(StudentCourse::getStudentId, studentId)) // 条件：学生 id
                .stream().map(StudentCourse::getCourseId).collect(Collectors.toList()); // 取出课程 id 列表
        if (courseIds.isEmpty()) return Collections.emptyList(); // 未选任何课程直接返回空列表
        List<Course> courses = courseMapper.selectList(new LambdaQueryWrapper<Course>() // 按课程 id 批量查询课程
                .in(Course::getId, courseIds)); // IN 条件：课程 id 集合
        Map<Long, Staff> teacherMap = courseService.loadTeachers(courses); // 批量加载授课教师信息
        return courses.stream().map(c -> { // 逐门课程组装卡片视图
            CourseCardVO vo = new CourseCardVO(); // 创建课程卡片视图对象
            BeanUtils.copyProperties(c, vo); // 拷贝课程同名字段
            Staff teacher = teacherMap.get(c.getTeacherId()); // 从教师映射取授课教师
            vo.setTeacherName(teacher != null ? teacher.getRealName() : "未知"); // 填充教师姓名
            vo.setEnrolled(true); // 我的课表中的课程必然是已选状态
            vo.setFull(c.getCurrentEnrolled() >= c.getCapacity()); // 计算课程是否满员
            return vo; // 返回组装好的视图
        }).collect(Collectors.toList()); // 收集为视图列表
    }

    /** 教秘：选课监控 */
    public List<EnrollMonitorVO> monitor() { // 教秘选课监控：列出全部课程及剩余名额
        // 教秘选课监控：列出全部课程并按更新时间倒序，计算每门课的剩余名额，便于及时发现热门/满员课程
        List<Course> courses = courseMapper.selectList(new LambdaQueryWrapper<Course>() // 查询全部课程
                .orderByDesc(Course::getUpdatedAt)); // 按更新时间倒序
        Map<Long, Staff> teacherMap = courseService.loadTeachers(courses); // 批量加载授课教师信息
        return courses.stream().map(c -> { // 逐门课程组装监控视图
            EnrollMonitorVO vo = new EnrollMonitorVO(); // 创建选课监控视图对象
            BeanUtils.copyProperties(c, vo); // 拷贝课程同名字段
            vo.setCourseId(c.getId()); // 显式设置课程 id
            Staff teacher = teacherMap.get(c.getTeacherId()); // 从教师映射取授课教师
            vo.setTeacherName(teacher != null ? teacher.getRealName() : "未知"); // 填充教师姓名
            vo.setRemain(Math.max(0, c.getCapacity() - c.getCurrentEnrolled())); // 剩余名额 = 容量 - 已选（下限 0）
            return vo; // 返回组装好的视图
        }).collect(Collectors.toList()); // 收集为视图列表
    }

    /** 教秘：手动退课 */
    @Transactional // 声明式事务：退课操作原子提交
    public void adminDrop(Long studentId, Long courseId) { // 教秘代学生退课
        // 教秘代退课：先做角色鉴权，再复用学生退课逻辑（含成绩已发布不可退等全部校验），保持口径一致
        if (!"ADMIN".equals(UserContext.getRole())) { // 当前用户不是教学秘书
            throw new BusinessException(403, "无权限，仅教学秘书可操作"); // 抛出 403 越权提示
        }
        drop(studentId, courseId); // 复用学生退课逻辑（含全部校验）
    }

    /**
     * 教秘：代学生选课（复用学生选课的全部校验：课程已发布、容量、重复选课、学生状态、成绩已发布等）
     * 本方法开启事务，保证选课记录与容量计数原子提交。
     * 选课成功通知由 enroll 内部统一发送（避免代选时重复通知）。
     */
    @Transactional // 声明式事务：选课操作原子提交
    public void adminEnroll(Long studentId, Long courseId) { // 教秘代学生选课
        // 教秘代学生选课：角色校验后直接复用 enroll 的全部业务校验与通知逻辑（见上方注释）
        if (!"ADMIN".equals(UserContext.getRole())) { // 当前用户不是教学秘书
            throw new BusinessException(403, "无权限，仅教学秘书可操作"); // 抛出 403 越权提示
        }
        enroll(studentId, courseId); // 复用学生选课逻辑（含全部校验与通知）
    }

    /** 学生已选课程 ID 列表（用于选课中心标记） */
    public List<Long> enrolledCourseIds(Long studentId) { // 查询学生已选的全部课程 id（供选课中心标记已选状态）
        return studentCourseMapper.selectList(new LambdaQueryWrapper<StudentCourse>() // 查询该学生的选课记录
                        .eq(StudentCourse::getStudentId, studentId)) // 条件：学生 id
                .stream().map(StudentCourse::getCourseId).collect(Collectors.toList()); // 取出课程 id 列表
    }
}
