package com.clacjz.medical.rag.security;

import com.clacjz.medical.rag.config.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("JwtTokenProvider 单元测试")
class JwtTokenProviderTest {

    @Mock
    private JwtProperties jwtProperties;

    private JwtTokenProvider jwtTokenProvider;

    // Use a cryptographically strong key with no weak patterns
    private static final String TEST_SECRET = "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0u1v2w3x4y5z6a7b8c9d0e1f2";
    private static final String USER_ID = "user-123";
    private static final String USERNAME = "testuser";
    private static final String ROLE = "DEPT_USER";
    // Use 7 days in ms to avoid timing issues
    private static final long EXPIRATION = 604800000L;
    private static final long REFRESH_EXPIRATION = 604800000L;

    @BeforeEach
    void setUp() throws Exception {
        // Set up mocks BEFORE creating the provider
        lenient().when(jwtProperties.getSecret()).thenReturn(TEST_SECRET);
        // The init() method reads 'expiration' and 'refreshExpiration' fields
        lenient().when(jwtProperties.getExpiration()).thenReturn(EXPIRATION);
        lenient().when(jwtProperties.getRefreshExpiration()).thenReturn(REFRESH_EXPIRATION);
        // getAccessTokenExpiration/getRefreshTokenExpiration return from AtomicLong
        lenient().when(jwtProperties.getAccessTokenExpiration()).thenReturn(EXPIRATION);
        lenient().when(jwtProperties.getRefreshTokenExpiration()).thenReturn(REFRESH_EXPIRATION);

        jwtTokenProvider = new JwtTokenProvider(jwtProperties);
        jwtTokenProvider.init();
    }

    @Test
    @DisplayName("生成 AccessToken 成功")
    void generateAccessToken_Success() {
        String token = jwtTokenProvider.generateAccessToken(USER_ID, USERNAME, ROLE);

        assertNotNull(token);
        assertFalse(token.isEmpty());
        assertTrue(token.split("\\.").length == 3);
    }

    @Test
    @DisplayName("生成的 AccessToken 包含正确的声明")
    void generateAccessToken_ContainsCorrectClaims() {
        String token = jwtTokenProvider.generateAccessToken(USER_ID, USERNAME, ROLE);

        assertEquals(USER_ID, jwtTokenProvider.getUserIdFromToken(token));
        assertEquals(USERNAME, jwtTokenProvider.getUsernameFromToken(token));
        assertEquals(ROLE, jwtTokenProvider.getRoleFromToken(token));
        assertEquals("access", jwtTokenProvider.getTokenType(token));
    }

    @Test
    @DisplayName("生成的 RefreshToken 类型正确")
    void generateRefreshToken_CorrectType() {
        String token = jwtTokenProvider.generateRefreshToken(USER_ID);

        assertEquals("refresh", jwtTokenProvider.getTokenType(token));
        assertTrue(jwtTokenProvider.isRefreshToken(token));
        assertFalse(jwtTokenProvider.isAccessToken(token));
    }

    @Test
    @DisplayName("验证有效 Token")
    void validateToken_ValidToken() {
        String token = jwtTokenProvider.generateAccessToken(USER_ID, USERNAME, ROLE);

        assertTrue(jwtTokenProvider.validateToken(token));
    }

    @Test
    @DisplayName("验证无效 Token")
    void validateToken_InvalidToken() {
        assertFalse(jwtTokenProvider.validateToken("invalid.token.here"));
    }

    @Test
    @DisplayName("密钥长度不足时抛出异常")
    void init_ShortSecret() {
        JwtTokenProvider provider = new JwtTokenProvider(jwtProperties);
        lenient().when(jwtProperties.getSecret()).thenReturn("short-secret");
        lenient().when(jwtProperties.getExpiration()).thenReturn(EXPIRATION);
        lenient().when(jwtProperties.getRefreshExpiration()).thenReturn(REFRESH_EXPIRATION);
        lenient().when(jwtProperties.getAccessTokenExpiration()).thenReturn(EXPIRATION);
        lenient().when(jwtProperties.getRefreshTokenExpiration()).thenReturn(REFRESH_EXPIRATION);

        assertThrows(IllegalStateException.class, provider::init);
    }

    @Test
    @DisplayName("获取过期时间配置")
    void getExpiration() {
        assertEquals(EXPIRATION, jwtTokenProvider.getExpiration());
    }

    @Test
    @DisplayName("获取刷新过期时间配置")
    void getRefreshExpiration() {
        assertEquals(REFRESH_EXPIRATION, jwtTokenProvider.getRefreshExpiration());
    }

    @Test
    @DisplayName("生成多个 Token 的唯一性")
    void generateMultipleTokens_Unique() {
        String token1 = jwtTokenProvider.generateAccessToken(USER_ID, USERNAME, ROLE);
        String token2 = jwtTokenProvider.generateAccessToken(USER_ID, USERNAME, ROLE);

        assertNotEquals(token1, token2);
        assertNotEquals(jwtTokenProvider.getJtiFromToken(token1), jwtTokenProvider.getJtiFromToken(token2));
    }
}
