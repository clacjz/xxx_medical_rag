package com.clacjz.medical.rag.util;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RequestUtils 单元测试")
class RequestUtilsTest {

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private RequestUtils requestUtils;

    private void setupRequestContext() {
        ServletRequestAttributes attributes = mock(ServletRequestAttributes.class);
        when(attributes.getRequest()).thenReturn(request);
        RequestContextHolder.setRequestAttributes(attributes);
        ReflectionTestUtils.setField(requestUtils, "trustProxyHeaders", true);
    }

    private void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Nested
    @DisplayName("getClientIp 测试")
    class GetClientIpTests {

        @Test
        @DisplayName("从 X-Forwarded-For 获取 IP")
        void getClientIp_FromXForwardedFor() {
            setupRequestContext();
            when(request.getHeader("X-Forwarded-For")).thenReturn("192.168.1.1, 10.0.0.1");

            String ip = requestUtils.getClientIp();

            assertEquals("192.168.1.1", ip);
        }

        @Test
        @DisplayName("从 X-Real-IP 获取 IP")
        void getClientIp_FromXRealIP() {
            setupRequestContext();
            when(request.getHeader("X-Forwarded-For")).thenReturn(null);
            when(request.getHeader("X-Real-IP")).thenReturn("10.0.0.5");

            String ip = requestUtils.getClientIp();

            assertEquals("10.0.0.5", ip);
        }

        @Test
        @DisplayName("从 Proxy-Client-IP 获取 IP")
        void getClientIp_FromProxyClientIP() {
            setupRequestContext();
            when(request.getHeader("X-Forwarded-For")).thenReturn(null);
            when(request.getHeader("X-Real-IP")).thenReturn(null);
            when(request.getHeader("Proxy-Client-IP")).thenReturn("172.16.0.1");

            String ip = requestUtils.getClientIp();

            assertEquals("172.16.0.1", ip);
        }

        @Test
        @DisplayName("从 HTTP_X_FORWARDED_FOR 获取 IP")
        void getClientIp_FromHttpXForwardedFor() {
            setupRequestContext();
            when(request.getHeader("X-Forwarded-For")).thenReturn(null);
            when(request.getHeader("X-Real-IP")).thenReturn(null);
            when(request.getHeader("Proxy-Client-IP")).thenReturn(null);
            when(request.getHeader("WL-Proxy-Client-IP")).thenReturn(null);
            when(request.getHeader("HTTP_X_FORWARDED_FOR")).thenReturn("203.0.113.50");

            String ip = requestUtils.getClientIp();

            assertEquals("203.0.113.50", ip);
        }

        @Test
        @DisplayName("所有代理头都为空时使用 RemoteAddr")
        void getClientIp_FromRemoteAddr() {
            setupRequestContext();
            when(request.getHeader(anyString())).thenReturn(null);
            when(request.getRemoteAddr()).thenReturn("127.0.0.1");

            String ip = requestUtils.getClientIp();

            assertEquals("127.0.0.1", ip);
        }

        @Test
        @DisplayName("X-Forwarded-For 为 unknown")
        void getClientIp_UnknownValue() {
            setupRequestContext();
            when(request.getHeader("X-Forwarded-For")).thenReturn("unknown");
            when(request.getHeader("X-Real-IP")).thenReturn(null);
            when(request.getHeader("Proxy-Client-IP")).thenReturn(null);
            when(request.getHeader("WL-Proxy-Client-IP")).thenReturn(null);
            when(request.getHeader("HTTP_X_FORWARDED_FOR")).thenReturn(null);
            when(request.getHeader("HTTP_X_FORWARDED")).thenReturn(null);
            when(request.getHeader("HTTP_X_CLUSTER_CLIENT_IP")).thenReturn(null);
            when(request.getHeader("HTTP_CLIENT_IP")).thenReturn(null);
            when(request.getHeader("HTTP_FORWARDED_FOR")).thenReturn(null);
            when(request.getHeader("HTTP_FORWARDED")).thenReturn(null);
            when(request.getHeader("HTTP_VIA")).thenReturn(null);
            when(request.getHeader("REMOTE_ADDR")).thenReturn(null);
            when(request.getRemoteAddr()).thenReturn("192.168.1.100");

            String ip = requestUtils.getClientIp();

            assertEquals("192.168.1.100", ip);
        }

