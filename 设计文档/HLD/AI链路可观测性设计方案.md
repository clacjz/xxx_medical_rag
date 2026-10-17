# RAG系统AI链路可观测性设计方案

> **文档类型**: 技术方案补充
> **编制日期**: 2026-03-25
> **适用范围**: RAG知识库系统HLD文档补充
> **更新日期**: 2026-05-14 (ES存储实现状态更新)

---

## 实现状态 (2026-05-14)

> ✅ **ES AI Trace存储已实现** - 以下为当前实现状态：

### 已实现组件

| 组件 | 状态 | 文件位置 |
| ---- | ---- | -------- |
| AITracingService | ✅ 已实现 | `monitor/AITracingService.java` |
| AISpan模型 | ✅ 已实现 | `monitor/AISpan.java` |
| ES索引创建 | ✅ 已实现 | `ensureIndexExists()` 方法 |
| 异步ES存储 | ✅ 已实现 | `saveToElasticsearchAsync()` @Async |
| 内存缓存 | ✅ 已实现 | `ConcurrentHashMap<String, List<AISpan>>` |

### ES索引结构

```
Index: medical_rag_ai_trace

字段映射:
├── traceId: keyword
├── spanId: keyword
├── parentSpanId: keyword
├── operationName: text
├── spanType: keyword
├── startTime: long (epoch millis)
├── endTime: long (epoch millis)
├── durationMs: long
├── inputText: text
├── outputText: text
├── metadata: object
├── userId: keyword
├── sessionId: keyword
├── collectionId: keyword
├── similarityScore: float
├── tokenCount: integer
└── chunkCount: integer
```

### Span类型

```
支持以下Span类型:
- embedding_query: 查询向量化
- vector_search: 向量检索
- rerank: 重排序
- prompt_construction: Prompt构建
- llm_generate: LLM生成
- answer_verification: 答案验证
```

### 待完善功能

| 功能 | 状态 | 说明 |
| ---- | ---- | ---- |
| ES查询接口 | ❌ 待实现 | 从ES查询历史Trace |
| Trace回放 | ❌ 待实现 | 支持问题回放分析 |
| 根因分析 | ❌ 待实现 | 自动问题诊断 |

---

## 一、设计背景与目标

### 1.1 为什么需要AI链路可观测性？

传统系统监控主要关注：
- **基础设施指标**：CPU、内存、磁盘、网络
- **应用指标**：API响应时间、错误率、吞吐量
- **业务指标**：订单量、用户活跃度

但RAG系统的核心价值在于**AI能力**，系统指标只能告诉我们"系统是否健康"，却无法回答：

```yaml
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

### 1.2 AI链路可观测性定义

**AI链路可观测性**（AI Observability）是指：

> 对RAG系统中AI组件的输入、输出、处理过程和结果进行全面监控、追踪和分析的能力，目的是：
> 1. **可观测**：了解AI系统内部发生了什么
> 2. **可诊断**：快速定位问题根因
> 3. **可优化**：基于数据持续优化AI效果
> 4. **可信赖**：建立对AI系统的信任

### 1.3 设计目标

```yaml
核心目标：
1. 全链路追踪：端到端追踪RAG Pipeline每个环节
2. 质量评估：量化评估检索质量和生成质量
3. 性能监控：监控AI组件的性能指标
4. 成本分析：追踪AI资源消耗和成本
5. 调试能力：支持问题查询和回放分析
6. 持续优化：支持A/B测试和Prompt迭代

量化指标：
- AI链路覆盖率：100%（所有AI调用都要追踪）
- 问题诊断时间：< 5分钟（从用户反馈到定位根因）
- 质量评估自动化：> 80%（自动评分，无需人工）
- 监控数据保留：30天（支持历史分析）
```

---

## 二、整体架构设计

### 2.1 AI链路可观测性架构

```yaml
┌────────────────────────────────────────────────────────────────────────┐
│                         AI链路可观测性平台                               │
├────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │                      数据采集层                                │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐   │   │
│  │  │ Tracing     │  │ Metrics    │  │     Logging         │   │   │
│  │  │ (链路追踪)   │  │ (指标采集)   │  │     (日志记录)      │   │   │
│  │  └─────────────┘  └─────────────┘  └─────────────────────┘   │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                              │                                      │
│                              ▼                                      │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │                      数据处理层                                │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐   │   │
│  │  │ 数据清洗     │  │ 数据聚合     │  │     数据存储        │   │   │
│  │  │            │  │            │  │   Elasticsearch    │   │   │
│  │  └─────────────┘  └─────────────┘  └─────────────────────┘   │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                              │                                      │
│                              ▼                                      │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │                      可视化分析层                               │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐   │   │
│  │  │ Grafana    │  │ 自定义看板   │  │     报表生成       │   │   │
│  │  │ 基础监控    │  │ AI质量看板   │  │   周报/月报       │   │   │
│  │  └─────────────┘  └─────────────┘  └─────────────────────┘   │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                         │
└────────────────────────────────────────────────────────────────────────┘
```

### 2.2 与传统监控的关系

```yaml
三层监控体系：

