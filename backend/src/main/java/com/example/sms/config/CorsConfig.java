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

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
