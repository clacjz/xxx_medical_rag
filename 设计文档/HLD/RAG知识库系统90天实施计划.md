# RAG知识库系统90天实施计划

> **计划周期**: 90天（3个月）  
> **最后更新**: 2026-03-25  
> **技术栈**: Redis + CUDA + Ollama + PyTorch + vLLM + llama.cpp + Qdrant + etcd + MinIO + PaddleOCR  
> **部署模式**: 全本地化私有部署  

---

## 一、技术栈整合方案

### 1.1 技术栈定位与职责

```yaml
# 核心基础设施
- Redis          # 缓存层 + 会话存储 + Celery消息队列
- etcd           # 配置中心 + 服务注册发现
- MinIO          # 对象存储（文档原始文件）
- Qdrant         # 向量数据库（Embedding存储）

# AI模型服务
- CUDA           # GPU计算驱动
- Ollama         # LLM推理服务管理（推荐用于快速部署）
- vLLM           # 高性能LLM推理（生产环境推荐）
- llama.cpp      # CPU端LLM推理（备选/轻量场景）
- PyTorch        # Embedding模型运行框架
- PaddleOCR      # 图像/扫描文档OCR识别

# 后端服务
- Spring Boot 3.x + Spring AI  # API服务 + RAG编排
```

### 1.2 技术架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                        前端层 (Vue3/React)                        │
└──────────────────────────────┬──────────────────────────────────┘
                               │ HTTPS
┌──────────────────────────────▼──────────────────────────────────┐
│                      Nginx 反向代理                               │
│                  (SSL终止 + 静态资源 + 限流)                       │
└──────────────────────────────┬──────────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────────┐
│                    API网关 (Spring Cloud Gateway)                 │
│                    - JWT认证                                      │
│                    - 请求限流                                     │
│                    - 路由转发                                     │
└──────────────────────────────┬──────────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────────┐
│                      核心服务层                                   │
│  ┌────────────────┐  ┌────────────────┐  ┌──────────────────┐  │
│  │ 文档管理服务   │  │  RAG引擎服务    │  │   用户权限服务    │  │
│  │ (文档上传/解析)│  │ (检索+生成)     │  │  (JWT+RBAC)      │  │
│  └────────────────┘  └────────────────┘  └──────────────────┘  │
│  ┌────────────────┐  ┌────────────────┐  ┌──────────────────┐  │
│  │ 配置管理服务   │  │ 对话管理服务    │  │   任务调度服务    │  │
│  │ (etcd配置中心) │  │ (多轮对话)      │  │  (Celery异步)    │  │
│  └────────────────┘  └────────────────┘  └──────────────────┘  │
└──────────────────────────────┬──────────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────────┐
│                        AI服务层                                   │
│  ┌────────────────┐  ┌────────────────┐  ┌──────────────────┐  │
│  │ Embedding服务  │  │   LLM服务       │  │   OCR服务         │  │
│  │ (PyTorch+FastAPI)│  │ (vLLM/Ollama)  │  │  (PaddleOCR)     │  │
│  │ Port: 8081      │  │ Port: 8082     │  │  Port: 8083      │  │
│  └────────────────┘  └────────────────┘  └──────────────────┘  │
└──────────────────────────────┬──────────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────────┐
│                        数据存储层                                 │
│  ┌────────────────┐  ┌────────────────┐  ┌──────────────────┐  │
│  │     Redis      │  │    Qdrant      │  │     MinIO        │  │
│  │  (缓存+队列)   │  │  (向量存储)     │  │   (文件存储)     │  │
│  │  Port: 6379    │  │  Port: 6333    │  │  Port: 9000      │  │
│  └────────────────┘  └────────────────┘  └──────────────────┘  │
│  ┌────────────────┐  ┌────────────────┐  ┌──────────────────┐  │
│  │   PostgreSQL   │  │     etcd       │  │   文档本地存储    │  │
│  │  (关系数据)    │  │  (配置中心)     │  │  (/data/docs)   │  │
│  │  Port: 5432    │  │  Port: 2379    │  │                  │  │
│  └────────────────┘  └────────────────┘  └──────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 二、90天详细实施计划

### 阶段划分总览

```yaml
第一阶段：基础架构搭建（第1-15天）
第二阶段：AI模型服务（第16-35天）
第三阶段：核心功能开发（第36-60天）
第四阶段：前端开发（第45-75天）
第五阶段：系统集成与测试（第61-85天）
第六阶段：部署上线（第86-90天）
```

### 第一阶段：基础架构搭建（第1-15天）

#### 第1-3天：项目初始化

```yaml
任务清单：
1. 创建Spring Boot项目结构
   - 父POM + 子模块（api/doc-processor/rag-engine等）
   - Git仓库初始化
   - README和项目文档

2. 搭建开发环境
   - JDK 17+
   - Maven/Gradle
   - Docker + Docker Compose
   - Python 3.10+（AI服务）
   - GPU驱动和CUDA环境验证

3. 基础设施服务部署（Docker Compose）
   - PostgreSQL 15+
   - Redis 7+
   - etcd
   - MinIO（对象存储）
   - Qdrant（向量数据库）

交付物：
✅ 项目代码仓库
✅ 开发环境搭建文档
✅ 基础设施服务运行
```

