package com.example.sms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.sms.common.BusinessException;
import com.example.sms.entity.Course;
import com.example.sms.entity.CourseGradeAudit;
import com.example.sms.entity.Staff;
import com.example.sms.entity.Student;
import com.example.sms.entity.StudentCourse;
import com.example.sms.mapper.CourseGradeAuditMapper;
import com.example.sms.mapper.CourseMapper;
import com.example.sms.mapper.StaffMapper;
import com.example.sms.mapper.StudentCourseMapper;
import com.example.sms.mapper.StudentMapper;
import com.example.sms.util.UserContext;
import com.example.sms.vo.CourseCardVO;
import com.example.sms.vo.EnrollMonitorVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 选课/退课服务
 */
@Service
public class EnrollService {

    @Autowired
    private StudentCourseMapper studentCourseMapper;

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private StaffMapper staffMapper;

    @Autowired
    private StudentMapper studentMapper;

    @Autowired
    private CourseGradeAuditMapper auditMapper;

    @Autowired
    private CourseService courseService;

    @Autowired
    private NotificationService notificationService;

    /** 学生选课 */
    @Transactional
    public void enroll(Long studentId, Long courseId) {
        // 行级锁（SELECT ... FOR UPDATE）：串行化同课程的容量检查与计数更新，防止并发超选
        Course course = courseMapper.selectByIdForUpdate(courseId);
        if (course == null) throw new BusinessException("课程不存在");
        if (!"PUBLISHED".equals(course.getStatus())) throw new BusinessException("课程未发布，无法选课");
        if (course.getCurrentEnrolled() >= course.getCapacity()) throw new BusinessException("课程已满员");
        // 成绩已发布的课程不可选课（否则选了退不掉，形成卡死状态）
        CourseGradeAudit audit = auditMapper.selectOne(new LambdaQueryWrapper<CourseGradeAudit>()
                .eq(CourseGradeAudit::getCourseId, courseId));
        if (audit != null && "PUBLISHED".equals(audit.getStatus())) {
            throw new BusinessException("该课程成绩已发布，不可选课");
        }

        Student student = studentMapper.selectById(studentId);
        if (student == null) throw new BusinessException("学生不存在");
        if ("SUSPENDED".equals(student.getStatus())) throw new BusinessException("休学状态不可选课");
        if ("FROZEN".equals(student.getStatus())) throw new BusinessException("账号已冻结，不可选课");

        Long count = studentCourseMapper.selectCount(new LambdaQueryWrapper<StudentCourse>()
                .eq(StudentCourse::getStudentId, studentId)
                .eq(StudentCourse::getCourseId, courseId));
        if (count > 0) throw new BusinessException("已选修该课程，不可重复选课");

        checkScheduleConflict(studentId, course);

        StudentCourse sc = new StudentCourse();
        sc.setStudentId(studentId);
        sc.setCourseId(courseId);
        studentCourseMapper.insert(sc);

        course.setCurrentEnrolled(course.getCurrentEnrolled() + 1);
        courseMapper.updateById(course);

        // 选课成功自动通知
        notificationService.sendSystem("ENROLL", "选课成功",
                "「" + course.getCourseName() + "」选课成功，可在“我的课表”中查看。",
                List.of(studentId));
    }

    /** 学生退课 */
    @Transactional
    public void drop(Long studentId, Long courseId) {
        // 先锁课程行（SELECT ... FOR UPDATE），串行化退课时的容量计数更新，防止并发退课丢失更新
        Course course = courseMapper.selectByIdForUpdate(courseId);
        if (course == null) throw new BusinessException("课程不存在");

        StudentCourse sc = studentCourseMapper.selectOne(new LambdaQueryWrapper<StudentCourse>()
                .eq(StudentCourse::getStudentId, studentId)
                .eq(StudentCourse::getCourseId, courseId));
        if (sc == null) throw new BusinessException("未选修该课程");

        // 成绩已发布则不可退课
        CourseGradeAudit audit = auditMapper.selectOne(new LambdaQueryWrapper<CourseGradeAudit>()
                .eq(CourseGradeAudit::getCourseId, courseId));
        if (audit != null && "PUBLISHED".equals(audit.getStatus())) {
            throw new BusinessException("成绩已发布，不可退课");
        }

        studentCourseMapper.deleteById(sc.getId());

        course.setCurrentEnrolled(Math.max(0, course.getCurrentEnrolled() - 1));
        courseMapper.updateById(course);
    }

    /** 校验上课时间冲突（支持"周一 1-2节"、多段以分号分隔） */
    private void checkScheduleConflict(Long studentId, Course newCourse) {
        if (newCourse.getSchedule() == null || newCourse.getSchedule().isBlank()) return;
        List<Course> myCourses = courseMapper.selectList(new LambdaQueryWrapper<Course>()
                .inSql(Course::getId,
                        "SELECT course_id FROM student_course WHERE student_id = " + studentId));
        for (Course mine : myCourses) {
            if (mine.getSchedule() == null || mine.getSchedule().isBlank()) continue;
            if (hasConflict(mine.getSchedule(), newCourse.getSchedule())) {
                throw new BusinessException("与课程[" + mine.getCourseName() + "]上课时间冲突");
            }
        }
    }

