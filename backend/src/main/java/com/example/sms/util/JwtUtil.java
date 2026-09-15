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
        // 安全加固：密钥必须来自环境变量，禁止空值 / 弱密钥 / 已知泄露的默认密钥
        String leakedDefault = "student-management-system-secret-key-please-change-in-production-2026";
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT 密钥未配置：请通过环境变量 JWT_SECRET 注入");
        }
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
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("userNo", userNo);
        claims.put("realName", realName);
        claims.put("roleType", roleType);
        Date now = new Date();
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
            return Jwts.parserBuilder().setSigningKey(key).build()
                    .parseClaimsJws(token).getBody();
        } catch (Exception e) {
            return null;
        }
    }
}
