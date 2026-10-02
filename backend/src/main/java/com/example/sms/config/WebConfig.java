package com.example.sms.config; // 声明当前类所属包：本项目所有配置类统一放在 com.example.sms.config 包下

// ---- import 区域：引入拦截器注册所需的 Spring 与 Spring MVC 类 ----
import org.springframework.beans.factory.annotation.Autowired; // @Autowired 注解：按类型自动注入依赖
import org.springframework.beans.factory.annotation.Value; // @Value 注解：将配置项值注入到字段
import org.springframework.context.annotation.Configuration; // @Configuration 注解：标记该类为 Spring 配置类
import org.springframework.web.servlet.config.annotation.InterceptorRegistration; // 单个拦截器的注册对象：可链式配置拦截/排除路径
import org.springframework.web.servlet.config.annotation.InterceptorRegistry; // 拦截器注册表：注册所有拦截器
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer; // Spring MVC 全局配置接口

/**
 * Web 配置：注册 JWT 拦截器
 */
@Configuration // 标记为 Spring 配置类：启动时自动加载生效
public class WebConfig implements WebMvcConfigurer { // 实现 WebMvcConfigurer：参与 Spring MVC 的全局配置（注册拦截器）

    // 注入 JWT 鉴权拦截器：负责登录校验与角色-路径权限（fail-closed）
    @Autowired // 注入 JWT 鉴权拦截器
    private JwtInterceptor jwtInterceptor; // JWT 登录鉴权拦截器实例

    // 注入登录接口 IP 限流拦截器：防止暴力刷登录接口
    @Autowired // 注入登录限流拦截器
    private RateLimitInterceptor rateLimitInterceptor; // 登录接口 IP 维度限流拦截器实例

    /** 与 application.yml 的 knife4j.enable 保持一致：文档路径仅在开发调试时放行 */
    @Value("${knife4j.enable:false}") // 读取配置项 knife4j.enable（缺省 false）：是否启用接口文档
    private boolean knife4jEnabled; // 文档开关：为 true 时才放行文档相关路径，避免生产环境文档裸奔

    /**
     * 调用逻辑：Spring 启动阶段由框架回调本方法，一次性注册 JWT 鉴权拦截器与登录限流拦截器及其排除路径，
     * 注册完成后对后续所有请求生效（每次请求都会经过已注册拦截器链）。
     * 为什么：采用"默认拦截、显式放行"策略——登录、OAuth 回调、favicon 等公开路径放行，
     * 其余业务接口全部走鉴权（fail-closed，漏配即 403）；文档路径仅在 knife4j 开启时放行，避免生产环境接口文档裸奔。
     */
    @Override // 重写 WebMvcConfigurer 的 addInterceptors 方法
    public void addInterceptors(InterceptorRegistry registry) { // 注册自定义拦截器到 Spring MVC
        // JWT 拦截器覆盖全部 /api/**（业务接口）+ 文档路径。
        // 注意：/api/** 已包含 /api/auth/login 与 /api/oauth/**，因此必须显式排除，
        // 否则登录与 OAuth 回调（本就不能携带 Token）会被误判为未登录而 401。
        InterceptorRegistration reg = registry.addInterceptor(jwtInterceptor) // 注册 JWT 鉴权拦截器
                .addPathPatterns("/api/**", "/doc.html", "/v2/api-docs", "/v3/api-docs", // 指定拦截路径：业务接口 + 文档相关路径
                        "/swagger-resources/**", "/webjars/**") // 继续指定拦截：Swagger 资源与 webjars 静态资源
                .excludePathPatterns( // 指定排除路径（这些路径无需登录即可访问）
                        "/api/auth/login", // 登录接口：本身就是获取 Token 的入口，必须放行
                        "/api/oauth/**", // OAuth 第三方回调：浏览器跳转无法携带 Token，必须放行
                        "/favicon.ico" // 站点图标：纯静态资源，放行
                );
        // Knife4j 开启时才放行文档相关路径，否则接口文档页面/定义同样要求认证（fail-closed）
        if (knife4jEnabled) { // 文档开关为开启状态（开发调试环境）
            reg.excludePathPatterns( // 追加排除文档相关路径（仅开发时免登录）
                    "/doc.html", // 文档首页
                    "/webjars/**", // 文档依赖的前端静态资源
                    "/swagger-resources/**", // Swagger 资源配置接口
                    "/v2/api-docs", // Swagger 2 接口定义
                    "/v3/api-docs" // Swagger 3 接口定义
            );
        }
        // 登录接口 IP 维度限流（Redis，多实例共享计数）：
        // 只注册到 /api/auth/login 这一个路径，限流拦截器内部对非登录 URI 也会直接放行，双保险。
        registry.addInterceptor(rateLimitInterceptor) // 注册登录限流拦截器
                .addPathPatterns("/api/auth/login"); // 仅拦截登录接口这一个路径
    }
}
