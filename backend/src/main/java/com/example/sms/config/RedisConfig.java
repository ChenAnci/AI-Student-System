package com.example.sms.config; // 声明当前类所属包：本项目所有配置类统一放在 com.example.sms.config 包下

// ---- import 区域：引入 Redis 缓存配置所需的 Jackson 与 Spring Data Redis 相关类 ----
import com.fasterxml.jackson.annotation.JsonTypeInfo; // Jackson 类型信息注解枚举：用于在 JSON 中携带类型标识（@class 字段）
import com.fasterxml.jackson.databind.ObjectMapper; // Jackson 核心对象映射器：用于定制 Redis 的 JSON 序列化行为
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator; // 多态类型白名单校验器：限制允许反序列化的类型
import org.springframework.cache.annotation.EnableCaching; // 启用 Spring Cache 注解（@Cacheable/@CacheEvict 等）
import org.springframework.context.annotation.Bean; // @Bean 注解：将方法返回值注册为 Spring 容器管理的 Bean
import org.springframework.context.annotation.Configuration; // @Configuration 注解：标记该类为 Spring 配置类
import org.springframework.data.redis.cache.RedisCacheConfiguration; // Redis 缓存配置类：设置 TTL、序列化方式等
import org.springframework.data.redis.cache.RedisCacheManager; // Redis 缓存管理器：Spring Cache 接入 Redis 的桥梁
import org.springframework.data.redis.connection.RedisConnectionFactory; // Redis 连接工厂：用于创建 Redis 连接
import org.springframework.data.redis.core.script.DefaultRedisScript; // Redis Lua 脚本封装类：声明脚本内容与返回类型
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer; // 通用 JSON 序列化器：带类型信息、跨语言可读
import org.springframework.data.redis.serializer.RedisSerializationContext; // 缓存序列化上下文：用于设置值序列化器

import java.time.Duration; // 时长类：用于设置缓存过期时间（TTL）

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
@Configuration // 标记为 Spring 配置类：启动时自动加载生效
@EnableCaching // 启用 Spring Cache 缓存注解功能（@Cacheable 等在此项目可用）
public class RedisConfig { // Redis 配置类

    /**
     * 计数自增 + 首次设置过期时间的原子脚本（INCR 返回 1 时 EXPIRE）。
     * 相比 INCR+EXPIRE 两条命令，避免进程在两者之间崩溃导致 key 无 TTL 永久残留（R-4）。
     */
    public static final DefaultRedisScript<Long> INCR_EXPIRE_SCRIPT = new DefaultRedisScript<>( // 定义全局共享的 Lua 原子计数脚本（返回 Long）
            "local c = redis.call('INCR', KEYS[1]) " // Lua 第 1 句：对指定键执行自增并把结果存入变量 c
                    + "if c == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end " // Lua 第 2 句：首次计数（c==1）时才设置过期时间
                    + "return c", // Lua 第 3 句：返回自增后的计数值
            Long.class); // 指定脚本返回类型为 Long

    /**
     * 构建 Redis 缓存管理器（Spring Cache 接入 Redis）：
     * 值序列化采用 JSON，并通过 BasicPolymorphicTypeValidator 白名单限制反序列化类型（安全加固）。
     */
    @Bean // 注册缓存管理器 Bean：替换 Spring Boot 默认的缓存管理器，使 @Cacheable 落到 Redis
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) { // 方法签名：由框架注入 Redis 连接工厂
        // 多态反序列化白名单：项目实体包 + JDK 常用值类型（集合/时间/数值/字符串/包装类），
        // 阻断未知 class 的 gadget 投毒（R-3）。缺 java.lang/java.math 会导致 BigDecimal 等字段反序列化被拒。
        BasicPolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder() // 构建类型白名单校验器
                .allowIfSubType("com.example.sms.") // 白名单 1：允许项目自身包下的实体类型
                .allowIfSubType("java.util.") // 白名单 2：允许 JDK 集合工具包类型（List/Map 等）
                .allowIfSubType("java.time.") // 白名单 3：允许 JDK 时间类型（LocalDateTime 等）
                .allowIfSubType("java.lang.") // 白名单 4：允许 JDK 基础类型（String、包装类等）
                .allowIfSubType("java.math.") // 白名单 5：允许高精度数值类型（BigDecimal 等）
                .build(); // 构建完成
        // 开启默认类型信息（@class 字段）用于 JSON 反序列化时还原具体类型。
        // 安全关键点：通过上面的 BasicPolymorphicTypeValidator 做白名单校验——反序列化只允许落在
        // 项目包与 JDK 常用值类型内的 class，Redis 即使被外部写入恶意 JSON（gadget 链）也无法
        // 反序列化到任意攻击类，从根源上缓解 Redis 反序列化 RCE 风险。
        ObjectMapper mapper = new ObjectMapper(); // 创建 Jackson 映射器实例（用于定制序列化细节）
        mapper.activateDefaultTyping(typeValidator, ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY); // 开启"带白名单校验"的默认类型信息：序列化时写入 @class、反序列化时按白名单还原类型

        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig() // 以默认缓存配置为起点
                // 默认 TTL 30s：业务缓存（如统计、列表）允许短时间陈旧，30 秒足够显著降低
                // DB/计算压力，又不会让用户看到明显过期的数据；比长 TTL 更不易积累脏数据。
                .entryTtl(Duration.ofSeconds(30)) // 设置缓存默认过期时间为 30 秒
                .serializeValuesWith(RedisSerializationContext.SerializationPair // 配置缓存值的序列化方式
                        .fromSerializer(new GenericJackson2JsonRedisSerializer(mapper))); // 使用带类型白名单的 JSON 序列化器
        return RedisCacheManager.builder(factory) // 基于连接工厂构建缓存管理器
                .cacheDefaults(config) // 应用上面配置的默认缓存策略（TTL + 序列化）
                .build(); // 构建完成并返回
    }
}
