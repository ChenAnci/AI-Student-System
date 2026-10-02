package com.example.sms.config; // 声明当前类所属包：本项目所有配置类统一放在 com.example.sms.config 包下

// ---- import 区域：引入登录限流所需的 Redis、Spring MVC 与 Servlet 类 ----
import lombok.extern.slf4j.Slf4j; // Lombok 的 @Slf4j 注解：自动生成 log 日志对象
import org.springframework.beans.factory.annotation.Autowired; // @Autowired 注解：按类型自动注入依赖
import org.springframework.beans.factory.annotation.Value; // @Value 注解：将配置项值注入到字段
import org.springframework.dao.DataAccessException; // Spring 数据访问异常：用于捕获 Redis 访问故障
import org.springframework.data.redis.core.StringRedisTemplate; // Redis 字符串操作模板：执行 Lua 脚本
import org.springframework.stereotype.Component; // @Component 注解：将该类注册为 Spring 容器管理的组件
import org.springframework.web.servlet.HandlerInterceptor; // Spring MVC 拦截器接口

import javax.servlet.http.HttpServletRequest; // HTTP 请求类：读取 URI、请求头等
import javax.servlet.http.HttpServletResponse; // HTTP 响应类：写入 429 状态码与提示信息
import java.util.Collections; // 集合工具类：生成单元素列表（Redis 脚本的 keys 参数）

/**
 * 登录接口 IP 维度限流（Redis 固定窗口计数）：
 * 单 IP 每分钟最多 LIMIT 次登录尝试，防止暴力刷登录接口消耗后端资源。
 * 账号维度限流在 AuthService 内实现（同一 Redis 计数）。
 * <p>
 * 安全说明：默认使用连接对端 IP（remoteAddr），**不信任** X-Forwarded-For，
 * 防止攻击者伪造 XFF 头绕过 IP 限流；仅当部署在可信反向代理之后时，通过
 * TRUST_XFF=true 显式启用（由代理写入真实客户端 IP）。
 * <p>
 * 可用性：Redis 不可用时限流降级放行（记录告警），避免登录接口整体不可用。
 */
@Slf4j // 自动生成 log 日志对象，用于输出 Redis 故障告警
@Component // 注册为 Spring Bean：由 WebConfig 注入并注册到登录接口
public class RateLimitInterceptor implements HandlerInterceptor { // 实现拦截器接口：对登录接口做 IP 维度限流

    // 限流参数：单 IP 每 WINDOW_SECONDS 秒内最多 LIMIT 次登录尝试，超限返回 429
    private static final String KEY = "sms:rate:login:ip:"; // Redis 键前缀：拼接上 IP 即形成每个 IP 唯一的计数键
    private static final int LIMIT = 30; // 固定窗口内允许的最大尝试次数：30 次
    private static final long WINDOW_SECONDS = 60; // 固定窗口时长：60 秒（即每分钟）

    @Value("${app.rate-limit.trust-xff:false}") // 读取配置项 trust-xff（缺省 false）：是否信任 X-Forwarded-For 头
    private boolean trustXff; // 是否信任代理写入的 XFF 头（仅当部署在可信反向代理之后时置 true）

    @Autowired // 注入 Redis 模板
    private StringRedisTemplate redis; // 用于执行 Lua 计数脚本（存储登录尝试计数）

    /**
     * 调用逻辑：Spring MVC 拦截器，仅对登录接口 /api/auth/login 在进入 Controller 之前执行
     * （WebConfig 中只把本拦截器注册到该路径，内部对其它 URI 也直接放行），每次登录尝试都会先做 IP 限流计数。
     * 为什么：用 Redis INCR+EXPIRE 原子 Lua 脚本计数，避免分步执行（INCR 后进程崩溃）导致 key 永久无 TTL 残留，
     * 且计数存 Redis 使多实例共享同一限流窗口；XFF 默认不信（只认连接对端 IP）防止伪造请求头绕过限流；
     * Redis 故障时仅捕获 DataAccessException 降级放行——可用性优先，限流组件故障不拖垮登录，只记告警。
     */
    @Override // 重写拦截器的 preHandle 方法
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) // 方法签名：请求、响应、处理器对象
            throws Exception { // 声明方法可能抛出的异常
        // 只对登录接口生效：登录接口无鉴权、人人可访问，是最主要的暴力破解/资源耗尽攻击面；
        // 其余接口本身已被 JWT 拦截器鉴权，无需再做 IP 限流。
        if (!"/api/auth/login".equals(request.getRequestURI())) { // 当前请求 URI 不是登录接口
            return true; // 非登录接口：直接放行
        }
        // 取客户端 IP 作为限流维度：默认用连接对端 IP（trustXff=false），不信任 XFF 头（详见 clientIp 注释）
        String ip = clientIp(request); // 获取客户端 IP（作为限流计数的 key 维度）
        try { // 尝试执行 Redis 计数（可能因 Redis 故障抛出异常）
            // INCR + 首次 EXPIRE 原子脚本（避免进程崩溃导致 key 无 TTL 永久残留）
            Long count = redis.execute(RedisConfig.INCR_EXPIRE_SCRIPT, // 执行预先定义好的原子 Lua 计数脚本
                    Collections.singletonList(KEY + ip), String.valueOf(WINDOW_SECONDS)); // 参数：计数键（KEY+IP）、窗口秒数
            if (count != null && count > LIMIT) { // 窗口内累计次数超过上限（且脚本正常返回计数）
                // 固定窗口内次数超限：返回 429（Too Many Requests），
                // 计数存 Redis 使多实例部署共享同一窗口，避免单机限流失效。
                response.setStatus(429); // 设置 HTTP 状态码 429（请求过于频繁）
                response.setContentType("application/json;charset=UTF-8"); // 设置响应内容类型为 UTF-8 JSON
                response.getWriter().write("{\"code\":429,\"message\":\"请求过于频繁，请稍后再试\",\"data\":null}"); // 输出限流提示 JSON
                return false; // 拦截本次登录请求
            }
        } catch (DataAccessException e) { // 捕获 Redis 访问异常（如连接失败/超时）
            // Redis 不可用时降级放行（可用性优先，防护暂时失效），记录告警。
            // 注意只捕获 DataAccessException 而非 Exception：确保限流组件自身故障不拖垮登录功能，
            // 即使防护短暂失效，也优先保证合法用户能正常登录（由日志告警驱动人工介入）。
            log.warn("Redis 不可用，登录 IP 限流降级放行：{}", e.getMessage()); // 打印告警日志，便于运维介入
        }
        return true; // 未超限或已降级：放行登录请求
    }

    private String clientIp(HttpServletRequest req) { // 私有方法：获取客户端真实 IP
        // 仅当显式信任（TRUST_XFF=true，部署在可信代理后）才取 X-Forwarded-For 首值，否则用连接对端 IP
        if (trustXff) { // 配置为信任代理写入的 XFF 头
            String xff = req.getHeader("X-Forwarded-For"); // 读取 X-Forwarded-For 请求头
            if (xff != null && !xff.isBlank()) { // 头存在且内容非空白
                return xff.split(",")[0].trim(); // XFF 可能含多级代理 IP，取最左侧（真实客户端 IP）并去除首尾空格
            }
        }
        return req.getRemoteAddr(); // 默认返回连接对端 IP（不信任 XFF，防止伪造头绕过限流）
    }
}
