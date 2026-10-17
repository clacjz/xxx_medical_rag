package com.clacjz.medical.rag.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.analysis.Analyzer;
import co.elastic.clients.elasticsearch._types.analysis.TokenFilter;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.clacjz.medical.rag.config.ElasticsearchProperties;
import com.clacjz.medical.rag.model.entity.Collection;
import com.clacjz.medical.rag.model.entity.Document;
import com.clacjz.medical.rag.model.enums.DocType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

/**
 * ES文档索引服务
 * 负责将文档内容索引到Elasticsearch，支持全文检索和结构化字段查询
 *
 * 索引命名规则：knowledge_{collection_id}
 * 每个知识库对应一个独立的ES索引
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EsIndexService {

    private final ElasticsearchClient esClient;
    private final ElasticsearchProperties esProperties;
    private final ObjectMapper objectMapper;
    private final MedicalTermNormalizer medicalTermNormalizer;

    private static final String INDEX_PREFIX = "knowledge_";

    /**
     * 获取索引名称
     */
    public String getIndexName(String collectionId) {
        return INDEX_PREFIX + collectionId;
    }

    /**
     * 创建知识库索引
     * 根据设计文档8.3节的mapping结构创建
     */
    public void createCollectionIndex(Collection collection) throws IOException {
        String indexName = getIndexName(collection.getId());
        log.info("Creating ES index for collection: {}, indexName: {}", collection.getId(), indexName);

        if (indexExists(collection.getId())) {
            log.warn("ES index already exists: {}", indexName);
            return;
        }

        // 构建mapping
        Map<String, Property> properties = new HashMap<>();

        // document_id：keyword 类型
        properties.put("document_id", Property.of(p -> p.keyword(k -> k)));

        // collection_id：keyword 类型
        properties.put("collection_id", Property.of(p -> p.keyword(k -> k)));

        // title：text 类型，带 keyword 子字段
        properties.put("title", Property.of(p -> p.text(t -> t
                .fields("keyword", f -> f.keyword(k -> k))
        )));

        // doc_type：keyword 类型
        properties.put("doc_type", Property.of(p -> p.keyword(k -> k)));

        // file_type：keyword 类型
        properties.put("file_type", Property.of(p -> p.keyword(k -> k)));

        // key_sections：object 动态映射（用于 MANUAL 文档）
        properties.put("key_sections", Property.of(p -> p.object(o -> o
                .dynamic(co.elastic.clients.elasticsearch._types.mapping.DynamicMapping.True)
        )));

        // page_annotations：nested 类型
        properties.put("page_annotations", Property.of(p -> p.nested(n -> n
                .properties("page", np -> np.integer(i -> i))
                .properties("start_pos", np -> np.long_(l -> l))
                .properties("end_pos", np -> np.long_(l -> l))
                .properties("preview", np -> np.text(t -> t))
        )));

        // metadata：object 类型
        properties.put("metadata", Property.of(p -> p.object(o -> o
                .properties("source", mp -> mp.keyword(k -> k))
                .properties("uploaded_by", mp -> mp.keyword(k -> k))
                .properties("uploaded_at", mp -> mp.date(d -> d))
                .properties("parsed_at", mp -> mp.date(d -> d))
        )));

        // full_text 字段使用 ik_synonym 分词器（支持同义词扩展）
        properties.put("full_text", Property.of(p -> p.text(t -> t
                .analyzer("ik_synonym")
        )));

        TypeMapping mapping = TypeMapping.of(m -> m.properties(properties));

        // 同义词规则（与 MedicalTermNormalizer 的 medical-term-mappings.json 保持一致）
        List<String> synonymRules = medicalTermNormalizer.getSynonymGroups().stream()
                .map(group -> String.join(", ", group))
                .toList();

        CreateIndexRequest request = CreateIndexRequest.of(builder -> builder
                .index(indexName)
                .mappings(mapping)
                .settings(s -> s
                        .index(idx -> idx
                                .analysis(a -> a
                                        .analyzer("ik_synonym",
                                                Analyzer.of(an -> an
                                                        .custom(c -> c
                                                                .tokenizer("ik_max_word")
                                                                .filter(List.of("medical_synonym"))
                                                        )
                                                )
                                        )
                                        .filter("medical_synonym",
                                                TokenFilter.of(tf -> tf
                                                        .definition(d -> d
                                                                .synonymGraph(sg -> sg
                                                                        .synonyms(synonymRules)
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                )
        );

        esClient.indices().create(request);
        log.info("ES index created successfully: {}", indexName);
    }

    /**
     * 检查索引是否存在
     */
    public boolean indexExists(String collectionId) throws IOException {
        String indexName = getIndexName(collectionId);
        ExistsRequest request = ExistsRequest.of(builder -> builder.index(indexName));
        return esClient.indices().exists(request).value();
    }

    /**
     * 删除知识库索引
     */
    public void deleteCollectionIndex(String collectionId) throws IOException {
        String indexName = getIndexName(collectionId);
        log.info("Deleting ES index: {}", indexName);

        if (!indexExists(collectionId)) {
            log.warn("ES index does not exist: {}", indexName);
            return;
        }

        esClient.indices().delete(d -> d.index(indexName));
        log.info("ES index deleted: {}", indexName);
    }

    /**
     * 索引文档内容
     */
    public void indexDocument(DocumentIndexRequest request) throws IOException {
        String indexName = getIndexName(request.getCollectionId());

        Map<String, Object> doc = new HashMap<>();
        doc.put("document_id", request.getDocumentId());
        doc.put("collection_id", request.getCollectionId());
        doc.put("title", request.getTitle());
        doc.put("doc_type", request.getDocType() != null ? request.getDocType().name() : DocType.NORMAL.name());
        doc.put("file_type", request.getFileType());
        doc.put("full_text", request.getFullText());

        // key_sections：仅 MANUAL 类型
        if (request.getKeySections() != null && !request.getKeySections().isEmpty()) {
            doc.put("key_sections", request.getKeySections());
        }

        // page_annotations：仅 MANUAL 类型（来自 OCR）
        if (request.getPageAnnotations() != null && !request.getPageAnnotations().isEmpty()) {
            List<Map<String, Object>> pageAnnotationsList = request.getPageAnnotations().stream()
                    .map(pa -> {
                        Map<String, Object> m = new HashMap<>();
                        m.put("page", pa.getPage());
                        m.put("start_pos", pa.getStartPos());
                        m.put("end_pos", pa.getEndPos());
                        m.put("preview", pa.getPreview());
                        return m;
                    })
                    .toList();
            doc.put("page_annotations", pageAnnotationsList);
        }

        // metadata
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", request.getSource());
        metadata.put("uploaded_by", request.getUploadedBy());
        metadata.put("uploaded_at", request.getUploadedAt());
        metadata.put("parsed_at", request.getParsedAt());
        doc.put("metadata", metadata);

        IndexRequest<Map<String, Object>> indexRequest = IndexRequest.of(builder -> builder
                .index(indexName)
                .id(request.getDocumentId())
                .document(doc)
        );

        IndexResponse response = esClient.index(indexRequest);
        log.info("Document indexed to ES: index={}, document_id={}, result={}",
                indexName, request.getDocumentId(), response.result());
    }

    /**
     * 删除文档索引
     */
    public void deleteDocument(String collectionId, String documentId) throws IOException {
        String indexName = getIndexName(collectionId);

        DeleteRequest request = DeleteRequest.of(builder -> builder
                .index(indexName)
                .id(documentId)
        );

        esClient.delete(request);
        log.info("Document deleted from ES: index={}, document_id={}", indexName, documentId);
    }

    /**
     * 检查文档是否存在
     */
    public boolean documentExists(String collectionId, String documentId) throws IOException {
        String indexName = getIndexName(collectionId);

        GetRequest request = GetRequest.of(builder -> builder
                .index(indexName)
                .id(documentId)
        );

        GetResponse<Object> response = esClient.get(request, Object.class);
        return response.found();
    }

    /**
     * 获取文档索引内容
     */
    public Optional<DocumentIndexResponse> getDocument(String collectionId, String documentId) throws IOException {
        String indexName = getIndexName(collectionId);

        GetRequest request = GetRequest.of(builder -> builder
                .index(indexName)
                .id(documentId)
        );

        GetResponse<Map> response = esClient.get(request, Map.class);

        if (!response.found() || response.source() == null) {
            return Optional.empty();
        }

        Map<String, Object> source = response.source();
        DocumentIndexResponse docResponse = new DocumentIndexResponse();
        docResponse.setDocumentId((String) source.get("document_id"));
        docResponse.setCollectionId((String) source.get("collection_id"));
        docResponse.setTitle((String) source.get("title"));
        docResponse.setDocType(source.get("doc_type") != null ?
                DocType.valueOf((String) source.get("doc_type")) : DocType.NORMAL);
        docResponse.setFileType((String) source.get("file_type"));
        docResponse.setFullText((String) source.get("full_text"));
        docResponse.setKeySections((Map<String, Object>) source.get("key_sections"));
        docResponse.setPageAnnotations((List<Map<String, Object>>) source.get("page_annotations"));
        docResponse.setMetadata((Map<String, Object>) source.get("metadata"));

        return Optional.of(docResponse);
    }

    /**
     * 文档索引请求
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class DocumentIndexRequest {
        private String documentId;
        private String collectionId;
        private String title;
        private DocType docType;
        private String fileType;
        private String fullText;
        private Map<String, Object> keySections;
        private List<PageAnnotation> pageAnnotations;
        private String source;
        private String uploadedBy;
        private Date uploadedAt;
        private Date parsedAt;

        @lombok.Data
        @lombok.Builder
        @lombok.NoArgsConstructor
        @lombok.AllArgsConstructor
        public static class PageAnnotation {
            private Integer page;
            private Long startPos;
            private Long endPos;
            private String preview;
        }
    }

    /**
     * 文档索引响应
     */
    @lombok.Data
    public static class DocumentIndexResponse {
        private String documentId;
        private String collectionId;
        private String title;
        private DocType docType;
        private String fileType;
        private String fullText;
        private Map<String, Object> keySections;
        private List<Map<String, Object>> pageAnnotations;
        private Map<String, Object> metadata;
    }
}