# RAG知识库系统详细设计文档（LLD）

## 第一部分：数据库设计和API规范

> **文档类型**: 低层设计（LLD）
> **编制日期**: 2026-03-25
> **项目**: RAG知识库系统
> **参考文档**: RAG知识库系统完整HLD文档_V3.0

---

## 一、数据库设计

## 1.1 数据库选型

```yaml
主数据库: PostgreSQL 15+
向量数据库: Qdrant
缓存: Redis
对象存储: MinIO
配置中心: etcd
```

## 1.2 数据一致性保障

### 1.2.1 事务管理策略

```java
// 分布式事务配置
@EnableTransactionManagement
@Configuration
public class TransactionConfig {
    
    @Bean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager tm) {
        TransactionTemplate template = new TransactionTemplate(tm);
        template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        return template;
    }
}

// 事务边界定义
@Service
@RequiredArgsConstructor
public class DocumentService {
    
    private final DocumentRepository documentRepository;
    private final VectorStoreService vectorStoreService;
    private final AuditService auditService;
    
    @Transactional(rollbackFor = Exception.class)
    public DocumentUploadResult uploadDocument(UploadRequest request) {
        // 1. 验证知识库存在且用户有权限
        Collection collection = validateCollectionAccess(request.getCollectionId(), request.getUserId());
        
        // 2. 保存文档元数据 (事务开始点)
        Document document = documentRepository.save(buildDocument(request, collection));
        
        // 3. 上传文件到对象存储
        String filePath = storageService.upload(request.getFile(), document.getId());
        document.setFilePath(filePath);
        documentRepository.save(document);
        
        // 4. 记录审计日志
        auditService.log(AuditAction.DOCUMENT_UPLOAD, document.getId());
        
        return DocumentUploadResult.builder()
            .documentId(document.getId())
            .filePath(filePath)
            .status("pending")
            .build();
    }
    
    @Transactional(rollbackFor = Exception.class)
    public void deleteDocument(UUID documentId, UUID userId) {
        Document document = documentRepository.findById(documentId)
            .orElseThrow(() -> new ResourceNotFoundException("文档不存在"));
        
        // 1. 验证权限
        validateDocumentOwnership(document, userId);
        
        // 2. 删除向量数据 (与文档删除在同一事务)
        vectorStoreService.deleteByDocumentId(documentId);
        
        // 3. 删除文件存储
        storageService.delete(document.getFilePath());
        
        // 4. 软删除文档记录
        document.setStatus(DocumentStatus.DELETED);
        document.setDeletedAt(LocalDateTime.now());
        documentRepository.save(document);
        
        auditService.log(AuditAction.DOCUMENT_DELETE, documentId);
    }
}
```

### 1.2.2 乐观锁与悲观锁

```java
// 文档实体 - 使用版本号实现乐观锁
@Entity
@Table(name = "documents")
public class Document extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Version
    private Long version;  // 乐观锁版本号
    
    // ... 其他字段
}

// 批量操作 - 使用悲观锁
@Service
@RequiredArgsConstructor
public class DocumentBatchService {
    
    @Transactional
    public void batchUpdateStatus(List<UUID> documentIds, DocumentStatus newStatus) {
        List<Document> documents = documentRepository.findAllByIdWithLock(documentIds);
        
        documents.forEach(doc -> {
            doc.setStatus(newStatus);
            doc.setUpdatedAt(LocalDateTime.now());
        });
        
        documentRepository.saveAll(documents);
    }
}

// Repository悲观锁查询
@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Document d WHERE d.id IN :ids")
    List<Document> findAllByIdWithLock(@Param("ids") List<UUID> ids);
}
```

### 1.2.3 最终一致性保障

```java
// 消息队列补偿机制
@Service
@RequiredArgsConstructor
public class EventConsistencyService {
    
    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @Transactional
    public void publishDocumentEvent(DocumentEvent event) {
        // 1. 写入Outbox表
        OutboxEvent outbox = OutboxEvent.builder()
            .aggregateType("Document")
            .aggregateId(event.getDocumentId())
            .eventType(event.getType())
            .payload(toJson(event))
            .status(EventStatus.PENDING)
            .build();
        outboxRepository.save(outbox);
        
        // 2. 更新文档状态
        // ... 业务逻辑
    }
    
    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void processOutbox() {
        List<OutboxEvent> pending = outboxRepository.findByStatusOrderByCreatedAt(
            EventStatus.PENDING, PageRequest.of(0, 100));
        
        for (OutboxEvent event : pending) {
            try {
                kafkaTemplate.send("document-events", event.getAggregateId().toString(), event);
                event.setStatus(EventStatus.PUBLISHED);
                event.setPublishedAt(LocalDateTime.now());
            } catch (Exception e) {
                event.setStatus(EventStatus.FAILED);
                event.setErrorMessage(e.getMessage());
                event.setRetryCount(event.getRetryCount() + 1);
            }
            outboxRepository.save(event);
        }
    }
}
```

### 1.2.4 数据完整性约束

