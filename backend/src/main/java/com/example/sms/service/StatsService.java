package com.example.sms.service;

import com.example.sms.mapper.StatsMapper;
import com.example.sms.vo.AdminStatsVO;
import com.example.sms.vo.CourseScoreStat;
import com.example.sms.vo.TeacherStatsVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 数据统计服务：教秘端全校数据总览与教师端授课成绩统计。
 * 纯只读聚合查询，统计口径由 StatsMapper 的 SQL 聚合实现，无事务与写操作。
 */
@Service
public class StatsService {

    @Autowired
    private StatsMapper statsMapper;

    /**
     * 教秘端：全校数据统计。
     * 调用逻辑：StatsController.adminStats → statsService.adminStats：教秘打开数据总览页时一次聚合学生/教师/课程/选课人数、专业分布、热门课程、分数段与课程状态分布等指标返回前端。
     * 为什么：统计口径由 StatsMapper 原生 SQL 聚合（COUNT/GROUP BY/CASE WHEN）实现，一次查询出多组数据避免应用层 N 次查库；纯只读无事务与写操作，适合大屏/总览高频展示。
     */
    public AdminStatsVO adminStats() {
        // 教秘全校总览：一次聚合学生/教师/课程/选课人数、专业分布、各系课程数、热门课程、分数段与课程状态分布
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

    /**
     * 教师端：所授课程成绩统计。
     * 调用逻辑：StatsController.teacherStats → statsService.teacherStats：教师打开个人授课统计页，按当前登录教师 id 聚合授课门数、各课选课人数与分数段分布返回前端。
     * 为什么：统计由 StatsMapper 原生 SQL 聚合（count/分组/分数段 CASE WHEN）实现且只统计已发布成绩，与学生可见口径一致；studentTotal 由各课选课人次求和得出，避免额外查库。
     */
    public TeacherStatsVO teacherStats(Long teacherId) {
        // 教师端统计：本人授课门数、各课选课人数与分数段分布，studentTotal 为所有课程选课人次合计
        TeacherStatsVO vo = new TeacherStatsVO();
        vo.setCourseCount(statsMapper.countTeacherCourses(teacherId));
        List<CourseScoreStat> courseScores = statsMapper.teacherCourseStats(teacherId);
        vo.setCourseScores(courseScores);
        vo.setStudentTotal(courseScores.stream().mapToLong(CourseScoreStat::getStudentCount).sum());
        vo.setScoreBands(statsMapper.teacherScoreBands(teacherId));
        return vo;
    }
}
