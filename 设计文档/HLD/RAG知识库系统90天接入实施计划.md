# RAG知识库系统90天接入实施计划

> **计划周期**: 90天（3个月）  
> **最后更新**: 2026-03-25  
> **核心前提**: 所有基础服务已部署（Redis、CUDA、Ollama、PyTorch、vLLM、llama.cpp、Qdrant、etcd、MinIO、PaddleOCR）  
> **重点**: 服务接入、配置优化、功能开发、系统集成  

---

## 一、已部署服务概览

### 1.1 服务器基础设施

```yaml
✅ 已安装并运行的服务：

基础设施层：
- Redis        → 缓存 + 会话存储（端口：6379）
- etcd         → 配置中心（端口：2379）
- MinIO        → 对象存储（端口：9000/9001）
- Qdrant       → 向量数据库（端口：6333）

AI服务层：
- CUDA         → GPU计算驱动（已安装）
- Ollama       → LLM推理服务（端口：11434）
- vLLM         → 高性能LLM推理（端口：8082）
- llama.cpp    → CPU推理（本地运行）
- PyTorch      → Embedding运行框架
- PaddleOCR    → OCR识别服务

待集成：
- PostgreSQL   → 关系数据存储（需确认部署情况）
```

### 1.2 接入优先级

```yaml
P0（必须接入）:
1. Qdrant - 向量数据库
2. Redis - 缓存和会话
3. Ollama/vLLM - LLM服务
4. MinIO - 文件存储
5. etcd - 配置中心

P1（重要接入）:
6. PaddleOCR - OCR服务
7. Embedding服务 - BGE模型
8. PostgreSQL - 关系数据

P2（可选/后期）:
9. llama.cpp - CPU备选
```

---

## 二、调整后的90天计划

### 计划调整说明

```yaml
调整要点：
1. ✅ 删除所有部署相关任务（Day 16-35大幅缩短）
2. ✅ 聚焦服务接入和配置
3. ✅ 加快核心功能开发
4. ✅ 增加性能调优时间
5. ✅ 增加测试和优化时间

新阶段划分：
第一阶段：项目初始化 + 服务接入（第1-10天）
第二阶段：核心功能开发（第11-50天）
第三阶段：前端开发（第30-70天，与第二阶段并行）
第四阶段：系统集成与测试（第51-85天）
第五阶段：部署上线（第86-90天）
```

### 第一阶段：项目初始化 + 服务接入（第1-10天）

#### Day 1-2: 项目初始化

```yaml
任务：
1. 创建Spring Boot项目结构
2. 配置Maven/Gradle依赖
3. 配置Git仓库
4. 创建docker-compose.yml（仅用于本地开发）

交付物：
- ✅ Spring Boot项目框架
- ✅ 基础配置文件
```

**Day 1-2 详细任务**：

```java
// Day 1: 项目搭建
Day 1 上午：
- [ ] 创建Spring Boot多模块项目
  ├── rag-parent
  │   ├── rag-api              # API服务
  │   ├── rag-common           # 公共模块
  │   ├── rag-doc-processor    # 文档处理
  │   └── rag-rag-engine       # RAG引擎
- [ ] 配置pom.xml依赖（Spring Boot 3.x, Spring AI, etc.）
- [ ] 创建README.md

Day 1 下午：
- [ ] 配置application.yml基础结构
- [ ] 配置多环境支持（dev/test/prod）
- [ ] 配置Git hooks

// Day 2: 服务连接配置
Day 2 上午：
- [ ] 配置application-dev.yml
- [ ] 测试连接到各服务（Redis, Qdrant, etcd, MinIO）
- [ ] 创建服务连接工具类

Day 2 下午：
- [ ] 编写服务健康检查接口
- [ ] 编写配置加载Demo
- [ ] 完成项目初始化Code Review
```

#### Day 3-7: 服务接入

```yaml
任务：
1. 接入Redis（缓存 + 会话）
2. 接入Qdrant（向量数据库）
3. 接入etcd（配置中心）
4. 接入MinIO（文件存储）
5. 编写服务连接测试

交付物：
- ✅ 服务连接模块
- ✅ 健康检查API
- ✅ 连接测试报告
```

