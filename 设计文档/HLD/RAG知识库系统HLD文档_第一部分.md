# RAG知识库系统高层设计文档

## 第一部分：项目概述与架构设计

> **文档版本**: V1.0  
> **编制日期**: 2026-03-25  
> **项目周期**: 约65个工作日（3个月自然月）  
> **技术栈**: Spring Boot 3.x + Spring AI + Redis + Qdrant + Ollama/vLLM + MinIO + etcd + PaddleOCR  

---

## 一、项目概述

### 1.1 项目背景

企业知识管理面临着文档分散、检索效率低、知识利用率不高等挑战。传统基于关键词的搜索已无法满足用户对语义理解和智能问答的需求。本项目旨在构建一个基于RAG（Retrieval-Augmented Generation）技术的智能知识库系统，通过自然语言处理和向量检索技术，实现文档的智能管理和智能问答。

### 1.2 项目目标

```yaml
核心目标：
1. 实现多格式文档（PDF、Word、Markdown、TXT）的自动解析和向量化存储
2. 构建基于向量检索的智能问答系统，支持自然语言查询
3. 提供完善的用户权限管理和审计日志功能
4. 确保系统安全可靠，满足企业级应用需求

量化指标：
- 文档处理准确率 > 95%
- 问答响应时间 < 10秒（首Token）
- 系统可用性 > 99%
- 支持50+并发用户
```

### 1.3 实际工作日计算

```yaml
项目周期：2026-03-25 至 2026-06-25（3个自然月）

详细工作日分布：
- 3月（剩余）：5个工作日（25-31日）
- 4月：约21个工作日（清明节1天）
- 5月：约19个工作日（劳动节1天，端午节3天）
- 6月（前25天）：约18个工作日（端午节3天）

总计：约63个工作日
建议Buffer：5-7天（应对节假日和突发事件）
可用工作日：约58-63天
```

### 1.4 项目约束

```yaml
技术约束：
- 所有数据必须存储在客户内网，不允许使用云端AI服务
- 必须支持本地部署，包括LLM、Embedding模型和向量数据库
- 需要与现有企业系统集成（LDAP/AD、单点登录等）

时间约束：
- 总工期：约63个工作日
- 必须在上線前完成UAT测试
- 预留5-7天Buffer应对风险

资源约束：
- 开发团队：5-6人（后端3人、前端1-2人、测试1人）
- GPU资源：已有CUDA环境，支持Ollama和vLLM运行
- 服务器资源：Redis、Qdrant、etcd、MinIO等已部署
```

---

## 二、技术架构设计

### 2.1 整体架构

```yaml
系统采用分层架构设计，主要包括以下层次：

┌─────────────────────────────────────────────────────────────┐
│                      客户端层                                │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐ │
│  │  Web前端    │  │  移动端H5   │  │    API调用         │ │
│  │  Vue3/React │  │   响应式    │  │   第三方集成       │ │
│  └─────────────┘  └─────────────┘  └─────────────────────┘ │
└────────────────────────────┬────────────────────────────────┘
                             │ HTTPS/REST
┌────────────────────────────▼────────────────────────────────┐
│                      网关层                                   │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │          Nginx（反向代理 + SSL终止 + 限流）               │ │
│  └─────────────────────────────────────────────────────────┘ │
└────────────────────────────┬────────────────────────────────┘
                             │
┌────────────────────────────▼────────────────────────────────┐
│                      服务层（Spring Boot）                    │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────────────────┐│
│  │  文档管理    │ │   RAG引擎   │ │      用户权限            ││
│  │  服务        │ │   服务       │ │      服务               ││
│  └─────────────┘ └─────────────┘ └─────────────────────────┘│
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────────────────┐│
│  │  对话管理    │ │   配置管理   │ │       任务调度           ││
│  │  服务        │ │   服务       │ │       服务              ││
│  └─────────────┘ └─────────────┘ └─────────────────────────┘│
└────────────────────────────┬────────────────────────────────┘
                             │
┌────────────────────────────▼────────────────────────────────┐
│                      AI服务层                                 │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────────────────┐│
│  │  Embedding  │ │    LLM      │ │        OCR              ││
│  │  服务        │ │   服务       │ │       服务              ││
│  │  (BGE模型)   │ │(Ollama/vLLM)│ │     (PaddleOCR)        ││
│  └─────────────┘ └─────────────┘ └─────────────────────────┘│
└────────────────────────────┬────────────────────────────────┘
                             │
┌────────────────────────────▼────────────────────────────────┐
│                      数据存储层                               │
│  ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌─────────────┐ │
│  │  PostgreSQL│ │   Redis   │ │   Qdrant  │ │    MinIO    │ │
│  │  (关系数据) │ │  (缓存)    │ │  (向量)   │ │  (文件存储)  │ │
│  └───────────┘ └───────────┘ └───────────┘ └─────────────┘ │
│  ┌───────────┐ ┌───────────┐                               │
│  │    etcd   │ │ 本地存储   │                               │
│  │  (配置)   │ │ (临时文件)  │                               │
│  └───────────┘ └───────────┘                               │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 技术选型说明

```yaml
后端框架：Spring Boot 3.x + Spring AI
选型理由：
- Spring AI提供统一的AI模型抽象层，支持多种LLM和Embedding服务
- 与企业Java技术栈一致，便于后续维护和扩展
- 成熟的生态体系，包括Spring Security、Spring Data等