```sql
-- 文档处理状态机约束
CREATE TYPE document_processing_stage AS ENUM (
    'pending', 'uploading', 'parsing', 'chunking', 'embedding', 'storing', 'completed', 'failed'
);

CREATE TYPE document_status AS ENUM (
    'active', 'processing', 'completed', 'failed', 'archived', 'deleted'
);

-- 触发器：自动更新统计信息
CREATE OR REPLACE FUNCTION update_collection_statistics()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        UPDATE collections 
        SET statistics = jsonb_set(
            jsonb_set(statistics, '{doc_count}', (statistics->>'doc_count')::int + 1),
            '{size_bytes}', (statistics->>'size_bytes')::int + NEW.file_size
        )
        WHERE id = NEW.collection_id;
        RETURN NEW;
    ELSIF TG_OP = 'DELETE' THEN
        UPDATE collections 
        SET statistics = jsonb_set(
            jsonb_set(statistics, '{doc_count}', (statistics->>'doc_count')::int - 1),
            '{size_bytes}', (statistics->>'size_bytes')::int - OLD.file_size
        )
        WHERE id = OLD.collection_id;
        RETURN OLD;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_collection_stats
AFTER INSERT OR DELETE ON documents
FOR EACH ROW EXECUTE FUNCTION update_collection_statistics();
```

## 1.2 PostgreSQL表结构设计

### 1.2.1 用户相关表

#### 用户表 (users)

```sql
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(100),
    avatar_url VARCHAR(500),
    phone VARCHAR(20),
    status VARCHAR(20) DEFAULT 'active',
    last_login_at TIMESTAMP,
    last_login_ip VARCHAR(45),
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT users_status_check CHECK (status IN ('active', 'inactive', 'locked', 'deleted'))
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_status ON users(status);
CREATE INDEX idx_users_created_at ON users(created_at);
```

#### 角色表 (roles)

```sql
CREATE TABLE roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(50) UNIQUE NOT NULL,
    code VARCHAR(50) UNIQUE NOT NULL,
    description VARCHAR(200),
    is_system BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    sort_order INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT roles_code_check CHECK (code IN ('super_admin', 'admin', 'user', 'guest'))
);

-- 预置角色
INSERT INTO roles (name, code, description, is_system, sort_order) VALUES
('超级管理员', 'super_admin', '系统超级管理员，拥有所有权限', TRUE, 1),
('管理员', 'admin', '系统管理员', TRUE, 2),
('普通用户', 'user', '普通用户', TRUE, 3),
('访客', 'guest', '访客用户', TRUE, 99);
```

#### 用户角色关联表 (user_roles)

```sql
CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, role_id)
);

CREATE INDEX idx_user_roles_user ON user_roles(user_id);
CREATE INDEX idx_user_roles_role ON user_roles(role_id);
```

#### 权限表 (permissions)

```sql
CREATE TABLE permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    code VARCHAR(100) UNIQUE NOT NULL,
    resource VARCHAR(50) NOT NULL,
    action VARCHAR(20) NOT NULL,
    description VARCHAR(200),
    is_active BOOLEAN DEFAULT TRUE,
    parent_id UUID REFERENCES permissions(id),
    sort_order INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT permissions_resource_action_unique UNIQUE (resource, action),
    CONSTRAINT permissions_action_check CHECK (action IN ('create', 'read', 'update', 'delete', 'manage'))
);

-- 预置权限
INSERT INTO permissions (name, code, resource, action, description, sort_order) VALUES
-- 用户权限
('查看用户', 'user:read', 'user', 'read', '查看用户信息', 1),
('创建用户', 'user:create', 'user', 'create', '创建用户', 2),
('更新用户', 'user:update', 'user', 'update', '更新用户', 3),
('删除用户', 'user:delete', 'user', 'delete', '删除用户', 4),
('管理用户', 'user:manage', 'user', 'manage', '管理用户', 5),
-- 角色权限
('查看角色', 'role:read', 'role', 'read', '查看角色', 1),
('创建角色', 'role:create', 'role', 'create', '创建角色', 2),
('更新角色', 'role:update', 'role', 'update', '更新角色', 3),
('删除角色', 'role:delete', 'role', 'delete', '删除角色', 4),
('管理角色', 'role:manage', 'role', 'manage', '管理角色', 5),
-- 文档权限
('上传文档', 'document:create', 'document', 'create', '上传文档', 1),
('查看文档', 'document:read', 'document', 'read', '查看文档', 2),
('更新文档', 'document:update', 'document', 'update', '更新文档', 3),
('删除文档', 'document:delete', 'document', 'delete', '删除文档', 4),
('管理文档', 'document:manage', 'document', 'manage', '管理文档', 5),
-- 知识库权限
('查看知识库', 'collection:read', 'collection', 'read', '查看知识库', 1),
('创建知识库', 'collection:create', 'collection', 'create', '创建知识库', 2),
('更新知识库', 'collection:update', 'collection', 'update', '更新知识库', 3),
('删除知识库', 'collection:delete', 'collection', 'delete', '删除知识库', 4),
('管理知识库', 'collection:manage', 'collection', 'manage', '管理知识库', 5);
```

#### 角色权限关联表 (role_permissions)

```sql
CREATE TABLE role_permissions (
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (role_id, permission_id)
);
```

### 1.2.2 知识库相关表

#### 知识库表 (collections)

