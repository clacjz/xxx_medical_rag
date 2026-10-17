# RAG知识库系统详细设计文档（LLD）

## 第二部分：核心模块详细设计

> **文档类型**: 低层设计（LLD）
> **编制日期**: 2026-03-25
> **项目**: RAG知识库系统

---

# 一、文档处理模块

## 1.1 模块架构

```java
┌─────────────────────────────────────────────┐
│           DocumentProcessorFactory              │
│    (工厂模式，根据文件类型创建处理器)          │
└─────────────────┬───────────────────────────┘
                  │
    ┌─────────────┼─────────────────┐
    │             │                 │
    ▼             ▼                 ▼
┌─────────┐  ┌─────────┐         ┌─────────┐
│PDFParser│  │WordParser│         │MDParser │
└────┬────┘  └────┬────┘         └────┬────┘
     │            │                 │
     ▼            ▼                 ▼
┌─────────────────────────────────────────┐
│           DocumentChunker                  │
│  (文档分块策略：固定长度/语义/混合)        │
└─────────────────┬───────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────┐
│           TextCleaner                    │
│  (文本清洗：编码/格式/去噪)              │
└─────────────────────────────────────────┘
```

## 1.2 文档处理器接口

```java
public interface DocumentProcessor {
    
    /**
     * 判断是否支持该文件类型
     */
    boolean supports(String fileType);
    
    /**
     * 提取文本内容
     */
    Document extractText(InputStream inputStream, String fileName) throws Exception;
    
    /**
     * 提取元数据
     */
    DocumentMetadata extractMetadata(InputStream inputStream) throws Exception;
    
    /**
     * 获取支持的文件类型
     */
    default List<String> getSupportedTypes() {
        return Collections.singletonList(getType());
    }
    
    /**
     * 返回处理器类型
     */
    String getType();
}
```

### 1.2.1 PDF文档处理器

```java
@Component
@Slf4j
public class PDFDocumentProcessor implements DocumentProcessor {
    
    @Override
    public boolean supports(String fileType) {
        return "pdf".equalsIgnoreCase(fileType);
    }
    
    @Override
    public Document extractText(InputStream inputStream, String fileName) throws Exception {
        PDDocument document = PDDocument.load(inputStream);
        PDFTextStripper stripper = new PDFTextStripper();
        
        List<PageContent> pages = new ArrayList<>();
        
        for (int i = 1; i <= document.getNumberOfPages(); i++) {
            stripper.setStartPage(i);
            stripper.setEndPage(i);
            String pageText = stripper.getText(document);
            
            pages.add(PageContent.builder()
                .pageNumber(i)
                .content(pageText)
                .build());
        }
        
        // 提取目录结构
        List<OutlineItem> outline = extractOutline(document);
        
        // 提取表格
        List<TableContent> tables = extractTables(document);
        
        return Document.builder()
            .pages(pages)
            .outline(outline)
            .tables(tables)
            .metadata(extractMetadata(inputStream)
            .build();
    }
    
    private List<OutlineItem> extractOutline(PDDocument document) {
        List<OutlineItem> outline = new ArrayList<>();
        PDDocumentCatalog catalog = document.getDocumentCatalog();
        PDOutlineNode outlineRoot = catalog.getDocumentOutline();
        
        if (outlineRoot != null) {
            extractOutlineItems(outlineRoot, outline, 0);
        }
        
        return outline;
    }
    
    private List<TableContent> extractTables(PDDocument document) {
        // 表格提取逻辑
    }
    
    @Override
    public DocumentMetadata extractMetadata(InputStream inputStream) throws IOException {
        try (PDDocument document = PDDocument.load(inputStream)) {
            PDDocumentInformation info = document.getDocumentInformation();
            
            return DocumentMetadata.builder()
                .title(info.getTitle())
                .author(info.getAuthor())
                .subject(info.getSubject())
                .keywords(info.getKeywords())
                .creator(info.getCreator)
                .producer(info.getProducer())
                .creationDate(info.getCreationDate())
                .pageCount(document.getNumberOfPages())
                .build();
        }
    }
}
```

