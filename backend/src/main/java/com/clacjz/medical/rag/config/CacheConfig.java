package com.clacjz.medical.rag.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String USER_PERMISSIONS_CACHE = "userPermissions";
    public static final String DOCUMENT_PERMISSION_CACHE = "documentPermissions";
    public static final String DEPT_USERS_CACHE = "deptUsers";

    /**
     * 权限缓存 TTL: 5分钟
     */
    private static final Duration PERMISSION_CACHE_TTL = Duration.ofMinutes(5);

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .entryTtl(Duration.ofMinutes(10));

        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        // 用户权限缓存 - TTL 5分钟
        cacheConfigurations.put(USER_PERMISSIONS_CACHE, defaultConfig.entryTtl(PERMISSION_CACHE_TTL));

        // 文档权限缓存 - TTL 5分钟
        cacheConfigurations.put(DOCUMENT_PERMISSION_CACHE, defaultConfig.entryTtl(PERMISSION_CACHE_TTL));

        // 部门用户缓存 - TTL 5分钟
        cacheConfigurations.put(DEPT_USERS_CACHE, defaultConfig.entryTtl(PERMISSION_CACHE_TTL));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .transactionAware()
                .build();
    }
}