package com.example.sms.service; // 声明当前类所在的包：service 服务层

// ===== import 区域：引入 MyBatis-Plus 查询构造器、业务异常、DTO、实体、Mapper、用户上下文、VO、Spring 相关注解与 Java 工具 =====
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper; // MyBatis-Plus 的 Lambda 查询条件构造器：类型安全地拼装查询条件
import com.example.sms.common.BusinessException; // 自定义业务异常类：抛出后由全局异常处理器统一转换响应
import com.example.sms.dto.CourseFormDTO; // 课程表单 DTO（创建/编辑课程时的请求参数）
import com.example.sms.entity.Course; // 课程实体类
import com.example.sms.entity.CourseGradeAudit; // 课程成绩审核记录实体类
import com.example.sms.entity.Staff; // 教职工实体类
import com.example.sms.entity.StudentCourse; // 学生选课关系实体类
import com.example.sms.mapper.CourseGradeAuditMapper; // 成绩审核记录表 Mapper 接口
import com.example.sms.mapper.CourseMapper; // 课程表 Mapper 接口
import com.example.sms.mapper.StaffMapper; // 教职工表 Mapper 接口
import com.example.sms.mapper.StudentCourseMapper; // 学生选课表 Mapper 接口
import com.example.sms.util.UserContext; // 用户上下文工具：读取当前登录用户信息
import com.example.sms.vo.CourseCardVO; // 选课中心课程卡片视图对象
import com.example.sms.vo.MyCourseVO; // 教师端/教秘端课程列表视图对象
import org.springframework.beans.BeanUtils; // Spring 属性拷贝工具：同名属性批量赋值
import org.springframework.beans.factory.annotation.Autowired; // Spring 依赖注入注解
import org.springframework.cache.annotation.Cacheable; // Spring 缓存注解：方法结果按 key 缓存
import org.springframework.stereotype.Service; // Spring 服务层注解
import org.springframework.transaction.annotation.Transactional; // Spring 声明式事务注解
import java.math.BigDecimal; // 高精度十进制数：用于学分等数值运算
import java.util.Collections; // 集合工具类
import java.util.List; // 列表接口
import java.util.Map; // 键值映射接口
import java.util.Objects; // 对象工具类：提供 null 安全的 equals 比较
import java.util.function.Function; // 函数式接口：用于 Collectors.toMap 的值提取器
import java.util.stream.Collectors; // Stream 收集器

/**
 * 课程管理服务：课程创建/编辑/发布/删除（含 UNPUBLISHED -> PUBLISHED 单向状态机、
 * 教师归属权限校验）、教师端"我的课程"、教秘全部课程、选课中心已发布课程列表（Redis 缓存 30s）
 */
@Service // 声明为 Spring 服务组件，交由容器管理
public class CourseService { // 课程管理服务类：课程的增删改查、发布锁定、归属校验与选课中心课程列表

    @Autowired // Spring 自动注入 CourseMapper
    private CourseMapper courseMapper; // 课程表 Mapper

    @Autowired // Spring 自动注入 StaffMapper
    private StaffMapper staffMapper; // 教职工表 Mapper：用于校验授课教师有效性

    @Autowired // Spring 自动注入 CourseGradeAuditMapper
    private CourseGradeAuditMapper auditMapper; // 成绩审核记录表 Mapper：用于带出审核状态与删除课程时清理审核行

    @Autowired // Spring 自动注入 StudentCourseMapper
    private StudentCourseMapper studentCourseMapper; // 学生选课表 Mapper：用于统计选课人数/查找选课学生

    @Autowired // Spring 自动注入 NotificationService
    private NotificationService notificationService; // 通知服务：调课时向选课学生发送系统通知

    /** 当前用户是否为教学秘书（ADMIN） */
    private boolean isAdmin() { // 判断当前登录用户是否是教学秘书
        return "ADMIN".equals(UserContext.getRole()); // 角色为 ADMIN 则返回 true
    }

