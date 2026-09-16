package com.example.sms.controller;

import com.example.sms.common.BusinessException;
import com.example.sms.common.Result;
import com.example.sms.config.RedisConfig;
import com.example.sms.dto.LoginResponse;
import com.example.sms.dto.OAuthBindDTO;
import com.example.sms.dto.OAuthCallbackVO;
import com.example.sms.service.GithubOAuthService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

/**
 * OAuth 登录（GitHub）：
 * authorize 返回授权跳转地址（带每 IP 轻量限流，防止刷量制造 Redis state key）；
 * callback 302 到前端回调页（携带一次性授权码）；bind 绑定现有账号并登录。
 * 三个端点均无鉴权（WebConfig 已放行 /api/oauth/**），通过 state / 账号密码校验保证安全。
 */
@Slf4j
@Api(tags = "OAuth 登录")
@RestController
@RequestMapping("/api/oauth")
public class OAuthController {

    /** authorize 轻量限流：每 IP 每分钟最多 10 次，防止刷量膨胀 Redis state key（R-2） */
    private static final String AUTHORIZE_RATE_KEY = "sms:rate:oauth:ip:";
    private static final int AUTHORIZE_RATE_LIMIT = 10;
    private static final long AUTHORIZE_RATE_SECONDS = 60;

    /** 前端回调页基址（默认 vite 5173；生产通过 FRONT_BASE 环境变量替换为部署域名） */
    @Value("${oauth.github.front-base:http://localhost:5173}")
    private String frontBase;

    @Autowired
    private GithubOAuthService githubOAuthService;

    @Autowired
    private StringRedisTemplate redis;

    @ApiOperation("获取 GitHub 授权跳转地址")
    @GetMapping("/github/authorize")
    public Result<Map<String, String>> authorize(HttpServletRequest request) {
        try {
            Long count = redis.execute(RedisConfig.INCR_EXPIRE_SCRIPT,
                    Collections.singletonList(AUTHORIZE_RATE_KEY + request.getRemoteAddr()),
                    String.valueOf(AUTHORIZE_RATE_SECONDS));
            if (count != null && count > AUTHORIZE_RATE_LIMIT) {
                throw new BusinessException("操作过于频繁，请稍后再试");
            }
        } catch (DataAccessException e) {
            // Redis 不可用时降级放行（可用性优先），记录告警
            log.warn("Redis 不可用，authorize 限流降级放行：{}", e.getMessage());
        }
        return Result.success(Map.of("url", githubOAuthService.buildAuthorizeUrl()));
    }

    @ApiOperation("GitHub 授权回调（302 重定向到前端回调页，携带一次性授权码）")
    @GetMapping("/github/callback")
    public RedirectView callback(@RequestParam(value = "code", required = false) String code,
                                 @RequestParam(value = "state", required = false) String state) {
        OAuthCallbackVO vo = githubOAuthService.handleCallback(code, state);
        if ("LOGIN_SUCCESS".equals(vo.getStatus())) {
            // 回调 URL 不携带 JWT，携带一次性授权码（回调页凭此换取登录态）
            return new RedirectView(frontBase + "/oauth/callback?authCode=" + enc(vo.getAuthCode()));
        }
        return new RedirectView(frontBase + "/oauth/callback?needBind=1&providerUid=" + enc(vo.getProviderUid()));
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
