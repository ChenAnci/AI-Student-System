package com.example.sms.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 工具类
 */
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expire-hours}")
    private long expireHours;

    private Key key;

    @PostConstruct
    public void init() {
        // 安全加固：密钥必须来自环境变量，禁止空值 / 弱密钥 / 已知泄露的默认密钥。
        // JWT 是签名凭证：密钥一旦泄露，攻击者可自签任意身份的 Token 直接提权为管理员。
        String leakedDefault = "student-management-system-secret-key-please-change-in-production-2026";
        if (secret == null || secret.isBlank()) {
            // 启动即失败（fail-fast）：宁可系统起不来，也不允许带空密钥运行产生可被伪造的 Token
            throw new IllegalStateException("JWT 密钥未配置：请通过环境变量 JWT_SECRET 注入");
        }
        // 强度校验：HS256 要求密钥至少 32 字节（256 位），不足则签名可被暴力穷举；
        // 同时拒绝已知泄露的默认密钥——即便误用了仓库里出现过的字符串也直接拦截，杜绝"带着默认密钥上线"。
        if (secret.length() < 32 || secret.equals(leakedDefault)) {
            throw new IllegalStateException("JWT 密钥强度不足或仍在沿用已泄露的默认密钥，请更换 JWT_SECRET");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 Token
     *
     * @param userId   用户ID
     * @param userNo   工号/学号
     * @param realName 姓名
     * @param roleType 角色 ADMIN / TEACHER / STUDENT
     */
    public String generateToken(Long userId, String userNo, String realName, String roleType) {
        // Token 内只放身份与角色等非敏感声明，供拦截器/接口直接使用，避免每次查库；
        // 不放入密码、邮箱等敏感字段，降低 Token 泄露时的信息暴露面。
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("userNo", userNo);
        claims.put("realName", realName);
        claims.put("roleType", roleType);
        Date now = new Date();
        // 过期时间由配置（jwt.expire-hours）控制：过期后 Token 失效，强制重新登录
        Date expiry = new Date(now.getTime() + expireHours * 3600 * 1000L);
        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 解析 Token，失败返回 null
     */
    public Claims parseToken(String token) {
        try {
            // 解析时自动校验签名与过期时间：签名不符（被篡改/密钥不对）、已过期、格式非法都会抛异常
            return Jwts.parserBuilder().setSigningKey(key).build()
                    .parseClaimsJws(token).getBody();
        } catch (Exception e) {
            // 所有失败统一返回 null（不区分具体原因），由调用方按"未认证"处理：
            // 既不向攻击者暴露签名校验细节，也避免异常穿透到全局处理器造成 500。
            return null;
        }
    }
}
