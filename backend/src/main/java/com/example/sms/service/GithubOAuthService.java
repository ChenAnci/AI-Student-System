package com.example.sms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.sms.common.BusinessException;
import com.example.sms.dto.LoginResponse;
import com.example.sms.dto.OAuthCallbackVO;
import com.example.sms.entity.OAuthBinding;
import com.example.sms.mapper.OAuthBindingMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * GitHub OAuth 登录服务：
 * 1. buildAuthorizeUrl：生成授权跳转地址（state 防 CSRF）
 * 2. handleCallback：code 换 token → 拉取 GitHub 用户 → 已绑定直接登录 / 未绑定返回待绑定凭证
 * 3. bind：校验现有账号密码后建立绑定关系并签发系统 JWT
 */
@Service
public class GithubOAuthService {

    private static final String PROVIDER = "github";
    private static final String AUTH_URL = "https://github.com/login/oauth/authorize";
    private static final String TOKEN_URL = "https://github.com/login/oauth/access_token";
    private static final String USER_URL = "https://api.github.com/user";
    /** OAuth state 有效期（与 Redis key TTL 一致，10 分钟） */
    private static final long STATE_TTL_SECONDS = 10 * 60L;
    private static final String STATE_KEY = "sms:oauth:state:";

    @Value("${oauth.github.client-id:}")
    private String clientId;

    @Value("${oauth.github.client-secret:}")
    private String clientSecret;

    @Value("${oauth.github.redirect-uri:}")
    private String redirectUri;

    @Autowired
    private OAuthBindingMapper bindingMapper;

    @Autowired
    private AuthService authService;

    @Autowired
    private StringRedisTemplate redis;

    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final SecureRandom random = new SecureRandom();

    /** 1. 生成 GitHub 授权跳转地址（未配置时抛业务提示） */
    public String buildAuthorizeUrl() {
        if (clientId.isBlank() || clientSecret.isBlank()) {
            throw new BusinessException("GitHub 登录未启用，请联系管理员配置 GITHUB_CLIENT_ID / GITHUB_CLIENT_SECRET");
        }
        String state = genState();
        return AUTH_URL + "?client_id=" + enc(clientId)
                + "&redirect_uri=" + enc(redirectUri)
                + "&scope=read:user&state=" + state;
    }

    /** 2. 回调处理：code + state → 已绑定返回登录成功（含用户信息），否则返回待绑定凭证 */
    public OAuthCallbackVO handleCallback(String code, String state) {
        if (code == null || code.isBlank()) {
            throw new BusinessException("授权回调缺少 code");
        }
        if (!consumeState(state)) {
            throw new BusinessException("回调 state 无效或已过期，请重新发起登录");
        }
        String accessToken = exchangeToken(code);
        JsonNode user = fetchGithubUser(accessToken);
        String uid = String.valueOf(user.path("id").asLong());
        OAuthBinding binding = findBinding(PROVIDER, uid);
        OAuthCallbackVO vo = new OAuthCallbackVO();
        if (binding != null) {
            LoginResponse resp = authService.issueByUserNo(binding.getUserNo());
            vo.setStatus("LOGIN_SUCCESS");
            vo.setToken(resp.getToken());
            vo.setUserId(resp.getUserId());
            vo.setUserNo(resp.getUserNo());
            vo.setRealName(resp.getRealName());
            vo.setRoleType(resp.getRoleType());
        } else {
            vo.setStatus("NEED_BIND");
            vo.setProviderUid(uid);
        }
        return vo;
    }

    /** 3. 绑定现有账号并登录 */
    public LoginResponse bind(String username, String password, String providerUid) {
        if (providerUid == null || providerUid.isBlank()) {
            throw new BusinessException("绑定凭证缺失，请重新发起 GitHub 登录");
        }
        // 防一码多用：该 GitHub uid 必须尚未绑定其他账号
        if (findBinding(PROVIDER, providerUid) != null) {
            throw new BusinessException("该 GitHub 账号已绑定其他账号，请直接登录");
        }
        LoginResponse resp = authService.verifyAndLogin(username.trim(), password);
        OAuthBinding b = new OAuthBinding();
        b.setUserNo(resp.getUserNo());
        b.setProvider(PROVIDER);
        b.setProviderUid(providerUid);
        bindingMapper.insert(b);
        return resp;
    }

    // ===== 内部工具 =====

    private OAuthBinding findBinding(String provider, String uid) {
        return bindingMapper.selectOne(new LambdaQueryWrapper<OAuthBinding>()
                .eq(OAuthBinding::getProvider, provider)
                .eq(OAuthBinding::getProviderUid, uid));
    }

    private String exchangeToken(String code) {
        String body = "client_id=" + enc(clientId)
                + "&client_secret=" + enc(clientSecret)
                + "&code=" + enc(code)
                + "&redirect_uri=" + enc(redirectUri);
        HttpRequest req = HttpRequest.newBuilder(URI.create(TOKEN_URL))
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        JsonNode json = sendJson(req);
        String token = json.path("access_token").asText(null);
        if (token == null) {
            throw new BusinessException("GitHub 授权失败："
                    + json.path("error_description").asText("获取 access_token 失败"));
        }
        return token;
    }

    private JsonNode fetchGithubUser(String accessToken) {
        HttpRequest req = HttpRequest.newBuilder(URI.create(USER_URL))
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/vnd.github+json")
                .GET()
                .build();
        return sendJson(req);
    }

    private JsonNode sendJson(HttpRequest req) {
        try {
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new BusinessException("GitHub 接口调用失败（HTTP " + resp.statusCode() + "）");
            }
            return mapper.readTree(resp.body());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("GitHub 服务调用异常，请稍后重试");
        }
    }

    private String genState() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        // Redis 存储并设置 TTL（多实例共享；过期自动清理，无需手动回收）
        redis.opsForValue().set(STATE_KEY + state, "1", STATE_TTL_SECONDS, TimeUnit.SECONDS);
        return state;
    }

    private boolean consumeState(String state) {
        if (state == null) {
            return false;
        }
        // 原子删除：返回 true 表示存在且仅能消费一次（一次性 + TTL 过期双重兜底）
        return Boolean.TRUE.equals(redis.delete(STATE_KEY + state));
    }

    private String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
