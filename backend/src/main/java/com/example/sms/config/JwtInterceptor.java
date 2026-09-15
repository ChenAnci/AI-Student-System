package com.example.sms.config;

import com.example.sms.util.JwtUtil;
import com.example.sms.util.UserContext;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * JWT 登录鉴权拦截器
 */
@Component
public class JwtInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    /**
     * 接口角色规则：按顺序匹配，先命中的生效；未匹配到任何规则时默认拒绝（fail-closed）。
     * 新增接口必须在 ROLE_RULES 中登记角色，否则一律 403，防止遗漏造成越权。
     * 同一路径下精确规则必须放在通配规则之前（如 /api/accounts/me 在 /api/accounts/** 前）。
     */
    private static final String[][] ROLE_RULES = {
            {"/api/accounts/me", "STUDENT,TEACHER,ADMIN"},
            {"/api/accounts/**", "ADMIN"},
            {"/api/courses/my", "TEACHER,ADMIN"},
            {"/api/courses/all", "ADMIN"},
            {"/api/courses/**", "TEACHER,ADMIN"},
            {"/api/grades/my", "STUDENT"},
            {"/api/grades/dashboard", "STUDENT"},
            {"/api/grades/pending", "ADMIN"},
            {"/api/grades/audits", "ADMIN"},
            {"/api/grades/audit", "ADMIN"},
            {"/api/grades/*/publish", "ADMIN"},
            {"/api/grades/course/**", "TEACHER,ADMIN"},
            {"/api/grades/entry", "TEACHER,ADMIN"},
            {"/api/grades/*/submit", "TEACHER,ADMIN"},
            {"/api/grades/my-audits", "TEACHER,ADMIN"},
            {"/api/grades/**", "TEACHER"},
            {"/api/enrollments/*/students/*", "ADMIN"},
            {"/api/enrollments/monitor", "ADMIN"},
            {"/api/enrollments/**", "STUDENT"},
            {"/api/ai/**", "STUDENT,ADMIN"},
    };

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 放行预检请求
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String token = request.getHeader("Authorization");
        Claims claims = (token != null && token.startsWith("Bearer "))
                ? jwtUtil.parseToken(token.substring(7))
                : null;
        if (claims == null) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"未登录或登录已过期\",\"data\":null}");
            return false;
        }
        UserContext.CurrentUser user = new UserContext.CurrentUser();
        user.setUserId(((Number) claims.get("userId")).longValue());
        user.setUserNo((String) claims.get("userNo"));
        user.setRealName((String) claims.get("realName"));
        user.setRoleType((String) claims.get("roleType"));
        UserContext.set(user);

        // 角色-路径校验（读接口此前普遍缺失，此处统一兜底）
        if (!checkRole(request.getRequestURI(), user.getRoleType())) {
            // 权限失败返回真实 HTTP 403 + code=403，便于网关/WAF 按状态码统计拦截
            response.setStatus(403);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":403,\"message\":\"无权限访问该接口\",\"data\":null}");
            return false;
        }
        return true;
    }

    /** 按角色规则校验路径访问权，未匹配到任何规则时默认拒绝（fail-closed） */
    private boolean checkRole(String uri, String role) {
        for (String[] rule : ROLE_RULES) {
            if (PATH_MATCHER.match(rule[0], uri)) {
                for (String allowed : rule[1].split(",")) {
                    if (allowed.equals(role)) {
                        return true;
                    }
                }
                return false;
            }
        }
        return false;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}