向量数据库：Qdrant
选型理由：
- Rust实现，性能优异，内存占用低
- 支持复杂的过滤查询，权限控制友好
- 10GB规模最优，性价比最高
- 提供成熟Java客户端

Embedding模型：BGE-large-zh-v1.5
选型理由：
- 专为中文语义理解设计，MTEB中文榜单表现优异
- 开源免费，可商用
- 1024维向量，平衡精度与效率

LLM服务：Ollama（开发）+ vLLM（生产）
选型理由：
- Ollama：部署简单，适合快速验证
- vLLM：高性能推理，生产环境首选
- 支持本地部署，满足数据安全要求

文档存储：MinIO
选型理由：
- S3兼容协议，易于集成
- 支持大文件存储和分享
- 企业级特性：版本控制、生命周期管理等

配置中心：etcd
选型理由：
- 高可用配置存储
- 支持配置变更监听和热更新
- 与Kubernetes生态兼容
```

### 2.3 系统部署架构

```yaml
生产环境部署拓扑：

┌──────────────────────────────────────────────────────────────┐
│                      DMZ区（可选）                            │
│  ┌─────────────────┐                                        │
│  │   Nginx集群      │                                        │
│  │  (负载均衡)      │                                        │
│  └────────┬────────┘                                        │
└───────────┼──────────────────────────────────────────────────┘
            │
┌───────────▼──────────────────────────────────────────────────┐
│                      应用区                                   │
│  ┌────────────────┐  ┌────────────────┐                     │
│  │  应用服务器1    │  │   应用服务器2    │  ← Spring Boot    │
│  │  (4核8G)       │  │   (4核8G)       │    应用集群        │
│  └────────────────┘  └────────────────┘                     │
│                                                              │
│  ┌────────────────┐  ┌────────────────┐                     │
│  │  GPU服务器1    │  │   GPU服务器2    │  ← AI服务          │
│  │  (16GB显存)    │  │   (16GB显存)    │    集群            │
│  │  - vLLM       │  │   - vLLM       │                    │
│  │  - Embedding  │  │   - Embedding  │                    │
│  └────────────────┘  └────────────────┘                     │
└───────────┬──────────────────────────────────────────────────┘
            │
┌───────────▼──────────────────────────────────────────────────┐
│                      数据区                                   │
│  ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌────────────┐ │
│  │ PostgreSQL │ │   Redis    │ │   Qdrant   │ │   MinIO    │ │
│  │ 主从集群   │ │ 哨兵/集群  │ │  单节点    │ │   分布式   │ │
│  └────────────┘ └────────────┘ └────────────┘ └────────────┘ │
│  ┌────────────┐ ┌────────────┐                               │
│  │    etcd    │ │ 本地磁盘    │                               │
│  │   集群     │ │  (SSD)     │                               │
│  └────────────┘ └────────────┘                               │
└──────────────────────────────────────────────────────────────┘
```

---

## 三、功能架构设计

### 3.1 功能模块划分

```yaml
系统功能划分为6大模块：

