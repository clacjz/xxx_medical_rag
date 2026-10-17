# 完整RAG知识库系统实施计划

> **最后更新日期**: 2026-03-25
> **技术栈**: Java Spring AI + Qdrant + 本地LLM部署 + 本地Embedding部署

---

## 一、系统架构设计

### 1.1 整体架构
```
┌────────────────────────────────────────────────────────────────────────┐
│                    前端交互层 (Web UI)                                  │
│                    (技术栈待定)                                          │
├────────────────────────────────────────────────────────────────────────┤
│                    API网关层 (Spring Cloud Gateway)                       │
├──────────────┬─────────────────┬─────────────────┬───────────────────┤
│  文档管理API │     问答API      │    用户管理API   │    配置管理API     │
├──────────────┴─────────────────┴─────────────────┴───────────────────┤
│                    核心服务层 (Spring Boot)                               │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────────┐ │
│  │ 文档处理服务 │ │ RAG引擎服务  │ │ 权限控制服务 │ │ 配置管理服务     │ │
│  └─────────────┘ └─────────────┘ └─────────────┘ └─────────────────┘ │
├────────────────────────────────────────────────────────────────────────┤
│                    AI服务层                                              │
│  ┌──────────────────┐ ┌──────────────────┐ ┌──────────────────────┐ │
│  │  Embedding服务   │ │    LLM服务       │ │    Rerank服务        │ │
│  │ (本地BGE/Instructor) │ │ (Ollama/vLLM)  │ │  (可选，Cohere本地) │ │
│  └──────────────────┘ └──────────────────┘ └──────────────────────┘ │
├────────────────────────────────────────────────────────────────────────┤
│                    数据存储层                                            │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────────┐│
│  │   Qdrant    │ │ PostgreSQL  │ │    Redis    │ │ 本地上传目录     ││
│  │  (向量数据库) │ │  (关系数据库) │ │   (缓存)    │ │   (客户内网)    ││
│  └─────────────┘ └─────────────┘ └─────────────┘ └─────────────────┘│
└────────────────────────────────────────────────────────────────────────┘
```

### 1.2 技术栈选型

**后端框架**: Spring Boot 3.x + Spring AI
- Spring AI 提供统一的AI模型抽象层
- 支持多种LLM提供商和本地模型
- 自动API文档生成（SpringDoc OpenAPI）

**数据库**:
- PostgreSQL 15+: 用户数据、文档元数据、权限配置
- Qdrant: 向量数据库（文档嵌入存储，10GB规模最优选择）
- Redis: 缓存层、会话管理、Celery结果存储

**文档处理**:
- PDF: Apache PDFBox + pdfplumber（表格提取）
- Word: Apache POI
- Markdown: CommonMark解析
- TXT: 原生IO处理

**Embedding模型（本地部署）**:
- **主要推荐**: BGE-large-zh-v1.5（中文优化，开源免费）
- **备选**: Instructor-large（MTEB榜单表现优秀）
- **备选**: M3E-large（国产开源，中文友好）
- 服务化部署：Sentence Transformers + FastAPI

**LLM集成（本地部署）**:
- Ollama / vLLM（主要）
- Spring AI抽象层支持无缝切换

**认证授权**:
- JWT Token（jjwt库）
- Spring Security + RBAC

**异步任务**:
- Celery + Redis（生产环境推荐）

---

## 二、Embedding模型服务设计

### 2.1 本地Embedding模型选型对比

| 模型 | 向量维度 | 中文支持 | 硬件要求 | MTEB评分 | 推荐场景 |
|------|----------|----------|----------|----------|----------|
| **BGE-large-zh-v1.5** | 1024 | ⭐⭐⭐⭐⭐ | GPU推荐（8GB+） | 64.1% | **首选** |
| Instructor-large | 1024/768 | ⭐⭐⭐⭐ | GPU推荐 | 66.3% | 高精度场景 |
| M3E-large | 1024 | ⭐⭐⭐⭐ | CPU可用 | 63.0% | 轻量部署 |
| text2vec-base-chinese | 768 | ⭐⭐⭐ | CPU可用 | 57.0% | 资源受限 |

**推荐方案**: BGE-large-zh-v1.5
- 中文语义理解优秀
- 开源免费，可商用
- 社区活跃，模型更新及时
- 支持CPU推理（速度较慢）

### 2.2 Embedding服务架构

```yaml
# Embedding服务架构
┌─────────────────────────────────────────────────────────┐
│                 Spring Boot Application                  │
│  ┌─────────────────────────────────────────────────┐   │
│  │           EmbeddingService                       │   │
│  │  ┌─────────────┐  ┌─────────────────────────┐  │   │
│  │  │ 批量嵌入API  │  │  单条查询嵌入API        │  │   │
│  │  └──────┬──────┘  └───────────┬─────────────┘  │   │
│  └─────────┼──────────────────────┼─────────────────┘   │
│            │                      │                      │
│            └──────────┬───────────┘                      │
│                       │ HTTP/REST                        │
│                       ▼                                  │
│  ┌─────────────────────────────────────────────────┐   │
│  │           Embedding Service (独立部署)           │   │
│  │  ┌─────────────────────────────────────────────┐│   │
│  │  │  Sentence Transformers + FastAPI             ││   │
│  │  │  Model: BGE-large-zh-v1.5                   ││   │
│  │  │  Port: 8081                                  ││   │
│  │  └─────────────────────────────────────────────┘│   │
│  └─────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
```

