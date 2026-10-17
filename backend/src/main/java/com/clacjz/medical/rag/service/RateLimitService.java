package com.clacjz.medical.rag.service;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * API 限流服务
 * 使用 Resilience4j RateLimiter 实现全局限流保护
 */
@Slf4j
@Service
public class RateLimitService {

    private final RateLimiter apiRateLimiter;
    private final RateLimiter authRateLimiter;

    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong successfulRequests = new AtomicLong(0);
    private final AtomicLong rejectedRequests = new AtomicLong(0);

    public RateLimitService() {
        // API 全局限流：100 QPS
        RateLimiterConfig apiConfig = RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .limitForPeriod(100)
                .timeoutDuration(Duration.ofMillis(500))
                .build();

        // 认证接口限流：10 QPS（更严格，防止暴力破解）
        RateLimiterConfig authConfig = RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .limitForPeriod(10)
                .timeoutDuration(Duration.ofMillis(500))
                .build();

        RateLimiterRegistry registry = RateLimiterRegistry.of(apiConfig);
        this.apiRateLimiter = registry.rateLimiter("api");
        this.authRateLimiter = RateLimiterRegistry.of(authConfig).rateLimiter("auth");

        log.info("RateLimiter initialized: api=100QPS, auth=10QPS");
    }

    /**
     * 检查 API 请求是否允许通过
     */
    public boolean allowApiRequest() {
        totalRequests.incrementAndGet();
        boolean allowed = apiRateLimiter.acquirePermission();
        if (allowed) {
            successfulRequests.incrementAndGet();
        } else {
            rejectedRequests.incrementAndGet();
            log.warn("API request rejected due to rate limit");
        }
        return allowed;
    }

    /**
     * 检查认证请求是否允许通过
     */
    public boolean allowAuthRequest() {
        totalRequests.incrementAndGet();
        boolean allowed = authRateLimiter.acquirePermission();
        if (allowed) {
            successfulRequests.incrementAndGet();
        } else {
            rejectedRequests.incrementAndGet();
            log.warn("Auth request rejected due to rate limit");
        }
        return allowed;
    }

    /**
     * 获取限流统计信息
     */
    public RateLimitStats getStats() {
        return new RateLimitStats(
                totalRequests.get(),
                successfulRequests.get(),
                rejectedRequests.get(),
                apiRateLimiter.getMetrics().getAvailablePermissions(),
                authRateLimiter.getMetrics().getAvailablePermissions()
        );
    }

    public record RateLimitStats(
            long totalRequests,
            long successfulRequests,
            long rejectedRequests,
            int apiAvailablePermissions,
            int authAvailablePermissions
    ) {}
}