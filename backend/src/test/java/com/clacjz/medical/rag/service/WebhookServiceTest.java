package com.clacjz.medical.rag.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("WebhookService 单元测试")
class WebhookServiceTest {

    @Mock
    private RestTemplate restTemplate;

    private WebhookService webhookService;

    @BeforeEach
    void setUp() {
        webhookService = new WebhookService(restTemplate);
    }

    @Nested
    @DisplayName("URL 为空/空白的快速返回")
    class EmptyUrlTests {

        @Test
        @DisplayName("null URL 不发送请求")
        void shouldSkipWhenUrlIsNull() throws Exception {
            CompletableFuture<Void> result = webhookService.sendCallback(
                    null, "doc-1", true, 5, null);

            assertNotNull(result);
            assertNull(result.get());
            verifyNoInteractions(restTemplate);
        }

        @Test
        @DisplayName("空白 URL 不发送请求")
        void shouldSkipWhenUrlIsBlank() throws Exception {
            CompletableFuture<Void> result = webhookService.sendCallback(
                    "   ", "doc-1", true, 5, null);

            assertNotNull(result);
            assertNull(result.get());
            verifyNoInteractions(restTemplate);
        }

        @Test
        @DisplayName("空字符串 URL 不发送请求")
        void shouldSkipWhenUrlIsEmpty() throws Exception {
            CompletableFuture<Void> result = webhookService.sendCallback(
                    "", "doc-1", false, null, "err");

            assertNotNull(result);
            assertNull(result.get());
            verifyNoInteractions(restTemplate);
        }
    }

    @Nested
    @DisplayName("成功发送回调")
    class SuccessTests {

        @Test
        @DisplayName("成功回调体包含 storedCount 不含 errorMessage")
        void shouldSendSuccessPayload() {
            when(restTemplate.postForEntity(eq("https://callback.example.com/hook"), any(Map.class), eq(String.class)))
                    .thenReturn(ResponseEntity.ok("ok"));

            webhookService.sendCallback("https://callback.example.com/hook", "doc-1", true, 42, null);

            verify(restTemplate).postForEntity(eq("https://callback.example.com/hook"), any(Map.class), eq(String.class));
        }

        @Test
        @DisplayName("失败回调体包含 errorMessage 不含 storedCount")
        void shouldSendFailurePayload() {
            when(restTemplate.postForEntity(eq("https://callback.example.com/hook"), any(Map.class), eq(String.class)))
                    .thenReturn(ResponseEntity.ok("ok"));

            webhookService.sendCallback("https://callback.example.com/hook", "doc-2", false, null, "OCR 失败");

            verify(restTemplate).postForEntity(eq("https://callback.example.com/hook"), any(Map.class), eq(String.class));
        }

        @Test
        @DisplayName("返回的 CompletableFuture 立即完成")
        void shouldReturnCompletedFuture() throws Exception {
            when(restTemplate.postForEntity(any(String.class), any(Map.class), eq(String.class)))
                    .thenReturn(ResponseEntity.ok("ok"));

            CompletableFuture<Void> result = webhookService.sendCallback(
                    "https://callback.example.com/hook", "doc-1", true, 10, null);

            assertTrue(result.isDone());
            assertNull(result.get());
        }
    }

    @Nested
    @DisplayName("回调失败容错")
    class FailureTests {

        @Test
        @DisplayName("RestTemplate 抛异常时不向上传播")
        void shouldNotPropagateRestTemplateException() {
            when(restTemplate.postForEntity(any(String.class), any(Map.class), eq(String.class)))
                    .thenThrow(new RestClientException("Connection refused"));

            // 不应抛异常
            assertDoesNotThrow(() -> webhookService.sendCallback(
                    "https://callback.example.com/hook", "doc-1", true, 5, null));
        }

        @Test
        @DisplayName("网络异常时仍返回完成的 Future")
        void shouldStillReturnCompletedFutureOnError() throws Exception {
            when(restTemplate.postForEntity(any(String.class), any(Map.class), eq(String.class)))
                    .thenThrow(new RuntimeException("Network down"));

            CompletableFuture<Void> result = webhookService.sendCallback(
                    "https://callback.example.com/hook", "doc-1", false, null, "err");

            assertTrue(result.isDone());
            assertNull(result.get());
        }
    }

    @Nested
    @DisplayName("SSRF 防护测试")
    class SsrfProtectionTests {

        @Test
        @DisplayName("默认构造函数创建SSRF安全RestTemplate")
        void defaultConstructor_createsSsrfSafeRestTemplate() {
            WebhookService service = new WebhookService();

            RestTemplate rt = service.getRestTemplate();
            assertNotNull(rt);
            assertInstanceOf(SimpleClientHttpRequestFactory.class, rt.getRequestFactory());
        }

        @Test
        @DisplayName("测试构造函数接受mock RestTemplate")
        void testConstructor_acceptsMockRestTemplate() {
            WebhookService service = new WebhookService(restTemplate);

            assertSame(restTemplate, service.getRestTemplate());
        }

        @Test
        @DisplayName("SSRF安全RestTemplate拒绝localhost回调")
        void ssrfSafeRestTemplate_rejectsLocalhost() throws Exception {
            WebhookService service = new WebhookService();

            SimpleClientHttpRequestFactory factory = (SimpleClientHttpRequestFactory) service.getRestTemplate().getRequestFactory();
            IOException exception = assertThrows(IOException.class, () ->
                    factory.createRequest(new java.net.URI("http://127.0.0.1/"), org.springframework.http.HttpMethod.POST));
            assertTrue(exception.getMessage().contains("SSRF"),
                    "Expected IOException message to contain 'SSRF' but was: " + exception.getMessage());
        }

        @Test
        @DisplayName("SSRF安全RestTemplate拒绝内网IP回调")
        void ssrfSafeRestTemplate_rejectsInternalIp() throws Exception {
            WebhookService service = new WebhookService();

            SimpleClientHttpRequestFactory factory = (SimpleClientHttpRequestFactory) service.getRestTemplate().getRequestFactory();
            IOException exception = assertThrows(IOException.class, () ->
                    factory.createRequest(new java.net.URI("http://10.0.0.1/"), org.springframework.http.HttpMethod.POST));
            assertTrue(exception.getMessage().contains("SSRF"),
                    "Expected IOException message to contain 'SSRF' but was: " + exception.getMessage());
        }
    }
}
