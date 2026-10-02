// 包声明：本类位于 dto（数据传输对象）包，用于接收前端提交的请求参数并做参数校验
package com.example.sms.dto;

// ---------- import 区域说明 ----------
// 此处导入 Lombok 的 @Data（自动生成 getter/setter 等样板代码）。
import lombok.Data;

/**
 * OAuth 授权回调结果
 */
// @Data：Lombok 注解，编译期自动生成 getter、setter、toString、equals、hashCode 方法
@Data
// GitHub OAuth 授权回调后，服务端返回给前端（回调页）的处理结果，
// 前端根据 status 决定跳转登录成功页还是引导绑定账号
public class OAuthCallbackVO {

    /** LOGIN_SUCCESS 直接登录成功 | NEED_BIND 需绑定现有账号 */
    // 回调状态：LOGIN_SUCCESS（GitHub 账号已绑定系统账号，直接登录成功）/
    // NEED_BIND（GitHub 账号未绑定，需要绑定现有账号后才能登录）
    private String status;

    /** 登录成功时的一次性授权码（回调页凭此换取登录态，避免 JWT 暴露在 URL） */
    // 一次性授权码：仅登录成功时返回，回调页凭此向后端换取正式登录态，
    // 避免把 JWT 令牌直接暴露在 URL 中造成泄露风险
    private String authCode;

    /** 待绑定时用于关联回调的 GitHub 用户唯一 id */
    // GitHub 用户唯一 id：状态为 NEED_BIND 时返回，供绑定页面关联本次回调的 GitHub 账号
    private String providerUid;
}
