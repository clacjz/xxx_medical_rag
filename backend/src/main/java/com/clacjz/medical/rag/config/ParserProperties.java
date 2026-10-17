package com.clacjz.medical.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "rag.parser")
public class ParserProperties {

    private String tempDir = "/tmp/rag-parser";

    private long maxFileSize = 100 * 1024 * 1024;

    private String[] supportedTypes = {"pdf", "doc", "docx", "xlsx", "xls", "ppt", "pptx", "md", "txt"};

    private int maxPreviewChars = 50000;

    private int maxOcrPages = 50;

    private ChunkConfig chunk = new ChunkConfig();

    private CleanupConfig cleanup = new CleanupConfig();

    private MinioConfig minio = new MinioConfig();

    @Data
    public static class ChunkConfig {
        /**
         * BGE-M3 优化配置（基于 8192 max sequence length）：
         * - chunk size: 4000 字符（约 2000 tokens）
         * - overlap: 400 字符（约 200 tokens）
         * 保留足够安全边界以确保 embedding 质量
         */
        private int size = 4000;
        private int overlap = 400;
    }

    @Data
    public static class CleanupConfig {
        private boolean enabled = true;
        private int retentionHours = 24;
    }

    @Data
    public static class MinioConfig {
        private BucketConfig bucket = new BucketConfig();

        @Data
        public static class BucketConfig {
            private String documents = "rag-documents";
            private String temp = "rag-temp";
        }
    }
}
