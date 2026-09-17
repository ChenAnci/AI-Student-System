package com.example.sms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.sms.common.BusinessException;
import com.example.sms.dto.LoginResponse;
import com.example.sms.dto.OAuthCallbackVO;
import com.example.sms.entity.OAuthBinding;
import com.example.sms.mapper.OAuthBindingMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
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
import java.util.concurrent.ConcurrentHashMap;
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

    /** 登录态授权码 key / 有效期（回调页凭此换取登录态，120 秒一次性，避免 JWT 暴露在 URL） */
    private static final long AUTH_CODE_TTL_SECONDS = 120;
    private static final String AUTH_CODE_KEY = "sms:oauth:code:";

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

    /** Redis 不可用时的内存降级存储（与登录锁定/限流一致的"降级放行"策略）：
     *  仅保证单实例场景 OAuth 流程可用；生产多实例务必保证 Redis 可用。 */
    private final ConcurrentHashMap<String, MemEntry> memCache = new ConcurrentHashMap<>();

    private static final class MemEntry {
        final String value;
        final long expireAt;
        MemEntry(String value, long ttlSeconds) {
            this.value = value;
            this.expireAt = System.currentTimeMillis() + ttlSeconds * 1000;
        }
    }

    /** 1. 生成 GitHub 授权跳转地址（未配置时抛业务提示） */
    public String buildAuthorizeUrl() {
        // 客户端凭据缺失时直接给出明确提示而非静默失败，便于运维发现配置遗漏
        if (clientId.isBlank() || clientSecret.isBlank()) {
            throw new BusinessException("GitHub 登录未启用，请联系管理员配置 GITHUB_CLIENT_ID / GITHUB_CLIENT_SECRET");
        }
        // state 防 CSRF：随机值随跳转地址发出，回调时必须原样带回并被校验（consumeState），
        // 防止攻击者构造恶意回调 URL 诱导受害者携带其授权码（登录劫持/绑定劫持）。
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
        // 校验并消费 state（原子删除）：state 必须是本系统先前发出且未被使用过的，
        // 一次性校验保证回调确实由我们发起的授权流程触发，而非攻击者伪造的请求。
        if (!consumeState(state)) {
            throw new BusinessException("回调 state 无效或已过期，请重新发起登录");
        }
        // 用 GitHub 返回的授权码 code 换取 access_token，再拉取用户信息获取全局唯一 uid
        String accessToken = exchangeToken(code);
        JsonNode user = fetchGithubUser(accessToken);
        String uid = String.valueOf(user.path("id").asLong());
        OAuthBinding binding = findBinding(PROVIDER, uid);
        OAuthCallbackVO vo = new OAuthCallbackVO();
        if (binding != null) {
            // 已绑定过系统账号：直接按绑定关系签发登录态（无需再输密码）。
            // issueByUserNo 内部仍会校验账号存在且 ENABLED，防止账号被删/停用后继续放行。
            LoginResponse resp = authService.issueByUserNo(binding.getUserNo());
            vo.setStatus("LOGIN_SUCCESS");
            // 回调 URL 不携带 JWT，改为一次性授权码（Redis 短 TTL 缓存登录态）
            vo.setAuthCode(issueAuthCode(resp));
        } else {
            // 未绑定：返回 GitHub uid 给前端，走 bind 绑定现有账号
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
        // 防一码多用：该 GitHub uid 必须尚未绑定其他账号。
        // 若不检查，同一 GitHub 账号可被反复绑定到不同系统账号，造成账号归属混乱与越权风险；
        // 绑定关系一旦建立，后续登录一律按该条绑定记录签发（见 handleCallback 中 findBinding）。
        if (findBinding(PROVIDER, providerUid) != null) {
            throw new BusinessException("该 GitHub 账号已绑定其他账号，请直接登录");
        }
        // 绑定前必须校验账号密码（verifyAndLogin 与 login 共用同一登录守卫：锁定+限流+ENABLED），
        // 确保绑定操作由账号本人发起，杜绝"拿别人 uid 直接绑到自己的账号"的越权绑定。
        LoginResponse resp = authService.verifyAndLogin(username.trim(), password);
        OAuthBinding b = new OAuthBinding();
        b.setUserNo(resp.getUserNo());
        b.setProvider(PROVIDER);
        b.setProviderUid(providerUid);
        try {
            bindingMapper.insert(b);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 并发/重试下唯一索引（uk_provider_uid / uk_user_provider）兜底：
            // 两个请求同时通过 findBinding 检查后都走到 insert，后到者撞唯一键，
            // 这里转成明确的业务提示而非 500，避免竞态暴露为服务端错误。
            throw new BusinessException("该 GitHub 账号或系统账号已存在绑定关系，请直接登录");
        }
        return resp;
    }

    // ===== 内部工具 =====

    /** 生成一次性授权码并缓存登录态（Redis 短 TTL，不可用则降级内存），回调 URL 不携带 JWT（Q-5） */
    private String issueAuthCode(LoginResponse resp) {
        String code = genState();
        try {
            cacheSet(AUTH_CODE_KEY + code, mapper.writeValueAsString(resp), AUTH_CODE_TTL_SECONDS);
        } catch (JsonProcessingException e) {
            throw new BusinessException("登录态生成失败，请重新登录");
        }
        return code;
    }

    /** 用一次性授权码换取登录态（校验存在 + 消费删除，仅能使用一次） */
    public LoginResponse exchangeAuthCode(String authCode) {
        if (authCode == null || authCode.isBlank()) {
            throw new BusinessException("授权码缺失，请重新登录");
        }
        String json = cacheTake(AUTH_CODE_KEY + authCode);
        if (json == null) {
            // 授权码不存在（已被使用或已过期）：拒绝换取。
            // 120 秒短 TTL + 使用即删除，双重机制保证"一次性"——即便授权码泄露，泄露窗口也被压缩到极小。
            throw new BusinessException("授权码无效或已过期，请重新登录");
        }
        try {
            return mapper.readValue(json, LoginResponse.class);
        } catch (Exception e) {
            throw new BusinessException("登录态解析失败，请重新登录");
        }
    }

    private OAuthBinding findBinding(String provider, String uid) {
        // LIMIT 1 显式取第一条：配合数据库唯一索引（uk_provider_uid），正常数据只有一条；
        // 即使历史遗留脏数据存在多条，也按"第一条"稳定签发，避免 selectOne 在多于一条时抛异常。
        return bindingMapper.selectOne(new LambdaQueryWrapper<OAuthBinding>()
                .eq(OAuthBinding::getProvider, provider)
                .eq(OAuthBinding::getProviderUid, uid)
                .last("LIMIT 1"));
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
        // 存储并设置 TTL（多实例共享走 Redis；Redis 不可用降级内存，过期自动清理，无需手动回收）
        cacheSet(STATE_KEY + state, "1", STATE_TTL_SECONDS);
        return state;
    }

    private boolean consumeState(String state) {
        if (state == null) {
            return false;
        }
        // 一次性消费：存在即删除（Redis 用 DEL 返回值原子判断，内存用 remove 返回值）。
        // 比"先 GET 判断再 DEL"更安全——多实例并发回调时只有删除操作能保证只有一个请求拿到 true。
        return cacheDelete(STATE_KEY + state);
    }

    // ===== 缓存读写（Redis 优先，不可用时降级本机内存，保证 OAuth 流程不被基础设施故障拖垮） =====

    /** 写入缓存：优先 Redis，Redis 不可用（连接失败/超时）时降级到本机内存。
     *  降级仅影响"多实例共享"，单实例下 state/authCode 仍是一次性 + TTL 双重兜底。 */
    private void cacheSet(String key, String value, long ttlSeconds) {
        try {
            redis.opsForValue().set(key, value, ttlSeconds, TimeUnit.SECONDS);
            return;
        } catch (Exception ignored) {
            // Redis 不可用：降级内存（与登录锁定/限流一致的"降级放行"策略）
        }
        memCache.put(key, new MemEntry(value, ttlSeconds));
    }

    /** 存在即删除（一次性消费判断）：Redis 用 DEL 返回值（原子），内存用 remove 返回值。 */
    private boolean cacheDelete(String key) {
        try {
            return Boolean.TRUE.equals(redis.delete(key));
        } catch (Exception ignored) {
            // Redis 不可用：直接走内存
        }
        MemEntry entry = memCache.remove(key);
        return entry != null && entry.expireAt >= System.currentTimeMillis();
    }

    /** 读取并删除（返回缓存值，可能为 null）：授权码场景使用（保留原 get→delete 语义）。 */
    private String cacheTake(String key) {
        try {
            String value = redis.opsForValue().get(key);
            if (value != null) {
                redis.delete(key);
                return value;
            }
        } catch (Exception ignored) {
            // Redis 不可用：直接走内存
        }
        MemEntry entry = memCache.remove(key);
        return entry != null && entry.expireAt >= System.currentTimeMillis() ? entry.value : null;
    }

    private String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