### 1.2.2 文档分块器

```java
@Service
@Slf4j
public class DocumentChunker {
    
    private final FixedSizeChunkingStrategy fixedStrategy;
    private final SemanticChunkingStrategy semanticStrategy;
    private final HybridChunkingStrategy hybridStrategy;
    
    @Value("${rag.chunking.default-strategy:fixed}")
    private String defaultStrategy;
    
    @Value("${rag.chunking.default-size:512}")
    private int defaultChunkSize;
    
    @Value("${rag.chunking.overlap:64}")
    private int defaultOverlap;
    
    public List<TextChunk> chunk(String text, String strategy, ChunkConfig config) {
        ChunkingStrategy chunkingStrategy = resolveStrategy(strategy);
        ChunkConfig finalConfig = config != null ? config : buildDefaultConfig();
        
        long startTime = System.currentTimeMillis();
        
        List<TextChunk> chunks = chunkingStrategy.chunk(text, finalConfig);
        
        log.info("文档分块完成，策略:{}，原始长度:{}，分块数:{}，耗时:{}ms",
            strategy, text.length(), chunks.size(), 
            System.currentTimeMillis() - startTime);
        
        return chunks;
    }
    
    private ChunkingStrategy resolveStrategy(String strategy) {
        return switch (strategy.toLowerCase()) {
            case "semantic" -> semanticStrategy;
            case "hybrid" -> hybridStrategy;
            default -> fixedStrategy;
        };
    }
    
    private ChunkConfig buildDefaultConfig() {
        return ChunkConfig.builder()
            .chunkSize(defaultChunkSize)
            .overlap(defaultOverlap)
            .minChunkSize(50)
            .preserveStructure(true)
            .build();
    }
}
```

## 1.3 文档服务实现

```java
@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class DocumentService {
    
    private final DocumentRepository documentRepository;
    private final CollectionRepository collectionRepository;
    private final DocumentProcessorFactory processorFactory;
    private final DocumentChunker documentChunker;
    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    private final MinioStorageService storageService;
    private final AuditService auditService;
    
    @Async
    public void processDocumentAsync(UUID documentId) {
        Document document = documentRepository.findById(documentId)
            .orElseThrow(() -> new ResourceNotFoundException("文档不存在"));
        
        try {
            // 更新状态
            document.setStatus(DocumentStatus.PROCESSING);
            document.setProcessingStage("parsing");
            documentRepository.save(document);
            
            // 1. 下载文件
            byte[] fileContent = storageService.download(document.getFilePath());
            
            // 2. 解析文档
            document.setProcessingStage("parsing");
            Document parsed = processorFactory.process(
                new ByteArrayInputStream(fileContent),
                document.getFileType()
            );
            
            // 3. 文本清洗
            String cleanedText = cleanText(parsed.getContent());
            
            // 4. 分块
            document.setProcessingStage("chunking");
            List<TextChunk> chunks = documentChunker.chunk(cleanedText, null, null);
            
            // 5. 向量化
            document.setProcessingStage("embedding");
            List<float[]> vectors = embeddingService.embedBatch(
                chunks.stream()
                    .map(TextChunk::getContent)
                    .collect(Collectors.toList())
            );
            
            // 6. 存储向量
            document.setProcessingStage("storing");
            for (int i = 0; i < chunks.size(); i++) {
                TextChunk chunk = chunks.get(i);
                chunk.setVectorId(vectorStoreService.store(
                    document.getId(), 
                    vectors.get(i), 
                    chunk.getContent()
                ));
            }
            
            // 7. 保存元数据
            document.setStatus(DocumentStatus.COMPLETED);
            document.setChunkCount(chunks.size());
            document.setProcessedAt(LocalDateTime.now());
            documentRepository.save(document);
            
            auditService.log(AuditAction.DOCUMENT_PROCESS, documentId);
            
        } catch (Exception e) {
            log.error("文档处理失败", e);
            document.setStatus(DocumentStatus.FAILED);
            document.setErrorMessage(e.getMessage());
            documentRepository.save(document);
        }
    }
    
    private String cleanText(String text) {
        // 文本清洗逻辑
        return text
            .replaceAll("\\s+", " ")
            .replaceAll("[\\x00-\\x1F]", "")
            .trim();
    }
}
```