**Day 3-7 详细任务**：

```java
// Day 3: Redis接入
Day 3：
- [ ] 配置Spring Data Redis依赖
- [ ] 配置application.yml
  spring:
    data:
      redis:
        host: ${REDIS_HOST:localhost}
        port: ${REDIS_PORT:6379}
        password: ${REDIS_PASSWORD:}
- [ ] 实现RedisConfig配置类
- [ ] 实现RedisTemplate封装
- [ ] 测试Redis连接
- [ ] 测试String/Hash/List操作
- [ ] 实现Session存储Demo
- [ ] 实现缓存Demo（@Cacheable）
- [ ] 编写Redis连接测试用例

// Day 4: Qdrant接入
Day 4：
- [ ] 添加Qdrant Java客户端依赖
  <dependency>
    <groupId>io.qdrant</groupId>
    <artifactId>qdrant-client</artifactId>
  </dependency>
- [ ] 配置Qdrant连接信息
  rag:
    qdrant:
      host: ${QDRANT_HOST:localhost}
      port: ${QDRANT_PORT:6333}
      collection: document_embeddings
- [ ] 实现QdrantConfig配置类
- [ ] 实现QdrantClient封装
- [ ] 创建Collection（配置向量维度1024，距离Cosine）
- [ ] 测试向量插入
- [ ] 测试向量检索（余弦相似度）
- [ ] 编写Qdrant连接测试用例

// Day 5: etcd接入
Day 5：
- [ ] 添加etcd客户端依赖（jetcd）
- [ ] 配置etcd连接
  rag:
    etcd:
      endpoints: ${ETCD_HOST:localhost}:${ETCD_PORT:2379}
- [ ] 实现etcd配置服务
- [ ] 实现配置监听和热更新
- [ ] 测试配置读取
- [ ] 测试配置变更监听
- [ ] 编写etcd连接测试用例

// Day 6: MinIO接入
Day 6：
- [ ] 添加MinIO Java客户端依赖
- [ ] 配置MinIO连接
  spring:
    servlet:
      multipart:
        max-file-size: 100MB
  rag:
    minio:
      endpoint: ${MINIO_ENDPOINT:http://localhost:9000}
      access-key: ${MINIO_ACCESS_KEY:}
      secret-key: ${MINIO_SECRET_KEY:}
      bucket: rag-documents
- [ ] 实现MinioConfig配置类
- [ ] 实现MinioTemplate封装
- [ ] 创建Bucket配置
- [ ] 测试文件上传
- [ ] 测试文件下载
- [ ] 测试文件删除
- [ ] 编写MinIO连接测试用例

// Day 7: 统一健康检查
Day 7：
- [ ] 实现统一健康检查接口 GET /health
- [ ] 包含所有服务健康状态
- [ ] 实现健康检查前端页面
- [ ] 编写服务接入集成测试
- [ ] 完成阶段一Code Review
- [ ] 输出：服务接入报告
```

#### Day 8-10: LLM服务接入

```yaml
任务：
1. 接入Ollama（开发环境）
2. 接入vLLM（生产环境）
3. 编写LLM客户端封装
4. 测试LLM调用

交付物：
- ✅ LLM客户端封装
- ✅ LLM调用测试报告
```

**Day 8-10 详细任务**：

