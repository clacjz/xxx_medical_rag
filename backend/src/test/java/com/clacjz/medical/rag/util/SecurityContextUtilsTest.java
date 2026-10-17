package com.clacjz.medical.rag.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SecurityContextUtils 单元测试")
class SecurityContextUtilsTest {

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private SecurityContext createMockContext(Authentication authentication) {
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        return securityContext;
    }

    @Nested
    @DisplayName("getCurrentUsername 测试")
    class GetCurrentUsernameTests {

        @Test
        @DisplayName("成功获取用户名 - UserDetails 类型")
        void getCurrentUsername_WithUserDetails() {
            UserDetails userDetails = User.builder()
                    .username("testuser")
                    .password("password")
                    .authorities(Collections.emptyList())
                    .build();

            Authentication authentication = mock(Authentication.class);
            when(authentication.isAuthenticated()).thenReturn(true);
            when(authentication.getPrincipal()).thenReturn(userDetails);

            SecurityContext securityContext = createMockContext(authentication);
            SecurityContextHolder.setContext(securityContext);

            Optional<String> result = SecurityContextUtils.getCurrentUsername();

            assertTrue(result.isPresent());
            assertEquals("testuser", result.get());
        }

        @Test
        @DisplayName("成功获取用户名 - String 类型")
        void getCurrentUsername_WithStringPrincipal() {
            Authentication authentication = mock(Authentication.class);
            when(authentication.isAuthenticated()).thenReturn(true);
            when(authentication.getPrincipal()).thenReturn("anonymousUser");

            SecurityContext securityContext = createMockContext(authentication);
            SecurityContextHolder.setContext(securityContext);

            Optional<String> result = SecurityContextUtils.getCurrentUsername();

            assertTrue(result.isPresent());
            assertEquals("anonymousUser", result.get());
        }

        @Test
        @DisplayName("认证对象为 null")
        void getCurrentUsername_NullAuthentication() {
            SecurityContext securityContext = createMockContext(null);
            SecurityContextHolder.setContext(securityContext);

            Optional<String> result = SecurityContextUtils.getCurrentUsername();

            assertFalse(result.isPresent());
        }

        @Test
        @DisplayName("未认证")
        void getCurrentUsername_NotAuthenticated() {
            Authentication authentication = mock(Authentication.class);
            when(authentication.isAuthenticated()).thenReturn(false);

            SecurityContext securityContext = createMockContext(authentication);
            SecurityContextHolder.setContext(securityContext);

            Optional<String> result = SecurityContextUtils.getCurrentUsername();

            assertFalse(result.isPresent());
        }

        @Test
        @DisplayName("未知类型的 Principal")
        void getCurrentUsername_UnknownPrincipalType() {
            Authentication authentication = mock(Authentication.class);
            when(authentication.isAuthenticated()).thenReturn(true);
            when(authentication.getPrincipal()).thenReturn(12345);

            SecurityContext securityContext = createMockContext(authentication);
            SecurityContextHolder.setContext(securityContext);

            Optional<String> result = SecurityContextUtils.getCurrentUsername();

            assertFalse(result.isPresent());
        }

        @Test
        @DisplayName("获取用户名时发生异常")
        void getCurrentUsername_ExceptionThrown() {
            SecurityContext securityContext = mock(SecurityContext.class);
            when(securityContext.getAuthentication()).thenThrow(new RuntimeException("Security error"));

            SecurityContextHolder.setContext(securityContext);

            Optional<String> result = SecurityContextUtils.getCurrentUsername();

            assertFalse(result.isPresent());
        }
    }

    @Nested
    @DisplayName("getCurrentUsernameOrDefault 测试")
    class GetCurrentUsernameOrDefaultTests {

        @Test
        @DisplayName("返回当前用户名")
        void getCurrentUsernameOrDefault_WithUser() {
            UserDetails userDetails = User.builder()
                    .username("currentuser")
                    .password("password")
                    .authorities(Collections.emptyList())
                    .build();

            Authentication authentication = mock(Authentication.class);
            when(authentication.isAuthenticated()).thenReturn(true);
            when(authentication.getPrincipal()).thenReturn(userDetails);

            SecurityContext securityContext = createMockContext(authentication);
            SecurityContextHolder.setContext(securityContext);

            String result = SecurityContextUtils.getCurrentUsernameOrDefault("anonymous");

            assertEquals("currentuser", result);
        }

        @Test
        @DisplayName("返回默认值 - 无认证")
        void getCurrentUsernameOrDefault_NoUser() {
            SecurityContext securityContext = createMockContext(null);
            SecurityContextHolder.setContext(securityContext);

            String result = SecurityContextUtils.getCurrentUsernameOrDefault("anonymous");

            assertEquals("anonymous", result);
        }

        @Test
        @DisplayName("用户名包含特殊字符")
        void getCurrentUsernameOrDefault_SpecialCharacters() {
            UserDetails userDetails = User.builder()
                    .username("user@example.com")
                    .password("password")
                    .authorities(Collections.emptyList())
                    .build();

            Authentication authentication = mock(Authentication.class);
            when(authentication.isAuthenticated()).thenReturn(true);
            when(authentication.getPrincipal()).thenReturn(userDetails);

            SecurityContext securityContext = createMockContext(authentication);
            SecurityContextHolder.setContext(securityContext);

            String result = SecurityContextUtils.getCurrentUsernameOrDefault("default");

            assertEquals("user@example.com", result);
        }
    }

    @Nested
    @DisplayName("getCurrentUserDetails 测试")
    class GetCurrentUserDetailsTests {

