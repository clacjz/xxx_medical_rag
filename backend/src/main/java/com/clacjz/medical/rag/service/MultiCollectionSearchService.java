package com.clacjz.medical.rag.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.clacjz.medical.rag.config.RAGProperties;
import com.clacjz.medical.rag.model.config.CollectionConfig;
import com.clacjz.medical.rag.model.dto.AccessCheckResult;
import com.clacjz.medical.rag.model.dto.SearchResult;
import com.clacjz.medical.rag.model.entity.Collection;
import com.clacjz.medical.rag.mapper.CollectionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MultiCollectionSearchService {

    private final CollectionMapper collectionMapper;
    private final EmbeddingService embeddingService;
    private final RAGProperties ragProperties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final Executor taskExecutor;
    private final DocumentPermissionService documentPermissionService;

    public List<SearchResult> search(String query, List<String> collectionIds, int topK, Double threshold) {
        return search(query, collectionIds, topK, threshold, null, null);
    }

    public List<SearchResult> search(String query, List<String> collectionIds, int topK, Double threshold, String userId) {
        return search(query, collectionIds, topK, threshold, userId, null);
    }

    public List<SearchResult> search(String query, List<String> collectionIds, int topK, Double threshold, String userId,
                                     Map<String, Object> filter) {
        log.info("=== MultiCollectionSearchService.search START: query={}, collectionIds={}, topK={}, userId={}, hasFilter={}",
                query, collectionIds, topK, userId, filter != null && !filter.isEmpty());

        if (collectionIds == null || collectionIds.isEmpty()) {
            log.warn("No collection IDs provided for search");
            return Collections.emptyList();
        }

        // 过滤掉 null 值，防止 MyBatis OGNL 异常
        List<String> validIds = collectionIds.stream()
                .filter(id -> id != null && !id.trim().isEmpty())
                .collect(Collectors.toList());

        if (validIds.isEmpty()) {
            log.warn("No valid collection IDs after filtering nulls");
            return Collections.emptyList();
        }

        List<Collection> collections = collectionMapper.selectActiveByIds(validIds);
        log.info("=== Found {} active collections for IDs: {}", collections.size(), validIds);

        if (collections.isEmpty()) {
            log.warn("No active collections found for IDs: {}", collectionIds);
            return Collections.emptyList();
        }

        float[] queryVector = embeddingService.embed(query);
        if (queryVector == null) {
            log.warn("Embedding returned null for query: {}", query);
            return Collections.emptyList();
        }
        log.info("=== Embedding done, vector.length={}", queryVector.length);

        List<SearchResult> allResults = new ArrayList<>();

        List<CompletableFuture<List<SearchResult>>> futures = collections.stream()
                .map(collection -> CompletableFuture.supplyAsync(
                        () -> searchInCollection(queryVector, collection, topK, threshold, filter),
                        taskExecutor))
                .toList();

        log.info("=== Started {} async search tasks", futures.size());

        for (int i = 0; i < futures.size(); i++) {
            try {
                List<SearchResult> results = futures.get(i).join();
                log.info("=== Async task {} completed with {} results", i, results.size());
                allResults.addAll(results);
            } catch (Exception e) {
                log.error("=== Async task {} failed: {}", i, e.getMessage(), e);
            }
        }

        log.info("=== Total results before permission filter: {}", allResults.size());

        List<SearchResult> filteredResults = filterResultsByPermission(allResults, userId);
        log.info("=== Results after permission filter: {}", filteredResults.size());

        List<SearchResult> finalResults = mergeAndSortResults(filteredResults, topK);
        log.info("=== MultiCollectionSearchService.search END: finalResults={}", finalResults.size());

        return finalResults;
    }

    private List<SearchResult> filterResultsByPermission(List<SearchResult> results, String userId) {
        if (userId == null || userId.isBlank()) {
            return results;
        }

        // 按 documentId 分组，每个文档只做一次权限校验
        Map<String, List<SearchResult>> resultsByDoc = results.stream()
                .collect(Collectors.groupingBy(SearchResult::getDocumentId));

        List<SearchResult> filtered = new ArrayList<>();
        for (Map.Entry<String, List<SearchResult>> entry : resultsByDoc.entrySet()) {
            String documentId = entry.getKey();
            try {
                AccessCheckResult accessResult = documentPermissionService.checkDocumentAccess(userId, documentId);
                if (accessResult.isHasAccess()) {
                    filtered.addAll(entry.getValue());
                }
            } catch (Exception e) {
                log.debug("Access check failed for document {}: {}", documentId, e.getMessage(), e);
            }
        }
        return filtered;
    }

    private List<SearchResult> searchInCollection(float[] queryVector, Collection collection,
                                                 int topK, Double threshold, Map<String, Object> filter) {
        log.info("=== searchInCollection START: collectionId={}, vector.length={}, topK={}, hasFilter={}",
                collection.getId(), queryVector.length, topK, filter != null && !filter.isEmpty());
        try {
            List<SearchResult> results = performQdrantSearch(collection.getId(), queryVector, topK, filter);
            log.info("=== searchInCollection: collectionId={}, results from Qdrant={}", collection.getId(), results.size());

            // 调试：打印所有分数
            if (!results.isEmpty()) {
                double maxScore = results.stream().mapToDouble(SearchResult::getScore).max().orElse(0);
                double minScore = results.stream().mapToDouble(SearchResult::getScore).min().orElse(0);
                log.info("=== searchInCollection: score range=[{}-{}], top 3 scores={}",
                        minScore, maxScore,
                        results.stream().sorted(Comparator.comparing(SearchResult::getScore).reversed()).limit(3).map(r -> r.getScore()).toList());
            }

            CollectionConfig.RetrievalConfig retrievalConfig = collection.getConfig() != null ?
                    collection.getConfig().getRetrieval() : null;
            double effectiveThreshold = threshold != null ? threshold :
                    (retrievalConfig != null ? retrievalConfig.getSimilarityThreshold() : 0.3);

            log.info("=== searchInCollection: collectionId={}, effectiveThreshold={}, thresholdParam={}, retrievalConfig={}",
                    collection.getId(), effectiveThreshold, threshold,
                    retrievalConfig != null ? retrievalConfig.getSimilarityThreshold() : "null");

            List<SearchResult> filtered = results.stream()
                    .filter(result -> result.getScore() >= effectiveThreshold)
                    .collect(Collectors.toList());

            log.info("=== searchInCollection END: collectionId={}, final results={}", collection.getId(), filtered.size());
            return filtered;
        } catch (Exception e) {
            log.error("=== searchInCollection ERROR: collectionId={}, error={}", collection.getId(), e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private List<SearchResult> performQdrantSearch(String collectionId, float[] queryVector, int topK,
                                                    Map<String, Object> filter) {
        try {
            var config = ragProperties.getQdrant();
            if (config == null || config.getHost() == null) {
                log.warn("Qdrant config not available");
                return Collections.emptyList();
            }
            String url = String.format("http://%s:%d/collections/%s/points/search",
                    config.getHost(), config.getPort(), collectionId);

            log.debug("Qdrant search request: url={}, vector.length={}, topK={}, apiKey={}, hasFilter={}",
                    url, queryVector.length, topK, config.getApiKey() != null ? "set" : "null",
                    filter != null && !filter.isEmpty());

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("vector", convertToDoubleList(queryVector));
            requestBody.put("limit", topK);
            requestBody.put("with_payload", true);

            // 由扁平 key-value map 构建 Qdrant REST filter
            if (filter != null && !filter.isEmpty()) {
                Map<String, Object> qdrantFilter = buildQdrantRestFilter(filter);
                requestBody.put("filter", qdrantFilter);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (config.getApiKey() != null && !config.getApiKey().isBlank()) {
                headers.set("api-key", config.getApiKey());
            }
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            log.debug("Qdrant REST response: status={}, body length={}",
                    response.getStatusCode(), response.getBody() != null ? response.getBody().length() : 0);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("Qdrant search failed with status: {}, body: {}",
                        response.getStatusCode(), response.getBody());
                return Collections.emptyList();
            }

            List<SearchResult> results = parseSearchResponse(response.getBody(), collectionId);
            log.debug("Qdrant search parsed results: count={}", results.size());
            return results;
        } catch (Exception e) {
            log.error("Qdrant search failed: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private List<SearchResult> parseSearchResponse(String jsonResponse, String collectionId) {
        List<SearchResult> results = new ArrayList<>();
        try {
            log.debug("Parsing Qdrant response for collection: {}", collectionId);
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode result = root.get("result");

            if (result == null || !result.isArray()) {
                log.warn("Qdrant response has no result array or result is null. Response: {}", jsonResponse);
                return results;
            }

            log.debug("Qdrant response result array size: {}", result.size());

            for (JsonNode point : result) {
                double score = point.has("score") ? point.get("score").asDouble() : 0.0;
                String id = point.has("id") ? point.get("id").asText() : UUID.randomUUID().toString();

                Map<String, Object> payload = new HashMap<>();
                if (point.has("payload")) {
                    JsonNode payloadNode = point.get("payload");
                    payload = objectMapper.convertValue(payloadNode, new TypeReference<Map<String, Object>>() {});
                }

                String text = extractTextFromPayload(payload);
                String title = payload.getOrDefault("title", "").toString();
                String snippet = text.length() > 200 ? text.substring(0, 200) + "..." : text;
                String documentId = payload.getOrDefault("document_id", id).toString();

                // 提取页码信息
                Integer startPage = extractIntFromPayload(payload, "start_page");
                Integer endPage = extractIntFromPayload(payload, "end_page");

                log.debug("Parsed point: id={}, score={}, text.length={}, documentId={}",
                        id, score, text.length(), documentId);

                results.add(SearchResult.builder()
                        .id(id)
                        .documentId(documentId)
                        .collectionId(collectionId)
                        .text(text)
                        .score(score)
                        .title(title)
                        .snippet(snippet)
                        .metadata(payload)
                        .startPage(startPage)
                        .endPage(endPage)
                        .build());
            }
        } catch (Exception e) {
            log.error("Failed to parse Qdrant response: {}", e.getMessage(), e);
        }
        return results;
    }

    private String extractTextFromPayload(Map<String, Object> payload) {
        Object text = payload.get("text");
        if (text != null) return text.toString();
        Object content = payload.get("content");
        if (content != null) return content.toString();
        Object pageContent = payload.get("page_content");
        if (pageContent != null) return pageContent.toString();
        return "";
    }

    private Integer extractIntFromPayload(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null) return 0;
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private List<SearchResult> mergeAndSortResults(List<SearchResult> allResults, int topK) {
        if (allResults.isEmpty()) {
            return allResults;
        }

        Map<String, List<SearchResult>> byCollection = allResults.stream()
                .collect(Collectors.groupingBy(SearchResult::getCollectionId));

        List<SearchResult> normalized = new ArrayList<>();
        for (Map.Entry<String, List<SearchResult>> entry : byCollection.entrySet()) {
            List<SearchResult> collectionResults = entry.getValue();

            // 单结果集保留原始分数，避免 min-max 归一化归零
            if (collectionResults.size() == 1) {
                normalized.add(collectionResults.get(0));
                continue;
            }

            double maxScore = collectionResults.stream().mapToDouble(SearchResult::getScore).max().orElse(1.0);
            double minScore = collectionResults.stream().mapToDouble(SearchResult::getScore).min().orElse(0.0);
            double range = maxScore - minScore;
            if (range == 0) range = 1.0;

            for (SearchResult result : collectionResults) {
                double normalizedScore = (result.getScore() - minScore) / range;
                normalized.add(SearchResult.builder()
                        .id(result.getId())
                        .documentId(result.getDocumentId())
                        .collectionId(result.getCollectionId())
                        .text(result.getText())
                        .score(normalizedScore)
                        .title(result.getTitle())
                        .snippet(result.getSnippet())
                        .metadata(result.getMetadata())
                        .startPage(result.getStartPage())
                        .endPage(result.getEndPage())
                        .build());
            }
        }

        Map<String, SearchResult> deduplicated = new LinkedHashMap<>();
        normalized.stream()
                .sorted(Comparator.comparing(SearchResult::getScore).reversed())
                .limit(topK)
                .forEach(r -> deduplicated.putIfAbsent(r.getId(), r));

        return new ArrayList<>(deduplicated.values());
    }

    private List<Double> convertToDoubleList(float[] array) {
        List<Double> list = new ArrayList<>(array.length);
        for (float v : array) {
            list.add((double) v);
        }
        return list;
    }

    /**
     * Build Qdrant REST API filter from flat key-value map.
     * Supports String (keyword match), Number (range match), and List&lt;String&gt; (each item as must condition) values.
     * Produces Qdrant REST filter format:
     * {"must": [{"key": "field", "match": {"keyword": "value"}}]}
     */
    private Map<String, Object> buildQdrantRestFilter(Map<String, Object> filter) {
        List<Map<String, Object>> mustConditions = new ArrayList<>();

        for (Map.Entry<String, Object> entry : filter.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof String s) {
                mustConditions.add(Map.of(
                        "key", key,
                        "match", Map.of("value", s)
                ));
            } else if (value instanceof Number n) {
                mustConditions.add(Map.of(
                        "key", key,
                        "range", Map.of("gte", n.doubleValue(), "lte", n.doubleValue())
                ));
            } else if (value instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof String s) {
                        mustConditions.add(Map.of(
                                "key", key,
                                "match", Map.of("value", s)
                        ));
                    }
                }
            }
        }

        return mustConditions.isEmpty() ? Map.of() : Map.of("must", mustConditions);
    }
}
