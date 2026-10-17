package com.clacjz.medical.rag.service;

import com.google.common.primitives.Floats;
import com.clacjz.medical.rag.config.RAGProperties;
import com.clacjz.medical.rag.exception.BatchOperationException;
import com.clacjz.medical.rag.model.dto.qdrant.BatchInsertRequest;
import com.clacjz.medical.rag.model.dto.qdrant.BatchInsertResult;
import com.clacjz.medical.rag.model.dto.qdrant.BatchProgressCallback;
import com.clacjz.medical.rag.model.dto.qdrant.VectorBatchItem;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.qdrant.client.PointIdFactory;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.VectorsFactory;
import io.qdrant.client.grpc.JsonWithInt;
import io.qdrant.client.grpc.Common;
import io.qdrant.client.grpc.Points;
import io.qdrant.client.grpc.Points.PointStruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
@Service
public class BatchVectorService implements DisposableBean {

    private static final int BATCH_SIZE = 1000;
    private static final long THREADPOOL_AWAIT_TERMINATION_SECONDS = 30;

    private final QdrantClient qdrantClient;
    private final CircuitBreaker circuitBreaker;
    private final RAGProperties ragProperties;

    private final ExecutorService batchExecutor;

    public BatchVectorService(QdrantClient qdrantClient,
                             @Qualifier("qdrantCircuitBreaker") CircuitBreaker circuitBreaker,
                             RAGProperties ragProperties) {
        this.qdrantClient = qdrantClient;
        this.circuitBreaker = circuitBreaker;
        this.ragProperties = ragProperties;
        this.batchExecutor = Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors()
        );
    }

    @Override
    public void destroy() {
        log.info("关闭 BatchVectorService 线程池...");
        batchExecutor.shutdown();
        try {
            if (!batchExecutor.awaitTermination(THREADPOOL_AWAIT_TERMINATION_SECONDS, TimeUnit.SECONDS)) {
                log.warn("线程池未能在 {} 秒内正常关闭，强制中断",
                        THREADPOOL_AWAIT_TERMINATION_SECONDS);
                batchExecutor.shutdownNow();
                if (!batchExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.error("线程池强制关闭后仍未终止");
                }
            }
        } catch (InterruptedException e) {
            log.warn("线程池关闭被中断，强制关闭");
            batchExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("BatchVectorService 线程池已关闭");
    }

    public BatchInsertResult batchInsert(BatchInsertRequest request) {
        String collectionName = request.getCollectionName();
        List<VectorBatchItem> items = request.getItems();

        if (items == null || items.isEmpty()) {
            return BatchInsertResult.builder()
                    .totalCount(0)
                    .successCount(0)
                    .failedCount(0)
                    .durationMs(0)
                    .build();
        }

        long startTime = System.currentTimeMillis();
        List<String> failedIds = new ArrayList<>();
        int successCount = 0;

        List<List<VectorBatchItem>> batches = partitionList(items, BATCH_SIZE);
        int totalBatches = batches.size();

        log.info("开始批量插入: collection={}, 总数={}, 批次数={}",
                collectionName, items.size(), totalBatches);

        for (int batchIndex = 0; batchIndex < totalBatches; batchIndex++) {
            List<VectorBatchItem> batch = batches.get(batchIndex);

            try {
                List<PointStruct> points = batch.stream()
                        .map(item -> buildPoint(item))
                        .collect(Collectors.toList());

                qdrantClient.upsertAsync(collectionName, points)
                        .get(60, TimeUnit.SECONDS);

                successCount += batch.size();

                log.debug("批次 {} / {} 完成", batchIndex + 1, totalBatches);

            } catch (Exception e) {
                log.error("批次 {} / {} 失败", batchIndex + 1, totalBatches, e);

                for (VectorBatchItem item : batch) {
                    failedIds.add(item.getPointId());
                }

                throw new BatchOperationException(batchIndex, batch.size(), e);
            }
        }

        long duration = System.currentTimeMillis() - startTime;

        BatchInsertResult result = BatchInsertResult.builder()
                .totalCount(items.size())
                .successCount(successCount)
                .failedCount(failedIds.size())
                .failedPointIds(failedIds.isEmpty() ? null : failedIds)
                .durationMs(duration)
                .build();

        log.info("批量插入完成: collection={}, 成功={}, 失败={}, 耗时={}ms",
                collectionName, successCount, failedIds.size(), duration);

        return result;
    }

    public CompletableFuture<BatchInsertResult> batchInsertAsync(
            BatchInsertRequest request,
            Consumer<BatchProgressCallback> progressCallback) {

        return CompletableFuture.supplyAsync(() -> {
            String collectionName = request.getCollectionName();
            List<VectorBatchItem> items = request.getItems();

            if (items == null || items.isEmpty()) {
                return BatchInsertResult.builder()
                        .totalCount(0)
                        .successCount(0)
                        .failedCount(0)
                        .durationMs(0)
                        .build();
            }

            long startTime = System.currentTimeMillis();
            List<String> failedIds = new ArrayList<>();
            int successCount = 0;

            List<List<VectorBatchItem>> batches = partitionList(items, BATCH_SIZE);
            int totalBatches = batches.size();

            log.info("开始异步批量插入: collection={}, 总数={}, 批次数={}",
                    collectionName, items.size(), totalBatches);

            for (int batchIndex = 0; batchIndex < totalBatches; batchIndex++) {
                List<VectorBatchItem> batch = batches.get(batchIndex);

                try {
                    List<PointStruct> points = batch.stream()
                            .map(this::buildPoint)
                            .collect(Collectors.toList());

                    qdrantClient.upsertAsync(collectionName, points)
                            .get(60, TimeUnit.SECONDS);

                    successCount += batch.size();

                    if (progressCallback != null) {
                        long currentTime = System.currentTimeMillis();
                        long elapsed = currentTime - startTime;
                        int processedItems = successCount + failedIds.size();
                        double progressPercent = (processedItems * 100.0) / items.size();
                        long estimatedTotal = (long) (elapsed * items.size() / processedItems);
                        long remaining = estimatedTotal - elapsed;

                        BatchProgressCallback progress = BatchProgressCallback.builder()
                                .currentBatch(batchIndex + 1)
                                .totalBatches(totalBatches)
                                .processedItems(processedItems)
                                .totalItems(items.size())
                                .progressPercent(progressPercent)
                                .startTimeMs(startTime)
                                .estimatedRemainingMs(remaining)
                                .build();

                        progressCallback.accept(progress);
                    }

                } catch (Exception e) {
                    log.error("异步批量插入批次 {} / {} 失败", batchIndex + 1, totalBatches, e);

                    for (VectorBatchItem item : batch) {
                        failedIds.add(item.getPointId());
                    }
                }
            }

            long duration = System.currentTimeMillis() - startTime;

            return BatchInsertResult.builder()
                    .totalCount(items.size())
                    .successCount(successCount)
                    .failedCount(failedIds.size())
                    .failedPointIds(failedIds.isEmpty() ? null : failedIds)
                    .durationMs(duration)
                    .build();
        }, batchExecutor);
    }

    public BatchInsertResult batchInsertWithProgress(
            BatchInsertRequest request,
            Consumer<BatchProgressCallback> progressCallback) {

        return batchInsertAsync(request, progressCallback).join();
    }

    public BatchInsertResult batchUpsert(BatchInsertRequest request) {
        return batchInsert(request);
    }

    public CompletableFuture<BatchInsertResult> batchUpsertAsync(
            BatchInsertRequest request,
            Consumer<BatchProgressCallback> progressCallback) {

        return batchInsertAsync(request, progressCallback);
    }

    public void batchDelete(String collectionName, List<String> pointIds) {
        if (pointIds == null || pointIds.isEmpty()) {
            return;
        }

        List<List<String>> batches = partitionList(pointIds, BATCH_SIZE);

        log.info("开始批量删除: collection={}, 总数={}, 批次数={}",
                collectionName, pointIds.size(), batches.size());

        for (int batchIndex = 0; batchIndex < batches.size(); batchIndex++) {
            List<String> batch = batches.get(batchIndex);

            try {
                List<Common.PointId> ids = batch.stream()
                        .map(id -> PointIdFactory.id(UUID.fromString(id)))
                        .collect(Collectors.toList());

                qdrantClient.deleteAsync(collectionName, ids).get(60, TimeUnit.SECONDS);

                log.debug("删除批次 {} / {} 完成", batchIndex + 1, batches.size());

            } catch (Exception e) {
                log.error("批量删除失败: collection={}, batch={}", collectionName, batchIndex, e);
                throw new BatchOperationException(batchIndex, batch.size(), e);
            }
        }

        log.info("批量删除完成: collection={}, 总数={}", collectionName, pointIds.size());
    }

    private PointStruct buildPoint(VectorBatchItem item) {
        String pointId = item.getPointId();
        if (pointId == null || pointId.isBlank()) {
            pointId = UUID.randomUUID().toString();
        }

        PointStruct.Builder builder = PointStruct.newBuilder()
                .setId(PointIdFactory.id(UUID.fromString(pointId)))
                .setVectors(VectorsFactory.vectors(item.getVector()));

        if (item.getPayload() != null && !item.getPayload().isEmpty()) {
            builder.putAllPayload(convertPayload(item.getPayload()));
        }

        return builder.build();
    }

    private Map<String, JsonWithInt.Value> convertPayload(Map<String, Object> payload) {
        Map<String, JsonWithInt.Value> result = new HashMap<>();
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            result.put(entry.getKey(), convertToValue(entry.getValue()));
        }
        return result;
    }

    private JsonWithInt.Value convertToValue(Object value) {
        JsonWithInt.Value.Builder builder = JsonWithInt.Value.newBuilder();

        if (value instanceof String s) {
            return builder.setStringValue(s).build();
        } else if (value instanceof Number n) {
            return builder.setDoubleValue(n.doubleValue()).build();
        } else if (value instanceof Boolean b) {
            return builder.setBoolValue(b).build();
        } else if (value instanceof List<?> list) {
            JsonWithInt.ListValue.Builder listBuilder = JsonWithInt.ListValue.newBuilder();
            for (Object item : list) {
                listBuilder.addValues(convertToValue(item));
            }
            return builder.setListValue(listBuilder.build()).build();
        }

        return builder.setStringValue(value != null ? value.toString() : "").build();
    }

    private <T> List<List<T>> partitionList(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }
}