### 2.3 Embedding服务API接口

```yaml
# Embedding Service REST API

# 单条文本嵌入
POST /embed
Request:
{
  "text": "要嵌入的文本内容",
  "normalize": true  # 是否归一化向量
}
Response:
{
  "embedding": [0.123, -0.456, ...],  # 1024维向量
  "model": "bge-large-zh-v1.5",
  "dimension": 1024,
  "processing_time_ms": 45
}

# 批量文本嵌入
POST /embed/batch
Request:
{
  "texts": ["文本1", "文本2", "文本3"],
  "normalize": true
}
Response:
{
  "embeddings": [
    [0.123, -0.456, ...],
    [0.789, 0.012, ...],
    [-0.234, 0.567, ...]
  ],
  "model": "bge-large-zh-v1.5",
  "dimension": 1024,
  "batch_size": 3,
  "processing_time_ms": 120
}

# 模型信息
GET /model/info
Response:
{
  "model_name": "bge-large-zh-v1.5",
  "model_type": "sentence-transformers",
  "dimension": 1024,
  "max_seq_length": 512,
  "normalize": true,
  "device": "cuda"  # 或 "cpu"
}

# 健康检查
GET /health
Response:
{
  "status": "healthy",
  "model_loaded": true,
  "device": "cuda",
  "gpu_available": true
}
```

---

## 三、模块详细设计

### 3.1 文档收集与清洗模块

#### 功能特性:
1. **多格式文档支持**
   - PDF文档解析与文本提取
   - Word(.docx)文档解析
   - Markdown文件解析
   - TXT纯文本处理
   - 支持批量导入（ZIP打包）

2. **文本预处理**
   - 编码检测与转换（chardet）
   - 格式标准化（统一换行符、编码）
   - 无关信息过滤（页眉页脚、目录、水印）

3. **去重与质量控制**
   - 内容相似度检测（SimHash/MinHash）
   - 重复段落识别与合并
   - 空文档过滤

4. **分块策略（可配置）**
   ```yaml
   # application.yml 中的默认配置
   rag:
     chunking:
       strategy: "fixed_size"  # fixed_size / semantic / hybrid
       chunk_size: 512         # tokens
       overlap: 64             # tokens
       min_chunk_size: 50      # tokens
       preserve_structure: true # 尽量保持段落完整性
   ```

#### 核心类设计:
```java
// 文档处理器接口（策略模式）
public interface DocumentProcessor {
    boolean supports(String fileType);
    Document extractText(InputStream inputStream, String fileName);
    DocumentMetadata extractMetadata(InputStream inputStream);
}

// PDF处理器
@Component
public class PDFDocumentProcessor implements DocumentProcessor {
    @Override
    public boolean supports(String fileType) {
        return "pdf".equalsIgnoreCase(fileType);
    }
    // 使用Apache PDFBox + pdfplumber
}

// 文本清洗器
@Component
public class TextCleaner {
    public String removeNoise(String text);
    public String normalizeWhitespace(String text);
    public String detectAndConvertEncoding(byte[] content);
}

// 文档分块器（策略模式 + 可配置）
@Component
public class DocumentChunker {
    private final ChunkingStrategy fixedSizeStrategy;
    private final ChunkingStrategy semanticStrategy;
    private final ChunkingStrategy hybridStrategy;
    
    public List<TextChunk> chunk(Document document, ChunkingConfig config);
}

// 分块策略接口
public interface ChunkingStrategy {
    List<TextChunk> chunk(String text, ChunkingConfig config);
}

// 固定长度分块
@Component
public class FixedSizeChunkingStrategy implements ChunkingStrategy {
    // 按固定长度切分，保留重叠部分
}

// 语义分块
@Component
public class SemanticChunkingStrategy implements ChunkingStrategy {
    // 按段落/章节边界切分，保持语义完整性
}
```

### 3.2 内容生成模块（RAG引擎）

