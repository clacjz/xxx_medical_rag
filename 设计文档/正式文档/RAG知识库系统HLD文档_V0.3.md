# RAG知识库系统完整高层设计文档

> **文档版本**: V0.3
> **编制日期**: 2026-03-27
> **项目周期**: 63个工作日（2026-03-25 至 2026-06-25）
> **技术栈**: Spring Boot 3.x + Spring AI + Redis + Qdrant + Ollama/vLLM + MinIO + etcd
> **最后更新**: 2026-05-14

> ⚠️ **实现状态说明 (2026-05-14)**
> - **OCR**: 使用GLM-OCR + Qwen3.6-27BB回退方案
> - **AI Trace ES存储**: **已实现** (AITracingService.saveToElasticsearchAsync)
> - **目录识别/表格识别**: 本版本未实现

***

## 目录

1. [项目概述](#一项目概述)
2. [技术架构设计](#二技术架构设计)
3. [AI链路可观测性设计](#三ai链路可观测性设计)
4. [增强型RBAC权限管理](#四增强型rbac权限管理)
5. [文档清洗质量标准](#五文档清洗质量标准)
6. [功能架构设计](#六功能架构设计)
7. [模块详细设计](#七模块详细设计)
8. [数据模型设计](#八数据模型设计)
9. [部署方案设计](#九部署方案设计)
10. [实施计划](#十实施计划)
11. [验收标准](#十一验收标准)
12. [风险评估](#十二风险评估)
13. [成本估算](#十三成本估算)

***

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

* 文档处理准确率 > 95%

* 问答响应时间 < 10秒（首Token < 3秒）

* 系统可用性 > 99%

* 支持50+并发用户

* AI链路追踪覆盖率 > 95%

## 1.3 实际工作日计算

**项目周期**：2026-03-25 至 2026-06-25（3个自然月）

### 工作日汇总

| 月份       | 工作日     | 假期          |
| -------- | ------- | ----------- |
| 3月（剩余）   | 3天      | 周末          |
| 4月       | 21天     | 清明节1天       |
| 5月       | 19天     | 劳动节1天、端午节1天 |
| 6月（前25天） | 18天     | 端午节1天       |
| **总计**   | **61天** | <br />      |

**建议Buffer**：5个工作日（应对节假日和突发事件）
**实际可用**：约56个工作日

## 1.4 资源约束

**开发资源**：
- 开发人员：1人（全职）
- 工作时间：每天8小时（有效6-7小时）

**已有基础设施**：
- GPU服务器（CUDA已安装）
- Redis（端口待配置）
- Qdrant（端口待配置）
- etcd（端口待配置）
- MinIO（端口待配置）
- Ollama/vLLM（端口待配置）
- PaddleOCR（端口待配置）

**技术约束**：
- 所有数据必须存储在客户内网，不允许使用云端AI服务
- 所有服务已部署，本项目仅需接入配置
- 需要建立完整的AI链路可观测性体系
- 告警通知仅支持邮件和钉钉

**时间约束**：
- 总工期：61个工作日
- 项目结束：2026-06-25
- 单人开发需分阶段交付，优先核心功能
- Phase 1（第1-40天）：核心RAG系统
- Phase 2（第41-61天）：AI可观测性和监控

***

# 二、技术架构设计

## 2.1 整体架构

```
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
│  │  文档管理   │ │   RAG引擎   │ │      用户权限           ││
│  │  服务       │ │   服务       │ │      服务               ││
│  └─────────────┘ └─────────────┘ └─────────────────────────┘│
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────────────────┐│
│  │  对话管理   │ │   配置管理   │ │       任务调度          ││
│  │  服务       │ │   服务       │ │       服务              ││
│  └─────────────┘ └─────────────┘ └─────────────────────────┘│
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────────────────┐│
│  │ AI可观测性  │ │   监控告警   │ │       质量评估          ││
│  │   服务       │ │   服务       │ │       服务              ││
│  └─────────────┘ └─────────────┘ └─────────────────────────┘│
└────────────────────────────┬────────────────────────────────┘
                             │
┌────────────────────────────▼────────────────────────────────┐
│                      AI服务层                               │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────────────────┐│
│  │  Embedding  │ │    LLM      │ │  OCR服务                ││
│  │  服务       │ │   服务       │ │  (GLM-OCR+Qwen回退)     ││
│  │  (BGE模型)  │ │(Ollama/vLLM)│ │                        ││
│  └─────────────┘ └─────────────┘ └─────────────────────────┘│
└────────────────────────────┬────────────────────────────────┘
                             │
┌────────────────────────────▼────────────────────────────────┐
│                      数据存储层                               │
│  ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌─────────────┐ │
│  │ PostgreSQL│ │   Redis   │ │  Qdrant  │ │    MinIO    │ │
│  │ (关系数据) │ │  (缓存)   │ │  (向量)   │ │  (文件存储) │ │
│  └───────────┘ └───────────┘ └───────────┘ └─────────────┘ │
│  ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌─────────────┐ │
│  │   etcd    │ │ Elasticsearch│ │ Prometheus│ │  Grafana   │ │
│  │  (配置)   │ │  (追踪数据) │ │  (指标)   │ │  (可视化)  │ │
│  └───────────┘ └───────────┘ └───────────┘ └─────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

## 2.2 技术选型说明

| 组件 | 技术选型 | 状态 | 端口 | 说明 |
|------|----------|------|------|------|
| 后端框架 | Spring Boot 3.x + Spring AI | 需开发 | 8080 | 业务开发 |
| 向量数据库 | Qdrant | **已部署** | **待填写** | 端口配置在第7章 |
| Embedding模型 | BGE-large-zh-v1.5 | **需部署** | **待填写** | 端口配置在第7章 |
| LLM服务 | Ollama/vLLM | **已部署** | **待填写** | 端口配置在第7章 |
| 文档存储 | MinIO | **已部署** | **待填写** | 端口配置在第7章 |
| 配置中心 | etcd | **已部署** | **待填写** | 端口配置在第7章 |
| AI可观测性 | OpenTelemetry + Elasticsearch | **已部署** | **待填写** | 端口配置在第7章 |
| 监控系统 | Prometheus + Grafana | **需部署** | **待填写** | 端口配置在第7章 |
| **OCR服务** | **GLM-OCR + Qwen3.6-27BB回退** | **已实现** | **已实现** | 详见OCR选型文档 |
| 关系数据库 | PostgreSQL | **已部署** | **待填写** | 端口配置在第7章 |
| 缓存 | Redis | **已部署** | **待填写** | 端口配置在第7章 |

***

# 三、AI链路可观测性设计

## 3.1 设计背景与目标

### 3.1.1 为什么需要AI链路可观测性？

传统系统监控主要关注：

* **基础设施指标**：CPU、内存、磁盘、网络

* **应用指标**：API响应时间、错误率、吞吐量

* **业务指标**：订单量、用户活跃度

但RAG系统的核心价值在于**AI能力**，系统指标只能告诉我们"系统是否健康"，却无法回答：

```
❌ 系统正常运行，但RAG效果很差
❌ API响应快，但LLM生成质量差
❌ 检索返回了结果，但相关性很低
❌ 用户反馈"回答不准确"，但不知道根因

需要回答的核心问题：
✅ "为什么这次问答效果不好？"
✅ "Embedding模型是否需要更新？"
✅ "Prompt调优是否有效果？"
✅ "不同检索策略哪个更好？"
✅ "LLM的幻觉问题有多严重？"
```

### 3.1.2 AI链路可观测性定义

**AI链路可观测性**是指对RAG系统中AI组件的输入、输出、处理过程和结果进行全面监控、追踪和分析的能力，目的是：

1. **可观测**：了解AI系统内部发生了什么
2. **可诊断**：快速定位问题根因
3. **可优化**：基于数据持续优化AI效果
4. **可信赖**：建立对AI系统的信任

### 3.1.3 设计目标

**核心目标**：

1. 全链路追踪：端到端追踪RAG Pipeline每个环节
2. 质量评估：量化评估检索质量和生成质量
3. 性能监控：监控AI组件的性能指标
4. 成本分析：追踪AI资源消耗和成本
5. 调试能力：支持问题查询和回放分析
6. 持续优化：支持A/B测试和Prompt迭代

**量化指标**：

* AI链路覆盖率：≥ 95%

* 问题诊断时间：< 5分钟

* 质量评估自动化：> 80%

* 监控数据保留：30天

## 3.2 整体架构

### 3.2.1 AI链路可观测性架构

```
┌────────────────────────────────────────────────────────────────────────┐
│                         AI链路可观测性平台                               │
├────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │                      数据采集层                                │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐   │   │
│  │  │ Tracing     │  │ Metrics    │  │     Logging        │   │   │
│  │  │ (链路追踪)   │  │ (指标采集)   │  │     (日志记录)      │   │   │
│  │  └─────────────┘  └─────────────┘  └─────────────────────┘   │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                              │                                      │
│                              ▼                                      │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │                      数据处理层                                │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐   │   │
│  │  │ 数据清洗    │  │ 数据聚合    │  │     数据存储        │   │   │
│  │  │             │  │             │  │   Elasticsearch    │   │   │
│  │  └─────────────┘  └─────────────┘  └─────────────────────┘   │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                              │                                      │
│                              ▼                                      │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │                      可视化分析层                               │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐   │   │
│  │  │ Grafana     │  │ 自定义看板   │  │     报表生成        │   │   │
│  │  │ 基础监控    │  │ AI质量看板   │  │   周报/月报        │   │   │
│  │  └─────────────┘  └─────────────┘  └─────────────────────┘   │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                         │
└────────────────────────────────────────────────────────────────────────┘
```

### 3.2.2 与传统监控的关系

```
三层监控体系：

┌─────────────────────────────────────────────────────────┐
│  第一层：基础设施监控（传统）                              │
│  - CPU、内存、磁盘、网络                                 │
│  - 服务健康检查                                          │
│  - 工具：Prometheus + Grafana                           │
├─────────────────────────────────────────────────────────┤
│  第二层：应用性能监控（APM）                              │
│  - API响应时间、错误率、吞吐量                           │
│  - 数据库查询性能                                        │
│  - 工具：Skywalking / Jaeger                            │
├─────────────────────────────────────────────────────────┤
│  第三层：AI链路可观测性（AI Observability）                │
│  - AI Pipeline追踪                                       │
│  - 检索质量评估                                          │
│  - 生成质量评估                                          │
│  - LLM调用分析                                          │
│  - 工具：OpenTelemetry + 自定义SDK                       │
└─────────────────────────────────────────────────────────┘
```

## 3.3 五大可观测性维度

### 3.3.1 维度一：全链路追踪（Tracing）

#### 端到端Pipeline追踪

一个完整的RAG请求追踪结构：

```
┌─────────────────────────────────────────────────────────────────┐
│ Trace ID: abc123                                               │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─ Span: rag_query                                            │
│  │  总耗时: 2850ms                                             │
│  │  ┌─ Span: embedding_query                                     │
│  │  │   耗时: 45ms                                             │
│  │  │   输入: "RAG系统架构设计"                                  │
│  │  │   输出: [0.123, -0.456, ...] (1024维)                   │
│  │  └─────────────────────────────────────────────────────────┘ │
│  │                                                             │
│  │  ┌─ Span: vector_search                                      │
│  │  │   耗时: 23ms                                             │
│  │  │   检索策略: hybrid                                        │
│  │  │   Top-K: 10                                              │
│  │  │   相似度: [0.89, 0.85, 0.82, 0.78, 0.75]           │
│  │  └─────────────────────────────────────────────────────────┘ │
│  │                                                             │
│  │  ┌─ Span: rerank                                            │
│  │  │   耗时: 67ms                                             │
│  │  │   输入: query + 8个候选文档                               │
│  │  │   输出: 5个重排序后的文档                                │
│  │  └─────────────────────────────────────────────────────────┘ │
│  │                                                             │
│  │  ┌─ Span: prompt构建                                        │
│  │  │   耗时: 2ms                                              │
│  │  │   Token统计:                                             │
│  │  │   - System: 200 tokens                                   │
│  │  │   - Context: 1500 tokens                                 │
│  │  │   - Query: 15 tokens                                     │
│  │  └─────────────────────────────────────────────────────────┘ │
│  │                                                             │
│  │  ┌─ Span: llm_generate                                      │
│  │  │   耗时: 2680ms                                           │
│  │  │   模型: qwen2.5-7b-instruct                               │
│  │  │   ┌─ Span: first_token                                  │
│  │  │   │   耗时: 2100ms  ← 首次响应时间                      │
│  │  │   └─────────────────────────────────────────────────────┘ │
│  │  │   ┌─ Span: token_generation                              │
│  │  │   │   耗时: 580ms                                       │
│  │  │   │   生成Token数: 145                                   │
│  │  │   └─────────────────────────────────────────────────────┘ │
│  │  └─────────────────────────────────────────────────────────┘ │
│  │                                                             │
│  │  ┌─ Span: answer_verification                               │
│  │  │   耗时: 150ms                                           │
│  │  │   幻觉检测: 通过                                          │
│  │  └─────────────────────────────────────────────────────────┘ │
│  │                                                             │
│  └─────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

#### 追踪数据模型

```java
// Trace主记录
@Data
@Builder
public class AITrace {
    private String traceId;              // 追踪ID
    private String userId;              // 用户ID
    private String sessionId;           // 会话ID
    private String query;               // 用户查询
    private String collectionId;        // 知识库ID
    
    private long startTime;             // 开始时间
    private long endTime;               // 结束时间
    private long duration;              // 总耗时(ms)
    
    private List<AISpan> spans;         // 链路节点
    private TraceStatus status;         // 状态：SUCCESS/FAILED/PARTIAL
    
    // 检索指标
    private double recallAt5;
    private double precisionAt5;
    
    // 生成指标
    private int qualityScore;
    private double hallucinationRate;
    private int promptTokens;
    private int completionTokens;
    
    // 成本
    private double cost;
}

// Span记录
@Data
@Builder
public class AISpan {
    private String spanId;              // Span ID
    private String parentSpanId;        // 父Span ID
    private String name;                 // Span名称
    
    private SpanType type;              // 类型
    private long startTime;
    private long duration;
    
    private Object input;               // 输入数据（脱敏）
    private Object output;              // 输出数据（脱敏）
    
    private Map<String, Object> attributes;  // 额外属性
    private SpanStatus status;
    private String errorMessage;        // 错误信息
}

// Span类型枚举
public enum SpanType {
    // 检索阶段
    EMBEDDING_QUERY,           // 查询向量化
    EMBEDDING_BATCH,          // 批量向量化
    VECTOR_SEARCH,             // 向量检索
    SEMANTIC_SEARCH,          // 语义检索
    KEYWORD_SEARCH,           // 关键词检索
    HYBRID_SEARCH,            // 混合检索
    RERANK,                   // 重排序
    
    // 生成阶段
    PROMPT_CONSTRUCTION,      // Prompt构建
    LLM_GENERATE,              // LLM生成
    STREAM_GENERATE,          // 流式生成
    ANSWER_VERIFICATION,       // 答案验证
    
    // 质量评估
    RETRIEVAL_EVALUATION,     // 检索质量评估
    GENERATION_EVALUATION,     // 生成质量评估
}
```

### 3.3.2 维度二：检索质量评估

#### 检索质量指标体系

| 指标类型 | 指标名称         | 定义              | 目标值    |
| ---- | ------------ | --------------- | ------ |
| 基础指标 | Recall\@K    | 前K个结果中包含相关文档的比例 | ≥ 0.75 |
| 基础指标 | Precision\@K | 前K个结果中相关文档的比例   | ≥ 0.60 |
| 基础指标 | MRR          | 首个相关文档排名的倒数平均值  | ≥ 0.70 |
| 基础指标 | NDCG\@K      | 考虑排名位置的加权得分     | ≥ 0.65 |
| 质量指标 | 平均相似度得分      | 检索结果与查询的平均余弦相似度 | ≥ 0.75 |
| 质量指标 | 零结果率         | 无相关结果的查询比例      | ≤ 5%   |
| 性能指标 | 检索延迟         | 向量检索平均延迟        | ≤ 50ms |

### 3.3.3 维度三：生成质量评估

#### 生成质量指标体系

| 指标类型 | 指标名称   | 定义                  | 目标值           |
| ---- | ------ | ------------------- | ------------- |
| 质量指标 | 质量评分   | 综合评估答案质量            | ≥ 80分         |
| 质量指标 | 完整性    | 回答是否完整覆盖问题          | ≥ 75分         |
| 质量指标 | 准确性    | 回答中的事实是否与参考文档一致     | ≥ 80分         |
| 质量指标 | 相关性    | 回答是否直接针对用户问题        | ≥ 80分         |
| 安全指标 | 幻觉率    | 生成内容中幻觉的比例          | ≤ 5%          |
| 安全指标 | 引用准确率  | 引用的文档是否被实际使用        | ≥ 90%         |
| 性能指标 | 首次响应时间 | Time to First Token | ≤ 3秒          |
| 性能指标 | 生成速度   | Tokens Per Second   | ≥ 50 tokens/s |

### 3.3.4 维度四：成本追踪

#### 4.1 成本分类模型

本地部署的成本追踪应该考虑完整的资源消耗：

| 成本类型        | 构成                  | 计算方式   | 说明        |
| ----------- | ------------------- | ------ | --------- |
| **GPU计算成本** | GPU时间 × GPU单价       | 按小时计费  | 本地部署主要成本  |
| **内存成本**    | 内存使用 × 时间 × 单价      | 按GB/小时 | 模型加载显存占用  |
| **存储成本**    | 向量存储 + 元数据存储 + 模型存储 | 按GB/月  | 持久化存储     |
| **网络成本**    | 内部通信流量              | 按GB计费  | 内网通信可忽略   |
| **运维成本**    | 人力成本分摊              | 按人天计费  | DevOps、监控 |

#### 4.2 GPU成本计算模型

```yaml
GPU成本计算公式：

单次推理成本 = GPU时间(秒) × GPU单价(元/秒)

其中：
- GPU时间 = 模型加载时间 + 推理时间
- GPU单价 = GPU折旧费/使用小时 + 电费/使用小时

示例计算：
假设RTX 4090 (24GB):
- 购买价格: ¥15,000
- 使用寿命: 3年 (每天8小时)
- 电费: ¥0.6/度 (GPU功耗450W)

折旧成本:
  ¥15,000 / (3年 × 365天 × 8小时) = ¥1.71/小时

电费成本:
  0.45度/小时 × ¥0.6 = ¥0.27/小时

总成本:
  ¥1.71 + ¥0.27 = ¥1.98/小时
```

#### 5.3 存储成本计算

```yaml
存储成本计算公式：

月度存储成本 = 存储容量(GB) × 单价(元/GB/月)

存储容量估算：
- Embedding向量: 文档数 × 平均Chunk数 × 向量维度 × 4字节
- 元数据: 文档数 × 平均Metadata大小
- 模型文件: 模型大小

示例计算（10GB文档规模）：
- 向量数据: 500万chunk × 1024维 × 4字节 = 20GB
- 元数据: 500万 × 1KB = 5GB
- 模型文件: BGE(1.3GB) + LLM(14GB) = 15GB
- 日志数据: 10GB/月

总存储: 50GB
存储成本: 50GB × ¥0.3/GB/月 = ¥15/月
```

#### 4.4 运维成本分摊

```yaml
运维成本包括：

1. AI可观测性运维
   - 监控系统维护: 0.5人 × 2月 = 1人月
   - 问题诊断和优化: 1人 × 2月 = 2人月
   
2. 模型运维
   - 模型更新和调优: 1人 × 1月 = 1人月
   - 性能优化: 1人 × 1月 = 1人月

总计: 5人月
月度分摊: ¥150,000 / 12月 = ¥12,500/月
```

#### 4.5 完整成本追踪指标

| 指标类型  | 指标名称    | 计算公式           | 目标值         |
| ----- | ------- | -------------- | ----------- |
| GPU成本 | 单次推理成本  | GPU时间 × GPU单价  | -           |
| GPU成本 | 日均GPU成本 | Σ(推理次数 × 单次成本) | ¥50/日       |
| 存储成本  | 存储使用量   | 向量 + 元数据 + 模型  | < 100GB     |
| 存储成本  | 月均存储成本  | 存储量 × 单价       | < ¥50/月     |
| 运维成本  | 运维分摊成本  | 运维人力 / 12      | < ¥12,500/月 |
| 综合成本  | 单次查询成本  | 总成本 / 查询数      | < ¥0.1/次    |
| 综合成本  | 质量调整后成本 | 单次成本 / 质量得分    | < ¥0.15/次   |

### 3.3.5 维度五：调试与诊断

#### 问题诊断框架

**问题分类**：

1. **检索问题**：无结果返回、结果不相关、结果重复、结果排序不合理
2. **生成问题**：幻觉严重、回答不完整、回答偏离主题、语言不通顺
3. **性能问题**：响应延迟高、Embedding慢、LLM生成慢

**诊断流程**：

1. 问题识别（用户反馈、自动告警、监控发现）
2. 初步分析（查看Trace、分析Span耗时、定位异常环节）
3. 深度分析（查看输入输出、对比相似案例、分析根因）
4. 制定方案（检索优化、Prompt调优、模型调整）
5. 效果验证（A/B测试、效果对比）

## 3.4 技术实现方案

### 3.4.1 数据采集架构

```
┌────────────────────────────────────────────────────────────────────────┐
│                        数据采集架构                                      │
├────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌────────────────┐     ┌────────────────┐     ┌────────────────┐        │
│  │ Spring Boot   │     │ Python Service │     │  LLM Service  │        │
│  │ Application    │     │                │     │               │        │
│  │                │     │                │     │               │        │
│  │ ┌────────────┐│     │ ┌────────────┐│     │ ┌────────────┐│        │
│  │ │ AI Tracing  ││     │ │ AI Tracing ││     │ │ AI Tracing ││        │
│  │ │  SDK        ││     │ │  SDK       ││     │ │  SDK       ││        │
│  │ └──────┬───────┘│     │ └──────┬─────┘│     │ └──────┬─────┘│        │
│  └────────┼─────────┘     └────────┼──────┘     └────────┼──────┘        │
│           │                        │                        │              │
│           └────────────────────────┼────────────────────────┘              │
│                                    │                                       │
│                                    ▼                                       │
│                         ┌────────────────┐                                │
│                         │  Data Collector │                                │
│                         │    (Kafka)      │                                │
│                         └────────┬───────┘                                │
│                                    │                                       │
│            ┌───────────────────────┼───────────────────────┐             │
│            │                       │                       │             │
│            ▼                       ▼                       ▼             │
│  ┌────────────────┐   ┌────────────────┐   ┌────────────────┐           │
│  │ Trace Storage  │   │ Metrics Store │   │  Log Storage  │           │
│  │(Elasticsearch)│   │  (Prometheus)  │   │(Elasticsearch)│           │
│  └────────────────┘   └────────────────┘   └────────────────┘           │
│                                                                         │
└────────────────────────────────────────────────────────────────────────┘
```

### 3.4.2 AI Tracing SDK实现

```java
// AI Tracing SDK
@Component
public class AITracingSDK {
    
    private final Tracer tracer;
    
    public AITracingSDK(Tracer tracer) {
        this.tracer = tracer;
    }
    
    /**
     * 开始一个AI Pipeline追踪
     */
    public TraceContext startPipeline(String query, String collectionId) {
        Span span = tracer.startSpan("rag_pipeline");
        
        span.setAttribute("query", query);
        span.setAttribute("collection_id", collectionId);
        span.setAttribute("start_time", System.currentTimeMillis());
        
        return new TraceContext(span);
    }
    
    /**
     * Embedding操作追踪
     */
    public void traceEmbedding(TraceContext ctx, String text, float[] embedding) {
        Span span = tracer.startSpan("embedding_query", ctx.getCurrentSpan());
        
        span.setAttribute("input_length", text.length());
        span.setAttribute("output_dimension", embedding.length);
        span.setAttribute("duration_ms", 45);
        span.setAttribute("status", "SUCCESS");
        
        span.end();
    }
    
    /**
     * 向量检索追踪
     */
    public void traceVectorSearch(TraceContext ctx, int topK, List<SearchResult> results) {
        Span span = tracer.startSpan("vector_search", ctx.getCurrentSpan());
        
        span.setAttribute("top_k", topK);
        span.setAttribute("results_count", results.size());
        span.setAttribute("avg_score", calculateAvgScore(results));
        span.setAttribute("duration_ms", 23);
        
        span.end();
    }
    
    /**
     * LLM生成追踪
     */
    public void traceLLMGenerate(TraceContext ctx, LLMResponse response) {
        Span span = tracer.startSpan("llm_generate", ctx.getCurrentSpan());
        
        span.setAttribute("prompt_tokens", response.getPromptTokens());
        span.setAttribute("completion_tokens", response.getCompletionTokens());
        span.setAttribute("first_token_latency_ms", response.getFirstTokenLatencyMs());
        span.setAttribute("total_latency_ms", response.getTotalLatencyMs());
        span.setAttribute("model", response.getModel());
        
        span.end();
    }
    
    /**
     * 完成Pipeline追踪
     */
    public void completePipeline(TraceContext ctx, String answer, boolean success) {
        ctx.getRootSpan()
            .setAttribute("end_time", System.currentTimeMillis())
            .setAttribute("success", success)
            .setAttribute("answer_length", answer.length())
            .setAttribute("status", success ? "SUCCESS" : "FAILED");
        
        ctx.getRootSpan().end();
    }
}
```

### 3.4.3 数据存储设计

```yaml
# AI Trace索引
indices:
  ai-trace:
    mappings:
      properties:
        traceId: keyword
        userId: keyword
        query: text
        answer: text
        
        startTime: date
        endTime: date
        duration: long
        
        status: keyword
        
        # 检索指标
        retrieval:
          properties:
            recallAt5: float
            precisionAt5: float
            mrr: float
            ndcgAt5: float
            resultsCount: integer
            avgSimilarityScore: float
            
        # 生成指标
        generation:
          properties:
            qualityScore: integer
            hallucinationRate: float
            firstTokenLatencyMs: long
            totalLatencyMs: long
            promptTokens: integer
            completionTokens: integer
            
        # Token使用
        tokens:
          properties:
            promptTokens: integer
            completionTokens: integer
            totalTokens: integer
```

### 3.4.4 Prometheus指标设计

```yaml
# AI Pipeline指标
ai_pipeline_requests_total:
  description: "AI Pipeline请求总数"
  labels: [status, collection_id]
  
ai_pipeline_duration_seconds:
  description: "AI Pipeline耗时分布"
  type: histogram
  buckets: [0.1, 0.5, 1, 2, 5, 10, 30]

# Embedding指标
embedding_requests_total:
  description: "Embedding请求总数"
  
embedding_duration_seconds:
  description: "Embedding耗时分布"
  type: histogram
  buckets: [0.01, 0.05, 0.1, 0.2, 0.5]

# LLM指标
llm_requests_total:
  description: "LLM请求总数"
  
llm_duration_seconds:
  description: "LLM生成耗时分布"
  type: histogram
  buckets: [0.5, 1, 2, 5, 10, 30]
  
llm_first_token_latency_seconds:
  description: "首次响应时间分布"
  type: histogram
  buckets: [0.5, 1, 2, 3, 5, 10]

# 检索质量指标
retrieval_recall_at_5:
  description: "Recall@5指标"
  type: gauge
  
retrieval_precision_at_5:
  description: "Precision@5指标"
  type: gauge

# 生成质量指标
generation_quality_score:
  description: "生成质量综合得分"
  type: gauge
  
generation_hallucination_rate:
  description: "幻觉率"
  type: gauge
```

## 3.5 Grafana看板设计

### 3.5.1 AI链路可观测性总览看板

**概览指标**：

* Pipeline请求数、成功率

* 平均响应时间

* 质量得分趋势

**趋势图**：

* Pipeline各阶段耗时占比（Stacked area chart）

* 检索质量趋势（Line chart）

* 生成质量趋势（Line chart）

**分析视图**：

* Token使用趋势（Bar chart）

* 成本趋势（Area chart）

* 问题分布（Pie chart）

* Top问题查询（Table）

***

# 四、增强型RBAC权限管理系统设计

> **重要更新**：本章节为新增设计，定义增强型RBAC权限管理系统架构，实现部门维度控制和文档级精细化权限管理。

## 4.1 设计背景与目标

### 4.1.1 设计背景

原系统采用简化RBAC权限控制，仅支持基础角色划分。随着企业知识管理的深入推进，需要更精细的权限管控能力。

### 4.1.2 设计目标

| 目标类型 | 目标描述 |
|---------|---------|
| 部门隔离 | 实现部门间知识隔离与共享 |
| 精细控制 | 文档级访问权限控制 |
| 权限可溯 | 完整的权限变更审计 |
| 双重保障 | 前后端双重权限校验 |

## 4.2 四级角色体系

| 角色 | 级别 | 权限范围 |
|------|------|---------|
| 系统管理员 | L1 | 全系统管理权限 |
| 部门管理员 | L2 | 本部门全部权限 |
| 部门文档上传者 | L3 | 文档上传+本部门权限 |
| 部门普通人员 | L4 | 基础访问权限 |

## 4.3 文档三级访问权限

| 权限级别 | 标识 | 访问范围 | 配置权限 |
|---------|------|---------|---------|
| 全公司可查看 | COMPANY_WIDE | 公司内所有认证用户 | 系统管理员 |
| 部门内公开 | DEPARTMENT_WIDE | 文档所属部门成员 | 上传者、部门管理员 |
| 指定人员开放 | RESTRICTED | 指定的特定用户（≤10人） | 上传者、部门管理员 |
| 部门内公开+外部受限 | DEPARTMENT_WIDE_RESTRICTED | 部门全员+其他部门最多10人 | 上传者、部门管理员 |

> **第四种权限类型 DEPARTMENT_WIDE_RESTRICTED**：适用于需要部门内完全共享，但对外严格限制的场景。部门成员可直接访问，其他部门需单独授权（≤10人）。

## 4.4 权限优先级规则

```
权限优先级（高→低）：
1. 系统管理员权限
2. 文档所有者权限
3. 显式指定权限
4. 部门管理员权限
5. 集合继承权限
6. 知识库继承权限
```

## 4.5 安全策略

### 4.5.1 最小权限原则

- 用户仅获得完成工作所需的最低权限
- 权限变更需经过审批流程
- 所有越权请求被拒绝

### 4.5.2 审计日志

| 操作类型 | 记录内容 |
|---------|---------|
| GRANT | 操作人、被授权人、权限类型 |
| REVOKE | 操作人、被授权人、原权限 |
| UPDATE | 操作人、变更前后对比 |

### 4.5.3 双重校验

- **前端**：路由守卫+UI权限指令
- **后端**：@PreAuthorize注解+服务层二次验证

### 4.5.4 动态配置

- 权限变更后立即生效
- 缓存TTL：5分钟
- 支持手动刷新权限缓存

## 4.6 权限验证流程

```
用户请求 → JWT验证 → 角色检查 → 所有者检查 → 显式权限检查 → 部门权限检查 → 返回结果
```

## 4.7 数据库扩展

新增表结构：

| 表名 | 说明 |
|------|------|
| departments | 部门表 |
| user_departments | 用户部门关系表 |
| document_permissions | 文档权限表 |
| document_user_permissions | 文档指定人员权限表 |
| permission_audit_logs | 权限变更审计表 |

详见：[增强RBAC权限管理系统设计.md](增强RBAC权限管理系统设计.md)

***

# 五、文档清洗质量标准与监控

## 5.1 设计背景

为确保RAG系统数据质量，必须建立完整的文档清洗质量标准。本章节定义文档清洗的质量指标、监控机制和可观测性评估体系。

## 6.2 质量标准体系

### 5.2.1 OCR识别质量标准

| 指标类型 | 指标名称 | 目标值 | 验收标准 | 监控方式 |
|---------|---------|--------|----------|----------|
| 字符准确率 | OCR字符识别准确率 | ≥95% | 置信度>0.85的字符占比 | 自动统计 |
| 置信度 | 平均置信度 | ≥0.90 | 低于阈值自动重试 | 实时监控 |
| 低质量比例 | OCR低质量页面比例 | ≤5% | 触发人工复核 | 告警通知 |

### 5.2.2 文档分块质量标准

| 指标类型 | 指标名称 | 目标值 | 验收标准 | 监控方式 |
|---------|---------|--------|----------|----------|
| 语义完整性 | Chunk语义完整性评分 | ≥0.90 | 段落不被不当分割 | 自动评估 |
| 长度分布 | Chunk长度分布合理性 | 90%在200-800字符 | 超出范围的chunk占比≤10% | 统计分布 |
| 重叠率 | 上下文重叠比例 | 5-10% | 符合配置的chunk重叠 | 参数校验 |
| 覆盖率 | 内容覆盖率 | ≥98% | chunk覆盖内容占原文比例 | 自动统计 |

### 5.2.4 向量化质量标准

| 指标类型 | 指标名称 | 目标值 | 验收标准 | 监控方式 |
|---------|---------|--------|----------|----------|
| 向量维度 | Embedding维度正确性 | 1024维 | 与BGE模型配置一致 | 自动校验 |
| 检索质量 | Recall@5 | ≥0.75 | 基于黄金样本集测试 | 定期评估 |
| 零结果率 | 无检索结果比例 | ≤5% | 超阈值触发告警 | 实时监控 |

## 5.3 质量监控机制

### 5.3.1 监控指标体系

```
┌─────────────────────────────────────────────────────────────┐
│                  文档清洗质量监控体系                         │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  实时监控（处理过程中）：                                      │
│  ├─ OCR置信度实时统计                                       │
│  ├─ 处理速度监控（页/秒）                                   │
│  ├─ GPU/CPU/内存使用率                                       │
│  └─ 异常中断计数                                             │
│                                                             │
│  定期评估（处理完成后）：                                     │
│  ├─ 抽样人工校验                                             │
│  └─ 检索质量测试                                             │
│                                                             │
│  告警通知（阈值触发时）：                                     │
│  ├─ 置信度低于阈值                                          │
│  ├─ 处理失败率超过5%                                        │
│  ├─ GPU显存不足                                             │
│  └─ 人工复核请求                                             │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 5.3.2 质量评估流程

1. **自动评估**：每处理完成一批文档，自动计算各项质量指标
2. **抽样校验**：对每批处理结果随机抽取5%进行人工校验
3. **问题追溯**：对低质量结果进行根因分析，记录问题类型
4. **持续优化**：基于问题分析优化处理流程和模型参数

### 5.3.3 可观测性指标

| 指标类别 | 具体指标 | 目标值 | 告警阈值 |
|----------|----------|--------|----------|
| **处理进度** | 已处理文件数/总数 | - | - |
| | 处理速度（页/分钟） | >30 | <10 |
| **OCR质量** | 平均置信度 | >0.90 | <0.85 |
| | 低置信度比例 | <5% | >15% |
| **系统资源** | GPU利用率 | >60% | <40% |
| | 系统内存使用 | <80% | >90% |
| | GPU温度 | <83°C | >88°C |

## 5.4 技术实现

### 5.4.1 质量监控SDK

```python
class DocumentQualityMonitor:
    """文档清洗质量监控器"""
    
    def record_ocr_result(self, page_id: str, confidence: float, 
                         character_count: int, error_count: int):
        """记录OCR结果"""
        metric = {
            "page_id": page_id,
            "confidence": confidence,
            "accuracy": 1 - error_count / character_count if character_count > 0 else 0,
            "timestamp": datetime.now().isoformat()
        }
        self.metrics_store.record("ocr_result", metric)
        
        # 阈值检查
        if confidence < self.confidence_threshold:
            self.alert_service.send(
                level="warning",
                message=f"低置信度OCR: page_id={page_id}, confidence={confidence}"
            )
    
    def get_quality_report(self, batch_id: str) -> dict:
        """生成质量报告"""
        ocr_metrics = self.metrics_store.aggregate(
            "ocr_result",
            filters={"batch_id": batch_id}
        )
        
        return {
            "batch_id": batch_id,
            "ocr": {
                "avg_confidence": ocr_metrics["avg_confidence"],
                "low_confidence_ratio": ocr_metrics["low_confidence_count"] / ocr_metrics["total"],
                "total_pages": ocr_metrics["total"]
            },
            "toc": {
                "avg_f1": toc_metrics["avg_f1"],
                "chapter_count": toc_metrics["total_chapters"],
                "pass_rate": toc_metrics["pass_count"] / toc_metrics["total"]
            },
            "timestamp": datetime.now().isoformat()
        }
```

## 5.5 与AI可观测性集成

文档清洗质量监控作为AI可观测性体系的重要组成部分，与RAG系统的可观测性设计保持一致：

- **链路追踪**：文档处理全流程Span记录
- **指标采集**：Prometheus指标导出
- **日志记录**：处理日志统一存储到ES
- **告警通知**：与现有告警机制集成

### 4.5.1 Prometheus指标定义

```yaml
# 文档清洗质量指标
document_ocr_confidence:
  description: "OCR平均置信度"
  type: gauge
  labels: [batch_id]

document_ocr_low_quality_ratio:
  description: "OCR低质量页面比例"
  type: gauge
  labels: [batch_id]

document_processing_pages_total:
  description: "已处理页数"
  type: counter

document_processing_duration_seconds:
  description: "文档处理耗时分布"
  type: histogram
  buckets: [0.1, 0.5, 1, 2, 5, 10]
```

### 4.5.2 Grafana看板

文档清洗质量监控看板包含：

1. **概览面板**：处理进度、处理速度、质量得分总览
2. **OCR质量面板**：置信度分布、低质量页面追踪
3. **系统资源面板**：GPU/CPU/内存使用率
4. **告警面板**：未处理告警列表

***

# 六、功能架构设计

## 6.1 功能模块划分

系统划分为8大模块：

| 模块       | 主要功能                     |
| -------- | ------------------------ |
| 用户权限模块   | 用户注册登录、JWT认证、RBAC权限、审计日志 |
| **文档清洗模块** | **文档OCR、文本清洗、分块处理** |
| 知识库管理模块  | 知识库创建、配置、权限、检索           |
| 智能问答模块   | 问答对话、上下文管理、引用追踪、历史管理     |
| AI可观测性模块 | 链路追踪、质量评估、成本分析、问题诊断      |
| 系统管理模块   | 配置管理、模型管理、统计、备份恢复        |
| 运维监控模块   | 健康检查、性能监控、日志管理、告警管理      |

## 5.2 核心业务流程

### 5.2.1 文档清洗流程（新增）

```
PDF文档 → 格式检测 → 类型分类 → [电子版]→ PyMuPDF提取 → 文本清洗 → 分块
                        ↓
                   [扫描版]→ GLM-OCR识别 → 文本清洗 → 分块

分块 → 向量化(BGE-M3) → 质量检查 → Qdrant存储

**文档类型说明**：
- MANUAL（说明书类）：强制不分块，整篇存储，必须OCR
- NORMAL（普通文档）：支持多种分块策略（fixed/paragraph/semantic）

**OCR回退机制**：NORMAL文档采用启发式触发，当文本量<200字符时自动启用OCR回退
```

### 5.2.2 智能问答流程

### 4.2.2 智能问答流程

```
用户输入问题 → 查询向量化 → 向量检索 → 结果重排序 → Prompt构建 → LLM生成 → 答案输出 → 保存对话
```

### 4.2.3 AI可观测性流程

```
AI调用 → Tracing SDK采集 → 数据预处理 → 质量评估 → 存储ES → 生成指标 → 告警通知 → 看板展示
```

***

# 七、模块详细设计

## 5.1 用户权限模块

**核心类**：User、Role、Permission实体，JwtTokenProvider、AuthService、RBACService

**安全配置**：Spring Security + JWT认证，RBAC权限控制

## 5.2 文档处理模块

**策略模式设计**：

```java
public interface DocumentProcessor {
    boolean supports(String fileType);
    Document extractText(InputStream inputStream, String fileName);
}

@Component
public class PDFDocumentProcessor implements DocumentProcessor { }
@Component
public class WordDocumentProcessor implements DocumentProcessor { }
@Component
public class MarkdownProcessor implements DocumentProcessor { }
```

**分块策略**（ChunkingStrategy）：

| 策略 | 说明 | 适用场景 |
|------|------|---------|
| semantic | 语义分块 | NORMAL文档，按段落/句子边界语义切分 |
| paragraph | 段落分块 | NORMAL文档，按段落自然切分 |
| fixed | 固定长度分块 | NORMAL文档，按字符数固定切分 |
| character | 字符级分块 | NORMAL文档，按字符数切分 |
| sentence | 句子级分块 | NORMAL文档，按句子边界切分 |
| none | 不分块 | MANUAL文档，整篇存储 |

## 5.3 RAG引擎模块

```java
public class RAGOrchestrator {
    public QueryResponse query(QueryRequest request) {
        // 1. 查询向量化
        float[] queryVector = embeddingService.embedQuery(request.getQuestion());
        
        // 2. 检索相关文档
        List<SearchResult> searchResults = retrievalService.search(
            request.getCollectionId(),
            queryVector,
            request.getSearchConfig()
        );
        
        // 3. 构建上下文
        String context = buildContext(searchResults);
        
        // 4. 生成回答
        String answer = llmService.generate(buildPrompt(request.getQuestion(), context));
        
        // 5. 追踪和评估
        traceAndEvaluate(traceId, searchResults, answer);
        
        // 6. 构建响应
        return QueryResponse.builder()
            .answer(answer)
            .sources(searchResults)
            .build();
    }
}
```

## 5.4 AI可观测性模块

```java
@Service
public class AIObservabilityService {
    
    @Autowired
    private AITracingSDK tracingSDK;
    
    @Autowired
    private MetricsService metricsService;
    
    @Autowired
    private EvaluationService evaluationService;
    
    /**
     * 追踪和评估AI Pipeline
     */
    public void traceAndEvaluate(String traceId, List<SearchResult> results, String answer) {
        // 1. 评估检索质量
        RetrievalMetrics retrievalMetrics = evaluationService.evaluateRetrieval(results);
        
        // 2. 评估生成质量
        GenerationMetrics generationMetrics = evaluationService.evaluateGeneration(answer);
        
        // 3. 更新指标
        metricsService.recordRetrievalMetrics(retrievalMetrics);
        metricsService.recordGenerationMetrics(generationMetrics);
        
        // 4. 检查告警条件
        checkAlerts(retrievalMetrics, generationMetrics);
    }
}
```

***

# 八、数据模型设计

## 6.1 PostgreSQL核心表

```sql
-- 用户表、角色表、权限表、知识库表、文档表、文档块表、对话表、消息表、审计日志表、系统配置表、AI追踪表

-- 文档-知识库多对多关系表
CREATE TABLE collection_documents (
    collection_id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (collection_id, document_id),
    FOREIGN KEY (collection_id) REFERENCES collections(id),
    FOREIGN KEY (document_id) REFERENCES documents(id)
);
```

## 6.2 Qdrant Collection配置

```yaml
collection_name: document_embeddings
vector_config:
  size: 1024
  distance: Cosine
```

## 6.3 Redis缓存设计

```yaml
# 会话缓存、Token黑名单、Embedding缓存、限流控制
session:{session_id}
jwt:blacklist:{token_id}
embedding:cache:{text_hash}
rate_limit:{user_id}:{endpoint}
```

***

# 九、服务接入配置

## 7.1 服务端口配置模板

所有服务已部署，端口和地址由运维人员填写：

```yaml
# application.yml 配置模板

spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  
  data:
    redis:
      host: ${REDIS_HOST}
      port: ${REDIS_PORT}

rag:
  qdrant:
    host: ${QDRANT_HOST}
    port: ${QDRANT_PORT}
  
  etcd:
    endpoints: ${ETCD_HOST}:${ETCD_PORT}
  
  minio:
    endpoint: ${MINIO_ENDPOINT}
    access-key: ${MINIO_ACCESS_KEY}
    secret-key: ${MINIO_SECRET_KEY}
  
  embedding:
    url: ${EMBEDDING_SERVICE_URL}
  
  llm:
    provider: ${LLM_PROVIDER}
    base-url: ${LLM_BASE_URL}
    model: ${LLM_MODEL}
  
  ocr:
    url: ${OCR_SERVICE_URL}
```

## 7.2 环境变量配置示例

```bash
# 数据库配置
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=rag_knowledge_base
export DB_USERNAME=postgres
export DB_PASSWORD=password

# Redis配置
export REDIS_HOST=localhost
export REDIS_PORT=6379

# Qdrant配置
export QDRANT_HOST=localhost
export QDRANT_PORT=6333

# etcd配置
export ETCD_HOST=localhost
export ETCD_PORT=2379

# MinIO配置
export MINIO_ENDPOINT=http://localhost:9000
export MINIO_ACCESS_KEY=minioadmin
export MINIO_SECRET_KEY=minioadmin

# Embedding服务配置
export EMBEDDING_SERVICE_URL=http://localhost:8081

# LLM服务配置
export LLM_PROVIDER=ollama
export LLM_BASE_URL=http://localhost:11434
export LLM_MODEL=qwen2.5:7b

# OCR服务配置 (GLM-OCR)
export OCR_SERVICE_URL=http://localhost:38247
```

## 7.3 服务健康检查

```bash
#!/bin/bash
echo "=== RAG系统服务健康检查 ==="

echo "[1/8] PostgreSQL..."
curl -s http://${DB_HOST}:${DB_PORT}/health || echo "未响应"

echo "[2/8] Redis..."
redis-cli -h ${REDIS_HOST} -p ${REDIS_PORT} ping || echo "未响应"

echo "[3/8] Qdrant..."
curl -s http://${QDRANT_HOST}:${QDRANT_PORT}/health || echo "未响应"

echo "[4/8] etcd..."
curl -s http://${ETCD_HOST}:${ETCD_PORT}/health || echo "未响应"

echo "[5/8] MinIO..."
curl -s http://${MINIO_HOST}:${MINIO_PORT}/minio/health/live || echo "未响应"

echo "[6/8] Embedding服务..."
curl -s ${EMBEDDING_SERVICE_URL}/health || echo "未响应"

echo "[7/8] LLM服务..."
curl -s ${LLM_BASE_URL}/api/tags || echo "未响应"

echo "[8/8] OCR服务..."
curl -s ${OCR_SERVICE_URL}/health || echo "未响应"

echo "=== 检查完成 ==="
```

## 八、实施计划（单人开发61个工作日）

## 8.1 里程碑规划

| 里程碑 | 内容       | 日期         | 工作日 | 验收标准               |
| --- | -------- | ---------- | ------ | ------------------ |
| M1 | 基础框架完成 | 2026-04-04 | Day 8 | 服务接入完成，健康检查可用 |
| M2 | 核心功能完成 | 2026-05-02 | Day 29 | OCR文档处理、RAG引擎、用户权限可用 |
| M3 | AI可观测性完成 | 2026-05-16 | Day 38 | 链路追踪、质量评估、监控看板可用 |
| M4 | 系统集成完成 | 2026-05-30 | Day 49 | 前端、API集成完成，E2E测试通过 |
| M5 | UAT测试完成 | 2026-06-13 | Day 58 | 功能验收、性能验收通过 |
| M6 | 项目上线 | 2026-06-25 | Day 61 | 正式上线，用户培训完成 |

> **整合说明**：OCR/文档清洗作为核心功能模块（文档处理子模块）的一部分，与用户权限、RAG引擎并行开发，不作为独立阶段。

## 8.2 详细实施计划

> **阶段说明**：
> - OCR/文档处理是核心功能模块（文档处理子模块）的核心组成部分
> - 与用户权限开发并行进行，充分利用等待时间
> - 前端开发与后端核心功能并行，最大化效率

### 第一阶段：基础架构（第1-8天）

| 日期 | Day | 星期 | 任务 | 负责人 | 备注 |
| --- | --- | --- | --- | --- | --- |
| 2026-03-25 | 1 | 三 | 项目初始化、Git仓库 | 单人 | |
| 2026-03-26 | 2 | 四 | Redis/Qdrant接入 | 单人 | |
| 2026-03-27 | 3 | 五 | etcd/MinIO接入 | 单人 | |
| 2026-03-30 | 4 | 一 | Ollama/vLLM接入 | 单人 | |
| 2026-03-31 | 5 | 二 | Embedding服务接入 | 单人 | |
| 2026-04-01 | 6 | 三 | OCR服务接入 | 单人 | OCR服务配置 |
| 2026-04-03 | 8 | 五 | **M1里程碑验收** | 单人 | |

### 第二阶段：核心功能（第9-29天）

> **核心功能模块包含**：用户权限、OCR文档处理、RAG引擎、对话管理

| 日期 | Day | 星期 | 任务 | 负责人 | 备注 |
| --- | --- | --- | --- | --- | --- |
| 2026-04-07 | 9 | 一 | JWT认证 | 单人 | 用户权限-认证 |
| 2026-04-08 | 10 | 二 | 简化权限控制 | 单人 | 用户权限-权限 |
| 2026-04-09 | 11 | 三 | 审计日志 | 单人 | 用户权限-审计 |
| 2026-04-10 | 12 | 四 | 用户管理API | 单人 | 用户权限-API |
| 2026-04-11 | 13 | 五 | **OCR文档处理阶段开始** | 单人 | 核心模块-文档处理 |
| 2026-04-14 | 14 | 一 | OCR服务集成 | 单人 | OCR-服务集成 |
| 2026-04-15 | 15 | 二 | 文档解析与分块 | 单人 | OCR-解析分块 |
| 2026-04-16 | 16 | 三 | 语义分块处理 | 单人 | OCR-分块 |
| 2026-04-17 | 17 | 四 | 向量化+入库 | 单人 | OCR-向量化 |
| 2026-04-18 | 18 | 五 | 全量文档处理 | 单人 | OCR-批处理 |
| 2026-04-21 | 19 | 一 | 全量处理持续 | 单人 | OCR-批处理 |
| 2026-04-22 | 20 | 二 | 全量处理收尾 | 单人 | OCR-批处理 |
| 2026-04-23 | 21 | 三 | 检索服务 | 单人 | RAG引擎 |
| 2026-04-24 | 22 | 四 | RAG编排器 | 单人 | RAG引擎 |
| 2026-04-25 | 23 | 五 | LLM集成 | 单人 | RAG引擎 |
| 2026-04-28 | 24 | 一 | 对话管理 | 单人 | 对话 |
| 2026-04-29 | 25 | 二 | 配置管理 | 单人 | 系统 |
| 2026-05-02 | 26 | 五 | **M2里程碑验收** | 单人 | |

### 第三阶段：AI可观测性（第30-38天）

| 日期 | Day | 星期 | 任务 | 负责人 | 备注 |
| --- | --- | --- | --- | --- | --- |
| 2026-05-04 | 27 | 一 | Tracing SDK设计 | 单人 | |
| 2026-05-05 | 28 | 二 | Tracing SDK实现 | 单人 | |
| 2026-05-06 | 29 | 三 | 检索质量评估 | 单人 | |
| 2026-05-07 | 30 | 四 | 生成质量评估 | 单人 | |
| 2026-05-08 | 31 | 五 | Prometheus集成 | 单人 | |
| 2026-05-11 | 32 | 一 | Grafana看板-1 | 单人 | |
| 2026-05-12 | 33 | 二 | Grafana看板-2 | 单人 | |
| 2026-05-13 | 34 | 三 | 告警配置 | 单人 | |
| 2026-05-16 | 35 | 五 | **M3里程碑验收** | 单人 | |

### 第四阶段：系统集成（第36-49天）

| 日期 | Day | 星期 | 任务 | 负责人 | 备注 |
| --- | --- | --- | --- | --- | --- |
| 2026-05-18 | 36 | 一 | 简化前端UI | 单人 | 前端 |
| 2026-05-19 | 37 | 二 | 文档管理页面 | 单人 | 前端 |
| 2026-05-20 | 38 | 三 | 问答页面 | 单人 | 前端 |
| 2026-05-21 | 39 | 四 | 用户管理页面 | 单人 | 前端 |
| 2026-05-22 | 40 | 五 | API集成测试 | 单人 | |
| 2026-05-26 | 41 | 一 | 端到端测试 | 单人 | |
| 2026-05-27 | 42 | 二 | Bug修复 | 单人 | |
| 2026-05-28 | 43 | 三 | 性能测试 | 单人 | |
| 2026-05-29 | 44 | 四 | 安全测试 | 单人 | |
| 2026-05-30 | 45 | 五 | **M4里程碑验收** | 单人 | |

### 第五阶段：UAT与上线（第50-61天）

| 日期 | Day | 星期 | 任务 | 负责人 | 备注 |
| --- | --- | --- | --- | --- | --- |
| 2026-06-02 | 46 | 一 | UAT测试执行 | 单人 | |
| 2026-06-03 | 47 | 二 | UAT问题修复 | 单人 | |
| 2026-06-04 | 48 | 三 | 系统优化 | 单人 | |
| 2026-06-05 | 49 | 四 | 预发布测试 | 单人 | |
| 2026-06-09 | 50 | 一 | Bug修复 | 单人 | |
| 2026-06-10 | 51 | 二 | **M5里程碑验收** | 单人 | |
| 2026-06-11 | 52 | 三 | 数据迁移 | 单人 | |
| 2026-06-12 | 53 | 四 | 正式上线 | 单人 | |
| 2026-06-15 | 54 | 五 | 用户培训 | 单人 | |
| 2026-06-16 | 55 | 六 | 培训答疑 | 单人 | |
| 2026-06-17 | 56 | 日 | 项目验收准备 | 单人 | |
| 2026-06-18 | 57 | 一 | 功能验收 | 单人 | |
| 2026-06-19 | 58 | 二 | 性能验收 | 单人 | |
| 2026-06-22 | 59 | 三 | 安全验收 | 单人 | |
| 2026-06-23 | 60 | 四 | 文档整理 | 单人 | |
| 2026-06-24 | 61 | 五 | **项目正式结束** | 单人 | |

***

# 十一、验收标准

## 9.1 功能验收标准

**用户权限模块**：

* [ ] 用户注册登录功能正常

* [ ] JWT认证机制正常

* [ ] RBAC权限控制正常

* [ ] 审计日志记录正常

**文档管理模块**：

* [ ] PDF/Word/Markdown/TXT文档处理正常

* [ ] 文档分块功能正常

* [ ] 文档状态跟踪正常

**RAG问答模块**：

* [ ] 语义检索功能正常

* [ ] 问答生成功能正常

* [ ] 流式输出功能正常

* [ ] 引用来源展示正常

## 9.2 AI可观测性验收标准

**追踪覆盖率**：

* [ ] AI链路追踪覆盖率 ≥ 95%

* [ ] 所有RAG Pipeline环节都有Span记录

* [ ] 错误请求100%被追踪

**检索质量指标**：

* [ ] Recall\@5 ≥ 0.75

* [ ] Precision\@5 ≥ 0.60

* [ ] MRR ≥ 0.70

* [ ] 零结果率 ≤ 5%

**生成质量指标**：

* [ ] 质量评分 ≥ 80分

* [ ] 幻觉率 ≤ 5%

* [ ] 引用准确率 ≥ 90%

**性能指标**：

* [ ] Embedding延迟 ≤ 100ms（P95）

* [ ] 检索延迟 ≤ 50ms（P95）

* [ ] LLM首次响应时间 ≤ 3秒（P95）

* [ ] Pipeline总延迟 ≤ 10秒（P95）

**监控看板**：

* [ ] AI链路可观测性总览看板完成

* [ ] 检索质量监控看板完成

* [ ] 生成质量监控看板完成

## 9.3 性能验收标准

| 指标             | 目标值     | 测试方法       |
| -------------- | ------- | ---------- |
| API响应时间（P95）   | < 200ms | JMeter并发测试 |
| 问答响应时间（首Token） | < 3秒    | 端到端测试      |
| 问答响应时间（完成）     | < 10秒   | 端到端测试      |
| 并发用户数          | ≥ 50    | 50并发压力测试   |
| API QPS        | ≥ 100   | 压力测试       |

## 9.4 安全验收标准

* [ ] 用户密码加密存储（BCrypt）

* [ ] JWT Token验证正常

* [ ] SQL注入防护有效

* [ ] XSS防护有效

* [ ] API限流生效

* [ ] 告警通知（邮件、钉钉）生效

## 9.5 稳定性验收标准

| 指标     | 目标值    |
| ------ | ------ |
| 系统可用性  | ≥ 99%  |
| 故障恢复时间 | ≤ 30分钟 |
| 数据丢失率  | 0%     |
| API错误率 | < 0.1% |

***

# 十二、风险评估

## 10.1 主要技术风险

| 风险ID  | 风险描述       | 概率 | 影响 | 优先级 | 缓解措施             |
| ----- | ---------- | -- | -- | --- | ---------------- |
| R-001 | LLM推理性能不达标 | 高  | 高  | P0  | 预留Buffer，准备多方案   |
| R-002 | 文档处理质量不达标  | 中  | 中  | P1  | 集成PaddleOCR      |
| R-003 | AI可观测性实施困难 | 中  | 中  | P1  | 分阶段实施            |
| R-004 | 性能瓶颈       | 中  | 中  | P1  | Redis缓存，预留Buffer |

## 10.2 项目管理风险

| 风险ID  | 风险描述  | 概率 | 影响 | 缓解措施       |
| ----- | ----- | -- | -- | ---------- |
| R-005 | 时间延期  | 中  | 高  | 预留5天Buffer |
| R-006 | 需求变更  | 中  | 中  | 严格变更管理     |
| R-007 | 节假日影响 | 高  | 中  | 工作日精确计算    |

***

# 附录

## 术语表

| 术语            | 定义                                     |
| ------------- | -------------------------------------- |
| RAG           | Retrieval-Augmented Generation，检索增强生成  |
| Embedding     | 将文本转换为向量的技术                            |
| Vector        | 向量，用于表示文本语义                            |
| Chunk         | 文档分块，将长文档切分成小块                         |
| LLM           | Large Language Model，大语言模型             |
| Ollama        | LLM本地部署工具                              |
| vLLM          | 高性能LLM推理框架                             |
| BGE           | BAAI General Embedding，智源开源Embedding模型 |
| Qdrant        | 向量数据库                                  |
| RBAC          | Role-Based Access Control，基于角色的访问控制    |
| JWT           | JSON Web Token，认证令牌                    |
| SSE           | Server-Sent Events，服务端推送技术             |
| Rerank        | 重排序，优化检索结果顺序                           |
| Hallucination | 幻觉，LLM生成不准确回答的现象                       |

## 技术文档链接

* Spring Boot: <https://spring.io/projects/spring-boot>

* Spring AI: <https://spring.io/projects/spring-ai>

* Qdrant: <https://qdrant.tech/documentation/>

* vLLM: <https://docs.vllm.ai/>

* Ollama: <https://github.com/ollama/ollama>

* BGE Model: <https://github.com/FlagOpen/FlagEmbedding>

* PaddleOCR: <https://github.com/PaddlePaddle/PaddleOCR>

* OpenTelemetry: <https://opentelemetry.io/>

***

**文档版本**：V0.3
**编制日期**：2026-03-25
**编制人**：RAG知识库系统项目组

***

*RAG知识库系统完整高层设计文档编制完成*
