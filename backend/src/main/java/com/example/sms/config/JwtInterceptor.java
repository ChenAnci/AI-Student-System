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
            {"/api/stats/admin", "ADMIN"},
            {"/api/stats/teacher", "TEACHER"},
            {"/api/ai/**", "STUDENT,ADMIN"},
            // 站内通知（精确规则在前，fail-closed）
            {"/api/notifications/unread-count", "STUDENT"},
            {"/api/notifications/read-all",     "STUDENT"},
            {"/api/notifications/sent",         "TEACHER,ADMIN"},
            {"/api/notifications/*/read",       "STUDENT"},
            {"/api/notifications/send",         "TEACHER,ADMIN"},
            {"/api/notifications/**",           "STUDENT"},
    };

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 放行预检请求：浏览器跨域时会先发 OPTIONS 预检（无 Authorization 头），
        // 若在此被拦截会返回 401/403，导致前端真正的业务请求无法发起，因此必须直接放行。
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        // 从请求头取 Bearer Token：只有形如 "Bearer <token>" 才尝试解析，
        // 缺少前缀或未携带时解析结果为 null（parseToken 内部对任何异常都返回 null，不向外抛）。
        String token = request.getHeader("Authorization");
        Claims claims = (token != null && token.startsWith("Bearer "))
                ? jwtUtil.parseToken(token.substring(7))
                : null;
        if (claims == null) {
            // 未登录/Token 失效/被篡改：统一按未认证处理，返回真实 HTTP 401，
            // 由前端引导跳转登录页；fail-closed——任何解析不到合法身份的情况都不得放行。
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"未登录或登录已过期\",\"data\":null}");
            return false;
        }
        // 将当前登录用户写入 ThreadLocal（线程私有）：同一请求线程内的 Controller/Service 可直接
        // 通过 UserContext 取到 userId/role 等，避免层层透传参数；注意必须在 afterCompletion 中清理。
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
        // 顺序遍历规则表：规则按"精确优先、通配靠后"排列，
        // 一旦路径命中某条规则，就只在该规则允许的角色内判断——命中即终止，避免继续被后面的通配规则覆盖。
        for (String[] rule : ROLE_RULES) {
            if (PATH_MATCHER.match(rule[0], uri)) {
                for (String allowed : rule[1].split(",")) {
                    if (allowed.equals(role)) {
                        return true;
                    }
                }
                // 命中规则但角色不在允许列表：直接拒绝，不再向下匹配通配规则（防止越权）
                return false;
            }
        }
        // 循环结束仍无任何规则命中：默认拒绝（fail-closed）。
        // 新增接口若忘记在 ROLE_RULES 登记，访问一律 403，宁可误伤也不放开（R-5）。
        return false;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 请求处理完毕必须清理 ThreadLocal：Servlet 容器线程池会复用线程，
        // 若不清理，下一请求（甚至未登录请求）可能读到上一个用户的信息，造成串号/越权。
        UserContext.clear();
    }
}
