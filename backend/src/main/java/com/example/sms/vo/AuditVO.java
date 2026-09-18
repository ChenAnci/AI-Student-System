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
    /** 成绩审核流程状态：DRAFT 草稿 | SUBMITTED 待审核 | APPROVED 已通过 | PUBLISHED 已发布 */
    private String status;
    private LocalDateTime submittedAt;
    private LocalDateTime approvedAt;
    private LocalDateTime publishedAt;
    private String rejectReason;
}
