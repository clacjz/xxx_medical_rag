package com.clacjz.medical.rag.controller;

import com.clacjz.medical.rag.model.vo.Result;
import com.clacjz.medical.rag.service.TokenBlacklistService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TokenMonitorController 单元测试")
class TokenMonitorControllerTest {

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    @InjectMocks
    private TokenMonitorController tokenMonitorController;

    @Nested
    @DisplayName("获取Token黑名单统计测试")
    class GetStatisticsTests {

        @Test
        @DisplayName("获取统计成功")
        void getStatistics_Success() {
            Map<String, Object> stats = Map.of(
                    "totalChecks", 1000L,
                    "blacklistedTokens", 50L,
                    "whitelistedTokens", 950L,
                    "cacheHitRate", 0.95
            );

            when(tokenBlacklistService.getStatistics()).thenReturn(stats);

            ResponseEntity<Result<Map<String, Object>>> response =
                    tokenMonitorController.getStatistics();

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(200, response.getBody().getCode());
            assertEquals("获取成功", response.getBody().getMessage());
            assertEquals(1000L, response.getBody().getData().get("totalChecks"));
        }

        @Test
        @DisplayName("获取统计返回空统计")
        void getStatistics_EmptyStats() {
            Map<String, Object> emptyStats = Map.of(
                    "totalChecks", 0L,
                    "blacklistedTokens", 0L
            );

            when(tokenBlacklistService.getStatistics()).thenReturn(emptyStats);

            ResponseEntity<Result<Map<String, Object>>> response =
                    tokenMonitorController.getStatistics();

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(0L, response.getBody().getData().get("totalChecks"));
        }
    }

    @Nested
    @DisplayName("Redis健康检查测试")
    class HealthCheckTests {

        @Test
        @DisplayName("健康检查成功 - Redis正常")
        void healthCheck_Success_RedisHealthy() {
            Map<String, Object> health = Map.of(
                    "status", "UP",
                    "redis", "CONNECTED",
                    "responseTime", 5L
            );

            when(tokenBlacklistService.healthCheck()).thenReturn(health);

            ResponseEntity<Result<Map<String, Object>>> response =
                    tokenMonitorController.healthCheck();

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("健康检查完成", response.getBody().getMessage());
            assertEquals("UP", response.getBody().getData().get("status"));
        }

        @Test
        @DisplayName("健康检查成功 - Redis断开")
        void healthCheck_Success_RedisDown() {
            Map<String, Object> health = Map.of(
                    "status", "DOWN",
                    "redis", "DISCONNECTED",
                    "error", "Connection refused"
            );

            when(tokenBlacklistService.healthCheck()).thenReturn(health);

            ResponseEntity<Result<Map<String, Object>>> response =
                    tokenMonitorController.healthCheck();

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals("DOWN", response.getBody().getData().get("status"));
        }
    }

    @Nested
    @DisplayName("重置统计信息测试")
    class ResetStatisticsTests {

        @Test
        @DisplayName("重置统计信息成功")
        void resetStatistics_Success() {
            doNothing().when(tokenBlacklistService).resetStatistics();

            ResponseEntity<Result<Void>> response =
                    tokenMonitorController.resetStatistics();

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("统计信息已重置", response.getBody().getMessage());
            assertNull(response.getBody().getData());
            verify(tokenBlacklistService, times(1)).resetStatistics();
        }
    }

    @Nested
    @DisplayName("响应结构验证测试")
    class ResponseStructureTests {

        @Test
        @DisplayName("统计响应包含时间戳")
        void getStatistics_ResponseContainsTimestamp() {
            when(tokenBlacklistService.getStatistics()).thenReturn(Map.of("total", 0));

            ResponseEntity<Result<Map<String, Object>>> response =
                    tokenMonitorController.getStatistics();

            assertNotNull(response.getBody());
            assertNotNull(response.getBody().getTimestamp());
        }

        @Test
        @DisplayName("健康检查响应包含时间戳")
        void healthCheck_ResponseContainsTimestamp() {
            when(tokenBlacklistService.healthCheck()).thenReturn(Map.of("status", "UP"));

            ResponseEntity<Result<Map<String, Object>>> response =
                    tokenMonitorController.healthCheck();

            assertNotNull(response.getBody());
            assertNotNull(response.getBody().getTimestamp());
        }

        @Test
        @DisplayName("重置统计响应包含时间戳")
        void resetStatistics_ResponseContainsTimestamp() {
            doNothing().when(tokenBlacklistService).resetStatistics();

            ResponseEntity<Result<Void>> response =
                    tokenMonitorController.resetStatistics();

            assertNotNull(response.getBody());
            assertNotNull(response.getBody().getTimestamp());
        }
    }

    @Nested
    @DisplayName("边界值测试")
    class BoundaryTests {

        @Test
        @DisplayName("统计服务返回极大数值")
        void getStatistics_LargeNumbers() {
            Map<String, Object> stats = Map.of(
                    "totalChecks", Long.MAX_VALUE,
                    "blacklistedTokens", 0L
            );

            when(tokenBlacklistService.getStatistics()).thenReturn(stats);

            ResponseEntity<Result<Map<String, Object>>> response =
                    tokenMonitorController.getStatistics();

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(Long.MAX_VALUE, response.getBody().getData().get("totalChecks"));
        }

        @Test
        @DisplayName("统计服务返回极小数值")
        void getStatistics_SmallNumbers() {
            Map<String, Object> stats = Map.of(
                    "totalChecks", 0L,
                    "blacklistedTokens", 0L,
                    "whitelistedTokens", 0L,
                    "cacheHitRate", 0.0
            );

            when(tokenBlacklistService.getStatistics()).thenReturn(stats);

            ResponseEntity<Result<Map<String, Object>>> response =
                    tokenMonitorController.getStatistics();

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(0L, response.getBody().getData().get("totalChecks"));
            assertEquals(0.0, response.getBody().getData().get("cacheHitRate"));
        }
    }
}