```sql
CREATE TABLE collections (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    description TEXT,
    owner_id UUID NOT NULL REFERENCES users(id),
    is_public BOOLEAN DEFAULT FALSE,
    status VARCHAR(20) DEFAULT 'active',
    config JSONB DEFAULT '{}',
    statistics JSONB DEFAULT '{"doc_count": 0, "chunk_count": 0, "size_bytes": 0}',
    tags TEXT[],
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP
);

CREATE INDEX idx_collections_owner ON collections(owner_id);
CREATE INDEX idx_collections_status ON collections(status);
CREATE INDEX idx_collections_public ON collections(is_public);
CREATE INDEX idx_collections_created ON collections(created_at);
```

#### 文档表 (documents)

```sql
CREATE TABLE documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    collection_id UUID NOT NULL REFERENCES collections(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    file_path VARCHAR(500),
    file_type VARCHAR(20),
    file_hash VARCHAR(64),
    file_size BIGINT,
    status VARCHAR(20) DEFAULT 'pending',
    processing_status JSONB DEFAULT '{"stage": "pending", "progress": 0, "message": ""}',
    metadata JSONB DEFAULT '{}',
    tags TEXT[],
    upload_by UUID REFERENCES users(id),
    reviewed_by UUID REFERENCES users(id),
    reviewed_at TIMESTAMP,
    reviewed_status VARCHAR(20) DEFAULT 'pending',
    processed_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT documents_status_check CHECK (status IN ('pending', 'processing', 'completed', 'failed', 'archived')),
    CONSTRAINT documents_file_type_check CHECK (file_type IN ('pdf', 'docx', 'doc', 'txt', 'md', 'html', 'csv', 'xlsx', 'pptx'))
);

CREATE INDEX idx_documents_collection ON documents(collection_id);
CREATE INDEX idx_documents_status ON documents(status);
CREATE INDEX idx_documents_upload_by ON documents(upload_by);
CREATE INDEX idx_documents_file_type ON documents(file_type);
CREATE INDEX idx_documents_created_at ON documents(created_at);
CREATE INDEX idx_documents_hash ON documents(file_hash);
```

#### 文档块表 (document_chunks)

```sql
CREATE TABLE document_chunks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    chunk_index INTEGER NOT NULL,
    chunk_size INTEGER,
    vector_id VARCHAR(100),
    parent_chunk_id UUID REFERENCES document_chunks(id),
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_chunks_document ON document_chunks(document_id);
CREATE INDEX idx_chunks_vector_id ON document_chunks(vector_id);
CREATE INDEX idx_chunks_index ON document_chunks(chunk_index);
```

### 1.2.3 对话相关表

#### 对话表 (conversations)

```sql
CREATE TABLE conversations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    collection_id UUID REFERENCES collections(id) ON DELETE SET NULL,
    title VARCHAR(200),
    status VARCHAR(20) DEFAULT 'active',
    settings JSONB DEFAULT '{"temperature": 0.7, "max_tokens": 2048}',
    last_message_at TIMESTAMP,
    message_count INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP
);

CREATE INDEX idx_conversations_user ON conversations(user_id);
CREATE INDEX idx_conversations_collection ON conversations(collection_id);
CREATE INDEX idx_conversations_status ON conversations(status);
CREATE INDEX idx_conversations_last_message ON conversations(last_message_at);
```

#### 消息表 (messages)

```sql
CREATE TABLE messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    metadata JSONB DEFAULT '{}',
    usage JSONB,
    latency_ms INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT messages_role_check CHECK (role IN ('user', 'assistant', 'system'))
);

CREATE INDEX idx_messages_conversation ON messages(conversation_id);
CREATE INDEX idx_messages_role ON messages(role);
CREATE INDEX idx_messages_created_at ON messages(created_at);

-- 消息使用量统计JSONB结构示例
-- usage: {"prompt_tokens": 100, "completion_tokens": 150, "total_tokens": 250}
```

### 1.2.4 审计日志表

```sql
CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id),
    username VARCHAR(50),
    action VARCHAR(100) NOT NULL,
    resource VARCHAR(100) NOT NULL,
    resource_id UUID,
    method VARCHAR(10) NOT NULL,
    path VARCHAR(255) NOT NULL,
    params JSONB,
    request_body JSONB,
    response_body JSONB,
    status_code INTEGER,
    ip_address VARCHAR(45),
    user_agent TEXT,
    error_message TEXT,
    duration_ms INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_user ON audit_logs(user_id);
CREATE INDEX idx_audit_action ON audit_logs(action);
CREATE INDEX idx_audit_resource ON audit_logs(resource);
CREATE INDEX idx_audit_created_at ON audit_logs(created_at);
```

### 1.2.5 系统配置表

