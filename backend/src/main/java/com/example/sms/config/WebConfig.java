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

    @Autowired
    private JwtInterceptor jwtInterceptor;

    /** 与 application.yml 的 knife4j.enable 保持一致：文档路径仅在开发调试时放行 */
    @Value("${knife4j.enable:false}")
    private boolean knife4jEnabled;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        InterceptorRegistration reg = registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/api/**", "/doc.html", "/v2/api-docs", "/v3/api-docs",
                        "/swagger-resources/**", "/webjars/**")
                .excludePathPatterns(
                        "/api/auth/login",
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
    }
}