```python
# Day 8: Ollama接入
Day 8：
Java侧：
- [ ] 配置Ollama连接
  rag:
    llm:
      provider: ollama
      base-url: ${OLLAMA_BASE_URL:http://localhost:11434}
      model: qwen2.5:7b
- [ ] 实现OllamaConfig配置类
- [ ] 使用RestTemplate/WebClient调用Ollama API
- [ ] 测试基本对话调用
- [ ] 测试流式输出

Python侧（可选）：
- [ ] 验证Ollama服务运行状态
- [ ] 测试模型加载
- [ ] 测试API调用

# Day 9: vLLM接入
Day 9：
Java侧：
- [ ] 配置vLLM连接
  rag:
    llm:
      provider: vllm
      base-url: ${VLLM_BASE_URL:http://localhost:8082}
      model: qwen2.5-7b-instruct
- [ ] 实现vLLM配置类（OpenAI兼容API）
- [ ] 实现LLM服务抽象接口
- [ ] 实现Provider切换逻辑（Ollama ↔ vLLM）
- [ ] 测试vLLM对话调用
- [ ] 测试流式输出

# Day 10: LLM客户端封装
Day 10：
- [ ] 实现LLMService统一接口
  public interface LLMService {
      String generate(String prompt);
      Flux<String> generateStream(String prompt);
      LLMProvider getProvider();
  }
- [ ] 实现OllamaLLMService
- [ ] 实现VLLMService
- [ ] 实现Provider自动选择
- [ ] 实现Prompt模板管理
- [ ] 编写LLM集成测试
- [ ] 完成阶段一Code Review
- [ ] 输出：LLM接入报告
```

**第一阶段交付物**：

```yaml
✅ 项目代码仓库（Spring Boot）
✅ 服务连接模块（Redis, Qdrant, etcd, MinIO, Ollama, vLLM）
✅ 统一健康检查API
✅ 服务连接测试报告
✅ 环境配置文档
```

---

### 第二阶段：核心功能开发（第11-50天）

#### Day 11-15: Embedding服务接入

```yaml
任务：
1. 接入PyTorch + BGE模型
2. 编写Embedding客户端
3. 测试Embedding调用
4. 性能测试

交付物：
- ✅ Embedding服务客户端
- ✅ 性能测试报告
```

**Day 11-15 详细任务**：

```python
# Day 11-12: Embedding服务准备（Python侧）
Day 11：
- [ ] 验证PyTorch + CUDA环境
  python -c "import torch; print(torch.cuda.is_available())"
- [ ] 验证BGE模型可用性
  from sentence_transformers import SentenceTransformer
  model = SentenceTransformer('BAAI/bge-large-zh-v1.5')
- [ ] 测试模型推理
- [ ] 测量推理延迟

Day 12：
- [ ] 创建FastAPI Embedding服务
- [ ] 实现单条嵌入接口
- [ ] 实现批量嵌入接口
- [ ] 性能优化（batch_size, GPU配置）

# Day 13-15: Java端接入
Day 13：
- [ ] 配置Embedding服务连接
  rag:
    embedding:
      url: ${EMBEDDING_SERVICE_URL:http://localhost:8081}
      model: bge-large-zh-v1.5
      dimension: 1024
- [ ] 实现EmbeddingService接口
- [ ] 实现EmbeddingClient
- [ ] 测试单条嵌入
- [ ] 测试批量嵌入

Day 14：
- [ ] 实现向量操作工具类
- [ ] 实现余弦相似度计算
- [ ] 实现向量归一化
- [ ] 编写Embedding服务测试用例

Day 15：
- [ ] 性能测试（延迟、吞吐量）
- [ ] 生成性能测试报告
- [ ] 完成Embedding服务接入验收
```

#### Day 16-25: 文档处理模块

```yaml
任务：
1. PaddleOCR接入
2. 文档解析器（PDF/Word/Markdown）
3. 文本预处理
4. 文档分块器

交付物：
✅ 文档解析服务
✅ 文本预处理服务
✅ 文档分块服务
```

**Day 16-25 详细任务**：

