// 包声明：本类位于 vo（视图对象）包，用于承载服务端返回给前端的展示数据
package com.example.sms.vo;

// ---------- import 区域说明 ----------
// 此处导入 Lombok 的 @Data（自动生成 getter/setter 等样板代码），
// 以及 JDK 的 List（列表类型，承载名称-数值统计项）。
import lombok.Data;

import java.util.List;

/**
 * 教秘端数据统计 VO
 */
// @Data：Lombok 注解，编译期自动生成 getter、setter、toString、equals、hashCode 方法
@Data
// 教学秘书（管理员）数据看板接口返回的统计数据对象
public class AdminStatsVO {

    // 学生总数：系统中学生账号的数量
    private Long studentCount;
    // 教职工总数：系统中教职工账号的总数量（含教师与管理员）
    private Long staffCount;
    // 教师总数：其中角色为教师（TEACHER）的人数
    private Long teacherCount;
    // 课程总数：系统中课程的数量
    private Long courseCount;
    // 选课记录总数：学生选课关系（student_course）的总条数
    private Long enrollmentCount;

    /** 学生专业分布 */
    // 学生专业分布：专业名 -> 人数 的统计列表（饼图/柱状图数据）
    private List<NameValue> majorDistribution;
    /** 各院系课程数 */
    // 各院系课程数：院系名 -> 课程数 的统计列表
    private List<NameValue> departmentCourses;
    /** 选课人数 Top 课程 */
    // 选课人数 Top 课程：课程名 -> 选课人数 的排行列表
    private List<NameValue> topEnrolledCourses;
    /** 成绩分数段分布 */
    // 成绩分数段分布：分数段（如 90-100）-> 人数 的统计列表
    private List<NameValue> scoreBands;
    /** 课程状态分布 */
    // 课程状态分布：课程状态（已发布/未发布）-> 数量 的统计列表
    private List<NameValue> courseStatus;
}
