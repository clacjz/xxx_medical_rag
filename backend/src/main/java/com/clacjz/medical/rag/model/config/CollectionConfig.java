package com.clacjz.medical.rag.model.config;

import com.clacjz.medical.rag.model.enums.ChunkingStrategy;
import com.clacjz.medical.rag.model.enums.IndexType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollectionConfig implements Serializable {

    private RetrievalConfig retrieval;
    private GenerationConfig generation;
    private ChunkingConfig chunking;
    private VectorConfig vector;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RetrievalConfig implements Serializable {
        @Builder.Default
        private Integer topK = 5;
        @Builder.Default
        private Double similarityThreshold = 0.7;
        @Builder.Default
        private Boolean rerankEnabled = true;
        private String rerankModel;
        @Builder.Default
        private Boolean queryExpansionEnabled = false;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GenerationConfig implements Serializable {
        @Builder.Default
        private Integer maxTokens = 2000;
        @Builder.Default
        private Double temperature = 0.7;
        @Builder.Default
        private Double topP = 0.9;
        @Builder.Default
        private Boolean streamEnabled = true;
        private String systemPrompt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChunkingConfig implements Serializable {
        @Builder.Default
        private ChunkingStrategy strategy = ChunkingStrategy.SEMANTIC;
        @Builder.Default
        private Integer chunkSize = 500;
        @Builder.Default
        private Integer chunkOverlap = 50;
        @Builder.Default
        private Integer minChunkLength = 50;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VectorConfig implements Serializable {
        @Builder.Default
        private String embeddingModel = "BGE-large-zh-v1.5";
        @Builder.Default
        private Integer vectorDimension = 1024;
        @Builder.Default
        private IndexType indexType = IndexType.HNSW;
    }

    public static CollectionConfig defaultConfig() {
        return CollectionConfig.builder()
                .retrieval(new RetrievalConfig())
                .generation(new GenerationConfig())
                .chunking(new ChunkingConfig())
                .vector(new VectorConfig())
                .build();
    }
}