┌─────────────────────────────────────────────────────────┐
│  第一层：基础设施监控（传统）                              │
│  - CPU、内存、磁盘、网络                                 │
│  - 服务健康检查                                          │
│  - 工具：Prometheus + Grafana                           │
├─────────────────────────────────────────────────────────┤
│  第二层：应用性能监控（APM）                              │
│  - API响应时间、错误率、吞吐量                            │
│  - 数据库查询性能                                        │
│  - 工具：Skywalking / Jaeger                            │
├─────────────────────────────────────────────────────────┤
│  第三层：AI链路可观测性（本方案）                          │ ← 新增
│  - AI Pipeline追踪                                       │
│  - 检索质量评估                                          │
│  - 生成质量评估                                          │
│  - LLM调用分析                                          │
│  - 工具：自定义 + LangSmith/Arize（可选）               │
└─────────────────────────────────────────────────────────┘
```

---

## 三、核心设计：五大可观测性维度

### 3.1 维度一：全链路追踪（Tracing）

#### 3.1.1 端到端Pipeline追踪

```yaml
一个完整的RAG请求追踪结构：

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
│  │  │   查询向量维度: 1024                                      │
│  │  │   检索策略: hybrid                                        │
│  │  │   Top-K: 5                                               │
│  │  │   ┌─ Span: semantic_search                               │
│  │  │   │   耗时: 18ms                                         │
│  │  │   │   命中: 5条                                          │
│  │  │   │   相似度: [0.89, 0.85, 0.82, 0.78, 0.75]           │
│  │  │   └─────────────────────────────────────────────────────┘ │
│  │  │   ┌─ Span: keyword_search                                 │
│  │  │   │   耗时: 5ms                                          │
│  │  │   │   命中: 3条                                          │
│  │  │   │   BM25分数: [0.72, 0.68, 0.65]                      │
│  │  │   └─────────────────────────────────────────────────────┘ │
│  │  └─────────────────────────────────────────────────────────┘ │
│  │                                                             │
│  │  ┌─ Span: rerank                                            │
│  │  │   耗时: 67ms                                             │
│  │  │   输入: query + 8个候选文档                               │
│  │  │   输出: 5个重排序后的文档                                │
│  │  │   Rerank模型: BGE-reranker                              │
│  │  └─────────────────────────────────────────────────────────┘ │
│  │                                                             │
│  │  ┌─ Span: prompt构建                                        │
│  │  │   耗时: 2ms                                             │
│  │  │   Token统计:                                             │
│  │  │   - System: 200 tokens                                   │
│  │  │   - Context: 1500 tokens                                 │
│  │  │   - Query: 15 tokens                                     │
│  │  │   - Total: 1715 tokens                                  │
│  │  └─────────────────────────────────────────────────────────┘ │
│  │                                                             │
│  │  ┌─ Span: llm_generate                                      │
│  │  │   耗时: 2680ms                                          │
│  │  │   模型: qwen2.5-7b-instruct                             │
│  │  │   参数: temperature=0.7, max_tokens=2048                  │
│  │  │   ┌─ Span: first_token                                  │
│  │  │   │   耗时: 2100ms  ← 首次响应时间                      │
│  │  │   └─────────────────────────────────────────────────────┘ │
│  │  │   ┌─ Span: token_generation                              │
│  │  │   │   耗时: 580ms                                       │
│  │  │   │   生成Token数: 145                                   │
│  │  │   │   生成速度: 250 tokens/s                             │
│  │  │   └─────────────────────────────────────────────────────┘ │
│  │  └─────────────────────────────────────────────────────────┘ │
│  │                                                             │
│  │  ┌─ Span: answer_verification                               │
│  │  │   耗时: 150ms                                            │
│  │  │   幻觉检测: 通过                                          │
│  │  │   引用追溯: 成功                                         │
│  │  └─────────────────────────────────────────────────────────┘ │
│  │                                                             │
│  └─────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

#### 3.1.2 追踪数据模型

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
    
    private TraceMetadata metadata;     // 元数据
}

// Span记录
@Data
@Builder
public class AISpan {
    private String spanId;              // Span ID
    private String parentSpanId;        // 父Span ID
    private String name;                // Span名称
    
    private SpanType type;              // 类型
    private long startTime;
    private long duration;
    
    private Object input;               // 输入数据（脱敏）
    private Object output;              // 输出数据（脱敏）
    
    private Map<String, Object> attributes;  // 额外属性
    private List<Event> events;         // 事件
    
    private SpanStatus status;
    private String errorMessage;        // 错误信息
}

// Span类型枚举
public enum SpanType {
    // 检索阶段
    EMBEDDING_QUERY,           // 查询向量化
    EMBEDDING_BATCH,          // 批量向量化
    VECTOR_SEARCH,            // 向量检索
    SEMANTIC_SEARCH,          // 语义检索
    KEYWORD_SEARCH,           // 关键词检索
    HYBRID_SEARCH,            // 混合检索
    RERANK,                  // 重排序
    
    // 生成阶段
    PROMPT_CONSTRUCTION,       // Prompt构建
    LLM_GENERATE,             // LLM生成
    STREAM_GENERATE,         // 流式生成
    ANSWER_VERIFICATION,      // 答案验证
    
    // 文档处理
    DOCUMENT_PARSING,         // 文档解析
    TEXT_CLEANING,           // 文本清洗
    DOCUMENT_CHUNKING,        // 文档分块
    DOCUMENT_EMBEDDING,       // 文档向量化
    
    // 质量评估
    RETRIEVAL_EVALUATION,    // 检索质量评估
    GENERATION_EVALUATION,   // 生成质量评估
    QUALITY_SCORING,         // 质量评分
}

// 事件记录
@Data
@Builder
public class Event {
    private String name;               // 事件名称
    private long timestamp;            // 时间戳
    private Map<String, Object> attributes;
}
```

#### 3.1.3 关键指标

```yaml
追踪覆盖率指标：

1. 请求追踪率
   - 目标：100%
   - 计算：追踪的请求数 / 总请求数 × 100%
   
2. Span完整率
   - 目标：> 95%
   - 计算：完整Span数 / 应有的Span数 × 100%
   
3. 错误追踪率
   - 目标：100%
   - 计算：追踪的错误数 / 总错误数 × 100%
   
4. Pipeline阶段覆盖率
   - 目标：100%
   - 覆盖所有关键阶段：Embedding → Search → Rerank → Prompt → LLM → Verify
```

### 3.2 维度二：检索质量评估（Retrieval Evaluation）

#### 3.2.1 检索质量指标体系

```yaml
┌─────────────────────────────────────────────────────────────┐
│                  检索质量评估指标体系                          │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌────────────────────────────────────────────────────┐    │
│  │ 基础指标                                              │    │
│  ├────────────────────────────────────────────────────┤    │
│  │ • 召回率 (Recall@K)                                  │    │
│  │   定义：前K个结果中包含相关文档的比例                 │    │
│  │   计算：Relevant_Retrieved / Relevant_Total          │    │
│  │   示例：Recall@5 = 0.8（5个结果中包含80%相关文档）    │    │
│  │                                                       │    │
│  │ • 精确率 (Precision@K)                                │    │
│  │   定义：前K个结果中相关文档的比例                      │    │
│  │   计算：Relevant_Retrieved / K                        │    │
│  │                                                       │    │
│  │ • MRR (Mean Reciprocal Rank)                          │    │
│  │   定义：首个相关文档排名的倒数平均值                    │    │
│  │   用途：衡量系统找到正确答案的速度                     │    │
│  │                                                       │    │
│  │ • NDCG (Normalized Discounted Cumulative Gain)        │    │
│  │   定义：考虑排名位置的加权得分                         │    │
│  │   用途：综合衡量相关性排序质量                        │    │
│  └────────────────────────────────────────────────────┘    │
│                                                              │
│  ┌────────────────────────────────────────────────────┐    │
│  │ 质量指标                                              │    │
│  ├────────────────────────────────────────────────────┤    │
│  │ • 平均相似度得分                                      │    │
│  │   检索结果与查询的平均余弦相似度                       │    │
│  │                                                       │    │
│  │ • 检索结果多样性                                      │    │
│  │   使用MMR或ILAD指标衡量结果多样性                     │    │
│  │                                                       │    │
│  │ • 检索延迟                                            │    │
│  │   向量检索平均延迟 < 50ms                            │    │
│  │                                                       │    │
│  │ • 零结果率                                            │    │
│  │   无相关结果的查询比例 < 5%                          │    │
│  └────────────────────────────────────────────────────┘    │
│                                                              │
│  ┌────────────────────────────────────────────────────┐    │
│  │ 业务指标                                              │    │
│  ├────────────────────────────────────────────────────┤    │
│  │ • 问答成功率                                          │    │
│  │   能够从检索结果中生成答案的查询比例                   │    │
│  │                                                       │    │
│  │ • 用户满意度                                          │    │
│  │   基于用户反馈的满意度评分                            │    │
│  │                                                       │    │
│  │ • 引用准确率                                          │    │
│  │   生成回答中引用文档与实际使用文档的匹配率              │    │
│  └────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
```

#### 3.2.2 检索质量评估数据模型

```java
// 检索质量评估记录
@Data
@Builder
public class RetrievalEvaluation {
    private String traceId;
    private String query;
    
