package com.example.sms.vo;

import lombok.Data;

import java.util.List;

/**
 * 教师端成绩统计 VO
 */
@Data
public class TeacherStatsVO {

    /** 所授课程数 */
    private Long courseCount;
    /** 选课总人次（已评分） */
    private Long studentTotal;
    /** 各课程平均分/人数/通过率 */
    private List<CourseScoreStat> courseScores;
    /** 成绩分数段分布 */
    private List<NameValue> scoreBands;
}