**详细任务列表**：

```java
// Day 1: 项目结构创建
Day 1 上午：
- [ ] 创建Git仓库
- [ ] 初始化Spring Boot多模块项目
  ├── rag-parent
  │   ├── rag-api              # API服务
  │   ├── rag-common           # 公共模块
  │   ├── rag-doc-processor    # 文档处理
  │   └── rag-rag-engine       # RAG引擎
  ├── embedding-service        # Python Embedding服务
  ├── llm-service              # Python LLM服务（可选）
  └── docker-compose.yml

Day 1 下午：
- [ ] 配置Maven依赖（Spring Boot 3.x, Spring AI, etc.）
- [ ] 配置Git hooks（pre-commit, commit-msg）
- [ ] 编写README.md
- [ ] 创建docker-compose.yml（基础设施服务）

// Day 2: 基础设施部署
Day 2 上午：
- [ ] 编写docker-compose基础架构
  ├── PostgreSQL (端口5432)
  ├── Redis (端口6379)
  ├── etcd (端口2379)
  ├── MinIO (端口9000/9001)
  └── Qdrant (端口6333)

Day 2 下午：
- [ ] 启动所有基础设施服务
- [ ] 初始化数据库（建表脚本）
- [ ] 配置MinIO bucket
- [ ] 配置Qdrant collection
- [ ] 验证所有服务连通性

// Day 3: 开发框架搭建
Day 3 上午：
- [ ] 配置Spring Boot基础框架
- [ ] 配置Spring Data JPA + PostgreSQL
- [ ] 配置Spring Data Redis
- [ ] 配置Spring Security + JWT
- [ ] 配置SpringDoc OpenAPI

Day 3 下午：
- [ ] 编写数据库实体类（User, Document, DocumentChunk等）
- [ ] 配置application.yml多环境支持
- [ ] 编写启动脚本（start.sh, stop.sh）
- [ ] 编写健康检查脚本
- [ ] 完成阶段一Code Review
```

#### 第4-7天：配置中心和服务注册

```yaml
任务清单：
1. etcd配置中心实现
   - 配置管理服务
   - 动态配置刷新
   - 配置版本管理

2. 服务注册与发现
   - Spring Cloud Netflix Eureka 或 Consul
   - 服务健康检查
   - 动态上下线

交付物：
✅ 配置中心服务
✅ 服务注册中心
✅ 动态配置更新Demo
```

**Day 4-7 详细任务**：

```java
// Day 4: etcd集成
Day 4：
- [ ] 添加etcd依赖（jetcd）
- [ ] 实现配置读取服务
- [ ] 实现配置监听和热更新
- [ ] 编写配置管理接口（CRUD）

// Day 5: 服务注册
Day 5：
- [ ] 引入Consul/Eureka依赖
- [ ] 配置服务注册
- [ ] 实现服务健康检查
- [ ] 配置服务发现客户端

// Day 6: 配置文件模板
Day 6：
- [ ] 设计配置结构（YAML）
  ├── database.yml
  ├── redis.yml
  ├── qdrant.yml
  ├── minio.yml
  ├── embedding.yml
  └── llm.yml
- [ ] 实现配置验证器
- [ ] 配置变更告警机制

// Day 7: 集成测试
Day 7：
- [ ] 编写配置中心集成测试
- [ ] 编写服务注册集成测试
- [ ] 编写配置热更新测试
- [ ] 完成阶段一总结文档
```

#### 第8-10天：用户权限系统

```yaml
任务清单：
1. 用户认证
   - 用户注册/登录
   - JWT Token生成和验证
   - Refresh Token机制

2. RBAC权限模型
   - 角色定义（Admin/User/Guest）
   - 权限分配
   - 权限拦截器

3. 审计日志
   - 操作日志记录
   - 登录日志
   - 日志查询接口

交付物：
✅ 用户认证API
✅ 权限控制API
✅ 审计日志API
```

#### 第11-15天：文档存储服务

```yaml
任务清单：
1. MinIO集成
   - 文件上传服务
   - 文件下载服务
   - 桶管理

2. 文件处理基础
   - 文件类型识别
   - 文件大小限制
   - 存储路径管理

3. 文档元数据管理
   - 文档上传接口
   - 文档列表接口
   - 文档删除接口

交付物：
✅ 文档上传/下载API
✅ 文档管理API
✅ 存储服务Demo
```

**Day 11-15 详细任务**：