---

# 二、RAG引擎模块

## 2.1 模块架构

```java
┌─────────────────────────────────────────────────────┐
│                  RAGOrchestrator                    │
│  (RAG编排器：协调检索和生成流程)                      │
└─────────────────────┬───────────────────────────────┘
                    │
    ┌───────────────┼─────────────────┐
    │               │                 │
    ▼               ▼                 ▼
┌────────┐    ┌────────┐         ┌────────┐
│Embedding│    │Retrieval│         │   LLM   │
│Service  │    │Service  │         │ Service │
└────────┘    └────────┘         └────────┘
    │            │                     │
    │            ▼                     │
    │     ┌────────────┐             │
    │     │  Qdrant  │             │
    │     │  向量库  │             │
    │     └────────────┘             │
    │            │                 │
    │            ▼                   │
    │     ┌────────────┐             │
    └────▶│  检索结果  │◀────────────┘
          └────────────┘
```

## 2.2 向量存储服务

```java
@Service
@Slf4j
@RequiredArgsConstructor
public class VectorStoreService {
    
    private final QdrantClient qdrantClient;
    private final ObjectMapper objectMapper;
    
    @Value("${rag.qdrant.collection:document_embeddings}")
    private String collectionName;
    
    @Value("${rag.vector.dimension:1024}")
    private int vectorDimension;
    
    @PostConstruct
    public void initCollection() {
        if (!collectionExists(collectionName)) {
            createCollection(collectionName);
        }
    }
    
    public String store(UUID documentId, float[] vector, String content) {
        String pointId = UUID.randomUUID().toString();
        
        PointStruct point = PointStruct.builder()
            .id(pointId)
            .vector(vector)
            .payload(buildPayload(documentId, content))
            .build();
        
        qdrantClient.upsert(collectionName, Collections.singletonList(point));
        
        return pointId;
    }
    
    public List<SearchResult> search(float[] queryVector, int topK, String collectionId) {
        SearchRequest request = SearchRequest.builder()
            .vector(queryVector)
            .topK(topK)
            .withPayload(true)
            .scoreThreshold(0.5)
            .build();
        
        ScrollResult result = qdrantClient.search(collectionName, request);
        
        return result.getResults().stream()
            .map(this::toSearchResult)
            .collect(Collectors.toList());
    }
    
    public void deleteByDocumentId(UUID documentId) {
        // 删除文档相关的所有向量
    }
    
    private Map<String, Object> buildPayload(UUID documentId, String content) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("document_id", documentId.toString());
        payload.put("content", content);
        payload.put("content_hash", content.hashCode());
        payload.put("created_at", System.currentTimeMillis());
        return payload;
    }
}
```

## 2.3 LLM服务

```java
@Service
@Slf4j
public class LLMService {
    
    @Value("${llm.provider:ollama}")
    private String provider;
    
    @Value("${llm.temperature:0.7}")
    private double temperature;
    
    @Value("${llm.max-tokens:2048}")
    private int maxTokens;
    
    public String generate(String prompt) {
        return switch (provider.toLowerCase()) {
            case "ollama" -> generateWithOllama(prompt);
            case "vllm" -> generateWithVLLM(prompt);
            default -> throw new UnsupportedOperationException("不支持的LLM provider: " + provider);
        };
    }
    
    public Flux<String> generateStream(String prompt) {
        return generateStream0(prompt);
    }
    
    private String generateWithOllama(String prompt) {
        OllamaRequest request = OllamaRequest.builder()
            .model("qwen2.5:7b")
            .prompt(prompt)
            .temperature(temperature)
            .options(Map.of("num_predict", maxTokens))
            .stream(false)
            .build();
        
        ResponseEntity<OllamaResponse> response = restTemplate.postForEntity(
            ollamaUrl + "/api/generate",
            request,
            OllamaResponse.class
        );
        
        return response.getBody().getResponse();
    }
    
    public Flux<String> generateStream0(String prompt) {
        // SSE流式生成
    }
}
```

