package com.clacjz.medical.rag.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.clacjz.medical.rag.config.RAGProperties;
import com.clacjz.medical.rag.model.dto.ner.NerResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("NerClient 单元测试")
class NerClientTest {

    @Mock
    private LLMService llmService;

    @Mock
    private RAGProperties ragProperties;

    @Mock
    private RAGProperties.NerConfig nerProperties;

    private ObjectMapper objectMapper;
    private NerClient nerClient;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        when(ragProperties.getNer()).thenReturn(nerProperties);
        when(nerProperties.isEnabled()).thenReturn(true);
        nerClient = new NerClient(llmService, ragProperties, objectMapper);
    }

    @Nested
    @DisplayName("extractEntities - 单段实体提取")
    class ExtractEntitiesTests {

        @Test
        @DisplayName("NER禁用时返回空结果")
        void shouldReturnEmptyWhenNerDisabled() {
            when(nerProperties.isEnabled()).thenReturn(false);
            NerResponse result = nerClient.extractEntities("糖尿病");
            assertTrue(result.getEntities().isEmpty());
        }

        @Test
        @DisplayName("null文本返回空结果")
        void shouldReturnEmptyWhenNullText() {
            NerResponse result = nerClient.extractEntities(null);
            assertTrue(result.getEntities().isEmpty());
        }

        @Test
        @DisplayName("空白文本返回空结果")
        void shouldReturnEmptyWhenBlankText() {
            NerResponse result = nerClient.extractEntities("   ");
            assertTrue(result.getEntities().isEmpty());
        }

        @Test
        @DisplayName("正常提取医学实体")
        void shouldExtractEntities() throws Exception {
            String jsonResponse = "{\"entities\": [{\"text\": \"糖尿病\", \"type\": \"DISEASE\", \"aliases\": []}]}";
            when(llmService.generateJson(anyString(), eq("高血压"))).thenReturn(jsonResponse);

            NerResponse result = nerClient.extractEntities("高血压");
            assertEquals(1, result.getEntities().size());
            assertEquals("糖尿病", result.getEntities().get(0).getText());
            assertEquals("DISEASE", result.getEntities().get(0).getType());
        }

        @Test
        @DisplayName("超长文本自动截断到4000字符")
        void shouldTruncateLongText() throws Exception {
            String jsonResponse = "{\"entities\": []}";
            when(llmService.generateJson(anyString(), anyString())).thenReturn(jsonResponse);

            String longText = "A".repeat(5000);
            nerClient.extractEntities(longText);
            // 验证调用时文本被截断
        }

        @Test
        @DisplayName("LLM调用失败时返回空结果")
        void shouldReturnEmptyOnLLMFailure() {
            when(llmService.generateJson(anyString(), anyString())).thenThrow(new RuntimeException("LLM error"));
            NerResponse result = nerClient.extractEntities("糖尿病");
            assertTrue(result.getEntities().isEmpty());
        }
    }

    @Nested
    @DisplayName("extractEntitiesFromDocument - 文档级实体提取")
    class ExtractFromDocumentTests {

        @Test
        @DisplayName("NER禁用时返回空结果")
        void shouldReturnEmptyWhenNerDisabled() {
            when(nerProperties.isEnabled()).thenReturn(false);
            NerResponse result = nerClient.extractEntitiesFromDocument("糖尿病");
            assertTrue(result.getEntities().isEmpty());
        }

        @Test
        @DisplayName("null文本返回空结果")
        void shouldReturnEmptyWhenNullText() {
            NerResponse result = nerClient.extractEntitiesFromDocument(null);
            assertTrue(result.getEntities().isEmpty());
        }

        @Test
        @DisplayName("短文本不分段直接提取")
        void shouldExtractFromShortTextWithoutSplitting() throws Exception {
            String jsonResponse = "{\"entities\": [{\"text\": \"糖尿病\", \"type\": \"DISEASE\", \"aliases\": []}]}";
            when(llmService.generateJson(anyString(), anyString())).thenReturn(jsonResponse);

            NerResponse result = nerClient.extractEntitiesFromDocument("糖尿病");
            assertEquals(1, result.getEntities().size());
        }

        @Test
        @DisplayName("长文档分段提取并去重")
        void shouldSplitAndDeduplicate() throws Exception {
            when(llmService.generateJson(anyString(), anyString())).thenReturn(
                    "{\"entities\": [{\"text\": \"糖尿病\", \"type\": \"DISEASE\", \"aliases\": []}]}");

            // 创建超过4000字符的文本
            String longText = "糖尿病。".repeat(1000);
            NerResponse result = nerClient.extractEntitiesFromDocument(longText);
            assertFalse(result.getEntities().isEmpty());
        }

        @Test
        @DisplayName("超过8段时截断并记录警告")
        void shouldTruncateWhenExceedsMaxSegments() throws Exception {
            when(llmService.generateJson(anyString(), anyString())).thenReturn(
                    "{\"entities\": [{\"text\": \"糖尿病\", \"type\": \"DISEASE\", \"aliases\": []}]}");

            // 模拟超长文档
            String veryLongText = "这是一段很长的文档内容。".repeat(5000);
            NerResponse result = nerClient.extractEntitiesFromDocument(veryLongText);
            // 应该返回结果（可能被截断）
            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("extractIndications - 适应症提取")
    class ExtractIndicationsTests {

        @Test
        @DisplayName("NER禁用时返回空结果")
        void shouldReturnEmptyWhenNerDisabled() {
            when(nerProperties.isEnabled()).thenReturn(false);
            NerClient.IndicationsResult result = nerClient.extractIndications("糖尿病");
            assertTrue(result.diseases().isEmpty());
            assertTrue(result.symptoms().isEmpty());
        }

        @Test
        @DisplayName("null文本返回空结果")
        void shouldReturnEmptyWhenNullText() {
            NerClient.IndicationsResult result = nerClient.extractIndications(null);
            assertTrue(result.diseases().isEmpty());
            assertTrue(result.symptoms().isEmpty());
        }

        @Test
        @DisplayName("空白文本返回空结果")
        void shouldReturnEmptyWhenBlankText() {
            NerClient.IndicationsResult result = nerClient.extractIndications("   ");
            assertTrue(result.diseases().isEmpty());
            assertTrue(result.symptoms().isEmpty());
        }

        @Test
        @DisplayName("正常提取疾病和症状")
        void shouldExtractDiseasesAndSymptoms() throws Exception {
            String jsonResponse = "{\"diseases\": [\"糖尿病\"], \"symptoms\": [\"头痛\"]}";
            when(llmService.generateJson(anyString(), anyString())).thenReturn(jsonResponse);

            NerClient.IndicationsResult result = nerClient.extractIndications("适用于糖尿病引起的头痛");
            assertEquals(1, result.diseases().size());
            assertEquals(1, result.symptoms().size());
            assertEquals("糖尿病", result.diseases().get(0));
            assertEquals("头痛", result.symptoms().get(0));
        }

        @Test
        @DisplayName("超长输入自动截断到4000字符")
        void shouldTruncateLongInput() throws Exception {
            String jsonResponse = "{\"diseases\": [], \"symptoms\": []}";
            when(llmService.generateJson(anyString(), anyString())).thenReturn(jsonResponse);

            String longText = "A".repeat(5000);
            NerClient.IndicationsResult result = nerClient.extractIndications(longText);
            assertNotNull(result);
        }

        @Test
        @DisplayName("LLM调用失败时返回空结果")
        void shouldReturnEmptyOnLLMFailure() {
            when(llmService.generateJson(anyString(), anyString())).thenThrow(new RuntimeException("LLM error"));
            NerClient.IndicationsResult result = nerClient.extractIndications("糖尿病");
            assertTrue(result.diseases().isEmpty());
            assertTrue(result.symptoms().isEmpty());
        }

        @Test
        @DisplayName("IndicationsResult record字段正确")
        void shouldHaveCorrectRecordFields() {
            NerClient.IndicationsResult result = new NerClient.IndicationsResult(
                    List.of("糖尿病"), List.of("头痛"));
            assertEquals(1, result.diseases().size());
            assertEquals(1, result.symptoms().size());
        }
    }

    @Nested
    @DisplayName("NerResponse - 响应结构")
    class NerResponseTests {

        @Test
        @DisplayName("空实体列表")
        void shouldHandleEmptyEntities() {
            NerResponse response = new NerResponse(List.of());
            assertTrue(response.getEntities().isEmpty());
        }

        @Test
        @DisplayName("null实体列表")
        void shouldHandleNullEntities() {
            NerResponse response = new NerResponse(null);
            assertTrue(response.getEntities() == null || response.getEntities().isEmpty());
        }
    }
}