// 声明包名：本接口位于 com.example.sms.mapper 包，属于数据访问（Mapper）层
package com.example.sms.mapper;

// 导入区：引入本接口所需的类与注解
import com.example.sms.vo.CourseScoreStat;   // 课程成绩统计视图对象（课程名/平均分/人数/通过率）
import com.example.sms.vo.NameValue;          // 通用"名称-数值"视图对象，供各类分布图（name/value）使用
import org.apache.ibatis.annotations.Mapper; // MyBatis 的 @Mapper 注解，标记本接口为 Mapper
import org.apache.ibatis.annotations.Param;  // @Param 注解：为 SQL 中 #{} 占位符显式绑定参数名
import org.apache.ibatis.annotations.Select; // @Select 注解：标注自定义查询 SQL

import java.util.List; // 列表集合类型，用于返回多条统计结果

/**
 * 统计查询 Mapper（只读聚合，不继承 BaseMapper）：
 * 供首页卡片（学生/教职工/课程/选课数）、各维度分布图（专业/院系/课程状态/分数段）与教师端成绩统计使用
 */
// @Mapper：让 MyBatis 扫描本接口并自动生成代理实现类，供 Service 层注入使用
@Mapper
// 接口声明：本接口只做只读统计聚合，因此不继承 BaseMapper，所有查询均通过下方 @Select 自定义 SQL 实现
public interface StatsMapper {

    // 首页卡片：学生总数（全校学生表行数）
    // @Select：标注下方字符串为执行的自定义 SQL；COUNT(*) 统计 student 表全部行数，返回一个数值
    @Select("SELECT COUNT(*) FROM student")
    // 方法定义：无参，返回全校学生总数
    long countStudents();

    // 首页卡片：教职工总数（含教师与教秘，staff 表全量）
    // @Select：统计 staff 表全部行数（含教师与教秘）
    @Select("SELECT COUNT(*) FROM staff")
    // 方法定义：无参，返回教职工总数
    long countStaff();

    // 首页卡片：教师数（staff 表中角色为 TEACHER 的子集）
    // @Select：加 WHERE 条件只统计 role_type = 'TEACHER'（教师角色）的行数
    @Select("SELECT COUNT(*) FROM staff WHERE role_type = 'TEACHER'")
    // 方法定义：无参，返回教师数量
    long countTeachers();

    // 首页卡片：课程总数
    // @Select：统计 course 表全部行数
    @Select("SELECT COUNT(*) FROM course")
    // 方法定义：无参，返回课程总数
    long countCourses();

    // 首页卡片：选课记录总数（一条 student_course 记录 = 一个学生选一门课）
    // @Select：统计 student_course 表全部行数，即全校选课记录总数
    @Select("SELECT COUNT(*) FROM student_course")
    // 方法定义：无参，返回选课记录总数
    long countEnrollments();

    /** 学生专业分布（全校：按专业分组统计人数，按人数倒序，供饼图/柱状图） */
    // @Select：SQL 逐行解释——
    //   SELECT major AS name            ：专业字段，别名 name（图表横轴标签）
    //   COUNT(*) AS value               ：该专业学生人数，别名 value（图表纵轴数值）
    //   FROM student GROUP BY major     ：按专业分组统计人数
    //   ORDER BY value DESC             ：按人数倒序，人数最多的专业排最前
    @Select("SELECT major AS name, COUNT(*) AS value FROM student GROUP BY major ORDER BY value DESC")
    // 方法定义：无参，返回"专业-人数"列表，供饼图/柱状图渲染
    List<NameValue> majorDistribution();

    /** 各院系课程数（按授课教师所属院系） */
    // 口径：课程归属院系 = 授课教师所在院系；LEFT JOIN 保证无教师/教师无院系的课程也计入（院系显示"未分配"）
    // @Select：SQL 逐行解释——
    @Select("SELECT IFNULL(s.department, '未分配') AS name, COUNT(c.id) AS value " +
            //   SELECT IFNULL(s.department, '未分配') AS name：取教师院系名，为空则显示"未分配"，别名 name
            //   COUNT(c.id) AS value：统计该院系下课程数，别名 value
            "FROM course c LEFT JOIN staff s ON c.teacher_id = s.id " +
            //   FROM course c LEFT JOIN staff s：课程表左连接教职工表，LEFT JOIN 保证无教师的课程也计入
            //   ON c.teacher_id = s.id：按授课教师 ID 关联，取教师所属院系
            "GROUP BY s.department ORDER BY value DESC")
    //   GROUP BY s.department：按院系分组统计；ORDER BY value DESC：按课程数倒序
    // 方法定义：无参，返回"院系-课程数"列表，供院系列图使用
    List<NameValue> departmentCourses();

