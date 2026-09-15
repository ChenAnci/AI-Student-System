package com.example.sms.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 课程卡片 VO（学生选课中心 / 通用课程展示）
 */
@Data
public class CourseCardVO {

    private Long id;
    private String courseCode;
    private String courseName;
    private BigDecimal credit;
    private Integer hours;
    private String coverImageUrl;
    private String teacherName;
    private String schedule;
    private String location;
    private Integer capacity;
    private Integer currentEnrolled;
    private String status;
    /** 当前学生是否已选 */
    private Boolean enrolled;
    /** 是否满员 */
    private Boolean full;
}
