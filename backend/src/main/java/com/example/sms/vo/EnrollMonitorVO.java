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
    private Integer remain;
    private String status;
}
