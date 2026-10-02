// 声明包名：本类属于 controller（控制器）包，统一负责接收 HTTP 请求并返回响应
package com.example.sms.controller;

// ===== 导入依赖：业务异常、统一响应体 Result、Redis 配置与操作模板、OAuth 相关 DTO/VO/服务、Swagger 注解、Lombok 日志、Spring Web 注解、Servlet、URL 编码与集合工具类 =====
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
// Lombok 注解：自动生成 SLF4J 日志对象 log，用于打印告警等日志
@Slf4j
// Swagger 注解：把本控制器在文档中归入"OAuth 登录"分组
@Api(tags = "OAuth 登录")
// Spring MVC 注解：标记为 REST 控制器，方法返回值自动以 JSON 格式写回响应体
@RestController
// 类级路由前缀：本控制器所有接口统一以 /api/oauth 开头（WebConfig 已放行，无需登录即可访问）
@RequestMapping("/api/oauth")
// OAuth 登录控制器：实现 GitHub 第三方登录的授权地址生成、回调处理、授权码换登录态、账号绑定
public class OAuthController {

    /** authorize 轻量限流：每 IP 每分钟最多 10 次，防止刷量膨胀 Redis state key（R-2） */
    // 限流计数键的前缀：完整键为 "sms:rate:oauth:ip:" + 客户端 IP
    private static final String AUTHORIZE_RATE_KEY = "sms:rate:oauth:ip:";
    // 限流阈值：同一 IP 在窗口内最多允许调用 10 次 authorize
    private static final int AUTHORIZE_RATE_LIMIT = 10;
    // 计数窗口时长（秒）：60 秒内计数有效，窗口过后自动清零
    private static final long AUTHORIZE_RATE_SECONDS = 60;

    /** 前端回调页基址（默认 vite 5173；生产通过 FRONT_BASE 环境变量替换为部署域名） */
    // 读取配置 oauth.github.front-base，缺省值为 http://localhost:5173（前端开发服务器地址）
    @Value("${oauth.github.front-base:http://localhost:5173}")
    private String frontBase;

    // 自动注入 GitHub OAuth 服务：负责拼装授权地址、处理回调、兑换授权码、绑定账号
    @Autowired
    private GithubOAuthService githubOAuthService;

    // 自动注入 Redis 字符串模板：用于 authorize 限流计数与一次性授权码的存取
    @Autowired
    private StringRedisTemplate redis;

    // Swagger 接口说明：获取 GitHub 授权跳转地址
    @ApiOperation("获取 GitHub 授权跳转地址")
    // GET 映射：访问 /api/oauth/github/authorize 获取授权地址
    @GetMapping("/github/authorize")
    // 获取授权地址接口：无鉴权，返回 {url: GitHub 授权跳转链接}；request 用于取客户端 IP 做限流
    public Result<Map<String, String>> authorize(HttpServletRequest request) {
        // 限流逻辑放在 try-catch 中：Redis 异常时降级放行，保证可用性
        try {
            // 每 IP 轻量限流：authorize 无鉴权、可被任意调用，每次调用都会在 Redis 落一个 state key，
            // 若不加限制可被批量刷量造成 Redis 内存膨胀（R-2）；这里用与登录一致的原子计数脚本。
            // 执行 Lua 原子脚本"自增计数并设置过期时间"：以"前缀+客户端IP"为 key，防止并发下计数丢失
            Long count = redis.execute(RedisConfig.INCR_EXPIRE_SCRIPT,
                    Collections.singletonList(AUTHORIZE_RATE_KEY + request.getRemoteAddr()),
                    String.valueOf(AUTHORIZE_RATE_SECONDS));
            // 当前窗口内调用次数超过阈值则拒绝，抛业务异常（由全局异常处理器统一转成错误响应）
            if (count != null && count > AUTHORIZE_RATE_LIMIT) {
                throw new BusinessException("操作过于频繁，请稍后再试");
            }
        } catch (DataAccessException e) {
            // Redis 不可用时降级放行（可用性优先），记录告警
            log.warn("Redis 不可用，authorize 限流降级放行：{}", e.getMessage());
        }
        // 调用 OAuth 服务生成 GitHub 授权跳转地址（含 client_id、redirect_uri、state）并返回
        return Result.success(Map.of("url", githubOAuthService.buildAuthorizeUrl()));
    }

