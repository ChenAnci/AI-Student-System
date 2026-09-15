package com.example.sms.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * AI 服务转发配置（连接 5s / 读超时 60s；LLM 单次生成超过 60s 视为异常，避免长占 Tomcat 线程）
 */
@Configuration
public class AiConfig {

    @Bean
    public RestTemplate aiRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(60_000);
        return new RestTemplate(factory);
    }
}
