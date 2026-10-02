package com.example.sms.config; // 声明当前类所属包：本项目所有配置类统一放在 com.example.sms.config 包下

// ---- import 区域：引入 MyBatis-Plus 分页插件相关的类 ----
import com.baomidou.mybatisplus.annotation.DbType; // 数据库类型枚举：用于指定分页 SQL 方言（此处为 MySQL）
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor; // MyBatis-Plus 核心拦截器：可挂载多个内部插件
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor; // 分页内部拦截器：自动为分页查询生成 LIMIT 语句
import org.springframework.context.annotation.Bean; // @Bean 注解：将方法返回值注册为 Spring 容器管理的 Bean
import org.springframework.context.annotation.Configuration; // @Configuration 注解：标记该类为 Spring 配置类

/**
 * MyBatis-Plus 配置（分页插件）
 */
@Configuration // 标记为 Spring 配置类：启动时自动加载生效
public class MybatisPlusConfig { // MyBatis-Plus 配置类

    /**
     * 注册 MyBatis-Plus 拦截器：启用分页插件（MySQL 方言）。
     * setMaxLimit(500) 限制单次查询最大返回条数，防止超大分页参数导致全表扫描拖垮数据库。
     */
    @Bean // 注册拦截器 Bean：MyBatis-Plus 会自动装配并使用它
    public MybatisPlusInterceptor mybatisPlusInterceptor() { // 构建并返回 MyBatis-Plus 拦截器
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor(); // 创建拦截器实例（内部可挂多个插件）
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL); // 创建分页插件并指定 MySQL 方言（生成对应分页 SQL）
        pagination.setMaxLimit(500L); // 限制单次查询最大返回 500 条：防止超大分页参数引发全表扫描
        interceptor.addInnerInterceptor(pagination); // 把分页插件挂载到拦截器上使其生效
        return interceptor; // 返回配置好的拦截器
    }
}
