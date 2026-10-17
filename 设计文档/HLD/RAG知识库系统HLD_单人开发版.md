# RAG知识库系统高层设计文档（单人开发版）

> **文档版本**: V3.0  
> **编制日期**: 2026-03-25  
> **项目周期**: 61个工作日（2026-03-25 至 2026-06-25）  
> **开发人员**: 1人  
> **技术栈**: Spring Boot 3.x + Spring AI + Redis + Qdrant + Ollama/vLLM + MinIO + etcd + PaddleOCR  

---

## 目录

1. [项目概述](#一项目概述)
2. [技术架构设计](#二技术架构设计)
3. [AI链路可观测性设计](#三ai链路可观测性设计)
4. [功能架构设计](#四功能架构设计)
5. [模块详细设计](#五模块详细设计)
6. [服务接入配置](#六服务接入配置)
7. [实施计划](#七实施计划)
8. [验收标准](#八验收标准)
9. [成本估算](#九成本估算)

---

# 一、项目概述

## 1.1 项目背景

企业知识管理面临着文档分散、检索效率低，知识利用率不高等挑战。传统基于关键词的搜索已无法满足用户对语义理解和智能问答的需求。本项目旨在构建一个基于RAG（Retrieval-Augmented Generation）技术的智能知识库系统，通过自然语言处理和向量检索技术，实现文档的智能管理和智能问答。

## 1.2 项目目标

**核心目标**：
1. 实现多格式文档（PDF、Word、Markdown、TXT）的自动解析和向量化存储
2. 构建基于向量检索的智能问答系统，支持自然语言查询
3. 提供完善的用户权限管理和审计日志功能
4. 建立完整的AI链路可观测性体系，支撑系统性优化
5. 确保系统安全可靠，满足企业级应用需求

**量化指标**：
- 文档处理准确率 > 95%
- 问答响应时间 < 10秒（首Token < 3秒）
- 系统可用性 > 99%
- 支持50+并发用户
- AI链路追踪覆盖率 > 95%

## 1.3 实际情况

```yaml
开发资源：
- 开发人员：1人（全职）
- 工作时间：每天8小时（有效6-7小时）

已有基础设施（无需部署）：
- GPU服务器（CUDA已安装）
- Redis（端口待填写）
- Qdrant（端口待填写）
- etcd（端口待填写）
- MinIO（端口待填写）
- Ollama/vLLM（端口待填写）
- PaddleOCR（端口待填写）

本项目任务：服务接入 + 业务开发
```

## 1.4 实际工作日计算

**项目周期**：2026-03-25 至 2026-06-25（3个自然月）

### 工作日详细计算

| 月份 | 工作日 | 说明 |
|------|--------|------|
| 3月（剩余） | 3天 | 25、26、27 |
| 4月 | 21天 | 含清明节1天 |
| 5月 | 19天 | 含劳动节1天 |
| 6月（前25天） | 18天 | 含端午节1天 |
| **总计** | **61天** | |

### 单人开发工作量评估

```yaml
原计划6人61天 = 366人天

单人开发评估：
- 单人并行度低：需串行处理各模块
- 上下文切换成本：前端+后端+AI知识需切换
- 有效工作时间比：约65%（开会、文档、调试）
- 实际可用人天：61天 × 0.65 = 40人天

工作量缺口：366 - 40 = 326人天

解决方案：分阶段交付，优先核心功能
```

## 1.5 分阶段交付策略

### Phase 1：核心RAG（Day 1-40）
```yaml
目标：可运行的最小RAG系统

核心功能：
- 用户认证（基础）
- 文档上传和处理
- 向量检索
- 问答生成

非核心（移到Phase 2）：
- AI可观测性
- 高级权限
- 监控告警
- 成本追踪
```

### Phase 2：完善功能（Day 41-61）
```yaml
目标：完整的生产系统

补充功能：
- AI链路可观测性
- 监控告警
- 性能优化
- 系统集成
```

---

# 二、技术架构设计

## 2.1 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                      客户端层                                │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐ │
│  │  Web前端    │  │  API调用    │  │    移动端H5        │ │
│  │  (简化版)   │  │             │  │   (后续开发)       │ │
│  └─────────────┘  └─────────────┘  └─────────────────────┘ │
└────────────────────────────┬────────────────────────────────┘
                             │ HTTP/REST
┌────────────────────────────▼────────────────────────────────┐
│                      服务层（Spring Boot）                    │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────────────────┐│
│  │  文档管理   │ │   RAG引擎   │ │      用户权限           ││
│  │  服务       │ │   服务       │ │      服务               ││
│  └─────────────┘ └─────────────┘ └─────────────────────────┘│
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────────────────┐│
│  │  对话管理   │ │   配置管理   │ │      监控服务           ││
│  │  服务       │ │   服务       │ │      (Phase 2)        ││
│  └─────────────┘ └─────────────┘ └─────────────────────────┘│
└────────────────────────────┬────────────────────────────────┘
                             │
┌────────────────────────────▼────────────────────────────────┐
│                      AI服务层（已部署，端口待配置）              │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────────────────┐│
│  │  Embedding  │ │    LLM      │ │        OCR            ││
│  │  服务       │ │   服务       │ │       服务            ││
│  │  (BGE模型)  │ │(Ollama/vLLM)│ │    (PaddleOCR)       ││
│  └─────────────┘ └─────────────┘ └─────────────────────────┘│
└────────────────────────────┬────────────────────────────────┘
                             │
┌────────────────────────────▼────────────────────────────────┐
│                      数据存储层（已部署，端口待配置）              │
│  ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌─────────────┐ │
│  │ PostgreSQL│ │   Redis   │ │  Qdrant  │ │    MinIO    │ │
│  │  (关系数据)│ │  (缓存)   │ │  (向量)   │ │  (文件存储) │ │
│  └───────────┘ └───────────┘ └───────────┘ └─────────────┘ │
│  ┌───────────┐                                                 │
│  │   etcd    │                                                 │
│  │  (配置)    │                                                 │
│  └───────────┘                                                 │
└─────────────────────────────────────────────────────────────┘
```

## 2.2 技术选型

| 组件 | 技术选型 | 状态 | 端口 |
|------|----------|------|------|
| 后端框架 | Spring Boot 3.x + Spring AI | 需开发 | 8080（待定） |
| 向量数据库 | Qdrant | 已部署 | ________ |
| Embedding模型 | BGE-large-zh-v1.5 | 已部署 | ________ |
| LLM服务 | Ollama/vLLM | 已部署 | ________ |
| 文档存储 | MinIO | 已部署 | ________ |
| 配置中心 | etcd | 已部署 | ________ |
| 缓存 | Redis | 已部署 | ________ |
| 关系数据库 | PostgreSQL | 已部署 | ________ |
| OCR服务 | PaddleOCR | 已部署 | ________ |

---

# 三、AI链路可观测性设计

## 3.1 设计背景

AI链路可观测性是指对RAG系统中AI组件的输入、输出，处理过程和结果进行全面监控、追踪和分析的能力。

**核心价值**：
- 可观测：了解AI系统内部发生了什么
- 可诊断：快速定位问题根因
- 可优化：基于数据持续优化AI效果

## 3.2 五大可观测性维度

### 维度一：全链路追踪（Tracing）

```yaml
端到端Pipeline追踪：

Trace ID: abc123
├─ Embedding查询: 45ms
├─ 向量检索: 23ms
│  ├─ 语义检索: 18ms (5条结果)
│  └─ 关键词检索: 5ms (3条结果)
├─ 重排序: 67ms
├─ Prompt构建: 2ms
├─ LLM生成: 2680ms
│  ├─ 首次Token: 2100ms
│  └─ Token生成: 580ms (145 tokens)
└─ 答案验证: 150ms

总计: 2850ms
```

### 维度二：检索质量评估

| 指标 | 目标值 | 说明 |
|------|--------|------|
| Recall@5 | ≥ 0.75 | 召回率 |
| Precision@5 | ≥ 0.60 | 精确率 |
| MRR | ≥ 0.70 | 平均倒数排名 |
| 零结果率 | ≤ 5% | 无结果查询比例 |

### 维度三：生成质量评估

| 指标 | 目标值 | 说明 |
|------|--------|------|
| 质量评分 | ≥ 80分 | 综合质量 |
| 幻觉率 | ≤ 5% | 生成不准确内容比例 |
| 引用准确率 | ≥ 90% | 引用准确性 |

### 维度四：成本追踪

| 成本类型 | 计算方式 |
|----------|----------|
| GPU计算成本 | GPU时间 × GPU单价 |
| 存储成本 | 存储容量 × 单价 |
| 运维成本 | 人力分摊 |

### 维度五：调试与诊断

```yaml
问题分类：
1. 检索问题（无结果/不相关/重复/排序差）
2. 生成问题（幻觉/不完整/偏离主题）
3. 性能问题（响应延迟高）

诊断流程：
问题识别 → 初步分析 → 深度分析 → 制定方案 → 效果验证
```

---

# 四、功能架构设计

## 4.1 功能模块划分

### Phase 1核心功能（Day 1-40）

| 模块 | 功能 | 优先级 |
|------|------|----------|
| 用户权限模块 | 用户注册登录、JWT认证 | P0 |
| 文档管理模块 | 文档上传、格式解析、分块处理 | P0 |
| RAG引擎模块 | 向量检索、问答生成、流式输出 | P0 |
| 对话管理模块 | 会话管理、历史记录 | P1 |
| 配置管理模块 | 基础配置管理 | P1 |

### Phase 2完善功能（Day 41-61）

| 模块 | 功能 | 优先级 |
|------|------|----------|
| AI可观测性模块 | 链路追踪、质量评估、监控看板 | P1 |
| 监控告警模块 | Prometheus、Grafana、告警配置 | P2 |
| 权限完善模块 | RBAC权限、审计日志 | P2 |
| 性能优化模块 | 缓存优化、异步处理 | P2 |

## 4.2 核心业务流程

### 文档处理流程
```
用户上传文档 → 文件校验 → 存储原始文件 → 格式检测 → 文本提取 → 文本清洗 → 文档分块 → 向量化 → 向量存储
```

### 智能问答流程
```
用户输入问题 → 查询向量化 → 向量检索 → Prompt构建 → LLM生成 → 答案输出 → 保存对话
```

---

# 五、模块详细设计

## 5.1 用户权限模块

```java
// 核心类
public class User { }
public class AuthService { }
public class JwtTokenProvider { }
public class RBACService { }
```

**API接口**：
- POST /api/auth/register - 用户注册
- POST /api/auth/login - 用户登录
- GET /api/users - 用户列表（管理员）

## 5.2 文档处理模块

```java
// 文档处理器接口（策略模式）
public interface DocumentProcessor {
    boolean supports(String fileType);
    Document extractText(InputStream inputStream, String fileName);
}

// 实现类
@Component
public class PDFDocumentProcessor implements DocumentProcessor { }
@Component
public class WordDocumentProcessor implements DocumentProcessor { }
@Component
public class MarkdownProcessor implements DocumentProcessor { }

// 文档分块器
@Component
public class DocumentChunker { }
```

**API接口**：
- POST /api/documents/upload - 上传文档
- GET /api/documents - 文档列表
- GET /api/documents/{id}/chunks - 文档块列表
- POST /api/documents/{id}/process - 触发处理

## 5.3 RAG引擎模块

```java
// RAG编排器
@Service
public class RAGOrchestrator {
    public QueryResponse query(QueryRequest request) {
        // 1. 查询向量化
        float[] queryVector = embeddingService.embedQuery(request.getQuestion());
        
        // 2. 检索相关文档
        List<SearchResult> results = retrievalService.search(queryVector);
        
        // 3. 构建上下文
        String context = buildContext(results);
        
        // 4. 生成回答
        String answer = llmService.generate(buildPrompt(context, request.getQuestion()));
        
        return QueryResponse.builder()
            .answer(answer)
            .sources(results)
            .build();
    }
}
```

**API接口**：
- POST /api/rag/query - 问答查询
- POST /api/rag/query/stream - 流式问答
- GET /api/conversations - 对话列表
- GET /api/conversations/{id}/messages - 对话详情

---

# 六、服务接入配置

## 6.1 服务端口配置模板

```yaml
# application.yml 配置模板
# 请填写实际运行的端口和地址

# 数据库配置
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}

# Redis配置
  data:
    redis:
      host: ${REDIS_HOST}
      port: ${REDIS_PORT}

# Qdrant配置
rag:
  qdrant:
    host: ${QDRANT_HOST}
    port: ${QDRANT_PORT}

# etcd配置
  etcd:
    endpoints: ${ETCD_HOST}:${ETCD_PORT}

# MinIO配置
  minio:
    endpoint: ${MINIO_ENDPOINT}
    access-key: ${MINIO_ACCESS_KEY}
    secret-key: ${MINIO_SECRET_KEY}

# Embedding服务配置
  embedding:
    url: ${EMBEDDING_SERVICE_URL}

# LLM服务配置
  llm:
    provider: ${LLM_PROVIDER}
    base-url: ${LLM_BASE_URL}
    model: ${LLM_MODEL}

# OCR服务配置
  ocr:
    url: ${OCR_SERVICE_URL}
```

## 6.2 环境变量配置示例

```bash
# 部署环境变量配置
# 请根据实际服务地址填写

# ===========================================
# 数据库配置（PostgreSQL）
# ===========================================
export DB_HOST=10.0.0.10
export DB_PORT=5432
export DB_NAME=rag_knowledge_base
export DB_USERNAME=postgres
export DB_PASSWORD=your_password

# ===========================================
# Redis配置
# ===========================================
export REDIS_HOST=10.0.0.11
export REDIS_PORT=6379

# ===========================================
# Qdrant向量数据库配置
# ===========================================
export QDRANT_HOST=10.0.0.12
export QDRANT_PORT=6333

# ===========================================
# etcd配置中心配置
# ===========================================
export ETCD_HOST=10.0.0.13
export ETCD_PORT=2379

# ===========================================
# MinIO对象存储配置
# ===========================================
export MINIO_ENDPOINT=http://10.0.0.14:9000
export MINIO_ACCESS_KEY=your_access_key
export MINIO_SECRET_KEY=your_secret_key

# ===========================================
# Embedding模型服务配置
# ===========================================
export EMBEDDING_SERVICE_URL=http://10.0.0.15:8081

# ===========================================
# LLM服务配置
# ===========================================
export LLM_PROVIDER=ollama  # 或 vllm
export LLM_BASE_URL=http://10.0.0.16:11434
export LLM_MODEL=qwen2.5:7b

# ===========================================
# OCR服务配置
# ===========================================
export OCR_SERVICE_URL=http://10.0.0.17:8083

# ===========================================
# 应用配置
# ===========================================
export APP_HOST=0.0.0.0
export APP_PORT=8080
```

## 6.3 服务健康检查

```bash
#!/bin/bash
# 服务健康检查脚本

echo "=== RAG系统服务健康检查 ==="
echo ""

# PostgreSQL
echo "[1/8] PostgreSQL..."
curl -s http://${DB_HOST}:${DB_PORT}/health || echo "PostgreSQL未响应"
echo ""

# Redis
echo "[2/8] Redis..."
redis-cli -h ${REDIS_HOST} -p ${REDIS_PORT} ping || echo "Redis未响应"
echo ""

# Qdrant
echo "[3/8] Qdrant..."
curl -s http://${QDRANT_HOST}:${QDRANT_PORT}/health || echo "Qdrant未响应"
echo ""

# etcd
echo "[4/8] etcd..."
curl -s http://${ETCD_HOST}:${ETCD_PORT}/health || echo "etcd未响应"
echo ""

# MinIO
echo "[5/8] MinIO..."
curl -s http://${MINIO_HOST}:${MINIO_PORT}/minio/health/live || echo "MinIO未响应"
echo ""

# Embedding服务
echo "[6/8] Embedding服务..."
curl -s ${EMBEDDING_SERVICE_URL}/health || echo "Embedding服务未响应"
echo ""

# LLM服务
echo "[7/8] LLM服务..."
curl -s ${LLM_BASE_URL}/api/tags || echo "LLM服务未响应"
echo ""

# OCR服务
echo "[8/8] OCR服务..."
curl -s ${OCR_SERVICE_URL}/health || echo "OCR服务未响应"
echo ""

echo "=== 检查完成 ==="
```

---

# 七、实施计划

## 7.1 单人开发时间规划

### Phase 1：核心RAG（第1-40天）

| 周 | 日期范围 | 任务 | 交付物 |
|----|----------|------|--------|
| 1 | 03-25~03-27 | 项目初始化、配置接入 | 项目框架、服务连接 |
| 2 | 03-30~04-03 | 用户认证模块 | 登录注册API |
| 3 | 04-07~04-10 | 文档上传和解析 | PDF/Word解析器 |
| 4 | 04-13~04-17 | 文档分块和向量化 | 分块器、Embedding |
| 5 | 04-20~04-24 | RAG引擎开发 | 检索和生成API |
| 6 | 04-27~05-01 | 对话管理 | 会话API |
| 7 | 05-04~05-08 | 前端开发 | 简化UI |
| 8 | 05-11~05-15 | 集成测试 | Phase 1验收 |

### Phase 2：完善功能（第41-61天）

| 周 | 日期范围 | 任务 | 交付物 |
|----|----------|------|--------|
| 9 | 05-18~05-22 | AI可观测性基础 | 链路追踪 |
| 10 | 05-25~05-29 | 监控看板 | Grafana配置 |
| 11 | 06-01~06-05 | 性能优化 | 缓存、异步 |
| 12 | 06-08~06-12 | 系统集成 | 完整测试 |
| 13 | 06-15~06-19 | UAT测试 | 测试报告 |
| 14 | 06-22~06-25 | 上线和验收 | 项目交付 |

## 7.2 详细实施任务

### 第1周：项目初始化（Day 1-5）

```yaml
Day 1 (03-25, 三)：
- [ ] 创建Spring Boot项目结构
- [ ] 配置Git仓库
- [ ] 填写服务端口配置
- [ ] 测试服务连接

Day 2 (03-26, 四)：
- [ ] Redis连接测试
- [ ] PostgreSQL连接测试
- [ ] Qdrant连接测试

Day 3 (03-27, 五)：
- [ ] etcd连接测试
- [ ] MinIO连接测试
- [ ] Embedding服务连接测试

Day 4 (03-30, 一)：
- [ ] LLM服务连接测试（Ollama）
- [ ] vLLM连接测试
- [ ] 服务健康检查API

Day 5 (03-31, 二)：
- [ ] 第一周总结
- [ ] 文档编写
```

### 第2周：用户认证（Day 6-10）

```yaml
Day 6 (04-01, 三)：
- [ ] 用户注册接口
- [ ] 用户登录接口
- [ ] JWT Token生成

Day 7 (04-02, 四)：
- [ ] Token验证
- [ ] 简单权限检查
- [ ] 基础用户管理

Day 8-9 (04-03~04-07)：
- [ ] 用户模块测试
- [ ] 代码优化
- [ ] 文档编写

Day 10 (04-08, 三)：
- [ ] 第二周验收
```

### 第3-4周：文档处理（Day 11-18）

```yaml
Day 11-13 (04-09~04-14)：
- [ ] PDF解析器
- [ ] Word解析器
- [ ] Markdown解析器

Day 14-16 (04-15~04-17)：
- [ ] 文本清洗
- [ ] 文档分块器
- [ ] MinIO存储集成

Day 17-18 (04-20~04-21)：
- [ ] Embedding服务集成
- [ ] Qdrant存储
- [ ] 第三-四周验收
```

### 第5-6周：RAG引擎（Day 19-26）

```yaml
Day 19-21 (04-22~04-24)：
- [ ] 向量检索服务
- [ ] 语义检索实现
- [ ] 混合检索实现

Day 22-23 (04-27~04-28)：
- [ ] LLM服务集成
- [ ] Prompt模板

Day 24-25 (04-29~04-30)：
- [ ] RAG编排器
- [ ] 流式输出

Day 26 (05-01, 五)：
- [ ] 第五-六周验收
```

### 第7周：对话管理和前端（Day 27-31）

```yaml
Day 27-28 (05-04~05-05)：
- [ ] 会话管理API
- [ ] 历史记录

Day 29-31 (05-06~05-08)：
- [ ] 简化前端UI
- [ ] 文档上传页面
- [ ] 问答页面
```

### 第8周：Phase 1集成（Day 32-36）

```yaml
Day 32-33 (05-11~05-12)：
- [ ] API集成测试
- [ ] 端到端测试

Day 34-35 (05-13~05-14)：
- [ ] Bug修复
- [ ] 性能初步优化

Day 36 (05-15, 五)：
- [ ] Phase 1验收
```

### 第9-10周：AI可观测性（Day 37-46）

```yaml
Day 37-39 (05-18~05-20)：
- [ ] Tracing SDK设计
- [ ] 链路追踪实现

Day 40-42 (05-21~05-23)：
- [ ] 检索质量评估
- [ ] 生成质量评估

Day 43-46 (05-25~05-29)：
- [ ] Prometheus集成
- [ ] Grafana看板
```

### 第11-14周：收尾和上线（Day 47-61）

```yaml
Day 47-50 (06-01~06-05)：
- [ ] 性能优化
- [ ] 缓存优化

Day 51-54 (06-08~06-12)：
- [ ] 系统集成测试
- [ ] Bug修复

Day 55-58 (06-15~06-19)：
- [ ] UAT测试
- [ ] 问题修复

Day 59-61 (06-22~06-25)：
- [ ] 上线部署
- [ ] 项目验收
```

---

# 八、验收标准

## 8.1 Phase 1核心功能验收（Day 40）

**功能验收**：
- [ ] 用户注册登录功能正常
- [ ] JWT认证机制正常
- [ ] PDF/Word/Markdown文档处理正常
- [ ] 文档分块功能正常
- [ ] 向量检索功能正常
- [ ] 问答生成功能正常
- [ ] 流式输出功能正常
- [ ] 会话管理功能正常

**性能验收**：
- [ ] API响应时间 < 200ms（P95）
- [ ] 问答响应时间 < 10秒
- [ ] 支持10并发用户

## 8.2 Phase 2完善功能验收（Day 61）

**AI可观测性验收**：
- [ ] 链路追踪覆盖率 ≥ 95%
- [ ] Recall@5 ≥ 0.75
- [ ] 质量评分 ≥ 80分

**监控验收**：
- [ ] Prometheus指标采集正常
- [ ] Grafana看板可展示
- [ ] 告警配置完成（邮件）

**性能验收**：
- [ ] 支持50并发用户
- [ ] 系统可用性 > 99%

---

# 九、成本估算

## 9.1 开发成本

```yaml
单人开发成本：
- 开发时间：61天 × 8小时 = 488小时
- 人力成本：488小时 × ¥100/小时 = ¥48,800

其他成本：
- 培训学习：¥5,000
- 差旅：¥2,000
- 其他杂费：¥5,000

开发阶段总计：约¥60,800
```

## 9.2 运维成本（月度）

```yaml
服务器运维：
- 电费（已有服务器）：约¥200/月
- 网络带宽：约¥100/月

运维人力：
- 如需运维支持：约¥10,000/月

月度运维成本：约¥10,300/月
```

## 9.3 总成本估算

| 阶段 | 成本 |
|------|------|
| 开发阶段（61天） | ¥60,800 |
| 首月运维 | ¥10,300 |
| **总计** | **¥71,100** |

---

# 附录

## 术语表

| 术语 | 定义 |
|------|------|
| RAG | Retrieval-Augmented Generation，检索增强生成 |
| Embedding | 将文本转换为向量的技术 |
| LLM | Large Language Model，大语言模型 |
| Qdrant | 向量数据库 |
| RBAC | Role-Based Access Control，基于角色的访问控制 |
| JWT | JSON Web Token，认证令牌 |
| Hallucination | 幻觉，LLM生成不准确回答的现象 |

## 技术文档链接

- Spring Boot: https://spring.io/projects/spring-boot
- Spring AI: https://spring.io/projects/spring-ai
- Qdrant: https://qdrant.tech/documentation/
- vLLM: https://docs.vllm.ai/
- BGE Model: https://github.com/FlagOpen/FlagEmbedding
- OpenTelemetry: https://opentelemetry.io/

---

**文档版本**：V3.0
**编制日期**：2026-03-25
**开发人员**：单人开发

---

*RAG知识库系统高层设计文档编制完成*
