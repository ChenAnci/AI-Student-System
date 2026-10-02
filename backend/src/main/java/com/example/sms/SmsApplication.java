package com.example.sms; // 声明该类所在包：项目根包，Spring Boot 启动类所在位置

// ---- import 区域：引入 MyBatis Mapper 扫描注解与 Spring Boot 启动相关类 ----
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 学生管理系统（Student Management System）启动类。
 * @SpringBootApplication 开启 Spring Boot 自动配置与组件扫描；
 * @MapperScan 扫描 MyBatis Mapper 接口所在包，自动注册为 Bean。
 */
@SpringBootApplication // 组合注解：开启自动配置、组件扫描与 Spring Boot 配置类（应用总入口）
@MapperScan("com.example.sms.mapper") // 扫描 mapper 包下的 MyBatis Mapper 接口，自动生成实现代理并注册为 Bean
public class SmsApplication { // 应用启动类：作为 Spring Boot 可执行 Jar 的入口

    /** 应用入口：启动内嵌 Web 容器并加载整个 Spring 上下文 */
    public static void main(String[] args) { // 标准 main 方法：JVM 启动后从此进入
        SpringApplication.run(SmsApplication.class, args); // 启动 Spring Boot 应用：加载配置、初始化上下文并启动内嵌 Tomcat
    }
}
