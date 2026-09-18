package com.example.sms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 课程成绩审核实体，对应数据库表 course_grade_audit：一门课程一条审核记录，跟踪提交→审核→发布全流程
 */
@Data
@TableName("course_grade_audit")
public class CourseGradeAudit {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 课程ID（关联 course.id） */
    private Long courseId;

    /** 授课教师ID（关联 staff.id） */
    private Long teacherId;

    /** DRAFT录入中 | SUBMITTED待审核 | APPROVED审核通过 | PUBLISHED已发布 */
    private String status;

    /** 提交审核时间 */
    private LocalDateTime submittedAt;

    /** 审核通过时间 */
    private LocalDateTime approvedAt;

    /** 成绩发布时间 */
    private LocalDateTime publishedAt;

    /** 审核不通过原因 */
    private String rejectReason;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