```sql
CREATE TABLE system_configs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    config_key VARCHAR(100) UNIQUE NOT NULL,
    config_value TEXT,
    config_type VARCHAR(20) DEFAULT 'string',
    description VARCHAR(200),
    config_group VARCHAR(50),
    is_encrypted BOOLEAN DEFAULT FALSE,
    is_system BOOLEAN DEFAULT FALSE,
    updated_by UUID REFERENCES users(id),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT config_type_check CHECK (config_type IN ('string', 'number', 'boolean', 'json'))
);

-- 预置系统配置
INSERT INTO system_configs (config_key, config_value, config_type, config_group, description, is_system) VALUES
('system.max_upload_size', '104857600', 'number', 'system', '最大上传文件大小（字节）', TRUE),
('system.allowed_file_types', 'pdf,docx,doc,txt,md', 'string', 'system', '允许的文件类型', TRUE),
('rag.chunk.size', '512', 'number', 'rag', '默认分块大小', TRUE),
('rag.chunk.overlap', '64', 'number', 'rag', '分块重叠大小', TRUE),
('rag.retrieval.top_k', '5', 'number', 'rag', '检索返回数量', TRUE),
('rag.retrieval.score_threshold', '0.5', 'number', 'rag', '检索得分阈值', TRUE),
('llm.temperature', '0.7', 'number', 'llm', 'LLM温度参数', TRUE),
('llm.max_tokens', '2048', 'number', 'llm', 'LLM最大token数', TRUE),
('llm.model', 'qwen2.5:7b', 'string', 'llm', 'LLM模型名称', TRUE);
```

---

## 1.3 向量数据库Collection设计

### Qdrant Collection: document_embeddings

```yaml
# Collection配置
collection_name: document_embeddings
vector_size: 1024
distance: Cosine

# 向量配置
vector_config:
  size: 1024
  distance: Cosine
  on_disk: true

# HNSW索引配置
hnsw_config:
  m: 16
  ef_construct: 100
  full_scan_threshold: 10000
  on_disk: true

# 优化器配置
optimizers_config:
  indexed_threshold: 20000
  memmap_threshold: 50000
  on_disk: true

# 分片配置
shard_number: 1
replication_factor: 1
write_consistency_factor: 1
```

---

# 二、API接口设计

## 2.1 RESTful API规范

### 2.1.1 统一响应格式

#### 成功响应

```json
{
  "code": 200,
  "message": "success",
  "data": {},
  "timestamp": "2026-03-25T10:00:00Z",
  "requestId": "uuid",
  "traceId": "uuid"
}
```

