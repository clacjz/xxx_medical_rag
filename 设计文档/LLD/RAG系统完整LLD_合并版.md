# RAG知识库系统完整LLD

## 完整详细设计文档

> **文档类型**: 低层设计（LLD）
> **编制日期**: 2026-03-25
> **参考**: RAG知识库系统HLD_V3.0

---

# 第一部分：数据库和API设计

## 一、数据库设计

### 1.1 PostgreSQL表结构

#### 用户表(users)

```sql
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(100),
    status VARCHAR(20) DEFAULT 'active',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_status ON users(status);
```

#### 文档表(documents)

```sql
CREATE TABLE documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    collection_id UUID REFERENCES collections(id),
    title VARCHAR(255) NOT NULL,
    file_path VARCHAR(500),
    file_type VARCHAR(20),
    status VARCHAR(20) DEFAULT 'pending',
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_documents_collection ON documents(collection_id);
CREATE INDEX idx_documents_status ON documents(status);
```

#### 消息表(messages)

```sql
CREATE TABLE messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID REFERENCES conversations(id),
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

## 二、API规范

### 2.1 统一响应格式

```json
{
  "code": 200,
  "message": "success",
  "data": {},
  "timestamp": "2026-03-25T10:00:00Z"
}
```

### 2.2 核心API

#### 认证接口

```yaml
POST /api/v1/auth/register - 用户注册
POST /api/v1/auth/login - 用户登录
POST /api/v1/auth/refresh - 刷新Token
POST /api/v1/auth/logout - 登出
```

#### 文档接口

```yaml
POST /api/v1/documents/upload - 上传文档
GET /api/v1/documents/{id} - 文档详情
GET /api/v1/documents/{id}/chunks - 文档块
DELETE /api/v1/documents/{id} - 删除文档
```

#### RAG接口

```yaml
POST /api/v1/rag/query - 问答查询
POST /api/v1/rag/query/stream - 流式问答
GET /api/v1/conversations - 对话列表
```

---

# 第二部分：核心模块设计

## 三、文档处理模块

### 3.1 DocumentProcessor接口

```java
public interface DocumentProcessor {
    boolean supports(String fileType);
    Document extractText(InputStream inputStream, String fileName);
    DocumentMetadata extractMetadata(InputStream inputStream);
}

@Component
public class PDFDocumentProcessor implements DocumentProcessor {
    @Override
    public boolean supports(String fileType) {
        return "pdf".equalsIgnoreCase(fileType);
    }
    
    @Override
    public Document extractText(InputStream inputStream, String fileName) {
        // PDF解析实现
    }
}

@Component
public class WordDocumentProcessor implements DocumentProcessor {
    @Override
    public boolean supports(String fileType) {
        return "docx".equalsIgnoreCase(fileType);
    }
}

@Component
public class MarkdownProcessor implements DocumentProcessor {
    @Override
    public boolean supports(String fileType) {
        return "md".equalsIgnoreCase(fileType);
    }
}
```

### 3.2 DocumentChunker

```java
@Service
public class DocumentChunker {
    public List<TextChunk> chunk(String text, ChunkingConfig config) {
        return strategy.chunk(text, config);
    }
}

@Component
public class FixedSizeChunkingStrategy implements ChunkingStrategy {
    public List<TextChunk> chunk(String text, ChunkingConfig config) {
        // 固定长度分块
    }
}

@Component
public class SemanticChunkingStrategy implements ChunkingStrategy {
    public List<TextChunk> chunk(String text, ChunkingConfig config) {
        // 语义分块
    }
}
```

## 四、RAG引擎

### 4.1 Embedding服务

```java
@Service
public class EmbeddingService {
    public float[] embed(String text) {
        // 调用本地Embedding服务
    }
    
    public List<float[]> embedBatch(List<String> texts) {
        // 批量嵌入
    }
}
```

### 4.2 LLM服务

```java
@Service
public class LLMService {
    public String generate(String prompt) {
        // Ollama/vLLM调用
    }
    
    public Flux<String> generateStream(String prompt) {
        // 流式生成
    }
}
```

### 4.3 RAGOrchestrator

```java
@Service
public class RAGOrchestrator {
    public QueryResponse query(QueryRequest request) {
        // 1. Embedding查询
        float[] vector = embeddingService.embed(request.getQuestion());
        
        // 2. 检索
        List<SearchResult> results = retrievalService.search(vector);
        
        // 3. 构建Prompt
        String context = buildContext(results);
        
        // 4. LLM生成
        String answer = llmService.generate(context);
        
        return QueryResponse.builder()
            .answer(answer)
            .sources(results)
            .build();
    }
    
    private String buildContext(List<SearchResult> results) {
        // 构建上下文
    }
}
```

---

# 第三部分：性能优化和安全

## 五、性能优化

### 5.1 数据库优化

```sql
-- 索引
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_documents_collection ON documents(collection_id);
CREATE INDEX idx_messages_conversation ON messages(conversation_id);

-- 分页查询
SELECT * FROM documents 
WHERE collection_id = ?
ORDER BY created_at DESC
LIMIT 20 OFFSET 0;
```

### 5.2 缓存策略

```java
@Service
public class CacheService {
    @Cacheable(value = "users", key = "#id")
    public User getUser(UUID id) {
        // 三级缓存
    }
}
```

## 六、安全设计

### 6.1 JWT认证

```java
@Component
public class JwtTokenProvider {
    public String createToken(UserDetails user) {
        return Jwts.builder()
            .setSubject(user.getUsername())
            .claim("roles", user.getAuthorities())
            .setExpiration(new Date(System.currentTimeMillis() + 3600000))
            .signWith(key)
            .compact();
    }
}
```

### 6.2 限流

```java
@RateLimiter(name = "api", fallbackMethod = "fallback")
public String apiEndpoint() {
    return "OK";
}
```

---

## 第四部分：部署配置

### 7.1 application.yml

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}
  
rag:
  embedding:
    url: ${EMBEDDING_SERVICE_URL}
  
  llm:
    base-url: ${LLM_BASE_URL}
  
  qdrant:
    host: ${QDRANT_HOST}
    port: ${QDRANT_PORT}
```

### 7.2 环境变量

```bash
export DB_HOST=localhost
export DB_PORT=5432
export REDIS_HOST=localhost
export QDRANT_HOST=localhost
export EMBEDDING_SERVICE_URL=http://localhost:8081
export LLM_BASE_URL=http://localhost:11434
```

---

*完整LLD文档完成*
