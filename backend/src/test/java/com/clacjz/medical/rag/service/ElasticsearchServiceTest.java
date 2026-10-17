package com.clacjz.medical.rag.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.clacjz.medical.rag.config.ElasticsearchProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ElasticsearchServiceTest {

    @Mock
    private ElasticsearchClient client;

    @Mock
    private ElasticsearchIndicesClient indicesClient;

    @Mock
    private ElasticsearchProperties properties;

    private ObjectMapper objectMapper;
    private ElasticsearchService elasticsearchService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        properties = mock(ElasticsearchProperties.class);
        when(properties.getIndexPrefix()).thenReturn("test_");

        elasticsearchService = new ElasticsearchService(client, properties, objectMapper);
    }

    @Test
    void testGetFullIndexName() {
        String fullName = elasticsearchService.getFullIndexName("documents");
        assertEquals("test_documents", fullName);
    }

    @Test
    void testIndexExists_True() throws IOException {
        when(properties.getIndexPrefix()).thenReturn("test_");
        when(client.indices()).thenReturn(indicesClient);

        co.elastic.clients.transport.endpoints.BooleanResponse existsResponse = 
            new co.elastic.clients.transport.endpoints.BooleanResponse(true);
        when(indicesClient.exists(any(co.elastic.clients.elasticsearch.indices.ExistsRequest.class)))
            .thenReturn(existsResponse);

        boolean exists = elasticsearchService.indexExists("documents");
        assertTrue(exists);
    }

    @Test
    void testIndexExists_False() throws IOException {
        when(properties.getIndexPrefix()).thenReturn("test_");
        when(client.indices()).thenReturn(indicesClient);

        co.elastic.clients.transport.endpoints.BooleanResponse existsResponse = 
            new co.elastic.clients.transport.endpoints.BooleanResponse(false);
        when(indicesClient.exists(any(co.elastic.clients.elasticsearch.indices.ExistsRequest.class)))
            .thenReturn(existsResponse);

        boolean exists = elasticsearchService.indexExists("documents");
        assertFalse(exists);
    }

    @Test
    void testIndexDocument_Success() throws IOException {
        when(properties.getIndexPrefix()).thenReturn("test_");

        Map<String, String> doc = new HashMap<>();
        doc.put("title", "Test Document");
        doc.put("content", "Test Content");

        IndexResponse indexResponse = mock(IndexResponse.class);
        when(indexResponse.result()).thenReturn(co.elastic.clients.elasticsearch._types.Result.Created);
        when(indexResponse.id()).thenReturn("doc1");

        when(client.index(any(IndexRequest.class))).thenReturn(indexResponse);

        String id = elasticsearchService.indexDocument("documents", "doc1", doc);
        assertNotNull(id);
        verify(client).index(any(IndexRequest.class));
    }

    @Test
    void testGetDocument_Found() throws IOException {
        when(properties.getIndexPrefix()).thenReturn("test_");

        Map<String, String> expectedDoc = new HashMap<>();
        expectedDoc.put("title", "Test Document");

        GetResponse<Map> getResponse = mock(GetResponse.class);
        when(getResponse.found()).thenReturn(true);
        when(getResponse.source()).thenReturn(expectedDoc);

        when(client.get(any(GetRequest.class), eq(Map.class))).thenReturn(getResponse);

        Optional<Map> result = elasticsearchService.getDocument("documents", "doc1", Map.class);

        assertTrue(result.isPresent());
        assertEquals(expectedDoc, result.get());
    }

    @Test
    void testGetDocument_NotFound() throws IOException {
        when(properties.getIndexPrefix()).thenReturn("test_");

        GetResponse<Map> getResponse = mock(GetResponse.class);
        when(getResponse.found()).thenReturn(false);

        when(client.get(any(GetRequest.class), eq(Map.class))).thenReturn(getResponse);

        Optional<Map> result = elasticsearchService.getDocument("documents", "doc1", Map.class);

        assertTrue(result.isEmpty());
    }

    @Test
    void testDeleteDocument_Success() throws IOException {
        when(properties.getIndexPrefix()).thenReturn("test_");

        DeleteResponse deleteResponse = mock(DeleteResponse.class);
        when(deleteResponse.result()).thenReturn(co.elastic.clients.elasticsearch._types.Result.Deleted);

        when(client.delete(any(DeleteRequest.class))).thenReturn(deleteResponse);

        boolean result = elasticsearchService.deleteDocument("documents", "doc1");

        assertTrue(result);
        verify(client).delete(any(DeleteRequest.class));
    }

    @Test
    void testSearch_BasicQuery() throws IOException {
        when(properties.getIndexPrefix()).thenReturn("test_");

        SearchResponse<Map> searchResponse = mock(SearchResponse.class);
        HitsMetadata<Map> hitsMetadata = mock(HitsMetadata.class);
        TotalHits totalHits = mock(TotalHits.class);

        when(totalHits.value()).thenReturn(1L);
        when(hitsMetadata.total()).thenReturn(totalHits);
        when(hitsMetadata.hits()).thenReturn(Collections.emptyList());
        when(searchResponse.hits()).thenReturn(hitsMetadata);
        when(searchResponse.took()).thenReturn(5L);

        when(client.search(any(SearchRequest.class), eq(Map.class))).thenReturn(searchResponse);

        ElasticsearchService.SearchResult<Map> result = elasticsearchService.search(
                "documents", "test query", Arrays.asList("title", "content"), 0, 10, Map.class
        );

        assertNotNull(result);
        assertEquals(1, result.getTotal());
        assertEquals(5L, result.getTook());
    }

    @Test
    void testCreateIndex_Success() throws IOException {
        when(properties.getIndexPrefix()).thenReturn("test_");

        co.elastic.clients.elasticsearch.indices.CreateIndexResponse createResponse =
                mock(co.elastic.clients.elasticsearch.indices.CreateIndexResponse.class);
        when(createResponse.acknowledged()).thenReturn(true);

        co.elastic.clients.transport.endpoints.BooleanResponse existsResponse = 
            new co.elastic.clients.transport.endpoints.BooleanResponse(false);

        when(client.indices()).thenReturn(indicesClient);
        when(indicesClient.exists(any(co.elastic.clients.elasticsearch.indices.ExistsRequest.class)))
            .thenReturn(existsResponse);
        when(indicesClient.create(any(co.elastic.clients.elasticsearch.indices.CreateIndexRequest.class)))
                .thenReturn(createResponse);

        Map<String, Object> mappings = new HashMap<>();
        mappings.put("title", Map.of("type", "text"));
        mappings.put("content", Map.of("type", "text"));

        boolean result = elasticsearchService.createIndex("documents", mappings);

        assertTrue(result);
        verify(indicesClient).create(any(co.elastic.clients.elasticsearch.indices.CreateIndexRequest.class));
    }

    @Test
    void testDeleteIndex_Success() throws IOException {
        when(properties.getIndexPrefix()).thenReturn("test_");

        co.elastic.clients.transport.endpoints.BooleanResponse existsResponse = 
            new co.elastic.clients.transport.endpoints.BooleanResponse(true);

        co.elastic.clients.elasticsearch.indices.DeleteIndexResponse deleteResponse =
                mock(co.elastic.clients.elasticsearch.indices.DeleteIndexResponse.class);
        when(deleteResponse.acknowledged()).thenReturn(true);

        when(client.indices()).thenReturn(indicesClient);
        when(indicesClient.exists(any(co.elastic.clients.elasticsearch.indices.ExistsRequest.class)))
            .thenReturn(existsResponse);
        when(indicesClient.delete(any(co.elastic.clients.elasticsearch.indices.DeleteIndexRequest.class)))
                .thenReturn(deleteResponse);

        boolean result = elasticsearchService.deleteIndex("documents");

        assertTrue(result);
        verify(indicesClient).delete(any(co.elastic.clients.elasticsearch.indices.DeleteIndexRequest.class));
    }

    @Test
    void testDeleteIndex_NotExists() throws IOException {
        when(properties.getIndexPrefix()).thenReturn("test_");

        co.elastic.clients.transport.endpoints.BooleanResponse existsResponse = 
            new co.elastic.clients.transport.endpoints.BooleanResponse(false);

        when(client.indices()).thenReturn(indicesClient);
        when(indicesClient.exists(any(co.elastic.clients.elasticsearch.indices.ExistsRequest.class)))
            .thenReturn(existsResponse);

        boolean result = elasticsearchService.deleteIndex("documents");

        assertFalse(result);
        verify(indicesClient, never()).delete(any(co.elastic.clients.elasticsearch.indices.DeleteIndexRequest.class));
    }

    @Test
    void testUpdateDocument_Success() throws IOException {
        when(properties.getIndexPrefix()).thenReturn("test_");

        Map<String, String> updates = new HashMap<>();
        updates.put("title", "Updated Title");

        UpdateResponse<Map> updateResponse = mock(UpdateResponse.class);
        when(updateResponse.result()).thenReturn(co.elastic.clients.elasticsearch._types.Result.Updated);

        when(client.update(any(UpdateRequest.class), eq(Map.class))).thenReturn(updateResponse);

        boolean result = elasticsearchService.updateDocument("documents", "doc1", updates, Map.class);

        assertTrue(result);
        verify(client).update(any(UpdateRequest.class), eq(Map.class));
    }

    @Test
    void testUpsertDocument_Success() throws IOException {
        when(properties.getIndexPrefix()).thenReturn("test_");

        Map<String, String> doc = new HashMap<>();
        doc.put("title", "Upserted Title");

        UpdateResponse<Map> updateResponse = mock(UpdateResponse.class);
        when(updateResponse.result()).thenReturn(co.elastic.clients.elasticsearch._types.Result.Created);

        when(client.update(any(UpdateRequest.class), eq(Map.class))).thenReturn(updateResponse);

        boolean result = elasticsearchService.upsertDocument("documents", "doc1", doc, Map.class);

        assertTrue(result);
        verify(client).update(any(UpdateRequest.class), eq(Map.class));
    }

    @Test
    void testIndexDocumentWithoutId() throws IOException {
        when(properties.getIndexPrefix()).thenReturn("test_");

        IndexResponse indexResponse = mock(IndexResponse.class);
        when(indexResponse.result()).thenReturn(co.elastic.clients.elasticsearch._types.Result.Created);
        when(indexResponse.id()).thenReturn("generated-id");

        when(client.index(any(IndexRequest.class))).thenReturn(indexResponse);

        Map<String, String> doc = Map.of("title", "Test");

        String id = elasticsearchService.indexDocumentWithoutId("documents", doc);

        assertNotNull(id);
        verify(client).index(any(IndexRequest.class));
    }
}
