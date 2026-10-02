package com.example.sms.service; // 声明当前类所在的包：service 服务层

// ===== import 区域：引入 MyBatis-Plus 查询构造器、业务异常、DTO/VO、实体、Mapper、Jackson、Spring 相关注解与 Java HTTP/安全/并发工具 =====
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper; // MyBatis-Plus 的 Lambda 查询条件构造器
import com.example.sms.common.BusinessException; // 自定义业务异常类
import com.example.sms.dto.LoginResponse; // 登录响应 DTO（用户信息 + JWT）
import com.example.sms.dto.OAuthCallbackVO; // OAuth 回调结果 VO（登录成功或待绑定）
import com.example.sms.entity.OAuthBinding; // OAuth 绑定关系实体类
import com.example.sms.mapper.OAuthBindingMapper; // OAuth 绑定表 Mapper 接口
import com.fasterxml.jackson.core.JsonProcessingException; // Jackson JSON 处理异常
import com.fasterxml.jackson.databind.JsonNode; // Jackson JSON 树节点：用于解析 GitHub 返回的 JSON
import com.fasterxml.jackson.databind.ObjectMapper; // Jackson JSON 序列化/反序列化器
import org.springframework.beans.factory.annotation.Autowired; // Spring 依赖注入注解
import org.springframework.beans.factory.annotation.Value; // Spring 配置注入注解：读取 application 配置项
import org.springframework.data.redis.core.StringRedisTemplate; // Spring Data Redis 字符串模板
import org.springframework.stereotype.Service; // Spring 服务层注解
import java.net.URI; // URI 对象：构造 HTTP 请求地址
import java.net.URLEncoder; // URL 编码工具：拼接查询参数与表单体
import java.net.http.HttpClient; // Java 11+ 原生 HTTP 客户端
import java.net.http.HttpRequest; // HTTP 请求对象
import java.net.http.HttpResponse; // HTTP 响应对象
import java.nio.charset.StandardCharsets; // 标准字符集（UTF-8）
import java.security.SecureRandom; // 密码学安全随机数：生成 state/授权码
import java.util.Base64; // Base64 编码：把随机字节转成 URL 安全字符串
import java.util.concurrent.ConcurrentHashMap; // 并发安全的哈希表：Redis 不可用时的内存降级存储
import java.util.concurrent.TimeUnit; // 时间单位枚举

/**
 * GitHub OAuth 登录服务：
 * 1. buildAuthorizeUrl：生成授权跳转地址（state 防 CSRF）
 * 2. handleCallback：code 换 token → 拉取 GitHub 用户 → 已绑定直接登录 / 未绑定返回待绑定凭证
 * 3. bind：校验现有账号密码后建立绑定关系并签发系统 JWT
 */
@Service // 声明为 Spring 服务组件，交由容器管理
public class GithubOAuthService { // GitHub OAuth 登录服务类：授权跳转、回调处理、账号绑定与一次性授权码登录态

    private static final String PROVIDER = "github"; // 第三方登录提供商标识：github（存于绑定表）
    private static final String AUTH_URL = "https://github.com/login/oauth/authorize"; // GitHub 授权页地址（引导用户授权）
    private static final String TOKEN_URL = "https://github.com/login/oauth/access_token"; // GitHub 换 access_token 的接口地址
    private static final String USER_URL = "https://api.github.com/user"; // GitHub 用户信息接口地址（拉取用户唯一 id）
    /** OAuth state 有效期（与 Redis key TTL 一致，10 分钟） */
    private static final long STATE_TTL_SECONDS = 10 * 60L; // state 有效期 10 分钟（防 CSRF 的临时凭证）
    private static final String STATE_KEY = "sms:oauth:state:"; // state 缓存 key 前缀

    /** 登录态授权码 key / 有效期（回调页凭此换取登录态，120 秒一次性，避免 JWT 暴露在 URL） */
    private static final long AUTH_CODE_TTL_SECONDS = 120; // 一次性授权码有效期 120 秒（短 TTL 压缩泄露窗口）
    private static final String AUTH_CODE_KEY = "sms:oauth:code:"; // 授权码缓存 key 前缀

