package com.clacjz.medical.rag.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.clacjz.medical.rag.config.ParserProperties;
import com.clacjz.medical.rag.config.RAGProperties;
import com.clacjz.medical.rag.mapper.CollectionMapper;
import com.clacjz.medical.rag.model.entity.Document;
import com.clacjz.medical.rag.mapper.DocumentMapper;
import com.clacjz.medical.rag.model.dto.ner.NerEntity;
import com.clacjz.medical.rag.model.dto.ner.NerResponse;
import com.clacjz.medical.rag.model.enums.DocType;
import com.clacjz.medical.rag.service.chunking.ChunkingResult;
import com.clacjz.medical.rag.service.chunking.ChunkingService;
import com.clacjz.medical.rag.service.parser.DocumentParserFactory;
import com.clacjz.medical.rag.service.parser.PageLayout;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentParseService {

    private final DocumentMapper documentMapper;
    private final CollectionMapper collectionMapper;
    private final DocumentParserFactory parserFactory;
    private final ChunkingService chunkingService;
    private final TempFileCleanupService tempFileCleanupService;
    private final MinioClient minioClient;
    private final RAGProperties ragProperties;
    private final ParserProperties parserProperties;
    private final ObjectMapper objectMapper;
    private final OcrService ocrService;
    private final LLMService llmService;
    private final DocSchemaService docSchemaService;
    private final NerClient nerClient;

    public DocumentParseResult parseDocument(String documentId, String strategy) {
        return parseDocument(documentId, strategy, false);
    }

    public DocumentParseResult parseDocument(String documentId, String strategy, boolean enableOcrFallback) {
        log.info("[Step: parseDocument] Starting, documentId={}, strategy={}, enableOcrFallback={}",
                documentId, strategy, enableOcrFallback);
        Document document = documentMapper.selectById(documentId);
        if (document == null) {
            throw new RuntimeException("Document not found: " + documentId);
        }

        // 获取文档类型
        DocType docType = determineDocType(document);
        log.info("[Step: parseDocument] Document type determined: documentId={}, docType={}", documentId, docType);

        // MANUAL类型文档处理
        if (docType == DocType.MANUAL) {
            return parseManualDocument(document, enableOcrFallback);
        }

        // NORMAL类型文档处理（原有逻辑）
        return parseNormalDocument(document, strategy, enableOcrFallback);
    }

    /**
     * 确定文档类型
     */
    private DocType determineDocType(Document document) {
        // 1. 优先使用文档自身的doc_type
        if (document.getDocType() != null) {
            return document.getDocType();
        }

        // 2. 默认NORMAL（多对多关系下，不再支持从 collection 继承 docType，
        //    调用方应在传入 collectionId 后自行设置 document.docType，或在调用前确定）
        return DocType.NORMAL;
    }

    /**
     * 处理MANUAL类型文档（说明书类）
     */
    private DocumentParseResult parseManualDocument(Document document, boolean enableOcrFallback) {
        log.info("[Step: parseManualDocument] Starting, documentId={}", document.getId());
        Path tempFile = null;
        Path resultFile = null;

        try {
            document.setParseStatus("processing");
            document.setLastParseAt(LocalDateTime.now());
            documentMapper.updateById(document);

            // 对于MANUAL类型文档，使用GLM-OCR extract-json进行结构化提取
            log.info("[Step: parseManualDocument] Performing extract-json, documentId={}", document.getId());
            String filePath = document.getFilePath();
            String schema = docSchemaService.getManualSchema();
            String prompt = "请根据JSON模板从药品说明书中提取结构化信息，严格按照模板字段输出";

            OcrService.OcrExtractResult extractResult = ocrService.extractDocumentWithSchema(
                    document.getId(), filePath, schema, prompt);

            if (extractResult.fullText() == null || extractResult.fullText().isBlank()) {
                throw new RuntimeException("OCR extract-json失败，无法获取文档内容");
            }

            String fullText = extractResult.fullText();
            Map<String, Object> structuredData = extractResult.structuredData();
            if (structuredData == null) {
                structuredData = new java.util.HashMap<>();
            }

            log.info("[Step: parseManualDocument] extract-json completed, documentId={}, textLength={}, dataFields={}",
                    document.getId(), fullText.length(), structuredData.keySet());

            // 提取页面标注
            List<Document.PageAnnotation> pageAnnotations = buildPageAnnotations(fullText, extractResult.successfulPages());
            document.setPageAnnotations(pageAnnotations);

            // 处理结构化数据：提取payload字段 + 适应症二次NER
            Map<String, Object> enrichedData = extractKeySections(structuredData);
            document.setKeySections(enrichedData);

            // MANUAL类型文档不切片，整篇作为1个chunk
            ChunkingResult chunkResult = createSingleChunkResult(document.getId(), fullText);
            log.info("[Step: parseManualDocument] Chunking (single chunk), documentId={}", document.getId());
            resultFile = saveParseResult(document.getId(), fullText, chunkResult);

            document.setParseStatus("completed");
            document.setParseCharCount(fullText.length());
            document.setParseErrorMessage(null);
            document.setProcessResultFile(resultFile.toString());
            document.setChunkStatus("completed");
            document.setExpectedChunkCount(chunkResult.chunkCount());
            documentMapper.updateById(document);

            log.info("[Step: parseManualDocument] Completed successfully, documentId={}", document.getId());

            return new DocumentParseResult(
                    document.getId(),
                    "completed",
                    fullText.length(),
                    extractResult.successfulPages(),
                    fullText,
                    chunkResult,
                    resultFile.toString(),
                    List.of(),
                    null,
                    DocType.MANUAL,
                    enrichedData,
                    pageAnnotations
            );

        } catch (Exception e) {
            log.error("[Step: parseManualDocument] Failed, documentId={}, error={}", document.getId(), e.getMessage(), e);

            document.setParseStatus("failed");
            document.setParseErrorMessage(e.getMessage());
            documentMapper.updateById(document);

            saveFailedResult(document.getId(), e);

            return new DocumentParseResult(
                    document.getId(),
                    "failed",
                    0,
                    0,
                    null,
                    null,
                    null,
                    List.of(),
                    e.getMessage(),
                    DocType.MANUAL,
                    null,
                    null
            );
        } finally {
            cleanupTempFile(tempFile, document.getParseStatus());
        }
    }

    /**
     * 对MANUAL文档执行全量OCR
     */
    private OcrService.OcrFullResult performFullDocumentOcr(Document document) throws IOException {
        int maxPages = parserProperties.getMaxOcrPages();
        if (maxPages <= 0) {
            maxPages = 50;
        }
        return ocrService.recognizeDocument(document.getId(), document.getFilePath(), maxPages);
    }

    /**
     * 构建页面标注
     */
    private List<Document.PageAnnotation> buildPageAnnotations(String fullText, int totalPages) {
        List<Document.PageAnnotation> annotations = new ArrayList<>();

        int currentPos = 0;
        for (int page = 1; page <= totalPages; page++) {
            String marker = "## 第 " + page + " 页";
            int pageStart = fullText.indexOf(marker);

            if (pageStart >= 0) {
                int contentStart = pageStart + marker.length();
                int contentEnd;
                String nextMarker = "## 第 " + (page + 1) + " 页";
                int nextPageStart = fullText.indexOf(nextMarker);

                if (nextPageStart >= 0) {
                    contentEnd = nextPageStart;
                } else {
                    contentEnd = fullText.length();
                }

                String preview = fullText.substring(contentStart, Math.min(contentStart + 100, contentEnd)).trim();
                if (preview.length() > 100) {
                    preview = preview.substring(0, 100) + "...";
                }

                annotations.add(Document.PageAnnotation.builder()
                        .page(page)
                        .startPos((long) pageStart)
                        .endPos((long) contentEnd)
                        .preview(preview)
                        .build());
            }
        }

        return annotations;
    }

    /**
     * 处理GLM-OCR extract-json返回的结构化数据
     * 提取payload字段 + 适应症二次NER
     *
     * @param structuredData GLM-OCR返回的data字段
     * @return 包含所有字段 + 二次NER结果的可序列化Map
     */
    private Map<String, Object> extractKeySections(Map<String, Object> structuredData) {
        Map<String, Object> result = new java.util.LinkedHashMap<>(structuredData);

        // 提取适应症文本进行二次NER
        String indications = structuredData.get("indications") instanceof String
                ? (String) structuredData.get("indications") : null;
        if (indications != null && !indications.isBlank()) {
            NerClient.IndicationsResult nerResult = nerClient.extractIndications(indications);
            if (!nerResult.diseases().isEmpty()) {
                result.put("_diseases", nerResult.diseases());
            }
            if (!nerResult.symptoms().isEmpty()) {
                result.put("_symptoms", nerResult.symptoms());
            }
            log.debug("[extractKeySections] Indications NER: diseases={}, symptoms={}",
                    nerResult.diseases().size(), nerResult.symptoms().size());
        }

        log.debug("[extractKeySections] Enriched structured data with {} fields", result.size());
        return result;
    }

    /**
     * 对NORMAL文档全文进行NER提取，按类型分组去重
     * 仅提取DRUG/ORG/REGION三类，每类最多6个值
     * 返回包含 _drug_names, _manufacturers, _regions 的Map，无实体时返回null
     */
    private Map<String, Object> extractNormalDocumentEntities(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }

        NerResponse nerResponse = nerClient.extractEntitiesFromDocument(text);
        if (nerResponse == null || nerResponse.getEntities() == null || nerResponse.getEntities().isEmpty()) {
            return null;
        }

        List<String> drugNames = new ArrayList<>();
        List<String> manufacturers = new ArrayList<>();
        List<String> regions = new ArrayList<>();

        for (NerEntity entity : nerResponse.getEntities()) {
            if (entity.getText() == null || entity.getText().isBlank()) {
                continue;
            }
            switch (entity.getType()) {
                case "DRUG" -> addIfNotPresent(drugNames, entity.getText());
                case "ORG" -> addIfNotPresent(manufacturers, entity.getText());
                case "REGION" -> addIfNotPresent(regions, entity.getText());
                default -> { } // 忽略DISEASE/SYMPTOM等（NORMAL文档不存储）
            }
        }

        Map<String, Object> result = new HashMap<>();
        if (!drugNames.isEmpty()) {
            result.put("_drug_names", drugNames.stream().distinct().limit(6).toList());
        }
        if (!manufacturers.isEmpty()) {
            result.put("_manufacturers", manufacturers.stream().distinct().limit(6).toList());
        }
        if (!regions.isEmpty()) {
            result.put("_regions", regions.stream().distinct().limit(6).toList());
        }

        return result.isEmpty() ? null : result;
    }

    private void addIfNotPresent(List<String> list, String value) {
        if (!list.contains(value)) {
            list.add(value);
        }
    }

    /**
     * 解析LLM返回的结构化字段
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseKeySectionsFromLLM(String llmResponse) {
        try {
            // 尝试从markdown代码块中提取JSON
            String jsonStr = llmResponse;
            int jsonStart = llmResponse.indexOf("```json");
            if (jsonStart >= 0) {
                jsonStart += 7;
                int jsonEnd = llmResponse.indexOf("```", jsonStart);
                if (jsonEnd > jsonStart) {
                    jsonStr = llmResponse.substring(jsonStart, jsonEnd).trim();
                }
            } else {
                // 尝试直接解析
                int braceStart = llmResponse.indexOf('{');
                int braceEnd = llmResponse.lastIndexOf('}');
                if (braceStart >= 0 && braceEnd > braceStart) {
                    jsonStr = llmResponse.substring(braceStart, braceEnd + 1);
                }
            }

            return objectMapper.readValue(jsonStr, Map.class);
        } catch (Exception e) {
            log.warn("[Step: parseManualDocument] Failed to parse LLM response as JSON:", e);
            return Map.of();
        }
    }

    /**
     * 为MANUAL文档创建单chunk结果
     */
    private ChunkingResult createSingleChunkResult(String documentId, String fullText) {
        List<ChunkingResult.ChunkInfo> chunks = List.of(
                new ChunkingResult.ChunkInfo(0, fullText, 0, fullText.length(), 0, 0, "")
        );

        return ChunkingResult.builder()
                .documentId(documentId)
                .totalChars(fullText.length())
                .strategy("manual")
                .config(new ChunkingResult.ChunkConfig(0, 0))
                .chunkCount(1)
                .chunks(chunks)
                .previewAt(LocalDateTime.now())
                .build();
    }

    /**
     * 处理NORMAL类型文档（原有逻辑）
     */
    private DocumentParseResult parseNormalDocument(Document document, String strategy, boolean enableOcrFallback) {
        log.info("[Step: parseNormalDocument] Starting, documentId={}, strategy={}", document.getId(), strategy);
        Path tempFile = null;
        Path resultFile = null;
        List<PageLayout> pageLayouts = null;

        try {
            log.info("[Step: parseNormalDocument] Updating status to PROCESSING, documentId={}", document.getId());
            document.setParseStatus("processing");
            document.setLastParseAt(LocalDateTime.now());
            documentMapper.updateById(document);

            log.info("[Step: parseNormalDocument] Downloading from MinIO, documentId={}, filePath={}",
                    document.getId(), document.getFilePath());
            tempFile = downloadFromMinio(document);

            // 对于 PDF，尝试获取页面布局信息（用于记录页码）
            boolean isPdf = document.getFileName() != null &&
                    document.getFileName().toLowerCase().endsWith(".pdf");

            if (isPdf) {
                try {
                    pageLayouts = parserFactory.parseWithLayout(document.getFileName(),
                            Files.newInputStream(tempFile));
                    log.info("[Step: parseNormalDocument] Got page layouts: {} pages, documentId={}",
                            pageLayouts.size(), document.getId());
                } catch (Exception e) {
                    log.warn("[Step: parseNormalDocument] Failed to get page layouts, continuing without page info, documentId={}",
                            document.getId(), e);
                    pageLayouts = null;
                }
            }

            log.info("[Step: parseNormalDocument] Parsing file, documentId={}, fileName={}",
                    document.getId(), document.getFileName());
            String parsedText = parseFile(document.getFileName(), tempFile);
            log.info("[Step: parseNormalDocument] Parsing complete, documentId={}, textLength={}",
                    document.getId(), parsedText.length());

            // 如果启用了OCR回退，且解析结果文本量很少（扫描件可能性大），尝试OCR
            if (enableOcrFallback && shouldTryOcr(document, parsedText)) {
                log.info("[Step: parseNormalDocument] Text too short, trying OCR fallback, documentId={}", document.getId());
                String ocrText = tryOcrFallback(document);
                if (ocrText != null && !ocrText.isEmpty()) {
                    log.info("[Step: parseNormalDocument] OCR fallback successful, documentId={}, ocrTextLength={}",
                            document.getId(), ocrText.length());
                    parsedText = ocrText;
                } else {
                    log.warn("[Step: parseNormalDocument] OCR fallback returned empty, using parsed text, documentId={}",
                            document.getId());
                }
            }

            // NER实体提取（全局开关控制）
            Map<String, Object> nerEntities = null;
            if (ragProperties.getNer().isEnabled()) {
                nerEntities = extractNormalDocumentEntities(parsedText);
                if (nerEntities != null) {
                    log.info("[Step: parseNormalDocument] NER extracted: drugNames={}, manufacturers={}, regions={}",
                            ((List<?>) nerEntities.getOrDefault("_drug_names", List.of())).size(),
                            ((List<?>) nerEntities.getOrDefault("_manufacturers", List.of())).size(),
                            ((List<?>) nerEntities.getOrDefault("_regions", List.of())).size());
                }
            }

            log.info("[Step: parseNormalDocument] Chunking, documentId={}, strategy={}, textLength={}",
                    document.getId(), strategy, parsedText.length());
            ChunkingResult chunkResult = chunkingService.chunk(document.getId(), parsedText, strategy, pageLayouts);
            log.info("[Step: parseNormalDocument] Chunking complete, documentId={}, chunkCount={}",
                    document.getId(), chunkResult.chunkCount());

            log.info("[Step: parseNormalDocument] Saving result, documentId={}", document.getId());
            resultFile = saveParseResult(document.getId(), parsedText, chunkResult);

            log.info("[Step: parseNormalDocument] Updating final status, documentId={}", document.getId());
            document.setParseStatus("completed");
            document.setParseCharCount(parsedText.length());
            document.setParseErrorMessage(null);
            document.setProcessResultFile(resultFile.toString());
            document.setChunkStatus("completed");
            document.setExpectedChunkCount(chunkResult.chunkCount());
            documentMapper.updateById(document);

            log.info("[Step: parseNormalDocument] Completed successfully, documentId={}, chars={}, chunks={}",
                    document.getId(), parsedText.length(), chunkResult.chunkCount());

            return new DocumentParseResult(
                    document.getId(),
                    "completed",
                    parsedText.length(),
                    1,
                    parsedText,
                    chunkResult,
                    resultFile.toString(),
                    List.of(),
                    null,
                    DocType.NORMAL,
                    nerEntities,
                    null
            );

        } catch (Exception e) {
            log.error("[Step: parseNormalDocument] Failed, documentId={}, error={}", document.getId(), e.getMessage(), e);

            document.setParseStatus("failed");
            document.setParseErrorMessage(e.getMessage());
            documentMapper.updateById(document);

            saveFailedResult(document.getId(), e);

            return new DocumentParseResult(
                    document.getId(),
                    "failed",
                    0,
                    0,
                    null,
                    null,
                    null,
                    List.of(),
                    e.getMessage(),
                    DocType.NORMAL,
                    null,
                    null
            );
        } finally {
            cleanupTempFile(tempFile, document.getParseStatus());
        }
    }

    private boolean shouldTryOcr(Document document, String parsedText) {
        // 如果解析出来的文本少于200字符，且文档是PDF且页数大于0，认为可能需要OCR
        if (parsedText == null || parsedText.trim().length() < 200) {
            if ("pdf".equalsIgnoreCase(document.getFileType())) {
                log.info("[Step: parseDocument] Parsed text too short ({} chars), PDF may be scanned",
                        parsedText != null ? parsedText.trim().length() : 0);
                return true;
            }
        }
        return false;
    }

    private String tryOcrFallback(Document document) {
        try {
            StringBuilder fullText = new StringBuilder();
            int maxPreviewPages = parserProperties.getMaxOcrPages();
            if (maxPreviewPages <= 0) {
                maxPreviewPages = 50; // 默认最多50页
            }

            for (int page = 1; page <= maxPreviewPages; page++) {
                try {
                    var ocrResult = ocrService.recognizePage(document.getId(), document.getFilePath(), page);
                    if (ocrResult.getStatus().name().equals("SUCCESS") && ocrResult.getOcrText() != null
                            && !ocrResult.getOcrText().isEmpty()) {
                        fullText.append("\n## 第 ").append(page).append(" 页\n\n")
                                .append(ocrResult.getOcrText());

                        // 已达到文档实际页数，停止循环防止重复解析
                        if (ocrResult.getTotalPages() > 0 && page >= ocrResult.getTotalPages()) {
                            log.info("[OCR] Reached actual page limit ({}), stopping OCR fallback", ocrResult.getTotalPages());
                            break;
                        }
                    } else {
                        // EXCEEDED 状态表示超出单次识别上限，无需继续
                        if ("EXCEEDED".equals(ocrResult.getStatus().name())) {
                            log.debug("[OCR] Page limit exceeded, stopping fallback at page {}", page);
                            break;
                        }
                        log.debug("OCR page {} returned empty or failed, continuing", page);
                    }
                } catch (Exception e) {
                    log.warn("OCR page {} failed, skipping: {}", page, e);
                    continue;
                }
            }

            String result = fullText.toString().trim();
            return result.isEmpty() ? null : result;
        } catch (Exception e) {
            log.warn("OCR fallback failed:", e);
            return null;
        }
    }

    private Path downloadFromMinio(Document document) throws IOException {
        Path tempDir = tempFileCleanupService.ensureTempDirectory();
        String fileName = document.getId() + "_" + document.getFileName();
        Path tempFile = tempDir.resolve(fileName);

        try (InputStream stream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(ragProperties.getMinio().getBucket().getDocuments())
                        .object(document.getFilePath())
                        .build())) {

            Files.copy(stream, tempFile);
            log.debug("Downloaded file from MinIO to: {}", tempFile);
        } catch (Exception e) {
            throw new IOException("Failed to download file from MinIO: " + e.getMessage(), e);
        }

        return tempFile;
    }

    private String parseFile(String fileName, Path filePath) throws IOException {
        try (InputStream inputStream = Files.newInputStream(filePath)) {
            return parserFactory.parse(fileName, inputStream);
        }
    }

    private Path saveParseResult(String documentId, String text, ChunkingResult chunkResult)
            throws IOException {
        Path tempDir = tempFileCleanupService.ensureTempDirectory();
        Path resultFile = tempDir.resolve(documentId + "_process.json");

        int maxPreviewChars = parserProperties.getMaxPreviewChars();
        String previewText = text.length() > maxPreviewChars
                ? text.substring(0, maxPreviewChars)
                : text;

        DocumentProcessResult result = new DocumentProcessResult(
                documentId,
                previewText,
                new DocumentProcessResult.ParseInfo("completed", text.length(), LocalDateTime.now()),
                chunkResult
        );

        objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(resultFile.toFile(), result);

        return resultFile;
    }

    private void saveFailedResult(String documentId, Exception e) {
        try {
            Path tempDir = tempFileCleanupService.ensureTempDirectory();
            Path failedDir = tempDir.resolve(documentId + "_failed");
            Files.createDirectories(failedDir);

            Path errorFile = failedDir.resolve("error.log");
            Files.writeString(errorFile, e.getMessage());

            log.debug("Saved failed result for document: {}", documentId);
        } catch (IOException ex) {
            log.warn("Failed to save failed result for document: {}", documentId, ex);
        }
    }

    private void cleanupTempFile(Path tempFile, String parseStatus) {
        if (tempFile != null && Files.exists(tempFile)) {
            if ("completed".equals(parseStatus)) {
                try {
                    Files.delete(tempFile);
                    log.debug("Deleted temp file: {}", tempFile);
                } catch (IOException e) {
                    log.warn("Failed to delete temp file: {}", tempFile, e);
                }
            } else {
                log.debug("Kept temp file for debugging: {}", tempFile);
            }
        }
    }

    public Optional<DocumentProcessResult> getParseResult(String documentId) {
        Document document = documentMapper.selectById(documentId);
        if (document == null) {
            throw new RuntimeException("Document not found: " + documentId);
        }

        if (document.getProcessResultFile() != null) {
            try {
                Path resultPath = Path.of(document.getProcessResultFile());
                if (Files.exists(resultPath)) {
                    DocumentProcessResult result = objectMapper.readValue(
                            resultPath.toFile(), DocumentProcessResult.class);
                    return Optional.of(result);
                }
            } catch (IOException e) {
                log.warn("Failed to load parse result for document: {}", documentId, e);
            }
        }

        return Optional.empty();
    }

    public DocumentParseStatus getParseStatus(String documentId) {
        Document document = documentMapper.selectById(documentId);
        if (document == null) {
            throw new RuntimeException("Document not found: " + documentId);
        }

        return new DocumentParseStatus(
                documentId,
                document.getUploadStatus(),
                document.getParseStatus(),
                document.getChunkStatus(),
                document.getChunkCount(),
                document.getParseErrorMessage(),
                document.getLastParseAt()
        );
    }

    public record DocumentParseResult(
            String documentId,
            String status,
            int charCount,
            int pageCount,
            String originalText,
            ChunkingResult chunkPreview,
            String tempFilePath,
            List<String> warnings,
            String errorMessage,
            DocType docType,
            Map<String, Object> keySections,
            List<Document.PageAnnotation> pageAnnotations
    ) {}

    public record DocumentParseStatus(
            String documentId,
            String uploadStatus,
            String parseStatus,
            String chunkStatus,
            Integer chunkCount,
            String errorMessage,
            LocalDateTime lastParseAt
    ) {}

    public record DocumentProcessResult(
            String documentId,
            String originalText,
            ParseInfo parseInfo,
            ChunkingResult chunkPreview
    ) {
        public record ParseInfo(
                String status,
                int charCount,
                LocalDateTime parsedAt
        ) {}
    }
}
