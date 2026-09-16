package com.example.sms.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Collections;

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
@Slf4j
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final String KEY = "sms:rate:login:ip:";
    private static final int LIMIT = 30;
    private static final long WINDOW_SECONDS = 60;

    @Value("${app.rate-limit.trust-xff:false}")
    private boolean trustXff;

    @Autowired
    private StringRedisTemplate redis;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!"/api/auth/login".equals(request.getRequestURI())) {
            return true;
        }
        String ip = clientIp(request);
        try {
            // INCR + 首次 EXPIRE 原子脚本（避免进程崩溃导致 key 无 TTL 永久残留）
            Long count = redis.execute(RedisConfig.INCR_EXPIRE_SCRIPT,
                    Collections.singletonList(KEY + ip), String.valueOf(WINDOW_SECONDS));
            if (count != null && count > LIMIT) {
                response.setStatus(429);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":429,\"message\":\"请求过于频繁，请稍后再试\",\"data\":null}");
                return false;
            }
        } catch (DataAccessException e) {
            // Redis 不可用时降级放行（可用性优先，防护暂时失效），记录告警
            log.warn("Redis 不可用，登录 IP 限流降级放行：{}", e.getMessage());
        }
        return true;
    }

    private String clientIp(HttpServletRequest req) {
        // 仅当显式信任（TRUST_XFF=true，部署在可信代理后）才取 X-Forwarded-For 首值，否则用连接对端 IP
        if (trustXff) {
            String xff = req.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                return xff.split(",")[0].trim();
            }
        }
        return req.getRemoteAddr();
    }
}