    /**
     * 教师创建课程（默认 UNPUBLISHED）。
     * 调用逻辑：CourseController.create → courseService.createCourse：教师/教秘在课程管理页提交课程表单（教秘必须指定授课教师、教师强制归属自己），以 UNPUBLISHED 落库并返回，前端刷新"我的课程/全部课程"列表。
     * 为什么：新课程统一未发布状态，未发布前学生不可见、不可选；授课教师归属在创建时即锁定，防止教师替他人建课或课程无人认领。
     */
    public Course createCourse(CourseFormDTO dto) { // 创建课程：返回落库后的课程实体（含自增 ID）
        Course course = new Course(); // 创建课程实体
        BeanUtils.copyProperties(dto, course); // 把 DTO 中同名属性批量拷贝到课程实体（课程名、学分、学时等）
        if (isAdmin()) { // 当前操作者是教学秘书
            // 教学秘书创建课程必须指定授课教师并校验其为在职教师，避免课程无人认领
            if (dto.getTeacherId() == null) { // 未指定授课教师
                throw new BusinessException("教学秘书创建课程必须指定授课教师"); // 抛出必填提示
            }
            course.setTeacherId(requireValidTeacher(dto.getTeacherId()).getId()); // 校验教师有效后设置为课程归属教师
        } else { // 当前操作者是普通教师
            // 教师创建：授课人强制取当前登录教师（忽略请求里的 teacherId，防止教师替别人建课）
            course.setTeacherId(UserContext.getUserId()); // 授课教师强制为当前登录教师
        }
        // 新课程统一以未发布状态落库：容量计数从 0 开始，未发布前学生不可见、不可选
        course.setCurrentEnrolled(0); // 已选人数初始为 0
        course.setStatus("UNPUBLISHED"); // 新课程统一为未发布状态
        courseMapper.insert(course); // 插入课程表
        return course; // 返回落库后的课程（含自增主键，供前端刷新列表）
    }

    /**
     * 编辑课程：未发布可全量编辑；已发布仅允许调课（修改上课时间/地点），其余字段强制保留，变化时通知选课学生。
     * 调用逻辑：CourseController.update → courseService.updateCourse：编辑页提交后先经 getEditableCourse 校验归属（教秘可改全部、教师仅限自己名下），已发布课程仅写 schedule/location，若有变化则调 notificationService.sendSystem 通知选课学生，前端刷新课程列表。
     * 为什么：发布后课程基本信息进入锁定态，只允许调课以保持选课/成绩流程口径稳定；调课通知让学生及时知晓时间地点变更，避免到错教室上课。
     */
    public void updateCourse(Long id, CourseFormDTO dto) { // 编辑课程：按发布状态区分"仅调课"与"全量编辑"
        Course course = getEditableCourse(id); // 获取可编辑课程（内含归属权限校验）
        if ("PUBLISHED".equals(course.getStatus())) { // 课程已发布：进入"仅调课"模式
            String oldSchedule = course.getSchedule(); // 记录原上课时间（用于对比是否有变化）
            String oldLocation = course.getLocation(); // 记录原上课地点（用于对比是否有变化）
            course.setSchedule(dto.getSchedule()); // 更新上课时间
            course.setLocation(dto.getLocation()); // 更新上课地点
            courseMapper.updateById(course); // 落库更新（已发布课程只允许改这两项）
            boolean changed = !Objects.equals(oldSchedule, dto.getSchedule()) // 上课时间是否发生变化
                    || !Objects.equals(oldLocation, dto.getLocation()); // 或上课地点是否发生变化
            if (changed) { // 调课信息有实际变化
                List<Long> studentIds = studentCourseMapper.selectList( // 查询选修该课程的全部学生
                                new LambdaQueryWrapper<StudentCourse>() // 构建选课查询条件
                                        .eq(StudentCourse::getCourseId, id)) // 等值条件：course_id = 课程 id
                        .stream().map(StudentCourse::getStudentId).collect(Collectors.toList()); // 取出学生 ID 列表
                if (!studentIds.isEmpty()) { // 有选课学生需要通知
                    notificationService.sendSystem("COURSE_CHANGE", "调课通知", // 发送系统调课通知（类型 COURSE_CHANGE）
                            "「" + course.getCourseName() + "」课程信息已调整：上课时间 " // 通知内容：课程名 + 新的时间地点
                                    + (dto.getSchedule() == null ? "未指定" : dto.getSchedule()) // 时间未填则显示"未指定"
                                    + "，上课地点 " + (dto.getLocation() == null ? "未指定" : dto.getLocation()), // 地点未填则显示"未指定"
                            studentIds); // 接收人：该课程的全部选课学生
                }
            }
            return; // 已发布课程编辑到此结束
        }
        course.setCourseCode(dto.getCourseCode()); // 未发布课程：全量编辑，更新课程代码
        course.setCourseName(dto.getCourseName()); // 更新课程名称
        course.setCredit(dto.getCredit()); // 更新学分
        course.setHours(dto.getHours()); // 更新学时
        course.setCoverImageUrl(dto.getCoverImageUrl()); // 更新封面图地址
        course.setSchedule(dto.getSchedule()); // 更新上课时间
        course.setLocation(dto.getLocation()); // 更新上课地点
        course.setCapacity(dto.getCapacity()); // 更新容量
        // 未发布状态下，仅教秘可重新指定授课教师（教师编辑时不允许更换课程归属）
        if (isAdmin() && dto.getTeacherId() != null) { // 教学秘书且传了新教师
            course.setTeacherId(requireValidTeacher(dto.getTeacherId()).getId()); // 校验新教师有效后重新归属
        }
        courseMapper.updateById(course); // 落库更新
    }