    // Swagger 接口说明：GitHub 授权回调（302 重定向到前端回调页，携带一次性授权码）
    @ApiOperation("GitHub 授权回调（302 重定向到前端回调页，携带一次性授权码）")
    // GET 映射：访问 /api/oauth/github/callback 接收 GitHub 的回调请求
    @GetMapping("/github/callback")
    // 回调接口：code 为 GitHub 授权码（可缺省，失败时可能没有），state 为防 CSRF 随机串（可缺省）
    public RedirectView callback(@RequestParam(value = "code", required = false) String code,
                                 @RequestParam(value = "state", required = false) String state) {
        // 交给服务层校验 state 并兑换 GitHub 用户信息（已绑定账号则签发一次性授权码）
        OAuthCallbackVO vo = githubOAuthService.handleCallback(code, state);
        // 回调结果为"登录成功"时进入下发登录态分支
        if ("LOGIN_SUCCESS".equals(vo.getStatus())) {
            // 回调 URL 不携带 JWT，携带一次性授权码（回调页凭此换取登录态）。
            // 原因：回调 URL 会出现在浏览器历史/跳转记录/第三方日志中，JWT 直接暴露风险高；
            // 改用 120 秒一次性授权码，由前端回调页再调 exchange 接口换取，降低泄露面。
            // 302 重定向到前端回调页，URL 上携带 URL 编码后的一次性授权码
            return new RedirectView(frontBase + "/oauth/callback?authCode=" + enc(vo.getAuthCode()));
        }
        // 未绑定：把 GitHub uid 带回前端，引导用户走 bind 绑定现有账号（uid 本身不含敏感信息）
        return new RedirectView(frontBase + "/oauth/callback?needBind=1&providerUid=" + enc(vo.getProviderUid()));
    }

    // Swagger 接口说明：用一次性授权码换取登录态（回调页调用，避免 JWT 暴露在 URL）
    @ApiOperation("用一次性授权码换取登录态（回调页调用，避免 JWT 暴露在 URL）")
    // POST 映射：访问 /api/oauth/github/exchange 换取登录态
    @PostMapping("/github/exchange")
    // 换取登录态接口：请求体为 {authCode: 一次性授权码}，无需鉴权（一次性授权码本身即凭证）
    public Result<LoginResponse> exchange(@RequestBody Map<String, String> body) {
        // 换取登录态：服务端校验授权码存在并一次性删除，见 GithubOAuthService.exchangeAuthCode
        // 调用服务层校验并消费授权码，返回登录响应（含 token）
        return Result.success(githubOAuthService.exchangeAuthCode(body.get("authCode")));
    }

    // Swagger 接口说明：绑定现有账号并登录
    @ApiOperation("绑定现有账号并登录")
    // POST 映射：访问 /api/oauth/github/bind 绑定账号
    @PostMapping("/github/bind")
    // 绑定接口：@Valid 校验用户名/密码/uid 等字段，@RequestBody 绑定 OAuthBindDTO
    public Result<LoginResponse> bind(@Valid @RequestBody OAuthBindDTO dto) {
        // 绑定必须走"账号密码校验"（AuthService.verifyAndLogin 复用登录守卫），
        // 防止攻击者拿着截获的 providerUid 直接绑定到他人账号。
        // 调用服务层：校验账号密码后将 GitHub uid 与现有账号绑定并登录
        return Result.success(githubOAuthService.bind(dto.getUsername(), dto.getPassword(), dto.getProviderUid()));
    }

    // 私有工具方法：对字符串做 URL 编码（null 安全），用于把授权码/uid 安全拼接到重定向 URL 的查询参数中
    private String enc(String s) {
        // 空值返回空字符串；非空则按 UTF-8 进行 URL 编码，避免特殊字符破坏 URL
        return s == null ? "" : URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