    // 查询信息
    private QueryInfo queryInfo;
    
    // 检索结果
    private List<RetrievalResult> results;
    
    // 评估指标
    private RetrievalMetrics metrics;
    
    // 人工标注（如果有）
    private Annotation annotation;
    
    // 时间戳
    private long evaluatedAt;
    
    // 评估方法
    private EvaluationMethod method;
}

@Data
@Builder
public class QueryInfo {
    private String queryText;
    private String intent;               // 查询意图
    private String queryType;           // 事实型/推理型/总结型
    private int queryLength;             // 查询长度
    private boolean hasNegation;         // 是否包含否定词
}

@Data
@Builder
public class RetrievalResult {
    private int rank;                   // 排名
    private String documentId;
    private String documentTitle;
    private String chunkContent;
    
    // 得分
    private double vectorScore;         // 向量相似度
    private double keywordScore;        // 关键词得分
    private double rerankScore;          // 重排得分
    private double finalScore;           // 最终得分
    
    // 评估结果
    private boolean isRelevant;         // 是否相关
    private int relevanceLevel;         // 相关等级 (0-3)
    private String relevanceReason;      // 相关性原因
    
    // 文档信息
    private String source;
    private int pageNumber;
    private Map<String, Object> metadata;
}

@Data
@Builder
public class RetrievalMetrics {
    // 基础指标
    private double recallAt5;
    private double precisionAt5;
    private double mrr;
    private double ndcgAt5;
    
    // 质量指标
    private double avgSimilarityScore;
    private double diversityScore;       // 多样性
    private double coverageScore;        // 覆盖率
    
    // 性能指标
    private long embeddingLatencyMs;
    private long searchLatencyMs;
    private long totalLatencyMs;
    
    // 业务指标
    private boolean hasResults;          // 是否有结果
    private int resultCount;             // 结果数量
    private boolean qualitySatisfied;    // 质量是否达标
}

public enum EvaluationMethod {
    AUTOMATIC,           // 自动评估（基于标注数据）
    MODEL_BASED,         // 基于LLM评估
    USER_FEEDBACK,       // 用户反馈评估
    HYBRID              // 混合评估
}
```

#### 3.2.3 检索质量看板设计

```yaml
Grafana看板 - 检索质量监控：

┌─────────────────────────────────────────────────────────────────────────┐
│                    检索质量监控看板                                       │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  【概览指标】                                                           │
│  ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌────────────┐        │
│  │ Recall@5   │ │ Precision@5│ │   MRR      │ │   NDCG@5   │        │
│  │   0.82    │ │   0.65    │ │   0.75    │ │   0.68    │        │
│  │  ↑ 2.3%   │ │  ↑ 1.5%   │ │  ↓ 0.8%   │ │  ↑ 3.2%   │        │
│  └────────────┘ └────────────┘ └────────────┘ └────────────┘        │
│                                                                          │
│  【趋势图】                                                             │
│  ┌─────────────────────────────────────────────────────────────┐      │
│  │                                                              │      │
│  │  0.9 │    ╭─╮                    ╭─╮                       │      │
│  │  0.8 │   ╱   ╲      ╭─╮       ╱   ╲      ╭─╮             │      │
│  │  0.7 │  ╱     ╲    ╱   ╲     ╱     ╲    ╱   ╲             │      │
│  │  0.6 │ ╱       ╲──╱     ╲───╱       ╲──╱     ╲            │      │
│  │      └────────────────────────────────────────────────────   │      │
│  │       Mon   Tue   Wed   Thu   Fri   Sat   Sun             │      │
│  │                          时间趋势                             │      │
│  └─────────────────────────────────────────────────────────────┘      │
│                                                                          │
│  【分类统计】                    【Top问题查询】                         │
│  ┌──────────────────────┐       ┌──────────────────────────────┐      │
│  │ 事实型查询    ████ 75%│      │ "如何部署RAG系统"   差    │      │
│  │ 推理型查询    ██    15%│      │ "架构设计原则"     优    │      │
│  │ 总结型查询    █      8% │      │ "Embedding原理"   良    │      │
│  │ 其他         █      2% │      │ "优化建议"       优    │      │
│  └──────────────────────┘      └──────────────────────────────┘      │
│                                                                          │
│  【低质量查询分析】                                                      │
│  ┌─────────────────────────────────────────────────────────────┐      │
│  │ 点击查看Recall<0.5的查询 →                                  │      │
│  │ • 模糊查询（5%）→ 建议：查询扩展                            │      │
│  │ • 长文档查询（3%）→ 建议：分段检索                          │      │
│  │ • 专业术语查询（2%）→ 建议：术语同步                        │      │
│  └─────────────────────────────────────────────────────────────┘      │
└─────────────────────────────────────────────────────────────────────────┘
```

### 3.3 维度三：生成质量评估（Generation Evaluation）

#### 3.3.1 生成质量评估体系

```yaml
┌─────────────────────────────────────────────────────────────┐
│                  生成质量评估指标体系                          │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌────────────────────────────────────────────────────┐    │
│  │ 基础质量指标                                          │    │
│  ├────────────────────────────────────────────────────┤    │
│  │                                                        │    │
│  │ • 答案完整性 (Completeness)                           │    │
│  │   回答是否完整覆盖了问题的各个方面                      │    │
│  │                                                        │    │
│  │ • 答案准确性 (Accuracy)                               │    │
│  │   回答中的事实是否与参考文档一致                        │    │
│  │                                                        │    │
│  │ • 答案相关性 (Relevance)                               │    │
│  │   回答是否直接针对用户问题                              │    │
│  │                                                        │    │
│  │ • 答案一致性 (Consistency)                             │    │
│  │   回答内部是否逻辑一致，无矛盾                          │    │
│  │                                                        │    │
│  └────────────────────────────────────────────────────┘    │
│                                                              │
│  ┌────────────────────────────────────────────────────┐    │
│  │ 安全性指标                                            │    │
│  ├────────────────────────────────────────────────────┤    │
│  │                                                        │    │
│  │ • 幻觉检测 (Hallucination)                            │    │
│  │   是否生成了参考文档中没有的事实                        │    │
│  │   幻觉率 = 幻觉语句数 / 总语句数                      │    │
│  │                                                        │    │
│  │ • 有害内容检测                                         │    │
│  │   是否包含有害、偏见或不当内容                          │    │
│  │                                                        │    │
│  │ • 敏感信息泄露                                          │    │
│  │   是否泄露了不应该公开的信息                            │    │
│  │                                                        │    │
│  └────────────────────────────────────────────────────┘    │
│                                                              │
│  ┌────────────────────────────────────────────────────┐    │
│  │ 实用性指标                                            │    │
│  ├────────────────────────────────────────────────────┤    │
│  │                                                        │    │
│  │ • 引用准确性                                          │    │
│  │   引用的文档是否真的被用于生成了答案                    │    │
│  │                                                        │    │
│  │ • 回答可读性                                          │    │
│  │   语言是否通顺、结构是否清晰                           │    │
│  │                                                        │    │
│  │ • 用户满意度                                          │    │
│  │   基于用户反馈的评分                                  │    │
│  │                                                        │    │
│  └────────────────────────────────────────────────────┘    │
│                                                              │
│  ┌────────────────────────────────────────────────────┐    │
│  │ 性能指标                                              │    │
│  ├────────────────────────────────────────────────────┤    │
│  │                                                        │    │
│  │ • 首次响应时间 (TTFT)                                 │    │
│  │   Time to First Token                                 │    │
│  │                                                        │    │
│  │ • 生成速度                                            │    │
│  │   Tokens Per Second                                  │    │
│  │                                                        │    │
│  │ • 总响应时间                                          │    │
│  │   从请求到完整回答的时间                              │    │
│  │                                                        │    │
│  └────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
```

#### 3.3.2 生成质量评估数据模型

```java
// 生成质量评估记录
@Data
@Builder
public class GenerationEvaluation {
    private String traceId;
    private String query;
    private String answer;
    
