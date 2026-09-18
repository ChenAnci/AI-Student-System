package com.example.sms.config;

import com.github.xiaoymin.knife4j.spring.annotations.EnableKnife4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.ApiKey;
import springfox.documentation.service.AuthorizationScope;
import springfox.documentation.service.SecurityReference;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spi.service.contexts.SecurityContext;
import springfox.documentation.spring.web.plugins.Docket;

import java.util.Collections;
import java.util.List;

/**
 * Knife4j 接口文档配置
 * 访问地址: http://localhost:8080/doc.html
 */
@Configuration
@EnableKnife4j
public class Knife4jConfig {

    /**
     * 构建接口文档 Docket：扫描 controller 包生成 Swagger 文档，
     * 并挂接 Authorization 头鉴权，使文档页可直接填写 JWT 调试受保护接口。
     */
    @Bean
    public Docket docket() {
        return new Docket(DocumentationType.SWAGGER_2)
                .apiInfo(apiInfo())
                .select()
                // 只扫描 controller 包下的接口
                .apis(RequestHandlerSelectors.basePackage("com.example.sms.controller"))
                .paths(PathSelectors.any())
                .build()
                // 声明 header 形式的 API Key 鉴权方式
                .securitySchemes(Collections.singletonList(apiKey()))
                // 对除登录外的接口路径应用该鉴权上下文
                .securityContexts(Collections.singletonList(securityContext()));
    }

    /** 文档元信息：标题、描述、版本 */
    private ApiInfo apiInfo() {
        return new ApiInfoBuilder()
                .title("学生信息管理系统 API")
                .description("学生信息管理系统后端接口文档 - Vue3 + Spring Boot + MyBatis-Plus")
                .version("1.0.0")
                .build();
    }

    /** 定义名为 Authorization 的 header API Key，供文档页输入 JWT 后调试接口 */
    private ApiKey apiKey() {
        return new ApiKey("Authorization", "Authorization", "header");
    }

    /**
     * 鉴权上下文：对除 /api/auth/** 外的所有路径要求携带 Authorization 头，
     * 使文档页调试带鉴权接口时能附带 JWT。
     */
    private SecurityContext securityContext() {
        return SecurityContext.builder()
                .securityReferences(securityReferences())
                // 正则排除 /api/auth 开头的路径：登录等接口无需鉴权
                .forPaths(PathSelectors.regex("^(?!/(api/auth)).*"))
                .build();
    }

    /** 将 Authorization 与全局访问范围绑定，作为鉴权上下文引用的安全引用 */
    private List<SecurityReference> securityReferences() {
        AuthorizationScope scope = new AuthorizationScope("global", "accessEverything");
        return Collections.singletonList(new SecurityReference("Authorization", new AuthorizationScope[]{scope}));
    }
}