## 2.4 RAG编排器

```java
@Service
@Slf4j
@RequiredArgsConstructor
public class RAGOrchestrator {
    
    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    private final LLMService llmService;
    private final PromptTemplateManager promptManager;
    
    public RAGResponse query(RAGRequest request) {
        long startTime = System.currentTimeMillis();
        
        try {
            // 1. 查询向量化
            float[] queryVector = embeddingService.embedQuery(request.getQuestion());
            long embeddingMs = System.currentTimeMillis() - startTime;
            
            // 2. 检索
            List<SearchResult> results = vectorStoreService.search(
                queryVector,
                request.getTopK(),
                request.getCollectionId()
            );
            long searchMs = System.currentTimeMillis() - embeddingMs - startTime;
            
            // 3. 构建上下文
            String context = buildContext(results);
            
            // 4. 构建Prompt
            String prompt = promptManager.buildPrompt(
                request.getQuestion(),
                context,
                request.getConversationHistory()
            );
            
            // 5. 生成回答
            long genStartMs = System.currentTimeMillis();
            String answer = llmService.generate(prompt);
            long genMs = System.currentTimeMillis() - genStartMs;
            
            // 6. 构建响应
            return RAGResponse.builder()
                .answer(answer)
                .sources(results)
                .latency(Latency.builder()
                    .embedding(embeddingMs)
                    .search(searchMs)
                    .generation(genMs)
                    .total(System.currentTimeMillis() - startTime)
                    .build())
                .build();
                
        } catch (Exception e) {
            log.error("RAG查询失败", e);
            throw new BusinessException("RAG_QUERY_FAILED", e.getMessage());
        }
    }
    
    public Flux<String> queryStream(RAGRequest request) {
        // 流式实现
    }
    
    private String buildContext(List<SearchResult> results) {
        if (results.isEmpty()) {
            return "没有找到相关信息";
        }
        
        StringBuilder context = new StringBuilder();
        context.append("参考文档：\n\n");
        
        for (int i = 0; i < results.size(); i++) {
            SearchResult r = results.get(i);
            context.append(String.format("[%d] %s\n", i + 1, r.getContent()));
        }
        
        return context.toString();
    }
}
```

---

# 三、缓存和存储

## 3.1 Redis缓存策略

```java
@Configuration
@EnableCaching
public class RedisConfig {
    
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory factory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofHours(1))
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new StringRedisSerializer()))
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new GenericJackson2JsonRedisSerializer()));
        
        return new RedisCacheManager(factory, config);
    }
}

@Service
@Slf4j
public class EmbeddingCacheService {
    
    @Cacheable(value = "embeddings", key = "#text.hashCode()")
    public float[] getEmbedding(String text) {
        // 实际调用Embedding服务
    }
    
    @Cacheable(value = "documents", key = "#docId", unless = "#result == null")
    public Document getDocument(UUID docId) {
        return documentRepository.findById(docId).orElse(null);
    }
}
```

## 3.2 文件存储服务

```java
@Service
@Slf4j
@RequiredArgsConstructor
public class MinioStorageService {
    
    private final MinioClient minioClient;
    
    @Value("${minio.bucket.documents:rag-documents}")
    private String bucket;
    
    public String upload(MultipartFile file, UUID userId) throws Exception {
        String objectName = generateObjectName(userId, file.getOriginalFilename());
        
        minioClient.putObject(PutObjectArgs.builder()
            .bucket(bucket)
            .object(objectName)
            .stream(
                file.getInputStream(),
                file.getSize(),
                -1)
            .build());
        
        return objectName;
    }
    
    public InputStream download(String objectName) throws Exception {
        return minioClient.getObject(GetObjectArgs.builder()
            .bucket(bucket)
            .object(objectName)
            .build());
    }
    
    public void delete(String objectName) {
        minioClient.removeObject(RemoveObjectArgs.builder()
            .bucket(bucket)
            .object(objectName)
            .build());
    }
    
    public String getPresignedUrl(String objectName) {
        return minioClient.getPresignedObjectUrl(
            GetPresignedObjectUrlArgs.builder()
                .method(Method.GET)
                .bucket(bucket)
                .object(objectName)
                .expiry(1, TimeUnit.HOURS)
                .build());
    }
}
```

