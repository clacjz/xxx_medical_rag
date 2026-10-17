package com.clacjz.medical.rag.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.clacjz.medical.rag.config.RAGProperties;
import com.clacjz.medical.rag.exception.AiServiceException;
import com.clacjz.medical.rag.exception.BizException;
import com.clacjz.medical.rag.model.dto.ai.EmbeddingResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private static final String SERVICE_NAME = "Embedding";
    private static final int MAX_CACHE_SIZE = 1000;
    private static final int MAX_TOKEN_LIMIT = 32000;

    private final RAGProperties ragProperties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private final Map<String, float[]> embeddingCache = new ConcurrentHashMap<>();
    private final ReentrantLock cacheLock = new ReentrantLock();

    public float[] embed(String text) {
        String cacheKey = text;

        float[] cached = embeddingCache.get(cacheKey);
        if (cached != null) {
            return Arrays.copyOf(cached, cached.length);
        }

        try {
            float[] embedding = embedRemote(text);
            embeddingCache.put(cacheKey, embedding);

            if (embeddingCache.size() > MAX_CACHE_SIZE) {
                cacheLock.lock();
                try {
                    if (embeddingCache.size() > MAX_CACHE_SIZE) {
                        Iterator<String> iterator = embeddingCache.keySet().iterator();
                        int removed = 0;
                        while (iterator.hasNext() && removed < 100) {
                            iterator.next();
                            iterator.remove();
                            removed++;
                        }
                    }
                } finally {
                    cacheLock.unlock();
                }
            }

            return embedding;
        } catch (Exception e) {
            log.error("Failed to embed text");
            throw new AiServiceException(SERVICE_NAME, "Failed to embed text: " + e.getMessage(), e);
        }
    }

    private float[] embedRemote(String text) {
        RAGProperties.EmbeddingConfig config = ragProperties.getEmbedding();
        String url = config.getUrl() + "/api/embedding/embed";

        // 根据模型最大序列长度截断输入，避免超过 context window 导致静默截断或失败
        final int maxTokens = config.getMaxSequenceLength();
        int estimatedTokens = estimateTokens(text);
        if (estimatedTokens > maxTokens) {
            // 按比例截断保留安全裕量 (0.8)
            int safeChars = (int) (text.length() * ((double) maxTokens / estimatedTokens) * 0.8);
            safeChars = Math.max(safeChars, 100); // 至少保留 100 字符
            log.warn("Embedding input too long (est. {} tokens, max {}), truncating to {} chars",
                    estimatedTokens, maxTokens, safeChars);
            text = text.substring(0, safeChars);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("input", List.of(text));
        requestBody.put("model", config.getModel());

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new BizException("Embedding request failed: " + response.getStatusCode());
            }

            EmbeddingResponse embeddingResponse = objectMapper.readValue(response.getBody(), EmbeddingResponse.class);

            if (embeddingResponse.getData() == null || embeddingResponse.getData().isEmpty()) {
                throw new BizException("Invalid embedding response: no data");
            }

            List<Float> embeddingList = embeddingResponse.getData().get(0).getEmbedding();
            if (embeddingList == null || embeddingList.isEmpty()) {
                throw new BizException("Invalid embedding response: empty embedding array");
            }

            float[] result = new float[embeddingList.size()];
            for (int i = 0; i < embeddingList.size(); i++) {
                result[i] = embeddingList.get(i);
            }
            return result;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("Embedding service error: {}", e.getMessage());
            throw new AiServiceException(SERVICE_NAME, "Embedding service error: " + e.getMessage(), e);
        }
    }

    public int getDimension() {
        return ragProperties.getEmbedding().getDimension();
    }

    public void clearCache() {
        embeddingCache.clear();
        log.info("Embedding cache cleared");
    }

    /**
     * 计算文本的token数量
     * 用于文档长度校验（32K token限制）
     *
     * @param text 输入文本
     * @return token数量
     */
    public int tokenize(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return estimateTokens(text);
    }

    /**
     * 估算token数量
     * 区分中英文：中文约 1.5 字符/token，英文约 4 字符/token，标点/数字约 2 字符/token
     * BGE-M3 最大序列长度 8192 tokens
     */
    private int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        int cjkCount = 0;
        int alphaCount = 0;
        int otherCount = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isIdeographic(c) || (c >= '一' && c <= '鿿')) {
                cjkCount++;
            } else if (Character.isLetter(c)) {
                alphaCount++;
            } else {
                otherCount++;
            }
        }
        // 中文约 1.5 字符/token，字母约 4 字符/token，其他（数字/标点）约 2 字符/token
        int tokens = (int) Math.ceil(cjkCount / 1.5 + alphaCount / 4.0 + otherCount / 2.0);
        return Math.max(tokens, 1);
    }

    /**
     * 检查文本是否超过最大token限制（32K）
     *
     * @param text 输入文本
     * @return true if within limit, false if exceeds
     */
    public boolean withinTokenLimit(String text) {
        return tokenize(text) <= MAX_TOKEN_LIMIT;
    }

    /**
     * 获取最大token限制
     */
    public int getMaxTokenLimit() {
        return MAX_TOKEN_LIMIT;
    }
}
