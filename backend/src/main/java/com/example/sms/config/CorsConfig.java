package com.example.sms.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 跨域配置（仅允许配置的精确源，禁止任意源回显）
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    /**
     * 允许的跨域来源（逗号分隔），可通过环境变量 CORS_ALLOWED_ORIGINS 覆盖；
     * 默认仅开发环境前端地址，生产必须显式配置正式域名。
     */
    @Value("${cors.allowed-origins:http://localhost:5173}")
    private String[] allowedOrigins;

    /**
     * 注册全局跨域映射：仅允许配置的精确来源（禁止 "*" 通配源），
     * 从而支持 allowCredentials(true) 携带 Cookie 的跨域请求（通配源 + 凭据会被浏览器直接拒绝）。
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                // 精确来源白名单（默认开发前端地址，生产用环境变量覆盖为正式域名）
                .allowedOrigins(allowedOrigins)
                // 允许的 HTTP 方法；OPTIONS 用于跨域预检
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                // 允许任意请求头（含 Authorization：跨域调用需携带 JWT）
                .allowedHeaders("*")
                // 允许携带 Cookie 凭据（与精确源搭配使用）
                .allowCredentials(true)
                // 预检结果缓存 3600 秒，减少频繁的 OPTIONS 预检请求
                .maxAge(3600);
    }
}
