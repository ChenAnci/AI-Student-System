package com.example.sms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.sms.common.BusinessException;
import com.example.sms.dto.CourseFormDTO;
import com.example.sms.entity.Course;
import com.example.sms.entity.CourseGradeAudit;
import com.example.sms.entity.Staff;
import com.example.sms.mapper.CourseGradeAuditMapper;
import com.example.sms.mapper.CourseMapper;
import com.example.sms.mapper.StaffMapper;
import com.example.sms.util.UserContext;
import com.example.sms.vo.CourseCardVO;
import com.example.sms.vo.MyCourseVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 课程管理服务
 */
@Service
public class CourseService {

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private StaffMapper staffMapper;

    @Autowired
    private CourseGradeAuditMapper auditMapper;

    private boolean isAdmin() {
        return "ADMIN".equals(UserContext.getRole());
    }

    /** 教师创建课程（默认 UNPUBLISHED） */
    public Course createCourse(CourseFormDTO dto) {
        Course course = new Course();
        BeanUtils.copyProperties(dto, course);
        if (isAdmin()) {
            // 教学秘书创建课程必须指定授课教师并校验其为在职教师，避免课程无人认领
            if (dto.getTeacherId() == null) {
                throw new BusinessException("教学秘书创建课程必须指定授课教师");
            }
            course.setTeacherId(requireValidTeacher(dto.getTeacherId()).getId());
        } else {
            course.setTeacherId(UserContext.getUserId());
        }
        course.setCurrentEnrolled(0);
        course.setStatus("UNPUBLISHED");
        courseMapper.insert(course);
        return course;
    }

    /** 编辑课程（仅 UNPUBLISHED 状态；教师只能改自己的，教秘可改全部） */
    public void updateCourse(Long id, CourseFormDTO dto) {
        Course course = getEditableCourse(id);
        course.setCourseCode(dto.getCourseCode());
        course.setCourseName(dto.getCourseName());
        course.setCredit(dto.getCredit());
        course.setHours(dto.getHours());
        course.setCoverImageUrl(dto.getCoverImageUrl());
        course.setSchedule(dto.getSchedule());
        course.setLocation(dto.getLocation());
        course.setCapacity(dto.getCapacity());
        if (isAdmin() && dto.getTeacherId() != null) {
            course.setTeacherId(requireValidTeacher(dto.getTeacherId()).getId());
        }
        courseMapper.updateById(course);
    }

    /** 校验授课教师有效（存在且为在职教师账号），返回教师实体 */
    private Staff requireValidTeacher(Long teacherId) {
        Staff teacher = staffMapper.selectById(teacherId);
        if (teacher == null || !"TEACHER".equals(teacher.getRoleType()) || !"ENABLED".equals(teacher.getStatus())) {
            throw new BusinessException("授课教师无效：需选择在职（ENABLED）的教师账号");
        }
        return teacher;
    }

    /** 发布课程（锁定，永久不可编辑） */
    public void publishCourse(Long id) {
        Course course = getEditableCourse(id);
        course.setStatus("PUBLISHED");
        courseMapper.updateById(course);
    }

    /** 删除课程（仅 UNPUBLISHED） */
    @Transactional
    public void deleteCourse(Long id) {
        Course course = getEditableCourse(id);
        // 有选课记录的课程不允许删除
        courseMapper.deleteById(course.getId());
    }

    /** 获取可编辑课程（UNPUBLISHED + 权限校验） */
    private Course getEditableCourse(Long id) {
        Course course = courseMapper.selectById(id);
        if (course == null) throw new BusinessException("课程不存在");
        if ("PUBLISHED".equals(course.getStatus())) {
            throw new BusinessException("课程已发布，信息永久锁定，不可修改");
        }
        if (!isAdmin() && !course.getTeacherId().equals(UserContext.getUserId())) {
            throw new BusinessException(403, "无权限操作他人课程");
        }
        return course;
    }

    /** 教师端：我的课程（含成绩审核状态） */
    public List<MyCourseVO> listMyCourses() {
        List<Course> courses = courseMapper.selectList(new LambdaQueryWrapper<Course>()
                .eq(Course::getTeacherId, UserContext.getUserId())
                .orderByDesc(Course::getUpdatedAt));
        return toMyCourseVO(courses);
    }

    /** 教秘端：全部课程 */
    public List<MyCourseVO> listAllCourses(String keyword) {
        LambdaQueryWrapper<Course> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(keyword != null && !keyword.isBlank(), w -> w
                        .like(Course::getCourseName, keyword)
                        .or().like(Course::getCourseCode, keyword))
                .orderByDesc(Course::getUpdatedAt);
        return toMyCourseVO(courseMapper.selectList(wrapper));
    }

    private List<MyCourseVO> toMyCourseVO(List<Course> courses) {
        if (courses.isEmpty()) return Collections.emptyList();
        Map<Long, String> auditMap = auditMapper.selectList(
                        new LambdaQueryWrapper<CourseGradeAudit>()
                                .in(CourseGradeAudit::getCourseId, courses.stream().map(Course::getId).collect(Collectors.toList())))
                .stream().collect(Collectors.toMap(CourseGradeAudit::getCourseId, CourseGradeAudit::getStatus));
        Map<Long, Staff> teacherMap = loadTeachers(courses);
        return courses.stream().map(c -> {
            MyCourseVO vo = new MyCourseVO();
            BeanUtils.copyProperties(c, vo);
            vo.setAuditStatus(auditMap.get(c.getId()));
            Staff teacher = teacherMap.get(c.getTeacherId());
            vo.setTeacherName(teacher != null ? teacher.getRealName() : "未知");
            return vo;
        }).collect(Collectors.toList());
    }

    /** 学生选课中心：已发布课程列表（含是否已选） */
    public List<CourseCardVO> listPublishedForStudent(Long studentId, List<Long> enrolledCourseIds) {
        List<Course> courses = courseMapper.selectList(new LambdaQueryWrapper<Course>()
                .eq(Course::getStatus, "PUBLISHED")
                .orderByDesc(Course::getUpdatedAt));
        Map<Long, Staff> teacherMap = loadTeachers(courses);
        return courses.stream().map(c -> {
            CourseCardVO vo = new CourseCardVO();
            BeanUtils.copyProperties(c, vo);
            Staff teacher = teacherMap.get(c.getTeacherId());
            vo.setTeacherName(teacher != null ? teacher.getRealName() : "未知");
            vo.setEnrolled(enrolledCourseIds != null && enrolledCourseIds.contains(c.getId()));
            vo.setFull(c.getCapacity() != null && c.getCurrentEnrolled() != null
                    && c.getCurrentEnrolled() >= c.getCapacity());
            return vo;
        }).collect(Collectors.toList());
    }

    /** 按 ID 批量加载教师信息 */
    public Map<Long, Staff> loadTeachers(List<Course> courses) {
        if (courses == null || courses.isEmpty()) return Collections.emptyMap();
        List<Long> teacherIds = courses.stream().map(Course::getTeacherId).distinct().collect(Collectors.toList());
        return staffMapper.selectBatchIds(teacherIds).stream()
                .collect(Collectors.toMap(Staff::getId, Function.identity()));
    }
}