```java
// Day 16-17: PaddleOCR接入
Day 16：
- [ ] 验证PaddleOCR服务（Python）
  paddleocr --version
- [ ] 创建PaddleOCR FastAPI服务
- [ ] 实现文字识别接口
- [ ] 实现表格识别接口

Day 17：
- [ ] 配置PaddleOCR连接
  rag:
    ocr:
      url: ${PADDLEOCR_SERVICE_URL:http://localhost:8083}
- [ ] Java端实现OCRClient
- [ ] 测试OCR识别
- [ ] 测试表格识别

// Day 18-20: 文档解析器
Day 18：
- [ ] DocumentProcessor接口
- [ ] PDF解析器（Apache PDFBox）
- [ ] 表格提取（pdfplumber）
- [ ] 测试PDF解析

Day 19：
- [ ] Word解析器（Apache POI）
- [ ] Markdown解析器
- [ ] TXT解析器
- [ ] 测试各类文档解析

Day 20：
- [ ] 实现策略模式（自动选择解析器）
- [ ] 文档格式验证
- [ ] 单元测试编写

// Day 21-23: 文本预处理
Day 21：
- [ ] TextCleaner服务
- [ ] 编码检测（chardet）
- [ ] 格式标准化

Day 22：
- [ ] 噪声过滤（页眉页脚、水印）
- [ ] 特殊字符处理
- [ ] 文本分段和合并

Day 23：
- [ ] 单元测试编写
- [ ] 集成测试编写

// Day 24-25: 文档分块器
Day 24：
- [ ] ChunkingStrategy接口
- [ ] FixedSizeChunkingStrategy
- [ ] SemanticChunkingStrategy

Day 25：
- [ ] HybridChunkingStrategy
- [ ] 配置化分块参数
- [ ] 分块质量评估
- [ ] 文档处理完整流程测试
```

#### Day 26-35: RAG引擎开发

```yaml
任务：
1. Qdrant向量存储
2. 检索服务（语义/混合）
3. RAG编排器
4. Prompt管理

交付物：
✅ 向量存储服务
✅ 检索服务
✅ RAG编排器
```

**Day 26-35 详细任务**：

```java
// Day 26-28: 向量存储服务
Day 26：
- [ ] Qdrant向量操作封装
- [ ] Collection管理
- [ ] 向量插入

Day 27：
- [ ] 向量查询
- [ ] 向量更新
- [ ] 向量删除

Day 28：
- [ ] 批量操作优化
- [ ] 索引管理
- [ ] 存储监控

// Day 29-31: 检索服务
Day 29：
- [ ] SemanticSearchStrategy
- [ ] HybridSearchStrategy
- [ ] 配置化检索参数

Day 30：
- [ ] 查询改写（Query Expansion）
- [ ] 检索结果过滤
- [ ] 分页支持

Day 31：
- [ ] 检索评估指标
- [ ] 检索质量优化
- [ ] 检索性能优化

// Day 32-35: RAG编排器
Day 32：
- [ ] RAGOrchestrator核心逻辑
- [ ] Prompt模板管理
- [ ] 上下文构建

Day 33：
- [ ] 引用来源追踪
- [ ] 答案验证
- [ ] Hallucination检测

Day 34：
- [ ] 多轮对话支持
- [ ] 会话历史管理
- [ ] Redis缓存集成

Day 35：
- [ ] RAG完整流程测试
- [ ] 性能测试
- [ ] 生成RAG评估报告
```

#### Day 36-45: 用户权限和对话管理

```yaml
任务：
1. 用户认证（JWT）
2. RBAC权限模型
3. 对话管理
4. 审计日志

交付物：
✅ 用户权限系统
✅ 对话管理系统
✅ 审计日志系统
```

**Day 36-45 详细任务**：

```java
// Day 36-39: 用户认证
Day 36：
- [ ] User实体和Repository
- [ ] 用户注册接口
- [ ] 用户登录接口

Day 37：
- [ ] JWT Token生成
- [ ] Token验证
- [ ] Refresh Token机制

Day 38：
- [ ] Spring Security配置
- [ ] JWT过滤器
- [ ] 接口权限控制

Day 39：
- [ ] 单元测试编写
- [ ] 集成测试编写

// Day 40-42: RBAC权限
Day 40：
- [ ] Role实体和Permission实体
- [ ] 角色管理接口
- [ ] 权限分配接口

Day 41：
- [ ] @RequirePermission注解
- [ ] 权限拦截器
- [ ] 资源权限校验

Day 42：
- [ ] 权限测试
- [ ] 文档访问控制

// Day 43-45: 对话管理和审计
Day 43：
- [ ] Conversation实体
- [ ] Message实体
- [ ] 对话列表接口

Day 44：
- [ ] 对话详情接口
- [ ] 对话历史接口
- [ ] Redis会话缓存

Day 45：
- [ ] 审计日志实体
- [ ] 操作日志记录
- [ ] 日志查询接口
- [ ] 完成阶段二Code Review
```

