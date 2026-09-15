package com.example.sms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 学生实体
 */
@Data
@TableName("student")
public class Student {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 学号（登录账号） */
    private String studentNo;

    /** bcrypt 加密密码，序列化时忽略 */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String passwordHash;

    /** 姓名 */
    private String realName;

    /** ENABLED | FROZEN | SUSPENDED */
    private String status;

    /** 男/女 */
    private String gender;

    private String phone;

    /** 院系 */
    private String department;

    /** 专业 */
    private String major;

    /** 班级 */
    private String className;

    /** 入学年份 */
    private Integer enrollmentYear;

    /** 已修总学分 */
    private BigDecimal totalEarnedCredits;

    /** 毕业要求总学分 */
    private BigDecimal requiredCredits;

    /** 累计平均绩点 */
    private BigDecimal gpa;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
