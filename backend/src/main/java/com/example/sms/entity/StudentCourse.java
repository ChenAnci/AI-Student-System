package com.example.sms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 学生选课实体，对应数据库表 student_course：选课关系、成绩与考试标记
 */
@Data
@TableName("student_course")
public class StudentCourse {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 学生ID（关联 student.id） */
    private Long studentId;

    /** 课程ID（关联 course.id） */
    private Long courseId;

    /** 总评成绩（NULL 表示未录入） */
    private BigDecimal score;

    /** NORMAL正常 | DEFER缓考 | ABSENT缺考 | CHEAT舞弊 */
    private String mark;

    /** 选课时间 */
    private LocalDateTime createdAt;

    /** 更新时间（成绩/标记变更时刷新） */
    private LocalDateTime updatedAt;
}
