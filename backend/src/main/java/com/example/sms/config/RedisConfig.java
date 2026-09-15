package com.example.sms.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

/**
 * Redis 配置：
 * 1. 启用 Spring Cache（@Cacheable 等）并接入 Redis，默认 TTL 30s（业务缓存短过期，保证数据新鲜度）
 * 2. 值序列化使用 JSON（GenericJackson2JsonRedisSerializer），跨语言可读
 * <p>
 * 连接凭据由 application.yml（环境变量 REDIS_HOST/REDIS_PORT/REDIS_PASSWORD）注入；
 * 登录锁定 / OAuth state / 限流等共享状态直接使用 StringRedisTemplate（Spring Data Redis 自动装配）。
 */
@Configuration
@EnableCaching
public class RedisConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(30))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()));
        return RedisCacheManager.builder(factory)
                .cacheDefaults(config)
                .build();
    }
}
