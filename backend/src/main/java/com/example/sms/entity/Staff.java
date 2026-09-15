package com.example.sms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 教职工实体
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

    /** 所属院系 */
    private String department;

    /** 手机号 */
    private String phone;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
