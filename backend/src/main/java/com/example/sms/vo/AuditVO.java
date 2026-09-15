package com.example.sms.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 待审核/成绩流程 VO（教秘审核、教师提交后查看）
 */
@Data
public class AuditVO {

    private Long courseId;
    private String courseCode;
    private String courseName;
    private String teacherName;
    private String status;
    private LocalDateTime submittedAt;
    private LocalDateTime approvedAt;
    private LocalDateTime publishedAt;
    private String rejectReason;
}
