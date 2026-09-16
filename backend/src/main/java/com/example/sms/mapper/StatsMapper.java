package com.example.sms.mapper;

import com.example.sms.vo.CourseScoreStat;
import com.example.sms.vo.NameValue;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface StatsMapper {

    @Select("SELECT COUNT(*) FROM student")
    long countStudents();

    @Select("SELECT COUNT(*) FROM staff")
    long countStaff();

    @Select("SELECT COUNT(*) FROM staff WHERE role_type = 'TEACHER'")
    long countTeachers();

    @Select("SELECT COUNT(*) FROM course")
    long countCourses();

    @Select("SELECT COUNT(*) FROM student_course")
    long countEnrollments();

    /** 学生专业分布 */
    @Select("SELECT major AS name, COUNT(*) AS value FROM student GROUP BY major ORDER BY value DESC")
    List<NameValue> majorDistribution();

    /** 各院系课程数（按授课教师所属院系） */
    @Select("SELECT IFNULL(s.department, '未分配') AS name, COUNT(c.id) AS value " +
            "FROM course c LEFT JOIN staff s ON c.teacher_id = s.id " +
            "GROUP BY s.department ORDER BY value DESC")
    List<NameValue> departmentCourses();

    /** 选课人数 Top 课程 */
    @Select("SELECT c.course_name AS name, COUNT(sc.id) AS value " +
            "FROM course c JOIN student_course sc ON sc.course_id = c.id " +
            "GROUP BY c.id, c.course_name ORDER BY value DESC LIMIT 10")
    List<NameValue> topEnrolledCourses();

    /** 课程状态分布 */
    @Select("SELECT status AS name, COUNT(*) AS value FROM course GROUP BY status ORDER BY value DESC")
    List<NameValue> courseStatus();

    /** 全校成绩分数段分布（仅统计已发布成绩，与学生可见口径一致） */
    @Select("SELECT CASE WHEN sc.score < 60 THEN '60分以下' WHEN sc.score < 70 THEN '60-69分' " +
            "WHEN sc.score < 80 THEN '70-79分' WHEN sc.score < 90 THEN '80-89分' ELSE '90-100分' END AS name, " +
            "COUNT(*) AS value FROM student_course sc " +
            "JOIN course_grade_audit cga ON cga.course_id = sc.course_id " +
            "WHERE sc.score IS NOT NULL AND cga.status = 'PUBLISHED' " +
            "GROUP BY 1 ORDER BY MIN(sc.score)")
    List<NameValue> scoreBands();

    /** 某教师所授课程的成绩分数段分布（仅统计已发布成绩） */
    @Select("SELECT CASE WHEN sc.score < 60 THEN '60分以下' WHEN sc.score < 70 THEN '60-69分' " +
            "WHEN sc.score < 80 THEN '70-79分' WHEN sc.score < 90 THEN '80-89分' ELSE '90-100分' END AS name, " +
            "COUNT(*) AS value FROM student_course sc JOIN course c ON sc.course_id = c.id " +
            "JOIN course_grade_audit cga ON cga.course_id = sc.course_id " +
            "WHERE sc.score IS NOT NULL AND c.teacher_id = #{teacherId} AND cga.status = 'PUBLISHED' " +
            "GROUP BY 1 ORDER BY MIN(sc.score)")
    List<NameValue> teacherScoreBands(@Param("teacherId") Long teacherId);

    /** 教师各课程平均分/人数/通过率（仅统计已发布成绩） */
    @Select("SELECT c.course_name AS courseName, ROUND(AVG(sc.score), 1) AS avgScore, COUNT(sc.id) AS studentCount, " +
            "ROUND(SUM(CASE WHEN sc.score >= 60 THEN 1 ELSE 0 END) / COUNT(sc.id) * 100, 1) AS passRate " +
            "FROM course c JOIN student_course sc ON sc.course_id = c.id " +
            "JOIN course_grade_audit cga ON cga.course_id = c.id " +
            "WHERE c.teacher_id = #{teacherId} AND sc.score IS NOT NULL AND cga.status = 'PUBLISHED' " +
            "GROUP BY c.id, c.course_name ORDER BY avgScore DESC")
    List<CourseScoreStat> teacherCourseStats(@Param("teacherId") Long teacherId);

    /** 教师所授课程数（含未评分成绩的课程） */
    @Select("SELECT COUNT(*) FROM course WHERE teacher_id = #{teacherId}")
    long countTeacherCourses(@Param("teacherId") Long teacherId);
}
