package com.clacjz.medical.rag.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RateLimitService 单元测试")
class RateLimitServiceTest {

    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        rateLimitService = new RateLimitService();
    }

    @Nested
    @DisplayName("allowApiRequest - API请求限流")
    class AllowApiRequestTests {

        @Test
        @DisplayName("首次请求应该被允许")
        void shouldAllowFirstRequest() {
            boolean result = rateLimitService.allowApiRequest();
            assertTrue(result);
        }

        @Test
        @DisplayName("连续请求在限流范围内应该被允许")
        void shouldAllowRequestsWithinLimit() {
            for (int i = 0; i < 50; i++) {
                assertTrue(rateLimitService.allowApiRequest(),
                        "Request " + (i + 1) + " should be allowed");
            }
        }

        @Test
        @DisplayName("返回统计信息包含成功计数")
        void shouldIncrementSuccessfulCount() {
            rateLimitService.allowApiRequest();
            rateLimitService.allowApiRequest();

            RateLimitService.RateLimitStats stats = rateLimitService.getStats();
            assertTrue(stats.successfulRequests() >= 2);
        }

        @Test
        @DisplayName("多次请求后统计信息更新")
        void shouldUpdateStatsAfterMultipleRequests() {
            long initialTotal = rateLimitService.getStats().totalRequests();

            rateLimitService.allowApiRequest();
            rateLimitService.allowApiRequest();
            rateLimitService.allowApiRequest();

            long newTotal = rateLimitService.getStats().totalRequests();
            assertEquals(initialTotal + 3, newTotal);
        }
    }

    @Nested
    @DisplayName("allowAuthRequest - 认证请求限流")
    class AllowAuthRequestTests {

        @Test
        @DisplayName("认证请求应该被允许")
        void shouldAllowAuthRequest() {
            boolean result = rateLimitService.allowAuthRequest();
            assertTrue(result);
        }

        @Test
        @DisplayName("认证限流更严格")
        void shouldHaveStricterLimitForAuth() {
            for (int i = 0; i < 10; i++) {
                rateLimitService.allowAuthRequest();
            }
            RateLimitService.RateLimitStats stats = rateLimitService.getStats();
            assertTrue(stats.totalRequests() >= 10);
        }

        @Test
        @DisplayName("认证请求统计信息正确")
        void shouldTrackAuthRequests() {
            long initialTotal = rateLimitService.getStats().totalRequests();

            rateLimitService.allowAuthRequest();

            long newTotal = rateLimitService.getStats().totalRequests();
            assertEquals(initialTotal + 1, newTotal);
        }
    }

    @Nested
    @DisplayName("getStats - 获取统计信息")
    class GetStatsTests {

        @Test
        @DisplayName("返回完整统计信息")
        void shouldReturnCompleteStats() {
            rateLimitService.allowApiRequest();
            rateLimitService.allowAuthRequest();

            RateLimitService.RateLimitStats stats = rateLimitService.getStats();

            assertNotNull(stats);
            assertTrue(stats.totalRequests() >= 2);
            assertTrue(stats.successfulRequests() >= 2);
            assertEquals(0, stats.rejectedRequests());
            assertTrue(stats.apiAvailablePermissions() >= 0);
            assertTrue(stats.authAvailablePermissions() >= 0);
        }

        @Test
        @DisplayName("统计信息包含所有请求计数")
        void shouldTrackAllRequests() {
            long initialTotal = rateLimitService.getStats().totalRequests();

            rateLimitService.allowApiRequest();
            rateLimitService.allowApiRequest();
            rateLimitService.allowAuthRequest();

            long newTotal = rateLimitService.getStats().totalRequests();
            assertEquals(initialTotal + 3, newTotal);
        }

        @Test
        @DisplayName("统计信息初始值为零")
        void shouldHaveInitialZeroValues() {
            RateLimitService newService = new RateLimitService();
            RateLimitService.RateLimitStats stats = newService.getStats();

            assertEquals(0, stats.totalRequests());
            assertEquals(0, stats.successfulRequests());
            assertEquals(0, stats.rejectedRequests());
        }
    }
}