#### 分页响应

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "content": [],
    "page": 1,
    "size": 20,
    "totalElements": 100,
    "totalPages": 5
  },
  "timestamp": "2026-03-25T10:00:00Z",
  "requestId": "uuid"
}
```

#### 错误响应

```json
{
  "code": 400,
  "message": "业务错误描述",
  "errors": [
    {
      "field": "username",
      "message": "用户名不能为空",
      "code": "USERNAME_NOT_NULL"
    }
  ],
  "timestamp": "2026-03-25T10:00:00Z",
  "requestId": "uuid"
}
```

### 2.1.2 错误码体系

#### 错误码命名规范

```
{模块}_{层级}_{序号}
示例：AUTH_001, DOC_102, RAG_201
```

#### 模块代码对照表

| 模块代码 | 模块名称 | 说明 |
|---------|----------|------|
| AUTH | 认证授权模块 | 用户登录、注册、Token管理 |
| USER | 用户管理模块 | 用户CRUD操作 |
| DOC | 文档管理模块 | 文档上传、处理、删除 |
| COL | 知识库模块 | 知识库CRUD |
| RAG | RAG问答模块 | 检索、问答、对话 |
| SYS | 系统错误 | 系统级错误 |

#### 认证授权错误码 (AUTH)

| 错误码 | HTTP状态码 | 说明 | 处理建议 |
|--------|-----------|------|----------|
| AUTH_001 | 401 | 用户名或密码错误 | 提示用户检查凭据 |
| AUTH_002 | 401 | Token已过期 | 引导用户重新登录 |
| AUTH_003 | 401 | Token格式错误 | 提示重新获取Token |
| AUTH_004 | 403 | 账户已被锁定 | 提示联系管理员 |
| AUTH_005 | 403 | 权限不足 | 提示用户权限范围 |
| AUTH_006 | 401 | Refresh Token已过期 | 引导用户重新登录 |
| AUTH_007 | 401 | Refresh Token无效 | 引导用户重新登录 |
| AUTH_008 | 429 | 登录尝试次数过多 | 提示稍后重试 |
| AUTH_009 | 400 | 注册参数校验失败 | 显示具体校验错误 |
| AUTH_010 | 409 | 用户名已存在 | 提示更换用户名 |
| AUTH_011 | 409 | 邮箱已被注册 | 提示使用其他邮箱 |

#### 用户管理错误码 (USER)

| 错误码 | HTTP状态码 | 说明 | 处理建议 |
|--------|-----------|------|----------|
| USER_001 | 404 | 用户不存在 | 检查请求的用户ID |
| USER_002 | 400 | 用户状态不允许操作 | 提示当前用户状态 |
| USER_003 | 403 | 无法修改超级管理员 | 提示权限限制 |
| USER_004 | 409 | 用户名冲突 | 提示更换用户名 |
| USER_005 | 400 | 更新参数无效 | 显示具体错误 |

#### 文档管理错误码 (DOC)

| 错误码 | HTTP状态码 | 说明 | 处理建议 |
|--------|-----------|------|----------|
| DOC_001 | 404 | 文档不存在 | 检查文档ID |
| DOC_002 | 400 | 文件类型不支持 | 显示支持的文件类型 |
| DOC_003 | 413 | 文件大小超限 | 提示文件大小限制(100MB) |
| DOC_004 | 409 | 文件已存在(哈希冲突) | 可选择覆盖或跳过 |
| DOC_005 | 500 | 文档解析失败 | 提示检查文件格式 |
| DOC_006 | 500 | 文档处理超时 | 提示文件过大，建议拆分 |
| DOC_007 | 400 | 文档状态不允许操作 | 等待处理完成 |
| DOC_008 | 500 | 文档上传失败 | 稍后重试 |
| DOC_009 | 400 | 批量操作参数无效 | 检查批量大小 |
| DOC_010 | 500 | 文档删除失败 | 稍后重试 |

#### 知识库错误码 (COL)

| 错误码 | HTTP状态码 | 说明 | 处理建议 |
|--------|-----------|------|----------|
| COL_001 | 404 | 知识库不存在 | 检查知识库ID |
| COL_002 | 403 | 无权限访问该知识库 | 联系知识库所有者 |
| COL_003 | 409 | 知识库名称已存在 | 更换知识库名称 |
| COL_004 | 400 | 知识库配置无效 | 检查配置参数 |
| COL_005 | 500 | 知识库创建失败 | 稍后重试 |
| COL_006 | 500 | 知识库删除失败 | 确保无文档后重试 |

#### RAG问答错误码 (RAG)

| 错误码 | HTTP状态码 | 说明 | 处理建议 |
|--------|-----------|------|----------|
| RAG_001 | 404 | 知识库不存在 | 检查知识库ID |
| RAG_002 | 503 | Embedding服务不可用 | 稍后重试 |
| RAG_003 | 503 | LLM服务不可用 | 稍后重试 |
| RAG_004 | 400 | 检索结果为空 | 提示调整检索策略 |
| RAG_005 | 500 | RAG处理失败 | 查看详细错误信息 |
| RAG_006 | 400 | 问题长度超限 | 缩短问题长度(最大2000字符) |
| RAG_007 | 400 | 检索参数无效 | 检查检索配置 |
| RAG_008 | 500 | 对话保存失败 | 稍后重试 |
| RAG_009 | 400 | 对话上下文超限 | 开启新对话 |

#### 系统错误码 (SYS)

| 错误码 | HTTP状态码 | 说明 | 处理建议 |
|--------|-----------|------|----------|
| SYS_001 | 500 | 系统内部错误 | 查看系统日志 |
| SYS_002 | 503 | 服务暂时不可用 | 稍后重试 |
| SYS_003 | 429 | 请求频率超限 | 降低请求频率 |
| SYS_004 | 500 | 数据库连接失败 | 检查数据库状态 |
| SYS_005 | 500 | 缓存服务不可用 | 检查Redis状态 |
| SYS_006 | 500 | 文件存储服务不可用 | 检查MinIO状态 |
| SYS_007 | 500 | 向量数据库不可用 | 检查Qdrant状态 |

### 2.1.3 HTTP状态码

| 状态码 | 说明 | 使用场景 |
|--------|------|----------|
| 200 | OK | 成功响应 |
| 201 | Created | 资源创建成功 |
| 204 | No Content | 删除成功 |
| 400 | Bad Request | 请求参数错误 |
| 401 | Unauthorized | 未认证 |
| 403 | Forbidden | 无权限 |
| 404 | Not Found | 资源不存在 |
| 409 | Conflict | 资源冲突 |
| 422 | Unprocessable Entity | 验证失败 |
| 429 | Too Many Requests | 限流 |
| 500 | Internal Server Error | 服务器错误 |
| 503 | Service Unavailable | 服务不可用 |

### 2.1.4 认证机制详解

#### JWT Token结构

```json
{
  "header": {
    "alg": "HS256",
    "typ": "JWT"
  },
  "payload": {
    "sub": "user-uuid",
    "userId": "user-uuid",
    "username": "john",
    "roles": ["user"],
    "permissions": ["document:read", "document:create"],
    "iat": 1711344000,
    "exp": 1711347600,
    "iss": "rag-knowledge-base",
    "jti": "unique-token-id"
  }
}
```

#### Token传递方式

| 场景 | 传递方式 | 说明 |
|------|----------|------|
| HTTP Header | `Authorization: Bearer <token>` | 推荐方式，适用于API调用 |
| Cookie | `HttpOnly; Secure` | 适用于Web前端，自动发送 |
| Query Param | `?token=<token>` | 仅适用于GET请求，不推荐 |

#### Token刷新策略

```
┌─────────────────────────────────────────────────────┐
│                   Token刷新流程                       │
│                                                      │
│  1. Access Token有效期: 30分钟                        │
│  2. Refresh Token有效期: 7天                         │
│  3. Access Token过期前5分钟触发自动刷新                │
│  4. 刷新失败则引导用户重新登录                         │
│  5. Refresh Token仅限使用一次(轮换机制)               │
└─────────────────────────────────────────────────────┘
```

#### Token吊销机制

```java
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {
    
    private final RedisTemplate<String, String> redisTemplate;
    private static final String BLACKLIST_PREFIX = "token:blacklist:";
    
    public void revokeToken(String tokenId) {
        String key = BLACKLIST_PREFIX + tokenId;
        long ttl = calculateRemainingTTL(tokenId);
        redisTemplate.opsForValue().set(key, "revoked", ttl, TimeUnit.SECONDS);
    }
    
    public boolean isRevoked(String tokenId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + tokenId));
    }
    
    @Transactional
    public void logout(HttpServletRequest request) {
        String token = extractToken(request);
        String tokenId = parseTokenId(token);
        
        // 吊销Access Token
        revokeToken(tokenId);
        
        // 吊销Refresh Token
        String refreshTokenId = parseRefreshTokenId(token);
        revokeToken(refreshTokenId);
    }
}
```

## 2.2 认证接口

### 2.2.1 用户认证

#### POST /api/v1/auth/register - 用户注册

```yaml
请求参数:
{
  "username": "string (必填, 3-50字符",
  "email": "string (必填, 邮箱格式",
  "password": "string (必填, 8-20字符)",
  "displayName": "string (可选)",
  "phone": "string (可选)"
}

