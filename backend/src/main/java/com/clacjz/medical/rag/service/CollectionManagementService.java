package com.clacjz.medical.rag.service;

import com.clacjz.medical.rag.config.RAGProperties;
import com.clacjz.medical.rag.exception.CollectionAlreadyExistsException;
import com.clacjz.medical.rag.exception.CollectionNotFoundException;
import com.clacjz.medical.rag.exception.CollectionOperationException;
import com.clacjz.medical.rag.model.dto.qdrant.CollectionStats;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Collections;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.HnswConfigDiff;
import io.qdrant.client.grpc.Collections.VectorParams;
import io.qdrant.client.grpc.Collections.VectorsConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class CollectionManagementService {

    private final QdrantClient qdrantClient;
    private final RAGProperties ragProperties;

    public void createCollection(String collectionName, int vectorSize, Distance distance) {
        if (collectionExists(collectionName)) {
            throw new CollectionAlreadyExistsException(collectionName);
        }

        VectorParams vectorParams = VectorParams.newBuilder()
                .setSize(vectorSize)
                .setDistance(distance)
                .build();

        HnswConfigDiff hnswConfig = HnswConfigDiff.newBuilder()
                .setM(16)
                .setEfConstruct(100)
                .build();

        Collections.CreateCollection createRequest = Collections.CreateCollection.newBuilder()
                .setCollectionName(collectionName)
                .setVectorsConfig(
                        VectorsConfig.newBuilder()
                                .setParams(vectorParams)
                                .build()
                )
                .setHnswConfig(hnswConfig)
                .build();

        try {
            qdrantClient.createCollectionAsync(createRequest).get(30, TimeUnit.SECONDS);
            log.info("Collection创建成功: name={}, vectorSize={}, distance={}",
                    collectionName, vectorSize, distance);
        } catch (Exception e) {
            log.error("Collection创建失败: name={}", collectionName, e);
            throw new CollectionOperationException(collectionName,
                    CollectionOperationException.OperationType.CREATE, e);
        }
    }

    public boolean collectionExists(String collectionName) {
        try {
            return qdrantClient.collectionExistsAsync(collectionName).get(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("collectionExists检查失败，尝试通过getCollectionInfo确认: name={}", collectionName);
            // collectionExistsAsync 可能返回 UNIMPLEMENTED (版本兼容性问题)
            // 使用 getCollectionInfoAsync 作为 fallback
            try {
                qdrantClient.getCollectionInfoAsync(collectionName).get(3, TimeUnit.SECONDS);
                return true; // get成功说明collection存在
            } catch (Exception fallbackEx) {
                log.warn("getCollectionInfo也失败，collection可能不存在: name={}, error={}",
                        collectionName, fallbackEx.getMessage());
                return false;
            }
        }
    }

    public Collections.CollectionInfo getCollectionInfo(String collectionName) {
        if (!collectionExists(collectionName)) {
            throw new CollectionNotFoundException(collectionName);
        }

        try {
            return qdrantClient.getCollectionInfoAsync(collectionName).get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("获取Collection信息失败: name={}", collectionName, e);
            throw new CollectionOperationException(collectionName,
                    CollectionOperationException.OperationType.GET, e);
        }
    }

    public void deleteCollection(String collectionName) {
        if (!collectionExists(collectionName)) {
            throw new CollectionNotFoundException(collectionName);
        }

        try {
            qdrantClient.deleteCollectionAsync(collectionName).get(30, TimeUnit.SECONDS);
            log.info("Collection删除成功: name={}", collectionName);
        } catch (Exception e) {
            log.error("Collection删除失败: name={}", collectionName, e);
            throw new CollectionOperationException(collectionName,
                    CollectionOperationException.OperationType.DELETE, e);
        }
    }

    public List<String> listCollections() {
        try {
            return qdrantClient.listCollectionsAsync().get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("列举Collection失败", e);
            throw new CollectionOperationException("列举Collection失败", e);
        }
    }

    public CollectionStats getCollectionStats(String collectionName) {
        Collections.CollectionInfo info = getCollectionInfo(collectionName);

        return CollectionStats.builder()
                .name(collectionName)
                .pointsCount(info.getPointsCount())
                .status(info.getStatus().toString())
                .build();
    }

    public void createCollectionWithDefaultConfig(String collectionName) {
        // 先检查collection是否已存在（幂等处理）
        if (collectionExists(collectionName)) {
            log.info("Collection已存在，跳过创建: name={}", collectionName);
            return;
        }

        RAGProperties.QdrantConfig qdrantConfig = ragProperties.getQdrant();
        int vectorSize = qdrantConfig.getCollection().getVectorSize();
        Distance distance = mapDistance(qdrantConfig.getCollection().getDistance());

        try {
            createCollection(collectionName, vectorSize, distance);
        } catch (CollectionAlreadyExistsException e) {
            // 并发场景下其他线程可能已创建成功，幂等处理
            log.info("Collection已被其他线程创建，跳过: name={}", collectionName);
        }
    }

    private Distance mapDistance(String distanceType) {
        if (distanceType == null) {
            return Distance.Cosine;
        }

        return switch (distanceType.toLowerCase()) {
            case "cosine" -> Distance.Cosine;
            case "euclidean", "euclid" -> Distance.Euclid;
            case "dot", "dotproduct" -> Distance.Dot;
            default -> Distance.Cosine;
        };
    }

    public Distance parseDistance(String distance) {
        return mapDistance(distance);
    }
}