#### Day 46-50: 配置管理和监控

```yaml
任务：
1. etcd配置管理
2. Prometheus监控
3. Grafana看板
4. 日志聚合

交付物：
✅ 配置管理系统
✅ 监控告警系统
✅ 日志系统
```

**Day 46-50 详细任务**：

```java
// Day 46-47: etcd配置管理
Day 46：
- [ ] 配置实体设计
- [ ] 配置CRUD接口
- [ ] 配置版本管理

Day 47：
- [ ] 配置变更监听
- [ ] 热更新机制
- [ ] 配置验证

// Day 48-49: Prometheus监控
Day 48：
- [ ] 添加Micrometer依赖
- [ ] 定义业务指标
- [ ] 配置Prometheus端点

Day 49：
- [ ] 自定义指标
  - API调用次数
  - 响应时间
  - 错误率
  - RAG检索延迟
  - LLM生成延迟
- [ ] Grafana看板配置

// Day 50: 日志和总结
Day 50：
- [ ] 日志框架配置（Logback）
- [ ] 结构化日志
- [ ] 日志聚合配置
- [ ] 完成阶段二Code Review
- [ ] 输出：阶段二交付报告
```

---

### 第三阶段：前端开发（第30-70天，与第二阶段并行）

#### Day 30-40: 前端项目搭建

```yaml
技术栈：Vue3 + Element Plus + TypeScript

任务：
1. 项目初始化
2. 路由和状态管理
3. API封装
4. 公共组件

交付物：
✅ 前端项目框架
✅ 公共组件库
```

**Day 30-40 详细任务**：

```javascript
// Day 30-33: 项目搭建
Day 30：
- [ ] 创建Vue3项目
- [ ] 配置TypeScript
- [ ] 配置Vite
- [ ] 配置ESLint + Prettier

Day 31：
- [ ] 安装Element Plus
- [ ] 配置按需加载
- [ ] 配置主题色

Day 32：
- [ ] 配置路由（Vue Router）
- [ ] 路由权限控制
- [ ] 动态路由

Day 33：
- [ ] 配置Pinia状态管理
- [ ] 创建Store模块

// Day 34-37: API封装
Day 34：
- [ ] 安装Axios
- [ ] 创建Axios实例
- [ ] 请求拦截器（Token）

Day 35：
- [ ] 响应拦截器
- [ ] 错误处理
- [ ] 重试机制

Day 36：
- [ ] API模块封装
  - userAPI
  - documentAPI
  - chatAPI
  - configAPI

Day 37：
- [ ] 接口类型定义
- [ ] Mock数据配置

// Day 38-40: 公共组件
Day 38：
- [ ] 布局组件（Header, Sidebar, Content）
- [ ] 响应式布局

Day 39：
- [ ] 公共业务组件
  - DocumentCard
  - ChatBubble
  - FileUploader

Day 40：
- [ ] 公共工具组件
  - Pagination
  - SearchBar
  - DatePicker
- [ ] 完成前端基础搭建
```

#### Day 41-55: 核心功能页面

```yaml
任务：
1. 文档管理页面
2. 问答对话页面
3. 用户管理页面
4. 配置管理页面

交付物：
✅ 文档管理页面
✅ 问答对话页面
✅ 用户管理页面
✅ 配置管理页面
```

**Day 41-55 详细任务**：

