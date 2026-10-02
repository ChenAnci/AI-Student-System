package com.example.sms.config; // 声明当前类所属包：本项目所有配置类统一放在 com.example.sms.config 包下

// ---- import 区域：引入接口文档（Swagger/Knife4j）配置所需的类 ----
import com.github.xiaoymin.knife4j.spring.annotations.EnableKnife4j; // Knife4j 增强开关注解：启用更友好的接口文档 UI
import org.springframework.context.annotation.Bean; // @Bean 注解：将方法返回值注册为 Spring 容器管理的 Bean
import org.springframework.context.annotation.Configuration; // @Configuration 注解：标记该类为 Spring 配置类
import springfox.documentation.builders.ApiInfoBuilder; // 文档元信息构建器（链式构造标题、描述、版本）
import springfox.documentation.builders.PathSelectors; // 路径选择器：按路径筛选要生成文档的接口
import springfox.documentation.builders.RequestHandlerSelectors; // 处理器选择器：按包名筛选要生成文档的接口
import springfox.documentation.service.ApiInfo; // 文档元信息类（标题、描述、版本等）
import springfox.documentation.service.ApiKey; // API Key 鉴权信息类：声明请求头形式的鉴权方式
import springfox.documentation.service.AuthorizationScope; // 鉴权范围类：描述鉴权的作用域
import springfox.documentation.service.SecurityReference; // 鉴权引用类：将某个鉴权方案与范围绑定
import springfox.documentation.spi.DocumentationType; // 文档类型枚举：此处使用 SWAGGER_2
import springfox.documentation.spi.service.contexts.SecurityContext; // 鉴权上下文：定义哪些路径需要携带鉴权
import springfox.documentation.spring.web.plugins.Docket; // Docket：Swagger 文档的构建入口对象

import java.util.Collections; // 集合工具类：生成单元素集合（API 需要 List 入参时使用）
import java.util.List; // List 接口：集合类型引用

/**
 * Knife4j 接口文档配置
 * 访问地址: http://localhost:8080/doc.html
 */
@Configuration // 标记为 Spring 配置类：启动时自动加载生效
@EnableKnife4j // 启用 Knife4j 增强功能（提供更友好的文档页面）
public class Knife4jConfig { // 接口文档配置类

    /**
     * 构建接口文档 Docket：扫描 controller 包生成 Swagger 文档，
     * 并挂接 Authorization 头鉴权，使文档页可直接填写 JWT 调试受保护接口。
     */
    @Bean // 注册 Docket Bean：Spring Boot 启动时据此生成并发布接口文档
    public Docket docket() { // 构建并返回 Docket 对象
        return new Docket(DocumentationType.SWAGGER_2) // 使用 Swagger 2 规范生成文档
                .apiInfo(apiInfo()) // 设置文档元信息（标题/描述/版本）
                .select() // 进入接口选择阶段
                // 只扫描 controller 包下的接口
                .apis(RequestHandlerSelectors.basePackage("com.example.sms.controller")) // 指定扫描的包：只为本包内的接口生成文档
                .paths(PathSelectors.any()) // 匹配包内所有路径的接口
                .build() // 完成接口选择并构建
                // 声明 header 形式的 API Key 鉴权方式
                .securitySchemes(Collections.singletonList(apiKey())) // 注册名为 Authorization 的请求头鉴权方案
                // 对除登录外的接口路径应用该鉴权上下文
                .securityContexts(Collections.singletonList(securityContext())); // 指定哪些路径的接口要求携带鉴权（登录接口除外）
    }

    /** 文档元信息：标题、描述、版本 */
    private ApiInfo apiInfo() { // 私有方法：构建文档元信息
        return new ApiInfoBuilder() // 使用构建器模式构造 ApiInfo
                .title("学生信息管理系统 API") // 设置文档标题
                .description("学生信息管理系统后端接口文档 - Vue3 + Spring Boot + MyBatis-Plus") // 设置文档描述
                .version("1.0.0") // 设置文档版本号
                .build(); // 构建完成并返回
    }

    /** 定义名为 Authorization 的 header API Key，供文档页输入 JWT 后调试接口 */
    private ApiKey apiKey() { // 私有方法：定义鉴权方式
        return new ApiKey("Authorization", "Authorization", "header"); // 构造 ApiKey：显示名、请求头名、位置（header）
    }

    /**
     * 鉴权上下文：对除 /api/auth/** 外的所有路径要求携带 Authorization 头，
     * 使文档页调试带鉴权接口时能附带 JWT。
     */
    private SecurityContext securityContext() { // 私有方法：构建鉴权上下文
        return SecurityContext.builder() // 使用构建器模式构造 SecurityContext
                .securityReferences(securityReferences()) // 绑定鉴权引用（指向 Authorization 头）
                // 正则排除 /api/auth 开头的路径：登录等接口无需鉴权
                .forPaths(PathSelectors.regex("^(?!/(api/auth)).*")) // 使用负向前瞻正则：除 /api/auth 开头的路径外均要求鉴权
                .build(); // 构建完成并返回
    }

    /** 将 Authorization 与全局访问范围绑定，作为鉴权上下文引用的安全引用 */
    private List<SecurityReference> securityReferences() { // 私有方法：构建安全引用列表
        AuthorizationScope scope = new AuthorizationScope("global", "accessEverything"); // 定义全局访问范围（名称 + 描述）
        return Collections.singletonList(new SecurityReference("Authorization", new AuthorizationScope[]{scope})); // 将 Authorization 与全局范围绑定成单元素列表返回
    }
}
