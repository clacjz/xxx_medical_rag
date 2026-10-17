package com.clacjz.medical.rag.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.primitives.Floats;
import com.clacjz.medical.rag.exception.VectorNotFoundException;
import com.clacjz.medical.rag.exception.VectorOperationException;
import com.clacjz.medical.rag.model.dto.qdrant.VectorSearchRequest;
import com.clacjz.medical.rag.model.dto.qdrant.VectorSearchResult;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.qdrant.client.PointIdFactory;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Common;
import io.qdrant.client.grpc.JsonWithInt;
import io.qdrant.client.grpc.Points;
import io.qdrant.client.grpc.Points.PointStruct;
import io.qdrant.client.grpc.Points.ScoredPoint;
import io.qdrant.client.grpc.Points.SearchParams;
import io.qdrant.client.grpc.Points.WithPayloadSelector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class VectorCrudService {

    private final QdrantClient qdrantClient;
    private final CircuitBreaker circuitBreaker;
    private final ObjectMapper objectMapper;

    public VectorCrudService(QdrantClient qdrantClient,
                            @Qualifier("qdrantCircuitBreaker") CircuitBreaker circuitBreaker,
                            ObjectMapper objectMapper) {
        this.qdrantClient = qdrantClient;
        this.circuitBreaker = circuitBreaker;
        this.objectMapper = objectMapper;
    }

    public String insertVector(String collectionName, float[] vector, Map<String, Object> payload) {
        String pointId = UUID.randomUUID().toString();

        PointStruct point = buildPoint(pointId, vector, payload);

        try {
            qdrantClient.upsertAsync(collectionName, List.of(point))
                    .get(30, TimeUnit.SECONDS);
            log.debug("向量插入成功: collection={}, pointId={}", collectionName, pointId);
            return pointId;
        } catch (Exception e) {
            log.error("向量插入失败: collection={}", collectionName, e);
            throw new VectorOperationException("向量插入失败", e);
        }
    }

    public String upsertVector(String collectionName, float[] vector, Map<String, Object> payload, String pointId) {
        if (pointId == null || pointId.isBlank()) {
            pointId = UUID.randomUUID().toString();
        }

        log.info("Upsert向量: collection={}, pointId={}, vector.length={}, vector[0]={}",
                collectionName, pointId, vector != null ? vector.length : "null",
                vector != null && vector.length > 0 ? vector[0] : "N/A");

        PointStruct point = buildPoint(pointId, vector, payload);

        try {
            qdrantClient.upsertAsync(collectionName, List.of(point))
                    .get(30, TimeUnit.SECONDS);
            log.debug("向量Upsert成功: collection={}, pointId={}", collectionName, pointId);
            return pointId;
        } catch (Exception e) {
            log.error("向量Upsert失败: collection={}", collectionName, e);
            throw new VectorOperationException("向量Upsert失败", e);
        }
    }

    public Map<String, Object> getVector(String collectionName, String pointId) {
        try {
            List<Points.RetrievedPoint> results = qdrantClient.retrieveAsync(
                    collectionName,
                    PointIdFactory.id(UUID.fromString(pointId)),
                    true,
                    true,
                    null
            ).get(30, TimeUnit.SECONDS);

            if (results.isEmpty()) {
                throw new VectorNotFoundException(pointId);
            }

            return convertRetrievedPointToMap(results.get(0));
        } catch (VectorNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取向量失败: pointId={}", pointId, e);
            throw new VectorOperationException("获取向量失败", e);
        }
    }

    public List<VectorSearchResult> searchVectors(String collectionName, VectorSearchRequest request) {
        try {
            return circuitBreaker.executeSupplier(() -> {
                try {
                    return doSearchVectors(collectionName, request);
                } catch (Exception e) {
                    throw new RuntimeException("Vector search failed", e);
                }
            });
        } catch (Exception e) {
            if (e instanceof VectorOperationException) {
                throw (VectorOperationException) e;
            }
            log.error("向量检索失败（断路器触发）: collection={}, error={}", collectionName, e.getMessage());
            throw new VectorOperationException("向量检索失败（服务暂时不可用）", e);
        }
    }

    private List<VectorSearchResult> doSearchVectors(String collectionName, VectorSearchRequest request) throws Exception {
        SearchParams.Builder searchParamsBuilder = SearchParams.newBuilder()
                .setExact(false);

        if (request.getEf() != null) {
            searchParamsBuilder.setHnswEf(request.getEf());
        }

        Points.SearchPoints.Builder searchBuilder = Points.SearchPoints.newBuilder()
                .setCollectionName(collectionName)
                .addAllVector(Floats.asList(request.getVector()))
                .setLimit(request.getLimit())
                .setWithPayload(WithPayloadSelector.newBuilder().setEnable(true).build())
                .setParams(searchParamsBuilder.build());

        if (request.getScoreThreshold() != null) {
            searchBuilder.setScoreThreshold(request.getScoreThreshold().floatValue());
        }

        if (request.getFilter() != null && !request.getFilter().isEmpty()) {
            Common.Filter grpcFilter = buildSearchFilter(request.getFilter());
            searchBuilder.setFilter(grpcFilter);
            log.debug("Search with filter: {}", request.getFilter());
        }

        List<ScoredPoint> scoredPoints = qdrantClient.searchAsync(searchBuilder.build())
                .get(30, TimeUnit.SECONDS);

        return scoredPoints.stream()
                .map(this::convertScoredPointToResult)
                .collect(Collectors.toList());
    }

    public void updateVectorPayload(String collectionName, String pointId, Map<String, Object> payload) {
        try {
            Map<String, JsonWithInt.Value> grpcPayload = convertPayload(payload);

            qdrantClient.setPayloadAsync(
                    collectionName,
                    grpcPayload,
                    PointIdFactory.id(UUID.fromString(pointId)),
                    null,
                    null,
                    null
            ).get(30, TimeUnit.SECONDS);

            log.debug("向量Payload更新成功: pointId={}", pointId);
        } catch (Exception e) {
            log.error("向量Payload更新失败: pointId={}", pointId, e);
            throw new VectorOperationException("更新向量Payload失败", e);
        }
    }

    public void deleteVector(String collectionName, String pointId) {
        try {
            qdrantClient.deleteAsync(
                    collectionName,
                    List.of(PointIdFactory.id(UUID.fromString(pointId)))
            ).get(30, TimeUnit.SECONDS);

            log.debug("向量删除成功: pointId={}", pointId);
        } catch (Exception e) {
            log.error("向量删除失败: pointId={}", pointId, e);
            throw new VectorOperationException("删除向量失败", e);
        }
    }

    public void deleteVectorsByFilter(String collectionName, Map<String, Object> filter) {
        try {
            Common.Filter grpcFilter = buildSearchFilter(filter);

            qdrantClient.deleteAsync(collectionName, grpcFilter).get(30, TimeUnit.SECONDS);

            log.info("按条件删除向量完成: collection={}", collectionName);
        } catch (Exception e) {
            log.error("批量删除向量失败: collection={}", collectionName, e);
            throw new VectorOperationException("批量删除向量失败", e);
        }
    }

    public void deleteAllVectors(String collectionName) {
        try {
            qdrantClient.deleteAsync(collectionName, Common.Filter.newBuilder().build())
                    .get(30, TimeUnit.SECONDS);

            log.info("删除Collection所有向量完成: collection={}", collectionName);
        } catch (Exception e) {
            log.error("删除所有向量失败: collection={}", collectionName, e);
            throw new VectorOperationException("删除所有向量失败", e);
        }
    }

    public void deleteByDocumentId(String collectionName, String documentId) {
        deleteVectorsByFilter(collectionName, Map.of("document_id", documentId));
        log.info("按documentId删除向量完成: collection={}, documentId={}", collectionName, documentId);
    }

    public long getCollectionPointCount(String collectionName) {
        try {
            return qdrantClient.getCollectionInfoAsync(collectionName)
                    .get(30, TimeUnit.SECONDS).getPointsCount();
        } catch (Exception e) {
            log.error("获取Collection点数失败: collection={}", collectionName, e);
            return -1;
        }
    }

    /**
     * 滚动获取Collection中所有点的指定payload值
     * 用于清理孤立向量等场景
     *
     * @param collectionName 集合名
     * @param payloadKey 要提取的payload字段名
     * @return 该字段值的集合
     */
    public Set<String> scrollAllPayloadValues(String collectionName, String payloadKey) {
        Set<String> values = new HashSet<>();
        String scrollOffset = null;

        try {
            do {
                var scrollRequest = Points.ScrollPoints.newBuilder()
                        .setCollectionName(collectionName)
                        .setWithPayload(WithPayloadSelector.newBuilder().setEnable(true).build())
                        .setLimit(1000);

                if (scrollOffset != null) {
                    scrollRequest.setOffset(Common.PointId.newBuilder().setUuid(scrollOffset).build());
                }

                var scrollResult = qdrantClient.scrollAsync(scrollRequest.build())
                        .get(30, TimeUnit.SECONDS);

                for (var point : scrollResult.getResultList()) {
                    var payload = point.getPayloadMap();
                    if (payload.containsKey(payloadKey)) {
                        values.add(payload.get(payloadKey).getStringValue());
                    }
                }

                if (scrollResult.getNextPageOffset() != null) {
                    scrollOffset = scrollResult.getNextPageOffset().getUuid();
                } else {
                    scrollOffset = null;
                }

            } while (scrollOffset != null);

        } catch (Exception e) {
            log.error("滚动获取Collection payload失败: collection={}", collectionName, e);
        }

        return values;
    }

    private PointStruct buildPoint(String pointId, float[] vector, Map<String, Object> payload) {
        // 显式使用 DenseVector 构造 Vectors，保证兼容性
        Points.Vectors vectors = Points.Vectors.newBuilder()
                .setVector(Points.Vector.newBuilder()
                        .setDense(Points.DenseVector.newBuilder()
                                .addAllData(Floats.asList(vector))
                                .build())
                        .build())
                .build();

        return PointStruct.newBuilder()
                .setId(PointIdFactory.id(UUID.fromString(pointId)))
                .setVectors(vectors)
                .putAllPayload(convertPayload(payload))
                .build();
    }

    private Map<String, JsonWithInt.Value> convertPayload(Map<String, Object> payload) {
        if (payload == null) {
            return Map.of();
        }

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
        } else if (value instanceof Map<?, ?> map) {
            // 将 Map 序列化为 JSON 字符串而不是调用 toString()
            try {
                String jsonStr = objectMapper.writeValueAsString(map);
                return builder.setStringValue(jsonStr).build();
            } catch (Exception e) {
                log.warn("Failed to serialize map to JSON:", e);
                return builder.setStringValue(value != null ? value.toString() : "").build();
            }
        }

        return builder.setStringValue(value != null ? value.toString() : "").build();
    }

    private Map<String, Object> convertRetrievedPointToMap(Points.RetrievedPoint point) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", point.getId().getUuid());

        Map<String, Object> payload = new HashMap<>();
        for (Map.Entry<String, JsonWithInt.Value> entry : point.getPayloadMap().entrySet()) {
            payload.put(entry.getKey(), convertValueFromGrpc(entry.getValue()));
        }
        result.put("payload", payload);

        if (point.getVectors().getVectorsOptionsCase() == Points.VectorsOutput.VectorsOptionsCase.VECTOR) {
            var denseVector = point.getVectors().getVector();
            float[] vector = new float[denseVector.getDataCount()];
            for (int i = 0; i < denseVector.getDataCount(); i++) {
                vector[i] = denseVector.getData(i);
            }
            result.put("vector", vector);
        }

        return result;
    }

    private VectorSearchResult convertScoredPointToResult(ScoredPoint point) {
        Map<String, Object> payload = new HashMap<>();
        for (Map.Entry<String, JsonWithInt.Value> entry : point.getPayloadMap().entrySet()) {
            payload.put(entry.getKey(), convertValueFromGrpc(entry.getValue()));
        }

        return VectorSearchResult.builder()
                .id(point.getId().getUuid())
                .score((double) point.getScore())
                .payload(payload)
                .build();
    }

    private Object convertValueFromGrpc(JsonWithInt.Value value) {
        return switch (value.getKindCase()) {
            case STRING_VALUE -> value.getStringValue();
            case DOUBLE_VALUE -> value.getDoubleValue();
            case BOOL_VALUE -> value.getBoolValue();
            case INTEGER_VALUE -> value.getIntegerValue();
            default -> value.toString();
        };
    }

    /**
     * Build a Qdrant filter for vector search from a simple key-value map.
     * Build a Qdrant filter from a map of conditions.
     * <p>
     * Supports:
     * <ul>
     *   <li>String values: keyword match (exact match) — uses must (AND)</li>
     *   <li>Number values: range match (GTE=LTE for exact match) — uses must (AND)</li>
     *   <li>List&lt;String&gt; values: any-item match — uses should (OR), then wrapped in must</li>
     * </ul>
     * <p>
     * <b>Important:</b> Different keys are always AND-combined. For the same key:
     * <ul>
     *   <li>A single String value requires exact match (AND)</li>
     *   <li>A List&lt;String&gt; value matches any item (OR), equivalent to SQL IN</li>
     * </ul>
     * Example: filter={"doc_type":"MANUAL", "drug_names":["阿司匹林","布洛芬"]}
     * → doc_type="MANUAL" AND (drug_names="阿司匹林" OR drug_names="布洛芬")
     * <p>
     * If you need AND semantics for list values (all items must match), do NOT use List —
     * instead, add multiple single-value entries with the same key via a custom filter builder.
     */
    private Common.Filter buildSearchFilter(Map<String, Object> filter) {
        Common.Filter.Builder filterBuilder = Common.Filter.newBuilder();

        for (Map.Entry<String, Object> entry : filter.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof String s) {
                filterBuilder.addMust(Common.Condition.newBuilder()
                        .setField(Common.FieldCondition.newBuilder()
                                .setKey(key)
                                .setMatch(Common.Match.newBuilder()
                                        .setKeyword(s)
                                        .build())
                                .build())
                        .build());
            } else if (value instanceof Number n) {
                filterBuilder.addMust(Common.Condition.newBuilder()
                        .setField(Common.FieldCondition.newBuilder()
                                .setKey(key)
                                .setRange(Common.Range.newBuilder()
                                        .setGte(n.doubleValue())
                                        .setLte(n.doubleValue())
                                        .build())
                                .build())
                        .build());
            } else if (value instanceof List<?> list) {
                // List 值使用 should（OR）：任一元素匹配即可
                Common.Filter.Builder orFilter = Common.Filter.newBuilder();
                for (Object item : list) {
                    if (item instanceof String s) {
                        orFilter.addShould(Common.Condition.newBuilder()
                                .setField(Common.FieldCondition.newBuilder()
                                        .setKey(key)
                                        .setMatch(Common.Match.newBuilder()
                                                .setKeyword(s)
                                                .build())
                                        .build())
                                .build());
                    }
                }
                // 将 OR filter 包装为单个 must 条件
                if (orFilter.getShouldCount() > 0) {
                    filterBuilder.addMust(Common.Condition.newBuilder()
                            .setFilter(orFilter.build())
                            .build());
                }
            }
        }

        return filterBuilder.build();
    }
}