#### 功能特性:
1. **文本嵌入（本地部署）**
   ```java
   // 嵌入服务客户端
   @Service
   public class EmbeddingClient {
       private final RestTemplate restTemplate;
       private final String embeddingServiceUrl;
       
       // 单条嵌入
       public float[] embed(String text) {
           EmbedRequest request = new EmbedRequest(text, true);
           EmbedResponse response = restTemplate.postForObject(
               embeddingServiceUrl + "/embed", 
               request, 
               EmbedResponse.class
           );
           return response.getEmbedding();
       }
       
       // 批量嵌入
       public List<float[]> embedBatch(List<String> texts) {
           BatchEmbedRequest request = new BatchEmbedRequest(texts, true);
           BatchEmbedResponse response = restTemplate.postForObject(
               embeddingServiceUrl + "/embed/batch", 
               request, 
               BatchEmbedResponse.class
           );
           return response.getEmbeddings();
       }
   }
   
   // 嵌入服务接口（统一抽象）
   public interface EmbeddingService {
       List<float[]> embed(List<String> texts);
       float[] embedQuery(String query);
       EmbeddingModel getModelType();
       int getDimension();
   }
   
   // 本地Embedding实现
   @Component
   public class LocalEmbeddingService implements EmbeddingService {
       private final EmbeddingClient embeddingClient;
       
       @Override
       public List<float[]> embed(List<String> texts) {
           return embeddingClient.embedBatch(texts);
       }
       
       @Override
       public float[] embedQuery(String query) {
           return embeddingClient.embed(query);
       }
       
       @Override
       public EmbeddingModel getModelType() {
           return EmbeddingModel.BGE_LARGE_ZH;
       }
       
       @Override
       public int getDimension() {
           return 1024;  // BGE-large-zh-v1.5 维度
       }
   }
   ```

2. **向量检索（可配置策略）**
   ```java
   // 检索策略接口
   public interface RetrievalStrategy {
       List<检索结果> search(String query, int topK, RetrievalConfig config);
   }
   
   // 语义检索
   @Component
   public class SemanticSearchStrategy implements RetrievalStrategy {
       // 余弦相似度检索
   }
   
   // 混合检索（可配置权重）
   @Component
   public class HybridSearchStrategy implements RetrievalStrategy {
       // α × vector_similarity + (1-α) × bm25_score
       // α可通过配置调整
   }
   
   // 检索配置
   @Data
   @ConfigurationProperties(prefix = "rag.retrieval")
   public class RetrievalConfig {
       private String strategy = "hybrid";  // semantic / hybrid / keyword
       private double hybridAlpha = 0.7;    // 语义权重 (0-1)
       private int topK = 5;               // 返回数量
       private double minScore = 0.5;      // 最低相似度阈值
   }
   ```

3. **答案生成（支持多种LLM）**
   ```java
   // LLM服务接口（适配器模式）
   public interface LLMService {
       String generate(String prompt);
       Flux<String> generateStream(String prompt);  // 流式输出
       LLMProvider getProviderType();
   }
   
   // 本地LLM实现（Ollama/vLLM）
   @Component
   public class LocalLLMService implements LLMService {
       // 调用本地部署的LLM API
   }
   
   // Spring AI配置示例：
   // spring.ai.ollama.base-url=http://localhost:11434
   // spring.ai.ollama.chat.model=llama3
   ```

4. **对话管理**
   - 多轮对话上下文保持
   - 会话历史管理（Redis缓存）
   - 引用来源追踪

#### 核心类设计:
```java
// RAG编排器
@Service
@Slf4j
public class RAGOrchestrator {
    private final RetrievalService retrievalService;
    private final LLMService llmService;
    private final EmbeddingService embeddingService;
    
    public QueryResponse query(String userQuery, String userId, String conversationId) {
        // 1. 嵌入查询
        float[] queryVector = embeddingService.embedQuery(userQuery);
        
        // 2. 检索相关文档
        List<检索结果> documents = retrievalService.search(queryVector, topK);
        
        // 3. 构建上下文
        String context = buildContext(documents);
        
        // 4. 生成回答
        String answer = llmService.generate(buildPrompt(userQuery, context));
        
        return QueryResponse.builder()
            .answer(answer)
            .sources(documents)
            .build();
    }
}

// 检索服务
@Service
public class RetrievalService {
    private final QdrantClient qdrantClient;
    private final RetrievalStrategy retrievalStrategy;
    
    public List<检索结果> search(float[] queryVector, int topK);
}
```

### 3.3 权限控制模块（RBAC）

#### 角色定义:
1. **超级管理员 (super_admin)**
   - 系统全局配置
   - 用户管理
   - 所有文档访问

2. **管理员 (admin)**
   - 知识库管理
   - 普通用户管理
   - 文档上传与审核

3. **普通用户 (user)**
   - 查看已授权文档
   - 发起问答查询
   - 查看自己的对话历史

4. **访客 (guest)**
   - 公开文档查看
   - 基础搜索

#### 功能特性:
1. **用户认证**
   - 用户注册与登录
   - JWT Token认证
   - 会话管理

2. **角色管理**
   - 角色创建与编辑
   - 角色权限分配
   - 角色继承关系

3. **资源权限**
   - 文档级别权限控制
   - 知识库访问控制
   - API接口权限

4. **审计日志**
   - 登录日志记录
   - 操作审计追踪
   - 异常行为检测