    /** 校验授课教师有效（存在且为在职教师账号），返回教师实体 */
    private Staff requireValidTeacher(Long teacherId) { // 校验教师 ID 对应的账号是"在职教师"，否则抛异常
        Staff teacher = staffMapper.selectById(teacherId); // 按 ID 查询教职工
        if (teacher == null || !"TEACHER".equals(teacher.getRoleType()) || !"ENABLED".equals(teacher.getStatus())) { // 教师不存在、或角色不是教师、或账号未启用
            throw new BusinessException("授课教师无效：需选择在职（ENABLED）的教师账号"); // 提示选择在职教师
        }
        return teacher; // 返回校验通过的教师实体
    }

    /**
     * 发布课程（锁定）。
     * 调用逻辑：CourseController.publish → courseService.publishCourse：教师/教秘在课程管理页点击发布，UNPUBLISHED → PUBLISHED 后课程进入选课中心可见，前端刷新课程列表。
     * 为什么：发布是单向状态机且不可逆——发布即锁定课程基本信息与删除路径，后续选课、成绩录入审核都以稳定口径进行；已发布再次发布直接拒绝。
     */
    public void publishCourse(Long id) { // 发布课程：把课程从未发布改为已发布（单向、不可逆）
        Course course = getEditableCourse(id); // 获取可编辑课程（内含归属权限校验）
        // 发布是单向状态机：UNPUBLISHED -> PUBLISHED 后即"锁定"，
        // 之后不可再改课程基本信息、不可删除，仅允许调课；保证选课与成绩流程以稳定口径进行。
        if ("PUBLISHED".equals(course.getStatus())) { // 课程已经发布
            throw new BusinessException("课程已发布"); // 拒绝重复发布
        }
        course.setStatus("PUBLISHED"); // 状态改为已发布
        courseMapper.updateById(course); // 落库更新
    }

