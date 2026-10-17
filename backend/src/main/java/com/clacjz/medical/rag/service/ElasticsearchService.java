package com.clacjz.medical.rag.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.IndexOperation;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.clacjz.medical.rag.config.ElasticsearchProperties;
import com.clacjz.medical.rag.constant.ErrorCode;
import com.clacjz.medical.rag.exception.BizException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ElasticsearchService {

    private final ElasticsearchClient client;
    private final ElasticsearchProperties properties;
    private final ObjectMapper objectMapper;

    private static final int MAX_FROM = 10000;
    private static final int MAX_SIZE = 100;
    private static final int MAX_QUERY_LENGTH = 1000;
    private static final int MAX_BULK_SIZE = 1000;
    private static final int MAX_DOCUMENT_IDS = 100;
    private static final Pattern VALID_DOCUMENT_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,512}$");

    @PostConstruct
    public void init() {
        log.info("Elasticsearch service initialized with prefix: {}", properties.getIndexPrefix());
    }

    private void validateDocumentId(String documentId) {
        if (documentId == null || documentId.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "文档ID不能为空");
        }

        if (!VALID_DOCUMENT_ID_PATTERN.matcher(documentId).matches()) {
            throw new BizException(ErrorCode.INVALID_PARAMETER,
                    "文档ID格式无效，只能包含字母、数字、下划线和连字符，长度1-512字符");
        }
    }

    private void validateSearchParams(String queryText, int from, int size) {
        if (from < 0) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "查询偏移量不能为负数");
        }

        if (from > MAX_FROM) {
            throw new BizException(ErrorCode.INVALID_PARAMETER,
                    String.format("查询偏移量超出允许范围，最大允许值为 %d", MAX_FROM));
        }

        if (size <= 0) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "查询大小必须大于0");
        }

        if (size > MAX_SIZE) {
            log.warn("Query size {} exceeds max {}, capping to {}", size, MAX_SIZE, MAX_SIZE);
            size = MAX_SIZE;
        }

        if (queryText != null && queryText.length() > MAX_QUERY_LENGTH) {
            throw new BizException(ErrorCode.INVALID_PARAMETER,
                    String.format("查询语句过长，最大允许长度为 %d 字符", MAX_QUERY_LENGTH));
        }
    }

    public String getFullIndexName(String indexName) {
        if (indexName == null || indexName.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "索引名称不能为空");
        }

        if (indexName.length() > 255) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "索引名称过长，最大允许255字符");
        }

        return properties.getIndexPrefix() + indexName;
    }

    public boolean createIndex(String indexName, Map<String, Object> mappings) throws IOException {
        String fullIndexName = getFullIndexName(indexName);
        log.info("Creating index: {}", fullIndexName);

        if (indexExists(indexName)) {
            log.warn("Index {} already exists", fullIndexName);
            return false;
        }

        CreateIndexRequest request = CreateIndexRequest.of(builder -> builder
                .index(fullIndexName)
                .mappings(m -> m.properties(propertiesMapToMap(mappings)))
        );

        co.elastic.clients.elasticsearch.indices.CreateIndexResponse response = client.indices().create(request);
        log.info("Index {} created: {}", fullIndexName, response.acknowledged());
        return response.acknowledged();
    }

    public boolean deleteIndex(String indexName) throws IOException {
        String fullIndexName = getFullIndexName(indexName);
        log.info("Deleting index: {}", fullIndexName);

        if (!indexExists(indexName)) {
            log.warn("Index {} does not exist", fullIndexName);
            return false;
        }

        DeleteIndexRequest request = DeleteIndexRequest.of(builder -> builder
                .index(fullIndexName)
        );

        co.elastic.clients.elasticsearch.indices.DeleteIndexResponse response = client.indices().delete(request);
        log.info("Index {} deleted: {}", fullIndexName, response.acknowledged());
        return response.acknowledged();
    }

    public boolean indexExists(String indexName) throws IOException {
        String fullIndexName = getFullIndexName(indexName);
        ExistsRequest request = ExistsRequest.of(builder -> builder.index(fullIndexName));
        return client.indices().exists(request).value();
    }

    public <T> String indexDocument(String indexName, String documentId, T document) throws IOException {
        validateDocumentId(documentId);
        String fullIndexName = getFullIndexName(indexName);
        log.debug("Indexing document {} to index {}: {}", documentId, fullIndexName, document);

        IndexRequest<T> request = IndexRequest.of(builder -> builder
                .index(fullIndexName)
                .id(documentId)
                .document(document)
        );

        IndexResponse response = client.index(request);
        log.info("Document {} indexed to {} with result: {}", documentId, fullIndexName, response.result());
        return response.id();
    }

    public <T> String indexDocumentWithoutId(String indexName, T document) throws IOException {
        String documentId = UUID.randomUUID().toString();
        return indexDocument(indexName, documentId, document);
    }

    public <T> Optional<T> getDocument(String indexName, String documentId, Class<T> clazz) throws IOException {
        validateDocumentId(documentId);
        String fullIndexName = getFullIndexName(indexName);
        log.debug("Getting document {} from index {}", documentId, fullIndexName);

        GetRequest request = GetRequest.of(builder -> builder
                .index(fullIndexName)
                .id(documentId)
        );

        GetResponse<T> response = client.get(request, clazz);

        if (response.found()) {
            log.debug("Document {} found in index {}", documentId, fullIndexName);
            return Optional.ofNullable(response.source());
        } else {
            log.debug("Document {} not found in index {}", documentId, fullIndexName);
            return Optional.empty();
        }
    }

    public <T> Map<String, T> getDocuments(String indexName, List<String> documentIds, Class<T> clazz) throws IOException {
        if (documentIds == null || documentIds.isEmpty()) {
            return new HashMap<>();
        }

        if (documentIds.size() > MAX_DOCUMENT_IDS) {
            throw new BizException(ErrorCode.INVALID_PARAMETER,
                    String.format("单次查询的文档ID数量不能超过 %d", MAX_DOCUMENT_IDS));
        }

        String fullIndexName = getFullIndexName(indexName);
        log.debug("Getting {} documents from index {}", documentIds.size(), fullIndexName);

        Map<String, T> results = new HashMap<>();
        for (String id : documentIds) {
            try {
                validateDocumentId(id);
                Optional<T> doc = getDocument(indexName, id, clazz);
                doc.ifPresent(d -> results.put(id, d));
            } catch (BizException e) {
                log.warn("Invalid document ID skipped: {}", id);
            }
        }

        return results;
    }

    public <T> boolean updateDocument(String indexName, String documentId, T document, Class<T> clazz) throws IOException {
        validateDocumentId(documentId);
        String fullIndexName = getFullIndexName(indexName);
        log.debug("Updating document {} in index {}", documentId, fullIndexName);

        UpdateRequest<T, T> request = UpdateRequest.of(builder -> builder
                .index(fullIndexName)
                .id(documentId)
                .doc(document)
                .docAsUpsert(false)
        );

        UpdateResponse<T> response = client.update(request, clazz);
        log.info("Document {} updated in {} with result: {}", documentId, fullIndexName, response.result());
        return true;
    }

    public <T> boolean upsertDocument(String indexName, String documentId, T document, Class<T> clazz) throws IOException {
        validateDocumentId(documentId);
        String fullIndexName = getFullIndexName(indexName);
        log.debug("Upserting document {} in index {}", documentId, fullIndexName);

        UpdateRequest<T, T> request = UpdateRequest.of(builder -> builder
                .index(fullIndexName)
                .id(documentId)
                .doc(document)
                .docAsUpsert(true)
        );

        UpdateResponse<T> response = client.update(request, clazz);
        log.info("Document {} upserted in {} with result: {}", documentId, fullIndexName, response.result());
        return true;
    }

    public boolean deleteDocument(String indexName, String documentId) throws IOException {
        validateDocumentId(documentId);
        String fullIndexName = getFullIndexName(indexName);
        log.debug("Deleting document {} from index {}", documentId, fullIndexName);

        DeleteRequest request = DeleteRequest.of(builder -> builder
                .index(fullIndexName)
                .id(documentId)
        );

        DeleteResponse response = client.delete(request);
        log.info("Document {} deleted from {} with result: {}", documentId, fullIndexName, response.result());
        return true;
    }

    public <T> int bulkIndex(String indexName, List<?> documents) throws IOException {
        if (documents == null || documents.isEmpty()) {
            return 0;
        }

        String fullIndexName = getFullIndexName(indexName);
        log.info("Bulk indexing {} documents to index {}", documents.size(), fullIndexName);

        if (documents.size() > MAX_BULK_SIZE) {
            throw new BizException(ErrorCode.INVALID_PARAMETER,
                    String.format("批量索引的文档数量不能超过 %d", MAX_BULK_SIZE));
        }

        List<BulkOperation> operations = documents.stream()
                .map(doc -> BulkOperation.of(op -> op
                        .index(IndexOperation.of(idx -> idx
                                .index(fullIndexName)
                                .document(doc)
                        ))
                ))
                .collect(Collectors.toList());

        BulkRequest request = BulkRequest.of(builder -> builder.operations(operations));
        BulkResponse response = client.bulk(request);

        if (response.errors()) {
            log.error("Bulk indexing had errors");
            response.items().stream()
                    .filter(item -> item.error() != null)
                    .forEach(item -> log.error("Error indexing document {}: {}",
                            item.id(), item.error().reason()));
        }

        log.info("Bulk indexed {} documents with {} errors",
                documents.size(), response.items().stream()
                        .filter(item -> item.error() != null)
                        .count());

        return (int) response.items().stream()
                .filter(item -> item.error() == null)
                .count();
    }

    public <T> SearchResult<T> search(String indexName, String queryText, List<String> fields,
                                      int from, int size, Class<T> clazz) throws IOException {
        validateSearchParams(queryText, from, size);
        String fullIndexName = getFullIndexName(indexName);
        log.debug("Searching in {} with query: {}", fullIndexName, queryText);

        int effectiveSize = Math.min(size, MAX_SIZE);

        BoolQuery.Builder boolQuery = new BoolQuery.Builder();

        if (StringUtils.hasText(queryText)) {
            boolQuery.must(Query.of(q -> q
                    .multiMatch(mm -> mm
                            .query(queryText)
                            .fields(fields)
                    )
            ));
        }

        SearchRequest request = SearchRequest.of(builder -> builder
                .index(fullIndexName)
                .query(Query.of(q -> q.bool(boolQuery.build())))
                .from(from)
                .size(effectiveSize)
                .terminateAfter((long) 10000)
        );

        return executeSearch(request, clazz);
    }

    public <T> SearchResult<T> searchWithFilters(String indexName, String queryText,
                                                   Map<String, Object> filters, int from, int size,
                                                   Class<T> clazz) throws IOException {
        validateSearchParams(queryText, from, size);
        String fullIndexName = getFullIndexName(indexName);
        log.debug("Searching in {} with query: {} and filters: {}", fullIndexName, queryText, filters);

        int effectiveSize = Math.min(size, MAX_SIZE);

        BoolQuery.Builder boolQuery = new BoolQuery.Builder();

        if (StringUtils.hasText(queryText)) {
            boolQuery.must(Query.of(q -> q
                    .multiMatch(mm -> mm
                            .query(queryText)
                            .fields("title^2", "content", "summary")
                    )
            ));
        }

        if (filters != null && !filters.isEmpty()) {
            for (Map.Entry<String, Object> entry : filters.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof List) {
                    List<?> listValue = (List<?>) value;
                    boolQuery.filter(Query.of(q -> q
                            .terms(t -> t
                                    .field(entry.getKey())
                                    .terms(tv -> tv.value(listValue.stream()
                                            .map(v -> FieldValue.of(v.toString()))
                                            .collect(Collectors.toList())))
                            )
                    ));
                } else {
                    boolQuery.filter(Query.of(q -> q
                            .term(t -> t
                                    .field(entry.getKey())
                                    .value(value.toString())
                            )
                    ));
                }
            }
        }

        SearchRequest request = SearchRequest.of(builder -> builder
                .index(fullIndexName)
                .query(Query.of(q -> q.bool(boolQuery.build())))
                .from(from)
                .size(effectiveSize)
                .terminateAfter((long) 10000)
        );

        return executeSearch(request, clazz);
    }

    public <T> SearchResult<T> searchWithAggregations(String indexName, String queryText,
                                                        Map<String, Object> filters,
                                                        Map<String, Aggregation> aggregations,
                                                        int from, int size, Class<T> clazz) throws IOException {
        validateSearchParams(queryText, from, size);
        String fullIndexName = getFullIndexName(indexName);
        log.debug("Searching in {} with aggregations", fullIndexName);

        int effectiveSize = Math.min(size, MAX_SIZE);

        BoolQuery.Builder boolQuery = new BoolQuery.Builder();

        if (StringUtils.hasText(queryText)) {
            boolQuery.must(Query.of(q -> q
                    .multiMatch(mm -> mm
                            .query(queryText)
                            .fields("title^2", "content", "summary")
                    )
            ));
        }

        if (filters != null && !filters.isEmpty()) {
            for (Map.Entry<String, Object> entry : filters.entrySet()) {
                Object value = entry.getValue();
                boolQuery.filter(Query.of(q -> q
                        .term(t -> t
                                .field(entry.getKey())
                                .value(value.toString())
                        )
                ));
            }
        }

        SearchRequest.Builder requestBuilder = new SearchRequest.Builder()
                .index(fullIndexName)
                .query(Query.of(q -> q.bool(boolQuery.build())))
                .from(from)
                .size(effectiveSize)
                .terminateAfter((long) 10000);

        if (aggregations != null && !aggregations.isEmpty()) {
            aggregations.forEach(requestBuilder::aggregations);
        }

        SearchRequest request = requestBuilder.build();
        return executeSearch(request, clazz);
    }

    private <T> SearchResult<T> executeSearch(SearchRequest request, Class<T> clazz) throws IOException {
        SearchResponse<T> response = client.search(request, clazz);

        List<T> results = new ArrayList<>();
        for (Hit<T> hit : response.hits().hits()) {
            if (hit.source() != null) {
                results.add(hit.source());
            }
        }

        Map<String, Object> aggregationResults = new HashMap<>();
        if (response.aggregations() != null) {
            for (Map.Entry<String, co.elastic.clients.elasticsearch._types.aggregations.Aggregate> entry :
                    response.aggregations().entrySet()) {
                aggregationResults.put(entry.getKey(), processAggregation(entry.getValue()));
            }
        }

        return SearchResult.<T>builder()
                .hits(results)
                .total(response.hits().total() != null ? response.hits().total().value() : 0)
                .aggregations(aggregationResults)
                .took(response.took())
                .build();
    }

    private Object processAggregation(co.elastic.clients.elasticsearch._types.aggregations.Aggregate agg) {
        if (agg.isSterms()) {
            return agg.sterms().buckets().array().stream()
                    .map(bucket -> {
                        Map<String, Object> bucketMap = new HashMap<>();
                        bucketMap.put("key", bucket.key().stringValue());
                        bucketMap.put("docCount", bucket.docCount());
                        return bucketMap;
                    })
                    .collect(Collectors.toList());
        } else if (agg.isRange()) {
            return agg.range().buckets().array().stream()
                    .map(bucket -> {
                        Map<String, Object> bucketMap = new HashMap<>();
                        bucketMap.put("key", bucket.key());
                        bucketMap.put("docCount", bucket.docCount());
                        bucketMap.put("from", bucket.from());
                        bucketMap.put("to", bucket.to());
                        return bucketMap;
                    })
                    .collect(Collectors.toList());
        } else if (agg.isCardinality()) {
            return agg.cardinality().value();
        } else if (agg.isStats()) {
            return agg.stats();
        }
        return null;
    }

    private Map<String, co.elastic.clients.elasticsearch._types.mapping.Property> propertiesMapToMap(
            Map<String, Object> properties) {
        Map<String, co.elastic.clients.elasticsearch._types.mapping.Property> result = new HashMap<>();

        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            if (entry.getValue() instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> propConfig = (Map<String, Object>) entry.getValue();
                String type = (String) propConfig.getOrDefault("type", "text");

                co.elastic.clients.elasticsearch._types.mapping.Property prop =
                        switch (type) {
                            case "keyword" -> co.elastic.clients.elasticsearch._types.mapping.Property.of(
                                    p -> p.keyword(k -> k));
                            case "integer" -> co.elastic.clients.elasticsearch._types.mapping.Property.of(
                                    p -> p.integer(i -> i));
                            case "long" -> co.elastic.clients.elasticsearch._types.mapping.Property.of(
                                    p -> p.long_(l -> l));
                            case "float" -> co.elastic.clients.elasticsearch._types.mapping.Property.of(
                                    p -> p.float_(f -> f));
                            case "double" -> co.elastic.clients.elasticsearch._types.mapping.Property.of(
                                    p -> p.double_(d -> d));
                            case "boolean" -> co.elastic.clients.elasticsearch._types.mapping.Property.of(
                                    p -> p.boolean_(b -> b));
                            case "date" -> co.elastic.clients.elasticsearch._types.mapping.Property.of(
                                    p -> p.date(d -> d));
                            case "text" -> co.elastic.clients.elasticsearch._types.mapping.Property.of(
                                    p -> p.text(t -> {
                                        if (propConfig.containsKey("analyzer")) {
                                            t.analyzer((String) propConfig.get("analyzer"));
                                        }
                                        return t;
                                    }));
                            case "nested" -> co.elastic.clients.elasticsearch._types.mapping.Property.of(
                                    p -> p.nested(n -> n));
                            default -> co.elastic.clients.elasticsearch._types.mapping.Property.of(
                                    p -> p.object(o -> o));
                        };

                result.put(entry.getKey(), prop);
            }
        }

        return result;
    }

    @lombok.Data
    @lombok.Builder
    public static class SearchResult<T> {
        private List<T> hits;
        private long total;
        private Map<String, Object> aggregations;
        private long took;
    }
}