#### 核心类设计:
```java
// 用户模型
@Entity
@Table(name = "users")
public class User {
    @Id
    private UUID id;
    private String username;
    private String email;
    private String passwordHash;
    
    @Enumerated(EnumType.STRING)
    private UserRole role;
    
    private Boolean isActive;
    private LocalDateTime createdAt;
}

// 权限检查注解
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {
    String resource();
    String action();
}

// 权限检查拦截器
@Component
public class PermissionInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, 
                             HttpServletResponse response, 
                             Object handler) {
        // 权限验证逻辑
    }
}

// RBAC服务
@Service
public class RBACService {
    public boolean checkPermission(UUID userId, String resource, String action);
    public void assignRole(UUID userId, UserRole role);
    public List<Permission> getRolePermissions(UserRole role);
}
```

---

## 四、数据模型设计

### 4.1 关系数据库模型（PostgreSQL）

```sql
-- 用户表
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'user',
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 文档表
CREATE TABLE documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(255) NOT NULL,
    file_path VARCHAR(500) NOT NULL,  -- 本地存储路径
    file_type VARCHAR(20),
    file_hash VARCHAR(64),
    file_size BIGINT,
    status VARCHAR(20) DEFAULT 'pending',
    uploaded_by UUID REFERENCES users(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

-- 文档块表
CREATE TABLE document_chunks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID REFERENCES documents(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    chunk_index INTEGER,
    vector_id VARCHAR(100),
    metadata JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 权限表
CREATE TABLE permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(50) UNIQUE NOT NULL,
    resource VARCHAR(50) NOT NULL,
    action VARCHAR(20) NOT NULL
);

-- 角色权限表
CREATE TABLE role_permissions (
    role VARCHAR(20) NOT NULL,
    permission_id UUID REFERENCES permissions(id),
    PRIMARY KEY (role, permission_id)
);

-- 文档访问控制表
CREATE TABLE document_access (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID REFERENCES documents(id) ON DELETE CASCADE,
    role VARCHAR(20),
    user_id UUID REFERENCES users(id),
    access_level VARCHAR(20) DEFAULT 'read',
    UNIQUE(document_id, user_id)
);

-- 会话历史表
CREATE TABLE conversations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id),
    title VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 消息表
CREATE TABLE messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID REFERENCES conversations(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL,  -- user / assistant / system
    content TEXT NOT NULL,
    metadata JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 审计日志表
CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id),
    action VARCHAR(50) NOT NULL,
    resource_type VARCHAR(50),
    resource_id UUID,
    details JSONB,
    ip_address VARCHAR(45),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 索引
CREATE INDEX idx_documents_uploaded_by ON documents(uploaded_by);
CREATE INDEX idx_documents_status ON documents(status);
CREATE INDEX idx_document_chunks_document_id ON document_chunks(document_id);
CREATE INDEX idx_messages_conversation_id ON messages(conversation_id);
CREATE INDEX idx_audit_logs_user_id ON audit_logs(user_id);
CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at);
```

### 4.2 向量数据库设计（Qdrant）

```yaml
# Qdrant Collection 配置
collection_name: "document_embeddings"

# 向量配置 - BGE-large-zh-v1.5 为1024维
vector_size: 1024
distance: "Cosine"  # 余弦相似度

# 存储配置
storage:
  on_disk: true  # 10GB数据建议开启
  mmap_threshold_kb: 1000

# 索引配置
hnsw:
  m: 16          # 连接数
  ef_construct: 128  # 构建时搜索精度
```

```java
// Collection创建代码
@Configuration
public class QdrantConfig {
    @Bean
    public QdrantClient qdrantClient() {
        return QdrantClient.builder()
            .withHost(qdrantHost)
            .withPort(qdrantPort)
            .build();
    }
}

// 向量payload结构
public class VectorPayload {
    private UUID documentId;
    private UUID chunkId;
    private String content;
    private Integer chunkIndex;
    private String title;
    private String source;
    private LocalDateTime createdAt;
    private String accessLevel;
    private List<String> tags;
}
```

---

## 五、API接口设计

### 5.1 认证接口
| 方法 | 路径 | 描述 |
|------|------|------|
| POST | /api/auth/register | 用户注册 |
| POST | /api/auth/login | 用户登录 |
| POST | /api/auth/logout | 用户登出 |
| POST | /api/auth/refresh | 刷新Token |

### 5.2 文档管理接口
| 方法 | 路径 | 描述 |
|------|------|------|
| POST | /api/documents/upload | 上传文档（支持批量） |
| GET | /api/documents | 获取文档列表 |
| GET | /api/documents/{id} | 获取文档详情 |
| DELETE | /api/documents/{id} | 删除文档 |
| POST | /api/documents/{id}/process | 处理文档（提取、嵌入） |
| GET | /api/documents/{id}/chunks | 获取文档块 |
| POST | /api/documents/{id}/retry | 重试失败的处理任务 |

