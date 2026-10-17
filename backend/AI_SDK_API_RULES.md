# AI SDK API 集成规范

本文档定义 Java 后端与 AI SDK Python 服务交互的标准规范。

## 1. 服务地址配置

| 环境 | AI SDK 地址 | 端口 |
|------|-------------|------|
| 本地开发 | localhost | 28347 |
| 服务器部署 | your-server-host | 28347 |

### 配置文件

- **本地开发**: `application.yml` (默认 localhost)
- **Docker部署**: `application-docker.yml` (默认 your-server-host)

```yaml
rag:
  embedding:
    host: ${EMBEDDING_HOST:localhost}
    port: ${EMBEDDING_PORT:28347}
  llm:
    host: ${LLM_HOST:localhost}
    port: ${LLM_PORT:28347}
  ocr:
    service-url: ${OCR_SERVICE_URL:http://localhost:28347}
```

## 2. API 接口规格

### 2.1 GLM OCR 接口

#### POST /api/glm/read
- **用途**: 无提示词读取文件（PDF/图片通过 OCR 识别，文本文件直接读取）
- **Content-Type**: `multipart/form-data`
- **请求体**:
  - `files`: 文件二进制数组 (application/octet-stream)

```java
// Java 调用示例
MultiValueMap<String, Object> requestBody = new LinkedMultiValueMap<>();
requestBody.add("files", new FileSystemResource(filePath));
// 注意: files 是数组，即使单文件也要通过 LinkedMultiValueMap.add() 添加
```

#### POST /api/glm/extract
- **用途**: 有提示词提取文件内容
- **Content-Type**: `multipart/form-data`
- **请求体**:
  - `files`: 文件二进制数组 (application/octet-stream)
  - `prompt`: 提取提示词 (string)

#### POST /api/glm/extract-json
- **用途**: 有提示词 + JSON Schema 提取结构化信息
- **Content-Type**: `multipart/form-data`
- **请求体**:
  - `files`: 文件二进制数组 (application/octet-stream)
  - `prompt`: 提取提示词 (string)
  - `json_schema`: JSON Schema 字符串 (string)

### 2.2 Qwen 对话接口

#### POST /api/qwen/chat
- **用途**: 普通对话接口（非流式）
- **Content-Type**: `application/json`
- **请求体**:
```json
{
  "message": "用户消息",
  "history": [{"role": "user", "content": "..."}],
  "temperature": 0.7,
  "top_p": 0.9,
  "top_k": 50,
  "max_tokens": 2048,
  "repetition_penalty": 1.1,
  "presence_penalty": 0.0,
  "thinking_enabled": false
}
```

#### POST /api/qwen/chat/stream
- **用途**: 流式对话接口（SSE 格式）
- **Content-Type**: `application/json`
- **响应格式**: `data: {...}` 行，每行一个 JSON 对象
- **请求体**: 同 `/api/qwen/chat`，`stream: true`

### 2.3 Embedding 接口

#### POST /api/embedding/embed
- **用途**: 生成文本 embedding 向量
- **Content-Type**: `application/json`
- **请求体**:
```json
{
  "input": ["文本1", "文本2"],
  "model": "/models/bge-large-zh-v1.5"
}
```

## 3. Java 服务实现规范

### 3.1 LLMService (Qwen 对话)

```java
@Service
@RequiredArgsConstructor
public class LLMService {
    private static final String CHAT_API_PATH = "/api/qwen/chat";
    private static final String STREAM_API_PATH = "/api/qwen/chat/stream";

    private final RAGProperties ragProperties;
    private final WebClient webClient;

    public String generate(String systemPrompt, String userMessage) {
        // 非流式调用
    }

    public Flux<String> streamGenerate(String systemPrompt, String userMessage) {
        // 流式调用，过滤 "data: " 前缀
    }
}
```

**请求参数映射**:
| API 字段 | 来源 | 默认值 |
|---------|------|--------|
| message | systemPrompt + userMessage 拼接 | - |
| temperature | RAGProperties.LLMConfig | 0.7 |
| top_p | 固定值 | 0.9 |
| top_k | 固定值 | 50 |
| max_tokens | RAGProperties.LLMConfig | 2048 |
| repetition_penalty | 固定值 | 1.1 |
| presence_penalty | 固定值 | 0.0 |
| thinking_enabled | 固定值 | false |
| stream | 根据方法 | true/false |