```java
// Day 11-12: MinIO集成
Day 11：
- [ ] 配置MinIO客户端
- [ ] 实现文件上传服务
- [ ] 实现文件下载服务
- [ ] 实现文件删除服务

Day 12：
- [ ] 实现文件预览服务（图片/PDF）
- [ ] 实现文件分享服务（生成临时URL）
- [ ] 配置MinIO生命周期策略

// Day 13-14: 文档管理API
Day 13：
- [ ] Document实体和Repository
- [ ] 文档上传接口（支持批量）
- [ ] 文档列表接口（分页+过滤）
- [ ] 文档详情接口

Day 14：
- [ ] 文档删除接口（软删除）
- [ ] 文档更新接口（重命名等）
- [ ] 文档搜索接口（文件名）
- [ ] 文档分类接口

// Day 15: 第一阶段验收
Day 15：
- [ ] 完成用户权限系统测试
- [ ] 完成文档存储系统测试
- [ ] 编写API接口文档（Swagger）
- [ ] 第一阶段Sprint Review
- [ ] 输出：第一阶段交付报告
```

---

### 第二阶段：AI模型服务（第16-35天）

#### 第16-20天：Embedding服务

```yaml
任务清单：
1. Embedding模型选型和部署
   - BGE-large-zh-v1.5模型
   - PyTorch服务化部署
   - FastAPI接口封装

2. Embedding API开发
   - 单条嵌入接口
   - 批量嵌入接口
   - 模型信息查询

3. 性能优化
   - GPU加速配置
   - 批量处理优化
   - 缓存策略

交付物：
✅ Embedding服务（端口8081）
✅ Python客户端SDK
✅ 性能测试报告
```

**Day 16-20 详细任务**：

```python
# Day 16: Embedding模型准备
Day 16:
- [ ] 准备GPU服务器环境（CUDA 11.8+/12.x）
- [ ] 安装PyTorch (CUDA版本)
  pip install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cu118
- [ ] 安装sentence-transformers
  pip install sentence-transformers
- [ ] 下载BGE-large-zh-v1.5模型
  from sentence_transformers import SentenceTransformer
  model = SentenceTransformer('BAAI/bge-large-zh-v1.5')

# Day 17-18: Embedding服务开发
Day 17:
- [ ] 创建FastAPI项目结构
- [ ] 实现模型加载服务
- [ ] 实现单条嵌入接口 POST /embed
- [ ] 实现批量嵌入接口 POST /embed/batch

Day 18:
- [ ] 实现模型信息接口 GET /model/info
- [ ] 实现健康检查接口 GET /health
- [ ] 添加请求验证（Pydantic）
- [ ] 配置日志

# Day 19-20: 性能优化
Day 19:
- [ ] GPU加速配置
- [ ] 批量处理优化（batch_size=32）
- [ ] 模型预热（warmup）
- [ ] 添加缓存层（Redis可选）

Day 20:
- [ ] 编写性能测试脚本
- [ ] 执行性能基准测试
- [ ] 生成性能测试报告
- [ ] 完成Embedding服务验收
```

#### 第21-28天：LLM服务（Ollama/vLLM）

```yaml
任务清单：
1. LLM模型选型
   - Ollama（快速原型/开发环境）
   - vLLM（生产环境推荐）
   - llama.cpp（CPU备选）

2. LLM服务部署
   - 模型下载和配置
   - API服务部署
   - 健康检查

3. Spring AI集成
   - ChatClient封装
   - 流式输出支持
   - 多模型支持

交付物：
✅ LLM服务（端口8082）
✅ Spring AI LLM客户端
✅ LLM调优指南
```

**Day 21-28 详细任务**：

```python
# Day 21-22: Ollama快速部署
Day 21:
- [ ] 安装Ollama
  # Linux/Mac
  curl -fsSL https://ollama.com/install.sh | sh
  # Windows: 下载安装包
  
- [ ] 拉取中文优化模型
  ollama pull qwen2.5:7b
  # 备选：glm4-9b-chat, llama3.1:8b
  
- [ ] 验证Ollama服务
  curl http://localhost:11434/api/generate -d '{
    "model": "qwen2.5:7b",
    "prompt": "你好"
  }'

Day 22:
- [ ] 配置Ollama服务开机自启
- [ ] 配置模型自动下载脚本
- [ ] 编写Ollama健康检查脚本
- [ ] 编写Ollama监控脚本

# Day 23-26: vLLM生产部署
Day 23:
- [ ] 安装vLLM
  pip install vllm
  
- [ ] 配置GPU环境
  nvidia-smi  # 确认CUDA可用
  
- [ ] 下载模型（HF格式）
  # Qwen2.5-7B-Instruct-GPTQ (量化版，推荐)

Day 24-25:
- [ ] 启动vLLM服务
  python -m vllm.entrypoints.openai.api_server \
    --model Qwen/Qwen2.5-7B-Instruct-GPTQ \
    --tensor-parallel-size 1 \
    --port 8082 \
    --gpu-memory-utilization 0.9

- [ ] 验证vLLM服务
  curl http://localhost:8082/v1/chat/completions \
    -H "Content-Type: application/json" \
    -d '{"model": "qwen2.5-7b-instruct", "messages": [{"role": "user", "content": "Hello"}]}'

Day 26:
- [ ] 配置vLLM性能优化
  # 调整tensor-parallel-size
  # 调整gpu-memory-utilization
  # 配置自动垃圾回收
  
- [ ] 编写vLLM运维脚本
  # 启动脚本、停止脚本、重启脚本

# Day 27-28: Spring AI集成
Day 27:
- [ ] 添加Spring AI依赖
  <dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
  </dependency>

- [ ] 配置LLM客户端
  spring:
    ai:
      openai:
        base-url: http://localhost:8082/v1
        api-key: dummy
        chat:
          options:
            model: qwen2.5-7b-instruct

- [ ] 实现LLM服务封装
  @Service
  public class LLMService {
      private final ChatClient chatClient;
      
      public String generate(String prompt);
      public Flux<String> generateStream(String prompt);
  }

Day 28:
- [ ] 实现流式输出接口
- [ ] 实现Prompt模板管理
- [ ] 编写LLM服务测试用例
- [ ] 完成LLM服务验收
```