```javascript
// Day 41-45: 文档管理页面
Day 41：
- [ ] 文档列表页面
- [ ] 表格组件
- [ ] 分页组件

Day 42：
- [ ] 文档上传页面
- [ ] 拖拽上传
- [ ] 上传进度

Day 43：
- [ ] 文档详情页面
- [ ] 文档预览
- [ ] 元数据展示

Day 44：
- [ ] 文档编辑页面
- [ ] 文档删除功能
- [ ] 批量操作

Day 45：
- [ ] 文档搜索
- [ ] 文档分类
- [ ] 文档标签

// Day 46-50: 问答对话页面
Day 46：
- [ ] 对话列表页面
- [ ] 新建对话

Day 47：
- [ ] 聊天界面
- [ ] 消息展示
- [ ] 输入框组件

Day 48：
- [ ] 流式输出实现（SSE）
- [ ] 打字机效果

Day 49：
- [ ] 引用来源展示
- [ ] 答案反馈

Day 50：
- [ ] 对话历史
- [ ] 对话收藏
- [ ] 对话导出

// Day 51-55: 管理页面
Day 51-52：
- [ ] 用户管理页面
- [ ] 角色管理页面
- [ ] 权限配置页面

Day 53-54：
- [ ] 配置管理页面
- [ ] 分块参数配置
- [ ] 检索策略配置

Day 55：
- [ ] 监控看板页面
- [ ] 审计日志页面
- [ ] 完成核心页面开发
```

#### Day 56-70: 前端优化和测试

```yaml
任务：
1. 性能优化
2. 安全加固
3. E2E测试
4. 浏览器兼容性

交付物：
✅ 优化后的前端
✅ E2E测试用例
✅ 部署配置
```

**Day 56-70 详细任务**：

```javascript
// Day 56-60: 性能优化
Day 56：
- [ ] 路由懒加载
- [ ] 组件懒加载

Day 57：
- [ ] 图片懒加载
- [ ] 虚拟滚动（长列表）

Day 58：
- [ ] 接口缓存
- [ ] 本地缓存

Day 59：
- [ ] 代码分割
- [ ] Bundle优化

Day 60：
- [ ] 性能测试
- [ ] Lighthouse审计

// Day 61-65: 安全和测试
Day 61：
- [ ] XSS防护
- [ ] CSRF Token
- [ ] 敏感信息脱敏

Day 62：
- [ ] 搭建Playwright环境
- [ ] 编写登录测试

Day 63：
- [ ] 编写文档上传测试
- [ ] 编写问答对话测试

Day 64：
- [ ] 编写权限测试
- [ ] 执行E2E测试

Day 65：
- [ ] 浏览器兼容性测试
- [ ] 移动端适配测试

// Day 66-70: 部署准备
Day 66-67：
- [ ] 构建优化
- [ ] 环境配置

Day 68：
- [ ] Nginx配置
- [ ] 部署脚本

Day 69-70：
- [ ] 前端性能优化
- [ ] 完成前端开发
- [ ] 输出：前端交付报告
```

---

### 第四阶段：系统集成与测试（第51-85天）

#### Day 51-65: 集成测试

```yaml
任务：
1. API集成测试
2. E2E测试
3. 性能测试
4. 安全测试

交付物：
✅ 集成测试报告
✅ 性能测试报告
✅ 安全测试报告
```

**Day 51-65 详细任务**：

```java
// Day 51-55: API集成测试
Day 51：
- [ ] 文档上传→解析→分块→存储全流程
- [ ] 向量生成→Qdrant存储

Day 52：
- [ ] 检索→生成全流程
- [ ] 对话管理全流程

Day 53：
- [ ] 权限控制全流程
- [ ] 配置更新全流程

Day 54：
- [ ] 异常场景测试
- [ ] 边界条件测试

Day 55：
- [ ] 集成测试报告

// Day 56-60: E2E测试
Day 56：
- [ ] 搭建Cypress/Playwright
- [ ] 登录注册测试

Day 57：
- [ ] 文档上传测试
- [ ] 文档管理测试

Day 58：
- [ ] 问答对话测试
- [ ] 引用展示测试

Day 59：
- [ ] 权限管理测试
- [ ] 配置管理测试

Day 60：
- [ ] 执行E2E测试
- [ ] 生成E2E报告

// Day 61-65: 性能和安全测试
Day 61-62：
- [ ] JMeter配置
- [ ] 并发测试（100并发）
- [ ] 压力测试

Day 63-64：
- [ ] 渗透测试
- [ ] 安全漏洞修复
- [ ] 安全加固

Day 65：
- [ ] 生成性能测试报告
- [ ] 生成安全测试报告
```