┌─────────────────────────────────────────────────────────────┐
│                    功能模块架构                              │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌───────────────┐  ┌───────────────┐  ┌───────────────┐  │
│  │   用户权限     │  │   文档管理     │  │   知识库管理   │  │
│  │   模块        │  │   模块        │  │   模块        │  │
│  ├───────────────┤  ├───────────────┤  ├───────────────┤  │
│  │ • 用户注册登录 │  │ • 文档上传    │  │ • 知识库创建  │  │
│  │ • JWT认证     │  │ • 格式解析    │  │ • 知识库配置  │  │
│  │ • RBAC权限   │  │ • 文本预处理  │  │ • 知识库权限  │  │
│  │ • 审计日志   │  │ • 分块处理    │  │ • 知识库检索  │  │
│  └───────────────┘  └───────────────┘  └───────────────┘  │
│                                                             │
│  ┌───────────────┐  ┌───────────────┐  ┌───────────────┐  │
│  │   智能问答     │  │   系统管理     │  │   运维监控     │  │
│  │   模块        │  │   模块        │  │   模块        │  │
│  ├───────────────┤  ├───────────────┤  ├───────────────┤  │
│  │ • 问答对话   │  │ • 配置管理    │  │ • 健康检查    │  │
│  │ • 上下文管理 │  │ • 模型管理    │  │ • 性能监控    │  │
│  │ • 引用追踪   │  │ • 知识库统计  │  │ • 日志管理    │  │
│  │ • 历史管理   │  │ • 备份恢复    │  │ • 告警管理    │  │
│  └───────────────┘  └───────────────┘  └───────────────┘  │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 3.2 核心业务流程

#### 3.2.1 文档处理流程

```yaml
文档处理完整流程：

用户操作                    系统处理                     AI服务
    │                          │                          │
    ▼                          ▼                          ▼
┌────────┐              ┌────────────┐            ┌────────────┐
│上传文档│ ──────────▶ │文件校验    │            │            │
└────────┘              │(类型/大小) │            │            │
    │                   └────────────┘            │            │
    │                          │                  │            │
    ▼                   ┌────────────┐            │            │
┌────────┐             │存储原始文件  │ ────────▶ │   MinIO    │
│上传进度│ ◀────────── │(MinIO)     │            │            │
└────────┘             └────────────┘            └────────────┘
    │                          │                          
    ▼                   ┌────────────┐            ┌────────────┐
┌────────┐             │格式检测     │            │            │
│解析状态│ ◀────────── │(PDF/Word)  │            │            │
└────────┘             └────────────┘            │            │
    │                          │                  │            │
    ▼                   ┌────────────┐            │            │
┌────────┐             │文本提取     │            │            │
│提取进度│ ◀────────── │(Apache PDFBox│◀─────────│            │
└────────┘             │ / POI)     │            │            │
    │                   └────────────┘            │            │
    │                          │                  │            │
    ▼                   ┌────────────┐            │            │
┌────────┐             │文本清洗     │            │            │
│清洗状态│ ◀────────── │(编码/噪声)  │            │            │
└────────┘             └────────────┘            │            │
    │                          │                  │            │
    ▼                   ┌────────────┐            │            │
┌────────┐             │文档分块     │            │            │
│分块进度│ ◀────────── │(策略模式)  │            │            │
└────────┘             └────────────┘            │            │
    │                          │                  │            │
    ▼                   ┌────────────┐     ┌─────┴────────┐  │
┌────────┐             │向量化       │ ──▶ │ Embedding    │  │
│向量化  │ ◀────────── │(批量处理)   │     │ Service      │  │
└────────┘             └────────────┘     │ (BGE模型)    │  │
    │                          │          └─────────────┘  │
    ▼                   ┌────────────┐            │         │
┌────────┐             │向量存储     │ ◀────────────────────┘
│存储完成│ ◀────────── │(Qdrant)   │            │
└────────┘             └────────────┘            │
    │                          │                  │
    ▼                   ┌────────────┐            │
┌────────┐             │元数据存储   │ ────────▶ │ PostgreSQL │
│完成通知│ ◀────────── │(块信息)    │            │            │
└────────┘             └────────────┘            └────────────┘
```

#### 3.2.2 智能问答流程