#### 第29-35天：OCR服务（PaddleOCR）

```yaml
任务清单：
1. PaddleOCR部署
   - 安装PaddlePaddle
   - 安装PaddleOCR
   - 模型下载

2. OCR服务开发
   - 通用文字识别API
   - 表格识别API
   - 文档结构化API

3. 集成文档处理流程
   - 扫描文档OCR识别
   - 表格提取
   - 混排文档处理

交付物：
✅ OCR服务（端口8083）
✅ 文档OCR处理流程
✅ OCR准确率测试报告
```

**Day 29-35 详细任务**：

```python
# Day 29-31: PaddleOCR环境搭建
Day 29:
- [ ] 安装PaddlePaddle GPU版
  pip install paddlepaddle-gpu
  
- [ ] 安装PaddleOCR
  pip install paddleocr
  
- [ ] 下载OCR模型
  # 中文识别模型
  # 表格识别模型
  # 文档OCR模型

Day 30-31:
- [ ] 创建PaddleOCR FastAPI服务
- [ ] 实现文字识别接口
- [ ] 实现表格识别接口
- [ ] 实现文档结构化接口

# Day 32-33: 高级OCR功能
Day 32:
- [ ] 实现PDF OCR识别
- [ ] 实现图片批量识别
- [ ] 实现手写文字识别
- [ ] 实现身份证/名片识别

Day 33:
- [ ] OCR结果后处理
- [ ] 坐标映射和文本清洗
- [ ] 编码自动检测和转换
- [ ] OCR性能优化

# Day 34-35: 集成测试
Day 34:
- [ ] 编写OCR集成测试
- [ ] 测试各类文档OCR效果
- [ ] 生成OCR准确率报告

Day 35:
- [ ] 第二阶段Sprint Review
- [ ] AI服务整体验收
- [ ] 输出：第二阶段交付报告
```

---

### 第三阶段：核心功能开发（第36-60天）

#### 第36-42天：文档处理模块

```yaml
任务清单：
1. 文档解析器开发
   - PDF解析器（Apache PDFBox + pdfplumber）
   - Word解析器（Apache POI）
   - Markdown解析器
   - TXT解析器

2. 文本清洗和预处理
   - 编码检测和转换
   - 格式标准化
   - 噪声过滤

3. 文档分块器
   - 固定长度分块
   - 语义分块
   - 混合分块策略

交付物：
✅ 文档解析服务
✅ 文本预处理服务
✅ 文档分块服务
```

**Day 36-42 详细任务**：

```java
// Day 36-38: 文档解析器
Day 36:
- [ ] DocumentProcessor接口定义
- [ ] PDF解析器实现（PDFBox + pdfplumber）
- [ ] 表格提取功能

Day 37:
- [ ] Word解析器实现（Apache POI）
- [ ] Markdown解析器实现
- [ ] TXT解析器实现

Day 38:
- [ ] 策略模式实现（自动选择解析器）
- [ ] 解析器注册机制
- [ ] 解析结果验证

// Day 39-40: 文本预处理
Day 39:
- [ ] TextCleaner服务实现
- [ ] 编码检测（chardet）
- [ ] 格式标准化
- [ ] 噪声过滤（页眉页脚、水印）

Day 40:
- [ ] 文本分段和合并
- [ ] 特殊字符处理
- [ ] 长度限制和截断
- [ ] 单元测试编写

// Day 41-42: 文档分块器
Day 41:
- [ ] ChunkingStrategy接口
- [ ] FixedSizeChunkingStrategy
- [ ] SemanticChunkingStrategy

Day 42:
- [ ] HybridChunkingStrategy（混合策略）
- [ ] 配置化分块参数
- [ ] 分块质量评估
- [ ] 文档处理完整流程测试
```

#### 第43-50天：RAG引擎开发

