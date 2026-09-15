package com.example.sms.dto;

import lombok.Data;

/**
 * 登录响应
 */
@Data
public class LoginResponse {

    private String token;
    private Long userId;
    private String userNo;
    private String realName;
    private String roleType;
    /** 学生专属：姓名之外的基本信息（前端首页展示） */
    private String department;
    private String major;
    private String className;
}