    // 检索上下文
    private List<String> retrievedContexts;
    
    // Prompt信息
    private PromptInfo promptInfo;
    
    // LLM响应
    private LLMResponse llmResponse;
    
    // 质量评估
    private GenerationMetrics metrics;
    
    // 幻觉检测
    private HallucinationDetection hallucinationDetection;
    
    // 引用分析
    private CitationAnalysis citationAnalysis;
    
    // 用户反馈
    private UserFeedback userFeedback;
    
    private long evaluatedAt;
}

@Data
@Builder
public class PromptInfo {
    private String templateName;
    private int systemPromptTokens;
    private int contextTokens;
    private int queryTokens;
    private int totalPromptTokens;
    
    private String systemPrompt;         // 系统提示词（脱敏）
    private String contextSummary;      // 上下文摘要
}

@Data
@Builder
public class LLMResponse {
    private String model;                // 模型名称
    private String fullResponse;         // 完整回答
    
    // Token统计
    private int promptTokens;
    private int completionTokens;
    private int totalTokens;
    
    // 性能指标
    private long firstTokenLatencyMs;    // 首次响应时间
    private long totalLatencyMs;        // 总生成时间
    private double tokensPerSecond;      // 生成速度
    
    // 响应参数
    private double temperature;
    private int maxTokens;
    private String finishReason;          // stop/complete/length
}

@Data
@Builder
public class GenerationMetrics {
    // 质量评分 (0-100)
    private int completenessScore;      // 完整性
    private int accuracyScore;           // 准确性
    private int relevanceScore;          // 相关性
    private int consistencyScore;        // 一致性
    private int readabilityScore;         // 可读性
    
    // 综合评分
    private double overallScore;          // 综合得分
    
    // 安全指标
    private double hallucinationRate;    // 幻觉率
    private boolean hasHarmfulContent;    // 是否有害
    private boolean hasSensitiveInfo;     // 是否泄露敏感信息
    
    // 业务指标
    private boolean answered;            // 是否成功回答
    private boolean needsClarification;  // 是否需要澄清
}

@Data
@Builder
public class HallucinationDetection {
    // 幻觉检测结果
    private boolean hasHallucination;    // 是否存在幻觉
    
    // 幻觉详情
    private List<HallucinationItem> hallucinations;
    
    // 统计
    private double hallucinationRate;    // 幻觉率
    private int totalStatements;         // 总语句数
    private int hallucinatedStatements;   // 幻觉语句数
    
    // 风险等级
    private RiskLevel riskLevel;         // HIGH/MEDIUM/LOW/NONE
    
    // 检测方法
    private DetectionMethod method;      // NLI/BERT/LLM_EVAL
}

@Data
@Builder
public class HallucinationItem {
    private int sentenceIndex;           // 语句索引
    private String statement;            // 语句内容
    private String type;                 // FABRICATED/CONTRADICTED/UNSUPPORTED
    
    private String claim;                // 具体声明
    private List<String> supportingEvidence;  // 支持证据
    private List<String> contradictingEvidence;  // 矛盾证据
    
    private double confidence;           // 检测置信度
}

@Data
@Builder
public class CitationAnalysis {
    // 引用统计
    private boolean hasCitations;         // 是否有引用
    
    // 引用详情
    private List<Citation> citations;
    
    // 引用准确性
    private double citationAccuracy;      // 引用准确率
    private double citationCoverage;     // 引用覆盖率
    
    // 引用质量
    private double relevanceScore;       // 引用的相关性
    private double supportScore;         // 引用的支持度
}

@Data
@Builder
public class Citation {
    private int startPosition;           // 引用在回答中的起始位置
    private int endPosition;             // 引用在回答中的结束位置
    private String citedText;            // 引用的文本
    
    private String documentId;          // 引用的文档ID
    private String documentTitle;       // 引用的文档标题
    private double relevanceScore;      // 引用相关性得分
    private boolean isAccurate;         // 引用是否准确
}
```

#### 3.3.3 生成质量监控看板

```yaml
Grafana看板 - 生成质量监控：