```yaml
任务清单：
1. 向量存储服务
   - Qdrant客户端封装
   - Collection管理
   - 向量CRUD操作

2. 检索服务
   - 语义检索
   - 混合检索（向量+关键词）
   - 重排序（Rerank）

3. RAG编排器
   - 检索-生成流程
   - Prompt工程
   - 引用来源追踪

交付物：
✅ 向量存储服务
✅ 检索服务
✅ RAG编排器
```

**Day 43-50 详细任务**：

```java
// Day 43-45: 向量存储服务
Day 43:
- [ ] Qdrant客户端配置
- [ ] Collection创建和配置
- [ ] 向量插入接口

Day 44:
- [ ] 向量查询接口
- [ ] 向量更新接口
- [ ] 向量删除接口

Day 45:
- [ ] 批量操作优化
- [ ] 索引管理
- [ ] 存储监控

// Day 46-48: 检索服务
Day 46:
- [ ] SemanticSearchStrategy
- [ ] HybridSearchStrategy
- [ ] 配置化检索参数

Day 47:
- [ ] Reranking实现（Cohere本地模型）
- [ ] 查询改写（Query Expansion）
- [ ] 检索结果过滤

Day 48:
- [ ] 检索评估指标计算
- [ ] 检索质量优化
- [ ] 检索性能优化

// Day 49-50: RAG编排器
Day 49:
- [ ] RAGOrchestrator核心逻辑
- [ ] Prompt模板管理
- [ ] 上下文构建

Day 50:
- [ ] 引用来源追踪
- [ ] 答案验证（Hallucination检测）
- [ ] RAG完整流程测试
```

#### 第51-60天：高级功能和系统增强

```yaml
任务清单：
1. 对话管理
   - 会话创建和管理
   - 历史消息存储
   - Redis缓存

2. 异步任务处理
   - 大文档处理
   - 批量文档导入
   - Celery任务队列

3. 监控和日志
   - Prometheus监控
   - Grafana看板
   - 日志聚合

4. 系统优化
   - 性能调优
   - 缓存优化
   - 异常处理

交付物：
✅ 对话管理服务
✅ 异步任务处理
✅ 监控告警系统
✅ 系统优化完成
```

**Day 51-60 详细任务**：

```java
// Day 51-53: 对话管理
Day 51:
- [ ] Conversation实体设计
- [ ] Message实体设计
- [ ] 对话Repository实现

Day 52:
- [ ] 会话创建和管理API
- [ ] 历史消息查询API
- [ ] 会话搜索功能

Day 53:
- [ ] Redis缓存会话
- [ ] 会话超时管理
- [ ] 会话导出功能

// Day 54-56: 异步任务处理
Day 54:
- [ ] Celery配置
- [ ] 文档解析任务
- [ ] 向量生成任务

Day 55:
- [ ] 批量文档导入任务
- [ ] 文档重建索引任务
- [ ] 任务状态查询

Day 56:
- [ ] 任务重试机制
- [ ] 任务失败告警
- [ ] 任务性能监控

// Day 57-60: 监控和日志
Day 57:
- [ ] Prometheus指标定义
- [ ] Grafana看板设计
- [ ] 告警规则配置

Day 58:
- [ ] 日志聚合（ELK Stack）
- [ ] 日志格式化
- [ ] 日志查询接口

Day 59-60:
- [ ] 性能调优
- [ ] 安全加固
- [ ] 代码审计
- [ ] 第三阶段Sprint Review
```

---

### 第四阶段：前端开发（第45-75天，与第三阶段并行）

#### 第45-52天：前端项目搭建

```yaml
技术栈选择：Vue3 + Element Plus 或 React + Ant Design

任务清单：
1. 项目初始化
   - 脚手架创建
   - 路由配置
   - 状态管理

2. UI组件库集成
   - 组件库安装
   - 主题配置
   - 公共组件封装

3. API服务封装
   - Axios配置
   - 接口封装
   - 错误处理

交付物：
✅ 前端项目框架
✅ 公共组件库
✅ API服务封装
```

**Day 45-52 详细任务**：

```javascript
// Day 45-46: 项目初始化
Day 45:
- [ ] 创建Vue3/React项目
- [ ] 配置TypeScript
- [ ] 配置路由（Vue Router / React Router）
- [ ] 配置状态管理（Pinia / Redux Toolkit）

Day 46:
- [ ] 配置CSS预处理器（Sass/Less）
- [ ] 配置代码规范（ESLint + Prettier）
- [ ] 配置环境变量

// Day 47-49: UI组件库
Day 47:
- [ ] 安装Element Plus / Ant Design
- [ ] 配置按需加载
- [ ] 配置主题色

Day 48:
- [ ] 封装公共组件
  // Button, Table, Form, Modal等
- [ ] 封装业务组件
  // DocumentCard, ChatBubble等

Day 49:
- [ ] 布局组件开发
  // Header, Sidebar, Content
- [ ] 响应式布局适配
- [ ] 主题切换功能

// Day 50-52: API服务
Day 50:
- [ ] Axios实例配置
- [ ] 请求拦截器（Token注入）
- [ ] 响应拦截器（错误处理）

Day 51:
- [ ] API接口封装
  // userAPI, documentAPI, chatAPI
- [ ] 接口类型定义
- [ ] Mock数据配置

Day 52:
- [ ] 页面结构搭建
- [ ] 路由权限控制
- [ ] 登录页面开发
```