#### Day 66-80: 问题修复和优化

```yaml
任务：
1. Bug修复
2. 性能优化
3. UAT测试

交付物：
✅ Bug修复清单
✅ 性能优化报告
✅ UAT测试报告
```

**Day 66-80 详细任务**：

```java
// Day 66-70: Bug修复
Day 66-67：
- [ ] 修复测试发现的问题
- [ ] 修复用户反馈的问题

Day 68-70：
- [ ] 性能优化
  - 数据库查询优化
  - 缓存优化
  - 异步处理优化

// Day 71-80: UAT测试
Day 71-75：
- [ ] UAT测试用例编写
- [ ] UAT测试执行
- [ ] 问题反馈和修复

Day 76-80：
- [ ] 小范围试运行
- [ ] 问题收集
- [ ] 最终优化
- [ ] 生成UAT报告
```

#### Day 81-85: 最终优化和文档

```yaml
任务：
1. 最终性能优化
2. 文档编写
3. 培训准备

交付物：
✅ 优化完成
✅ 完整文档
✅ 培训材料
```

**Day 81-85 详细任务**：

```java
Day 81-82：
- [ ] 最终性能调优
- [ ] 安全加固
- [ ] 代码审计

Day 83-84：
- [ ] 用户手册
- [ ] 运维手册
- [ ] 技术文档

Day 85：
- [ ] 培训PPT
- [ ] 演示视频
- [ ] 完成阶段四
- [ ] 输出：阶段四交付报告
```

---

### 第五阶段：部署上线（第86-90天）

#### Day 86-88: 生产环境部署

```yaml
任务：
1. 生产环境准备
2. 配置部署
3. 监控系统部署

交付物：
✅ 生产环境
✅ 监控系统
✅ 部署脚本
```

**Day 86-88 详细任务**：

```java
Day 86：
- [ ] 生产服务器准备
- [ ] 域名和SSL证书
- [ ] 配置生产环境参数

Day 87：
- [ ] 部署脚本编写
- [ ] 一键部署脚本
- [ ] 回滚脚本

Day 88：
- [ ] Prometheus部署
- [ ] Grafana部署
- [ ] 告警配置
```

#### Day 89-90: 上线和验收

```yaml
任务：
1. 正式上线
2. 验收测试
3. 项目交付

交付物：
✅ 上线报告
✅ 验收报告
✅ 完整交付
```

**Day 89-90 详细任务**：

```java
Day 89：
- [ ] 数据迁移
- [ ] 服务启动
- [ ] 健康检查
- [ ] 灰度发布

Day 90：
- [ ] 功能验收
- [ ] 性能验收
- [ ] 安全验收
- [ ] 项目正式上线
- [ ] 项目交付
```

---

## 三、里程碑

### 里程碑时间表

```
Day 10  ━━━━━━━ M1: 服务接入完成
        ✅ 所有服务连接正常
        ✅ 健康检查API可用

Day 25  ━━━━━━━ M2: 文档处理完成
        ✅ 文档解析器可用
        ✅ 文档分块器可用

Day 35  ━━━━━━━ M3: RAG引擎完成
        ✅ 向量存储可用
        ✅ 检索服务可用
        ✅ RAG问答可用

Day 50  ━━━━━━━ M4: 后端完成
        ✅ 用户权限系统
        ✅ 对话管理系统
        ✅ 监控系统

Day 70  ━━━━━━━ M5: 前端完成
        ✅ 所有页面开发
        ✅ E2E测试通过

Day 85  ━━━━━━━ M6: 测试完成
        ✅ 性能测试通过
        ✅ UAT测试通过

Day 90  ━━━━━━━ M7: 项目交付
        ✅ 正式上线
        ✅ 验收通过
```

