package com.example.sms.dto;

import lombok.Data;

/**
 * OAuth 授权回调结果
 */
@Data
public class OAuthCallbackVO {

    /** LOGIN_SUCCESS 直接登录成功 | NEED_BIND 需绑定现有账号 */
    private String status;

    /** 登录成功时的系统 JWT（与 LoginResponse.token 一致） */
    private String token;

    /** 待绑定时用于关联回调的 GitHub 用户唯一 id */
    private String providerUid;

    /** 登录成功的用户信息（前端直接构造登录态，免二次请求） */
    private Long userId;

    private String userNo;

    private String realName;

    private String roleType;
}
