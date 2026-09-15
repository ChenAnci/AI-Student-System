package com.example.sms.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * OAuth 绑定现有账号请求
 */
@Data
public class OAuthBindDTO {

    @NotBlank(message = "账号不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    @NotBlank(message = "绑定凭证缺失")
    private String providerUid;
}
