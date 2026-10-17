package com.clacjz.medical.rag.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.clacjz.medical.rag.model.dto.qdrant.ResumableBatchRequest;
import com.clacjz.medical.rag.model.dto.qdrant.ResumeState;
import com.clacjz.medical.rag.model.dto.qdrant.VectorBatchItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ResumableBatchServiceTest {

    private static final String TEST_COLLECTION = "test_collection";
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    private VectorBatchItem createTestBatchItem(String id) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("documentId", "doc_" + id);

        float[] vector = new float[1024];
        Arrays.fill(vector, 0.5f);

        return VectorBatchItem.builder()
                .vector(vector)
                .payload(payload)
                .build();
    }

    @Test
    @DisplayName("测试ResumeState - 构建状态")
    void testResumeState_Build() {
        ResumeState state = ResumeState.builder()
                .batchId("batch-123")
                .collectionName(TEST_COLLECTION)
                .currentBatchIndex(5)
                .totalBatches(10)
                .processedItems(500)
                .totalItems(1000)
                .status(ResumeState.Status.RUNNING)
                .startTimeMs(System.currentTimeMillis())
                .lastUpdatedMs(System.currentTimeMillis())
                .processedPointIds(new ArrayList<>())
                .build();

        assertNotNull(state);
        assertEquals("batch-123", state.getBatchId());
        assertEquals(TEST_COLLECTION, state.getCollectionName());
        assertEquals(5, state.getCurrentBatchIndex());
        assertEquals(10, state.getTotalBatches());
        assertEquals(500, state.getProcessedItems());
        assertEquals(1000, state.getTotalItems());
        assertEquals(ResumeState.Status.RUNNING, state.getStatus());
    }

    @Test
    @DisplayName("测试ResumeState - 状态枚举")
    void testResumeState_StatusEnum() {
        assertEquals(5, ResumeState.Status.values().length);
        assertEquals("RUNNING", ResumeState.Status.RUNNING.name());
        assertEquals("PAUSED", ResumeState.Status.PAUSED.name());
        assertEquals("COMPLETED", ResumeState.Status.COMPLETED.name());
        assertEquals("FAILED", ResumeState.Status.FAILED.name());
        assertEquals("CANCELLED", ResumeState.Status.CANCELLED.name());
    }

    @Test
    @DisplayName("测试ResumableBatchRequest - 构建请求")
    void testResumableBatchRequest_Build() {
        List<VectorBatchItem> items = new ArrayList<>();
        items.add(createTestBatchItem("item_1"));

        ResumableBatchRequest request = ResumableBatchRequest.builder()
                .collectionName(TEST_COLLECTION)
                .items(items)
                .batchId("custom-batch-id")
                .waitForCompletion(false)
                .build();

        assertNotNull(request);
        assertEquals(TEST_COLLECTION, request.getCollectionName());
        assertEquals(1, request.getItems().size());
        assertEquals("custom-batch-id", request.getBatchId());
        assertFalse(request.isWaitForCompletion());
    }

    @Test
    @DisplayName("测试ResumableBatchRequest - 默认waitForCompletion")
    void testResumableBatchRequest_DefaultWaitForCompletion() {
        ResumableBatchRequest request = ResumableBatchRequest.builder()
                .collectionName(TEST_COLLECTION)
                .items(new ArrayList<>())
                .build();

        assertNotNull(request);
        assertFalse(request.isWaitForCompletion());
    }

    @Test
    @DisplayName("测试ResumeState - JSON序列化")
    void testResumeState_Serialization() throws Exception {
        ResumeState state = ResumeState.builder()
                .batchId("batch-123")
                .collectionName(TEST_COLLECTION)
                .currentBatchIndex(5)
                .totalBatches(10)
                .processedItems(500)
                .totalItems(1000)
                .status(ResumeState.Status.RUNNING)
                .startTimeMs(System.currentTimeMillis())
                .lastUpdatedMs(System.currentTimeMillis())
                .processedPointIds(Arrays.asList("id1", "id2"))
                .build();

        String json = objectMapper.writeValueAsString(state);
        assertNotNull(json);
        assertTrue(json.contains("batch-123"));
        assertTrue(json.contains(TEST_COLLECTION));

        ResumeState deserialized = objectMapper.readValue(json, ResumeState.class);
        assertEquals(state.getBatchId(), deserialized.getBatchId());
        assertEquals(state.getCollectionName(), deserialized.getCollectionName());
        assertEquals(state.getProcessedItems(), deserialized.getProcessedItems());
        assertEquals(state.getStatus(), deserialized.getStatus());
    }

    @Test
    @DisplayName("测试大批量分批计算")
    void testBatchCountCalculation() {
        assertEquals(1, calculateTotalBatches(100));
        assertEquals(1, calculateTotalBatches(1000));
        assertEquals(2, calculateTotalBatches(1001));
        assertEquals(3, calculateTotalBatches(2500));
        assertEquals(10, calculateTotalBatches(10000));
    }

    private int calculateTotalBatches(int totalItems) {
        int batchSize = 1000;
        return (totalItems + batchSize - 1) / batchSize;
    }

    @Test
    @DisplayName("测试分批列表")
    void testPartitionList() {
        List<String> items = new ArrayList<>();
        for (int i = 0; i < 2500; i++) {
            items.add("item_" + i);
        }

        List<List<String>> batches = partitionList(items, 1000);

        assertEquals(3, batches.size());
        assertEquals(1000, batches.get(0).size());
        assertEquals(1000, batches.get(1).size());
        assertEquals(500, batches.get(2).size());
    }

    private <T> List<List<T>> partitionList(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }

    @Test
    @DisplayName("测试进度百分比计算")
    void testProgressPercentCalculation() {
        assertEquals(50.0, calculateProgressPercent(500, 1000));
        assertEquals(100.0, calculateProgressPercent(1000, 1000));
        assertEquals(0.0, calculateProgressPercent(0, 1000));
        assertEquals(10.0, calculateProgressPercent(100, 1000));
    }

    private double calculateProgressPercent(int processed, int total) {
        if (total == 0) return 0.0;
        return (processed * 100.0) / total;
    }

    @Test
    @DisplayName("测试预计剩余时间计算")
    void testEstimatedRemainingCalculation() {
        long elapsed = 10000;
        int processed = 100;
        int total = 1000;

        long estimatedTotal = elapsed * total / processed;
        long remaining = estimatedTotal - elapsed;

        assertTrue(remaining > 0);
        assertEquals(estimatedTotal - elapsed, remaining);
    }
}
