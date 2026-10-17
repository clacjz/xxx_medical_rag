package com.clacjz.medical.rag.model.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class DocumentProcessDTO {

    private String documentId;
    private String documentName;
    private String fileType;
    private Long fileSize;
    private ParseResultDTO parseResult;
    private ChunkPreviewDTO chunkPreview;
    private String tempFilePath;
    private String originalText;

    @Data
    public static class ParseResultDTO {
        private String status;
        private Integer pageCount;
        private Integer parsedPageCount;
        private Integer charCount;
        private String originalText;
        private List<PageDTO> parsedPages;
        private List<WarningDTO> warnings;
        private String errorMessage;
        private LocalDateTime parsedAt;
    }

    @Data
    public static class PageDTO {
        private Integer pageNum;
        private String text;
        private Integer charCount;
    }

    @Data
    public static class WarningDTO {
        private Integer page;
        private String message;
    }

    @Data
    public static class ChunkPreviewDTO {
        private Integer totalChars;
        private String strategy;
        private ChunkConfigDTO config;
        private Integer chunkCount;
        private List<ChunkDTO> chunks;
        private LocalDateTime previewAt;
    }

    @Data
    public static class ChunkConfigDTO {
        private Integer chunkSize;
        private Integer overlap;

        public ChunkConfigDTO() {}

        public ChunkConfigDTO(Integer chunkSize, Integer overlap) {
            this.chunkSize = chunkSize;
            this.overlap = overlap;
        }
    }

    @Data
    public static class ChunkDTO {
        private Integer index;
        private String text;
        private Integer startPos;
        private Integer endPos;
        private Integer startPage;   // 起始页
        private Integer endPage;      // 结束页

        public ChunkDTO() {}

        public ChunkDTO(Integer index, String text, Integer startPos, Integer endPos, Integer startPage, Integer endPage) {
            this.index = index;
            this.text = text;
            this.startPos = startPos;
            this.endPos = endPos;
            this.startPage = startPage;
            this.endPage = endPage;
        }
    }
}