    @Value("${oauth.github.client-id:}") // 从配置读取 GitHub OAuth Client ID（未配置默认空字符串）
    private String clientId; // GitHub 应用 Client ID（后端持有，不暴露前端）

    @Value("${oauth.github.client-secret:}") // 从配置读取 GitHub OAuth Client Secret
    private String clientSecret; // GitHub 应用 Client Secret（仅后端使用）

    @Value("${oauth.github.redirect-uri:}") // 从配置读取授权回调地址
    private String redirectUri; // 授权成功后的回调 URI（须与 GitHub 应用配置一致）

    @Autowired // Spring 自动注入 OAuthBindingMapper
    private OAuthBindingMapper bindingMapper; // OAuth 绑定表 Mapper：维护 GitHub 账号与系统账号的绑定关系

    @Autowired // Spring 自动注入 AuthService
    private AuthService authService; // 认证服务：绑定前校验账号密码、已绑定后直接签发登录态

    @Autowired // Spring 自动注入 StringRedisTemplate
    private StringRedisTemplate redis; // Redis 客户端：缓存 state 与一次性授权码

    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build(); // HTTP 客户端：自动跟随重定向（GitHub 接口偶发重定向）
    private final ObjectMapper mapper = new ObjectMapper(); // Jackson 对象映射器：解析 GitHub JSON 响应
    private final SecureRandom random = new SecureRandom(); // 安全随机数生成器：生成 state/授权码

    /** Redis 不可用时的内存降级存储（与登录锁定/限流一致的"降级放行"策略）：
     *  仅保证单实例场景 OAuth 流程可用；生产多实例务必保证 Redis 可用。 */
    private final ConcurrentHashMap<String, MemEntry> memCache = new ConcurrentHashMap<>(); // 内存降级缓存：Redis 故障时存 state/授权码

    /** 内存降级缓存条目：缓存值 + 绝对过期时间戳（读取时校验，过期即视为不存在） */
    private static final class MemEntry { // 内存缓存条目内部类
        final String value; // 缓存的值（state 标记或登录态 JSON）
        final long expireAt; // 绝对过期时间戳（毫秒）
        MemEntry(String value, long ttlSeconds) { // 构造函数：写入值并计算过期时间
            this.value = value; // 保存值
            this.expireAt = System.currentTimeMillis() + ttlSeconds * 1000; // 过期时间 = 当前时间 + TTL 秒数转毫秒
        }
    }

    /**
     * 1. 生成 GitHub 授权跳转地址（未配置时抛业务提示）。
     * 调用逻辑：OAuthController.authorize（/api/oauth/github/authorize，带每 IP 轻量限流）→ githubOAuthService.buildAuthorizeUrl：前端"GitHub 登录"点击后取回授权 URL，浏览器跳转 GitHub 授权页。
     * 为什么：每次生成随机 state 并缓存（Redis 优先、不可用降级内存），回调时必须原样带回并一次性消费，防止 CSRF 登录/绑定劫持；客户端凭据未配置时明确报错而非静默失败，便于运维发现配置遗漏。
     */
    public String buildAuthorizeUrl() { // 生成 GitHub 授权跳转 URL（带随机 state）
        // 客户端凭据缺失时直接给出明确提示而非静默失败，便于运维发现配置遗漏
        if (clientId.isBlank() || clientSecret.isBlank()) { // Client ID 或 Secret 未配置
            throw new BusinessException("GitHub 登录未启用，请联系管理员配置 GITHUB_CLIENT_ID / GITHUB_CLIENT_SECRET"); // 明确报错提示配置
        }
        // state 防 CSRF：随机值随跳转地址发出，回调时必须原样带回并被校验（consumeState），
        // 防止攻击者构造恶意回调 URL 诱导受害者携带其授权码（登录劫持/绑定劫持）。
        String state = genState(); // 生成随机 state 并缓存（一次性防 CSRF）
        return AUTH_URL + "?client_id=" + enc(clientId) // 拼接授权 URL：携带 Client ID
                + "&redirect_uri=" + enc(redirectUri) // 携带回调地址（URL 编码）
                + "&scope=read:user&state=" + state; // 申请读取用户公开信息权限，并携带 state 参数
    }