#### 第53-65天：核心功能页面

```yaml
任务清单：
1. 文档管理页面
   - 文档列表
   - 文档上传
   - 文档详情
   - 文档删除

2. 问答页面
   - 对话界面
   - 流式输出
   - 引用展示

3. 用户管理页面
   - 用户列表
   - 角色分配
   - 权限配置

4. 配置管理页面
   - 分块参数配置
   - 检索策略配置
   - 模型配置

交付物：
✅ 文档管理页面
✅ 问答对话页面
✅ 用户管理页面
✅ 配置管理页面
```

**Day 53-65 详细任务**：

```javascript
// Day 53-56: 文档管理页面
Day 53:
- [ ] 文档列表页面
  // 表格展示、分页、搜索

Day 54:
- [ ] 文档上传页面
  // 拖拽上传、进度展示

Day 55:
- [ ] 文档详情页面
  // 文档预览、元数据展示

Day 56:
- [ ] 文档删除功能
- [ ] 批量操作功能
- [ ] 文档状态展示

// Day 57-60: 问答对话页面
Day 57:
- [ ] 对话列表页面
- [ ] 新建对话功能

Day 58:
- [ ] 对话聊天界面
- [ ] 消息展示组件
- [ ] 输入框组件

Day 59:
- [ ] 流式输出实现
  // SSE (Server-Sent Events)
- [ ] 打字机效果

Day 60:
- [ ] 引用来源展示
- [ ] 答案反馈功能
- [ ] 对话历史功能

// Day 61-65: 管理页面
Day 61-62:
- [ ] 用户管理页面
  // 用户列表、新增、编辑、删除
- [ ] 角色管理页面
- [ ] 权限配置页面

Day 63-64:
- [ ] 配置管理页面
  // 分块参数、检索策略
- [ ] 模型管理页面

Day 65:
- [ ] 监控看板页面
- [ ] 审计日志页面
- [ ] 第四阶段Sprint Review
```

#### 第66-75天：前端优化和测试

```yaml
任务清单：
1. 性能优化
   - 懒加载
   - 虚拟滚动
   - 缓存优化

2. 安全加固
   - XSS防护
   - CSRF Token
   - 敏感信息脱敏

3. E2E测试
   - Cypress/Playwright
   - 关键流程测试
   - 兼容性测试

4. 部署准备
   - 构建优化
   - 环境配置
   - Nginx配置

交付物：
✅ 优化后的前端项目
✅ E2E测试用例
✅ 部署配置
```

---

### 第五阶段：系统集成与测试（第61-85天）

#### 第61-70天：集成测试

```yaml
任务清单：
1. API集成测试
   - 接口联调
   - 数据一致性
   - 异常场景

2. E2E测试
   - 用户注册登录
   - 文档上传处理
   - 问答对话
   - 权限控制

3. 性能测试
   - 并发测试
   - 压力测试
   - 性能瓶颈定位

交付物：
✅ 集成测试报告
✅ E2E测试报告
✅ 性能测试报告
```

**Day 61-70 详细任务**：

```java
// Day 61-64: API集成测试
Day 61:
- [ ] 文档上传→解析→分块→存储全流程测试
- [ ] 向量生成→Qdrant存储测试
- [ ] 检索→生成全流程测试

Day 62:
- [ ] 用户认证全流程测试
- [ ] 权限控制全流程测试
- [ ] 会话管理全流程测试

Day 63:
- [ ] 配置更新→服务生效测试
- [ ] 多租户隔离测试
- [ ] 数据备份恢复测试

Day 64:
- [ ] 异常场景测试
- [ ] 边界条件测试
- [ ] 编写集成测试文档

// Day 65-68: E2E测试
Day 65:
- [ ] 搭建Cypress/Playwright环境
- [ ] 编写登录注册测试用例
- [ ] 编写文档上传测试用例

Day 66:
- [ ] 编写问答对话测试用例
- [ ] 编写权限管理测试用例
- [ ] 编写配置管理测试用例

Day 67:
- [ ] 执行E2E测试
- [ ] 修复测试失败项
- [ ] 生成E2E测试报告

Day 68:
- [ ] 浏览器兼容性测试
- [ ] 移动端适配测试
- [ ] 性能测试准备

// Day 69-70: 性能测试
Day 69:
- [ ] 配置JMeter测试场景
- [ ] 执行并发测试（100并发）
- [ ] 性能数据收集

Day 70:
- [ ] 执行压力测试（逐步增加负载）
- [ ] 性能瓶颈分析
- [ ] 生成性能测试报告
```

