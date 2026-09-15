package com.example.sms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 课程成绩审核实体
 */
@Data
@TableName("course_grade_audit")
public class CourseGradeAudit {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long courseId;

    private Long teacherId;

    /** DRAFT录入中 | SUBMITTED待审核 | APPROVED审核通过 | PUBLISHED已发布 */
    private String status;

    private LocalDateTime submittedAt;

    private LocalDateTime approvedAt;

    private LocalDateTime publishedAt;

    /** 审核不通过原因 */
    private String rejectReason;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