    /**
     * 2. 回调处理：code + state → 已绑定返回登录成功（含用户信息），否则返回待绑定凭证。
     * 调用逻辑：OAuthController.callback（/api/oauth/github/callback）→ githubOAuthService.handleCallback：GitHub 授权后 302 回调，校验并一次性消费 state → code 换 access_token → 拉取用户 uid → 已绑定则 issueByUserNo 签发登录态（经一次性授权码回前端换取），未绑定则返回 providerUid 引导走 bind。
     * 为什么：state 一次性防 CSRF（存在即删除，多实例并发仅一个请求能消费成功）；回调 URL 不携带 JWT，改用 120 秒一次性授权码，避免登录态暴露在浏览器历史/第三方日志中。
     */
    public OAuthCallbackVO handleCallback(String code, String state) { // 处理 GitHub 授权回调：校验 state、换 token、拉用户、决定登录或待绑定
        if (code == null || code.isBlank()) { // 回调缺少授权码 code
            throw new BusinessException("授权回调缺少 code"); // 提示缺少 code
        }
        // 校验并消费 state（原子删除）：state 必须是本系统先前发出且未被使用过的，
        // 一次性校验保证回调确实由我们发起的授权流程触发，而非攻击者伪造的请求。
        if (!consumeState(state)) { // state 无效或已被使用（一次性消费失败）
            throw new BusinessException("回调 state 无效或已过期，请重新发起登录"); // 拒绝并提示重新发起
        }
        // 用 GitHub 返回的授权码 code 换取 access_token，再拉取用户信息获取全局唯一 uid
        String accessToken = exchangeToken(code); // 用授权码换取 GitHub access_token
        JsonNode user = fetchGithubUser(accessToken); // 拉取 GitHub 当前用户信息
        String uid = String.valueOf(user.path("id").asLong()); // 取 GitHub 用户全局唯一 id 作为绑定依据
        OAuthBinding binding = findBinding(PROVIDER, uid); // 查询该 GitHub uid 是否已绑定系统账号
        OAuthCallbackVO vo = new OAuthCallbackVO(); // 创建回调结果对象
        if (binding != null) { // 已绑定系统账号
            // 已绑定过系统账号：直接按绑定关系签发登录态（无需再输密码）。
            // issueByUserNo 内部仍会校验账号存在且 ENABLED，防止账号被删/停用后继续放行。
            LoginResponse resp = authService.issueByUserNo(binding.getUserNo()); // 按绑定账号直接签发系统登录态
            vo.setStatus("LOGIN_SUCCESS"); // 状态：登录成功
            // 回调 URL 不携带 JWT，改为一次性授权码（Redis 短 TTL 缓存登录态）
            vo.setAuthCode(issueAuthCode(resp)); // 生成一次性授权码（前端凭此换取登录态，避免 JWT 暴露在 URL）
        } else { // 未绑定系统账号
            // 未绑定：返回 GitHub uid 给前端，走 bind 绑定现有账号
            vo.setStatus("NEED_BIND"); // 状态：需要绑定
            vo.setProviderUid(uid); // 返回 GitHub uid 供前端绑定流程使用
        }
        return vo; // 返回回调处理结果
    }