    /** 选课人数 Top 课程 */
    // 口径：按课程分组统计选课记录数；GROUP BY 带上 c.id 避免同名课程被合并
    // @Select：SQL 逐行解释——
    @Select("SELECT c.course_name AS name, COUNT(sc.id) AS value " +
            //   SELECT c.course_name AS name：课程名，别名 name
            //   COUNT(sc.id) AS value：该课程的选课记录数，别名 value
            "FROM course c JOIN student_course sc ON sc.course_id = c.id " +
            //   FROM course c JOIN student_course sc：课程与选课记录内连接，只统计有学生选的课程
            //   ON sc.course_id = c.id：按课程 ID 关联
            "GROUP BY c.id, c.course_name ORDER BY value DESC LIMIT 10")
    //   GROUP BY c.id, c.course_name：按课程分组（带上 id 防止同名课程被合并统计）
    //   ORDER BY value DESC LIMIT 10：按选课人数倒序，只取前 10 名
    // 方法定义：无参，返回选课人数 Top10 课程列表
    List<NameValue> topEnrolledCourses();

    /** 课程状态分布（全校：按课程 status 分组计数） */
    // @Select：SQL 逐行解释——
    //   SELECT status AS name：课程状态字段（如草稿/已发布），别名 name
    //   COUNT(*) AS value：该状态下的课程数，别名 value
    //   FROM course GROUP BY status：按状态分组统计；ORDER BY value DESC：按数量倒序
    @Select("SELECT status AS name, COUNT(*) AS value FROM course GROUP BY status ORDER BY value DESC")
    // 方法定义：无参，返回"课程状态-数量"列表
    List<NameValue> courseStatus();

    /** 全校成绩分数段分布（仅统计已发布成绩，与学生可见口径一致） */
    // 口径：只统计 course_grade_audit.status = 'PUBLISHED' 的课程成绩——学生端只能看到已发布成绩，
    // 全校统计若混入未发布成绩会与学生在成绩页看到的数据不一致；
    // GROUP BY 1 即按 SELECT 第一列（CASE 分数段表达式）分组；ORDER BY MIN(sc.score) 让分数段按成绩升序展示
    // @Select：SQL 逐行解释——
    @Select("SELECT CASE WHEN sc.score < 60 THEN '60分以下' WHEN sc.score < 70 THEN '60-69分' " +
            //   CASE 表达式：按 sc.score 划分 5 个分数段并起别名 name（60以下 / 60-69 / 70-79 / 80-89 / 90-100）
            "WHEN sc.score < 80 THEN '70-79分' WHEN sc.score < 90 THEN '80-89分' ELSE '90-100分' END AS name, " +
            //   COUNT(*) AS value：统计每个分数段的人数
            //   FROM student_course sc：成绩数据来源于学生选课记录表
            "COUNT(*) AS value FROM student_course sc " +
            "JOIN course_grade_audit cga ON cga.course_id = sc.course_id " +
            //   JOIN course_grade_audit cga：关联成绩审核表，用其状态判断成绩是否已发布
            //   ON cga.course_id = sc.course_id：按课程 ID 关联两表
            "WHERE sc.score IS NOT NULL AND cga.status = 'PUBLISHED' " +
            //   WHERE sc.score IS NOT NULL：排除未录入成绩的记录
            //   AND cga.status = 'PUBLISHED'：只统计已发布成绩，与学生端可见口径一致
            "GROUP BY 1 ORDER BY MIN(sc.score)")
    //   GROUP BY 1：按 SELECT 第一列（分数段表达式）分组
    //   ORDER BY MIN(sc.score)：按该组最低分升序，让分数段从低到高展示
    // 方法定义：无参，返回全校各分数段人数列表
    List<NameValue> scoreBands();