        @Test
        @DisplayName("成功获取 UserDetails")
        void getCurrentUserDetails_Success() {
            UserDetails userDetails = User.builder()
                    .username("testuser")
                    .password("password")
                    .authorities(Collections.emptyList())
                    .build();

            Authentication authentication = mock(Authentication.class);
            when(authentication.isAuthenticated()).thenReturn(true);
            when(authentication.getPrincipal()).thenReturn(userDetails);

            SecurityContext securityContext = createMockContext(authentication);
            SecurityContextHolder.setContext(securityContext);

            Optional<UserDetails> result = SecurityContextUtils.getCurrentUserDetails();

            assertTrue(result.isPresent());
            assertEquals("testuser", result.get().getUsername());
        }

        @Test
        @DisplayName("返回空 - 非 UserDetails 类型")
        void getCurrentUserDetails_NotUserDetails() {
            Authentication authentication = mock(Authentication.class);
            when(authentication.isAuthenticated()).thenReturn(true);
            when(authentication.getPrincipal()).thenReturn("string-principal");

            SecurityContext securityContext = createMockContext(authentication);
            SecurityContextHolder.setContext(securityContext);

            Optional<UserDetails> result = SecurityContextUtils.getCurrentUserDetails();

            assertFalse(result.isPresent());
        }

        @Test
        @DisplayName("返回空 - 未认证")
        void getCurrentUserDetails_NotAuthenticated() {
            Authentication authentication = mock(Authentication.class);
            when(authentication.isAuthenticated()).thenReturn(false);

            SecurityContext securityContext = createMockContext(authentication);
            SecurityContextHolder.setContext(securityContext);

            Optional<UserDetails> result = SecurityContextUtils.getCurrentUserDetails();

            assertFalse(result.isPresent());
        }

        @Test
        @DisplayName("获取 UserDetails 时发生异常")
        void getCurrentUserDetails_ExceptionThrown() {
            SecurityContext securityContext = mock(SecurityContext.class);
            when(securityContext.getAuthentication()).thenThrow(new RuntimeException("Context error"));

            SecurityContextHolder.setContext(securityContext);

            Optional<UserDetails> result = SecurityContextUtils.getCurrentUserDetails();

            assertFalse(result.isPresent());
        }
    }

    @Nested
    @DisplayName("isAuthenticated 测试")
    class IsAuthenticatedTests {

        @Test
        @DisplayName("已认证返回 true")
        void isAuthenticated_True() {
            Authentication authentication = mock(Authentication.class);
            when(authentication.isAuthenticated()).thenReturn(true);

            SecurityContext securityContext = createMockContext(authentication);
            SecurityContextHolder.setContext(securityContext);

            assertTrue(SecurityContextUtils.isAuthenticated());
        }

        @Test
        @DisplayName("未认证返回 false")
        void isAuthenticated_False() {
            Authentication authentication = mock(Authentication.class);
            when(authentication.isAuthenticated()).thenReturn(false);

            SecurityContext securityContext = createMockContext(authentication);
            SecurityContextHolder.setContext(securityContext);

            assertFalse(SecurityContextUtils.isAuthenticated());
        }

        @Test
        @DisplayName("认证对象为 null 返回 false")
        void isAuthenticated_NullAuthentication() {
            SecurityContext securityContext = createMockContext(null);
            SecurityContextHolder.setContext(securityContext);

            assertFalse(SecurityContextUtils.isAuthenticated());
        }

        @Test
        @DisplayName("发生异常返回 false")
        void isAuthenticated_ExceptionThrown() {
            SecurityContext securityContext = mock(SecurityContext.class);
            when(securityContext.getAuthentication()).thenThrow(new RuntimeException("Error"));

            SecurityContextHolder.setContext(securityContext);

            assertFalse(SecurityContextUtils.isAuthenticated());
        }
    }

    @Nested
    @DisplayName("边界值测试")
    class BoundaryTests {

        @Test
        @DisplayName("空用户名")
        void getCurrentUsername_EmptyUsername() {
            UserDetails userDetails = User.builder()
                    .username(" ")
                    .password("password")
                    .authorities(Collections.emptyList())
                    .build();

            Authentication authentication = mock(Authentication.class);
            when(authentication.isAuthenticated()).thenReturn(true);
            when(authentication.getPrincipal()).thenReturn(userDetails);

            SecurityContext securityContext = createMockContext(authentication);
            SecurityContextHolder.setContext(securityContext);

            Optional<String> result = SecurityContextUtils.getCurrentUsername();

            assertTrue(result.isPresent());
            assertEquals(" ", result.get());
        }

        @Test
        @DisplayName("匿名用户")
        void getCurrentUsername_AnonymousUser() {
            Authentication authentication = mock(Authentication.class);
            when(authentication.isAuthenticated()).thenReturn(true);
            when(authentication.getPrincipal()).thenReturn("anonymousUser");

            SecurityContext securityContext = createMockContext(authentication);
            SecurityContextHolder.setContext(securityContext);

            Optional<String> result = SecurityContextUtils.getCurrentUsername();

            assertTrue(result.isPresent());
            assertEquals("anonymousUser", result.get());
        }

        @Test
        @DisplayName("clearContext 后获取用户")
        void getCurrentUsername_AfterClearContext() {
            SecurityContextHolder.clearContext();

            Optional<String> result = SecurityContextUtils.getCurrentUsername();

            assertFalse(result.isPresent());
        }

        @Test
        @DisplayName("clearContext 后检查认证状态")
        void isAuthenticated_AfterClearContext() {
            SecurityContextHolder.clearContext();

            assertFalse(SecurityContextUtils.isAuthenticated());
        }
    }
}