    /** 解析并比较两段排课时间是否有重叠 */
    private boolean hasConflict(String s1, String s2) {
        for (TimeSlot t1 : parseSlots(s1)) {
            for (TimeSlot t2 : parseSlots(s2)) {
                if (t1.day == t2.day && t1.start < t2.end && t2.start < t1.end) {
                    return true;
                }
            }
        }
        return false;
    }

    private static final Pattern DAY_PATTERN = Pattern.compile("周[一二三四五六日天]");
    private static final Pattern SLOT_PATTERN = Pattern.compile("(\\d+)\\s*[-~—至]\\s*(\\d+)节");

    private List<TimeSlot> parseSlots(String schedule) {
        List<TimeSlot> result = new ArrayList<>();
        for (String seg : schedule.split("[;；，,]")) {
            Matcher dayMatcher = DAY_PATTERN.matcher(seg);
            Matcher slotMatcher = SLOT_PATTERN.matcher(seg);
            if (dayMatcher.find() && slotMatcher.find()) {
                TimeSlot t = new TimeSlot();
                t.day = dayOfWeek(dayMatcher.group().charAt(1));
                t.start = Integer.parseInt(slotMatcher.group(1));
                t.end = Integer.parseInt(slotMatcher.group(2)) + 1;
                result.add(t);
            }
        }
        return result;
    }

    private int dayOfWeek(char c) {
        switch (c) {
            case '一': return 1;
            case '二': return 2;
            case '三': return 3;
            case '四': return 4;
            case '五': return 5;
            case '六': return 6;
            default: return 7;
        }
    }

    private static class TimeSlot {
        int day;
        int start;
        int end;
    }

    /** 学生：我的课表 */
    public List<CourseCardVO> myCourses(Long studentId) {
        List<Long> courseIds = studentCourseMapper.selectList(new LambdaQueryWrapper<StudentCourse>()
                        .eq(StudentCourse::getStudentId, studentId))
                .stream().map(StudentCourse::getCourseId).collect(Collectors.toList());
        if (courseIds.isEmpty()) return Collections.emptyList();
        List<Course> courses = courseMapper.selectList(new LambdaQueryWrapper<Course>()
                .in(Course::getId, courseIds));
        Map<Long, Staff> teacherMap = courseService.loadTeachers(courses);
        return courses.stream().map(c -> {
            CourseCardVO vo = new CourseCardVO();
            BeanUtils.copyProperties(c, vo);
            Staff teacher = teacherMap.get(c.getTeacherId());
            vo.setTeacherName(teacher != null ? teacher.getRealName() : "未知");
            vo.setEnrolled(true);
            vo.setFull(c.getCurrentEnrolled() >= c.getCapacity());
            return vo;
        }).collect(Collectors.toList());
    }

    /** 教秘：选课监控 */
    public List<EnrollMonitorVO> monitor() {
        List<Course> courses = courseMapper.selectList(new LambdaQueryWrapper<Course>()
                .orderByDesc(Course::getUpdatedAt));
        Map<Long, Staff> teacherMap = courseService.loadTeachers(courses);
        return courses.stream().map(c -> {
            EnrollMonitorVO vo = new EnrollMonitorVO();
            BeanUtils.copyProperties(c, vo);
            vo.setCourseId(c.getId());
            Staff teacher = teacherMap.get(c.getTeacherId());
            vo.setTeacherName(teacher != null ? teacher.getRealName() : "未知");
            vo.setRemain(Math.max(0, c.getCapacity() - c.getCurrentEnrolled()));
            return vo;
        }).collect(Collectors.toList());
    }

    /** 教秘：手动退课 */
    @Transactional
    public void adminDrop(Long studentId, Long courseId) {
        if (!"ADMIN".equals(UserContext.getRole())) {
            throw new BusinessException(403, "无权限，仅教学秘书可操作");
        }
        drop(studentId, courseId);
    }

    /**
     * 教秘：代学生选课（复用学生选课的全部校验：课程已发布、容量、重复选课、学生状态、成绩已发布等）
     * 本方法开启事务，保证选课记录与容量计数原子提交。
     * 选课成功通知由 enroll 内部统一发送（避免代选时重复通知）。
     */
    @Transactional
    public void adminEnroll(Long studentId, Long courseId) {
        if (!"ADMIN".equals(UserContext.getRole())) {
            throw new BusinessException(403, "无权限，仅教学秘书可操作");
        }
        enroll(studentId, courseId);
    }

    /** 学生已选课程 ID 列表（用于选课中心标记） */
    public List<Long> enrolledCourseIds(Long studentId) {
        return studentCourseMapper.selectList(new LambdaQueryWrapper<StudentCourse>()
                        .eq(StudentCourse::getStudentId, studentId))
                .stream().map(StudentCourse::getCourseId).collect(Collectors.toList());
    }
}
