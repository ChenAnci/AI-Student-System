package com.example.sms.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 教师端课程列表 VO（含成绩审核状态）
 */
@Data
public class MyCourseVO {

    private Long id;
    private String courseCode;
    private String courseName;
    private BigDecimal credit;
    private Integer hours;
    private String schedule;
    private String location;
    private Integer capacity;
    private Integer currentEnrolled;
    private String status;
    private String coverImageUrl;
    /** 授课教师 ID 与姓名（管理端展示/回填用） */
    private Long teacherId;
    private String teacherName;
    /** DRAFT | SUBMITTED | APPROVED | PUBLISHED | null(未发起) */
    private String auditStatus;
}
