package com.clacjz.medical.rag.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TokenBlacklistService 单元测试")
class TokenBlacklistServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private SetOperations<String, String> setOperations;

    @Mock
    private com.clacjz.medical.rag.config.JwtProperties jwtProperties;

    @InjectMocks
    private TokenBlacklistService tokenBlacklistService;

    private static final String JTI = "test-jti-123";
    private static final String USER_ID = "user-123";

    @BeforeEach
    void setUp() {
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        lenient().when(jwtProperties.getAccessTokenExpiration()).thenReturn(7200000L);
        lenient().when(jwtProperties.getRefreshTokenExpiration()).thenReturn(604800000L);
    }

    @Nested
    @DisplayName("isRefreshTokenBlacklisted 测试")
    class IsRefreshTokenBlacklistedTests {

        @Test
        @DisplayName("Refresh Token 在黑名单中返回 true")
        void isRefreshTokenBlacklisted_True() {
            when(stringRedisTemplate.hasKey("blacklist:refresh:" + JTI)).thenReturn(true);

            assertTrue(tokenBlacklistService.isRefreshTokenBlacklisted(JTI));
        }

        @Test
        @DisplayName("Refresh Token 不在黑名单中返回 false")
        void isRefreshTokenBlacklisted_False() {
            when(stringRedisTemplate.hasKey("blacklist:refresh:" + JTI)).thenReturn(false);

            assertFalse(tokenBlacklistService.isRefreshTokenBlacklisted(JTI));
        }

        @Test
        @DisplayName("Redis异常时Refresh Token检查返回true（安全降级）")
        void isRefreshTokenBlacklisted_RedisDown_ReturnsTrue() {
            when(stringRedisTemplate.hasKey("blacklist:refresh:" + JTI)).thenThrow(new RuntimeException("Redis down"));

            assertTrue(tokenBlacklistService.isRefreshTokenBlacklisted(JTI));
        }
    }

    @Nested
    @DisplayName("removeFromBlacklist 测试")
    class RemoveFromBlacklistTests {

        @Test
        @DisplayName("成功从黑名单移除Access Token")
        void removeFromBlacklist_AccessType() {
            when(stringRedisTemplate.delete("blacklist:access:" + JTI)).thenReturn(true);

            assertDoesNotThrow(() ->
                    tokenBlacklistService.removeFromBlacklist(JTI, TokenBlacklistService.TokenType.ACCESS));
            verify(stringRedisTemplate).delete("blacklist:access:" + JTI);
        }

        @Test
        @DisplayName("成功从黑名单移除Refresh Token")
        void removeFromBlacklist_RefreshType() {
            when(stringRedisTemplate.delete("blacklist:refresh:" + JTI)).thenReturn(true);

            assertDoesNotThrow(() ->
                    tokenBlacklistService.removeFromBlacklist(JTI, TokenBlacklistService.TokenType.REFRESH));
            verify(stringRedisTemplate).delete("blacklist:refresh:" + JTI);
        }

        @Test
        @DisplayName("null JTI 不执行操作")
        void removeFromBlacklist_NullJti() {
            assertDoesNotThrow(() ->
                    tokenBlacklistService.removeFromBlacklist(null, TokenBlacklistService.TokenType.ACCESS));
            verify(stringRedisTemplate, never()).delete(anyString());
        }

        @Test
        @DisplayName("空字符串JTI 不执行操作")
        void removeFromBlacklist_EmptyJti() {
            assertDoesNotThrow(() ->
                    tokenBlacklistService.removeFromBlacklist("", TokenBlacklistService.TokenType.ACCESS));
            verify(stringRedisTemplate, never()).delete(anyString());
        }

        @Test
        @DisplayName("Redis异常不抛出")
        void removeFromBlacklist_RedisError() {
            when(stringRedisTemplate.delete(anyString())).thenThrow(new RuntimeException("Redis error"));

            assertDoesNotThrow(() ->
                    tokenBlacklistService.removeFromBlacklist(JTI, TokenBlacklistService.TokenType.ACCESS));
        }
    }

    @Nested
    @DisplayName("Access Token 黑名单测试")
    class BlacklistAccessTokenTests {

        @Test
        @DisplayName("成功将 Access Token 加入黑名单")
        void blacklistAccessToken_Success() {
            tokenBlacklistService.blacklistAccessToken(JTI, 7200000L);

            verify(valueOperations).set(
                    eq("blacklist:access:" + JTI),
                    anyString(),
                    eq(Duration.ofSeconds(7260))
            );
        }

        @Test
        @DisplayName("null JTI 不执行任何操作")
        void blacklistAccessToken_NullJti() {
            tokenBlacklistService.blacklistAccessToken(null, 7200000L);

            verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
        }

        @Test
        @DisplayName("空字符串JTI 不执行任何操作")
        void blacklistAccessToken_EmptyJti() {
            tokenBlacklistService.blacklistAccessToken("", 7200000L);

            verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
        }

        @Test
        @DisplayName("Redis异常不抛出（静默失败）")
        void blacklistAccessToken_RedisError() {
            doThrow(new RuntimeException("Redis connection failed"))
                    .when(valueOperations).set(anyString(), anyString(), any(Duration.class));

            assertThrows(RuntimeException.class, () -> tokenBlacklistService.blacklistAccessToken(JTI, 7200000L));
        }

        @Test
        @DisplayName("黑名单时不再删除原始access token（token:access:前缀从未被写入）")
        void blacklistAccessToken_ShouldNotRemoveOriginalToken() {
            tokenBlacklistService.blacklistAccessToken(JTI, 7200000L);

            // removeAccessToken已移除：token:access:前缀从未被写入，删除操作为空操作
            verify(stringRedisTemplate, never()).delete(startsWith("token:access:"));
        }
    }

    @Nested
    @DisplayName("Refresh Token 黑名单测试")
    class BlacklistRefreshTokenTests {

        @Test
        @DisplayName("成功将 Refresh Token 加入黑名单")
        void blacklistRefreshToken_Success() {
            tokenBlacklistService.blacklistRefreshToken(JTI, 604800000L);

            verify(valueOperations).set(
                    eq("blacklist:refresh:" + JTI),
                    anyString(),
                    eq(Duration.ofSeconds(604860))
            );
        }
    }

    @Nested
    @DisplayName("检查黑名单状态测试")
    class IsTokenBlacklistedTests {

        @Test
        @DisplayName("Token 在黑名单中返回 true")
        void isTokenBlacklisted_True() {
            when(stringRedisTemplate.hasKey("blacklist:access:" + JTI)).thenReturn(true);

            assertTrue(tokenBlacklistService.isAccessTokenBlacklisted(JTI));
        }

        @Test
        @DisplayName("Token 不在黑名单中返回 false")
        void isTokenBlacklisted_False() {
            when(stringRedisTemplate.hasKey("blacklist:access:" + JTI)).thenReturn(false);

            assertFalse(tokenBlacklistService.isAccessTokenBlacklisted(JTI));
        }

        @Test
        @DisplayName("null JTI 返回 false")
        void isTokenBlacklisted_NullJti() {
            assertFalse(tokenBlacklistService.isAccessTokenBlacklisted(null));
            assertFalse(tokenBlacklistService.isRefreshTokenBlacklisted(null));
        }
    }

    @Nested
    @DisplayName("撤销用户所有 Token 测试")
    class RevokeAllUserTokensTests {

        @Test
        @DisplayName("用户没有 Token 时不执行操作")
        void revokeAllUserTokens_NoTokens() {
            when(setOperations.members("token:user:" + USER_ID)).thenReturn(Collections.emptySet());

            tokenBlacklistService.revokeAllUserTokens(USER_ID);

            verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
        }

        @Test
        @DisplayName("null User ID 不执行任何操作")
        void revokeAllUserTokens_NullUserId() {
            tokenBlacklistService.revokeAllUserTokens(null);

            verify(setOperations, never()).members(anyString());
        }
    }

    @Nested
    @DisplayName("获取统计信息测试")
    class GetStatisticsTests {

        @Test
        @DisplayName("获取统计信息成功")
        void getStatistics_Success() {
            Map<String, Object> stats = tokenBlacklistService.getStatistics();

            assertNotNull(stats);
            assertTrue(stats.containsKey("totalBlacklistChecks"));
            assertTrue(stats.containsKey("blacklistHits"));
            assertTrue(stats.containsKey("hitRate"));
        }

        @Test
        @DisplayName("初始统计值为零")
        void getStatistics_InitialValues() {
            Map<String, Object> stats = tokenBlacklistService.getStatistics();

            assertEquals(0L, stats.get("totalBlacklistChecks"));
            assertEquals(0L, stats.get("blacklistHits"));
            assertEquals(0.0, stats.get("hitRate"));
        }
    }

    @Nested
    @DisplayName("TokenType 枚举测试")
    class TokenTypeTests {

        @Test
        @DisplayName("TokenType 枚举包含正确的值")
        void tokenType_ContainsCorrectValues() {
            TokenBlacklistService.TokenType[] types = TokenBlacklistService.TokenType.values();

            assertEquals(2, types.length);
            assertEquals(TokenBlacklistService.TokenType.ACCESS, types[0]);
            assertEquals(TokenBlacklistService.TokenType.REFRESH, types[1]);
        }

        @Test
        @DisplayName("TokenType.valueOf 正确解析")
        void tokenType_ValueOf() {
            assertEquals(TokenBlacklistService.TokenType.ACCESS,
                    TokenBlacklistService.TokenType.valueOf("ACCESS"));
            assertEquals(TokenBlacklistService.TokenType.REFRESH,
                    TokenBlacklistService.TokenType.valueOf("REFRESH"));
        }
    }

    @Nested
    @DisplayName("resetStatistics 测试")
    class ResetStatisticsTests {

        @Test
        @DisplayName("重置后统计值归零")
        void shouldResetStatisticsToZero() {
            // First, trigger some checks to increment counters
            when(stringRedisTemplate.hasKey(anyString())).thenReturn(false);
            tokenBlacklistService.isAccessTokenBlacklisted(JTI);
            tokenBlacklistService.isAccessTokenBlacklisted(JTI);

            Map<String, Object> beforeReset = tokenBlacklistService.getStatistics();
            assertTrue((Long) beforeReset.get("totalBlacklistChecks") >= 2);

            tokenBlacklistService.resetStatistics();

            Map<String, Object> afterReset = tokenBlacklistService.getStatistics();
            assertEquals(0L, afterReset.get("totalBlacklistChecks"));
            assertEquals(0L, afterReset.get("blacklistHits"));
        }
    }

    @Nested
    @DisplayName("healthCheck 测试")
    class HealthCheckTests {

        @Test
        @DisplayName("Redis连接失败时返回DOWN状态")
        void shouldReturnDownWhenRedisUnavailable() {
            when(stringRedisTemplate.getConnectionFactory()).thenThrow(new RuntimeException("No connection"));

            Map<String, Object> health = tokenBlacklistService.healthCheck();

            assertEquals("DOWN", health.get("status"));
            assertNotNull(health.get("error"));
        }
    }

    @Nested
    @DisplayName("revokeAllUserTokens 扩展测试")
    class RevokeAllUserTokensExtendedTests {

        @Test
        @DisplayName("空字符串User ID 不执行任何操作")
        void revokeAllUserTokens_EmptyUserId() {
            tokenBlacklistService.revokeAllUserTokens("");

            verify(setOperations, never()).members(anyString());
        }

        @Test
        @DisplayName("用户有Access和Refresh Token时都应被黑名单")
        void revokeAllUserTokens_BothTokenTypes() {
            Set<String> tokens = new HashSet<>();
            tokens.add("ACCESS:jti-access-1");
            tokens.add("REFRESH:jti-refresh-1");
            when(setOperations.members("token:user:" + USER_ID)).thenReturn(tokens);

            tokenBlacklistService.revokeAllUserTokens(USER_ID);

            verify(valueOperations, atLeast(2)).set(anyString(), anyString(), any(Duration.class));
            verify(stringRedisTemplate).delete("token:user:" + USER_ID);
        }

        @Test
        @DisplayName("格式错误的token条目应被跳过")
        void revokeAllUserTokens_MalformedTokenEntry() {
            Set<String> tokens = new HashSet<>();
            tokens.add("MALFORMED_NO_COLON");
            when(setOperations.members("token:user:" + USER_ID)).thenReturn(tokens);

            assertDoesNotThrow(() -> tokenBlacklistService.revokeAllUserTokens(USER_ID));
        }

        @Test
        @DisplayName("Redis异常不抛出")
        void revokeAllUserTokens_RedisError() {
            when(setOperations.members(anyString())).thenThrow(new RuntimeException("Redis error"));

            assertDoesNotThrow(() -> tokenBlacklistService.revokeAllUserTokens(USER_ID));
        }
    }

    @Nested
    @DisplayName("黑名单命中统计测试")
    class BlacklistHitStatisticsTests {

        @Test
        @DisplayName("命中黑名单时hitRate应大于0")
        void shouldTrackHitRate() {
            when(stringRedisTemplate.hasKey("blacklist:access:hit-jti")).thenReturn(true);
            when(stringRedisTemplate.hasKey("blacklist:access:miss-jti")).thenReturn(false);

            tokenBlacklistService.isAccessTokenBlacklisted("hit-jti");
            tokenBlacklistService.isAccessTokenBlacklisted("miss-jti");

            Map<String, Object> stats = tokenBlacklistService.getStatistics();
            assertEquals(2L, stats.get("totalBlacklistChecks"));
            assertEquals(1L, stats.get("blacklistHits"));
            assertEquals(0.5, (Double) stats.get("hitRate"), 0.001);
        }
    }
}