    /**
     * 删除课程（仅未发布；有选课记录也不可删除）。
     * 调用逻辑：CourseController.delete → courseService.deleteCourse：删除前校验课程未发布且无选课记录，事务内先删该课程的草稿成绩审核记录再删课程，前端刷新列表。
     * 为什么：已发布/有选课记录的课程删除会造成选课与成绩数据孤儿，故禁止；事务内显式清理 DRAFT 审核行，避免外键约束（fk_audit_course）阻止删除路径。
     */
    @Transactional // 声明式事务：清理审核记录与删除课程原子完成
    public void deleteCourse(Long id) { // 删除课程：仅未发布且无选课记录的课程可删
        Course course = getEditableCourse(id); // 获取可编辑课程（内含归属权限校验）
        // 已发布课程涉及学生选课与成绩流程，删除会造成数据孤儿，故禁止；
        // 即便未发布，只要有学生选课记录也不可删（保证 student_course 无悬挂引用）
        if ("PUBLISHED".equals(course.getStatus())) { // 课程已发布
            throw new BusinessException("已发布课程不可删除"); // 拒绝删除已发布课程
        }
        Long enrolled = studentCourseMapper.selectCount(new LambdaQueryWrapper<StudentCourse>() // 统计该课程的选课人数
                .eq(StudentCourse::getCourseId, id)); // 等值条件：course_id = 课程 id
        if (enrolled > 0) { // 存在选课记录
            throw new BusinessException("该课程已有学生选课，不可删除"); // 拒绝删除，避免悬挂选课数据
        }
        // 事务内显式级联清理（F-1）：删除课程的草稿成绩审核记录，避免外键约束（fk_audit_course）阻止删除。
        // 未发布课程虽不能正式录成绩，但可能留有 DRAFT 状态的审核行，须一并清除保证删除路径通畅。
        auditMapper.delete(new LambdaQueryWrapper<CourseGradeAudit>() // 删除该课程的审核记录
                .eq(CourseGradeAudit::getCourseId, id)); // 等值条件：course_id = 课程 id
        courseMapper.deleteById(course.getId()); // 删除课程记录
    }

    /** 获取可编辑课程（权限校验：教师只能操作自己的课程） */
    private Course getEditableCourse(Long id) { // 查询课程并做归属权限校验（所有课程写操作的前置方法）
        Course course = courseMapper.selectById(id); // 按主键查询课程
        if (course == null) throw new BusinessException("课程不存在"); // 课程不存在则抛异常
        // 权限归属校验：教学秘书（ADMIN）可操作全部课程；教师只能操作"自己名下"的课程，
        // 防止教师越权改/删他人课程（所有课程写操作都先经过此方法）
        if (!isAdmin() && !course.getTeacherId().equals(UserContext.getUserId())) { // 非教秘且课程不属于当前教师
            throw new BusinessException(403, "无权限操作他人课程"); // 抛出 403 越权提示
        }
        return course; // 返回通过校验的课程实体
    }

    /** 教师端：我的课程（含成绩审核状态） */
    public List<MyCourseVO> listMyCourses() { // 查询当前教师名下的课程列表
        // 教师端"我的课程"：只查当前登录教师名下的课程，按更新时间倒序
        List<Course> courses = courseMapper.selectList(new LambdaQueryWrapper<Course>() // 查询课程
                .eq(Course::getTeacherId, UserContext.getUserId()) // 条件：授课教师 = 当前登录教师
                .orderByDesc(Course::getUpdatedAt)); // 按更新时间倒序，最新编辑的课程排前面
        return toMyCourseVO(courses); // 转换为课程视图（附带审核状态与教师姓名）
    }

    /** 教秘端：全部课程 */
    public List<MyCourseVO> listAllCourses(String keyword) { // 查询全部课程（教秘视角），支持关键词搜索
        // 教秘可查看全部课程，支持按课程名/课程代码模糊搜索（关键词为空时不过滤）
        LambdaQueryWrapper<Course> wrapper = new LambdaQueryWrapper<>(); // 创建课程查询条件构造器
        wrapper.and(keyword != null && !keyword.isBlank(), w -> w // 关键词非空时整体包裹一组条件
                        .like(Course::getCourseName, keyword) // 按课程名模糊匹配
                        .or().like(Course::getCourseCode, keyword)) // 或按课程代码模糊匹配
                .orderByDesc(Course::getUpdatedAt); // 按更新时间倒序
        return toMyCourseVO(courseMapper.selectList(wrapper)); // 查询并转换为课程视图
    }

