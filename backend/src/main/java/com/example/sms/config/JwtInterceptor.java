package com.example.sms.config; // 声明当前类所属包：本项目所有配置类统一放在 com.example.sms.config 包下

// ---- import 区域：引入 JWT 鉴权所需的实体、Mapper、工具类与 Spring MVC 类 ----
import com.example.sms.entity.Staff; // 教职工实体类：用于查询教师/管理员账号信息
import com.example.sms.entity.Student; // 学生实体类：用于查询学生账号信息
import com.example.sms.mapper.StaffMapper; // 教职工 Mapper 接口：操作 staff 表
import com.example.sms.mapper.StudentMapper; // 学生 Mapper 接口：操作 student 表
import com.example.sms.util.JwtUtil; // JWT 工具类：负责 Token 的签发、解析与版本读取
import com.example.sms.util.UserContext; // 用户上下文工具类：基于 ThreadLocal 保存当前登录用户
import io.jsonwebtoken.Claims; // JWT 解析出的载荷对象（内含 userId、roleType 等自定义字段）
import org.springframework.beans.factory.annotation.Autowired; // @Autowired 注解：按类型自动注入依赖
import org.springframework.stereotype.Component; // @Component 注解：将该类注册为 Spring 容器管理的组件
import org.springframework.util.AntPathMatcher; // Ant 路径匹配器：支持 /**、* 等通配符的路径匹配
import org.springframework.web.servlet.HandlerInterceptor; // Spring MVC 拦截器接口

import javax.servlet.http.HttpServletRequest; // HTTP 请求类：读取请求头、请求方法、URI 等
import javax.servlet.http.HttpServletResponse; // HTTP 响应类：写入 401/403 状态码与错误信息

/**
 * JWT 登录鉴权拦截器
 */
@Component // 注册为 Spring Bean：由 WebConfig 注入并注册到 Spring MVC 拦截器链
public class JwtInterceptor implements HandlerInterceptor { // 实现 HandlerInterceptor：在请求进入 Controller 之前执行登录鉴权

    @Autowired // 注入 JWT 工具类
    private JwtUtil jwtUtil; // 负责 Token 解析与令牌版本读取

    @Autowired // 注入教职工 Mapper
    private StaffMapper staffMapper; // 用于查询教师/管理员账号的状态与 tokenVersion

    @Autowired // 注入学生 Mapper
    private StudentMapper studentMapper; // 用于查询学生账号的状态与 tokenVersion

    // Ant 通配路径匹配器：用于将请求 URI 与 ROLE_RULES 中的路径规则做模式比对
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher(); // 路径匹配器实例：支持 /** 等通配语法

    /**
     * 接口角色规则：按顺序匹配，先命中的生效；未匹配到任何规则时默认拒绝（fail-closed）。
     * 新增接口必须在 ROLE_RULES 中登记角色，否则一律 403，防止遗漏造成越权。
     * 同一路径下精确规则必须放在通配规则之前（如 /api/accounts/me 在 /api/accounts/** 前）。
     */
    private static final String[][] ROLE_RULES = { // 角色-路径权限规则表：每行 = [路径模式, 允许的角色列表（逗号分隔）]
            // 自助修改密码：精确规则必须放在 /api/accounts/**（仅 ADMIN）之前，否则被通配拦截导致学生/教师无法改密
            {"/api/accounts/me/password", "STUDENT,TEACHER,ADMIN"}, // 自助修改密码：学生/教师/管理员均可访问
            {"/api/accounts/me", "STUDENT,TEACHER,ADMIN"}, // 查看/修改个人信息：三种角色均可访问
            {"/api/accounts/**", "ADMIN"}, // 账号管理其余接口（通配）：仅管理员
            {"/api/courses/my", "TEACHER,ADMIN"}, // 查询"我的课程"：教师/管理员
            {"/api/courses/all", "ADMIN"}, // 查询全部课程列表：仅管理员
            {"/api/courses/**", "TEACHER,ADMIN"}, // 课程管理其余接口（通配）：教师/管理员
            {"/api/grades/my", "STUDENT"}, // 查询我的成绩：仅学生
            {"/api/grades/dashboard", "STUDENT"}, // 成绩看板：仅学生
            {"/api/grades/pending", "ADMIN"}, // 待审核成绩列表：仅管理员
            {"/api/grades/audits", "ADMIN"}, // 成绩审核记录列表：仅管理员
            {"/api/grades/audit", "ADMIN"}, // 执行成绩审核操作：仅管理员
            {"/api/grades/*/publish", "ADMIN"}, // 发布成绩：仅管理员
            {"/api/grades/course/**", "TEACHER,ADMIN"}, // 按课程查询成绩：教师/管理员
            {"/api/grades/entry", "TEACHER,ADMIN"}, // 录入成绩：教师/管理员
            {"/api/grades/*/submit", "TEACHER,ADMIN"}, // 提交成绩：教师/管理员
            {"/api/grades/my-audits", "TEACHER,ADMIN"}, // 查询我的审核记录：教师/管理员
            {"/api/grades/**", "TEACHER"}, // 成绩模块其余接口（兜底通配）：仅教师
            {"/api/enrollments/*/students/*", "ADMIN"}, // 查询选课学生名单：仅管理员
            {"/api/enrollments/monitor", "ADMIN"}, // 选课监控：仅管理员
            {"/api/enrollments/**", "STUDENT"}, // 选课模块其余接口（兜底通配）：仅学生
            {"/api/stats/admin", "ADMIN"}, // 管理员统计看板：仅管理员
            {"/api/stats/teacher", "TEACHER"}, // 教师统计看板：仅教师
            {"/api/weather", "STUDENT,TEACHER,ADMIN"}, // 天气查询：所有已登录角色
            {"/api/ai/**", "STUDENT,ADMIN"}, // AI 助手接口：学生/管理员
            // 站内通知（精确规则在前，fail-closed）
            {"/api/notifications/unread-count", "STUDENT"}, // 查询未读通知数：仅学生
            {"/api/notifications/read-all",     "STUDENT"}, // 全部标记已读：仅学生
            {"/api/notifications/sent",         "TEACHER,ADMIN"}, // 查询已发送通知：教师/管理员
            {"/api/notifications/*/read",       "STUDENT"}, // 单条通知标记已读：仅学生
            {"/api/notifications/send",         "TEACHER,ADMIN"}, // 发送通知：教师/管理员
            {"/api/notifications/**",           "STUDENT"}, // 通知模块其余接口（兜底通配）：仅学生
    };