```yaml
问答处理完整流程：

用户查询                    系统处理                     AI服务
    │                          │                          │
    ▼                          ▼                          ▼
┌──────────┐             ┌────────────┐            ┌────────────┐
│输入问题  │ ──────────▶ │查询校验    │            │            │
└──────────┘             │(权限验证)  │            │            │
    │                    └────────────┘            │            │
    │                          │                  │            │
    ▼                   ┌────────────┐            │            │
┌──────────┐           │查询改写    │            │            │
│流式输出  │ ◀──────── │(可选)      │            │            │
└──────────┘           └────────────┘            │            │
    │                          │                  │            │
    │                   ┌────────────┐     ┌─────┴────────┐  │
    │                   │问题向量化   │ ──▶ │ Embedding    │  │
    │                   │(Query Embedding)   │ Service      │  │
    │                   └────────────┘     └─────────────┘  │
    │                          │                  │         │
    ▼                   ┌────────────┐     ┌──────────────┐  │
┌──────────┐           │向量检索    │ ◀── │   Qdrant     │  │
│显示思考  │ ◀──────── │(混合检索)  │     │ (向量数据库) │  │
└──────────┘           └────────────┘     └──────────────┘  │
    │                          │                            │
    ▼                   ┌────────────┐     ┌──────────────┐  │
┌──────────┐           │结果重排序  │ ◀── │   Reranker   │  │
│展示引用  │ ◀──────── │(可选)      │     │  (可选)      │  │
└──────────┘           └────────────┘     └──────────────┘  │
    │                          │                            │
    ▼                   ┌────────────┐     ┌──────────────┐  │
┌──────────┐           │Prompt构建  │ ──▶ │    LLM       │  │
│完整回答  │ ◀──────── │(上下文组装) │     │(Ollama/vLLM)│  │
└──────────┘           └────────────┘     └──────────────┘  │
    │                          │                            │
    ▼                   ┌────────────┐                      │
┌──────────┐           │保存对话    │ ◀─────────────▶ Redis │
│对话完成  │ ◀──────── │(历史记录)  │                      │
└──────────┘           └────────────┘                      │
```

### 3.3 模块详细说明

#### 3.3.1 用户权限模块

```yaml
功能描述：
- 实现用户注册、登录、认证功能
- 基于RBAC的权限控制模型
- 完整的操作审计日志

核心组件：
├── UserController          # 用户管理API
├── AuthController          # 认证API
├── RoleController          # 角色管理API
├── PermissionController    # 权限管理API
├── AuditLogController      # 审计日志API
├── UserService             # 用户服务
├── AuthService             # 认证服务
├── RBACService             # 权限服务
├── AuditService            # 审计服务
├── JwtTokenProvider        # JWT令牌提供器
└── SecurityConfig          # 安全配置

API接口：
POST   /api/auth/register     # 用户注册
POST   /api/auth/login        # 用户登录
POST   /api/auth/refresh      # 刷新Token
GET    /api/users             # 用户列表
POST   /api/users             # 创建用户
PUT    /api/users/{id}        # 更新用户
DELETE /api/users/{id}        # 删除用户
GET    /api/roles             # 角色列表
POST   /api/roles             # 创建角色
PUT    /api/roles/{id}        # 更新角色
GET    /api/audit-logs        # 审计日志
```

#### 3.3.2 文档管理模块

```yaml
功能描述：
- 支持多格式文档上传和处理
- 自动文本提取和清洗
- 智能文档分块

核心组件：
├── DocumentController       # 文档管理API
├── DocumentService          # 文档服务
├── DocumentProcessor        # 文档处理器（策略模式）
├── PDFProcessor             # PDF解析器
├── WordProcessor            # Word解析器
├── MarkdownProcessor        # Markdown解析器
├── TextCleaner              # 文本清洗器
├── DocumentChunker          # 文档分块器
├── ChunkingStrategy         # 分块策略接口
├── FixedSizeStrategy        # 固定长度分块
├── SemanticStrategy         # 语义分块
└── HybridStrategy           # 混合分块

API接口：
POST   /api/documents/upload       # 上传文档
GET    /api/documents               # 文档列表
GET    /api/documents/{id}          # 文档详情
PUT    /api/documents/{id}          # 更新文档
DELETE /api/documents/{id}          # 删除文档
GET    /api/documents/{id}/chunks   # 文档块列表
POST   /api/documents/{id}/process  # 触发处理
GET    /api/documents/{id}/status   # 处理状态
```