    /** 课程列表 -> 教师端/教秘端视图：批量带出成绩审核状态与授课教师姓名，避免逐条查库 */
    private List<MyCourseVO> toMyCourseVO(List<Course> courses) { // 批量转换课程实体为视图对象（一次查出审核状态与教师信息）
        if (courses.isEmpty()) return Collections.emptyList(); // 无课程直接返回空列表
        Map<Long, String> auditMap = auditMapper.selectList( // 批量查询这些课程的审核记录
                        new LambdaQueryWrapper<CourseGradeAudit>() // 构建审核记录查询条件
                                .in(CourseGradeAudit::getCourseId, courses.stream().map(Course::getId).collect(Collectors.toList()))) // IN 条件：课程 id 集合
                .stream().collect(Collectors.toMap(CourseGradeAudit::getCourseId, CourseGradeAudit::getStatus)); // 按课程 id 映射为审核状态，避免逐条查库（N+1）
        Map<Long, Staff> teacherMap = loadTeachers(courses); // 批量加载课程对应的教师信息
        return courses.stream().map(c -> { // 逐门课程组装视图
            MyCourseVO vo = new MyCourseVO(); // 创建课程视图对象
            BeanUtils.copyProperties(c, vo); // 拷贝课程同名字段到视图
            vo.setAuditStatus(auditMap.get(c.getId())); // 填充成绩审核状态（可能为 null，表示未录入成绩）
            Staff teacher = teacherMap.get(c.getTeacherId()); // 从教师映射取授课教师
            vo.setTeacherName(teacher != null ? teacher.getRealName() : "未知"); // 填充教师姓名（查不到显示"未知"）
            return vo; // 返回组装好的视图
        }).collect(Collectors.toList()); // 收集为视图列表
    }

    /**
     * 选课中心课程列表（Redis 缓存 30s，缓解高并发下重复查询 MySQL）。
     * key 含 studentId（结果含个人已选状态 enrolled）与筛选参数；选课人数容量最多滞后 30s。
     */
    @Cacheable(value = "courseCenter", // 使用 Redis 缓存：缓存名 courseCenter
            key = "#studentId + ':' + (#keyword != null ? #keyword : '') + ':'" // 缓存 key 第一段：学生 ID + 关键词
                    + " + (#minCredit != null ? #minCredit : '0') + ':'" // 缓存 key 第二段：最低学分（空取 0）
                    + " + (#maxCredit != null ? #maxCredit : '0')") // 缓存 key 第三段：最高学分（空取 0），不同筛选条件缓存不同结果
    public List<CourseCardVO> listPublishedForStudent(Long studentId, List<Long> enrolledCourseIds, // 选课中心列表：参数为学生 ID、已选课程 ID 列表
                                                      String keyword, BigDecimal minCredit, BigDecimal maxCredit) { // 以及课程名关键词、学分范围筛选
        List<Course> courses = courseMapper.selectCenterPublished(keyword, minCredit, maxCredit); // 调用自定义 SQL 查询已发布且符合筛选条件的课程
        Map<Long, Staff> teacherMap = loadTeachers(courses); // 批量加载课程对应的教师信息
        return courses.stream().map(c -> { // 逐门课程组装卡片视图
            CourseCardVO vo = new CourseCardVO(); // 创建课程卡片视图对象
            BeanUtils.copyProperties(c, vo); // 拷贝课程同名字段
            Staff teacher = teacherMap.get(c.getTeacherId()); // 从教师映射取授课教师
            vo.setTeacherName(teacher != null ? teacher.getRealName() : "未知"); // 填充教师姓名
            vo.setEnrolled(enrolledCourseIds != null && enrolledCourseIds.contains(c.getId())); // 标记该课程是否已被当前学生选中（用于前端按钮状态）
            vo.setFull(c.getCapacity() != null && c.getCurrentEnrolled() != null // 判断课程是否已满员
                    && c.getCurrentEnrolled() >= c.getCapacity()); // 已选人数达到容量即视为满员
            return vo; // 返回组装好的卡片视图
        }).collect(Collectors.toList()); // 收集为视图列表
    }

    /** 按 ID 批量加载教师信息 */
    public Map<Long, Staff> loadTeachers(List<Course> courses) { // 根据课程列表批量加载授课教师映射（避免逐条查库）
        if (courses == null || courses.isEmpty()) return Collections.emptyMap(); // 无课程直接返回空映射
        List<Long> teacherIds = courses.stream().map(Course::getTeacherId).distinct().collect(Collectors.toList()); // 提取所有教师 ID 并去重（一门课一个教师）
        return staffMapper.selectBatchIds(teacherIds).stream() // 按 ID 批量查询教职工
                .collect(Collectors.toMap(Staff::getId, Function.identity())); // 转成"教师 ID -> 教师实体"映射
    }
}
