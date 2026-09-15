package com.example.sms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 学生选课实体
 */
@Data
@TableName("student_course")
public class StudentCourse {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long studentId;

    private Long courseId;

    /** 总评成绩（NULL 表示未录入） */
    private BigDecimal score;

    /** NORMAL正常 | DEFER缓考 | ABSENT缺考 | CHEAT舞弊 */
    private String mark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
