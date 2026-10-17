package com.clacjz.medical.rag.service;

import com.clacjz.medical.rag.config.RAGProperties;
import com.clacjz.medical.rag.exception.CollectionAlreadyExistsException;
import com.clacjz.medical.rag.exception.CollectionNotFoundException;
import com.clacjz.medical.rag.model.dto.qdrant.CollectionStats;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CollectionManagementServiceTest {

    @Mock
    private QdrantClient qdrantClient;

    @Mock
    private RAGProperties ragProperties;

    private CollectionManagementService collectionManagementService;

    private static final String TEST_COLLECTION = "test_collection";

    @BeforeEach
    void setUp() {
        collectionManagementService = new CollectionManagementService(qdrantClient, ragProperties);
        lenient().when(ragProperties.getQdrant()).thenReturn(new RAGProperties.QdrantConfig());
    }

    @Test
    @DisplayName("测试parseDistance - Cosine")
    void testParseDistance_Cosine() {
        assertEquals(Collections.Distance.Cosine,
                collectionManagementService.parseDistance("Cosine"));
    }

    @Test
    @DisplayName("测试parseDistance - Euclid")
    void testParseDistance_Euclid() {
        assertEquals(Collections.Distance.Euclid,
                collectionManagementService.parseDistance("Euclid"));
    }

    @Test
    @DisplayName("测试parseDistance - Dot")
    void testParseDistance_Dot() {
        assertEquals(Collections.Distance.Dot,
                collectionManagementService.parseDistance("Dot"));
    }

    @Test
    @DisplayName("测试parseDistance - 未知类型默认Cosine")
    void testParseDistance_Unknown() {
        assertEquals(Collections.Distance.Cosine,
                collectionManagementService.parseDistance("Unknown"));
    }

    @Test
    @DisplayName("测试parseDistance - null值默认Cosine")
    void testParseDistance_Null() {
        assertEquals(Collections.Distance.Cosine,
                collectionManagementService.parseDistance(null));
    }

    @Test
    @DisplayName("测试parseDistance - 小写cosine")
    void testParseDistance_LowercaseCosine() {
        assertEquals(Collections.Distance.Cosine,
                collectionManagementService.parseDistance("cosine"));
    }

    @Test
    @DisplayName("测试parseDistance - 小写dot")
    void testParseDistance_LowercaseDot() {
        assertEquals(Collections.Distance.Dot,
                collectionManagementService.parseDistance("dot"));
    }

    @Test
    @DisplayName("测试parseDistance - dotproduct别名")
    void testParseDistance_DotProduct() {
        assertEquals(Collections.Distance.Dot,
                collectionManagementService.parseDistance("dotproduct"));
    }

    @Test
    @DisplayName("测试parseDistance - euclidean别名")
    void testParseDistance_Euclidean() {
        assertEquals(Collections.Distance.Euclid,
                collectionManagementService.parseDistance("euclidean"));
    }

    @Test
    @DisplayName("测试CollectionStats - 构建统计信息")
    void testCollectionStats_Build() {
        CollectionStats stats = CollectionStats.builder()
                .name(TEST_COLLECTION)
                .pointsCount(1000L)
                .vectorsCount(1000L)
                .indexedVectorsCount(950L)
                .status("READY")
                .createdAt(System.currentTimeMillis())
                .build();

        assertNotNull(stats);
        assertEquals(TEST_COLLECTION, stats.getName());
        assertEquals(1000L, stats.getPointsCount());
        assertEquals(1000L, stats.getVectorsCount());
        assertEquals(950L, stats.getIndexedVectorsCount());
        assertEquals("READY", stats.getStatus());
        assertNotNull(stats.getCreatedAt());
    }

    @Test
    @DisplayName("测试CollectionStats - 无参构建")
    void testCollectionStats_EmptyBuild() {
        CollectionStats stats = new CollectionStats();

        assertNotNull(stats);
        assertNull(stats.getName());
        assertNull(stats.getPointsCount());
    }
}