---

# 四、异常处理机制

## 4.1 异常分类体系

```java
// 基础异常
public class BaseException extends RuntimeException {
    private final String code;
    private final Map<String, Object> details;
    private final HttpStatus httpStatus;
}

// 业务异常
public class BusinessException extends BaseException {
    public BusinessException(String code, String message, HttpStatus status) {
        super(code, message, Collections.emptyMap(), status);
    }
}

// 文档处理异常
public class DocumentProcessingException extends BusinessException {
    private final UUID documentId;
    private final DocumentProcessingStage stage;
    
    public DocumentProcessingException(UUID documentId, DocumentProcessingStage stage, Throwable cause) {
        super("DOC_" + stage.ordinal(), 
              "文档处理在[" + stage + "]阶段失败: " + cause.getMessage(),
              HttpStatus.INTERNAL_SERVER_ERROR);
        this.documentId = documentId;
        this.stage = stage;
    }
}

// 向量服务异常
public class VectorStoreException extends BusinessException {
    public VectorStoreException(String operation, Throwable cause) {
        super("SYS_007", "向量数据库操作失败: " + operation, HttpStatus.SERVICE_UNAVAILABLE);
    }
}

// LLM服务异常
public class LLMServiceException extends BusinessException {
    private final String model;
    private final Integer errorCode;
    
    public LLMServiceException(String model, String message, Integer errorCode) {
        super("RAG_003", "LLM服务调用失败: " + message, HttpStatus.SERVICE_UNAVAILABLE);
        this.model = model;
        this.errorCode = errorCode;
    }
}

// Embedding服务异常
public class EmbeddingServiceException extends BusinessException {
    public EmbeddingServiceException(String message, Throwable cause) {
        super("RAG_002", "Embedding服务不可用: " + message, HttpStatus.SERVICE_UNAVAILABLE);
    }
}
```

