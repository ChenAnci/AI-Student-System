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

    /** 签名密钥（来自配置 jwt.secret，生产环境须通过环境变量 JWT_SECRET 注入） */
    @Value("${jwt.secret}")
    private String secret;

    /** Token 有效期（小时，来自配置 jwt.expire-hours） */
    @Value("${jwt.expire-hours}")
    private long expireHours;

    /** 由密钥派生出的 HS256 签名 Key，init() 时构建，签发与校验共用 */
    private Key key;

    /**
     * 初始化签名密钥：校验密钥非空、强度足够且非已泄露默认值，任何一项不满足都启动失败（fail-fast）
     *
     * 调用逻辑：Spring 容器启动时由 @PostConstruct 自动调用（依赖注入完成后、接收任何请求之前），
     * 生成全应用共用的 HS256 签名 Key，供后续 generateToken / parseToken 使用。
     * 为什么：JWT 是签名凭证，密钥一旦泄露，攻击者可自签任意身份 Token 直接提权为管理员；
     * 因此强制密钥来自环境变量、拒绝空值/弱密钥/已泄露默认值并 fail-fast——宁可系统起不来，
     * 也不允许带着可被伪造的弱密钥运行。
     */
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
     * 调用逻辑：AuthController.login 校验账号密码成功后签发，管理员重置密码 / 禁用账号 / 改角色后再次签发；
     * 一次签发随登录响应返回前端，前端存入本地并在后续请求头 Authorization 中携带。
     * 为什么：
     * 1) 选 HS256 对称签名：JDK / 库内直接实现、性能开销小，适合"单后端签发 + 校验"场景；
     * 2) tokenVersion 参与签名声明：签发时写入当前版本，拦截器验签后再与数据库版本比对，
     *    改密 / 禁用 / 改角色后版本 +1，旧 token 立即失效，实现"无状态吊销"，无需维护服务端黑名单；
     * 3) claims 只放身份与角色等非敏感信息（不含密码、邮箱），降低 Token 泄露时的信息暴露面。
     *
     * @param userId       用户ID
     * @param userNo       工号/学号
     * @param realName     姓名
     * @param roleType     角色 ADMIN / TEACHER / STUDENT
     * @param tokenVersion 令牌版本号（改密/禁用/改角色时 +1，用于吊销旧 token）
     */
    public String generateToken(Long userId, String userNo, String realName, String roleType, Integer tokenVersion) {
        // Token 内只放身份与角色等非敏感声明，供拦截器/接口直接使用，避免每次查库；
        // 不放入密码、邮箱等敏感字段，降低 Token 泄露时的信息暴露面。
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("userNo", userNo);
        claims.put("realName", realName);
        claims.put("roleType", roleType);
        // 令牌版本号：签发时写入当前版本，验签时与数据库比对；版本不一致（改密/禁用/改角色后）即视为已吊销
        claims.put("tokenVersion", tokenVersion != null ? tokenVersion : 1);
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
     *
     * 调用逻辑：JwtInterceptor.preHandle 在每个请求进入 Controller 前调用本方法完成验签与过期校验，
     * 解析成功后把 userId / roleType 等写入 UserContext 供 Service 层读取；解析失败按"未认证"处理
     * （白名单接口跳过校验，受保护接口返回 401）。
     * 为什么返回 null 而非抛异常：
     * 1) 不向调用方 / 攻击者区分"签名不符 / 已过期 / 格式非法"等具体失败原因，避免暴露校验细节；
     * 2) 异常被吞掉不会穿透到 GlobalExceptionHandler 产生 500，统一由拦截器按未登录处理，语义更清晰。
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

    /**
     * 从已解析的 Claims 中提取令牌版本号（缺失/非法时返回 1，与签发默认值一致）
     */
    public int tokenVersion(Claims claims) {
        Object v = claims.get("tokenVersion");
        return v instanceof Number ? ((Number) v).intValue() : 1;
    }
}
