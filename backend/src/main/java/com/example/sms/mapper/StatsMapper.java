package com.example.sms.mapper;

import com.example.sms.vo.CourseScoreStat;
import com.example.sms.vo.NameValue;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface StatsMapper {

    // 首页卡片：学生总数（全校学生表行数）
    @Select("SELECT COUNT(*) FROM student")
    long countStudents();

    // 首页卡片：教职工总数（含教师与教秘，staff 表全量）
    @Select("SELECT COUNT(*) FROM staff")
    long countStaff();

    // 首页卡片：教师数（staff 表中角色为 TEACHER 的子集）
    @Select("SELECT COUNT(*) FROM staff WHERE role_type = 'TEACHER'")
    long countTeachers();

    // 首页卡片：课程总数
    @Select("SELECT COUNT(*) FROM course")
    long countCourses();

    // 首页卡片：选课记录总数（一条 student_course 记录 = 一个学生选一门课）
    @Select("SELECT COUNT(*) FROM student_course")
    long countEnrollments();

    /** 学生专业分布（全校：按专业分组统计人数，按人数倒序，供饼图/柱状图） */
    @Select("SELECT major AS name, COUNT(*) AS value FROM student GROUP BY major ORDER BY value DESC")
    List<NameValue> majorDistribution();

    /** 各院系课程数（按授课教师所属院系） */
    // 口径：课程归属院系 = 授课教师所在院系；LEFT JOIN 保证无教师/教师无院系的课程也计入（院系显示"未分配"）
    @Select("SELECT IFNULL(s.department, '未分配') AS name, COUNT(c.id) AS value " +
            "FROM course c LEFT JOIN staff s ON c.teacher_id = s.id " +
            "GROUP BY s.department ORDER BY value DESC")
    List<NameValue> departmentCourses();

    /** 选课人数 Top 课程 */
    // 口径：按课程分组统计选课记录数；GROUP BY 带上 c.id 避免同名课程被合并
    @Select("SELECT c.course_name AS name, COUNT(sc.id) AS value " +
            "FROM course c JOIN student_course sc ON sc.course_id = c.id " +
            "GROUP BY c.id, c.course_name ORDER BY value DESC LIMIT 10")
    List<NameValue> topEnrolledCourses();

    /** 课程状态分布（全校：按课程 status 分组计数） */
    @Select("SELECT status AS name, COUNT(*) AS value FROM course GROUP BY status ORDER BY value DESC")
    List<NameValue> courseStatus();

    /** 全校成绩分数段分布（仅统计已发布成绩，与学生可见口径一致） */
    // 口径：只统计 course_grade_audit.status = 'PUBLISHED' 的课程成绩——学生端只能看到已发布成绩，
    // 全校统计若混入未发布成绩会与学生在成绩页看到的数据不一致；
    // GROUP BY 1 即按 SELECT 第一列（CASE 分数段表达式）分组；ORDER BY MIN(sc.score) 让分数段按成绩升序展示
    @Select("SELECT CASE WHEN sc.score < 60 THEN '60分以下' WHEN sc.score < 70 THEN '60-69分' " +
            "WHEN sc.score < 80 THEN '70-79分' WHEN sc.score < 90 THEN '80-89分' ELSE '90-100分' END AS name, " +
            "COUNT(*) AS value FROM student_course sc " +
            "JOIN course_grade_audit cga ON cga.course_id = sc.course_id " +
            "WHERE sc.score IS NOT NULL AND cga.status = 'PUBLISHED' " +
            "GROUP BY 1 ORDER BY MIN(sc.score)")
    List<NameValue> scoreBands();

    /** 某教师所授课程的成绩分数段分布（教师端：统计自己录入的全部成绩，含未发布，保证录入后实时可见） */
    // 口径：教师端统计自己名下课程的全部成绩（含未发布），因为教师录入成绩后要在图表中立即看到效果，
    // 若也过滤 PUBLISHED 会导致"录入后看不到"的体验问题；教师只能统计自己课程（WHERE teacher_id），天然隔离
    @Select("SELECT CASE WHEN sc.score < 60 THEN '60分以下' WHEN sc.score < 70 THEN '60-69分' " +
            "WHEN sc.score < 80 THEN '70-79分' WHEN sc.score < 90 THEN '80-89分' ELSE '90-100分' END AS name, " +
            "COUNT(*) AS value FROM student_course sc JOIN course c ON sc.course_id = c.id " +
            "WHERE sc.score IS NOT NULL AND c.teacher_id = #{teacherId} " +
            "GROUP BY 1 ORDER BY MIN(sc.score)")
    List<NameValue> teacherScoreBands(@Param("teacherId") Long teacherId);

    /** 教师各课程平均分/人数/通过率（教师端：统计自己录入的全部成绩，含未发布，保证录入后实时可见） */
    // 口径：按课程聚合；通过率 = 成绩 >= 60 的人数 / 有成绩人数 * 100；
    // 只统计有成绩的记录（sc.score IS NOT NULL），未录入成绩的学生不计入分母
    @Select("SELECT c.course_name AS courseName, ROUND(AVG(sc.score), 1) AS avgScore, COUNT(sc.id) AS studentCount, " +
            "ROUND(SUM(CASE WHEN sc.score >= 60 THEN 1 ELSE 0 END) / COUNT(sc.id) * 100, 1) AS passRate " +
            "FROM course c JOIN student_course sc ON sc.course_id = c.id " +
            "WHERE c.teacher_id = #{teacherId} AND sc.score IS NOT NULL " +
            "GROUP BY c.id, c.course_name ORDER BY avgScore DESC")
    List<CourseScoreStat> teacherCourseStats(@Param("teacherId") Long teacherId);

    /** 教师所授课程数（含未评分成绩的课程） */
    // 口径：统计名下全部课程（不要求已有成绩），用于"课程数"卡片——即使一门成绩都未录入也应计入
    @Select("SELECT COUNT(*) FROM course WHERE teacher_id = #{teacherId}")
    long countTeacherCourses(@Param("teacherId") Long teacherId);
}