### 5.3 问答接口
| 方法 | 路径 | 描述 |
|------|------|------|
| POST | /api/query | 发起问答（流式响应） |
| GET | /api/conversations | 获取会话列表 |
| GET | /api/conversations/{id}/messages | 获取会话消息 |
| DELETE | /api/conversations/{id} | 删除会话 |
| POST | /api/conversations/{id}/clear | 清空会话历史 |

### 5.4 管理接口
| 方法 | 路径 | 描述 |
|------|------|------|
| GET | /api/admin/users | 用户列表 |
| POST | /api/admin/users/{id}/role | 分配用户角色 |
| PUT | /api/admin/users/{id}/status | 启用/禁用用户 |
| GET | /api/admin/documents | 所有文档（管理员） |
| PUT | /api/admin/documents/{id}/status | 更新文档状态 |
| GET | /api/admin/permissions | 权限配置 |
| GET | /api/admin/audit-logs | 审计日志 |
| GET | /api/admin/stats | 系统统计 |

### 5.5 系统配置接口
| 方法 | 路径 | 描述 |
|------|------|------|
| GET | /api/config/chunking | 获取分块配置 |
| PUT | /api/config/chunking | 更新分块配置 |
| GET | /api/config/retrieval | 获取检索配置 |
| PUT | /api/config/retrieval | 更新检索配置 |
| GET | /api/config/llm | 获取LLM配置 |
| PUT | /api/config/llm | 更新LLM配置 |
| GET | /api/config/embedding | 获取Embedding配置 |
| PUT | /api/config/embedding | 更新Embedding配置 |
| POST | /api/config/test-embedding | 测试嵌入服务 |
| POST | /api/config/test-llm | 测试LLM服务 |

---

## 六、实施步骤与时间规划

### 阶段一：项目初始化（第1-2天）
1. **创建Spring Boot项目结构**
   ```
   rag-knowledge-base/
   ├── backend/                    # Spring Boot后端
   │   └── src/main/java/com/rag/
   │       ├── RAGApplication.java
   │       ├── config/           # 配置类
   │       ├── controller/       # API控制器
   │       ├── service/          # 业务服务
   │       ├── repository/       # 数据访问
   │       ├── model/           # 实体类
   │       ├── dto/             # 数据传输对象
   │       ├── security/        # 安全相关
   │       └── rag/             # RAG核心模块
   │           ├── processor/   # 文档处理器
   │           ├── chunker/     # 分块器
   │           ├── embedding/   # 嵌入服务客户端
   │           ├── retrieval/   # 检索服务
   │           └── llm/         # LLM服务
   ├── embedding-service/         # Embedding服务
   │   ├── main.py
   │   ├── model/
   │   └── api/
   ├── docker-compose.yml
   └── documents/                 # 文档存储目录
   ```

2. **配置开发环境**
   - JDK 17+
   - Maven/Gradle
   - Docker + Docker Compose
   - Python 3.10+（Embedding服务）

3. **搭建基础框架**
   - Spring Boot + Spring Security + JWT
   - Spring Data JPA + PostgreSQL
   - SpringDoc OpenAPI文档

4. **配置数据库连接**
   - PostgreSQL连接
   - Redis连接
   - Qdrant连接

### 阶段二：Embedding服务部署（第2-3天）
1. **部署Embedding模型服务**
   - 安装Python依赖（sentence-transformers, fastapi, uvicorn）
   - 下载BGE-large-zh-v1.5模型
   - 启动Embedding HTTP服务
   - 配置GPU支持（可选）

2. **测试Embedding服务**
   - 单条嵌入测试
   - 批量嵌入测试
   - 性能基准测试

3. **集成Embedding服务**
   - Java客户端封装
   - 服务健康检查

### 阶段三：权限控制模块（第4-6天）
1. **实现用户认证（JWT）**
   - 用户注册/登录接口
   - Token生成与验证
   - Refresh Token机制

2. **实现RBAC权限模型**
   - 角色与权限定义
   - 权限拦截器
   - @RequirePermission注解

3. **实现权限中间件**
   - 请求拦截
   - 资源权限校验

4. **实现审计日志**
   - 操作日志记录
   - 日志查询接口

### 阶段四：文档处理模块（第7-11天）
1. **实现文档上传接口**
   - 文件上传控制器
   - 本地存储管理
   - 文件类型校验

2. **实现PDF解析器**
   - Apache PDFBox文本提取
   - pdfplumber表格提取

3. **实现Word解析器**
   - Apache POI文档解析

4. **实现文本清洗器**
   - 编码检测与转换
   - 噪声过滤

5. **实现文档分块器**
   - 固定长度分块
   - 语义分块
   - 可配置策略切换

6. **实现文档嵌入流程**
   - 调用Embedding服务
   - 批量嵌入优化
   - Qdrant向量存储

