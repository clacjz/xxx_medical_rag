package com.clacjz.medical.rag.service;

import com.clacjz.medical.rag.config.OcrProperties;
import com.clacjz.medical.rag.config.RAGProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigRefreshService {

    private final RAGProperties ragProperties;
    private final OcrProperties ocrProperties;

    public void refreshConfig(String category, String configKey, String value) {
        log.info("Refreshing config: {} = {}", category + "." + configKey, value);

        switch (category) {
            case "embedding" -> refreshEmbeddingConfig(configKey, value);
            case "llm" -> refreshLlmConfig(configKey, value);
            case "qdrant" -> refreshQdrantConfig(configKey, value);
            case "minio" -> refreshMinioConfig(configKey, value);
            case "ocr" -> refreshOcrConfig(configKey, value);
            case "etcd" -> refreshEtcdConfig(configKey, value);
            case "elasticsearch" -> refreshElasticsearchConfig(configKey, value);
            default -> log.debug("No handler for config category: {}", category);
        }
    }

    private void refreshEmbeddingConfig(String key, String value) {
        RAGProperties.EmbeddingConfig embedding = ragProperties.getEmbedding();
        switch (key) {
            case "host" -> embedding.setHost(value);
            case "model" -> embedding.setModel(value);
            case "timeout" -> embedding.setTimeout(Integer.parseInt(value));
            case "dimension" -> embedding.setDimension(Integer.parseInt(value));
            default -> log.debug("Unknown embedding config key: {}", key);
        }
        log.info("Embedding config refreshed: {} = {}", key, value);
    }

    private void refreshLlmConfig(String key, String value) {
        RAGProperties.LLMConfig llm = ragProperties.getLlm();
        switch (key) {
            case "host" -> llm.setHost(value);
            case "model" -> llm.setModel(value);
            case "timeout" -> llm.setTimeout(Integer.parseInt(value));
            case "temperature" -> llm.setTemperature(Double.parseDouble(value));
            case "maxTokens" -> llm.setMaxTokens(Integer.parseInt(value));
            case "topP" -> llm.setTopP(Integer.parseInt(value));
            case "topK" -> llm.setTopK(Integer.parseInt(value));
            case "minP" -> llm.setMinP((int) Double.parseDouble(value));
            case "presencePenalty" -> llm.setPresencePenalty(Double.parseDouble(value));
            case "repetitionPenalty" -> llm.setRepetitionPenalty(Double.parseDouble(value));
            default -> log.debug("Unknown LLM config key: {}", key);
        }
        log.info("LLM config refreshed: {} = {}", key, value);
    }

    private void refreshQdrantConfig(String key, String value) {
        RAGProperties.QdrantConfig qdrant = ragProperties.getQdrant();
        switch (key) {
            case "host" -> qdrant.setHost(value);
            case "timeout" -> qdrant.setTimeout(Integer.parseInt(value));
            case "maxConnections" -> qdrant.setMaxConnections(Integer.parseInt(value));
            case "idleTimeout" -> qdrant.setIdleTimeout(Integer.parseInt(value));
            case "collection.defaultName" -> qdrant.getCollection().setDefaultName(value);
            case "collection.vectorSize" -> qdrant.getCollection().setVectorSize(Integer.parseInt(value));
            case "collection.distance" -> qdrant.getCollection().setDistance(value);
            default -> log.debug("Unknown Qdrant config key: {}", key);
        }
        log.info("Qdrant config refreshed: {} = {}", key, value);
    }

    private void refreshMinioConfig(String key, String value) {
        RAGProperties.MinIOConfig minio = ragProperties.getMinio();
        switch (key) {
            case "host" -> minio.setHost(value);
            case "accessKey" -> minio.setAccessKey(value);
            case "secretKey" -> minio.setSecretKey(value);
            case "bucket.documents" -> minio.getBucket().setDocuments(value);
            case "bucket.temp" -> minio.getBucket().setTemp(value);
            default -> log.debug("Unknown MinIO config key: {}", key);
        }
        log.info("MinIO config refreshed: {} = {}", key, value);
    }

    private void refreshOcrConfig(String key, String value) {
        switch (key) {
            case "serviceUrl" -> ocrProperties.setServiceUrl(value);
            case "fallbackUrl" -> ocrProperties.setFallbackUrl(value);
            case "timeout" -> ocrProperties.setTimeout(Integer.parseInt(value));
            case "maxCacheTtl" -> ocrProperties.setMaxCacheTtl(Integer.parseInt(value));
            case "maxPages" -> ocrProperties.setMaxPages(Integer.parseInt(value));
            case "enabled" -> ocrProperties.setEnabled(Boolean.parseBoolean(value));
            default -> log.debug("Unknown OCR config key: {}", key);
        }
        log.info("OCR config refreshed: {} = {}", key, value);
    }

    private void refreshEtcdConfig(String key, String value) {
        RAGProperties.EtcdConfig etcd = ragProperties.getEtcd();
        switch (key) {
            case "host" -> etcd.setHost(value);
            case "timeout" -> etcd.setTimeout(Integer.parseInt(value));
            default -> log.debug("Unknown Etcd config key: {}", key);
        }
        log.info("Etcd config refreshed: {} = {}", key, value);
    }

    private void refreshElasticsearchConfig(String key, String value) {
        RAGProperties.ElasticsearchConfig es = ragProperties.getElasticsearch();
        switch (key) {
            case "host" -> es.setHost(value);
            case "connectionTimeout" -> es.setConnectionTimeout(Integer.parseInt(value));
            case "socketTimeout" -> es.setSocketTimeout(Integer.parseInt(value));
            default -> log.debug("Unknown Elasticsearch config key: {}", key);
        }
        log.info("Elasticsearch config refreshed: {} = {}", key, value);
    }
}