响应: 201 Created
{
  "id": "uuid",
  "username": "string",
  "email": "string",
  "displayName": "string",
  "createdAt": "timestamp"
}

错误码:
- 400: 参数校验失败
- 409: 用户名或邮箱已存在
```

#### POST /api/v1/auth/login - 用户登录

```yaml
请求参数:
{
  "username": "string (必填)",
  "password": "string (必填)",
  "rememberMe": "boolean (可选, 默认false)"
}

响应: 200 OK
{
  "accessToken": "string",
  "refreshToken": "string",
  "expiresIn": 1800,
  "tokenType": "Bearer",
  "user": {
    "id": "uuid",
    "username": "string",
    "email": "string",
    "roles": ["string"]
  }
}

错误码:
- 401: 用户名或密码错误
- 403: 账户已被锁定
```

#### POST /api/v1/auth/refresh - 刷新Token

```yaml
请求头: Authorization: Bearer {refreshToken}

响应: 200 OK
{
  "accessToken": "string",
  "refreshToken": "string",
  "expiresIn": 1800
}
```

#### POST /api/v1/auth/logout - 登出

```yaml
请求头: Authorization: Bearer {accessToken}

响应: 200 OK
{
  "message": "logout_success"
}
```

### 2.2.2 用户管理

#### GET /api/v1/users - 用户列表

```yaml
请求参数:
- page: int (默认1)
- size: int (默认20, 最大100)
- keyword: string (可选)
- status: string (可选)
- roleId: uuid (可选)

响应: 200 OK
{
  "content": [
    {
      "id": "uuid",
      "username": "string",
      "email": "string",
      "displayName": "string",
      "status": "string",
      "roles": ["string"],
      "lastLoginAt": "timestamp",
      "createdAt": "timestamp"
    }
  ],
  "page": 1,
  "size": 20,
  "totalElements": 100,
  "totalPages": 5
}
```

#### GET /api/v1/users/{id} - 用户详情

```yaml
响应: 200 OK
{
  "id": "uuid",
  "username": "string",
  "email": "string",
  "phone": "string",
  "displayName": "string",
  "avatar": "string",
  "status": "string",
  "roles": ["string"],
  "permissions": ["string"],
  "createdAt": "timestamp",
  "updatedAt": "timestamp",
  "lastLoginAt": "timestamp"
}

错误码:
- 404: 用户不存在
```

#### PUT /api/v1/users/{id} - 更新用户

```yaml
请求参数:
{
  "displayName": "string (可选)",
  "phone": "string (可选)",
  "status": "string (可选)",
  "roleIds": ["uuid"]
}

响应: 200 OK
{
  "id": "uuid",
  "message": "update_success"
}
```

#### DELETE /api/v1/users/{id} - 删除用户

```yaml
响应: 204 No Content

错误码:
- 404: 用户不存在
- 409: 无法删除超级管理员
```

### 2.2.3 文档管理

#### POST /api/v1/documents/upload - 上传文档

```yaml
请求: multipart/form-data
- file: binary (必填, 最大100MB)
- collectionId: uuid (必填)
- title: string (可选, 默认使用文件名)
- tags: string[] (可选)
- metadata: JSON (可选)

响应: 201 Created
{
  "id": "uuid",
  "title": "string",
  "fileType": "string",
  "fileSize": 1024000,
  "status": "pending",
  "uploadedAt": "timestamp"
}

错误码:
- 400: 文件类型不支持
- 413: 文件大小超限
```

#### GET /api/v1/documents/{id}/status - 文档处理状态

```yaml
响应: 200 OK
{
  "id": "uuid",
  "status": "processing",
  "progress": 65,
  "currentStage": "embedding",
  "stages": [
    {"name": "upload", "status": "completed", "progress": 100},
    {"name": "parse", "status": "completed", "progress": 100},
    {"name": "chunk", "status": "completed", "progress": 100},
    {"name": "embed", "status": "processing", "progress": 50}
  ],
  "estimatedTime": 30,
  "errorMessage": null
}
```

#### GET /api/v1/documents/{id}/chunks - 文档块列表

```yaml
请求参数:
- page: int (默认1)
- size: int (默认20)