    /**
     * 调用逻辑：Spring MVC 拦截器，每个受保护的请求在进入 Controller 之前由框架调用本方法，
     * 执行顺序为：放行 OPTIONS 预检 → 解析 Bearer Token → tokenVersion 吊销检查 → 写入 UserContext → 按路径+角色规则鉴权；
     * 全部通过才继续放行到 Controller，否则直接写 401/403 并中断请求链。
     * 为什么：采用 fail-closed 规则表——未在 ROLE_RULES 登记的新接口默认 403，宁可误伤也不放开，防止漏配角色造成越权；
     * 令牌版本吊销（改密/禁用/改角色后 token_version +1）可在不等待 Token 过期的情况下立即作废已泄露/已窃取的旧令牌。
     */
    @Override // 重写拦截器的 preHandle 方法
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception { // 返回 true 放行、false 拦截并中断请求
        // 放行预检请求：浏览器跨域时会先发 OPTIONS 预检（无 Authorization 头），
        // 若在此被拦截会返回 401/403，导致前端真正的业务请求无法发起，因此必须直接放行。
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) { // 判断请求方法是否为 OPTIONS（跨域预检请求）
            return true; // 预检请求直接放行
        }
        // 从请求头取 Bearer Token：只有形如 "Bearer <token>" 才尝试解析，
        // 缺少前缀或未携带时解析结果为 null（parseToken 内部对任何异常都返回 null，不向外抛）。
        String token = request.getHeader("Authorization"); // 读取 Authorization 请求头（未携带时为 null）
        Claims claims = (token != null && token.startsWith("Bearer ")) // 只有"头不为空且以 Bearer 开头"才尝试解析
                ? jwtUtil.parseToken(token.substring(7)) // 去掉 "Bearer " 前缀（前 7 个字符）后解析 JWT，得到载荷
                : null; // 否则载荷为 null（视为未登录）
        if (claims == null) { // 解析不到合法载荷（未登录/Token 失效/被篡改）
            // 未登录/Token 失效/被篡改：统一按未认证处理，返回真实 HTTP 401，
            // 由前端引导跳转登录页；fail-closed——任何解析不到合法身份的情况都不得放行。
            response.setStatus(401); // 设置 HTTP 状态码 401（未认证）
            response.setContentType("application/json;charset=UTF-8"); // 设置响应内容类型为 UTF-8 编码的 JSON
            response.getWriter().write("{\"code\":401,\"message\":\"未登录或登录已过期\",\"data\":null}"); // 输出统一格式的错误响应 JSON
            return false; // 拦截请求，不再继续
        }
        // 令牌版本号比对（吊销检查）：改密/禁用/改角色后 token_version +1，
        // 旧 token 携带的版本号与数据库不一致即判定已吊销，立即返回 401 强制重新登录。
        if (!tokenVersionValid(claims)) { // 令牌版本号与数据库不一致（已被吊销）
            response.setStatus(401); // 设置 HTTP 状态码 401
            response.setContentType("application/json;charset=UTF-8"); // 设置响应内容类型
            response.getWriter().write("{\"code\":401,\"message\":\"登录状态已失效，请重新登录\",\"data\":null}"); // 输出登录失效提示 JSON
            return false; // 拦截请求
        }
        // 将当前登录用户写入 ThreadLocal（线程私有）：同一请求线程内的 Controller/Service 可直接
        // 通过 UserContext 取到 userId/role 等，避免层层透传参数；注意必须在 afterCompletion 中清理。
        UserContext.CurrentUser user = new UserContext.CurrentUser(); // 创建当前登录用户对象
        user.setUserId(((Number) claims.get("userId")).longValue()); // 从载荷中取出 userId 并转成 long 存入用户对象
        user.setUserNo((String) claims.get("userNo")); // 从载荷中取出工号/学号
        user.setRealName((String) claims.get("realName")); // 从载荷中取出真实姓名
        user.setRoleType((String) claims.get("roleType")); // 从载荷中取出角色类型（STUDENT/TEACHER/ADMIN）
        UserContext.set(user); // 把用户信息写入 ThreadLocal：本线程后续代码可随时读取

