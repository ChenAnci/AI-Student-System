package com.example.sms.controller;

import com.example.sms.common.Result;
import com.example.sms.dto.LoginResponse;
import com.example.sms.dto.OAuthBindDTO;
import com.example.sms.dto.OAuthCallbackVO;
import com.example.sms.service.GithubOAuthService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

import javax.validation.Valid;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * OAuth 登录（GitHub）：
 * authorize 返回授权跳转地址；callback 302 到前端回调页；bind 绑定现有账号并登录。
 * 三个端点均无鉴权（WebConfig 已放行 /api/oauth/**），通过 state / 账号密码校验保证安全。
 */
@Api(tags = "OAuth 登录")
@RestController
@RequestMapping("/api/oauth")
public class OAuthController {

    /** 前端回调页基址（整页跳转；开发走 vite 5173，生产替换为部署域名） */
    private static final String FRONT_BASE = "http://localhost:5173";

    @Autowired
    private GithubOAuthService githubOAuthService;

    @ApiOperation("获取 GitHub 授权跳转地址")
    @GetMapping("/github/authorize")
    public Result<Map<String, String>> authorize() {
        return Result.success(Map.of("url", githubOAuthService.buildAuthorizeUrl()));
    }

    @ApiOperation("GitHub 授权回调（302 重定向到前端回调页，携带一次性授权码）")
    @GetMapping("/github/callback")
    public RedirectView callback(@RequestParam(value = "code", required = false) String code,
                                 @RequestParam(value = "state", required = false) String state) {
        OAuthCallbackVO vo = githubOAuthService.handleCallback(code, state);
        if ("LOGIN_SUCCESS".equals(vo.getStatus())) {
            // 回调 URL 不携带 JWT，携带一次性授权码（回调页凭此换取登录态）
            return new RedirectView(FRONT_BASE + "/oauth/callback?authCode=" + enc(vo.getAuthCode()));
        }
        return new RedirectView(FRONT_BASE + "/oauth/callback?needBind=1&providerUid=" + enc(vo.getProviderUid()));
    }

    @ApiOperation("用一次性授权码换取登录态（回调页调用，避免 JWT 暴露在 URL）")
    @PostMapping("/github/exchange")
    public Result<LoginResponse> exchange(@RequestBody Map<String, String> body) {
        return Result.success(githubOAuthService.exchangeAuthCode(body.get("authCode")));
    }

    @ApiOperation("绑定现有账号并登录")
    @PostMapping("/github/bind")
    public Result<LoginResponse> bind(@Valid @RequestBody OAuthBindDTO dto) {
        return Result.success(githubOAuthService.bind(dto.getUsername(), dto.getPassword(), dto.getProviderUid()));
    }

    private String enc(String s) {
        return s == null ? "" : URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
