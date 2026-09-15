package com.example.sms.vo;

import lombok.Data;

import java.util.List;

/**
 * 教秘端数据统计 VO
 */
@Data
public class AdminStatsVO {

    private Long studentCount;
    private Long staffCount;
    private Long teacherCount;
    private Long courseCount;
    private Long enrollmentCount;

    /** 学生专业分布 */
    private List<NameValue> majorDistribution;
    /** 各院系课程数 */
    private List<NameValue> departmentCourses;
    /** 选课人数 Top 课程 */
    private List<NameValue> topEnrolledCourses;
    /** 成绩分数段分布 */
    private List<NameValue> scoreBands;
    /** 课程状态分布 */
    private List<NameValue> courseStatus;
}