#### 3.3.3 RAG引擎模块

```yaml
功能描述：
- 向量存储和检索
- RAG编排和答案生成
- 多轮对话管理

核心组件：
├── RAGController            # RAG API
├── RAGOrchestrator          # RAG编排器
├── EmbeddingService         # Embedding服务接口
├── EmbeddingClient          # Embedding客户端
├── RetrievalService         # 检索服务
├── SemanticSearchStrategy    # 语义检索策略
├── HybridSearchStrategy     # 混合检索策略
├── VectorStoreService       # 向量存储服务
├── QdrantClient             # Qdrant客户端封装
├── LLMService               # LLM服务接口
├── OllamaClient             # Ollama客户端
├── VLLMClient               # vLLM客户端
├── ConversationService      # 对话服务
└── PromptTemplateManager   # Prompt模板管理

API接口：
POST   /api/rag/query              # 问答查询
POST   /api/rag/query/stream        # 流式问答
GET    /api/rag/collections        # 知识库列表
POST   /api/rag/collections         # 创建知识库
GET    /api/rag/collections/{id}    # 知识库详情
PUT    /api/rag/collections/{id}    # 更新知识库
DELETE /api/rag/collections/{id}    # 删除知识库
POST   /api/rag/collections/{id}/search  # 知识库检索
GET    /api/conversations           # 对话列表
GET    /api/conversations/{id}      # 对话详情
DELETE /api/conversations/{id}      # 删除对话
```

#### 3.3.4 系统管理模块

```yaml
功能描述：
- 系统配置管理
- 模型配置管理
- 知识库统计

核心组件：
├── ConfigController         # 配置管理API
├── ConfigService            # 配置服务
├── EtcdConfigStore          # etcd配置存储
├── ModelConfigService        # 模型配置服务
├── StatisticsService         # 统计服务
├── BackupService            # 备份服务
└── HealthCheckService       # 健康检查服务

API接口：
GET    /api/config                # 获取配置
PUT    /api/config/{key}          # 更新配置
GET    /api/config/chunking       # 获取分块配置
PUT    /api/config/chunking       # 更新分块配置
GET    /api/config/retrieval       # 获取检索配置
PUT    /api/config/retrieval      # 更新检索配置
GET    /api/models                # 模型列表
GET    /api/models/{id}/status    # 模型状态
GET    /api/statistics            # 统计数据
POST   /api/backup                # 创建备份
GET    /api/health                # 健康检查
GET    /api/health/detailed       # 详细健康状态
```

---

## 四、非功能性设计

### 4.1 性能设计

```yaml
性能指标目标：

响应时间：
- API响应时间 < 200ms（P95）
- 文档上传 < 5秒（10MB）
- 文档处理 < 30秒（10页PDF）
- 问答首Token < 3秒
- 问答完成 < 10秒

吞吐量：
- 支持50+并发用户
- API QPS > 100
- 向量检索 QPS > 100
- 文档处理 > 10文档/分钟

容量：
- 支持10GB文档存储
- 支持100万向量条目
- 支持1000个知识库

性能优化策略：
1. 缓存策略
   - Redis缓存热点数据
   - Embedding结果缓存
   - 查询结果缓存
   
2. 异步处理
   - 文档处理异步化
   - 向量生成异步化
   - 批量处理优化
   
3. 索引优化
   - Qdrant索引优化
   - PostgreSQL索引优化
   - 定期索引重建
   
4. 连接池优化
   - 数据库连接池（HikariCP）
   - HTTP连接池（RestTemplate）
   - Redis连接池
```

### 4.2 可靠性设计

```yaml
可用性目标：
- 系统可用性 > 99%
- 每月计划内停机 < 4小时
- 故障恢复时间 < 30分钟

容错设计：
1. 服务容错
   - 服务降级（部分功能不可用时保持核心功能）
   - 熔断器模式（防止级联故障）
   - 重试机制（临时故障自动重试）
   
2. 数据容错
   - 数据多副本存储
   - 定期备份
   - 增量备份
   
3. 故障转移
   - 数据库主从切换
   - Redis哨兵/集群模式
   - 应用服务器负载均衡

监控告警：
- 系统指标监控（CPU、内存、磁盘、网络）
- 应用指标监控（请求量、响应时间、错误率）
- AI服务监控（模型加载、推理延迟）
- 告警规则配置
- 告警通知（邮件、钉钉）
```

