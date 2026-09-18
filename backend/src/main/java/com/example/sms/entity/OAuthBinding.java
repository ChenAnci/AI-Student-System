package com.example.sms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * OAuth 登录绑定关系，对应数据库表 oauth_binding（GitHub 账号 ↔ 系统工号/学号）
 */
@Data
@TableName("oauth_binding")
public class OAuthBinding {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 系统账号（工号/学号，对应 staff.staff_no / student.student_no） */
    private String userNo;

    /** OAuth 提供方：github */
    private String provider;

    /** GitHub 用户唯一 id */
    private String providerUid;

    /** 绑定创建时间 */
    private LocalDateTime createdAt;

    /** 绑定更新时间 */
    private LocalDateTime updatedAt;
}