    /** 某教师所授课程的成绩分数段分布（教师端：统计自己录入的全部成绩，含未发布，保证录入后实时可见） */
    // 口径：教师端统计自己名下课程的全部成绩（含未发布），因为教师录入成绩后要在图表中立即看到效果，
    // 若也过滤 PUBLISHED 会导致"录入后看不到"的体验问题；教师只能统计自己课程（WHERE teacher_id），天然隔离
    // @Select：SQL 逐行解释——
    @Select("SELECT CASE WHEN sc.score < 60 THEN '60分以下' WHEN sc.score < 70 THEN '60-69分' " +
            //   CASE 表达式：按分数划分 5 个分数段，别名 name（与学生端分数段口径一致）
            "WHEN sc.score < 80 THEN '70-79分' WHEN sc.score < 90 THEN '80-89分' ELSE '90-100分' END AS name, " +
            //   COUNT(*) AS value：统计每个分数段的人数
            //   FROM student_course sc JOIN course c ON sc.course_id = c.id：选课记录关联课程，用于取授课教师
            "COUNT(*) AS value FROM student_course sc JOIN course c ON sc.course_id = c.id " +
            "WHERE sc.score IS NOT NULL AND c.teacher_id = #{teacherId} " +
            //   WHERE sc.score IS NOT NULL：排除未录入成绩的记录
            //   AND c.teacher_id = #{teacherId}：只统计该教师名下课程的成绩（#{} 为预编译占位符）
            "GROUP BY 1 ORDER BY MIN(sc.score)")
    //   GROUP BY 1：按分数段分组；ORDER BY MIN(sc.score)：按最低分升序展示
    // 方法定义：入参 teacherId 为教师 ID（@Param 指定占位符名），返回该教师各分数段人数列表
    List<NameValue> teacherScoreBands(@Param("teacherId") Long teacherId);

    /** 教师各课程平均分/人数/通过率（教师端：统计自己录入的全部成绩，含未发布，保证录入后实时可见） */
    // 口径：按课程聚合；通过率 = 成绩 >= 60 的人数 / 有成绩人数 * 100；
    // 只统计有成绩的记录（sc.score IS NOT NULL），未录入成绩的学生不计入分母
    // @Select：SQL 逐行解释——
    @Select("SELECT c.course_name AS courseName, ROUND(AVG(sc.score), 1) AS avgScore, COUNT(sc.id) AS studentCount, " +
            //   c.course_name AS courseName：课程名，别名 courseName
            //   ROUND(AVG(sc.score), 1) AS avgScore：课程平均分，保留 1 位小数，别名 avgScore
            //   COUNT(sc.id) AS studentCount：有成绩的学生人数，别名 studentCount
            "ROUND(SUM(CASE WHEN sc.score >= 60 THEN 1 ELSE 0 END) / COUNT(sc.id) * 100, 1) AS passRate " +
            //   SUM(CASE WHEN sc.score >= 60 THEN 1 ELSE 0 END)：统计及格（>=60 分）人数
            //   及格人数 / 总人数 * 100 得到通过率百分比，ROUND(..., 1) 保留 1 位小数，别名 passRate
            "FROM course c JOIN student_course sc ON sc.course_id = c.id " +
            //   FROM course c JOIN student_course sc：课程表关联选课记录表
            "WHERE c.teacher_id = #{teacherId} AND sc.score IS NOT NULL " +
            //   WHERE c.teacher_id = #{teacherId}：只统计该教师的课程
            //   AND sc.score IS NOT NULL：只统计已录入成绩的记录（未录入不计入分母）
            "GROUP BY c.id, c.course_name ORDER BY avgScore DESC")
    //   GROUP BY c.id, c.course_name：按课程分组（带上 id 防止同名课程合并）
    //   ORDER BY avgScore DESC：按平均分倒序展示
    // 方法定义：入参 teacherId 为教师 ID，返回该教师各课程的统计结果列表（含平均分/人数/通过率）
    List<CourseScoreStat> teacherCourseStats(@Param("teacherId") Long teacherId);

    /** 教师所授课程数（含未评分成绩的课程） */
    // 口径：统计名下全部课程（不要求已有成绩），用于"课程数"卡片——即使一门成绩都未录入也应计入
    // @Select：WHERE teacher_id = #{teacherId} 只统计该教师名下课程，COUNT(*) 返回课程数
    @Select("SELECT COUNT(*) FROM course WHERE teacher_id = #{teacherId}")
    // 方法定义：入参 teacherId 为教师 ID，返回该教师名下课程总数
    long countTeacherCourses(@Param("teacherId") Long teacherId);
}