### 阶段五：RAG引擎模块（第12-16天）
1. **实现向量检索服务**
   - Qdrant客户端封装
   - 语义检索实现
   - 混合检索实现

2. **实现LLM生成服务**
   - 本地LLM集成（Ollama/vLLM）
   - Spring AI适配
   - 流式输出支持

3. **实现RAG编排器**
   - 检索+生成流程
   - 上下文构建
   - 引用来源追踪

4. **实现对话管理**
   - 会话创建与管理
   - 历史消息存储
   - Redis缓存

### 阶段六：前端开发（第17-21天）
（前端技术栈待定，此阶段可与后端并行开发）

1. **实现文档管理界面**
   - 文档列表
   - 上传功能
   - 状态展示

2. **实现问答界面**
   - 对话界面
   - 流式响应展示
   - 引用来源展示

3. **实现用户管理界面**
   - 用户列表
   - 角色分配

4. **实现配置管理界面**
   - 分块参数配置
   - 检索策略配置
   - Embedding/LLM配置

### 阶段七：系统集成与测试（第22-26天）
1. **模块集成测试**
   - API集成测试
   - 数据库操作测试
   - 端到端流程测试

2. **性能优化**
   - 向量检索性能优化
   - 批量处理优化
   - 缓存策略优化

3. **安全漏洞修复**
   - 渗透测试
   - 安全加固

4. **部署文档编写**
   - 部署手册
   - 运维指南

### 阶段八：上线与迭代（第27-30天）
1. **生产环境部署**
   - Docker Compose编排
   - 环境配置
   - 数据迁移

2. **用户培训**
   - 操作手册
   - 培训视频

3. **收集反馈**
   - 问题记录
   - 需求收集

4. **功能迭代**
   - Bug修复
   - 功能优化

---

## 七、扩展性设计

### 7.1 插件化架构

```java
// 文档处理器插件注册
@Configuration
public class DocumentProcessorConfig {
    private final List<DocumentProcessor> processors;
    
    @Bean
    public DocumentProcessorRegistry processorRegistry() {
        return new DocumentProcessorRegistry(processors);
    }
}

// 处理器注册表
public class DocumentProcessorRegistry {
    private final Map<String, DocumentProcessor> processors = new HashMap<>();
    
    public void register(DocumentProcessor processor) {
        for (String type : processor.supportedTypes()) {
            processors.put(type.toLowerCase(), processor);
        }
    }
    
    public DocumentProcessor get(String fileType) {
        return processors.get(fileType.toLowerCase());
    }
}

// Embedding服务工厂
public interface EmbeddingServiceFactory {
    String getType();
    EmbeddingService create(EmbeddingProperties properties);
}

// 自动注册所有实现
@Configuration
public class EmbeddingAutoConfiguration {
    @Autowired
    private List<EmbeddingServiceFactory> factories;
    
    @Bean
    public EmbeddingServiceProvider embeddingServiceProvider() {
        return new EmbeddingServiceProvider(factories);
    }
}
```

### 7.2 配置驱动

```yaml
# application.yml

# 系统配置
spring:
  application:
    name: rag-knowledge-base

# 数据库配置
  datasource:
    url: jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect

# Redis配置
  data:
    redis:
      host: ${REDIS_HOST}
      port: ${REDIS_PORT}

# RAG配置（核心可配置项）
rag:
  # 文档处理配置
  processing:
    upload-dir: ${UPLOAD_DIR:/data/documents}
    max-file-size: ${MAX_FILE_SIZE:104857600}  # 100MB
    allowed-types:
      - pdf
      - docx
      - md
      - txt
  
  # 分块配置（可动态调整）
  chunking:
    strategy: ${CHUNK_STRATEGY:fixed_size}
    chunk-size: ${CHUNK_SIZE:512}
    overlap: ${CHUNK_OVERLAP:64}
    min-chunk-size: ${MIN_CHUNK_SIZE:50}
    preserve-structure: ${PRESERVE_STRUCTURE:true}
  
  # 检索配置（可动态调整）
  retrieval:
    strategy: ${RETRIEVAL_STRATEGY:hybrid}
    hybrid-alpha: ${HYBRID_ALPHA:0.7}
    top-k: ${RETRIEVAL_TOP_K:5}
    min-score: ${RETRIEVAL_MIN_SCORE:0.5}
  
  # 向量数据库配置
  vector:
    host: ${QDRANT_HOST:localhost}
    port: ${QDRANT_PORT:6333}
    collection: ${QDRANT_COLLECTION:document_embeddings}
    vector-size: ${VECTOR_SIZE:1024}
  
  # LLM配置（支持多种provider）
  llm:
    provider: ${LLM_PROVIDER:ollama}
    
    # Ollama配置（本地部署）
    ollama:
      base-url: ${OLLAMA_BASE_URL:http://localhost:11434}
      model: ${OLLAMA_MODEL:llama3}
      temperature: ${LLM_TEMPERATURE:0.7}
    
    # vLLM配置（备选）
    vllm:
      base-url: ${VLLM_BASE_URL:http://localhost:8000}
      model: ${VLLM_MODEL:llama3}
    
    # OpenAI配置（云端备选）
    openai:
      api-key: ${OPENAI_API_KEY:}
      model: ${OPENAI_MODEL:gpt-4}
  
  # Embedding服务配置（本地部署）
  embedding:
    # Embedding服务地址（独立部署）
    service-url: ${EMBEDDING_SERVICE_URL:http://localhost:8081}
    
    # 嵌入模型配置
    model:
      name: ${EMBEDDING_MODEL_NAME:bge-large-zh-v1.5}
      dimension: ${EMBEDDING_DIMENSION:1024}
      max-seq-length: ${EMBEDDING_MAX_SEQ_LENGTH:512}
      normalize: ${EMBEDDING_NORMALIZE:true}
    
    # 批量处理配置
    batch:
      size: ${EMBEDDING_BATCH_SIZE:32}
      timeout-seconds: ${EMBEDDING_TIMEOUT:300}

# JWT配置
jwt:
  secret: ${JWT_SECRET:your-256-bit-secret}
  access-token-expiration: ${ACCESS_TOKEN_EXPIRY:1800}      # 30分钟
  refresh-token-expiration: ${REFRESH_TOKEN_EXPIRY:604800}    # 7天

# Celery配置
celery:
  broker-url: ${CELERY_BROKER_URL:redis://localhost:6379/0}
  result-backend: ${CELERY_RESULT_URL:redis://localhost:6379/1}

# 日志配置
logging:
  level:
    com.rag: DEBUG
    org.springframework.security: INFO
```

