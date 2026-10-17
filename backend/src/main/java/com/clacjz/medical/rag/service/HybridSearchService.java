package com.clacjz.medical.rag.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.clacjz.medical.rag.config.RAGProperties;
import com.clacjz.medical.rag.mapper.CollectionMapper;
import com.clacjz.medical.rag.model.dto.AccessCheckResult;
import com.clacjz.medical.rag.model.dto.BatchAccessCheckResult;
import com.clacjz.medical.rag.model.dto.SearchResult;
import com.clacjz.medical.rag.model.dto.qdrant.VectorSearchRequest;
import com.clacjz.medical.rag.model.dto.qdrant.VectorSearchResult;
import com.clacjz.medical.rag.model.entity.Collection;
import com.clacjz.medical.rag.model.entity.Document;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * 混合检索服务
 * 结合Qdrant向量检索和ES倒排索引，使用RRF(Reciprocal Rank Fusion)算法融合结果
 * 支持多知识库并行检索、NER filter、权限过滤
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HybridSearchService {

    private final VectorCrudService vectorCrudService;
    private final EsIndexService esIndexService;
    private final EmbeddingService embeddingService;
    private final CollectionMapper collectionMapper;
    private final ElasticsearchClient elasticsearchClient;
    private final DocumentPermissionService documentPermissionService;
    private final DocumentChunkService documentChunkService;
    private final com.clacjz.medical.rag.mapper.DocumentMapper documentMapper;
    private final Executor taskExecutor;
    private final RAGProperties ragProperties;

    private static final int RRF_K = 60;
    private static final int DEFAULT_TOP_K = 10;

    /**
     * 多知识库混合检索（无 filter 重载）
     */
    public List<SearchResult> search(String query, List<String> collectionIds, int topK,
                                     Double threshold, String userId) {
        return search(query, collectionIds, topK, threshold, userId, null);
    }

    /**
     * 多知识库混合检索（替代 MultiCollectionSearchService.search）
     * 每个知识库分别执行 Qdrant gRPC + ES 检索，RRF 融合后跨库聚合
     */
    public List<SearchResult> search(String query, List<String> collectionIds, int topK,
                                     Double threshold, String userId, Map<String, Object> filter) {
        log.info("[HybridSearch] search START: query={}, collectionIds={}, topK={}, userId={}, hasFilter={}",
                query, collectionIds, topK, userId, filter != null && !filter.isEmpty());

        if (collectionIds == null || collectionIds.isEmpty()) {
            log.warn("[HybridSearch] No collection IDs provided");
            return Collections.emptyList();
        }

        List<String> validIds = collectionIds.stream()
                .filter(id -> id != null && !id.trim().isEmpty())
                .collect(Collectors.toList());
        if (validIds.isEmpty()) {
            log.warn("[HybridSearch] No valid collection IDs after filtering");
            return Collections.emptyList();
        }

        List<Collection> collections = collectionMapper.selectActiveByIds(validIds);
        if (collections.isEmpty()) {
            log.warn("[HybridSearch] No active collections found");
            return Collections.emptyList();
        }

        float[] queryVector = embeddingService.embed(query);
        if (queryVector == null) {
            log.warn("[HybridSearch] Embedding returned null");
            return Collections.emptyList();
        }

        List<CompletableFuture<List<SearchResult>>> futures = collections.stream()
                .map(col -> CompletableFuture.supplyAsync(
                        () -> searchInCollection(query, queryVector, col, topK, threshold, filter),
                        taskExecutor))
                .toList();

        List<SearchResult> allResults = new ArrayList<>();
        for (int i = 0; i < futures.size(); i++) {
            try {
                List<SearchResult> results = futures.get(i).join();
                log.debug("[HybridSearch] Collection {} returned {} results",
                        collections.get(i).getId(), results.size());
                allResults.addAll(results);
            } catch (Exception e) {
                log.error("[HybridSearch] Collection search failed: {}", e.getMessage(), e);
            }
        }

        List<SearchResult> filtered = filterByPermission(allResults, userId);

        List<SearchResult> finalResults = filtered.stream()
                .sorted(Comparator.comparing(SearchResult::getScore).reversed())
                .limit(topK)
                .collect(Collectors.toList());

        log.info("[HybridSearch] search END: total={}, final={}", allResults.size(), finalResults.size());
        return finalResults;
    }

    /** 单知识库混合检索（Qdrant gRPC + ES + RRF 融合） */
    private List<SearchResult> searchInCollection(String queryText, float[] queryVector,
                                                   Collection collection, int topK,
                                                   Double threshold, Map<String, Object> filter) {
        String collectionId = collection.getId();

        double effectiveThreshold = threshold != null ? threshold : 0.3;
        if (threshold == null && collection.getConfig() != null
                && collection.getConfig().getRetrieval() != null
                && collection.getConfig().getRetrieval().getSimilarityThreshold() != null) {
            effectiveThreshold = collection.getConfig().getRetrieval().getSimilarityThreshold();
        }

        int queryLimit = Math.max(topK * 2, 20);

        // 1. Qdrant gRPC 向量检索
        List<VectorSearchResult> qdrantResults = qdrantSearch(collectionId, queryVector, queryLimit, effectiveThreshold, filter);

        // 2. ES 倒排检索
        List<RankResult> esResults = esSearch(collectionId, queryText, queryLimit);
        Map<String, Integer> esRankMap = esResults.stream()
                .collect(Collectors.toMap(
                        r -> r.documentId, r -> r.rank,
                        (a, b) -> Math.min(a, b)));

        // 3. Document-level RRF fusion (group chunks by document_id, aggregate scores)
        List<SearchResult> fusedResults = new ArrayList<>();
        Map<String, Double> docRrfScores = new LinkedHashMap<>();
        Map<String, SearchResult> docBestResult = new LinkedHashMap<>();
        int qRank = 1;
        for (VectorSearchResult qr : qdrantResults) {
            String docId = (String) qr.getPayload().get("document_id");
            double chunkRrfScore = 1.0 / (RRF_K + qRank);
            if (docId != null && esRankMap.containsKey(docId)) {
                chunkRrfScore += 1.0 / (RRF_K + esRankMap.get(docId));
            }
            // 按文档聚合 RRF 分数求和（标准 RRF 做法）
            if (docId != null) {
                docRrfScores.merge(docId, chunkRrfScore, Double::sum);
                // 保留分数最高的 chunk 作为代表
                docBestResult.putIfAbsent(docId, toSearchResult(qr, collectionId, chunkRrfScore));
            }
            qRank++;
        }

        // 构建文档级别的融合结果，使用聚合后的 RRF 分数
        for (Map.Entry<String, Double> entry : docRrfScores.entrySet()) {
            String docId = entry.getKey();
            double aggregatedScore = entry.getValue();
            SearchResult bestChunk = docBestResult.get(docId);
            if (bestChunk != null) {
                fusedResults.add(bestChunk.withScore(aggregatedScore));
            }
        }

        // 4. 处理 ES-only 文档：从 MySQL 补全 chunk 文本后加入融合
        // 限制处理数量，避免大量 ES-only 文档冲击性能
        int esOnlyLimit = Math.min(queryLimit, topK);
        Set<String> esOnlyDocIds = esRankMap.keySet().stream()
                .filter(docId -> !docRrfScores.containsKey(docId))
                .limit(esOnlyLimit)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (!esOnlyDocIds.isEmpty()) {
            log.debug("[HybridSearch] Found {} ES-only documents (limit={}), looking up chunks...",
                    esOnlyDocIds.size(), esOnlyLimit);
            Map<String, List<com.clacjz.medical.rag.model.entity.DocumentChunk>> chunksByDoc =
                    documentChunkService.getChunksByDocumentIds(new ArrayList<>(esOnlyDocIds));

            // 批量获取文档标题（避免 N+1 查询）
            Map<String, String> titlesByDocId = new HashMap<>();
            List<Document> docs = documentMapper.selectBatchIds(new ArrayList<>(esOnlyDocIds));
            for (Document doc : docs) {
                if (doc != null && doc.getTitle() != null) {
                    titlesByDocId.put(doc.getId(), doc.getTitle());
                }
            }

            for (String docId : esOnlyDocIds) {
                List<com.clacjz.medical.rag.model.entity.DocumentChunk> chunks = chunksByDoc.get(docId);
                if (chunks == null || chunks.isEmpty()) {
                    log.warn("[HybridSearch] ES found doc {} but no chunks in MySQL, skipping", docId);
                    continue;
                }
                // 使用第一个 chunk 作为代表性文本
                com.clacjz.medical.rag.model.entity.DocumentChunk firstChunk = chunks.get(0);
                if (firstChunk.getContent() == null || firstChunk.getContent().isBlank()) {
                    log.warn("[HybridSearch] ES-only doc {} has empty chunk content, skipping", docId);
                    continue;
                }
                int esRank = esRankMap.get(docId);
                // RRF score 补偿：ES-only 分数相对较低，但不应完全被忽略
                // 使用 min(Qdrant分数范围) * 补偿系数 作为补偿起点，确保 ES-only 结果可被发现
                double esBaseScore = 1.0 / (RRF_K + esRank);
                double esFactor = ragProperties.getHybridSearch().getEsOnlyScoreFactor();
                double compensatedScore = esBaseScore * esFactor;
                String content = firstChunk.getContent();
                String snippet = content.length() > 200 ? content.substring(0, 200) + "..." : content;

                fusedResults.add(SearchResult.builder()
                        .id(firstChunk.getId())
                        .documentId(docId)
                        .collectionId(collectionId)
                        .text(content)
                        .score(compensatedScore)
                        .title(titlesByDocId.getOrDefault(docId, ""))
                        .snippet(snippet)
                        .startPage(firstChunk.getStartPage())
                        .endPage(firstChunk.getEndPage())
                        .metadata(Map.of("source", "es_only", "es_rank", esRank, "total_chunks", chunks.size()))
                        .build());
            }
        }

        fusedResults.sort(Comparator.comparing(SearchResult::getScore).reversed());
        if (fusedResults.size() > topK) {
            fusedResults = fusedResults.subList(0, topK);
        }

        log.debug("[HybridSearch] Collection {}: qdrant={}, es={}, fused={}",
                collectionId, qdrantResults.size(), esResults.size(), fusedResults.size());
        return fusedResults;
    }

    /** Qdrant gRPC 向量检索 */
    private List<VectorSearchResult> qdrantSearch(String collectionId, float[] queryVector,
                                                   int limit, double scoreThreshold,
                                                   Map<String, Object> filter) {
        try {
            VectorSearchRequest request = VectorSearchRequest.builder()
                    .vector(queryVector)
                    .limit(limit)
                    .scoreThreshold(scoreThreshold)
                    .filter(filter != null && !filter.isEmpty() ? filter : null)
                    .build();
            return vectorCrudService.searchVectors(collectionId, request);
        } catch (Exception e) {
            log.warn("[HybridSearch] Qdrant search failed for {}: {}", collectionId, e);
            return Collections.emptyList();
        }
    }

    /** ES 倒排检索 */
    private List<RankResult> esSearch(String collectionId, String queryText, int limit) {
        try {
            String indexName = esIndexService.getIndexName(collectionId);

            BoolQuery.Builder boolQuery = new BoolQuery.Builder();
            if (queryText != null && !queryText.isBlank()) {
                boolQuery.must(Query.of(q -> q
                        .multiMatch(mm -> mm
                                .query(queryText)
                                .fields("title^2", "full_text", "key_sections.*")
                        )
                ));
            }

            SearchRequest request = SearchRequest.of(builder -> builder
                    .index(indexName)
                    .query(Query.of(q -> q.bool(boolQuery.build())))
                    .from(0)
                    .size(limit));

            SearchResponse<Map> response = elasticsearchClient.search(request, Map.class);

            List<RankResult> results = new ArrayList<>();
            int rank = 1;
            for (Hit<Map> hit : response.hits().hits()) {
                if (hit.source() != null) {
                    String docId = (String) hit.source().get("document_id");
                    if (docId != null) {
                        results.add(new RankResult(docId, rank++, hit.score() != null ? hit.score() : 0.0));
                    }
                }
            }
            return results;
        } catch (Exception e) {
            log.warn("[HybridSearch] ES search failed for {}: {}", collectionId, e);
            return Collections.emptyList();
        }
    }

    // 语义型：纯向量检索（仅 Qdrant，原始向量分数）

    /**
     * 纯向量检索（无 filter 重载）
     */
    public List<SearchResult> searchVectorOnly(String query, List<String> collectionIds, int topK,
                                                Double threshold, String userId) {
        return searchVectorOnly(query, collectionIds, topK, threshold, userId, null);
    }

    /**
     * 纯向量检索 — 仅 Qdrant 向量搜索，跳过 ES。返回原始 cosine similarity 分数（不经过 RRF）。
     */
    public List<SearchResult> searchVectorOnly(String query, List<String> collectionIds, int topK,
                                                Double threshold, String userId, Map<String, Object> filter) {
        log.info("[VectorOnly] search START: query={}, collectionIds={}, topK={}, userId={}, hasFilter={}",
                query, collectionIds, topK, userId, filter != null && !filter.isEmpty());

        if (collectionIds == null || collectionIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> validIds = collectionIds.stream()
                .filter(id -> id != null && !id.trim().isEmpty()).collect(Collectors.toList());
        if (validIds.isEmpty()) return Collections.emptyList();

        List<Collection> collections = collectionMapper.selectActiveByIds(validIds);
        if (collections.isEmpty()) return Collections.emptyList();

        float[] queryVector = embeddingService.embed(query);
        if (queryVector == null) return Collections.emptyList();

        List<CompletableFuture<List<SearchResult>>> futures = collections.stream()
                .map(col -> CompletableFuture.supplyAsync(
                        () -> searchVectorOnlyInCollection(queryVector, col, topK, threshold, filter),
                        taskExecutor))
                .toList();

        List<SearchResult> allResults = new ArrayList<>();
        for (var f : futures) {
            try { allResults.addAll(f.join()); }
            catch (Exception e) { log.warn("[VectorOnly] Collection search failed", e); }
        }

        List<SearchResult> filtered = filterByPermission(allResults, userId);
        List<SearchResult> finalResults = filtered.stream()
                .sorted(Comparator.comparing(SearchResult::getScore).reversed())
                .limit(topK)
                .collect(Collectors.toList());

        log.info("[VectorOnly] search END: total={}, final={}", allResults.size(), finalResults.size());
        return finalResults;
    }

    private List<SearchResult> searchVectorOnlyInCollection(float[] queryVector, Collection collection,
                                                             int topK, Double threshold,
                                                             Map<String, Object> filter) {
        String collectionId = collection.getId();
        double effectiveThreshold = threshold != null ? threshold : 0.3;
        if (threshold == null && collection.getConfig() != null
                && collection.getConfig().getRetrieval() != null
                && collection.getConfig().getRetrieval().getSimilarityThreshold() != null) {
            effectiveThreshold = collection.getConfig().getRetrieval().getSimilarityThreshold();
        }
        int queryLimit = Math.max(topK * 2, 20);

        List<VectorSearchResult> qdrantResults = qdrantSearch(collectionId, queryVector, queryLimit, effectiveThreshold, filter);

        List<SearchResult> results = new ArrayList<>();
        for (VectorSearchResult qr : qdrantResults) {
            results.add(toSearchResultWithOriginalScore(qr, collectionId));
        }
        results.sort(Comparator.comparing(SearchResult::getScore).reversed());
        if (results.size() > topK) results = results.subList(0, topK);
        return results;
    }

    // 精确匹配型：纯 ES match_phrase 检索

    /**
     * 纯 ES 精确检索 — 仅 ES match_phrase 检索，跳过 Qdrant 向量搜索。返回 BM25 分数。
     */
    public List<SearchResult> searchExactMatch(String query, List<String> collectionIds, int topK,
                                                Double threshold, String userId) {
        log.info("[ExactMatch] search START: query={}, collectionIds={}, topK={}, userId={}",
                query, collectionIds, topK, userId);

        if (collectionIds == null || collectionIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> validIds = collectionIds.stream()
                .filter(id -> id != null && !id.trim().isEmpty()).collect(Collectors.toList());
        if (validIds.isEmpty()) return Collections.emptyList();

        List<Collection> collections = collectionMapper.selectActiveByIds(validIds);
        if (collections.isEmpty()) return Collections.emptyList();

        List<CompletableFuture<List<SearchResult>>> futures = collections.stream()
                .map(col -> CompletableFuture.supplyAsync(
                        () -> searchExactMatchInCollection(query, col, topK),
                        taskExecutor))
                .toList();

        List<SearchResult> allResults = new ArrayList<>();
        for (var f : futures) {
            try { allResults.addAll(f.join()); }
            catch (Exception e) { log.warn("[ExactMatch] Collection search failed: {}", e.getMessage(), e); }
        }

        List<SearchResult> filtered = filterByPermission(allResults, userId);
        List<SearchResult> finalResults = filtered.stream()
                .sorted(Comparator.comparing(SearchResult::getScore).reversed())
                .limit(topK)
                .collect(Collectors.toList());

        log.info("[ExactMatch] search END: total={}, final={}", allResults.size(), finalResults.size());
        return finalResults;
    }

    private List<SearchResult> searchExactMatchInCollection(String queryText, Collection collection, int topK) {
        String collectionId = collection.getId();
        String indexName = esIndexService.getIndexName(collectionId);
        int queryLimit = Math.max(topK * 2, 20);

        try {
            BoolQuery.Builder boolQuery = new BoolQuery.Builder();
            if (queryText != null && !queryText.isBlank()) {
                // match_phrase 确保词序，走 ik_synonym 分词器
                boolQuery.must(Query.of(q -> q
                        .matchPhrase(mp -> mp.field("full_text").query(queryText))
                ));
                // title 上同时匹配则提权（should 在 must 存在时仅影响评分）
                boolQuery.should(Query.of(q -> q
                        .matchPhrase(mp -> mp.field("title").query(queryText).boost(2.0f))
                ));
            }

            SearchRequest request = SearchRequest.of(builder -> builder
                    .index(indexName)
                    .query(Query.of(q -> q.bool(boolQuery.build())))
                    .from(0)
                    .size(queryLimit));

            SearchResponse<Map> response = elasticsearchClient.search(request, Map.class);

            // 按 document_id 分组，取每个文档最高分 chunk
            Map<String, SearchResult> bestByDoc = new LinkedHashMap<>();
            for (Hit<Map> hit : response.hits().hits()) {
                if (hit.source() == null) continue;
                String docId = (String) hit.source().get("document_id");
                if (docId == null) continue;
                double score = hit.score() != null ? hit.score() : 0.0;

                SearchResult existing = bestByDoc.get(docId);
                if (existing != null && score <= existing.getScore()) continue;

                String text = extractExactText(hit.source());
                String title = Objects.toString(hit.source().getOrDefault("title", ""), "");
                String snippet = text.length() > 200 ? text.substring(0, 200) + "..." : text;

                bestByDoc.put(docId, SearchResult.builder()
                        .id(hit.id())
                        .documentId(docId)
                        .collectionId(collectionId)
                        .text(text)
                        .score(score)
                        .title(title)
                        .snippet(snippet)
                        .metadata(Map.of("source", "exact_match"))
                        .build());
            }

            List<SearchResult> results = new ArrayList<>(bestByDoc.values());
            results.sort(Comparator.comparing(SearchResult::getScore).reversed());
            if (results.size() > topK) results = results.subList(0, topK);
            log.debug("[ExactMatch] Collection {}: hits={}, docs={}", collectionId, response.hits().hits().size(), results.size());
            return results;
        } catch (Exception e) {
            log.warn("[ExactMatch] ES search failed for {}: {}", collectionId, e);
            return Collections.emptyList();
        }
    }

    /** 将 Qdrant VectorSearchResult 转为 SearchResult（RRF score 替换原始 score） */
    private SearchResult toSearchResult(VectorSearchResult qr, String collectionId, double rrfScore) {
        Map<String, Object> payload = qr.getPayload();
        String text = extractText(payload);
        String title = Objects.toString(payload.getOrDefault("title", ""), "");
        String snippet = text.length() > 200 ? text.substring(0, 200) + "..." : text;
        String documentId = Objects.toString(payload.getOrDefault("document_id", qr.getId()), qr.getId());

        return SearchResult.builder()
                .id(qr.getId())
                .documentId(documentId)
                .collectionId(collectionId)
                .text(text)
                .score(rrfScore)
                .title(title)
                .snippet(snippet)
                .metadata(payload)
                .startPage(extractInt(payload, "start_page"))
                .endPage(extractInt(payload, "end_page"))
                .build();
    }

    /** 将 Qdrant VectorSearchResult 转为 SearchResult（保持原始 cosine similarity 分数） */
    private SearchResult toSearchResultWithOriginalScore(VectorSearchResult qr, String collectionId) {
        Map<String, Object> payload = qr.getPayload();
        String text = extractText(payload);
        String title = Objects.toString(payload.getOrDefault("title", ""), "");
        String snippet = text.length() > 200 ? text.substring(0, 200) + "..." : text;
        String documentId = Objects.toString(payload.getOrDefault("document_id", qr.getId()), qr.getId());

        return SearchResult.builder()
                .id(qr.getId())
                .documentId(documentId)
                .collectionId(collectionId)
                .text(text)
                .score(qr.getScore())  // 使用原始向量分数，而非 RRF
                .title(title)
                .snippet(snippet)
                .metadata(payload)
                .startPage(extractInt(payload, "start_page"))
                .endPage(extractInt(payload, "end_page"))
                .build();
    }

    private static String extractText(Map<String, Object> payload) {
        for (String key : List.of("text", "content", "page_content")) {
            Object v = payload.get(key);
            if (v != null) return v.toString();
        }
        return "";
    }

    /** 从 ES hit source 中提取文本（ES 使用 full_text 字段） */
    private static String extractExactText(Map<String, Object> source) {
        for (String key : List.of("full_text", "text", "content", "page_content")) {
            Object v = source.get(key);
            if (v != null) return v.toString();
        }
        return "";
    }

    private static Integer extractInt(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null) return 0;
        if (value instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 按文档权限过滤结果（批量查询，避免 N+1） */
    private List<SearchResult> filterByPermission(List<SearchResult> results, String userId) {
        if (userId == null || userId.isBlank()) return results;

        Map<String, List<SearchResult>> byDoc = results.stream()
                .collect(Collectors.groupingBy(SearchResult::getDocumentId));

        // 批量查询权限
        Set<String> accessibleDocIds;
        try {
            BatchAccessCheckResult batchResult = documentPermissionService.batchCheckAccess(
                    userId, new ArrayList<>(byDoc.keySet()));
            if (batchResult == null || batchResult.getResults() == null) {
                return results;
            }
            accessibleDocIds = batchResult.getResults().stream()
                    .filter(BatchAccessCheckResult.DocumentAccessResult::isHasAccess)
                    .map(BatchAccessCheckResult.DocumentAccessResult::getDocumentId)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            log.warn("[HybridSearch] Batch access check failed, falling back to individual checks:", e);
            // 兜底：逐条查询
            accessibleDocIds = new HashSet<>();
            for (String docId : byDoc.keySet()) {
                try {
                    AccessCheckResult access = documentPermissionService.checkDocumentAccess(userId, docId);
                    if (access.isHasAccess()) {
                        accessibleDocIds.add(docId);
                    }
                } catch (Exception e2) {
                    log.debug("[HybridSearch] Access check failed for doc {}: {}", docId, e2.getMessage());
                }
            }
        }

        List<SearchResult> filtered = new ArrayList<>();
        for (Map.Entry<String, List<SearchResult>> entry : byDoc.entrySet()) {
            if (accessibleDocIds.contains(entry.getKey())) {
                filtered.addAll(entry.getValue());
            }
        }
        return filtered;
    }

    private record RankResult(String documentId, int rank, double originalScore) {}
}