响应: 200 OK
{
  "content": [
    {
      "id": "uuid",
      "content": "string",
      "chunkIndex": 1,
      "metadata": {}
    }
  ],
  "page": 1,
  "size": 20,
  "totalElements": 100
}
```

### 2.2.4 知识库管理

#### GET /api/v1/collections - 知识库列表

```yaml
响应: 200 OK
{
  "content": [
    {
      "id": "uuid",
      "name": "string",
      "description": "string",
      "documentCount": 10,
      "chunkCount": 1000,
      "isPublic": false,
      "status": "active",
      "createdAt": "timestamp"
    }
  ]
}
```

#### POST /api/v1/collections - 创建知识库

```yaml
请求参数:
{
  "name": "string (必填)",
  "description": "string (可选)",
  "isPublic": boolean (默认false),
  "tags": ["string"]
}

响应: 201 Created
{
  "id": "uuid",
  "name": "string"
}
```

### 2.2.5 RAG问答

#### POST /api/v1/rag/query - 问答查询

```yaml
请求参数:
{
  "collectionId": "uuid (必填)",
  "question": "string (必填)",
  "conversationId": "uuid (可选, 用于多轮对话)",
  "searchConfig": {
    "strategy": "hybrid",
    "topK": 5,
    "minScore": 0.5,
    "hybridAlpha": 0.7
  },
  "llmConfig": {
    "temperature": 0.7,
    "maxTokens": 2048
  }
}

响应: 200 OK
{
  "answer": "string",
  "sources": [
    {
      "documentId": "uuid",
      "documentTitle": "string",
      "chunkContent": "string",
      "score": 0.89,
      "pageNumber": 1
    }
  ],
  "conversationId": "uuid",
  "messageId": "uuid",
  "usage": {
    "promptTokens": 500,
    "completionTokens": 150,
    "totalTokens": 650
  },
  "latency": {
    "embedding": 45,
    "search": 23,
    "llm": 2680,
    "total": 2850
  }
}
```

#### POST /api/v1/rag/query/stream - 流式问答

```yaml
请求: 同 /api/v1/rag/query

响应: text/event-stream
event: token
data: {"content": "string", "type": "token"}

event: source
data: {"documentId": "uuid", "title": "string", "score": 0.89}

event: done
data: {"totalTokens": 650, "latency": 2850}

event: error
data: {"code": "string", "message": "string"}
```

---

## 三、核心类设计

### 3.1 后端项目结构

```yaml
rag-parent/
├── rag-api/                    # API层
│   ├── controller/
│   │   ├── AuthController.java
│   │   ├── UserController.java
│   │   ├── DocumentController.java
│   │   ├── CollectionController.java
│   │   └── RAGController.java
│   ├── config/
│   │   ├── SecurityConfig.java
│   │   ├── WebConfig.java
│   │   └── SwaggerConfig.java
│   └── dto/
│       ├── request/
│       └── response/
│
├── rag-service/                 # 服务层
│   ├── service/
│   │   ├── UserService.java
│   │   ├── DocumentService.java
│   │   ├── RAGService.java
│   │   └── AIObservabilityService.java
│   ├── repository/
│   └── mapper/
│
├── rag-engine/                # RAG引擎
│   ├── embedding/
│   │   ├── EmbeddingService.java
│   │   └── EmbeddingClient.java
│   ├── retrieval/
│   │   ├── RetrievalService.java
│   │   └── RetrievalStrategy.java
│   ├── llm/
│   │   ├── LLMService.java
│   │   ├── OllamaClient.java
│   │   └── VLLMClient.java
│   └── orchestrator/
│       └── RAGOrchestrator.java
│
├── rag-doc-processor/          # 文档处理
│   ├── parser/
│   │   ├── DocumentParser.java
│   │   ├── PDFParser.java
│   │   ├── WordParser.java
│   │   └── MarkdownParser.java
│   └── chunker/
│       ├── DocumentChunker.java
│       └── ChunkStrategy.java
│
├── rag-common/                # 公共模块
│   ├── config/
│   ├── exception/
│   ├── model/
│   └── util/
│
└── pom.xml
```

### 3.2 核心类定义

#### 3.2.1 实体类

```java
@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class User extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(unique = true, nullable = false, length = 50)
    private String username;
    
    @Column(unique = true, nullable = false, length = 100)
    private String email;
    
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;
    
    @Column(name = "display_name", length = 100)
    private String displayName;
    
    @Column(length = 20)
    @Enumerated(EnumType.STRING)
    private UserStatus status;
    
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "user_roles",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles;
    
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;
}
```

#### 3.2.2 服务类

```java
@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuditService auditService;
    
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        // 1. 参数校验
        validateCreateRequest(request);
        
        // 2. 检查用户名唯一性
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException("用户名已存在");
        }
        
        // 3. 创建用户
        User user = User.builder()
            .username(request.getUsername())
            .email(request.getEmail())
            .passwordHash(passwordEncoder.encode(request.getPassword()))
            .displayName(request.getDisplayName())
            .status(UserStatus.ACTIVE)
            .build();
        
        User savedUser = userRepository.save(user);
        
        // 4. 记录审计日志
        auditService.log(AuditAction.USER_CREATE, savedUser.getId());
        
        return toResponse(savedUser);
    }
    
    public Page<UserResponse> listUsers(UserQueryRequest request) {
        // 分页查询用户列表
        Specification<User> spec = buildSpecification(request);
        Page<User> page = userRepository.findAll(spec, PageRequest.of(
            request.getPage(), 
            request.getSize(),
            Sort.by(Sort.Direction.DESC, "createdAt")
        ));
        
        return page.map(this::toResponse);
    }
    
    @Transactional
    public void deleteUser(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("用户不存在"));
        
        user.setStatus(UserStatus.DELETED);
        user.setDeletedAt(LocalDateTime.now());
        userRepository.save(user);
        
        auditService.log(AuditAction.USER_DELETE, userId);
    }
}
```

#### 3.2.3 RAG编排器

```java
@Service
@Slf4j
@RequiredArgsConstructor
public class RAGOrchestrator {
    
