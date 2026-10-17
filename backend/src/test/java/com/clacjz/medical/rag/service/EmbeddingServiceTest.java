package com.clacjz.medical.rag.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.clacjz.medical.rag.config.RAGProperties;
import com.clacjz.medical.rag.exception.AiServiceException;
import com.clacjz.medical.rag.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmbeddingService 单元测试")
class EmbeddingServiceTest {

    @Mock
    private RAGProperties ragProperties;

    @Mock
    private RestTemplate restTemplate;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private EmbeddingService embeddingService;

    private RAGProperties.EmbeddingConfig embeddingConfig;

    @BeforeEach
    void setUp() {
        embeddingConfig = new RAGProperties.EmbeddingConfig();
        embeddingConfig.setUrl("http://localhost:8860");
        embeddingConfig.setModel("bge-large-zh");
        embeddingConfig.setDimension(1024);

        lenient().when(ragProperties.getEmbedding()).thenReturn(embeddingConfig);
    }

    @Nested
    @DisplayName("embed 方法测试")
    class EmbedTests {

        @Test
        @DisplayName("embed 成功 - 首次调用")
        void embed_Success_FirstCall() throws Exception {
            String text = "这是一个测试文本";

            String responseBody = "{\"data\":[{\"embedding\":[0.1, 0.2, 0.3, 0.4]}]}";
            ResponseEntity<String> response = new ResponseEntity<>(responseBody, HttpStatus.OK);

            when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                    .thenReturn(response);

            float[] result = embeddingService.embed(text);

            assertNotNull(result);
            assertEquals(4, result.length);
            assertEquals(0.1f, result[0], 0.001);
            assertEquals(0.4f, result[3], 0.001);
            verify(restTemplate).postForEntity(
                    eq("http://localhost:8860/api/embedding/embed"),
                    any(),
                    eq(String.class)
            );
        }

        @Test
        @DisplayName("embed 成功 - 使用缓存")
        void embed_Success_UsesCache() throws Exception {
            String text = "缓存测试文本";

            String responseBody = "{\"data\":[{\"embedding\":[0.5, 0.6, 0.7, 0.8]}]}";
            ResponseEntity<String> response = new ResponseEntity<>(responseBody, HttpStatus.OK);

            when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                    .thenReturn(response);

            float[] firstResult = embeddingService.embed(text);
            verify(restTemplate, times(1)).postForEntity(anyString(), any(), eq(String.class));

            float[] secondResult = embeddingService.embed(text);
            assertArrayEquals(firstResult, secondResult);
            verify(restTemplate, times(1)).postForEntity(anyString(), any(), eq(String.class));
        }

        @Test
        @DisplayName("embed 成功 - 长文本使用前100字符作为缓存键")
        void embed_Success_LongTextCacheKey() throws Exception {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 150; i++) {
                sb.append("x");
            }
            String longText = sb.toString();
            String expectedCacheKey = longText.substring(0, 100);

            String responseBody = "{\"data\":[{\"embedding\":[0.1, 0.2]}]}";
            ResponseEntity<String> response = new ResponseEntity<>(responseBody, HttpStatus.OK);

            when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                    .thenReturn(response);

            float[] result1 = embeddingService.embed(longText);
            float[] result2 = embeddingService.embed(longText);

            assertArrayEquals(result1, result2);
            verify(restTemplate, times(1)).postForEntity(anyString(), any(), eq(String.class));
        }

        @Test
        @DisplayName("embed 成功 - 不同长文本有不同缓存键")
        void embed_Success_DifferentLongTexts() throws Exception {
            StringBuilder sb1 = new StringBuilder();
            StringBuilder sb2 = new StringBuilder();
            for (int i = 0; i < 150; i++) {
                sb1.append("a");
                sb2.append("b");
            }
            String longText1 = sb1.toString();
            String longText2 = sb2.toString();

            String responseBody1 = "{\"data\":[{\"embedding\":[0.1, 0.2]}]}";
            String responseBody2 = "{\"data\":[{\"embedding\":[0.3, 0.4]}]}";

            when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                    .thenReturn(new ResponseEntity<>(responseBody1, HttpStatus.OK))
                    .thenReturn(new ResponseEntity<>(responseBody2, HttpStatus.OK));

            float[] result1 = embeddingService.embed(longText1);
            float[] result2 = embeddingService.embed(longText2);

            assertEquals(0.1f, result1[0], 0.001);
            assertEquals(0.3f, result2[0], 0.001);
            verify(restTemplate, times(2)).postForEntity(anyString(), any(), eq(String.class));
        }

        @Test
        @DisplayName("embed 失败 - HTTP 状态码非 2xx")
        void embed_Failure_BadStatus() {
            String text = "测试文本";

            ResponseEntity<String> response = new ResponseEntity<>("Error", HttpStatus.INTERNAL_SERVER_ERROR);
            when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                    .thenReturn(response);

            AiServiceException exception = assertThrows(AiServiceException.class,
                    () -> embeddingService.embed(text));

            assertTrue(exception.getMessage().contains("Embedding request failed"));
        }

