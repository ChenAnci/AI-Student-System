package com.example.sms.service;

import com.example.sms.mapper.StatsMapper;
import com.example.sms.vo.AdminStatsVO;
import com.example.sms.vo.CourseScoreStat;
import com.example.sms.vo.TeacherStatsVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 数据统计服务
 */
@Service
public class StatsService {

    @Autowired
    private StatsMapper statsMapper;

    /** 教秘端：全校数据统计 */
    public AdminStatsVO adminStats() {
        AdminStatsVO vo = new AdminStatsVO();
        vo.setStudentCount(statsMapper.countStudents());
        vo.setStaffCount(statsMapper.countStaff());
        vo.setTeacherCount(statsMapper.countTeachers());
        vo.setCourseCount(statsMapper.countCourses());
        vo.setEnrollmentCount(statsMapper.countEnrollments());
        vo.setMajorDistribution(statsMapper.majorDistribution());
        vo.setDepartmentCourses(statsMapper.departmentCourses());
        vo.setTopEnrolledCourses(statsMapper.topEnrolledCourses());
        vo.setScoreBands(statsMapper.scoreBands());
        vo.setCourseStatus(statsMapper.courseStatus());
        return vo;
    }

    /** 教师端：所授课程成绩统计 */
    public TeacherStatsVO teacherStats(Long teacherId) {
        TeacherStatsVO vo = new TeacherStatsVO();
        vo.setCourseCount(statsMapper.countTeacherCourses(teacherId));
        List<CourseScoreStat> courseScores = statsMapper.teacherCourseStats(teacherId);
        vo.setCourseScores(courseScores);
        vo.setStudentTotal(courseScores.stream().mapToLong(CourseScoreStat::getStudentCount).sum());
        vo.setScoreBands(statsMapper.teacherScoreBands(teacherId));
        return vo;
    }
}
