package com.clacjz.medical.rag.util;

import com.google.common.primitives.Floats;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.PointIdFactory;
import io.qdrant.client.VectorOutputHelper;
import io.qdrant.client.VectorsFactory;
import io.qdrant.client.grpc.Collections;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.VectorParams;
import io.qdrant.client.grpc.Common;
import io.qdrant.client.grpc.Common.PointId;
import io.qdrant.client.grpc.JsonWithInt;
import io.qdrant.client.grpc.Points;
import io.qdrant.client.grpc.Points.DenseVector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class QdrantTestUtil {

    private final QdrantClient qdrantClient;

    public void createCollection(String collectionName, int vectorSize) {
        if (collectionName == null || collectionName.isBlank()) {
            throw new IllegalArgumentException("Collection name cannot be null or empty");
        }
        if (vectorSize <= 0) {
            throw new IllegalArgumentException("Vector size must be positive");
        }

        try {
            qdrantClient.createCollectionAsync(collectionName,
                    VectorParams.newBuilder()
                            .setSize(vectorSize)
                            .setDistance(Distance.Cosine)
                            .build()).get();

            log.info("Collection created successfully: {}, vector dimension: {}", collectionName, vectorSize);
        } catch (Exception e) {
            log.error("Failed to create collection: {}", collectionName, e);
            throw new RuntimeException("Failed to create collection: " + e.getMessage(), e);
        }
    }

    public void deleteCollection(String collectionName) {
        if (collectionName == null || collectionName.isBlank()) {
            throw new IllegalArgumentException("Collection name cannot be null or empty");
        }

        try {
            qdrantClient.deleteCollectionAsync(collectionName).get();
            log.info("Collection deleted successfully: {}", collectionName);
        } catch (Exception e) {
            log.error("Failed to delete collection: {}", collectionName, e);
            throw new RuntimeException("Failed to delete collection: " + e.getMessage(), e);
        }
    }

    public boolean collectionExists(String collectionName) {
        if (collectionName == null || collectionName.isBlank()) {
            throw new IllegalArgumentException("Collection name cannot be null or empty");
        }

        try {
            return qdrantClient.collectionExistsAsync(collectionName).get();
        } catch (Exception e) {
            log.error("Failed to check collection existence: {}", collectionName, e);
            return false;
        }
    }

    public String insertVector(String collectionName, float[] vector, Map<String, Object> payload) {
        if (collectionName == null || collectionName.isBlank()) {
            throw new IllegalArgumentException("Collection name cannot be null or empty");
        }
        if (vector == null || vector.length == 0) {
            throw new IllegalArgumentException("Vector cannot be null or empty");
        }

        String pointId = UUID.randomUUID().toString();
        Points.PointStruct point = Points.PointStruct.newBuilder()
                .setId(PointIdFactory.id(UUID.fromString(pointId)))
                .setVectors(VectorsFactory.vectors(vector))
                .putAllPayload(convertPayload(payload))
                .build();

        try {
            qdrantClient.upsertAsync(collectionName, List.of(point)).get();

            log.info("Vector inserted successfully, Collection: {}, PointId: {}", collectionName, pointId);
            return pointId;
        } catch (Exception e) {
            log.error("Failed to insert vector: {}", collectionName, e);
            throw new RuntimeException("Failed to insert vector: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> getVector(String collectionName, String pointId) {
        if (collectionName == null || collectionName.isBlank()) {
            throw new IllegalArgumentException("Collection name cannot be null or empty");
        }
        if (pointId == null || pointId.isBlank()) {
            throw new IllegalArgumentException("Point ID cannot be null or empty");
        }

        try {
            List<Points.RetrievedPoint> results = qdrantClient.retrieveAsync(
                    collectionName,
                    PointIdFactory.id(UUID.fromString(pointId)),
                    true,
                    true,
                    null
            ).get();

            if (results.isEmpty()) {
                throw new RuntimeException("Vector not found: " + pointId);
            }

            Points.RetrievedPoint point = results.get(0);
            return convertRetrievedPointToMap(point);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("not found")) {
                throw (RuntimeException) e;
            }
            log.error("Failed to get vector: pointId={}", pointId, e);
            throw new RuntimeException("Failed to get vector: " + e.getMessage(), e);
        }
    }

    public void updateVectorPayload(String collectionName, String pointId, Map<String, Object> payload) {
        if (collectionName == null || collectionName.isBlank()) {
            throw new IllegalArgumentException("Collection name cannot be null or empty");
        }
        if (pointId == null || pointId.isBlank()) {
            throw new IllegalArgumentException("Point ID cannot be null or empty");
        }

        try {
            qdrantClient.setPayloadAsync(
                    collectionName,
                    convertPayload(payload),
                    PointIdFactory.id(UUID.fromString(pointId)),
                    null,
                    null,
                    null
            ).get();

            log.info("Vector payload updated successfully: {}", pointId);
        } catch (Exception e) {
            log.error("Failed to update vector payload: {}", pointId, e);
            throw new RuntimeException("Failed to update vector payload: " + e.getMessage(), e);
        }
    }

    public void deleteVector(String collectionName, String pointId) {
        if (collectionName == null || collectionName.isBlank()) {
            throw new IllegalArgumentException("Collection name cannot be null or empty");
        }
        if (pointId == null || pointId.isBlank()) {
            throw new IllegalArgumentException("Point ID cannot be null or empty");
        }

        try {
            qdrantClient.deleteAsync(collectionName, List.of(PointIdFactory.id(UUID.fromString(pointId)))).get();

            log.info("Vector deleted successfully: {}", pointId);
        } catch (Exception e) {
            log.error("Failed to delete vector: {}", pointId, e);
            throw new RuntimeException("Failed to delete vector: " + e.getMessage(), e);
        }
    }

    public List<Map<String, Object>> searchVectors(String collectionName, float[] queryVector, int limit) {
        if (collectionName == null || collectionName.isBlank()) {
            throw new IllegalArgumentException("Collection name cannot be null or empty");
        }
        if (queryVector == null || queryVector.length == 0) {
            throw new IllegalArgumentException("Query vector cannot be null or empty");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("Limit must be positive");
        }

        try {
            List<Points.ScoredPoint> results = qdrantClient.searchAsync(
                    Points.SearchPoints.newBuilder()
                            .setCollectionName(collectionName)
                            .addAllVector(Floats.asList(queryVector))
                            .setLimit(limit)
                            .setWithPayload(Points.WithPayloadSelector.newBuilder().setEnable(true).build())
                            .build()
            ).get();

            return results.stream()
                    .map(this::convertScoredPointToMap)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to search vectors: {}", collectionName, e);
            throw new RuntimeException("Failed to search vectors: " + e.getMessage(), e);
        }
    }

    public long getCollectionPointCount(String collectionName) {
        if (collectionName == null || collectionName.isBlank()) {
            throw new IllegalArgumentException("Collection name cannot be null or empty");
        }

        try {
            Collections.CollectionInfo info = qdrantClient.getCollectionInfoAsync(collectionName).get();
            return info.getPointsCount();
        } catch (Exception e) {
            log.error("Failed to get collection info: {}", collectionName, e);
            return -1;
        }
    }

    public List<String> listCollections() {
        try {
            return qdrantClient.listCollectionsAsync().get();
        } catch (Exception e) {
            log.error("Failed to list collections", e);
            throw new RuntimeException("Failed to list collections: " + e.getMessage(), e);
        }
    }

    public void deleteAllVectors(String collectionName) {
        if (collectionName == null || collectionName.isBlank()) {
            throw new IllegalArgumentException("Collection name cannot be null or empty");
        }

        try {
            qdrantClient.deleteAsync(collectionName,
                    Common.Filter.newBuilder().build()
            ).get();
            log.info("Deleted all vectors successfully: {}", collectionName);
        } catch (Exception e) {
            log.error("Failed to delete all vectors: {}", collectionName, e);
            throw new RuntimeException("Failed to delete all vectors: " + e.getMessage(), e);
        }
    }

    public String upsertVector(String collectionName, float[] vector, Map<String, Object> payload, String pointId) {
        if (collectionName == null || collectionName.isBlank()) {
            throw new IllegalArgumentException("Collection name cannot be null or empty");
        }
        if (vector == null || vector.length == 0) {
            throw new IllegalArgumentException("Vector cannot be null or empty");
        }
        if (pointId == null || pointId.isBlank()) {
            pointId = UUID.randomUUID().toString();
        }

        Points.PointStruct point = Points.PointStruct.newBuilder()
                .setId(PointIdFactory.id(UUID.fromString(pointId)))
                .setVectors(VectorsFactory.vectors(vector))
                .putAllPayload(convertPayload(payload))
                .build();

        try {
            qdrantClient.upsertAsync(collectionName, List.of(point)).get();

            log.info("Vector upserted successfully, Collection: {}, PointId: {}", collectionName, pointId);
            return pointId;
        } catch (Exception e) {
            log.error("Failed to upsert vector: {}", collectionName, e);
            throw new RuntimeException("Failed to upsert vector: " + e.getMessage(), e);
        }
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
            DenseVector denseVector = VectorOutputHelper.getDenseVector(point.getVectors().getVector());
            if (denseVector != null) {
                float[] vector = new float[denseVector.getDataCount()];
                for (int i = 0; i < denseVector.getDataCount(); i++) {
                    vector[i] = denseVector.getData(i);
                }
                result.put("vector", vector);
            }
        }

        return result;
    }

    private Map<String, Object> convertScoredPointToMap(Points.ScoredPoint point) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", point.getId().getUuid());

        Map<String, Object> payload = new HashMap<>();
        for (Map.Entry<String, JsonWithInt.Value> entry : point.getPayloadMap().entrySet()) {
            payload.put(entry.getKey(), convertValueFromGrpc(entry.getValue()));
        }
        result.put("payload", payload);

        if (point.getVectors().getVectorsOptionsCase() == Points.VectorsOutput.VectorsOptionsCase.VECTOR) {
            DenseVector denseVector = VectorOutputHelper.getDenseVector(point.getVectors().getVector());
            if (denseVector != null) {
                float[] vector = new float[denseVector.getDataCount()];
                for (int i = 0; i < denseVector.getDataCount(); i++) {
                    vector[i] = denseVector.getData(i);
                }
                result.put("vector", vector);
            }
        }

        result.put("score", point.getScore());
        return result;
    }

    private Object convertValueFromGrpc(JsonWithInt.Value value) {
        switch (value.getKindCase()) {
            case STRING_VALUE:
                return value.getStringValue();
            case DOUBLE_VALUE:
                return value.getDoubleValue();
            case BOOL_VALUE:
                return value.getBoolValue();
            case INTEGER_VALUE:
                return value.getIntegerValue();
            default:
                return value.toString();
        }
    }
}
