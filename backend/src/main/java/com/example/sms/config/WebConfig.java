package com.example.sms.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置：注册 JWT 拦截器
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    // 注入 JWT 鉴权拦截器：负责登录校验与角色-路径权限（fail-closed）
    @Autowired
    private JwtInterceptor jwtInterceptor;

    // 注入登录接口 IP 限流拦截器：防止暴力刷登录接口
    @Autowired
    private RateLimitInterceptor rateLimitInterceptor;

    /** 与 application.yml 的 knife4j.enable 保持一致：文档路径仅在开发调试时放行 */
    @Value("${knife4j.enable:false}")
    private boolean knife4jEnabled;

    /**
     * 调用逻辑：Spring 启动阶段由框架回调本方法，一次性注册 JWT 鉴权拦截器与登录限流拦截器及其排除路径，
     * 注册完成后对后续所有请求生效（每次请求都会经过已注册拦截器链）。
     * 为什么：采用"默认拦截、显式放行"策略——登录、OAuth 回调、favicon 等公开路径放行，
     * 其余业务接口全部走鉴权（fail-closed，漏配即 403）；文档路径仅在 knife4j 开启时放行，避免生产环境接口文档裸奔。
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // JWT 拦截器覆盖全部 /api/**（业务接口）+ 文档路径。
        // 注意：/api/** 已包含 /api/auth/login 与 /api/oauth/**，因此必须显式排除，
        // 否则登录与 OAuth 回调（本就不能携带 Token）会被误判为未登录而 401。
        InterceptorRegistration reg = registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/api/**", "/doc.html", "/v2/api-docs", "/v3/api-docs",
                        "/swagger-resources/**", "/webjars/**")
                .excludePathPatterns(
                        "/api/auth/login",
                        "/api/oauth/**",
                        "/favicon.ico"
                );
        // Knife4j 开启时才放行文档相关路径，否则接口文档页面/定义同样要求认证（fail-closed）
        if (knife4jEnabled) {
            reg.excludePathPatterns(
                    "/doc.html",
                    "/webjars/**",
                    "/swagger-resources/**",
                    "/v2/api-docs",
                    "/v3/api-docs"
            );
        }
        // 登录接口 IP 维度限流（Redis，多实例共享计数）：
        // 只注册到 /api/auth/login 这一个路径，限流拦截器内部对非登录 URI 也会直接放行，双保险。
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/auth/login");
    }
}