### 4.3 安全性设计

```yaml
安全措施：

1. 认证授权
   - JWT Token认证
   - RBAC权限控制
   - API签名验证
   
2. 数据安全
   - 敏感数据加密（AES-256）
   - 传输层加密（TLS 1.3）
   - 数据库加密存储
   
3. 应用安全
   - SQL注入防护
   - XSS防护
   - CSRF防护
   - 输入验证
   
4. API安全
   - API限流
   - IP白名单
   - 请求签名

5. 审计安全
   - 操作日志记录
   - 登录日志记录
   - 异常行为检测
   
安全加固：
- 定期安全扫描
- 渗透测试
- 安全补丁更新
- 安全配置审计
```

### 4.4 可扩展性设计

```yaml
水平扩展：
- 应用服务器无状态设计
- 负载均衡器分发请求
- 横向扩展AI服务
- 向量数据库分片

垂直扩展：
- 数据库读写分离
- 缓存层扩展
- 存储容量扩展

插件化设计：
- 文档处理器插件化
- Embedding服务可插拔
- LLM服务可插拔
- 检索策略可扩展

多租户支持（预留）：
- 数据隔离
- 配置隔离
- 资源隔离
```

---

## 五、数据模型设计

### 5.1 关系数据库模型（PostgreSQL）

```sql
-- 用户表
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(100),
    avatar_url VARCHAR(500),
    status VARCHAR(20) DEFAULT 'active',
    last_login_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 角色表
CREATE TABLE roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(50) UNIQUE NOT NULL,
    description VARCHAR(200),
    is_system BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 用户角色关联表
CREATE TABLE user_roles (
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    role_id UUID REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

-- 权限表
CREATE TABLE permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) UNIQUE NOT NULL,
    resource VARCHAR(50) NOT NULL,
    action VARCHAR(20) NOT NULL,
    description VARCHAR(200),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 角色权限关联表
CREATE TABLE role_permissions (
    role_id UUID REFERENCES roles(id) ON DELETE CASCADE,
    permission_id UUID REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

-- 知识库表
CREATE TABLE collections (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    description TEXT,
    owner_id UUID REFERENCES users(id),
    is_public BOOLEAN DEFAULT FALSE,
    config JSONB DEFAULT '{}',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 文档表
CREATE TABLE documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    collection_id UUID REFERENCES collections(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    file_path VARCHAR(500),
    file_type VARCHAR(20),
    file_hash VARCHAR(64),
    file_size BIGINT,
    status VARCHAR(20) DEFAULT 'pending',
    metadata JSONB DEFAULT '{}',
    processed_at TIMESTAMP,
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 文档块表
CREATE TABLE document_chunks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID REFERENCES documents(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    chunk_index INTEGER,
    chunk_size INTEGER,
    vector_id VARCHAR(100),
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 对话表
CREATE TABLE conversations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id),
    collection_id UUID REFERENCES collections(id),
    title VARCHAR(200),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 消息表
CREATE TABLE messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID REFERENCES conversations(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 审计日志表
CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id),
    action VARCHAR(100) NOT NULL,
    resource VARCHAR(100),
    resource_id UUID,
    details JSONB,
    ip_address VARCHAR(45),
    user_agent TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 系统配置表
CREATE TABLE system_configs (
    key VARCHAR(100) PRIMARY KEY,
    value TEXT,
    type VARCHAR(20) DEFAULT 'string',
    description VARCHAR(200),
    is_encrypted BOOLEAN DEFAULT FALSE,
    updated_by UUID REFERENCES users(id),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 索引
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_status ON users(status);
CREATE INDEX idx_documents_collection ON documents(collection_id);
CREATE INDEX idx_documents_status ON documents(status);
CREATE INDEX idx_document_chunks_document ON document_chunks(document_id);
CREATE INDEX idx_conversations_user ON conversations(user_id);
CREATE INDEX idx_messages_conversation ON messages(conversation_id);
CREATE INDEX idx_audit_logs_user ON audit_logs(user_id);
CREATE INDEX idx_audit_logs_created ON audit_logs(created_at);
```