        @Test
        @DisplayName("embed 失败 - 响应中没有 data 字段")
        void embed_Failure_NoDataField() throws Exception {
            String text = "测试文本";

            String responseBody = "{\"error\":\"something went wrong\"}";
            ResponseEntity<String> response = new ResponseEntity<>(responseBody, HttpStatus.OK);

            when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                    .thenReturn(response);

            AiServiceException exception = assertThrows(AiServiceException.class,
                    () -> embeddingService.embed(text));

            assertTrue(exception.getMessage().contains("Invalid embedding response"));
        }

        @Test
        @DisplayName("embed 失败 - data 数组为空")
        void embed_Failure_EmptyDataArray() throws Exception {
            String text = "测试文本";

            String responseBody = "{\"data\":[]}";
            ResponseEntity<String> response = new ResponseEntity<>(responseBody, HttpStatus.OK);

            when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                    .thenReturn(response);

            AiServiceException exception = assertThrows(AiServiceException.class,
                    () -> embeddingService.embed(text));

            assertTrue(exception.getMessage().contains("Invalid embedding response"));
        }

        @Test
        @DisplayName("embed 失败 - data 数组中第一个元素没有 embedding")
        void embed_Failure_NoEmbeddingInData() throws Exception {
            String text = "测试文本";

            String responseBody = "{\"data\":[{\"other\":\"field\"}]}";
            ResponseEntity<String> response = new ResponseEntity<>(responseBody, HttpStatus.OK);

            when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                    .thenReturn(response);

            AiServiceException exception = assertThrows(AiServiceException.class,
                    () -> embeddingService.embed(text));

            assertTrue(exception.getMessage().contains("Invalid embedding response"));
        }

        @Test
        @DisplayName("embed 失败 - 网络错误")
        void embed_Failure_NetworkError() {
            String text = "测试文本";

            when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                    .thenThrow(new RestClientException("Connection refused"));

            AiServiceException exception = assertThrows(AiServiceException.class,
                    () -> embeddingService.embed(text));

            assertTrue(exception.getMessage().contains("Embedding service error"));
        }
    }

    @Nested
    @DisplayName("getDimension 方法测试")
    class GetDimensionTests {

        @Test
        @DisplayName("getDimension 返回配置的值")
        void getDimension_ReturnsConfigValue() {
            int dimension = embeddingService.getDimension();

            assertEquals(1024, dimension);
        }
    }

    @Nested
    @DisplayName("clearCache 方法测试")
    class ClearCacheTests {

        @Test
        @DisplayName("clearCache 成功")
        void clearCache_Success() throws Exception {
            String text1 = "文本1";
            String text2 = "文本2";

            String responseBody = "{\"data\":[{\"embedding\":[0.1, 0.2]}]}";
            ResponseEntity<String> response = new ResponseEntity<>(responseBody, HttpStatus.OK);

            when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                    .thenReturn(response);

            embeddingService.embed(text1);
            embeddingService.clearCache();
            embeddingService.embed(text2);

            verify(restTemplate, times(2)).postForEntity(anyString(), any(), eq(String.class));
        }
    }

    @Nested
    @DisplayName("请求参数验证测试")
    class RequestValidationTests {

        @Test
        @DisplayName("验证请求体包含正确参数")
        void verifyRequestBody_ContainsCorrectParams() throws Exception {
            String text = "测试文本";

            String responseBody = "{\"data\":[{\"embedding\":[0.1]}]}";
            ResponseEntity<String> response = new ResponseEntity<>(responseBody, HttpStatus.OK);

            when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                    .thenReturn(response);

            embeddingService.embed(text);

            verify(restTemplate).postForEntity(
                    eq("http://localhost:8860/api/embedding/embed"),
                    any(),
                    eq(String.class)
            );
        }

        @Test
        @DisplayName("验证空文本可以处理")
        void verifyRequestBody_EmptyText() throws Exception {
            String text = "";

            String responseBody = "{\"data\":[{\"embedding\":[0.1]}]}";
            ResponseEntity<String> response = new ResponseEntity<>(responseBody, HttpStatus.OK);

            when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                    .thenReturn(response);

            float[] result = embeddingService.embed(text);

            assertNotNull(result);
            assertEquals(1, result.length);
        }

        @Test
        @DisplayName("验证多维向量正确解析")
        void verifyMultiDimensionVector() throws Exception {
            String text = "测试";
            float[] expected = new float[100];
            for (int i = 0; i < 100; i++) {
                expected[i] = (float) (i * 0.01);
            }

            StringBuilder sb = new StringBuilder("{\"data\":[{\"embedding\":[");
            for (int i = 0; i < 100; i++) {
                sb.append(expected[i]);
                if (i < 99) sb.append(",");
            }
            sb.append("]}]}");
            String responseBody = sb.toString();

            ResponseEntity<String> response = new ResponseEntity<>(responseBody, HttpStatus.OK);
            when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                    .thenReturn(response);

            float[] result = embeddingService.embed(text);

            assertEquals(100, result.length);
            for (int i = 0; i < 100; i++) {
                assertEquals(expected[i], result[i], 0.001);
            }
        }
    }
}
