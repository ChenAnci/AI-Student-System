package com.example.sms;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 学生管理系统（Student Management System）启动类。
 * @SpringBootApplication 开启 Spring Boot 自动配置与组件扫描；
 * @MapperScan 扫描 MyBatis Mapper 接口所在包，自动注册为 Bean。
 */
@SpringBootApplication
@MapperScan("com.example.sms.mapper")
public class SmsApplication {

    /** 应用入口：启动内嵌 Web 容器并加载整个 Spring 上下文 */
    public static void main(String[] args) {
        SpringApplication.run(SmsApplication.class, args);
    }
}
