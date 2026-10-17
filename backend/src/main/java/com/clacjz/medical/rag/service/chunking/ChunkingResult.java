package com.clacjz.medical.rag.service.chunking;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChunkingResult {
    public String documentId;
    public int totalChars;
    public String strategy;
    public ChunkConfig config;
    public int chunkCount;
    public List<ChunkInfo> chunks;
    public LocalDateTime previewAt;

    public int chunkCount() { return chunkCount; }
    public int totalChars() { return totalChars; }
    public String strategy() { return strategy; }
    public ChunkConfig config() { return config; }
    public List<ChunkInfo> chunks() { return chunks; }
    public LocalDateTime previewAt() { return previewAt; }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChunkConfig {
        public int chunkSize;
        public int overlap;

        public int chunkSize() { return chunkSize; }
        public int overlap() { return overlap; }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChunkInfo {
        public int index;
        public String text;
        public int startPos;
        public int endPos;
        public int startPage;    // 起始页（1-based，0表示无页码信息）
        public int endPage;      // 结束页
        public String summary;   // LLM语义分块摘要（仅semantic策略有效）

        public int index() { return index; }
        public String text() { return text; }
        public int startPos() { return startPos; }
        public int endPos() { return endPos; }
        public int startPage() { return startPage; }
        public int endPage() { return endPage; }
        public String summary() { return summary; }
    }
}