┌─────────────────────────────────────────────────────────────────────────┐
│                    生成质量监控看板                                       │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  【质量评分】                      【安全性指标】                         │
│  ┌─────────────────────────┐        ┌─────────────────────────┐         │
│  │  综合得分        85.2   │        │  幻觉率         3.2%  │         │
│  │  ↑ 2.1% vs 上周        │        │  ↓ 0.5% vs 上周      │         │
│  │                         │        │                        │         │
│  │  完整性    ████████░░ │        │  有害内容    ░░░░░░░ │         │
│  │  准确性    ███████░░░ │        │  敏感泄露    ░░░░░░ │         │
│  │  相关性    █████████░ │        │  隐私风险    ░░░░░░░ │         │
│  │  一致性    ████████░░ │        └─────────────────────────┘         │
│  └─────────────────────────┘                                              │
│                                                                          │
│  【幻觉趋势】                      【生成性能】                           │
│  ┌─────────────────────────┐        ┌─────────────────────────┐         │
│  │                         │        │  首次响应     1.8s   │         │
│  │    ╭─╮                  │        │  ↓ 15% vs 上周        │         │
│  │   ╱   ╲   ╭─╮          │        │                        │         │
│  │  ╱     ╲─╱   ╲──╮       │        │  生成速度     68/s   │         │
│  │─╱             ╲───    │        │  ↑ 12% vs 上周        │         │
│  │  Mon   Tue   Wed   Thu  │        │                        │         │
│  │  4.2%  3.8%  3.5%  3.2%│        │  平均Token    142    │         │
│  └─────────────────────────┘        └─────────────────────────┘         │
│                                                                          │
│  【低质量回答分析】                                                        │
│  ┌─────────────────────────────────────────────────────────────┐        │
│  │                                                              │        │
│  │  最近24小时低质量回答 (Score < 70)：23条                      │        │
│  │                                                              │        │
│  │  ┌───────────────────────────────────────────────────────┐   │        │
│  │  │ Trace ID    │ 问题类型    │ 得分 │ 主要原因          │   │        │
│  │  ├───────────────────────────────────────────────────────┤   │        │
│  │  │ abc123      │ 幻觉       │  58  │ 引用不准确       │   │        │
│  │  │ def456      │ 不完整     │  62  │ 缺少关键信息      │   │        │
│  │  │ ghi789      │ 不相关     │  55  │ 检索质量差       │   │        │
│  │  └───────────────────────────────────────────────────────┘   │        │
│  │                                                              │        │
│  │  点击查看详情 →                                               │        │
│  └─────────────────────────────────────────────────────────────┘        │
└─────────────────────────────────────────────────────────────────────────┘
```

### 3.4 维度四：成本追踪（Cost Tracking）

#### 3.4.1 成本追踪体系

```yaml
┌─────────────────────────────────────────────────────────────┐
│                    AI成本追踪体系                             │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  【成本构成】                                                  │
│                                                              │
│  1. LLM调用成本                                              │
│     ├── Token成本                                            │
│     │   Prompt Tokens × 单价                                  │
│     │   Completion Tokens × 单价                               │
│     ├── 推理成本                                             │
│     │   GPU时间 × GPU单价                                      │
│     └── 存储成本                                             │
│         模型文件存储成本                                       │
│                                                              │
│  2. Embedding成本                                            │
│     ├── 计算成本                                             │
│     │   查询Embedding × 单价                                  │
│     │   文档Embedding × 单价                                   │
│     └── GPU时间成本                                          │
│                                                              │
│  3. 存储成本                                                 │
│     ├── 向量存储成本                                         │
│     └── 元数据存储成本                                        │
│                                                              │
│  【成本优化指标】                                              │
│                                                              │
│  • 单次查询成本                                              │
│    = Embedding成本 + LLM成本 / 查询数                        │
│                                                              │
│  • 问答成功率成本                                             │
│    = 总成本 / 成功回答数                                       │
│                                                              │
│  • 质量调整后成本                                             │
│    = 单次查询成本 / 质量得分 × 100                            │
└─────────────────────────────────────────────────────────────┘
```

#### 3.4.2 成本追踪数据模型

```java
// 成本追踪记录
@Data
@Builder
public class AICostTracking {
    private String traceId;
    private String userId;
    private String sessionId;
    
    // 时间信息
    private long timestamp;
    private String date;                 // YYYY-MM-DD
    private String hour;                // HH
    private String month;               // YYYY-MM
    
    // Token使用
    private TokenUsage tokenUsage;
    
    // 成本计算
    private CostBreakdown costBreakdown;
    
    // 性能指标
    private PerformanceMetrics performance;
    
    // 质量指标
    private QualityMetrics quality;
    
    // 成本效益
    private CostEfficiency efficiency;
}

@Data
@Builder
public class TokenUsage {
    // LLM Token使用
    private int llmPromptTokens;
    private int llmCompletionTokens;
    private int llmTotalTokens;
    
    // Embedding Token使用
    private int embeddingTokens;
    
    // 总计
    private int totalTokens;
}

@Data
@Builder
public class CostBreakdown {
    // LLM成本
    private double llmPromptCost;        // Prompt成本
    private double llmCompletionCost;   // 生成成本
    private double llmTotalCost;        // LLM总成本
    
    // Embedding成本
    private double embeddingCost;       // Embedding成本
    
    // 其他成本
    private double storageCost;         // 存储成本
    private double computeCost;         // 计算成本
    
    // 总成本
    private double totalCost;
    
    // 成本单位
    private String currency;            // CNY/USD
}

@Data
@Builder
public class CostEfficiency {
    // 基础效率
    private double costPerQuery;        // 单次查询成本
    private double costPerSuccessfulQuery;  // 成功查询成本
    private double costPerThousandTokens;  // 千Token成本
    
    // 质量调整后效率
    private double qualityAdjustedCost; // 质量调整后成本
    private double costEffectiveness;   // 成本效益比
    
    // 趋势
    private double costTrend;           // 成本趋势
    private double efficiencyTrend;     // 效率趋势
}
```

#### 3.4.3 成本监控看板

```yaml
Grafana看板 - AI成本监控：

