package com.example.sms.dto;

import lombok.Data;

/**
 * 登录响应
 */
@Data
public class LoginResponse {

    /** JWT 登录令牌（后续请求放入 Authorization 头） */
    private String token;
    private Long userId;
    /** 学号（学生）或工号（教职工/管理员） */
    private String userNo;
    private String realName;
    /** ADMIN 管理员 | TEACHER 教师 | STUDENT 学生 */
    private String roleType;
    /** 学生专属：姓名之外的基本信息（前端首页展示） */
    private String department;
    private String major;
    private String className;
}