#### 第71-80天：问题修复和优化

```yaml
任务清单：
1. Bug修复
   - 测试发现的问题
   - 用户反馈的问题
   - 安全漏洞

2. 性能优化
   - 根据测试结果优化
   - 缓存策略优化
   - 查询优化

3. 安全加固
   - 渗透测试
   - 安全漏洞修复
   - 安全配置审计

交付物：
✅ Bug修复清单
✅ 性能优化报告
✅ 安全加固报告
```

#### 第81-85天：UAT测试

```yaml
任务清单：
1. UAT准备
   - UAT测试用例编写
   - 测试环境准备
   - 测试数据准备

2. UAT执行
   - 用户验收测试
   - 问题记录
   - 问题修复

3. 试运行
   - 小范围试运行
   - 问题收集
   - 反馈迭代

交付物：
✅ UAT测试报告
✅ 试运行报告
✅ 上线审批
```

---

### 第六阶段：部署上线（第86-90天）

#### 第86-88天：生产环境部署

```yaml
任务清单：
1. 生产环境准备
   - 服务器准备
   - 网络配置
   - 域名和SSL证书

2. 部署脚本编写
   - 一键部署脚本
   - 回滚脚本
   - 健康检查脚本

3. 监控系统部署
   - Prometheus部署
   - Grafana部署
   - 告警配置

交付物：
✅ 生产环境
✅ 部署脚本
✅ 监控系统
```

#### 第89-90天：上线和验收

```yaml
任务清单：
1. 正式上线
   - 数据迁移
   - 服务启动
   - 健康检查

2. 上线验收
   - 功能验收
   - 性能验收
   - 安全验收

3. 交付文档
   - 用户手册
   - 运维手册
   - 技术文档

交付物：
✅ 上线报告
✅ 验收报告
✅ 完整交付文档
```

---

## 三、里程碑和交付物

### 3.1 关键里程碑

```yaml
里程碑1: M1 - 第15天
名称：基础架构完成
标准：
- ✅ 所有基础设施服务运行正常
- ✅ 用户认证权限系统可用
- ✅ 文档存储服务可用
- ✅ API文档生成完成

里程碑2: M2 - 第35天
名称：AI服务完成
标准：
- ✅ Embedding服务性能达标（<100ms）
- ✅ LLM服务响应正常
- ✅ OCR服务准确率>95%
- ✅ AI服务集成测试通过

里程碑3: M3 - 第60天
名称：核心功能完成
标准：
- ✅ 文档处理全流程可用
- ✅ RAG问答功能可用
- ✅ 对话管理功能可用
- ✅ 监控告警系统可用

里程碑4: M4 - 第75天
名称：前端完成
标准：
- ✅ 所有页面开发完成
- ✅ E2E测试通过
- ✅ 前端性能优化完成
- ✅ 浏览器兼容性通过

里程碑5: M5 - 第85天
名称：系统集成完成
标准：
- ✅ 所有功能测试通过
- ✅ 性能测试达标
- ✅ 安全测试通过
- ✅ UAT测试通过

里程碑6: M6 - 第90天
名称：项目交付
标准：
- ✅ 生产环境部署完成
- ✅ 用户验收通过
- ✅ 文档交付完成
- ✅ 项目正式上线
```

### 3.2 交付物清单

```yaml
代码交付物：
- [ ] 后端源代码（Spring Boot）
- [ ] AI服务源代码（Python）
- [ ] 前端源代码（Vue3/React）
- [ ] Dockerfile配置
- [ ] docker-compose.yml
- [ ] 数据库脚本

文档交付物：
- [ ] 需求规格说明书
- [ ] 技术设计文档
- [ ] API接口文档
- [ ] 部署文档
- [ ] 用户操作手册
- [ ] 运维手册
- [ ] 测试报告

培训交付物：
- [ ] 系统演示视频
- [ ] 培训PPT
- [ ] FAQ文档
```

---

## 四、团队配置建议

### 4.1 最小团队配置（90天完成）

```yaml
核心开发：3人
- 后端开发1：负责Spring Boot核心服务
- 后端开发2：负责AI服务集成
- 全栈开发1：负责前端开发

DevOps：1人（可兼职）
- 负责部署、环境、监控

测试：1人
- 负责测试和质量保障

PM/需求：1人（可兼职）
- 负责需求管理、进度把控

总计：5-6人
```

### 4.2 时间分配建议

```yaml
个人每日工作安排：
上午（3小时）：
- 编码开发
- Code Review

下午（3小时）：
- 编码开发
- 测试调试

晚间（1小时）：
- 文档编写
- 总结复盘

每周时间分配：
- 编码：60%
- 测试：20%
- 文档：15%
- 会议：5%
```

---

## 五、风险识别和缓冲

### 5.1 主要风险

