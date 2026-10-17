package com.clacjz.medical.rag.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.clacjz.medical.rag.exception.BatchOperationException;
import com.clacjz.medical.rag.model.dto.qdrant.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResumableBatchService {

    private static final String REDIS_KEY_PREFIX = "batch:state:";
    private static final int BATCH_SIZE = 1000;
    private static final Duration STATE_TTL = Duration.ofHours(24);

    private final BatchVectorService batchVectorService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    private final Map<String, ResumeState> runningBatches = new ConcurrentHashMap<>();
    private final Map<String, AtomicBoolean> pauseFlags = new ConcurrentHashMap<>();
    private final ExecutorService resumeExecutor = Executors.newFixedThreadPool(2);

    public String startBatch(ResumableBatchRequest request, Consumer<BatchProgressCallback> progressCallback) {
        String batchId = request.getBatchId();
        if (batchId == null || batchId.isBlank()) {
            batchId = UUID.randomUUID().toString();
        }

        if (runningBatches.containsKey(batchId)) {
            throw new BatchOperationException("Batch already running: " + batchId);
        }

        ResumeState state = ResumeState.builder()
                .batchId(batchId)
                .collectionName(request.getCollectionName())
                .currentBatchIndex(0)
                .totalBatches(calculateTotalBatches(request.getItems().size()))
                .processedItems(0)
                .totalItems(request.getItems().size())
                .status(ResumeState.Status.RUNNING)
                .startTimeMs(System.currentTimeMillis())
                .lastUpdatedMs(System.currentTimeMillis())
                .processedPointIds(new ArrayList<>())
                .build();

        runningBatches.put(batchId, state);
        pauseFlags.put(batchId, new AtomicBoolean(false));

        saveStateToRedis(state);

        log.info("开始可恢复批量操作: batchId={}, collection={}, totalItems={}",
                batchId, request.getCollectionName(), request.getItems().size());

        if (request.isWaitForCompletion()) {
            executeBatch(state, request.getItems(), progressCallback);
        } else {
            resumeExecutor.submit(() -> executeBatch(state, request.getItems(), progressCallback));
        }

        return batchId;
    }

    public ResumeState resumeBatch(String batchId) {
        ResumeState savedState = loadStateFromRedis(batchId);

        if (savedState == null) {
            throw new BatchOperationException("未找到Batch状态: " + batchId);
        }

        if (savedState.getStatus() == ResumeState.Status.RUNNING) {
            throw new BatchOperationException("Batch正在运行中: " + batchId);
        }

        log.info("恢复可恢复批量操作: batchId={}, 从批次 {} 继续",
                batchId, savedState.getCurrentBatchIndex());

        savedState.setStatus(ResumeState.Status.RUNNING);
        savedState.setLastUpdatedMs(System.currentTimeMillis());
        runningBatches.put(batchId, savedState);
        pauseFlags.put(batchId, new AtomicBoolean(false));

        resumeExecutor.submit(() -> resumeFromSavedState(savedState));

        return savedState;
    }

    public ResumeState pauseBatch(String batchId) {
        AtomicBoolean pauseFlag = pauseFlags.get(batchId);
        if (pauseFlag != null) {
            pauseFlag.set(true);
        }

        ResumeState state = runningBatches.get(batchId);
        if (state != null) {
            state.setStatus(ResumeState.Status.PAUSED);
            state.setLastUpdatedMs(System.currentTimeMillis());
            saveStateToRedis(state);
        }

        log.info("暂停可恢复批量操作: batchId={}", batchId);

        return state;
    }

    public ResumeState cancelBatch(String batchId) {
        AtomicBoolean pauseFlag = pauseFlags.get(batchId);
        if (pauseFlag != null) {
            pauseFlag.set(true);
        }

        ResumeState state = runningBatches.remove(batchId);
        if (state != null) {
            state.setStatus(ResumeState.Status.CANCELLED);
            state.setLastUpdatedMs(System.currentTimeMillis());
            saveStateToRedis(state);
        }

        pauseFlags.remove(batchId);

        log.info("取消可恢复批量操作: batchId={}", batchId);

        return state;
    }

    public ResumeState getBatchState(String batchId) {
        ResumeState state = runningBatches.get(batchId);
        if (state != null) {
            return state;
        }

        return loadStateFromRedis(batchId);
    }

    public List<String> listActiveBatches() {
        return new ArrayList<>(runningBatches.keySet());
    }

    private void executeBatch(ResumeState state, List<VectorBatchItem> allItems,
                             Consumer<BatchProgressCallback> progressCallback) {
        List<VectorBatchItem> items = allItems;
        int startIndex = state.getCurrentBatchIndex() * BATCH_SIZE;

        if (startIndex > 0) {
            items = items.subList(startIndex, items.size());
            log.info("跳过已处理项目: batchId={}, startIndex={}", state.getBatchId(), startIndex);
        }

        AtomicInteger successCount = new AtomicInteger(state.getProcessedItems());
        List<List<VectorBatchItem>> batches = partitionList(items, BATCH_SIZE);
        int totalBatches = batches.size();

        for (int i = 0; i < batches.size(); i++) {
            AtomicBoolean pauseFlag = pauseFlags.get(state.getBatchId());

            if (pauseFlag != null && pauseFlag.get()) {
                state.setCurrentBatchIndex(state.getCurrentBatchIndex() + i);
                state.setProcessedItems(successCount.get());
                state.setStatus(ResumeState.Status.PAUSED);
                state.setLastUpdatedMs(System.currentTimeMillis());
                saveStateToRedis(state);
                runningBatches.remove(state.getBatchId());
                log.info("批量操作已暂停: batchId={}", state.getBatchId());
                return;
            }

            List<VectorBatchItem> batch = batches.get(i);
            state.setCurrentBatchIndex(state.getCurrentBatchIndex() + i + 1);

            try {
                BatchInsertRequest batchRequest = BatchInsertRequest.builder()
                        .collectionName(state.getCollectionName())
                        .items(batch)
                        .build();

                BatchInsertResult result = batchVectorService.batchInsert(batchRequest);

                successCount.addAndGet(result.getSuccessCount());

                state.setProcessedItems(successCount.get());
                state.setLastUpdatedMs(System.currentTimeMillis());
                saveStateToRedis(state);

                if (progressCallback != null) {
                    BatchProgressCallback progress = buildProgressCallback(state);
                    progressCallback.accept(progress);
                }

                log.debug("批次 {}/{} 完成: batchId={}",
                        state.getCurrentBatchIndex(), state.getTotalBatches(), state.getBatchId());

            } catch (Exception e) {
                log.error("批次 {}/{} 失败: batchId={}", state.getCurrentBatchIndex(),
                        state.getTotalBatches(), state.getBatchId(), e);

                state.setStatus(ResumeState.Status.FAILED);
                state.setErrorMessage(e.getMessage());
                state.setLastUpdatedMs(System.currentTimeMillis());
                saveStateToRedis(state);

                runningBatches.remove(state.getBatchId());
                throw new BatchOperationException("批量操作失败: " + e.getMessage(), e);
            }
        }

        state.setStatus(ResumeState.Status.COMPLETED);
        state.setProcessedItems(state.getTotalItems());
        state.setLastUpdatedMs(System.currentTimeMillis());
        saveStateToRedis(state);
        runningBatches.remove(state.getBatchId());

        log.info("批量操作完成: batchId={}, 总处理数={}", state.getBatchId(), state.getTotalItems());
    }

    private void resumeFromSavedState(ResumeState state) {
        List<VectorBatchItem> items = loadRemainingItems(state);

        if (items == null || items.isEmpty()) {
            log.info("没有剩余项目需要处理: batchId={}", state.getBatchId());
            state.setStatus(ResumeState.Status.COMPLETED);
            state.setLastUpdatedMs(System.currentTimeMillis());
            saveStateToRedis(state);
            return;
        }

        executeBatch(state, items, null);
    }

    private List<VectorBatchItem> loadRemainingItems(ResumeState state) {
        return null;
    }

    private BatchProgressCallback buildProgressCallback(ResumeState state) {
        long currentTime = System.currentTimeMillis();
        long elapsed = currentTime - state.getStartTimeMs();
        double progressPercent = (state.getProcessedItems() * 100.0) / state.getTotalItems();
        long estimatedTotal = elapsed * state.getTotalItems() / Math.max(state.getProcessedItems(), 1);
        long remaining = estimatedTotal - elapsed;

        return BatchProgressCallback.builder()
                .batchId(state.getBatchId())
                .currentBatch(state.getCurrentBatchIndex())
                .totalBatches(state.getTotalBatches())
                .processedItems(state.getProcessedItems())
                .totalItems(state.getTotalItems())
                .progressPercent(progressPercent)
                .startTimeMs(state.getStartTimeMs())
                .estimatedRemainingMs(remaining)
                .build();
    }

    private void saveStateToRedis(ResumeState state) {
        try {
            String key = REDIS_KEY_PREFIX + state.getBatchId();
            String json = objectMapper.writeValueAsString(state);
            stringRedisTemplate.opsForValue().set(key, json, STATE_TTL);
        } catch (JsonProcessingException e) {
            log.error("保存Batch状态到Redis失败: batchId={}", state.getBatchId(), e);
        }
    }

    private ResumeState loadStateFromRedis(String batchId) {
        try {
            String key = REDIS_KEY_PREFIX + batchId;
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json != null) {
                return objectMapper.readValue(json, ResumeState.class);
            }
        } catch (JsonProcessingException e) {
            log.error("从Redis加载Batch状态失败: batchId={}", batchId, e);
        }
        return null;
    }

    private int calculateTotalBatches(int totalItems) {
        return (totalItems + BATCH_SIZE - 1) / BATCH_SIZE;
    }

    private <T> List<List<T>> partitionList(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }
}