    /**
     * 3. 绑定现有账号并登录。
     * 调用逻辑：OAuthController.bind（/api/oauth/github/bind）→ githubOAuthService.bind：前端带 GitHub uid + 系统账号密码提交，先校验该 uid 未绑定他号 → verifyAndLogin 校验账号密码 → 插入 OAuthBinding 绑定记录 → 返回 JWT 登录态。
     * 为什么：绑定前必须账号密码校验（复用登录守卫：锁定+限流+ENABLED），防止拿截获 uid 越权绑定到他人账号；唯一索引（uk_provider_uid / uk_user_provider）兜底并发重复绑定并转明确业务提示；Redis 不可用时降级内存，保证单实例下 OAuth 流程可用。
     */
    public LoginResponse bind(String username, String password, String providerUid) { // 绑定 GitHub 账号到系统账号（校验账号密码后建立绑定关系）
        if (providerUid == null || providerUid.isBlank()) { // 缺少 GitHub uid 凭证
            throw new BusinessException("绑定凭证缺失，请重新发起 GitHub 登录"); // 提示重新发起登录
        }
        // 防一码多用：该 GitHub uid 必须尚未绑定其他账号。
        // 若不检查，同一 GitHub 账号可被反复绑定到不同系统账号，造成账号归属混乱与越权风险；
        // 绑定关系一旦建立，后续登录一律按该条绑定记录签发（见 handleCallback 中 findBinding）。
        if (findBinding(PROVIDER, providerUid) != null) { // 该 GitHub uid 已绑定其他账号
            throw new BusinessException("该 GitHub 账号已绑定其他账号，请直接登录"); // 拒绝重复绑定
        }
        // 绑定前必须校验账号密码（verifyAndLogin 与 login 共用同一登录守卫：锁定+限流+ENABLED），
        // 确保绑定操作由账号本人发起，杜绝"拿别人 uid 直接绑到自己的账号"的越权绑定。
        LoginResponse resp = authService.verifyAndLogin(username.trim(), password); // 校验系统账号密码并签发登录态
        OAuthBinding b = new OAuthBinding(); // 创建绑定关系实体
        b.setUserNo(resp.getUserNo()); // 绑定系统账号号（工号/学号）
        b.setProvider(PROVIDER); // 绑定提供方：github
        b.setProviderUid(providerUid); // 绑定 GitHub 唯一 id
        try { // 尝试插入绑定记录
            bindingMapper.insert(b); // 插入绑定表
        } catch (org.springframework.dao.DuplicateKeyException e) { // 插入撞唯一索引（并发/重试场景）
            // 并发/重试下唯一索引（uk_provider_uid / uk_user_provider）兜底：
            // 两个请求同时通过 findBinding 检查后都走到 insert，后到者撞唯一键，
            // 这里转成明确的业务提示而非 500，避免竞态暴露为服务端错误。
            throw new BusinessException("该 GitHub 账号或系统账号已存在绑定关系，请直接登录"); // 转成明确业务提示
        }
        return resp; // 返回已签发的登录态
    }

    // ===== 内部工具 =====

    /** 生成一次性授权码并缓存登录态（Redis 短 TTL，不可用则降级内存），回调 URL 不携带 JWT（Q-5） */
    private String issueAuthCode(LoginResponse resp) { // 生成一次性授权码并缓存登录态 JSON
        String code = genState(); // 复用随机生成逻辑生成授权码（同样是一次性消费）
        try { // 捕获 JSON 序列化异常
            cacheSet(AUTH_CODE_KEY + code, mapper.writeValueAsString(resp), AUTH_CODE_TTL_SECONDS); // 缓存登录态 JSON，TTL 120 秒
        } catch (JsonProcessingException e) { // 登录态序列化失败
            throw new BusinessException("登录态生成失败，请重新登录"); // 转成业务异常
        }
        return code; // 返回一次性授权码
    }

    /** 用一次性授权码换取登录态（校验存在 + 消费删除，仅能使用一次） */
    public LoginResponse exchangeAuthCode(String authCode) { // 前端用一次性授权码换取登录态（仅能换一次）
        if (authCode == null || authCode.isBlank()) { // 授权码缺失
            throw new BusinessException("授权码缺失，请重新登录"); // 提示重新登录
        }
        String json = cacheTake(AUTH_CODE_KEY + authCode); // 读取并删除授权码（一次性：存在即消费）
        if (json == null) { // 授权码不存在（已被使用或已过期）
            // 授权码不存在（已被使用或已过期）：拒绝换取。
            // 120 秒短 TTL + 使用即删除，双重机制保证"一次性"——即便授权码泄露，泄露窗口也被压缩到极小。
            throw new BusinessException("授权码无效或已过期，请重新登录"); // 拒绝换取
        }
        try { // 捕获反序列化异常
            return mapper.readValue(json, LoginResponse.class); // 把 JSON 反序列化为登录响应对象
        } catch (Exception e) { // 解析失败
            throw new BusinessException("登录态解析失败，请重新登录"); // 转成业务异常
        }
    }

