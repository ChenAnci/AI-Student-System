package com.example.sms.config; // 声明当前类所属包：本项目所有配置类统一放在 com.example.sms.config 包下

// ---- import 区域：引入 AI 服务转发所需的 Spring 配置注解与 HTTP 客户端类 ----
import org.springframework.context.annotation.Bean; // @Bean 注解：将方法返回值注册为 Spring 容器管理的 Bean
import org.springframework.context.annotation.Configuration; // @Configuration 注解：标记该类为 Spring 配置类
import org.springframework.http.client.SimpleClientHttpRequestFactory; // 简单 HTTP 请求工厂：可单独设置连接/读取超时时间
import org.springframework.web.client.RestTemplate; // RestTemplate：Spring 提供的同步 HTTP 客户端工具

/**
 * AI 服务转发配置（连接 5s / 读超时 60s；LLM 单次生成超过 60s 视为异常，避免长占 Tomcat 线程）
 */
@Configuration // 标记为 Spring 配置类：容器启动时会解析其中的 @Bean 方法并注册 Bean
public class AiConfig { // AI 服务转发配置类

    /**
     * 专用 RestTemplate Bean：供 AI 服务接口转发调用。
     * 连接 5s 超时快速失败；读超时 60s 兜底 LLM 生成耗时，避免 AI 慢响应长期占用 Tomcat 线程。
     */
    @Bean // 将该方法返回值注册为名为 aiRestTemplate 的 Bean，供其它组件按需注入
    public RestTemplate aiRestTemplate() { // 构造并返回一个配置好超时参数的专用 RestTemplate
        // 专用 RestTemplate：连接 5s 超时快速失败；读超时 60s 兜底 LLM 生成耗时，
        // 避免 AI 服务长时间无响应时拖死 Tomcat 线程池
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory(); // 创建请求工厂实例（用于配置超时参数）
        factory.setConnectTimeout(5000); // 设置连接超时 5 秒：AI 服务不可达时快速失败，不长时间占用线程
        factory.setReadTimeout(60_000); // 设置读取超时 60 秒：兜底 LLM 单次生成耗时，防止无限等待
        return new RestTemplate(factory); // 用配置好超时的工厂创建 RestTemplate 并返回
    }
}
