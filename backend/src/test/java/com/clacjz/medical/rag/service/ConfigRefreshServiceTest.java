package com.clacjz.medical.rag.service;

import com.clacjz.medical.rag.config.OcrProperties;
import com.clacjz.medical.rag.config.RAGProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConfigRefreshService 单元测试")
class ConfigRefreshServiceTest {

    @Mock
    private RAGProperties ragProperties;

    @Mock
    private OcrProperties ocrProperties;

    @InjectMocks
    private ConfigRefreshService configRefreshService;

    @Nested
    @DisplayName("refreshConfig - 刷新配置")
    class RefreshConfigTests {

        @Test
        @DisplayName("刷新embedding配置")
        void shouldRefreshEmbeddingConfig() {
            RAGProperties.EmbeddingConfig embedding = new RAGProperties.EmbeddingConfig();
            when(ragProperties.getEmbedding()).thenReturn(embedding);

            configRefreshService.refreshConfig("embedding", "model", "text-embedding-3");

            assertEquals("text-embedding-3", embedding.getModel());
        }

        @Test
        @DisplayName("刷新embedding dimension配置")
        void shouldRefreshEmbeddingDimension() {
            RAGProperties.EmbeddingConfig embedding = new RAGProperties.EmbeddingConfig();
            when(ragProperties.getEmbedding()).thenReturn(embedding);

            configRefreshService.refreshConfig("embedding", "dimension", "1024");

            assertEquals(1024, embedding.getDimension());
        }

        @Test
        @DisplayName("刷新llm配置temperature")
        void shouldRefreshLlmTemperature() {
            RAGProperties.LLMConfig llm = new RAGProperties.LLMConfig();
            when(ragProperties.getLlm()).thenReturn(llm);

            configRefreshService.refreshConfig("llm", "temperature", "0.7");

            assertEquals(0.7, llm.getTemperature(), 0.01);
        }

        @Test
        @DisplayName("刷新llm配置maxTokens")
        void shouldRefreshLlmMaxTokens() {
            RAGProperties.LLMConfig llm = new RAGProperties.LLMConfig();
            when(ragProperties.getLlm()).thenReturn(llm);

            configRefreshService.refreshConfig("llm", "maxTokens", "4096");

            assertEquals(4096, llm.getMaxTokens());
        }

        @Test
        @DisplayName("刷新qdrant host配置")
        void shouldRefreshQdrantHost() {
            RAGProperties.QdrantConfig qdrant = new RAGProperties.QdrantConfig();
            when(ragProperties.getQdrant()).thenReturn(qdrant);

            configRefreshService.refreshConfig("qdrant", "host", "localhost");

            assertEquals("localhost", qdrant.getHost());
        }

        @Test
        @DisplayName("刷新minio配置")
        void shouldRefreshMinioConfig() {
            RAGProperties.MinIOConfig minio = new RAGProperties.MinIOConfig();
            when(ragProperties.getMinio()).thenReturn(minio);

            configRefreshService.refreshConfig("minio", "host", "minio-server");

            assertEquals("minio-server", minio.getHost());
        }

        @Test
        @DisplayName("刷新ocr enabled配置")
        void shouldRefreshOcrEnabled() {
            configRefreshService.refreshConfig("ocr", "enabled", "true");

            verify(ocrProperties).setEnabled(true);
        }

        @Test
        @DisplayName("刷新ocr timeout配置")
        void shouldRefreshOcrTimeout() {
            configRefreshService.refreshConfig("ocr", "timeout", "30000");

            verify(ocrProperties).setTimeout(30000);
        }

        @Test
        @DisplayName("刷新etcd配置")
        void shouldRefreshEtcdConfig() {
            RAGProperties.EtcdConfig etcd = new RAGProperties.EtcdConfig();
            when(ragProperties.getEtcd()).thenReturn(etcd);

            configRefreshService.refreshConfig("etcd", "host", "etcd-server");

            assertEquals("etcd-server", etcd.getHost());
        }

        @Test
        @DisplayName("刷新elasticsearch配置")
        void shouldRefreshElasticsearchConfig() {
            RAGProperties.ElasticsearchConfig es = new RAGProperties.ElasticsearchConfig();
            when(ragProperties.getElasticsearch()).thenReturn(es);

            configRefreshService.refreshConfig("elasticsearch", "host", "es-server");

            assertEquals("es-server", es.getHost());
        }

        @Test
        @DisplayName("未知分类不抛异常")
        void shouldNotThrowForUnknownCategory() {
            assertDoesNotThrow(() ->
                    configRefreshService.refreshConfig("unknown", "key", "value"));
        }

        @Test
        @DisplayName("未知embedding key不抛异常")
        void shouldNotThrowForUnknownEmbeddingKey() {
            RAGProperties.EmbeddingConfig embedding = new RAGProperties.EmbeddingConfig();
            when(ragProperties.getEmbedding()).thenReturn(embedding);

            assertDoesNotThrow(() ->
                    configRefreshService.refreshConfig("embedding", "unknownKey", "value"));
        }

        @Test
        @DisplayName("未知llm key不抛异常")
        void shouldNotThrowForUnknownLlmKey() {
            RAGProperties.LLMConfig llm = new RAGProperties.LLMConfig();
            when(ragProperties.getLlm()).thenReturn(llm);

            assertDoesNotThrow(() ->
                    configRefreshService.refreshConfig("llm", "unknownKey", "value"));
        }
    }
}
