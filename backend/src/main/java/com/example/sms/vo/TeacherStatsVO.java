// 包声明：本类位于 vo（视图对象）包，用于承载服务端返回给前端的展示数据
package com.example.sms.vo;

// ---------- import 区域说明 ----------
// 此处导入 Lombok 的 @Data（自动生成 getter/setter 等样板代码），
// 以及 JDK 的 List（列表类型，承载课程统计与分数段分布）。
import lombok.Data;

import java.util.List;

/**
 * 教师端成绩统计 VO
 */
// @Data：Lombok 注解，编译期自动生成 getter、setter、toString、equals、hashCode 方法
@Data
// 教师端成绩统计页接口返回的数据对象（含所授课程汇总与各课程明细统计）
public class TeacherStatsVO {

    /** 所授课程数 */
    // 所授课程数：当前教师担任授课的课程总数量
    private Long courseCount;
    /** 选课总人次（已评分） */
    // 选课总人次：所有课程中已录入成绩的学生总人次
    private Long studentTotal;
    /** 各课程平均分/人数/通过率 */
    // 各课程统计明细：每门课程的平均分、已评分人数、通过率
    private List<CourseScoreStat> courseScores;
    /** 成绩分数段分布 */
    // 成绩分数段分布：分数段（如 90-100）-> 人数 的统计列表
    private List<NameValue> scoreBands;
}
