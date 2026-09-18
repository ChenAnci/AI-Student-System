package com.example.sms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 学生实体，对应数据库表 student：学生账号、学籍信息与学业数据（学分/绩点）
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

    /** 令牌版本号：改密/禁用/改角色时 +1，用于吊销旧 token */
    private Integer tokenVersion;

    /** 男/女 */
    private String gender;

    /** 手机号 */
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

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