    private final EmbeddingService embeddingService;
    private final RetrievalService retrievalService;
    private final LLMService llmService;
    private final PromptTemplateManager promptManager;
    private final TraceService traceService;
    
    public RAGResponse query(RAGRequest request) {
        String traceId = traceService.startTrace();
        
        try {
            // 1. Embedding查询
            long startTime = System.currentTimeMillis();
            float[] queryVector = embeddingService.embedQuery(request.getQuestion());
            long embeddingTime = System.currentTimeMillis() - startTime;
            
            // 2. 向量检索
            startTime = System.currentTimeMillis();
            List<SearchResult> searchResults = retrievalService.search(
                queryVector,
                request.getTopK(),
                request.getCollectionId()
            );
            long retrievalTime = System.currentTimeMillis() - startTime;
            
            // 3. 构建Prompt
            String context = buildContext(searchResults);
            String prompt = promptManager.buildPrompt(request.getQuestion(), context);
            
            // 4. LLM生成
            startTime = System.currentTimeMillis();
            String answer = llmService.generate(prompt);
            long generationTime = System.currentTimeMillis() - startTime;
            
            // 5. 构建响应
            RAGResponse response = RAGResponse.builder()
                .answer(answer)
                .sources(searchResults)
                .usage(buildUsage(prompt, answer))
                .latency(buildLatency(embeddingTime, retrievalTime, generationTime))
                .build();
            
            // 6. 记录Trace
            traceService.recordLatency(traceId, embeddingTime, retrievalTime, generationTime);
            
            return response;
            
        } catch (Exception e) {
            traceService.recordError(traceId, e);
            throw e;
        }
    }
    
    public Flux<String> queryStream(RAGRequest request) {
        // 流式响应实现
        // ...
    }
    
    private String buildContext(List<SearchResult> results) {
        // 构建上下文
    }
    
    private Usage buildUsage(String prompt, String answer) {
        // 构建使用统计
    }
    
    private Latency buildLatency(long embedding, long retrieval, long generation) {
        // 构建延迟统计
    }
}
```

### 3.3 配置类

```java
@Configuration
@ConfigurationProperties(prefix = "rag")
@Data
public class RAGProperties {
    
    private Embedding embedding = new Embedding();
    private Retrieval retrieval = new Retrieval();
    private LLM llm = new LLM();
    private Chunking chunking = new Chunking();
    
    @Data
    public static class Embedding {
        private String model = "bge-large-zh-v1.5";
        private String url = "http://localhost:8081";
        private Integer dimension = 1024;
        private Integer batchSize = 32;
    }
    
    @Data
    public static class Retrieval {
        private String strategy = "hybrid";
        private Integer topK = 5;
        private Double minScore = 0.5;
        private Double hybridAlpha = 0.7;
    }
    
    @Data
    public static class LLM {
        private String provider = "ollama";
        private String baseUrl = "http://localhost:11434";
        private String model = "qwen2.5:7b";
        private Double temperature = 0.7;
        private Integer maxTokens = 2048;
    }
}
```

---

## 四、异常处理设计

### 4.1 异常层次

```java
// 基础异常
public class BaseException extends RuntimeException {
    private String code;
    private Map<String, Object> params;
}

// 业务异常
public class BusinessException extends BaseException {
    // 业务逻辑异常
}

// 资源不存在
public class ResourceNotFoundException extends BaseException {
    // 404异常
}

// 认证异常
public class AuthenticationException extends BaseException {
    // 401异常
}

// 权限异常
public class AccessDeniedException extends BaseException {
    // 403异常
}

// 验证异常
public class ValidationException extends BaseException {
    // 参数验证异常
    private Map<String, String> errors;
}

// 限流异常
public class RateLimitException extends BaseException {
    private int limit;
    private long retryAfter;
}
```

### 4.2 全局异常处理器

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.of(e.getCode(), e.getMessage()));
    }
    
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException e) {
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse.of("NOT_FOUND", e.getMessage()));
    }
    
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse.of("FORBIDDEN", e.getMessage()));
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getFieldErrors().forEach(error -> 
            errors.put(error.getField(), error.getDefaultMessage())
        );
        
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.validation(errors));
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception e) {
        log.error("System error", e);
        
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse.of("INTERNAL_ERROR", "系统异常"));
    }
}
```

---

*第一部分完成*