┌─────────────────────────────────────────────────────────────────────────┐
│                    AI成本监控看板                                        │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  【本月AI成本】                      【成本构成】                         │
│  ┌─────────────────────────┐        ┌─────────────────────────┐         │
│  │ ¥ 12,580                │        │  LLM成本      ████████ │         │
│  │ ↑ 8.5% vs 上月          │        │  Embedding    ███      │         │
│  │                         │        │  其他         █        │         │
│  │ 目标: ¥ 15,000         │        │                        │         │
│  │ 进度: 83.9%            │        │  LLM: ¥11,200 (89%)   │         │
│  └─────────────────────────┘        │  Embedding: ¥980 (8%)  │         │
│                                    │  其他: ¥400 (3%)        │         │
│                                    └─────────────────────────┘         │
│                                                                          │
│  【每日成本趋势】                    【Token使用】                         │
│  ┌─────────────────────────┐        ┌─────────────────────────┐         │
│  │ ¥600│                    ╭╮         │  Prompt Tokens: 2.5M     │         │
│  │ ¥500│         ╭────╮   ╱  ╲        │  ↑ 12% vs 上周          │         │
│  │ ¥400│    ╭────╯    ╰──╯    ╲       │                          │         │
│  │ ¥300│────╯                 ╲──╮    │  Completion: 890K        │         │
│  │     └─────────────────────────────────│  ↑ 8% vs 上周          │         │
│  │      Mon  Tue  Wed  Thu  Fri        │                          │         │
│  └─────────────────────────┘        └─────────────────────────┘         │
│                                                                          │
│  【成本效率】                                                          │
│  ┌─────────────────────────────────────────────────────────────┐        │
│  │  单次查询成本      ¥ 0.126      目标: < ¥0.15             │        │
│  │  成功查询成本      ¥ 0.138      目标: < ¥0.16             │        │
│  │  质量调整后成本    ¥ 0.152      目标: < ¥0.18             │        │
│  └─────────────────────────────────────────────────────────────┘        │
└─────────────────────────────────────────────────────────────────────────┘
```

### 3.5 维度五：调试与问题诊断（Debug & Diagnosis）

#### 3.5.1 问题诊断框架

```yaml
┌─────────────────────────────────────────────────────────────┐
│                    问题诊断框架                               │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  【问题分类】                                                  │
│                                                              │
│  1. 检索问题                                                 │
│     • 无结果返回                                             │
│     • 结果不相关                                             │
│     • 结果重复                                               │
│     • 结果排序不合理                                         │
│                                                              │
│  2. 生成问题                                                 │
│     • 幻觉严重                                             │
│     • 回答不完整                                            │
│     • 回答偏离主题                                           │
│     • 语言不通顺                                            │
│                                                              │
│  3. 性能问题                                                 │
│     • 响应延迟高                                            │
│     • Embedding慢                                           │
│     • LLM生成慢                                             │
│                                                              │
│  4. 资源问题                                                 │
│     • GPU显存不足                                            │
│     • 内存溢出                                               │
│     • 服务宕机                                               │
│                                                              │
│  【诊断流程】                                                  │
│                                                              │
│  Step 1: 问题识别                                           │
│  ├── 用户反馈                                               │
│  ├── 自动告警                                               │
│  └── 监控发现                                               │
│                                                              │
│  Step 2: 初步分析                                           │
│  ├── 查看TraceID                                             │
│  ├── 分析Span耗时                                            │
│  └── 定位异常环节                                           │
│                                                              │
│  Step 3: 深度分析                                           │
│  ├── 查看输入输出                                            │
│  ├── 对比相似案例                                            │
│  └── 分析根因                                               │
│                                                              │
│  Step 4: 制定方案                                           │
│  ├── 检索优化                                               │
│  ├── Prompt调优                                             │
│  └── 模型调整                                               │
│                                                              │
│  Step 5: 效果验证                                           │
│  ├── A/B测试                                                │
│  └── 效果对比                                               │
└─────────────────────────────────────────────────────────────┘
```

#### 3.5.2 诊断工具设计

```java
// 问题诊断服务
@Service
@Slf4j
public class DiagnosisService {
    
    @Autowired
    private TraceRepository traceRepository;
    
    @Autowired
    private EvaluationService evaluationService;
    
    @Autowired
    private MetricsService metricsService;
    
    /**
     * 问题诊断入口
     */
    public DiagnosisReport diagnose(String traceId) {
        // 1. 获取Trace信息
        AITrace trace = traceRepository.findById(traceId)
            .orElseThrow(() -> new DiagnosisException("Trace not found: " + traceId));
        
        // 2. 分析各阶段耗时
        Map<String, Object> latencyAnalysis = analyzeLatency(trace);
        
        // 3. 分析质量指标
        Map<String, Object> qualityAnalysis = analyzeQuality(trace);
        
        // 4. 分析错误原因
        List<String> errorCauses = analyzeErrorCauses(trace);
        
        // 5. 生成诊断报告
        return DiagnosisReport.builder()
            .traceId(traceId)
            .query(trace.getQuery())
            .answer(trace.getAnswer())
            .latencyAnalysis(latencyAnalysis)
            .qualityAnalysis(qualityAnalysis)
            .errorCauses(errorCauses)
            .suggestions(generateSuggestions(trace))
            .similarCases(findSimilarCases(trace))
            .build();
    }
    
    /**
     * 耗时分析
     */
    private Map<String, Object> analyzeLatency(AITrace trace) {
        Map<String, Object> analysis = new HashMap<>();
        
        // 计算各阶段占比
        Map<String, Long> stageDurations = trace.getSpans().stream()
            .collect(Collectors.toMap(AISpan::getName, AISpan::getDuration));
        
        long totalDuration = trace.getDuration();
        
        Map<String, Double> percentages = stageDurations.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> (double) e.getValue() / totalDuration * 100
            ));
        
        // 找出瓶颈
        String bottleneck = percentages.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("unknown");
        
        analysis.put("stageDurations", stageDurations);
        analysis.put("percentages", percentages);
        analysis.put("bottleneck", bottleneck);
        analysis.put("totalDuration", totalDuration);
        
        return analysis;
    }
    
    /**
     * 质量分析
     */
    private Map<String, Object> analyzeQuality(AITrace trace) {
        Map<String, Object> analysis = new HashMap<>();
        
        // 获取评估指标
        RetrievalMetrics retrievalMetrics = evaluationService
            .getRetrievalMetrics(trace.getTraceId());
        GenerationMetrics generationMetrics = evaluationService
            .getGenerationMetrics(trace.getTraceId());
        
        // 计算质量得分
        double qualityScore = calculateQualityScore(
            retrievalMetrics, 
            generationMetrics
        );
        
        analysis.put("retrievalMetrics", retrievalMetrics);
        analysis.put("generationMetrics", generationMetrics);
        analysis.put("qualityScore", qualityScore);
        analysis.put("qualityLevel", getQualityLevel(qualityScore));
        
        return analysis;
    }
    
    /**
     * 根因分析
     */
    private List<String> analyzeErrorCauses(AITrace trace) {
        List<String> causes = new ArrayList<>();
        
        // 分析检索结果
        if (trace.getRetrievalMetrics().getRecallAt5() < 0.5) {
            causes.add("检索召回率低：检索策略可能需要优化");
        }
        
        if (trace.getRetrievalMetrics().getResultCount() == 0) {
            causes.add("无检索结果：可能是知识库中没有相关内容");
        }
        
        // 分析生成质量
        if (trace.getGenerationMetrics().getHallucinationRate() > 0.1) {
            causes.add("幻觉率高：LLM可能生成了不准确的内容");
        }
        
        if (trace.getGenerationMetrics().getCompletenessScore() < 60) {
            causes.add("回答不完整：可能需要补充更多上下文");
        }
        
        // 分析性能
        if (trace.getDuration() > 10000) {
            causes.add("响应延迟高：可能需要优化推理速度");
        }
        
        return causes;
    }
    
    /**
     * 生成优化建议
     */
    private List<String> generateSuggestions(AITrace trace) {
        List<String> suggestions = new ArrayList<>();
        
        // 基于质量分析
        if (trace.getRetrievalMetrics().getRecallAt5() < 0.5) {
            suggestions.add("建议1: 调整Top-K参数，从5增加到10");
            suggestions.add("建议2: 尝试混合检索策略，结合向量和关键词检索");
            suggestions.add("建议3: 考虑更新Embedding模型或重新训练");
        }
        
        if (trace.getGenerationMetrics().getHallucinationRate() > 0.1) {
            suggestions.add("建议1: 在Prompt中增加'仅基于提供的文档回答'的约束");
            suggestions.add("建议2: 降低temperature参数，从0.7降低到0.3");
            suggestions.add("建议3: 增加答案验证环节，过滤不确定的内容");
        }
        
        // 基于性能分析
        if (trace.getDuration() > 10000) {
            suggestions.add("建议1: 考虑使用量化模型减少推理时间");
            suggestions.add("建议2: 增加LLM服务实例进行负载均衡");
        }
        
        return suggestions;
    }
    
    /**
     * 查找相似案例
     */
    private List<AITrace> findSimilarCases(AITrace trace) {
        // 使用向量相似度查找相似查询
        return traceRepository.findSimilar(
            trace.getQueryEmbedding(),
            5,
            trace.getTimestamp() - 7 * 24 * 3600 * 1000  // 最近7天
        );
    }
}