### 5.2 向量数据库模型（Qdrant）

```yaml
Collection配置：

collection_name: document_embeddings

vector_config:
  size: 1024                    # BGE-large-zh-v1.5 向量维度
  distance: Cosine              # 余弦相似度

payload_schema:
  document_id: string           # 文档ID
  collection_id: string         # 知识库ID
  chunk_index: integer          # 块索引
  content: string               # 文本内容
  metadata: object              # 元数据
    - source: string            # 来源
    - author: string           # 作者
    - created_at: datetime      # 创建时间
    - tags: array[string]      # 标签
  
hnsw_config:
  m: 16
  ef_construct: 100
  full_scan_threshold: 10000

optimizers_config:
  indexed_threshold: 20000
  memmap_threshold: 50000
```

### 5.3 缓存数据结构（Redis）

```yaml
缓存键设计：

# 用户会话
session:{session_id}                    # 用户会话数据 (TTL: 2小时)
user:{user_id}:profile                 # 用户Profile (TTL: 30分钟)

# Token管理
jwt:blacklist:{token_id}               # Token黑名单 (TTL: Token过期时间)
jwt:refresh:{user_id}                   # Refresh Token (TTL: 7天)

# Embedding缓存
embedding:cache:{text_hash}           # 文本Embedding缓存 (TTL: 24小时)

# 检索结果缓存
search:cache:{query_hash}              # 检索结果缓存 (TTL: 10分钟)

# 限流控制
rate_limit:{user_id}:{endpoint}        # API限流 (TTL: 1分钟)

# 分布式锁
lock:document:{document_id}            # 文档处理锁
lock:collection:{collection_id}        # 知识库操作锁

# 计数器
counter:api:{endpoint}:{date}          # API调用计数
counter:user:{user_id}:{date}          # 用户请求计数
```

---

## 六、API接口设计

### 6.1 API设计原则

```yaml
RESTful API设计规范：

1. URL设计
   - 使用名词而非动词：/users, /documents, /collections
   - 使用复数形式：/users, /documents
   - 使用连字符分隔：/knowledge-bases, /audit-logs
   - 嵌套资源：/documents/{id}/chunks

2. HTTP方法
   GET    - 查询资源
   POST   - 创建资源
   PUT    - 完整更新资源
   PATCH  - 部分更新资源
   DELETE - 删除资源

3. 状态码
   200 OK           - 成功
   201 Created      - 创建成功
   400 Bad Request  - 请求参数错误
   401 Unauthorized - 未认证
   403 Forbidden    - 无权限
   404 Not Found    - 资源不存在
   500 Internal Error - 服务器错误

4. 统一响应格式
   {
     "code": 200,
     "message": "success",
     "data": { ... },
     "timestamp": "2026-03-25T10:00:00Z",
     "requestId": "uuid"
   }

5. 分页格式
   {
     "content": [ ... ],
     "page": 1,
     "size": 20,
     "total": 100,
     "totalPages": 5
   }

6. 错误格式
   {
     "code": 400,
     "message": "Invalid parameter",
     "errors": [
       {
         "field": "email",
         "message": "Invalid email format"
       }
     ],
     "timestamp": "2026-03-25T10:00:00Z",
     "requestId": "uuid"
   }
```

### 6.2 核心API接口

#### 6.2.1 认证接口

```yaml
POST /api/auth/register
请求：
{
  "username": "zhangsan",
  "email": "zhangsan@example.com",
  "password": "SecurePass123",
  "displayName": "张三"
}
响应：
{
  "code": 201,
  "message": "User registered successfully",
  "data": {
    "id": "uuid",
    "username": "zhangsan",
    "email": "zhangsan@example.com"
  }
}

POST /api/auth/login
请求：
{
  "username": "zhangsan",
  "password": "SecurePass123"
}
响应：
{
  "code": 200,
  "message": "Login successful",
  "data": {
    "accessToken": "eyJhbG...",
    "refreshToken": "eyJhbG...",
    "expiresIn": 1800
  }
}

POST /api/auth/refresh
请求：
{
  "refreshToken": "eyJhbG..."
}
响应：
{
  "code": 200,
  "message": "Token refreshed",
  "data": {
    "accessToken": "eyJhbG...",
    "refreshToken": "eyJhbG...",
    "expiresIn": 1800
  }
}
```

