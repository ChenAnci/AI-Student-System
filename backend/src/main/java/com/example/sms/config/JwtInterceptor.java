package com.example.sms.config;

import com.example.sms.entity.Staff;
import com.example.sms.entity.Student;
import com.example.sms.mapper.StaffMapper;
import com.example.sms.mapper.StudentMapper;
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

    @Autowired
    private StaffMapper staffMapper;

    @Autowired
    private StudentMapper studentMapper;

    // Ant 通配路径匹配器：用于将请求 URI 与 ROLE_RULES 中的路径规则做模式比对
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    /**
     * 接口角色规则：按顺序匹配，先命中的生效；未匹配到任何规则时默认拒绝（fail-closed）。
     * 新增接口必须在 ROLE_RULES 中登记角色，否则一律 403，防止遗漏造成越权。
     * 同一路径下精确规则必须放在通配规则之前（如 /api/accounts/me 在 /api/accounts/** 前）。
     */
    private static final String[][] ROLE_RULES = {
            // 自助修改密码：精确规则必须放在 /api/accounts/**（仅 ADMIN）之前，否则被通配拦截导致学生/教师无法改密
            {"/api/accounts/me/password", "STUDENT,TEACHER,ADMIN"},
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
            {"/api/weather", "STUDENT,TEACHER,ADMIN"},
            {"/api/ai/**", "STUDENT,ADMIN"},
            // 站内通知（精确规则在前，fail-closed）
            {"/api/notifications/unread-count", "STUDENT"},
            {"/api/notifications/read-all",     "STUDENT"},
            {"/api/notifications/sent",         "TEACHER,ADMIN"},
            {"/api/notifications/*/read",       "STUDENT"},
            {"/api/notifications/send",         "TEACHER,ADMIN"},
            {"/api/notifications/**",           "STUDENT"},
    };

    /**
     * 调用逻辑：Spring MVC 拦截器，每个受保护的请求在进入 Controller 之前由框架调用本方法，
     * 执行顺序为：放行 OPTIONS 预检 → 解析 Bearer Token → tokenVersion 吊销检查 → 写入 UserContext → 按路径+角色规则鉴权；
     * 全部通过才继续放行到 Controller，否则直接写 401/403 并中断请求链。
     * 为什么：采用 fail-closed 规则表——未在 ROLE_RULES 登记的新接口默认 403，宁可误伤也不放开，防止漏配角色造成越权；
     * 令牌版本吊销（改密/禁用/改角色后 token_version +1）可在不等待 Token 过期的情况下立即作废已泄露/已窃取的旧令牌。
     */
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
        // 令牌版本号比对（吊销检查）：改密/禁用/改角色后 token_version +1，
        // 旧 token 携带的版本号与数据库不一致即判定已吊销，立即返回 401 强制重新登录。
        if (!tokenVersionValid(claims)) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"登录状态已失效，请重新登录\",\"data\":null}");
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

    /**
     * 令牌版本号吊销检查：token 携带的版本号必须与数据库当前版本一致。
     * 账号被禁用（FROZEN）时同样按吊销处理——旧 token 不再放行，需重新登录。
     */
    private boolean tokenVersionValid(Claims claims) {
        Long userId = ((Number) claims.get("userId")).longValue();
        String roleType = (String) claims.get("roleType");
        int tokenVersion = jwtUtil.tokenVersion(claims);
        // 按角色路由查库：学生查 student 表，教师/管理员查 staff 表
        if ("STUDENT".equals(roleType)) {
            Student student = studentMapper.selectById(userId);
            if (student == null || !"ENABLED".equals(student.getStatus())) return false;
            return Integer.valueOf(tokenVersion).equals(student.getTokenVersion());
        } else {
            Staff staff = staffMapper.selectById(userId);
            if (staff == null || !"ENABLED".equals(staff.getStatus())) return false;
            return Integer.valueOf(tokenVersion).equals(staff.getTokenVersion());
        }
    }

    /**
     * 调用逻辑：请求处理链（Controller 执行完毕、响应返回后）由 Spring MVC 回调，业务成功或抛异常都会执行；
     * 此处统一清理 preHandle 中写入的 UserContext（ThreadLocal）。
     * 为什么：Servlet 容器线程池会复用线程，若不清理 ThreadLocal，下一个请求（甚至未登录请求）可能读到上一个用户的身份，造成串号/越权。
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 请求处理完毕必须清理 ThreadLocal：Servlet 容器线程池会复用线程，
        // 若不清理，下一请求（甚至未登录请求）可能读到上一个用户的信息，造成串号/越权。
        UserContext.clear();
    }
}