    private OAuthBinding findBinding(String provider, String uid) { // 按提供方与 uid 查询绑定关系
        // LIMIT 1 显式取第一条：配合数据库唯一索引（uk_provider_uid），正常数据只有一条；
        // 即使历史遗留脏数据存在多条，也按"第一条"稳定签发，避免 selectOne 在多于一条时抛异常。
        return bindingMapper.selectOne(new LambdaQueryWrapper<OAuthBinding>() // 查询绑定记录
                .eq(OAuthBinding::getProvider, provider) // 条件一：提供方 = github
                .eq(OAuthBinding::getProviderUid, uid) // 条件二：GitHub uid
                .last("LIMIT 1")); // 显式限制只取第一条（防御脏数据）
    }

    /** 用 GitHub 授权码 code 换取 access_token（表单 POST，失败转业务异常并透出 GitHub 错误描述） */
    private String exchangeToken(String code) { // 用授权码向 GitHub 换取 access_token
        String body = "client_id=" + enc(clientId) // 表单体：Client ID
                + "&client_secret=" + enc(clientSecret) // 表单体：Client Secret
                + "&code=" + enc(code) // 表单体：授权码
                + "&redirect_uri=" + enc(redirectUri); // 表单体：回调地址
        HttpRequest req = HttpRequest.newBuilder(URI.create(TOKEN_URL)) // 构造 POST 请求：指向换 token 接口
                .header("Accept", "application/json") // 要求返回 JSON（GitHub 默认返回表单格式）
                .header("Content-Type", "application/x-www-form-urlencoded") // 表单提交格式
                .POST(HttpRequest.BodyPublishers.ofString(body)) // 设置表单请求体
                .build(); // 构建请求对象
        JsonNode json = sendJson(req); // 发送请求并解析 JSON 响应
        String token = json.path("access_token").asText(null); // 从响应中取 access_token（缺失为 null）
        if (token == null) { // 未换取到 token（授权失败）
            throw new BusinessException("GitHub 授权失败：" // 抛出失败提示
                    + json.path("error_description").asText("获取 access_token 失败")); // 透出 GitHub 返回的错误描述
        }
        return token; // 返回 access_token
    }

    /** 携带 access_token 调用 GitHub API 拉取当前登录用户信息（从中取全局唯一 id 作为绑定依据） */
    private JsonNode fetchGithubUser(String accessToken) { // 拉取 GitHub 当前用户信息
        HttpRequest req = HttpRequest.newBuilder(URI.create(USER_URL)) // 构造 GET 请求：指向用户信息接口
                .header("Authorization", "Bearer " + accessToken) // 携带 Bearer 令牌认证
                .header("Accept", "application/vnd.github+json") // GitHub API 指定的媒体类型
                .GET() // GET 方法
                .build(); // 构建请求对象
        return sendJson(req); // 发送请求并返回解析后的 JSON
    }

