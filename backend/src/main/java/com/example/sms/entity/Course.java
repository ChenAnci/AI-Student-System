package com.example.sms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 课程实体
 */
@Data
@TableName("course")
public class Course {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 课程编号 */
    private String courseCode;

    /** 课程名称 */
    private String courseName;

    /** 学分 */
    private BigDecimal credit;

    /** 总学时 */
    private Integer hours;

    /** 课程封面图URL */
    private String coverImageUrl;

    /** 授课教师ID */
    private Long teacherId;

    /** 上课时间 */
    private String schedule;

    /** 上课地点 */
    private String location;

    /** 选课容量上限 */
    private Integer capacity;

    /** 当前已选人数 */
    private Integer currentEnrolled;

    /** UNPUBLISHED | PUBLISHED */
    private String status;

    private LocalDateTime updatedAt;
}
