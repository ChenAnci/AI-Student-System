package com.example.sms.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 选课监控 VO（教学秘书）
 */
@Data
public class EnrollMonitorVO {

    private Long courseId;
    private String courseCode;
    private String courseName;
    private String teacherName;
    private Integer capacity;
    private Integer currentEnrolled;
    /** 剩余名额 = 容量 - 已选人数 */
    private Integer remain;
    /** 课程状态：UNPUBLISHED 未发布 | PUBLISHED 已发布 */
    private String status;
}