#### 6.2.2 文档管理接口

```yaml
POST /api/documents/upload
请求：multipart/form-data
  - file: 文件
  - collectionId: 知识库ID (可选)
  - title: 文档标题 (可选，默认使用文件名)
响应：
{
  "code": 201,
  "message": "Document uploaded successfully",
  "data": {
    "id": "uuid",
    "title": "技术文档.pdf",
    "fileType": "pdf",
    "fileSize": 1048576,
    "status": "pending",
    "uploadedAt": "2026-03-25T10:00:00Z"
  }
}

GET /api/documents/{id}/status
响应：
{
  "code": 200,
  "message": "success",
  "data": {
    "id": "uuid",
    "status": "processing",
    "progress": 65,
    "currentStep": "chunking",
    "steps": [
      { "name": "upload", "status": "completed", "progress": 100 },
      { "name": "parse", "status": "completed", "progress": 100 },
      { "name": "clean", "status": "completed", "progress": 100 },
      { "name": "chunk", "status": "processing", "progress": 50 },
      { "name": "embed", "status": "pending", "progress": 0 },
      { "name": "store", "status": "pending", "progress": 0 }
    ],
    "chunksCreated": 45,
    "estimatedTime": 30
  }
}

GET /api/documents/{id}/chunks
参数：
  - page: 页码 (默认1)
  - size: 每页数量 (默认20)
响应：
{
  "code": 200,
  "message": "success",
  "data": {
    "content": [
      {
        "id": "uuid",
        "content": "这是文档的第一个块内容...",
        "chunkIndex": 1,
        "metadata": {
          "source": "page 1",
          "paragraph": 1
        }
      }
    ],
    "page": 1,
    "size": 20,
    "total": 156,
    "totalPages": 8
  }
}
```

#### 6.2.3 RAG问答接口

```yaml
POST /api/rag/query
请求：
{
  "collectionId": "uuid",
  "question": "RAG系统的Embedding模型是什么？",
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
响应：
{
  "code": 200,
  "message": "success",
  "data": {
    "answer": "RAG系统使用的是BGE-large-zh-v1.5模型...",
    "sources": [
      {
        "documentId": "uuid",
        "documentTitle": "技术架构文档",
        "chunkContent": "我们的Embedding模型选用BGE-large-zh-v1.5...",
        "score": 0.89,
        "pageNumber": 5
      }
    ],
    "conversationId": "uuid",
    "messageId": "uuid",
    "usage": {
      "promptTokens": 500,
      "completionTokens": 150,
      "totalTokens": 650
    }
  }
}

POST /api/rag/query/stream
请求：同 POST /api/rag/query
响应：SSE (Server-Sent Events)
event: message
data: {"content": "RAG", "type": "token"}

event: message
data: {"content": "系统", "type": "token"}

event: source
data: {"documentId": "uuid", "title": "技术文档", "score": 0.89}

event: done
data: {"usage": {"totalTokens": 650}}
```

### 6.3 API限流配置

```yaml
限流策略：

全局限流：
- 默认：100请求/分钟
- 认证用户：500请求/分钟
- VIP用户：2000请求/分钟

特定接口限流：
- 文件上传：10请求/分钟
- 问答接口：30请求/分钟
- 文档搜索：100请求/分钟
- 用户登录：5请求/分钟（防暴力破解）

限流响应：
HTTP 429 Too Many Requests
{
  "code": 429,
  "message": "Rate limit exceeded",
  "data": {
    "limit": 30,
    "remaining": 0,
    "resetAt": "2026-03-25T10:01:00Z"
  }
}
```

---

## 第二部分内容预告

HLD文档第二部分将包含：
- 接口详细设计
- 模块详细设计
- 数据库详细设计
- 消息队列设计
- 日志设计
- 部署方案
- 实施计划
- 风险评估
- 验收标准

完整文档将在第三部分输出。