## 4.2 全局异常处理器

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    private final AlertService alertService;
    private final TraceService traceService;
    
    @ExceptionHandler(DocumentProcessingException.class)
    public ResponseEntity<ErrorResponse> handleDocumentProcessing(
            DocumentProcessingException ex, HttpServletRequest request) {
        
        log.error("文档处理失败: documentId={}, stage={}", 
            ex.getDocumentId(), ex.getStage(), ex);
        
        // 发送告警
        alertService.sendDocumentProcessingAlert(ex);
        
        ErrorResponse response = ErrorResponse.builder()
            .code(ex.getCode())
            .message(ex.getMessage())
            .requestId(getRequestId(request))
            .timestamp(Instant.now())
            .details(Map.of(
                "documentId", ex.getDocumentId(),
                "stage", ex.getStage()
            ))
            .build();
        
        return ResponseEntity.status(ex.getHttpStatus()).body(response);
    }
    
    @ExceptionHandler(VectorStoreException.class)
    public ResponseEntity<ErrorResponse> handleVectorStore(
            VectorStoreException ex, HttpServletRequest request) {
        
        log.error("向量数据库异常", ex);
        alertService.sendCriticalAlert("VectorStore", ex.getMessage());
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(ErrorResponse.systemError("向量服务暂时不可用，请稍后重试"));
    }
    
    @ExceptionHandler(LLMServiceException.class)
    public ResponseEntity<ErrorResponse> handleLLMService(
            LLMServiceException ex, HttpServletRequest request) {
        
        log.error("LLM服务异常: model={}, errorCode={}", 
            ex.getModel(), ex.getErrorCode(), ex);
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(ErrorResponse.builder()
                .code(ex.getCode())
                .message("AI服务暂时繁忙，请稍后重试")
                .requestId(getRequestId(request))
                .timestamp(Instant.now())
                .build());
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
            .collect(Collectors.toMap(
                FieldError::getField,
                error -> error.getDefaultMessage() != null ? 
                    error.getDefaultMessage() : "无效的值",
                (a, b) -> a
            ));
        
        return ResponseEntity.badRequest()
            .body(ErrorResponse.validationFailed(errors));
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(
            Exception ex, HttpServletRequest request) {
        
        String traceId = traceService.captureException(ex);
        
        log.error("系统异常: traceId={}", traceId, ex);
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse.systemError("系统异常，请联系管理员"));
    }
}
```

## 4.3 边界条件处理

### 4.3.1 文档处理边界条件

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentBoundaryHandler {
    
    private static final long MAX_FILE_SIZE = 100 * 1024 * 1024; // 100MB
    private static final long MAX_TEXT_LENGTH = 10 * 1024 * 1024; // 10M字符
    private static final int MAX_PAGES = 10000;
    private static final Duration PROCESS_TIMEOUT = Duration.ofMinutes(30);
    
    public void validateFile(MultipartFile file) {
        // 空文件检查
        if (file.isEmpty() || file.getSize() == 0) {
            throw new ValidationException("DOC_011", "文件不能为空");
        }
        
        // 文件大小检查
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ValidationException("DOC_003", 
                String.format("文件大小不能超过%dMB", MAX_FILE_SIZE / 1024 / 1024));
        }
        
        // 文件类型检查
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !isSupportedFileType(originalFilename)) {
            throw new ValidationException("DOC_002", "不支持的文件类型");
        }
        
        // 文件名长度检查
        if (originalFilename.length() > 255) {
            throw new ValidationException("DOC_012", "文件名过长");
        }
    }
    
    public String truncateTextIfNeeded(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        
        if (text.length() > maxLength) {
            log.warn("文本长度{}超过限制{}，将进行截断", text.length(), maxLength);
            return text.substring(0, maxLength);
        }
        
        return text;
    }
    
    public boolean checkProcessingTimeout(Document document) {
        Duration elapsed = Duration.between(
            document.getCreatedAt(), 
            LocalDateTime.now()
        );
        
        if (elapsed.compareTo(PROCESS_TIMEOUT) > 0) {
            log.warn("文档{}处理超时，已运行{}分钟", 
                document.getId(), elapsed.toMinutes());
            return true;
        }
        
        return false;
    }
    
    private boolean isSupportedFileType(String filename) {
        String extension = FilenameUtils.getExtension(filename).toLowerCase();
        return Arrays.asList("pdf", "docx", "doc", "txt", "md", "html", "csv", "xlsx", "pptx")
            .contains(extension);
    }
}
```