```yaml
风险1：LLM模型部署困难
概率：高
影响：高
缓解措施：
- 预留5天Buffer
- 准备Ollama作为备选方案
- 提前测试vLLM部署

风险2：文档处理质量不达标
概率：中
影响：中
缓解措施：
- 提前进行PDF解析测试
- 准备多种解析方案
- 预留OCR作为补充

风险3：性能不达标
概率：中
影响：高
缓解措施：
- 每阶段进行性能测试
- 预留优化时间
- 准备降级方案

风险4：前端开发延期
概率：中
影响：中
缓解措施：
- 与后端并行开发
- 使用成熟UI组件库
- 预留3天Buffer
```

### 5.2 缓冲时间安排

```yaml
总Buffer时间：10天

分配：
- LLM部署：+5天
- 性能优化：+3天
- 测试修复：+2天

使用原则：
- 只有在对应任务超期时才启用
- 启用Buffer需要PM批准
- 每周评估Buffer使用情况
```

---

## 六、每周检查点

### 6.1 周例会检查项

```yaml
每周五下午：周例会

检查内容：
1. 本周任务完成情况
2. 下周任务计划
3. 遇到的问题和阻碍
4. 风险预警

输出：
- 周报（项目状态）
- 问题跟踪清单
- 下周计划

里程碑检查（第15/35/60/75/85/90天）：
1. 里程碑完成情况
2. 交付物检查
3. 质量检查
4. 风险重新评估
```

### 6.2 关键评审点

```yaml
评审1：第15天 - 架构评审
- 评审基础架构设计
- 评审技术选型
- 评审接口设计

评审2：第35天 - AI服务评审
- 评审AI服务性能
- 评审模型效果
- 评审集成方案

评审3：第60天 - 功能评审
- 评审核心功能
- 评审RAG效果
- 评审用户体验

评审4：第85天 - 上线评审
- 评审测试结果
- 评审部署方案
- 评审交付文档
```

---

## 七、成本预算

### 7.1 硬件成本

```yaml
开发测试环境：
- 开发服务器（4核/16GB）：¥3,000/月
- GPU服务器（RTX 4090）：¥5,000/月

生产环境：
- 应用服务器（4核/16GB）× 2：¥6,000/月
- GPU服务器（A10G）× 2：¥10,000/月
- 数据库服务器（8核/32GB）：¥4,000/月

总计：约¥28,000/月（开发+生产）
90天总计：约¥84,000
```

### 7.2 软件成本

```yaml
开源软件（零成本）：
- Spring Boot
- PyTorch
- vLLM
- Qdrant
- PostgreSQL
- Redis
- MinIO
- etcd

可选商业软件：
- 云服务备份：¥500/月
- 监控工具企业版：¥1,000/月

90天总计：约¥45,000
```

### 7.3 人力成本

```yaml
团队配置：5人
- 平均月薪：¥30,000/人

90天（3个月）人力成本：
- 开发成本：5人 × 3月 × ¥30,000 = ¥450,000

总成本估算：约¥579,000
```

---

## 八、成功标准

### 8.1 功能成功标准

```yaml
必须功能：
- [ ] 用户注册登录
- [ ] 文档上传和处理
- [ ] RAG问答功能
- [ ] 权限控制
- [ ] 对话历史

可选功能：
- [ ] 多知识库
- [ ] 高级检索
- [ ] 批量处理
```

### 8.2 性能成功标准

```yaml
响应时间：
- API响应 < 200ms（P95）
- 文档上传 < 5秒（10MB）
- 问答响应 < 10秒（首Token）

吞吐量：
- 支持50并发用户
- 支持100QPS检索

准确性：
- OCR准确率 > 95%
- RAG回答准确率 > 80%（基于评估集）
```

### 8.3 稳定性成功标准

```yaml
可用性：
- 系统可用性 > 99%
- 每月停机时间 < 7小时

可靠性：
- 数据丢失率 0%
- 错误率 < 0.1%

恢复能力：
- 故障恢复时间 < 30分钟
- 数据恢复时间 < 1小时
```

---

## 九、附录

### 附录A：技术文档链接

```yaml
- Spring Boot: https://spring.io/projects/spring-boot
- Spring AI: https://spring.io/projects/spring-ai
- vLLM: https://docs.vllm.ai/
- Ollama: https://github.com/ollama/ollama
- Qdrant: https://qdrant.tech/documentation/
- PaddleOCR: https://github.com/PaddlePaddle/PaddleOCR
- BGE: https://github.com/FlagOpen/FlagEmbedding
```

### 附录B：备选方案

```yaml
如果vLLM部署失败：
1. 使用Ollama作为备选
2. 使用llama.cpp作为CPU备选
3. 考虑云端API临时方案

如果性能不达标：
1. 水平扩展AI服务
2. 优化模型量化
3. 优化检索策略
```

---

**计划制定完成** - 建议按此计划严格执行，并在每周例会上评估进度和风险。

---

*最后更新：2026-03-25*