### 3.2 OcrService (GLM OCR)

```java
@Service
@RequiredArgsConstructor
public class OcrService {
    private static final String READ_API = "/api/glm/read";
    private static final String EXTRACT_API = "/api/glm/extract";

    private final RestTemplate restTemplate;

    private OcrResultDTO callOcrService(String url, String filePath, int page, String model) {
        // 重要: files 参数必须使用 FileSystemResource 发送实际文件
        // 不能只发送文件路径字符串，API 期望 UploadFile 对象
        requestBody.add("files", new FileSystemResource(filePath));
    }
}
```

**注意事项**:
- `files` 字段发送实际文件内容，不能只发送文件路径
- 使用 `FileSystemResource` 或 `UrlResource` 封装文件
- OCR 失败时自动切换到 fallback URL (Qwen 模型)

### 3.3 EmbeddingService

```java
@Service
public class EmbeddingService {
    // 使用同步 Map + LRU 缓存
    // 注意: 返回的 float[] 数组需要做防御性拷贝，防止缓存被修改

    public float[] embed(String text) {
        synchronized (embeddingCache) {
            if (embeddingCache.containsKey(cacheKey)) {
                float[] cached = embeddingCache.get(cacheKey);
                return Arrays.copyOf(cached, cached.length); // 防御性拷贝
            }
        }
        // 调用远程 API...
    }
}
```

## 4. 响应 DTO 规范

### 4.1 QwenChatResponse
```java
@Data
public class QwenChatResponse {
    private String response;           // 模型回复
    private List<Map<String, String>> history;  // 对话历史
    private TokenUsage usage;          // Token 使用统计
    private String request_id;         // 请求 ID
}
```

### 4.2 GlmReadResponse (OCR)
```java
@Data
public class GlmReadResponse {
    private List<FileResult> results;   // 文件处理结果列表
    private ProcessSummary summary;     // 处理汇总
    private String request_id;

    @Data
    public static class FileResult {
        private String filename;       // 文件名
        private String status;         // "success" 或 "failed"
        private String content;        // 文件内容 (read 模式)
        private String extracted;      // 提取内容 (extract 模式)
        private Object data;           // 结构化数据 (json 模式)
        private String error;           // 错误信息
        private Integer size;           // 文件大小
    }

    @Data
    public static class ProcessSummary {
        private Integer total;         // 总文件数
        private Integer success;       // 成功数
        private Integer failed;        // 失败数
        private Long total_size;       // 总大小
    }
}
```

## 5. 错误处理

### 5.1 AI SDK 服务不可用
```java
try {
    // 调用 AI SDK
} catch (Exception e) {
    throw new AiServiceException("LLM", "服务调用失败: " + e.getMessage());
}
```

### 5.2 OCR 降级处理
```java
try {
    result = callGlmOcr(url, filePath, page);
} catch (Exception e) {
    // 降级到 Qwen 模型
    result = callQwenOcr(fallbackUrl, filePath, page, "Qwen3.6-27B");
}
```

## 6. 常见问题

### Q: OCR 请求返回 422 验证错误
**A**: 检查 `files` 参数是否正确发送了文件内容，而非文件路径字符串。API 期望 `UploadFile` 对象。

### Q: AI SDK 返回 404 /v1/models
**A**: 这是 AI SDK Python 服务端问题，内部尝试调用 OpenAI 兼容接口但未配置。请检查:
1. AI SDK 是否正确安装了 LLM 模型后端
2. AI SDK 的 `/v1/models` 端点是否启用

### Q: 向量维度不匹配
**A**: 确保 `RAGProperties.embedding.dimension` (默认 1024) 与 `RAGProperties.qdrant.collection.vectorSize` 一致。

## 7. 配置优先级

```
环境变量 > application-{profile}.yml > application.yml > 代码默认值
```

示例:
```bash
export EMBEDDING_HOST=your-server-host
export LLM_HOST=your-server-host
export OCR_SERVICE_URL=http://your-server-host:28347
```
