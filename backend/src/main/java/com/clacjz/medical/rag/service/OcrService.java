package com.clacjz.medical.rag.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.clacjz.medical.rag.config.OcrProperties;
import com.clacjz.medical.rag.config.RAGProperties;
import com.clacjz.medical.rag.model.dto.OcrResultDTO;
import com.clacjz.medical.rag.model.dto.ai.BatchOcrItem;
import com.clacjz.medical.rag.model.dto.ai.BatchOcrResponse;
import com.clacjz.medical.rag.model.dto.ai.GlmReadResponse;
import com.clacjz.medical.rag.model.enums.OcrStatus;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;

@Slf4j
@Service
@RequiredArgsConstructor
public class OcrService {

    private static final String CACHE_KEY_PREFIX = "ocr:doc:";
    private static final String STATUS_SUCCESS = "success";

    /**
     * 内存级PDF字节缓存（LRU）。
     * batch 主路径将 PDF 加载到局部变量中，不依赖 cache 命中；
     * cache 仅用于：逐页回退路径、UI preview、batchEnabled=false 的旧路径。
     * 上限由 rag.ocr.batch-cache-max-size 控制（默认 20）。
     */
    private final Map<String, byte[]> pdfBytesCache =
        Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
                return size() > ocrProperties.getBatchCacheMaxSize();
            }
        });

    private final OcrProperties ocrProperties;
    private final RestTemplate restTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final MinioClient minioClient;
    private final RAGProperties ragProperties;

    public OcrResultDTO recognizePage(String documentId, String filePath, int page) {
        if (page <= 0) {
            return buildErrorResult(documentId, page, "页码必须大于0", OcrStatus.FAILED);
        }

        if (page > ocrProperties.getMaxPages()) {
            return buildErrorResult(documentId, page, "超出最大页数限制，请分页预览", OcrStatus.EXCEEDED);
        }

        String cacheKey = CACHE_KEY_PREFIX + documentId + ":page:" + page;
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            OcrResultDTO cachedResult = parseFromCache(cached);
            if (cachedResult != null) {
                return cachedResult;
            }
        }

        try {
            OcrResultDTO result = callOcrService(ocrProperties.getServiceUrl(), documentId, filePath, page, null);
            if (result.getStatus() == OcrStatus.SUCCESS) {
                cacheResult(cacheKey, result);
            }
            return result;
        } catch (Exception e) {
            log.warn("GLM-OCR failed, fallback to Qwen: documentId={}, page={}, error={}",
                    documentId, page, e.getMessage());
            try {
                OcrResultDTO fallbackResult = callOcrService(
                        ocrProperties.getFallbackUrl(), documentId, filePath, page, "qwen3.6-27b");
                if (fallbackResult.getStatus() == OcrStatus.SUCCESS) {
                    cacheResult(cacheKey, fallbackResult);
                }
                return fallbackResult;
            } catch (Exception fallbackError) {
                log.error("OCR fallback also failed: documentId={}, page={}", documentId, page, fallbackError);
                return buildErrorResult(documentId, page, "OCR识别失败: " + fallbackError.getMessage(), OcrStatus.FAILED);
            }
        }
    }

    /**
     * 从 MinIO 加载 PDF 字节（使用内存级LRU缓存，避免重复下载）。
     * 同时被单页 OCR（callOcrService）和 batch OCR（recognizeDocumentBatch）共用。
     */
    byte[] getPdfBytes(String documentId, String objectName) {
        String bucketName = ragProperties.getMinio().getBucket().getDocuments();

        byte[] fileBytes = pdfBytesCache.get(objectName);
        if (fileBytes != null) {
            log.debug("PDF loaded from memory cache: objectName={}", objectName);
            return fileBytes;
        }

        // 预检查MinIO文件是否存在
        try {
            minioClient.statObject(
                    io.minio.StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build());
        } catch (Exception statEx) {
            log.error("MinIO文件不存在或不可访问: bucket={}, objectName={}, documentId={}",
                    bucketName, objectName, documentId);
            throw new RuntimeException("MinIO文件不存在: bucket=" + bucketName + ", object=" + objectName
                    + ", documentId=" + documentId + ", error=" + statEx.getMessage(), statEx);
        }

        try (InputStream is = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .build())) {
            fileBytes = is.readAllBytes();
        } catch (Exception e) {
            log.error("Failed to read file from MinIO: bucket={}, objectName={}, documentId={}",
                    bucketName, objectName, documentId, e);
            throw new RuntimeException("Failed to read file from MinIO: bucket="
                    + bucketName + ", object=" + objectName, e);
        }

        pdfBytesCache.put(objectName, fileBytes);
        log.debug("PDF loaded from MinIO and cached: objectName={}, size={}", objectName, fileBytes.length);
        return fileBytes;
    }

    private OcrResultDTO callOcrService(String url, String documentId, String objectName, int page, String model) {
        // 幂等缓存: 同一文档同一页的 OCR 结果直接命中 Redis，避免重复调用 OCR 服务
        // (2026-06-08 Bug#2-B 修复: 此检查原本只在 recognizePage 入口，batch 路径的 per-page
        //  回退直接调本方法会跳过缓存。现统一在底层入口拦截。)
        String cacheKey = CACHE_KEY_PREFIX + documentId + ":page:" + page;
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            OcrResultDTO cachedResult = parseFromCache(cached);
            if (cachedResult != null) {
                log.debug("[OCR cache hit] documentId={}, page={}", documentId, page);
                return cachedResult;
            }
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        try {
            byte[] fileBytes = getPdfBytes(documentId, objectName);

            // 获取PDF真实页数（损坏的PDF会抛出异常）
            int realTotalPages = getPdfPageCount(fileBytes);

            String filename = objectName.contains("/")
                    ? objectName.substring(objectName.lastIndexOf("/") + 1)
                    : objectName;

            ByteArrayResource fileResource = new ByteArrayResource(fileBytes) {
                @Override
                public String getFilename() {
                    return filename;
                }
            };

            MultiValueMap<String, Object> requestBody = new LinkedMultiValueMap<>();
            requestBody.add("files", fileResource);
            // 始终传递页码prompt，确保OCR服务知道要处理哪一页
            requestBody.add("prompt", "请提取页面 " + page + " 的内容");

            HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(requestBody, headers);
            String apiPath = (model != null) ? "/api/glm/extract" : "/api/glm/read";
            String fullUrl = url + apiPath;
            log.info("OCR request: url={}, model={}, page={}/{}", fullUrl, model, page, realTotalPages);
            ResponseEntity<String> response = restTemplate.postForEntity(fullUrl, request, String.class);
            OcrResultDTO result = parseOcrResponse(documentId, page, response.getBody());
            // 使用真实的页数覆盖OCR返回的页数（OCR服务可能返回错误的页数）
            result.setTotalPages(realTotalPages);
            return result;
        } catch (Exception e) {
            log.error("OCR service call failed: documentId={}, objectName={}, page={}, error={}",
                    documentId, objectName, page, e.getMessage());
            throw new RuntimeException("OCR service unavailable: " + e.getMessage(), e);
        }
    }

    private int getPdfPageCount(byte[] pdfBytes) {
        try (var document = org.apache.pdfbox.Loader.loadPDF(pdfBytes)) {
            return document.getNumberOfPages();
        } catch (Exception e) {
            log.error("Failed to get PDF page count (PDF may be corrupted)", e);
            throw new RuntimeException("PDF解析失败，文件可能已损坏: " + e.getMessage(), e);
        }
    }

    // 批量 OCR
    /**
     * 将页码范围划分为等尺寸的批次。
     * 如 partitionPages(1, 10, 4) → [[1,2,3,4], [5,6,7,8], [9,10]]
     */
    static List<List<Integer>> partitionPages(int startInclusive, int endInclusive, int batchSize) {
        if (startInclusive > endInclusive || batchSize <= 0) {
            return Collections.emptyList();
        }
        List<List<Integer>> batches = new ArrayList<>();
        List<Integer> current = new ArrayList<>();
        for (int page = startInclusive; page <= endInclusive; page++) {
            current.add(page);
            if (current.size() == batchSize) {
                batches.add(current);
                current = new ArrayList<>();
            }
        }
        if (!current.isEmpty()) {
            batches.add(current);
        }
        return batches;
    }

    /**
     * 将 PDF 页面渲染为指定格式的图片，并返回 base64 编码字符串。
     *
     * @param pdfBytes PDF 原始字节
     * @param pageNumber 页码（从 1 开始）
     * @param dpi 渲染 DPI（150 = 约 150KB/页）
     * @param quality JPEG 压缩质量（0.0-1.0，仅 JPEG 格式有效）
     * @param format 图片格式 "jpeg" 或 "png"
     */
    static String renderPageToBase64(byte[] pdfBytes, int pageNumber, int dpi, float quality, String format) {
        try (var document = org.apache.pdfbox.Loader.loadPDF(pdfBytes)) {
            var renderer = new org.apache.pdfbox.rendering.PDFRenderer(document);
            BufferedImage image = renderer.renderImageWithDPI(pageNumber - 1, dpi);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            String fmt = "jpeg".equalsIgnoreCase(format) ? "JPEG" : "png";
            ImageIO.write(image, fmt, baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException("Failed to render PDF page " + pageNumber + " to " + format, e);
        }
    }

    /**
     * 单次 Batch OCR 调用：将 [fromPage..toPage] 发送到 /api/glm/batch-ocr。
     * 返回 页码 → OcrResultDTO 的映射，失败项自动回退单页 OCR。
     */
    private Map<Integer, OcrResultDTO> callBatchOcr(byte[] pdfBytes, String documentId,
                                                      String filePath, int totalPages, int fromPage, int toPage) {
        int batchSize = toPage - fromPage + 1;
        List<BatchOcrItem> items = new ArrayList<>(batchSize);
        for (int page = fromPage; page <= toPage; page++) {
            String base64 = renderPageToBase64(pdfBytes, page,
                    ocrProperties.getBatchImageDpi(),
                    ocrProperties.getBatchImageQuality(),
                    ocrProperties.getBatchImageFormat());
            items.add(BatchOcrItem.builder()
                    .index(page - 1) // 0-based index
                    .imageData(base64)
                    .taskType("text")
                    .build());
        }

        // 发送 batch 请求
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<List<BatchOcrItem>> request = new HttpEntity<>(items, headers);

        String url = ocrProperties.getServiceUrl() + "/api/glm/batch-ocr"
                + "?batch_size=" + batchSize + "&fallback=true";

        ResponseEntity<BatchOcrResponse> response;
        try {
            // 注意：超时由 RestTemplate 的 ClientHttpRequestFactory 统一控制
            // 若 batch 请求需更长超时，请增加 rag.ocr.timeout 或为 batch 配置专用 RestTemplate
            response = restTemplate.postForEntity(url, request, BatchOcrResponse.class);
        } catch (Exception e) {
            log.warn("[BatchOCR] Batch request failed for pages {}-{}, falling back per-page: {}",
                    fromPage, toPage, e.getMessage());
            return Collections.emptyMap();
        }

        if (response.getBody() == null || response.getBody().getResults() == null) {
            log.warn("[BatchOCR] Empty response for pages {}-{}, falling back per-page", fromPage, toPage);
            return Collections.emptyMap();
        }

        // 映射结果并处理失败的项
        Map<Integer, OcrResultDTO> results = new HashMap<>();
        for (BatchOcrResponse.BatchItemResult item : response.getBody().getResults()) {
            int pageNumber = item.getIndex() + 1; // 1-based
            if ("success".equals(item.getStatus())) {
                String text = item.getContent() != null ? item.getContent() : "";
                OcrResultDTO dto = OcrResultDTO.builder()
                        .documentId(documentId)
                        .currentPage(pageNumber)
                        .totalPages(totalPages)
                        .ocrText(text)
                        .htmlTable("")
                        .status(OcrStatus.SUCCESS)
                        .build();
                results.put(pageNumber, dto);
                // 写入 Redis 缓存，供后续单页查询命中
                String cacheKey = CACHE_KEY_PREFIX + documentId + ":page:" + pageNumber;
                cacheResult(cacheKey, dto);
            } else {
                log.warn("[BatchOCR] Page {} failed in batch ({}), falling back per-page",
                        pageNumber, item.getError());
                // 2026-06-08 Bug#2-D 修复: 用 recognizePage 替代 callOcrService，让 GLM 超时后
                // 自动 fallback 到 fallbackUrl 的 qwen3.6-27b 接口（满足"OCR 超时必须走 qwen 兜底"）
                try {
                    OcrResultDTO fallback = recognizePage(documentId, filePath, pageNumber);
                    results.put(pageNumber, fallback);
                } catch (Exception fallbackErr) {
                    log.error("[BatchOCR] Per-page fallback also failed for page {}", pageNumber, fallbackErr);
                    results.put(pageNumber, OcrResultDTO.builder()
                            .documentId(documentId)
                            .currentPage(pageNumber)
                            .totalPages(totalPages)
                            .status(OcrStatus.FAILED)
                            .errorMessage("Batch + fallback failed: " + fallbackErr.getMessage())
                            .build());
                }
            }
        }

        if (response.getBody().getStats() != null) {
            var stats = response.getBody().getStats();
            log.info("[BatchOCR] Batch {}-{}: {} success, {} failed, {} fallbacks",
                    fromPage, toPage, stats.getSuccessCount(),
                    stats.getFailedCount(), stats.getFallbackCount());
        }

        return results;
    }

    /**
     * 使用 Batch OCR 对整个文档进行识别。
     * 将页面分组后逐组调用 callBatchOcr()，失败项自动回退单页 OCR。
     */
    private OcrFullResult recognizeDocumentBatch(String documentId, String filePath, int maxPages) {
        log.info("[BatchOCR] Starting batch document OCR: documentId={}, maxPages={}", documentId, maxPages);

        // 加载 PDF
        byte[] pdfBytes = getPdfBytes(documentId, filePath);
        int totalPages = getPdfPageCount(pdfBytes);
        // 2026-06-08 Bug#2-C: 实际页数超过 maxOcrPages 阈值时 WARN 警告 (仅识别前 maxPages 页)
        if (totalPages > maxPages) {
            log.warn("[OCR] 文档页数 {} 超过 maxOcrPages={}, 仅处理前 {} 页，建议调整 parser.max-ocr-pages 或拆分文档: documentId={}",
                    totalPages, maxPages, maxPages, documentId);
        }
        int actualMaxPages = Math.min(maxPages, totalPages);

        // 分为批次
        int batchSize = ocrProperties.getBatchSize();
        List<List<Integer>> batches = partitionPages(1, actualMaxPages, batchSize);
        log.info("[BatchOCR] Partitioned into {} batches (batchSize={})", batches.size(), batchSize);

        // 逐组处理（串行，避免压垮 AI SDK）
        Map<Integer, OcrResultDTO> allResults = new LinkedHashMap<>();
        for (List<Integer> batch : batches) {
            int from = batch.get(0);
            int to = batch.get(batch.size() - 1);
            Map<Integer, OcrResultDTO> batchResults = callBatchOcr(pdfBytes, documentId, filePath, totalPages, from, to);
            allResults.putAll(batchResults);
        }

        // 按页码顺序组装文本
        StringBuilder fullText = new StringBuilder();
        int successfulPages = 0;
        for (int page = 1; page <= actualMaxPages; page++) {
            OcrResultDTO result = allResults.get(page);
            if (result != null && result.getStatus() == OcrStatus.SUCCESS
                    && result.getOcrText() != null && !result.getOcrText().isEmpty()) {
                if (!fullText.isEmpty()) {
                    fullText.append("\n\n## 第 ").append(page).append(" 页\n\n");
                } else {
                    fullText.append("## 第 ").append(page).append(" 页\n\n");
                }
                fullText.append(result.getOcrText());
                successfulPages++;
            } else if (result != null && result.getStatus() == OcrStatus.FAILED) {
                log.warn("[BatchOCR] Page {} failed after all fallbacks", page);
            }
        }

        log.info("[BatchOCR] Completed: documentId={}, successfulPages={}/{}, totalLength={}",
                documentId, successfulPages, actualMaxPages, fullText.length());

        return new OcrFullResult(documentId, fullText.toString(), successfulPages, totalPages, null);
    }

    private OcrResultDTO parseOcrResponse(String documentId, int page, String responseBody) {
        try {
            GlmReadResponse glmResponse = objectMapper.readValue(responseBody, GlmReadResponse.class);

            String ocrText = "";
            String errorMessage = null;

            if (glmResponse.getResults() != null && !glmResponse.getResults().isEmpty()) {
                GlmReadResponse.FileResult firstResult = glmResponse.getResults().get(0);

                if (STATUS_SUCCESS.equals(firstResult.getStatus())) {
                    ocrText = firstResult.getContent() != null ? firstResult.getContent() : "";
                    if (ocrText.isEmpty() && firstResult.getExtracted() != null) {
                        ocrText = firstResult.getExtracted();
                    }
                } else {
                    errorMessage = firstResult.getError() != null ? firstResult.getError() : "Unknown error";
                }
            }

            int totalPages = 1;
            if (glmResponse.getSummary() != null && glmResponse.getSummary().getTotal() != null) {
                totalPages = glmResponse.getSummary().getTotal();
            }

            if (errorMessage != null) {
                return buildErrorResult(documentId, page, errorMessage, OcrStatus.FAILED);
            }

            return OcrResultDTO.builder()
                    .documentId(documentId)
                    .currentPage(page)
                    .totalPages(totalPages)
                    .ocrText(ocrText)
                    .htmlTable("")
                    .status(OcrStatus.SUCCESS)
                    .build();
        } catch (Exception e) {
            log.error("Failed to parse OCR response: {}", responseBody, e);
            return buildErrorResult(documentId, page, "解析OCR响应失败", OcrStatus.FAILED);
        }
    }

    private void cacheResult(String cacheKey, OcrResultDTO result) {
        try {
            String json = objectMapper.writeValueAsString(result);
            stringRedisTemplate.opsForValue().set(
                    cacheKey,
                    json,
                    ocrProperties.getMaxCacheTtl(),
                    TimeUnit.MINUTES
            );
        } catch (Exception e) {
            log.warn("Failed to cache OCR result:", e);
        }
    }

    private OcrResultDTO parseFromCache(String cachedJson) {
        try {
            return objectMapper.readValue(cachedJson, OcrResultDTO.class);
        } catch (Exception e) {
            log.warn("Failed to parse cached OCR result, will re-recognize:", e);
            return null;
        }
    }

    private OcrResultDTO buildErrorResult(String documentId, int page, String errorMessage, OcrStatus status) {
        return OcrResultDTO.builder()
                .documentId(documentId)
                .currentPage(page)
                .status(status)
                .errorMessage(errorMessage)
                .build();
    }

    /**
     * 对整个文档进行OCR识别，返回完整文本和页面标注
     *
     * @param documentId 文档ID
     * @param filePath MinIO中的文件路径
     * @param maxPages 最大OCR页数
     * @return OCR识别结果（包含完整文本和页面位置映射）
     */
    public OcrFullResult recognizeDocument(String documentId, String filePath, int maxPages) {
        // 2026-06-08 Bug#2-C: 文档超 maxOcrPages 时 WARN 提示运营侧介入
        // (此处只能拿到 maxPages 配置，actual totalPages 在下游 batch/per-page 路径内获取后再次校验)
        log.info("[OCR] recognizeDocument entry: documentId={}, configuredMaxPages={}", documentId, maxPages);

        // 开启 batch 且 batchSize > 1 时走批量路径
        if (ocrProperties.isBatchEnabled() && ocrProperties.getBatchSize() > 1) {
            return recognizeDocumentBatch(documentId, filePath, maxPages);
        }

        log.info("[OCR] Starting full document OCR (per-page): documentId={}, maxPages={}", documentId, maxPages);

        StringBuilder fullText = new StringBuilder();
        int successfulPages = 0;
        int totalPages = 0;

        for (int page = 1; page <= maxPages; page++) {
            OcrResultDTO pageResult = recognizePage(documentId, filePath, page);

            if (pageResult.getStatus() == OcrStatus.SUCCESS && pageResult.getOcrText() != null
                    && !pageResult.getOcrText().isEmpty()) {

                int startPos = fullText.length();

                if (fullText.length() > 0) {
                    fullText.append("\n\n## 第 ").append(page).append(" 页\n\n");
                } else {
                    fullText.append("## 第 ").append(page).append(" 页\n\n");
                }

                fullText.append(pageResult.getOcrText());

                successfulPages++;
                totalPages = pageResult.getTotalPages() > 0 ? pageResult.getTotalPages() : totalPages;

                // 2026-06-08 Bug#2-C: 首次拿到 totalPages 时检测是否超过阈值
                if (totalPages > maxPages && successfulPages == 1) {
                    log.warn("[OCR] 文档页数 {} 超过 maxOcrPages={}, 仅处理前 {} 页，建议调整 parser.max-ocr-pages 或拆分文档: documentId={}",
                            totalPages, maxPages, maxPages, documentId);
                }

                if (page >= totalPages) {
                    log.info("[OCR] Reached actual page limit ({}), stopping", totalPages);
                    break;
                }
            } else {
                log.debug("[OCR] Page {} returned empty or failed, stopping", page);
                if (pageResult.getStatus() == OcrStatus.EXCEEDED) {
                    break;
                }
            }
        }

        log.info("[OCR] Full document OCR completed: documentId={}, successfulPages={}, totalLength={}",
                documentId, successfulPages, fullText.length());

        return new OcrFullResult(
                documentId,
                fullText.toString(),
                successfulPages,
                totalPages,
                null
        );
    }

    /**
     * 全文档OCR结果
     */
    public record OcrFullResult(
            String documentId,
            String fullText,
            int successfulPages,
            int totalPages,
            String errorMessage
    ) {}

    /**
     * 带结构化提取的OCR结果（来自 extract-json）
     */
    public record OcrExtractResult(
            String documentId,
            String fullText,
            Map<String, Object> structuredData,
            int successfulPages,
            int totalPages,
            String errorMessage
    ) {}

    /**
     * 对整份文档进行OCR + JSON Schema结构化提取
     * 调用 /api/glm/extract-json 接口，一次性完成识别和字段抽取
     *
     * @param documentId 文档ID
     * @param filePath MinIO中的文件路径
     * @param schema JSON Schema字符串（如 manual.json 的完整内容）
     * @param prompt 提取提示词
     * @return OCR提取结果（包含全文markdown + 结构化数据）
     */
    public OcrExtractResult extractDocumentWithSchema(String documentId, String filePath,
                                                      String schema, String prompt) {
        log.info("[OCR extract-json] Starting: documentId={}", documentId);

        String bucketName = ragProperties.getMinio().getBucket().getDocuments();
        String filename = filePath.contains("/")
                ? filePath.substring(filePath.lastIndexOf("/") + 1)
                : filePath;

        try {
            byte[] fileBytes;
            try (InputStream is = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(filePath)
                            .build())) {
                fileBytes = is.readAllBytes();
            }

            int totalPages = getPdfPageCount(fileBytes);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            ByteArrayResource fileResource = new ByteArrayResource(fileBytes) {
                @Override
                public String getFilename() {
                    return filename;
                }
            };

            MultiValueMap<String, Object> requestBody = new LinkedMultiValueMap<>();
            requestBody.add("files", fileResource);
            requestBody.add("prompt", prompt);
            requestBody.add("json_schema", schema);

            HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(requestBody, headers);
            String url = ocrProperties.getServiceUrl() + "/api/glm/extract-json";

            log.info("[OCR extract-json] Calling: url={}, documentId={}, fileSize={}",
                    url, documentId, fileBytes.length);

            ResponseEntity<String> response;
            try {
                response = restTemplate.postForEntity(url, request, String.class);
            } catch (Exception e) {
                log.warn("[OCR extract-json] GLM failed, fallback to Qwen: documentId={}, error={}",
                        documentId, e.getMessage());
                url = ocrProperties.getFallbackUrl() + "/api/glm/extract-json";
                response = restTemplate.postForEntity(url, request, String.class);
            }

            return parseExtractJsonResponse(documentId, totalPages, response.getBody());

        } catch (Exception e) {
            log.error("[OCR extract-json] Failed: documentId={}", documentId, e);
            return new OcrExtractResult(
                    documentId, null, null, 0, 0,
                    "OCR extract-json failed: " + e.getMessage()
            );
        }
    }

    private OcrExtractResult parseExtractJsonResponse(String documentId, int totalPages, String responseBody) {
        try {
            GlmReadResponse glmResponse = objectMapper.readValue(responseBody, GlmReadResponse.class);

            if (glmResponse.getResults() == null || glmResponse.getResults().isEmpty()) {
                return new OcrExtractResult(
                        documentId, null, null, 0, totalPages,
                        "OCR returned empty results"
                );
            }

            GlmReadResponse.FileResult firstResult = glmResponse.getResults().get(0);
            if (!"success".equals(firstResult.getStatus())) {
                return new OcrExtractResult(
                        documentId, null, null, 0, totalPages,
                        firstResult.getError() != null ? firstResult.getError() : "OCR failed"
                );
            }

            String fullText = firstResult.getContent() != null ? firstResult.getContent() : "";
            if (fullText.isEmpty() && firstResult.getExtracted() != null) {
                fullText = firstResult.getExtracted();
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> structuredData = (Map<String, Object>) firstResult.getData();
            if (structuredData == null) {
                structuredData = new HashMap<>();
            }

            log.info("[OCR extract-json] Success: documentId={}, textLength={}, hasData={}",
                    documentId, fullText.length(), firstResult.getData() != null);

            return new OcrExtractResult(
                    documentId, fullText, structuredData,
                    totalPages, totalPages, null
            );
        } catch (Exception e) {
            log.error("[OCR extract-json] Parse failed: documentId={}", documentId, e);
            return new OcrExtractResult(
                    documentId, null, null, 0, totalPages,
                    "Failed to parse extract-json response: " + e.getMessage()
            );
        }
    }
}