### 7.3 缓存策略

| 缓存内容 | 存储位置 | TTL | 更新策略 |
|----------|----------|-----|----------|
| 用户会话 | Redis | 7天 | 活跃延长期 |
| 对话历史 | Redis | 30天 | 手动清除 |
| 热门检索结果 | Redis | 1小时 | LRU淘汰 |
| 文档嵌入 | Redis + Qdrant | 永久 | 文档更新时清除 |
| Embedding服务健康状态 | Redis | 1分钟 | 实时检测 |
| 配置快照 | Redis | 1小时 | 配置变更时更新 |

---

## 八、安全考虑

### 8.1 数据安全
- **本地存储**：所有文档存储在客户内网服务器
- **敏感数据加密**：使用AES-256加密敏感配置
- **数据备份**：本地定时备份，不涉及云端传输
- **Embedding模型本地化**：向量计算不离开内网

### 8.2 访问安全
- **JWT Token**：
  - Access Token: 30分钟有效期
  - Refresh Token: 7天有效期
  - Token黑名单机制
- **CSRF防护**：Spring Security自动处理
- **SQL注入防护**：JPA参数化查询
- **XSS防护**：输入校验 + 输出编码
- **API限流**：防止恶意请求
- **Embedding服务内网隔离**：不对外网开放

### 8.3 内容安全
- **敏感词过滤**：可配置敏感词库
- **生成内容审核**：可选的内容审核服务
- **引用来源验证**：确保回答基于文档

---

## 九、测试策略

### 9.1 单元测试
- 各模块核心函数测试
- 覆盖率目标: >80%

### 9.2 集成测试
- API接口测试（MockMvc）
- 数据库操作测试
- Qdrant集成测试
- Embedding服务集成测试

### 9.3 性能测试
- 并发用户测试（JMeter）
- 大文档处理测试
- Embedding性能基准测试（吞吐量、延迟）
- 检索性能基准测试

### 9.4 安全测试
- 权限绕过测试
- 认证机制测试
- Embedding服务访问控制测试
- 渗透测试

---

## 十、环境变量清单

```bash
# ============================================
# 数据库配置
# ============================================
DB_HOST=localhost
DB_PORT=5432
DB_NAME=rag_knowledge_base
DB_USERNAME=postgres
DB_PASSWORD=your_password

# ============================================
# Redis配置
# ============================================
REDIS_HOST=localhost
REDIS_PORT=6379

# ============================================
# Qdrant向量数据库配置
# ============================================
QDRANT_HOST=localhost
QDRANT_PORT=6333
QDRANT_COLLECTION=document_embeddings

# ============================================
# Embedding模型配置（本地部署）
# ============================================
EMBEDDING_SERVICE_URL=http://localhost:8081
EMBEDDING_MODEL_NAME=bge-large-zh-v1.5
EMBEDDING_DIMENSION=1024
EMBEDDING_MAX_SEQ_LENGTH=512
EMBEDDING_NORMALIZE=true
EMBEDDING_BATCH_SIZE=32
EMBEDDING_TIMEOUT=300

# ============================================
# LLM配置（本地部署）
# ============================================
LLM_PROVIDER=ollama
OLLAMA_BASE_URL=http://localhost:11434
OLLAMA_MODEL=llama3
LLM_TEMPERATURE=0.7

# 备选：vLLM配置
# VLLM_BASE_URL=http://localhost:8000
# VLLM_MODEL=llama3

# ============================================
# RAG参数配置
# ============================================
CHUNK_STRATEGY=fixed_size
CHUNK_SIZE=512
CHUNK_OVERLAP=64
MIN_CHUNK_SIZE=50
RETRIEVAL_STRATEGY=hybrid
HYBRID_ALPHA=0.7
RETRIEVAL_TOP_K=5
RETRIEVAL_MIN_SCORE=0.5

# ============================================
# JWT配置
# ============================================
JWT_SECRET=your-256-bit-secret-key-here
ACCESS_TOKEN_EXPIRY=1800
REFRESH_TOKEN_EXPIRY=604800

# ============================================
# 文件存储配置
# ============================================
UPLOAD_DIR=/data/documents
MAX_FILE_SIZE=104857600

# ============================================
# Celery配置
# ============================================
CELERY_BROKER_URL=redis://localhost:6379/0
CELERY_RESULT_URL=redis://localhost:6379/1
```

