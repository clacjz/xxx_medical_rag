package com.clacjz.medical.rag.service;

import com.clacjz.medical.rag.config.RAGProperties;
import com.clacjz.medical.rag.model.dto.qdrant.BatchInsertRequest;
import com.clacjz.medical.rag.model.dto.qdrant.BatchInsertResult;
import com.clacjz.medical.rag.model.dto.qdrant.BatchProgressCallback;
import com.clacjz.medical.rag.model.dto.qdrant.VectorBatchItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BatchVectorServiceTest {

    private static final String TEST_COLLECTION = "test_collection";

    private VectorBatchItem createTestBatchItem(String id) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("documentId", "doc_" + id);
        payload.put("chunkIndex", 0);
        payload.put("text", "Test content " + id);

        float[] vector = new float[1024];
        Arrays.fill(vector, 0.1f * (id.hashCode() % 10));

        return VectorBatchItem.builder()
                .vector(vector)
                .payload(payload)
                .build();
    }

    @Test
    @DisplayName("测试BatchInsertRequest - 构建请求")
    void testBatchInsertRequest_Build() {
        List<VectorBatchItem> items = new ArrayList<>();
        items.add(createTestBatchItem("item_1"));

        BatchInsertRequest request = BatchInsertRequest.builder()
                .collectionName(TEST_COLLECTION)
                .items(items)
                .waitForCompletion(true)
                .build();

        assertNotNull(request);
        assertEquals(TEST_COLLECTION, request.getCollectionName());
        assertEquals(1, request.getItems().size());
        assertTrue(request.isWaitForCompletion());
    }

    @Test
    @DisplayName("测试BatchInsertRequest - 默认waitForCompletion")
    void testBatchInsertRequest_DefaultWaitForCompletion() {
        BatchInsertRequest request = BatchInsertRequest.builder()
                .collectionName(TEST_COLLECTION)
                .items(new ArrayList<>())
                .build();

        assertNotNull(request);
        assertFalse(request.isWaitForCompletion());
    }

    @Test
    @DisplayName("测试BatchInsertResult - 构建结果")
    void testBatchInsertResult_Build() {
        List<String> failedIds = Arrays.asList("id1", "id2");

        BatchInsertResult result = BatchInsertResult.builder()
                .totalCount(100)
                .successCount(98)
                .failedCount(2)
                .failedPointIds(failedIds)
                .durationMs(500)
                .build();

        assertNotNull(result);
        assertEquals(100, result.getTotalCount());
        assertEquals(98, result.getSuccessCount());
        assertEquals(2, result.getFailedCount());
        assertEquals(failedIds, result.getFailedPointIds());
        assertEquals(500, result.getDurationMs());
    }

    @Test
    @DisplayName("测试BatchInsertResult - 无失败ID")
    void testBatchInsertResult_NoFailedIds() {
        BatchInsertResult result = BatchInsertResult.builder()
                .totalCount(100)
                .successCount(100)
                .failedCount(0)
                .durationMs(100)
                .build();

        assertNotNull(result);
        assertEquals(0, result.getFailedCount());
        assertNull(result.getFailedPointIds());
    }

    @Test
    @DisplayName("测试VectorBatchItem - 构建项目")
    void testVectorBatchItem_Build() {
        float[] vector = new float[1024];
        Arrays.fill(vector, 0.5f);
        Map<String, Object> payload = new HashMap<>();
        payload.put("key", "value");

        VectorBatchItem item = VectorBatchItem.builder()
                .vector(vector)
                .payload(payload)
                .pointId("custom-id")
                .build();

        assertNotNull(item);
        assertEquals(vector, item.getVector());
        assertEquals(payload, item.getPayload());
        assertEquals("custom-id", item.getPointId());
    }

    @Test
    @DisplayName("测试VectorBatchItem - 无Payload")
    void testVectorBatchItem_NoPayload() {
        float[] vector = new float[1024];
        Arrays.fill(vector, 0.5f);

        VectorBatchItem item = VectorBatchItem.builder()
                .vector(vector)
                .build();

        assertNotNull(item);
        assertEquals(vector, item.getVector());
        assertNull(item.getPayload());
        assertNull(item.getPointId());
    }

    @Test
    @DisplayName("测试BatchProgressCallback - 构建进度回调")
    void testBatchProgressCallback_Build() {
        BatchProgressCallback progress = BatchProgressCallback.builder()
                .batchId("batch-123")
                .currentBatch(5)
                .totalBatches(10)
                .processedItems(500)
                .totalItems(1000)
                .progressPercent(50.0)
                .startTimeMs(System.currentTimeMillis())
                .estimatedRemainingMs(300000L)
                .build();

        assertNotNull(progress);
        assertEquals("batch-123", progress.getBatchId());
        assertEquals(5, progress.getCurrentBatch());
        assertEquals(10, progress.getTotalBatches());
        assertEquals(500, progress.getProcessedItems());
        assertEquals(1000, progress.getTotalItems());
        assertEquals(50.0, progress.getProgressPercent());
        assertNotNull(progress.getStartTimeMs());
        assertEquals(300000L, progress.getEstimatedRemainingMs());
    }

    @Test
    @DisplayName("测试大批量数据分批计算")
    void testBatchSizeCalculation() {
        int batchSize = 1000;

        assertEquals(1, (int) Math.ceil(500.0 / batchSize));
        assertEquals(1, (int) Math.ceil(1000.0 / batchSize));
        assertEquals(2, (int) Math.ceil(1001.0 / batchSize));
        assertEquals(3, (int) Math.ceil(2500.0 / batchSize));
        assertEquals(10, (int) Math.ceil(9500.0 / batchSize));
    }

    @Test
    @DisplayName("测试分批列表分割")
    void testListPartitioning() {
        List<Integer> numbers = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        int batchSize = 3;

        List<List<Integer>> partitions = partitionList(numbers, batchSize);

        assertEquals(4, partitions.size());
        assertEquals(Arrays.asList(1, 2, 3), partitions.get(0));
        assertEquals(Arrays.asList(4, 5, 6), partitions.get(1));
        assertEquals(Arrays.asList(7, 8, 9), partitions.get(2));
        assertEquals(Arrays.asList(10), partitions.get(3));
    }

    private <T> List<List<T>> partitionList(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }
}
