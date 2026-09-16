package com.example.sms.dto;

import lombok.Data;

/**
 * OAuth 授权回调结果
 */
@Data
public class OAuthCallbackVO {

    /** LOGIN_SUCCESS 直接登录成功 | NEED_BIND 需绑定现有账号 */
    private String status;

    /** 登录成功时的一次性授权码（回调页凭此换取登录态，避免 JWT 暴露在 URL） */
    private String authCode;

    /** 待绑定时用于关联回调的 GitHub 用户唯一 id */
    private String providerUid;
}
