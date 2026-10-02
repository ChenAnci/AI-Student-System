package com.example.sms.service; // 声明当前类所在的包：service 服务层

// ===== import 区域：引入统计 Mapper、统计视图对象、Spring 相关注解与 Java 集合工具 =====
import com.example.sms.mapper.StatsMapper; // 统计 Mapper 接口：内部用原生 SQL 做聚合查询
import com.example.sms.vo.AdminStatsVO; // 教秘端全校统计视图对象
import com.example.sms.vo.CourseScoreStat; // 单门课程成绩统计视图对象
import com.example.sms.vo.TeacherStatsVO; // 教师端授课统计视图对象
import org.springframework.beans.factory.annotation.Autowired; // Spring 依赖注入注解
import org.springframework.stereotype.Service; // Spring 服务层注解
import java.util.List; // 列表接口

/**
 * 数据统计服务：教秘端全校数据总览与教师端授课成绩统计。
 * 纯只读聚合查询，统计口径由 StatsMapper 的 SQL 聚合实现，无事务与写操作。
 */
@Service // 声明为 Spring 服务组件，交由容器管理
public class StatsService { // 数据统计服务类：为教秘与教师提供只读的聚合统计数据

    @Autowired // Spring 自动注入 StatsMapper
    private StatsMapper statsMapper; // 统计 Mapper：封装了各类 COUNT/GROUP BY 聚合 SQL

    /**
     * 教秘端：全校数据统计。
     * 调用逻辑：StatsController.adminStats → statsService.adminStats：教秘打开数据总览页时一次聚合学生/教师/课程/选课人数、专业分布、热门课程、分数段与课程状态分布等指标返回前端。
     * 为什么：统计口径由 StatsMapper 原生 SQL 聚合（COUNT/GROUP BY/CASE WHEN）实现，一次查询出多组数据避免应用层 N 次查库；纯只读无事务与写操作，适合大屏/总览高频展示。
     */
    public AdminStatsVO adminStats() { // 教秘端全校统计：聚合各类指标填充视图对象
        // 教秘全校总览：一次聚合学生/教师/课程/选课人数、专业分布、各系课程数、热门课程、分数段与课程状态分布
        AdminStatsVO vo = new AdminStatsVO(); // 创建全校统计视图对象
        vo.setStudentCount(statsMapper.countStudents()); // 学生总数（COUNT 聚合）
        vo.setStaffCount(statsMapper.countStaff()); // 教职工总数
        vo.setTeacherCount(statsMapper.countTeachers()); // 教师总数（角色为 TEACHER）
        vo.setCourseCount(statsMapper.countCourses()); // 课程总数
        vo.setEnrollmentCount(statsMapper.countEnrollments()); // 选课记录总数
        vo.setMajorDistribution(statsMapper.majorDistribution()); // 专业分布（按专业分组计数）
        vo.setDepartmentCourses(statsMapper.departmentCourses()); // 各院系课程数
        vo.setTopEnrolledCourses(statsMapper.topEnrolledCourses()); // 热门课程 TOP 榜
        vo.setScoreBands(statsMapper.scoreBands()); // 分数段分布（CASE WHEN 分段统计）
        vo.setCourseStatus(statsMapper.courseStatus()); // 课程状态分布（已发布/未发布）
        return vo; // 返回填充完整的统计视图
    }

    /**
     * 教师端：所授课程成绩统计。
     * 调用逻辑：StatsController.teacherStats → statsService.teacherStats：教师打开个人授课统计页，按当前登录教师 id 聚合授课门数、各课选课人数与分数段分布返回前端。
     * 为什么：统计由 StatsMapper 原生 SQL 聚合（count/分组/分数段 CASE WHEN）实现且只统计已发布成绩，与学生可见口径一致；studentTotal 由各课选课人次求和得出，避免额外查库。
     */
    public TeacherStatsVO teacherStats(Long teacherId) { // 教师端授课统计：按教师 id 聚合统计指标
        // 教师端统计：本人授课门数、各课选课人数与分数段分布，studentTotal 为所有课程选课人次合计
        TeacherStatsVO vo = new TeacherStatsVO(); // 创建教师统计视图对象
        vo.setCourseCount(statsMapper.countTeacherCourses(teacherId)); // 该教师授课门数
        List<CourseScoreStat> courseScores = statsMapper.teacherCourseStats(teacherId); // 各课程选课人数统计列表
        vo.setCourseScores(courseScores); // 填充各课选课人数
        vo.setStudentTotal(courseScores.stream().mapToLong(CourseScoreStat::getStudentCount).sum()); // 选课人次合计（各课选课人数求和）
        vo.setScoreBands(statsMapper.teacherScoreBands(teacherId)); // 该教师课程的分数段分布
        return vo; // 返回填充完整的统计视图
    }
}
