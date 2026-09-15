package com.example.sms.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 教师课程成绩统计项
 */
@Data
public class CourseScoreStat {

    private String courseName;
    /** 平均分 */
    private BigDecimal avgScore;
    /** 已评分学生数 */
    private Long studentCount;
    /** 通过率（0-100） */
    private BigDecimal passRate;
}
