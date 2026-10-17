package com.clacjz.medical.rag.config;

import com.clacjz.medical.rag.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Configuration Classes Tests")
class ConfigurationTests extends BaseIntegrationTest {

    @Autowired
    private RAGProperties ragProperties;

    @Autowired
    private JwtProperties jwtProperties;

    @Test
    @DisplayName("Should load RAG properties correctly")
    void testRAGProperties() {
        assertNotNull(ragProperties);
        assertNotNull(ragProperties.getEmbedding());
        assertNotNull(ragProperties.getLlm());
        assertNotNull(ragProperties.getQdrant());
        assertNotNull(ragProperties.getMinio());
        assertNotNull(ragProperties.getDocument());
    }

    @Test
    @DisplayName("Should load embedding configuration with dynamic URL")
    void testEmbeddingConfiguration() {
        assertNotNull(ragProperties.getEmbedding());
        assertNotNull(ragProperties.getEmbedding().getHost());
        assertTrue(ragProperties.getEmbedding().getPort() > 0);

        String expectedUrl = String.format("http://%s:%d",
                ragProperties.getEmbedding().getHost(),
                ragProperties.getEmbedding().getPort());
        assertEquals(expectedUrl, ragProperties.getEmbedding().getUrl());

        assertEquals("bge-large-zh-v1.5", ragProperties.getEmbedding().getModel());
        assertEquals(1024, ragProperties.getEmbedding().getDimension());
    }

    @Test
    @DisplayName("Should load LLM configuration with dynamic URL")
    void testLLMConfiguration() {
        assertNotNull(ragProperties.getLlm());
        assertNotNull(ragProperties.getLlm().getHost());
        assertTrue(ragProperties.getLlm().getPort() > 0);

        String expectedUrl = String.format("http://%s:%d",
                ragProperties.getLlm().getHost(),
                ragProperties.getLlm().getPort());
        assertEquals(expectedUrl, ragProperties.getLlm().getBaseUrl());

        assertEquals("qwen2.5:7b", ragProperties.getLlm().getModel());
        assertEquals(0.7, ragProperties.getLlm().getTemperature());
        assertEquals(2048, ragProperties.getLlm().getMaxTokens());
    }

    @Test
    @DisplayName("Should load Qdrant configuration")
    void testQdrantConfiguration() {
        assertNotNull(ragProperties.getQdrant());
        assertNotNull(ragProperties.getQdrant().getHost());
        assertEquals("localhost", ragProperties.getQdrant().getHost());
        assertEquals(6333, ragProperties.getQdrant().getPort());
        assertEquals(6334, ragProperties.getQdrant().getGrpcPort());
    }

    @Test
    @DisplayName("Should load MinIO configuration with dynamic endpoint")
    void testMinIOConfiguration() {
        assertNotNull(ragProperties.getMinio());
        assertNotNull(ragProperties.getMinio().getHost());
        assertTrue(ragProperties.getMinio().getPort() > 0);

        String expectedEndpoint = String.format("http://%s:%d",
                ragProperties.getMinio().getHost(),
                ragProperties.getMinio().getPort());
        assertEquals(expectedEndpoint, ragProperties.getMinio().getEndpoint());
    }

    @Test
    @DisplayName("Should load JWT properties correctly")
    void testJwtProperties() {
        assertNotNull(jwtProperties);
        assertNotNull(jwtProperties.getSecret());
        assertTrue(jwtProperties.getExpiration() > 0);
        assertTrue(jwtProperties.getRefreshExpiration() > 0);
    }

    @Test
    @DisplayName("Should load document chunk configuration")
    void testDocumentChunkConfiguration() {
        assertNotNull(ragProperties.getDocument());
        assertNotNull(ragProperties.getDocument().getChunk());
        assertEquals(500, ragProperties.getDocument().getChunk().getSize());
        assertEquals(50, ragProperties.getDocument().getChunk().getOverlap());
    }

    @Test
    @DisplayName("Should load supported document types")
    void testSupportedDocumentTypes() {
        assertNotNull(ragProperties.getDocument().getSupportedTypes());
        assertTrue(ragProperties.getDocument().getSupportedTypes().length > 0);
        assertTrue(java.util.Arrays.asList(ragProperties.getDocument().getSupportedTypes()).contains("pdf"));
        assertTrue(java.util.Arrays.asList(ragProperties.getDocument().getSupportedTypes()).contains("docx"));
        assertTrue(java.util.Arrays.asList(ragProperties.getDocument().getSupportedTypes()).contains("md"));
        assertTrue(java.util.Arrays.asList(ragProperties.getDocument().getSupportedTypes()).contains("txt"));
    }
}