        @Test
        @DisplayName("X-Forwarded-For 为空字符串")
        void getClientIp_EmptyString() {
            setupRequestContext();
            when(request.getHeader("X-Forwarded-For")).thenReturn("");
            when(request.getHeader("X-Real-IP")).thenReturn(null);
            when(request.getHeader("Proxy-Client-IP")).thenReturn(null);
            when(request.getHeader("WL-Proxy-Client-IP")).thenReturn(null);
            when(request.getHeader("HTTP_X_FORWARDED_FOR")).thenReturn(null);
            when(request.getHeader("HTTP_X_FORWARDED")).thenReturn(null);
            when(request.getHeader("HTTP_X_CLUSTER_CLIENT_IP")).thenReturn(null);
            when(request.getHeader("HTTP_CLIENT_IP")).thenReturn(null);
            when(request.getHeader("HTTP_FORWARDED_FOR")).thenReturn(null);
            when(request.getHeader("HTTP_FORWARDED")).thenReturn(null);
            when(request.getHeader("HTTP_VIA")).thenReturn(null);
            when(request.getHeader("REMOTE_ADDR")).thenReturn(null);
            when(request.getRemoteAddr()).thenReturn("10.0.0.2");

            String ip = requestUtils.getClientIp();

            assertEquals("10.0.0.2", ip);
        }

        @Test
        @DisplayName("单 IP 无逗号")
        void getClientIp_SingleIP() {
            setupRequestContext();
            when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.195");

            String ip = requestUtils.getClientIp();

            assertEquals("203.0.113.195", ip);
        }

        @Test
        @DisplayName("多个逗号分隔的 IP")
        void getClientIp_MultipleCommaSeparated() {
            setupRequestContext();
            when(request.getHeader("X-Forwarded-For"))
                    .thenReturn("203.0.113.195, 70.41.3.18, 150.172.238.178");

            String ip = requestUtils.getClientIp();

            assertEquals("203.0.113.195", ip);
        }

        @Test
        @DisplayName("X-Forwarded-For 包含空格")
        void getClientIp_WithSpaces() {
            setupRequestContext();
            when(request.getHeader("X-Forwarded-For"))
                    .thenReturn("  203.0.113.195  ,  70.41.3.18  ");

            String ip = requestUtils.getClientIp();

            assertEquals("203.0.113.195", ip);
        }

        @Test
        @DisplayName("没有请求上下文")
        void getClientIp_NoRequestContext() {
            clearRequestContext();

            String ip = requestUtils.getClientIp();

            assertEquals("unknown", ip);
        }
    }

    @Nested
    @DisplayName("getUserAgent 测试")
    class GetUserAgentTests {

        @Test
        @DisplayName("获取 User-Agent 成功")
        void getUserAgent_Success() {
            setupRequestContext();
            when(request.getHeader("User-Agent"))
                    .thenReturn("Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0");

            String userAgent = requestUtils.getUserAgent();

            assertEquals("Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0", userAgent);
        }

        @Test
        @DisplayName("User-Agent 为空")
        void getUserAgent_Empty() {
            setupRequestContext();
            when(request.getHeader("User-Agent")).thenReturn(null);

            String userAgent = requestUtils.getUserAgent();

            assertEquals("unknown", userAgent);
        }

        @Test
        @DisplayName("User-Agent 为 null")
        void getUserAgent_Null() {
            setupRequestContext();
            when(request.getHeader("User-Agent")).thenReturn(null);

            String userAgent = requestUtils.getUserAgent();

            assertEquals("unknown", userAgent);
        }

        @Test
        @DisplayName("没有请求上下文")
        void getUserAgent_NoRequestContext() {
            clearRequestContext();

            String userAgent = requestUtils.getUserAgent();

            assertEquals("unknown", userAgent);
        }
    }

    @Nested
    @DisplayName("getCurrentRequest 测试")
    class GetCurrentRequestTests {

        @Test
        @DisplayName("获取当前请求成功")
        void getCurrentRequest_Success() {
            setupRequestContext();

            HttpServletRequest result = requestUtils.getCurrentRequest();

            assertNotNull(result);
            assertEquals(request, result);
        }

        @Test
        @DisplayName("获取当前请求 - 无上下文")
        void getCurrentRequest_NoContext() {
            clearRequestContext();

            HttpServletRequest result = requestUtils.getCurrentRequest();

            assertNull(result);
        }
    }

    @Nested
    @DisplayName("边界值测试")
    class BoundaryTests {

        @Test
        @DisplayName("所有 HTTP 头都为 null")
        void getClientIp_AllHeadersNull() {
            setupRequestContext();
            when(request.getHeader(anyString())).thenReturn(null);
            when(request.getRemoteAddr()).thenReturn("::1");

            String ip = requestUtils.getClientIp();

            assertEquals("::1", ip);
        }

        @Test
        @DisplayName("特殊格式的 X-Forwarded-For")
        void getClientIp_SpecialFormat() {
            setupRequestContext();
            when(request.getHeader("X-Forwarded-For")).thenReturn("192.168.0.1");

            String ip = requestUtils.getClientIp();

            assertEquals("192.168.0.1", ip);
        }
    }
}