// 诊断报告
@Data
@Builder
public class DiagnosisReport {
    private String traceId;
    private String query;
    private String answer;
    
    private Map<String, Object> latencyAnalysis;
    private Map<String, Object> qualityAnalysis;
    
    private List<String> errorCauses;
    private List<String> suggestions;
    
    private List<AITrace> similarCases;
    
    private long generatedAt;
}
```

---

## 四、技术实现方案

### 4.1 数据采集架构

```yaml
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
│  │ (Elasticsearch)│   │  (Prometheus)  │   │ (Elasticsearch)│           │
│  └────────────────┘   └────────────────┘   └────────────────┘           │
│                                                                         │
└────────────────────────────────────────────────────────────────────────┘
```

### 4.2 核心组件实现

#### 4.2.1 AI Tracing SDK (Java)

```java
// AI Tracing SDK
@Component
public class AITracingSDK {
    
    private final Tracer tracer;
    private final ObjectMapper objectMapper;
    
    public AITracingSDK(Tracer tracer) {
        this.tracer = tracer;
        this.objectMapper = new ObjectMapper();
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
        span.setAttribute("start_time", System.currentTimeMillis());
        
        // 模拟处理
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
        
        // 添加结果详情
        results.forEach((result, index) -> {
            span.addEvent("result_" + index)
                .setAttribute("document_id", result.getDocumentId())
                .setAttribute("score", result.getScore());
        });
        
        span.end();
    }
    
