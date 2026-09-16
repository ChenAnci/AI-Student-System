package com.example.sms.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

/**
 * Redis 配置：
 * 1. 启用 Spring Cache（@Cacheable 等）并接入 Redis，默认 TTL 30s（业务缓存短过期，保证数据新鲜度）
 * 2. 值序列化使用 JSON（GenericJackson2JsonRedisSerializer），跨语言可读；
 *    多态类型信息通过 BasicPolymorphicTypeValidator **白名单**限制在项目包与 JDK 常用类型内，
 *    防止 Redis 被投毒时反序列化到任意 class（安全加固）。
 * 3. 提供 INCR+EXPIRE 原子 Lua 脚本（限流/失败计数共用），避免非原子窗口下 key 无 TTL 残留。
 * <p>
 * 连接凭据由 application.yml（环境变量 REDIS_HOST/REDIS_PORT/REDIS_PASSWORD）注入；
 * 登录锁定 / OAuth state / 限流等共享状态直接使用 StringRedisTemplate（Spring Data Redis 自动装配）。
 */
@Configuration
@EnableCaching
public class RedisConfig {

    /**
     * 计数自增 + 首次设置过期时间的原子脚本（INCR 返回 1 时 EXPIRE）。
     * 相比 INCR+EXPIRE 两条命令，避免进程在两者之间崩溃导致 key 无 TTL 永久残留（R-4）。
     */
    public static final DefaultRedisScript<Long> INCR_EXPIRE_SCRIPT = new DefaultRedisScript<>(
            "local c = redis.call('INCR', KEYS[1]) "
                    + "if c == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end "
                    + "return c",
            Long.class);

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        // 多态反序列化白名单：仅允许项目实体包与 JDK 集合/时间类型，阻断未知 class 的 gadget 投毒（R-3）
        BasicPolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.example.sms.")
                .allowIfSubType("java.util.")
                .allowIfSubType("java.time.")
                .build();
        ObjectMapper mapper = new ObjectMapper();
        mapper.activateDefaultTyping(typeValidator, ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);

        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(30))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer(mapper)));
        return RedisCacheManager.builder(factory)
                .cacheDefaults(config)
                .build();
    }
}
