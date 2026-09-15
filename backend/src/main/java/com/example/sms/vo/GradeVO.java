package com.example.sms.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 学生成绩单 VO
 */
@Data
public class GradeVO {

    private Long courseId;
    private String courseName;
    private BigDecimal credit;
    private BigDecimal score;
    /** NORMAL | DEFER | ABSENT | CHEAT */
    private String mark;
    /** DRAFT | SUBMITTED | APPROVED | PUBLISHED */
    private String auditStatus;
    /** 是否通过（已发布且 score >= 60） */
    private Boolean passed;
}
