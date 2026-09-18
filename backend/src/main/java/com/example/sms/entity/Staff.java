package com.example.sms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 教职工实体，对应数据库表 staff：教职工账号（工号登录）、角色（管理员/教师）与所属院系
 */
@Data
@TableName("staff")
public class Staff {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 工号（登录账号） */
    private String staffNo;

    /** bcrypt 加密密码，序列化时忽略 */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String passwordHash;

    /** 姓名 */
    private String realName;

    /** ADMIN | TEACHER */
    private String roleType;

    /** ENABLED | FROZEN */
    private String status;

    /** 令牌版本号：改密/禁用/改角色时 +1，用于吊销旧 token */
    private Integer tokenVersion;

    /** 所属院系 */
    private String department;

    /** 手机号 */
    private String phone;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
