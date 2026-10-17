package com.clacjz.medical.rag.controller;

import com.clacjz.medical.rag.constant.ErrorCode;
import com.clacjz.medical.rag.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DocumentController SSRF防护测试 - validateCallbackUrl")
class DocumentControllerSsrfTest {

    @InjectMocks
    private DocumentController documentController;

    private Method validateCallbackUrlMethod;

    @BeforeEach
    void setUp() throws Exception {
        validateCallbackUrlMethod = DocumentController.class.getDeclaredMethod(
                "validateCallbackUrl", String.class);
        validateCallbackUrlMethod.setAccessible(true);
    }

    private void invokeValidate(String url) throws Exception {
        validateCallbackUrlMethod.invoke(documentController, url);
    }

    private BizException invokeAndCatchBizException(String url) {
        try {
            invokeValidate(url);
            return null;
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof BizException biz) {
                return biz;
            }
            throw new RuntimeException("Unexpected exception type: " + cause.getClass().getName(), cause);
        } catch (Exception e) {
            throw new RuntimeException("Unexpected reflection error", e);
        }
    }

    @Test
    @DisplayName("合法HTTPS URL - 不应抛出异常")
    void validHttpsUrl_shouldNotThrow() throws Exception {
        assertDoesNotThrow(() -> invokeValidate("https://example.com/callback"),
                "合法HTTPS URL不应抛出异常");
    }

    @Test
    @DisplayName("合法HTTP URL - 不应抛出异常")
    void validHttpUrl_shouldNotThrow() throws Exception {
        assertDoesNotThrow(() -> invokeValidate("http://example.com/callback"),
                "合法HTTP URL不应抛出异常");
    }

    @Test
    @DisplayName("非法协议(ftp) - 应抛出BizException")
    void invalidScheme_ftp_shouldThrowBizException() {
        BizException ex = invokeAndCatchBizException("ftp://example.com/callback");
        assertNotNull(ex, "ftp协议应抛出BizException");
        assertEquals(ErrorCode.INVALID_PARAMETER, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("http/https"),
                "异常信息应提示仅支持http/https协议");
    }

    @Test
    @DisplayName("缺少主机名 - 应抛出BizException")
    void missingHost_shouldThrowBizException() {
        BizException ex = invokeAndCatchBizException("http:///path");
        assertNotNull(ex, "缺少主机名应抛出BizException");
        assertEquals(ErrorCode.INVALID_PARAMETER, ex.getErrorCode());
    }

    @Test
    @DisplayName("内网地址127.0.0.1 - 应抛出BizException")
    void loopbackAddress_shouldThrowBizException() {
        BizException ex = invokeAndCatchBizException("http://127.0.0.1/callback");
        assertNotNull(ex, "127.0.0.1回环地址应抛出BizException");
        assertEquals(ErrorCode.INVALID_PARAMETER, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("内网"),
                "异常信息应提示不允许内网地址");
    }

    @Test
    @DisplayName("内网地址10.x.x.x - 应抛出BizException")
    void siteLocalAddress_10_shouldThrowBizException() {
        BizException ex = invokeAndCatchBizException("http://10.0.0.1/callback");
        assertNotNull(ex, "10.x.x.x内网地址应抛出BizException");
        assertEquals(ErrorCode.INVALID_PARAMETER, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("内网"),
                "异常信息应提示不允许内网地址");
    }

    @Test
    @DisplayName("内网地址192.168.x.x - 应抛出BizException")
    void siteLocalAddress_192_shouldThrowBizException() {
        BizException ex = invokeAndCatchBizException("http://192.168.1.1/callback");
        assertNotNull(ex, "192.168.x.x内网地址应抛出BizException");
        assertEquals(ErrorCode.INVALID_PARAMETER, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("内网"),
                "异常信息应提示不允许内网地址");
    }

    @Test
    @DisplayName("无效URL格式 - 应抛出BizException")
    void invalidUrlFormat_shouldThrowBizException() {
        BizException ex = invokeAndCatchBizException("not-a-valid-url");
        assertNotNull(ex, "无效URL格式应抛出BizException");
        assertEquals(ErrorCode.INVALID_PARAMETER, ex.getErrorCode());
    }

    @Test
    @DisplayName("内网地址172.16.x.x - 应抛出BizException")
    void siteLocalAddress_172_shouldThrowBizException() {
        BizException ex = invokeAndCatchBizException("http://172.16.0.1/callback");
        assertNotNull(ex, "172.16.x.x内网地址应抛出BizException");
        assertEquals(ErrorCode.INVALID_PARAMETER, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("内网"),
                "异常信息应提示不允许内网地址");
    }

    @Test
    @DisplayName("localhost主机名 - 应抛出BizException")
    void localhostHostname_shouldThrowBizException() {
        BizException ex = invokeAndCatchBizException("http://localhost/callback");
        assertNotNull(ex, "localhost应抛出BizException");
        assertEquals(ErrorCode.INVALID_PARAMETER, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("内网"),
                "异常信息应提示不允许内网地址");
    }

    @Test
    @DisplayName("0.0.0.0通配地址 - 应抛出BizException")
    void anyLocalAddress_shouldThrowBizException() {
        BizException ex = invokeAndCatchBizException("http://0.0.0.0/callback");
        assertNotNull(ex, "0.0.0.0通配地址应抛出BizException");
        assertEquals(ErrorCode.INVALID_PARAMETER, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("内网"),
                "异常信息应提示不允许内网地址");
    }

    @Test
    @DisplayName("合法URL带端口号 - 不应抛出异常")
    void validUrlWithPort_shouldNotThrow() throws Exception {
        assertDoesNotThrow(() -> invokeValidate("https://example.com:8443/callback"),
                "合法URL带端口号不应抛出异常");
    }

    @Test
    @DisplayName("无协议scheme - 应抛出BizException")
    void noScheme_shouldThrowBizException() {
        BizException ex = invokeAndCatchBizException("example.com/callback");
        assertNotNull(ex, "无协议scheme应抛出BizException");
        assertEquals(ErrorCode.INVALID_PARAMETER, ex.getErrorCode());
    }
}
