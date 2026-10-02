package com.example.sms.config; // 声明当前类所属包：本项目所有配置类统一放在 com.example.sms.config 包下

// ---- import 区域：引入跨域配置所需的 Spring 注解与 Spring MVC 类 ----
import org.springframework.beans.factory.annotation.Value; // @Value 注解：把配置项的值注入到字段
import org.springframework.context.annotation.Configuration; // @Configuration 注解：标记该类为 Spring 配置类
import org.springframework.web.servlet.config.annotation.CorsRegistry; // 跨域注册器：用于注册全局跨域映射规则
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer; // Spring MVC 全局配置接口：可自定义跨域等行为

/**
 * 跨域配置（仅允许配置的精确源，禁止任意源回显）
 */
@Configuration // 标记为 Spring 配置类：启动时自动加载并生效
public class CorsConfig implements WebMvcConfigurer { // 实现 WebMvcConfigurer：参与 Spring MVC 的全局配置

    /**
     * 允许的跨域来源（逗号分隔），可通过环境变量 CORS_ALLOWED_ORIGINS 覆盖；
     * 默认仅开发环境前端地址，生产必须显式配置正式域名。
     */
    @Value("${cors.allowed-origins:http://localhost:5173}") // 读取配置项 cors.allowed-origins，缺省值为开发环境前端地址
    private String[] allowedOrigins; // 允许的跨域来源数组（精确源白名单）

    /**
     * 注册全局跨域映射：仅允许配置的精确来源（禁止 "*" 通配源），
     * 从而支持 allowCredentials(true) 携带 Cookie 的跨域请求（通配源 + 凭据会被浏览器直接拒绝）。
     */
    @Override // 重写 WebMvcConfigurer 的跨域配置方法
    public void addCorsMappings(CorsRegistry registry) { // 注册全局跨域规则
        registry.addMapping("/**") // 对后端所有路径启用跨域支持
                // 精确来源白名单（默认开发前端地址，生产用环境变量覆盖为正式域名）
                .allowedOrigins(allowedOrigins) // 只允许白名单中的精确源访问（不使用 "*" 通配，保证安全）
                // 允许的 HTTP 方法；OPTIONS 用于跨域预检
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // 允许的请求方法集合（OPTIONS 供预检使用）
                // 允许任意请求头（含 Authorization：跨域调用需携带 JWT）
                .allowedHeaders("*") // 允许携带任意请求头（如 Authorization 等）
                // 允许携带 Cookie 凭据（与精确源搭配使用）
                .allowCredentials(true) // 允许携带 Cookie/凭据：必须与精确源配合，通配源 + 凭据会被浏览器拒绝
                // 预检结果缓存 3600 秒，减少频繁的 OPTIONS 预检请求
                .maxAge(3600); // 预检请求结果缓存 1 小时，减少 OPTIONS 预检次数、提升性能
    }
}
