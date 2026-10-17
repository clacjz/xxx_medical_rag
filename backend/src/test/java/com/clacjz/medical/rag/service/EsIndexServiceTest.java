package com.clacjz.medical.rag.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.clacjz.medical.rag.config.ElasticsearchProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EsIndexService 单元测试")
class EsIndexServiceTest {

    @Mock
    private ElasticsearchClient esClient;

    @Mock
    private ElasticsearchProperties esProperties;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private MedicalTermNormalizer medicalTermNormalizer;

    @InjectMocks
    private EsIndexService esIndexService;

    @Test
    @DisplayName("getIndexName生成正确的索引名")
    void shouldGenerateCorrectIndexName() {
        String collectionId = "collection-123";

        String indexName = esIndexService.getIndexName(collectionId);

        assertEquals("knowledge_collection-123", indexName);
        assertTrue(indexName.startsWith("knowledge_"));
    }

    @Test
    @DisplayName("索引名前缀一致性")
    void shouldHaveConsistentPrefix() {
        String indexName = esIndexService.getIndexName("test-id");

        assertTrue(indexName.startsWith("knowledge_"));
        assertEquals("knowledge_test-id", indexName);
    }

    @Nested
    @DisplayName("CollectionIndex - 知识库索引操作测试")
    class CollectionIndexTests {

        @Mock
        private ElasticsearchIndicesClient indicesClient;

        @Test
        @DisplayName("deleteCollectionIndex 连接失败时抛出 IOException")
        void deleteCollectionIndex_throwsIOException_onConnectionFailure() throws Exception {
            // Given: ES 连接失败
            when(esClient.indices()).thenReturn(indicesClient);
            when(indicesClient.exists(any(ExistsRequest.class)))
                    .thenThrow(new IOException("Connection refused"));

            // When & Then
            assertThrows(IOException.class,
                    () -> esIndexService.deleteCollectionIndex("collection-123"),
                    "ES 连接失败时应抛出 IOException");
        }

        @Test
        @DisplayName("deleteCollectionIndex 索引不存在时直接返回")
        void deleteCollectionIndex_returns_whenIndexNotExists() throws Exception {
            // Given: 索引不存在
            when(esClient.indices()).thenReturn(indicesClient);
            when(indicesClient.exists(any(ExistsRequest.class)))
                    .thenReturn(new BooleanResponse(false));

            // When
            esIndexService.deleteCollectionIndex("collection-123");

            // Then: indices() 只被调用一次（exists），不应调用 delete
            verify(esClient, times(1)).indices();
        }

        @Test
        @DisplayName("deleteCollectionIndex 索引存在时成功删除")
        void deleteCollectionIndex_succeeds_onExistingIndex() throws Exception {
            // Given: 索引存在
            when(esClient.indices()).thenReturn(indicesClient);
            when(indicesClient.exists(any(ExistsRequest.class)))
                    .thenReturn(new BooleanResponse(true));

            // When
            esIndexService.deleteCollectionIndex("collection-123");

            // Then: indices() 被调用两次（exists + delete）
            verify(esClient, times(2)).indices();
        }

        @Test
        @DisplayName("indexExists 连接失败时抛出 IOException")
        void indexExists_throwsIOException_onConnectionFailure() throws Exception {
            // Given
            when(esClient.indices()).thenReturn(indicesClient);
            when(indicesClient.exists(any(ExistsRequest.class)))
                    .thenThrow(new IOException("Elasticsearch unavailable"));

            // When & Then
            assertThrows(IOException.class,
                    () -> esIndexService.indexExists("collection-123"),
                    "indexExists 在连接失败时应抛出 IOException");
        }

        @Test
        @DisplayName("indexExists 索引存在时返回 true")
        void indexExists_returnsTrue_whenIndexExists() throws Exception {
            // Given
            when(esClient.indices()).thenReturn(indicesClient);
            when(indicesClient.exists(any(ExistsRequest.class)))
                    .thenReturn(new BooleanResponse(true));

            // When
            boolean exists = esIndexService.indexExists("collection-123");

            // Then
            assertTrue(exists);
        }

        @Test
        @DisplayName("indexExists 索引不存在时返回 false")
        void indexExists_returnsFalse_whenIndexNotExists() throws Exception {
            // Given
            when(esClient.indices()).thenReturn(indicesClient);
            when(indicesClient.exists(any(ExistsRequest.class)))
                    .thenReturn(new BooleanResponse(false));

            // When
            boolean exists = esIndexService.indexExists("collection-123");

            // Then
            assertFalse(exists);
        }
    }
}