---

## 四、交付物清单

### 代码交付物

```yaml
- [ ] Spring Boot后端源代码
- [ ] Embedding服务源代码
- [ ] OCR服务源代码（可选）
- [ ] Vue3前端源代码
- [ ] 数据库脚本
- [ ] Docker配置文件
```

### 文档交付物

```yaml
- [ ] 技术设计文档
- [ ] API接口文档（Swagger）
- [ ] 部署文档
- [ ] 用户手册
- [ ] 运维手册
- [ ] 测试报告
```

### 培训交付物

```yaml
- [ ] 培训PPT
- [ ] 演示视频
- [ ] FAQ文档
```

---

## 五、服务接入配置参考

### 5.1 服务连接配置模板

```yaml
# application-prod.yml

# Redis配置
spring:
  data:
    redis:
      host: ${REDIS_HOST:10.0.0.10}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      database: 0
      timeout: 5000ms
      lettuce:
        pool:
          max-active: 20
          max-idle: 10
          min-idle: 5

# Qdrant配置
rag:
  qdrant:
    host: ${QDRANT_HOST:10.0.0.11}
    port: ${QDRANT_PORT:6333}
    collection: document_embeddings
    vector-size: 1024
    distance: Cosine

# etcd配置
  etcd:
    endpoints: ${ETCD_HOST:10.0.0.12:2379}
    timeout: 5000

# MinIO配置
  minio:
    endpoint: ${MINIO_ENDPOINT:http://10.0.0.13:9000}
    access-key: ${MINIO_ACCESS_KEY:}
    secret-key: ${MINIO_SECRET_KEY:}
    bucket: rag-documents
    presigned-expiry: 3600

# LLM配置
  llm:
    provider: ${LLM_PROVIDER:vllm}  # ollama 或 vllm
    base-url: ${LLM_BASE_URL:http://10.0.0.14:8082}
    model: ${LLM_MODEL:qwen2.5-7b-instruct}
    temperature: 0.7
    max-tokens: 2048

# Embedding配置
  embedding:
    url: ${EMBEDDING_SERVICE_URL:http://10.0.0.15:8081}
    model: bge-large-zh-v1.5
    dimension: 1024
    batch-size: 32

# OCR配置
  ocr:
    url: ${PADDLEOCR_SERVICE_URL:http://10.0.0.16:8083}
    timeout: 30000
```

### 5.2 服务健康检查清单

```bash
#!/bin/bash
# health_check.sh

echo "=== 服务健康检查 ==="

# Redis
curl -s http://localhost:8080/health/redis | jq .

# Qdrant
curl -s http://localhost:6333/health | jq .

# etcd
curl -s http://localhost:2379/health | jq .

# MinIO
curl -s http://localhost:9000/minio/health/live | jq .

# Ollama
curl -s http://localhost:11434/api/tags | jq .

# vLLM
curl -s http://localhost:8082/health | jq .

# Embedding服务
curl -s http://localhost:8081/health | jq .

# PaddleOCR
curl -s http://localhost:8083/health | jq .

echo "=== 检查完成 ==="
```

---

## 六、成本预算（精简版）

### 90天总成本

```yaml
软件成本：¥0（全部使用已有服务）

人力成本（5人团队）：
- 90天 × 5人 × ¥30,000/月 = ¥450,000

总预算：约¥450,000
```

---

## 七、成功标准

### 功能标准

```yaml
必须功能：
- [ ] 用户注册登录
- [ ] 文档上传和处理
- [ ] RAG问答功能
- [ ] 权限控制
- [ ] 对话历史
```

### 性能标准

```yaml
响应时间：
- API响应 < 200ms（P95）
- 文档上传 < 5秒（10MB）
- 问答响应 < 10秒（首Token）

吞吐量：
- 支持50并发用户
- 支持100QPS检索
```

---

**计划制定完成** - 重点已调整为服务接入和功能开发。

---

*最后更新：2026-03-25*