### 4.3.2 RAG查询边界条件

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class RAGBoundaryHandler {
    
    private static final int MAX_QUESTION_LENGTH = 2000;
    private static final int MAX_CONTEXT_LENGTH = 8000;
    private static final int MAX_HISTORY_MESSAGES = 20;
    private static final double MIN_SIMILARITY_SCORE = 0.3;
    
    public void validateQuery(RAGRequest request) {
        // 问题长度检查
        if (request.getQuestion() == null || request.getQuestion().isBlank()) {
            throw new ValidationException("RAG_010", "问题不能为空");
        }
        
        if (request.getQuestion().length() > MAX_QUESTION_LENGTH) {
            throw new ValidationException("RAG_006", 
                String.format("问题长度不能超过%d字符", MAX_QUESTION_LENGTH));
        }
        
        // 知识库ID检查
        if (request.getCollectionId() == null) {
            throw new ValidationException("COL_001", "知识库ID不能为空");
        }
        
        // 检索参数检查
        if (request.getTopK() != null) {
            if (request.getTopK() < 1 || request.getTopK() > 50) {
                throw new ValidationException("RAG_007", "topK参数范围应为1-50");
            }
        }
    }
    
    public String truncateContext(String context) {
        if (context == null) {
            return "";
        }
        
        if (context.length() > MAX_CONTEXT_LENGTH) {
            log.warn("上下文长度{}超过LLM限制{}，将进行截断", 
                context.length(), MAX_CONTEXT_LENGTH);
            return context.substring(0, MAX_CONTEXT_LENGTH);
        }
        
        return context;
    }
    
    public List<SearchResult> filterLowScoreResults(List<SearchResult> results) {
        return results.stream()
            .filter(r -> r.getScore() >= MIN_SIMILARITY_SCORE)
            .collect(Collectors.toList());
    }
    
    public List<Message> truncateHistory(List<Message> history) {
        if (history == null || history.size() <= MAX_HISTORY_MESSAGES) {
            return history;
        }
        
        log.info("对话历史{}条超过限制{}，将截断旧消息", 
            history.size(), MAX_HISTORY_MESSAGES);
        
        return history.subList(
            history.size() - MAX_HISTORY_MESSAGES, 
            history.size()
        );
    }
}
```

### 4.3.3 错误恢复机制

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentRecoveryService {
    
    private final DocumentRepository documentRepository;
    private final DocumentProcessingService processingService;
    private final AlertService alertService;
    
    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void processFailedDocuments() {
        List<Document> failedDocs = documentRepository.findByStatusWithRetryCount(
            DocumentStatus.FAILED, 3);
        
        for (Document doc : failedDocs) {
            try {
                log.info("开始重试处理文档: {}", doc.getId());
                doc.setStatus(DocumentStatus.PENDING);
                doc.setRetryCount(doc.getRetryCount() + 1);
                doc.setErrorMessage(null);
                documentRepository.save(doc);
                
                processingService.processDocumentAsync(doc.getId());
                
            } catch (Exception e) {
                log.error("重试处理失败: {}", doc.getId(), e);
                
                if (doc.getRetryCount() >= 3) {
                    alertService.sendDocumentProcessingAlert(doc, 
                        "文档处理重试超过3次，请人工介入");
                }
            }
        }
    }
    
    @Scheduled(cron = "0 0 2 * * ?") // 每天凌晨2点
    @Transactional
    public void cleanupStuckDocuments() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(2);
        List<Document> stuckDocs = documentRepository.findStuckDocuments(threshold);
        
        for (Document doc : stuckDocs) {
            log.warn("发现卡住文档: {}, 状态: {}, 创建时间: {}", 
                doc.getId(), doc.getStatus(), doc.getCreatedAt());
            
            doc.setStatus(DocumentStatus.FAILED);
            doc.setErrorMessage("处理超时，已自动标记为失败");
            documentRepository.save(doc);
        }
    }
}
```

### 4.3.4 降级策略

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class ServiceDegradationHandler {
    
    private final LLMService llmService;
    private final EmbeddingService embeddingService;
    private final FallbackResponseService fallbackService;
    
    public String generateWithFallback(RAGRequest request) {
        try {
            return llmService.generate(request.getPrompt());
        } catch (LLMServiceException e) {
            log.warn("LLM服务不可用，启用降级策略", e);
            return fallbackService.getSimpleResponse(request.getQuestion());
        }
    }
    
    public List<float[]> embedWithFallback(List<String> texts) {
        try {
            return embeddingService.embedBatch(texts);
        } catch (EmbeddingServiceException e) {
            log.warn("Embedding服务不可用，启用降级策略", e);
            return texts.stream()
                .map(this::generateSimpleEmbedding)
                .collect(Collectors.toList());
        }
    }
    
    private float[] generateSimpleEmbedding(String text) {
        float[] vector = new float[embeddingDimension];
        for (int i = 0; i < vector.length; i++) {
            vector[i] = (float) (Math.random() * 0.01);
        }
        return vector;
    }
}

@Service
@RequiredArgsConstructor
public class FallbackResponseService {
    
    public String getSimpleResponse(String question) {
        return String.format(
            "抱歉，当前AI服务暂时不可用。\n\n您的问题：「%s」\n\n" +
            "建议：\n" +
            "1. 请稍后重试\n" +
            "2. 如果问题紧急，请联系管理员\n" +
            "3. 可以尝试简化问题关键词",
            question
        );
    }
}
```

---

*第二部分完成*