        // 角色-路径校验（读接口此前普遍缺失，此处统一兜底）
        if (!checkRole(request.getRequestURI(), user.getRoleType())) { // 当前路径 + 角色不在允许范围内
            // 权限失败返回真实 HTTP 403 + code=403，便于网关/WAF 按状态码统计拦截
            response.setStatus(403); // 设置 HTTP 状态码 403（无权限）
            response.setContentType("application/json;charset=UTF-8"); // 设置响应内容类型
            response.getWriter().write("{\"code\":403,\"message\":\"无权限访问该接口\",\"data\":null}"); // 输出无权限提示 JSON
            return false; // 拦截请求
        }
        return true; // 所有校验通过：放行到 Controller
    }

    /** 按角色规则校验路径访问权，未匹配到任何规则时默认拒绝（fail-closed） */
    private boolean checkRole(String uri, String role) { // 私有方法：根据规则表校验路径与角色的匹配关系
        // 顺序遍历规则表：规则按"精确优先、通配靠后"排列，
        // 一旦路径命中某条规则，就只在该规则允许的角色内判断——命中即终止，避免继续被后面的通配规则覆盖。
        for (String[] rule : ROLE_RULES) { // 遍历每条规则
            if (PATH_MATCHER.match(rule[0], uri)) { // 当前 URI 与规则的路径模式匹配成功
                for (String allowed : rule[1].split(",")) { // 按逗号拆分该规则允许的角色列表并遍历
                    if (allowed.equals(role)) { // 当前角色在允许列表内
                        return true; // 校验通过，放行
                    }
                }
                // 命中规则但角色不在允许列表：直接拒绝，不再向下匹配通配规则（防止越权）
                return false; // 命中规则但无权限：拒绝
            }
        }
        // 循环结束仍无任何规则命中：默认拒绝（fail-closed）。
        // 新增接口若忘记在 ROLE_RULES 登记，访问一律 403，宁可误伤也不放开（R-5）。
        return false; // 未匹配到任何规则：默认拒绝
    }

    /**
     * 令牌版本号吊销检查：token 携带的版本号必须与数据库当前版本一致。
     * 账号被禁用（FROZEN）时同样按吊销处理——旧 token 不再放行，需重新登录。
     */
    private boolean tokenVersionValid(Claims claims) { // 私有方法：校验令牌版本号是否与数据库一致
        Long userId = ((Number) claims.get("userId")).longValue(); // 从载荷中取出用户 ID
        String roleType = (String) claims.get("roleType"); // 从载荷中取出角色类型
        int tokenVersion = jwtUtil.tokenVersion(claims); // 读取 token 载荷中携带的版本号
        // 按角色路由查库：学生查 student 表，教师/管理员查 staff 表
        if ("STUDENT".equals(roleType)) { // 学生角色：走学生表校验
            Student student = studentMapper.selectById(userId); // 按用户 ID 查询学生记录
            if (student == null || !"ENABLED".equals(student.getStatus())) return false; // 账号不存在或非启用状态：判定为已吊销
            return Integer.valueOf(tokenVersion).equals(student.getTokenVersion()); // 比对 token 版本号与库中版本号是否一致
        } else { // 教师/管理员角色：走职工表校验
            Staff staff = staffMapper.selectById(userId); // 按用户 ID 查询职工记录
            if (staff == null || !"ENABLED".equals(staff.getStatus())) return false; // 账号不存在或非启用状态：判定为已吊销
            return Integer.valueOf(tokenVersion).equals(staff.getTokenVersion()); // 比对 token 版本号与库中版本号是否一致
        }
    }

    /**
     * 调用逻辑：请求处理链（Controller 执行完毕、响应返回后）由 Spring MVC 回调，业务成功或抛异常都会执行；
     * 此处统一清理 preHandle 中写入的 UserContext（ThreadLocal）。
     * 为什么：Servlet 容器线程池会复用线程，若不清理 ThreadLocal，下一个请求（甚至未登录请求）可能读到上一个用户的身份，造成串号/越权。
     */
    @Override // 重写 afterCompletion 方法
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) { // 请求处理完成后回调（无论成功或异常都会执行）
        // 请求处理完毕必须清理 ThreadLocal：Servlet 容器线程池会复用线程，
        // 若不清理，下一请求（甚至未登录请求）可能读到上一个用户的信息，造成串号/越权。
        UserContext.clear(); // 清除 ThreadLocal 中的当前用户信息
    }
}