    /** 发送 HTTP 请求并解析 JSON 响应；非 200 或解析异常统一转为业务异常（不向外抛底层异常） */
    private JsonNode sendJson(HttpRequest req) { // 统一发送 HTTP 请求并解析 JSON
        try { // 捕获网络与解析异常
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString()); // 同步发送请求并取字符串响应体
            if (resp.statusCode() != 200) { // 响应状态不是 200
                throw new BusinessException("GitHub 接口调用失败（HTTP " + resp.statusCode() + "）"); // 转成业务异常并带状态码
            }
            return mapper.readTree(resp.body()); // 把响应体解析为 JSON 树
        } catch (BusinessException e) { // 业务异常直接上抛（不包装）
            throw e; // 保持原异常
        } catch (Exception e) { // 其他异常（网络超时/JSON 解析等）
            throw new BusinessException("GitHub 服务调用异常，请稍后重试"); // 统一转成业务异常
        }
    }

    private String genState() { // 生成随机 state（或授权码）并缓存（一次性防 CSRF）
        byte[] bytes = new byte[24]; // 24 字节随机数（192 位，足够随机）
        random.nextBytes(bytes); // 用安全随机数填充字节数组
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); // 编码为 URL 安全的 Base64 字符串
        // 存储并设置 TTL（多实例共享走 Redis；Redis 不可用降级内存，过期自动清理，无需手动回收）
        cacheSet(STATE_KEY + state, "1", STATE_TTL_SECONDS); // 缓存 state，TTL 10 分钟
        return state; // 返回随机字符串
    }

    private boolean consumeState(String state) { // 校验并一次性消费 state（防 CSRF）
        if (state == null) { // state 为空
            return false; // 直接判定无效
        }
        // 一次性消费：存在即删除（Redis 用 DEL 返回值原子判断，内存用 remove 返回值）。
        // 比"先 GET 判断再 DEL"更安全——多实例并发回调时只有删除操作能保证只有一个请求拿到 true。
        return cacheDelete(STATE_KEY + state); // 删除并返回是否删除成功（存在且有效才为 true）
    }

    // ===== 缓存读写（Redis 优先，不可用时降级本机内存，保证 OAuth 流程不被基础设施故障拖垮） =====

    /** 写入缓存：优先 Redis，Redis 不可用（连接失败/超时）时降级到本机内存。
     *  降级仅影响"多实例共享"，单实例下 state/authCode 仍是一次性 + TTL 双重兜底。 */
    private void cacheSet(String key, String value, long ttlSeconds) { // 写入缓存（Redis 优先，失败降级内存）
        try { // 尝试写 Redis
            redis.opsForValue().set(key, value, ttlSeconds, TimeUnit.SECONDS); // Redis 写入并设置 TTL
            return; // 写入成功直接返回
        } catch (Exception ignored) { // Redis 不可用
            // Redis 不可用：降级内存（与登录锁定/限流一致的"降级放行"策略）
        }
        memCache.put(key, new MemEntry(value, ttlSeconds)); // 降级：写入内存缓存（带过期时间）
    }

    /** 存在即删除（一次性消费判断）：Redis 用 DEL 返回值（原子），内存用 remove 返回值。 */
    private boolean cacheDelete(String key) { // 存在即删除：返回是否删掉了一个未过期的值（一次性消费语义）
        try { // 尝试走 Redis
            return Boolean.TRUE.equals(redis.delete(key)); // Redis 原子删除：删除成功（值存在）返回 true
        } catch (Exception ignored) { // Redis 不可用
            // Redis 不可用：直接走内存
        }
        MemEntry entry = memCache.remove(key); // 内存删除并返回条目
        return entry != null && entry.expireAt >= System.currentTimeMillis(); // 条目存在且未过期才算消费成功
    }

    /** 读取并删除（返回缓存值，可能为 null）：授权码场景使用（保留原 get→delete 语义）。 */
    private String cacheTake(String key) { // 读取并删除缓存值（授权码场景：一次性换取）
        try { // 尝试走 Redis
            String value = redis.opsForValue().get(key); // 读取值
            if (value != null) { // 值存在
                redis.delete(key); // 删除（一次性消费）
                return value; // 返回值
            }
        } catch (Exception ignored) { // Redis 不可用
            // Redis 不可用：直接走内存
        }
        MemEntry entry = memCache.remove(key); // 内存读取并删除
        return entry != null && entry.expireAt >= System.currentTimeMillis() ? entry.value : null; // 未过期则返回值，否则视为不存在
    }

    /** URL 编码（UTF-8），用于拼接授权地址查询参数与换 token 的表单体 */
    private String enc(String s) { // URL 编码：保证查询参数/表单体中的特殊字符安全
        return URLEncoder.encode(s, StandardCharsets.UTF_8); // 按 UTF-8 进行 URL 编码
    }
}