    /**
     * LLM生成追踪
     */
    public void traceLLMGenerate(TraceContext ctx, LLMResponse response) {
        Span span = tracer.startSpan("llm_generate", ctx.getCurrentSpan());
        
        // Token统计
        span.setAttribute("prompt_tokens", response.getPromptTokens());
        span.setAttribute("completion_tokens", response.getCompletionTokens());
        span.setAttribute("total_tokens", response.getTotalTokens());
        
        // 性能指标
        span.setAttribute("first_token_latency_ms", response.getFirstTokenLatencyMs());
        span.setAttribute("total_latency_ms", response.getTotalLatencyMs());
        span.setAttribute("tokens_per_second", response.getTokensPerSecond());
        
        // 模型信息
        span.setAttribute("model", response.getModel());
        span.setAttribute("temperature", response.getTemperature());
        
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

#### 4.2.2 AI Tracing SDK (Python)

```python
# AI Tracing SDK for Python Services
from opentelemetry import trace
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor
from opentelemetry.sdk.resources import Resource
import time
import json

class AITracingSDK:
    def __init__(self, service_name: str):
        self.tracer = trace.get_tracer(service_name)
        
    def trace_embedding(self, texts: List[str], embeddings: List[List[float]], duration_ms: float):
        """追踪Embedding操作"""
        with self.tracer.start_as_current_span("embedding_batch") as span:
            span.set_attribute("batch_size", len(texts))
            span.set_attribute("dimension", len(embeddings[0]) if embeddings else 0)
            span.set_attribute("duration_ms", duration_ms)
            span.set_attribute("avg_duration_per_text", duration_ms / len(texts) if texts else 0)
            
    def trace_llm_request(self, model: str, prompt_tokens: int, 
                          completion_tokens: int, duration_ms: float):
        """追踪LLM请求"""
        with self.tracer.start_as_current_span("llm_request") as span:
            span.set_attribute("model", model)
            span.set_attribute("prompt_tokens", prompt_tokens)
            span.set_attribute("completion_tokens", completion_tokens)
            span.set_attribute("total_tokens", prompt_tokens + completion_tokens)
            span.set_attribute("duration_ms", duration_ms)
            
            if duration_ms > 0:
                tokens_per_second = completion_tokens / (duration_ms / 1000)
                span.set_attribute("tokens_per_second", tokens_per_second)
                
    def trace_search(self, query_embedding: List[float], 
                     top_k: int, results: List[dict], duration_ms: float):
        """追踪检索操作"""
        with self.tracer.start_as_current_span("vector_search") as span:
            span.set_attribute("top_k", top_k)
            span.set_attribute("results_count", len(results))
            span.set_attribute("duration_ms", duration_ms)
            
            if results:
                scores = [r.get('score', 0) for r in results]
                span.set_attribute("avg_score", sum(scores) / len(scores))
                span.set_attribute("max_score", max(scores))
                span.set_attribute("min_score", min(scores))
                
    def trace_rerank(self, query: str, candidates: List[dict],
                      results: List[dict], model: str, duration_ms: float):
        """追踪重排序操作"""
        with self.tracer.start_as_current_span("rerank") as span:
            span.set_attribute("model", model)
            span.set_attribute("candidates_count", len(candidates))
            span.set_attribute("results_count", len(results))
            span.set_attribute("duration_ms", duration_ms)
```

### 4.3 数据存储设计

#### 4.3.1 Elasticsearch索引设计

```yaml
# AI Trace索引
indices:
  ai-trace:
    mappings:
      properties:
        traceId: keyword
        userId: keyword
        sessionId: keyword
        query: text
        answer: text
        collectionId: keyword
        
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
            completenessScore: integer
            accuracyScore: integer
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
            
        # 成本
        cost:
          properties:
            llmCost: float
            embeddingCost: float
            totalCost: float
            
        # 元数据
        metadata:
          properties:
            userAgent: keyword
            ip: keyword
            region: keyword
            
    settings:
      number_of_shards: 3
      number_of_replicas: 1
      refresh_interval: 5s
```

#### 4.3.2 Prometheus指标设计

```yaml
# Prometheus指标定义

# AI Pipeline指标
ai_pipeline_requests_total:
  description: "AI Pipeline请求总数"
  labels: [status, collection_id]
  
ai_pipeline_duration_seconds:
  description: "AI Pipeline耗时分布"
  type: histogram
  buckets: [0.1, 0.5, 1, 2, 5, 10, 30]
  labels: [stage]

# Embedding指标
embedding_requests_total:
  description: "Embedding请求总数"
  labels: [status, model]
  
embedding_duration_seconds:
  description: "Embedding耗时分布"
  type: histogram
  buckets: [0.01, 0.05, 0.1, 0.2, 0.5]
  
embedding_batch_size:
  description: "Embedding批量大小分布"
  type: histogram
  buckets: [1, 5, 10, 20, 50, 100]

# LLM指标
llm_requests_total:
  description: "LLM请求总数"
  labels: [status, model]
  
llm_duration_seconds:
  description: "LLM生成耗时分布"
  type: histogram
  buckets: [0.5, 1, 2, 5, 10, 30]
  
llm_first_token_latency_seconds:
  description: "首次响应时间分布"
  type: histogram
  buckets: [0.5, 1, 2, 3, 5, 10]
  
llm_tokens_total:
  description: "Token使用总量"
  labels: [type]  # prompt, completion
  type: counter

# 检索质量指标
retrieval_recall_at_5:
  description: "Recall@5指标"
  type: gauge
  
retrieval_precision_at_5:
  description: "Precision@5指标"
  type: gauge
  
retrieval_zero_results_ratio:
  description: "零结果率"
  type: gauge

# 生成质量指标
generation_quality_score:
  description: "生成质量综合得分"
  type: gauge
  
generation_hallucination_rate:
  description: "幻觉率"
  type: gauge

# 成本指标
ai_cost_total:
  description: "AI成本总计"
  type: counter
  labels: [type]  # llm, embedding
  
ai_cost_per_query:
  description: "单次查询成本"
  type: gauge
```

---

## 五、Grafana看板设计

### 5.1 AI链路可观测性总览看板

```yaml
Grafana Dashboard: AI链路可观测性总览

Variables:
- $date_range: 最近1小时/6小时/24小时/7天/30天
- $collection: 知识库筛选

Row 1: 核心指标概览
┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
│ Pipeline请求 │ │ 平均响应时间 │ │ 成功率      │ │ 质量得分    │
│ 1,234       │ │ 2.8s        │ │ 96.5%       │ │ 85.2        │
│ ↑ 12%       │ │ ↓ 8%        │ │ ↑ 1.2%      │ │ ↑ 2.1%      │
└──────────────┘ └──────────────┘ └──────────────┘ └──────────────┘

Row 2: Pipeline耗时分解
Graph: Stacked area chart
- Embedding: 5%
- 检索: 8%
- 重排序: 3%
- Prompt构建: 1%
- LLM生成: 80%
- 其他: 3%

Row 3: 检索质量 & 生成质量
┌────────────────────────────┐ ┌────────────────────────────┐
│ 检索质量趋势               │ │ 生成质量趋势              │
│ Graph: Line chart         │ │ Graph: Line chart        │
│ Recall@5 / Precision@5    │ │ 质量分 / 幻觉率          │
└────────────────────────────┘ └────────────────────────────┘

Row 4: Token使用 & 成本
┌────────────────────────────┐ ┌────────────────────────────┐
│ Token使用趋势               │ │ 成本趋势                  │
│ Graph: Bar chart           │ │ Graph: Area chart         │
│ Prompt / Completion        │ │ 累计成本                  │
└────────────────────────────┘ └────────────────────────────┘

Row 5: 问题分布 & Top问题
┌────────────────────────────┐ ┌────────────────────────────┐
│ 问题类型分布               │ │ Top问题查询               │
│ Pie chart                 │ │ Table                     │
│ - 检索差: 45%            │ │ 1. xxx: 差              │
│ - 幻觉: 30%              │ │ 2. xxx: 差              │
│ - 性能: 15%              │ │ 3. xxx: 良              │
│ - 其他: 10%              │ │                           │
└────────────────────────────┘ └────────────────────────────┘
```

---

## 六、实施计划

### 6.1 实施优先级

```yaml
Phase 1: 基础追踪（第1-2周）
✅ 必须完成
- AI Tracing SDK集成
- 基础Span追踪（Embedding、Search、LLM）
- Trace数据存储
- 基础Grafana看板

Phase 2: 质量评估（第3-4周）
🔄 重要功能
- 检索质量评估指标
- 生成质量评估指标
- 质量看板
- 告警配置

Phase 3: 成本追踪（第5周）
📈 运营需求
- Token使用统计
- 成本计算
- 成本看板
- 成本告警

Phase 4: 高级功能（第6-8周）
🎯 优化需求
- 根因分析
- 相似案例查询
- A/B测试框架
- 自动化诊断

Phase 5: 持续优化（持续）
🔄 迭代改进
- 评估指标优化
- 看板完善
- 告警优化
- 新功能添加
```

### 6.2 工作量估算

```yaml
开发任务：
1. AI Tracing SDK (Java): 3天
2. AI Tracing SDK (Python): 2天
3. 数据存储设计: 2天
4. Prometheus指标: 2天
5. Grafana基础看板: 2天
6. 检索质量评估: 3天
7. 生成质量评估: 3天
8. 成本追踪: 2天
9. Grafana高级看板: 2天
10. 告警配置: 1天

总计: 约22人天

基础设施任务：
- Elasticsearch部署: 1天
- Kafka配置: 1天
- 监控告警配置: 1天

总计: 约3人天
```

---

## 七、总结

### 7.1 价值体现

```yaml
AI链路可观测性带来的价值：

1. 问题定位效率
   从"用户反馈问题 → 猜测原因 → 逐个排查"
   升级为"TraceID → 直接定位根因"
   预计提升效率: 80%

2. AI效果优化
   以前：凭经验调优，无法量化效果
   现在：数据驱动，持续监控优化
   预计提升质量: 15%

3. 成本控制
   以前：月末才知道成本超支
   现在：实时监控，及时发现异常
   预计节省成本: 20%

4. 系统信任
   以前：无法解释AI决策
   现在：完整链路可追溯
   预计提升信任度: 40%
```

### 7.2 关键成功因素

```yaml
1. 覆盖率100%
   - 所有AI调用必须追踪
   - 不留死角
   
2. 数据质量
   - 追踪数据必须准确
   - 及时清洗和校验
   
3. 快速响应
   - 告警及时
   - 问题诊断快速
   
4. 持续优化
   - 指标体系不断完善
   - 看板持续改进
```

---

**文档编制完成**