---

## 十一、Docker Compose部署

```yaml
version: '3.8'

services:
  # ============================================
  # 后端API服务
  # ============================================
  rag-api:
    build: ./backend
    ports:
      - "8080:8080"
    environment:
      - DB_HOST=postgres
      - DB_PORT=5432
      - DB_NAME=rag_knowledge_base
      - DB_USERNAME=postgres
      - DB_PASSWORD=${DB_PASSWORD}
      - REDIS_HOST=redis
      - REDIS_PORT=6379
      - QDRANT_HOST=qdrant
      - QDRANT_PORT=6333
      - EMBEDDING_SERVICE_URL=http://embedding-service:8081
      - OLLAMA_BASE_URL=http://ollama:11434
    depends_on:
      - postgres
      - redis
      - qdrant
      - embedding-service
      - ollama
    volumes:
      - ./documents:/data/documents
    networks:
      - rag-network
    restart: unless-stopped

  # ============================================
  # Embedding模型服务
  # ============================================
  embedding-service:
    build: ./embedding-service
    ports:
      - "8081:8081"
    environment:
      - MODEL_NAME=BAAI/bge-large-zh-v1.5
      - DEVICE=cuda  # 或 cpu
      - MAX_BATCH_SIZE=32
      - MAX_SEQ_LENGTH=512
    volumes:
      - embedding-models:/app/models
    deploy:
      resources:
        reservations:
          devices:
            - driver: nvidia
              count: all
              capabilities: [gpu]
    networks:
      - rag-network
    restart: unless-stopped

  # ============================================
  # PostgreSQL数据库
  # ============================================
  postgres:
    image: postgres:15
    ports:
      - "5432:5432"
    environment:
      POSTGRES_DB: rag_knowledge_base
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - postgres-data:/var/lib/postgresql/data
    networks:
      - rag-network
    restart: unless-stopped

  # ============================================
  # Redis缓存
  # ============================================
  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    networks:
      - rag-network
    restart: unless-stopped

  # ============================================
  # Qdrant向量数据库
  # ============================================
  qdrant:
    image: qdrant/qdrant
    ports:
      - "6333:6333"
      - "6334:6334"
    volumes:
      - qdrant-data:/qdrant/storage
    networks:
      - rag-network
    restart: unless-stopped

  # ============================================
  # Ollama LLM服务
  # ============================================
  ollama:
    image: ollama/ollama
    ports:
      - "11434:11434"
    volumes:
      - ollama-data:/root/.ollama
    deploy:
      resources:
        reservations:
          devices:
            - driver: nvidia
              count: all
              capabilities: [gpu]
    networks:
      - rag-network
    restart: unless-stopped

# ============================================
# 存储卷
# ============================================
volumes:
  postgres-data:
  qdrant-data:
  ollama-data:
  embedding-models:

# ============================================
# 网络
# ============================================
networks:
  rag-network:
    driver: bridge
```

### Embedding服务Dockerfile

```dockerfile
# embedding-service/Dockerfile
FROM python:3.10-slim

WORKDIR /app

# 安装系统依赖
RUN apt-get update && apt-get install -y \
    build-essential \
    && rm -rf /var/lib/apt/lists/*

# 复制依赖文件
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# 复制应用代码
COPY . .

# 预下载模型（可选，在构建时下载）
# ENV MODEL_NAME=BAAI/bge-large-zh-v1.5
# RUN python -c "from sentence_transformers import SentenceTransformer; SentenceTransformer('${MODEL_NAME}')"

# 启动服务
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8081"]
```

### Embedding服务依赖

```txt
# embedding-service/requirements.txt
fastapi==0.109.0
uvicorn[standard]==0.27.0
sentence-transformers==2.3.1
torch==2.1.2
numpy==1.26.3
pydantic==2.5.3
python-multipart==0.0.6
```
