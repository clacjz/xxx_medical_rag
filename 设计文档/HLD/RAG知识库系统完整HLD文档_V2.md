# RAG知识库系统完整高层设计文档

> **文档版本**: V2.0\
> **编制日期**: 2026-03-25\
> **项目周期**: 63个工作日（2026-03-25 至 2026-06-25）\
> **技术栈**: Spring Boot 3.x + Spring AI + Redis + Qdrant + Ollama/vLLM + MinIO + etcd + PaddleOCR

***

## 目录

1. [项目概述](#一项目概述)
2. [技术架构设计](#二技术架构设计)
3. [AI链路可观测性设计](#三ai链路可观测性设计)
4. [功能架构设计](#四功能架构设计)
5. [模块详细设计](#五模块详细设计)
6. [数据模型设计](#六数据模型设计)
7. [部署方案设计](#七部署方案设计)
8. [实施计划](#八实施计划)
9. [验收标准](#九验收标准)
10. [风险评估](#十风险评估)
11. [成本估算](#十一成本估算)

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

### 工作日详细计算

**2026年3月（剩余）**：

```
周一 23  二 24  三 25★  四 26  五 27  六 28  日 29
                     开始              工作  工作
  
剩余工作日：3天（25、26、27）
```

**2026年4月**：

```
清明节：4月4日（周六）- 法定节假日

周一 30/6  二 31  三 1   四 2   五 3   
工作    工作  清明  清明  工作

周一 6    二 7   三 8   四 9   五 10  
工作    工作  工作  工作  工作

周一 13  二 14   三 15  四 16  五 17  
工作    工作  工作  工作  工作

周一 20  二 21   三 22  四 23  五 24  
工作    工作  工作  工作  工作

周一 27  二 28   三 29  四 30
工作    工作  工作  工作

4月工作日：21天
```

**2026年5月**：

```
劳动节：5月1日（周五）- 法定节假日
端午节：5月31日（周日）- 法定节假日

周一 4    二 5   三 6   四 7   五 8   
工作    工作  工作  工作  工作

周一 11   二 12  三 13  四 14  五 15  
工作    工作  工作  工作  工作

周一 18   二 19  三 20  四 21  五 22  
工作    工作  工作  工作  工作

周一 25   二 26  三 27  四 28  五 29  
工作    工作  工作  工作  工作

5月工作日：19天
```

**2026年6月（前25天）**：

```
端午节：6月2日（周一）- 法定节假日

周一 1    二 2★  三 3   四 4   五 5   
工作    端午  工作  工作  工作

周一 8    二 9   三 10  四 11  五 12  
工作    工作  工作  工作  工作

周一 15   二 16  三 17  四 18  五 19  
工作    工作  工作  工作  工作

周一 22   二 23  三 24  四 25★ 五 26  
工作    工作  工作  工作  结束

6月工作日：18天
```

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
│  │  Embedding  │ │    LLM      │ │        OCR              ││
│  │  服务       │ │   服务       │ │       服务              ││
│  │  (BGE模型)  │ │(Ollama/vLLM)│ │     (PaddleOCR)        ││
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
| Embedding模型 | BGE-large-zh-v1.5 | **已部署** | **待填写** | 端口配置在第7章 |
| LLM服务 | Ollama/vLLM | **已部署** | **待填写** | 端口配置在第7章 |
| 文档存储 | MinIO | **已部署** | **待填写** | 端口配置在第7章 |
| 配置中心 | etcd | **已部署** | **待填写** | 端口配置在第7章 |
| AI可观测性 | OpenTelemetry + Elasticsearch | **已部署** | **待填写** | 端口配置在第7章 |
| 监控系统 | Prometheus + Grafana | **已部署** | **待填写** | 端口配置在第7章 |
| OCR服务 | PaddleOCR | **已部署** | **待填写** | 端口配置在第7章 |
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
│  │  │   Top-K: 5                                               │
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

#### 4.3 存储成本计算

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

# 四、功能架构设计

## 4.1 功能模块划分

系统划分为7大模块：

| 模块       | 主要功能                     |
| -------- | ------------------------ |
| 用户权限模块   | 用户注册登录、JWT认证、RBAC权限、审计日志 |
| 文档管理模块   | 文档上传、格式解析、文本预处理、分块处理     |
| 知识库管理模块  | 知识库创建、配置、权限、检索           |
| 智能问答模块   | 问答对话、上下文管理、引用追踪、历史管理     |
| AI可观测性模块 | 链路追踪、质量评估、成本分析、问题诊断      |
| 系统管理模块   | 配置管理、模型管理、统计、备份恢复        |
| 运维监控模块   | 健康检查、性能监控、日志管理、告警管理      |

## 4.2 核心业务流程

### 4.2.1 文档处理流程

```
用户上传文档 → 文件校验 → 存储原始文件 → 格式检测 → 文本提取 → 文本清洗 → 文档分块 → 向量化 → 向量存储 → 元数据存储
```

### 4.2.2 智能问答流程

```
用户输入问题 → 查询向量化 → 向量检索 → 结果重排序 → Prompt构建 → LLM生成 → 答案输出 → 保存对话
```

### 4.2.3 AI可观测性流程

```
AI调用 → Tracing SDK采集 → 数据预处理 → 质量评估 → 存储ES → 生成指标 → 告警通知 → 看板展示
```

***

# 五、模块详细设计

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

**分块策略**：

* FixedSizeChunkingStrategy：固定长度分块

* SemanticChunkingStrategy：语义分块

* HybridChunkingStrategy：混合分块

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

# 六、数据模型设计

## 6.1 PostgreSQL核心表

```sql
-- 用户表、角色表、权限表、知识库表、文档表、文档块表、对话表、消息表、审计日志表、系统配置表、AI追踪表
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

# 七、服务接入配置

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

# OCR服务配置
export OCR_SERVICE_URL=http://localhost:8083
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

***

# 八、实施计划（单人开发61个工作日）

## 8.1 里程碑规划

| 里程碑 | 内容       | 日期         | 工作日    | 验收标准               |
| --- | -------- | ---------- | ------ | ------------------ |
| M1  | 基础框架完成   | 2026-04-04 | Day 8  | 服务接入完成，健康检查可用      |
| M2  | 核心功能完成   | 2026-04-24 | Day 23 | 文档处理、RAG引擎、用户权限可用  |
| M3  | AI可观测性完成 | 2026-05-12 | Day 35 | 链路追踪、质量评估、监控看板可用   |
| M4  | 系统集成完成   | 2026-05-27 | Day 46 | 前端、API集成完成，E2E测试通过 |
| M5  | UAT测试完成  | 2026-06-10 | Day 56 | 功能验收、性能验收通过        |
| M6  | 项目上线     | 2026-06-25 | Day 61 | 正式上线，用户培训完成        |

## 8.2 详细实施计划

### 第一阶段：基础架构（第1-8天）

| 日期 | Day | 星期 | 任务 | 负责人 |
| --- | --- | --- | --- | --- |
| 2026-03-25 | 1 | 三 | 项目初始化、Git仓库 | 单人 |
| 2026-03-26 | 2 | 四 | Redis/Qdrant接入 | 单人 |
| 2026-03-27 | 3 | 五 | etcd/MinIO接入 | 单人 |
| 2026-03-30 | 4 | 一 | Ollama/vLLM接入 | 单人 |
| 2026-03-31 | 5 | 二 | Embedding服务接入 | 单人 |
| 2026-04-01 | 6 | 三 | 健康检查API | 单人 |
| 2026-04-02 | 7 | 四 | 代码优化 | 单人 |
| 2026-04-03 | 8 | 五 | **M1里程碑验收** | 单人 |

### 第二阶段：核心功能（第9-23天）

| 日期 | Day | 星期 | 任务 | 负责人 |
| --- | --- | --- | --- | --- |
| 2026-04-07 | 9 | 一 | PDF解析器 | 单人 |
| 2026-04-08 | 10 | 二 | Word/Markdown解析器 | 单人 |
| 2026-04-09 | 11 | 三 | 文档分块器 | 单人 |
| 2026-04-10 | 12 | 四 | 向量存储服务 | 单人 |
| 2026-04-13 | 13 | 五 | 检索服务 | 单人 |
| 2026-04-14 | 14 | 一 | RAG编排器 | 单人 |
| 2026-04-15 | 15 | 二 | LLM集成 | 单人 |
| 2026-04-16 | 16 | 三 | JWT认证 | 单人 |
| 2026-04-17 | 17 | 四 | 简化权限控制 | 单人 |
| 2026-04-20 | 18 | 五 | 对话管理 | 单人 |
| 2026-04-21 | 19 | 一 | 配置管理 | 单人 |
| 2026-04-22 | 20 | 二 | 性能优化 | 单人 |
| 2026-04-23 | 21 | 三 | Bug修复 | 单人 |
| 2026-04-24 | 22 | 四 | 测试和验收 | 单人 |
| 2026-04-25 | 23 | 五 | **M2里程碑验收** | 单人 |

### 第三阶段：AI可观测性（第24-35天）

| 日期 | Day | 星期 | 任务 | 负责人 |
| --- | --- | --- | --- | --- |
| 2026-04-27 | 24 | 日 | Tracing SDK设计 | 单人 |
| 2026-04-28 | 25 | 一 | Tracing SDK实现 | 单人 |
| 2026-04-29 | 26 | 二 | ES存储设计 | 单人 |
| 2026-04-30 | 27 | 三 | 检索质量评估 | 单人 |
| 2026-05-04 | 28 | 一 | 生成质量评估 | 单人 |
| 2026-05-05 | 29 | 二 | 成本追踪 | 单人 |
| 2026-05-06 | 30 | 三 | 监控看板设计 | 单人 |
| 2026-05-07 | 31 | 四 | Prometheus集成 | 单人 |
| 2026-05-08 | 32 | 五 | Grafana看板-1 | 单人 |
| 2026-05-11 | 33 | 一 | Grafana看板-2 | 单人 |
| 2026-05-12 | 34 | 二 | 告警配置 | 单人 |
| 2026-05-13 | 35 | 三 | **M3里程碑验收** | 单人 |

### 第四阶段：系统集成（第36-46天）

| 日期 | Day | 星期 | 任务 | 负责人 |
| --- | --- | --- | --- | --- |
| 2026-05-14 | 36 | 四 | 简化前端UI | 单人 |
| 2026-05-15 | 37 | 五 | 文档管理页面 | 单人 |
| 2026-05-18 | 38 | 一 | 问答页面 | 单人 |
| 2026-05-19 | 39 | 二 | 用户管理页面 | 单人 |
| 2026-05-20 | 40 | 三 | API集成测试 | 单人 |
| 2026-05-21 | 41 | 四 | 端到端测试 | 单人 |
| 2026-05-22 | 42 | 五 | Bug修复 | 单人 |
| 2026-05-25 | 43 | 一 | 性能测试 | 单人 |
| 2026-05-26 | 44 | 二 | 安全测试 | 单人 |
| 2026-05-27 | 45 | 三 | Bug修复 | 单人 |
| 2026-05-28 | 46 | 四 | **M4里程碑验收** | 单人 |

### 第五阶段：UAT与上线（第47-61天）

| 日期 | Day | 星期 | 任务 | 负责人 |
| --- | --- | --- | --- | --- |
| 2026-05-29 | 47 | 五 | UAT测试执行 | 单人 |
| 2026-06-01 | 48 | 一 | UAT问题修复 | 单人 |
| 2026-06-02 | 49 | 二 | 系统优化 | 单人 |
| 2026-06-03 | 50 | 三 | 部署准备 | 单人 |
| 2026-06-04 | 51 | 四 | 生产环境配置 | 单人 |
| 2026-05-08 | 52 | 日 | 预发布测试 | 单人 |
| 2026-06-09 | 53 | 一 | Bug修复 | 单人 |
| 2026-06-10 | 54 | 二 | **M5里程碑验收** | 单人 |
| 2026-06-11 | 55 | 三 | 数据迁移 | 单人 |
| 2026-06-12 | 56 | 四 | 正式上线 | 单人 |
| 2026-06-15 | 57 | 五 | 用户培训 | 单人 |
| 2026-06-16 | 58 | 六 | 培训答疑 | 单人 |
| 2026-06-17 | 59 | 日 | 项目验收准备 | 单人 |
| 2026-06-18 | 60 | 一 | 功能验收 | 单人 |
| 2026-06-19 | 61 | 二 | 性能验收 | 单人 |
| 2026-06-22 | 62 | 三 | 安全验收 | 单人 |
| 2026-06-23 | 64 | 四 | 文档整理 | 单人 |
| 2026-06-24 | 65 | 五 | 项目总结 | 单人 |
| 2026-06-25 | 66 | 六 | **项目正式结束** | 单人 |

***

# 九、验收标准

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

# 十、风险评估

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

# 十一、成本估算

## 11.1 GPU服务器成本（如果需要采购）

**场景：需要采购GPU服务器**

| 配置项    | 规格                               | 单价      | 数量     | 小计          |
| ------ | -------------------------------- | ------- | ------ | ----------- |
| GPU服务器 | RTX 4090 24GB, 64GB RAM, 2TB SSD | ¥25,000 | 1      | ¥25,000     |
| GPU服务器 | A10G 24GB, 128GB RAM, 4TB SSD    | ¥50,000 | 1      | ¥50,000     |
| 网络设备   | 万兆交换机                            | ¥5,000  | 1      | ¥5,000      |
| 机柜和布线  | -                                | ¥3,000  | 1      | ¥3,000      |
| **小计** | <br />                           | <br />  | <br /> | **¥83,000** |

**如果使用已有服务器，此项为¥0**

## 11.2 运维成本（月度）

**GPU运行成本（电费）**：

| 服务器      | GPU功耗  | 日运行时间  | 月电费(¥0.6/度) |
| -------- | ------ | ------ | ----------- |
| GPU服务器1  | 450W   | 8小时    | ¥64.8       |
| GPU服务器2  | 350W   | 8小时    | ¥50.4       |
| 应用服务器    | 200W   | 24小时   | ¥86.4       |
| 存储服务器    | 100W   | 24小时   | ¥43.2       |
| 网络设备     | 50W    | 24小时   | ¥21.6       |
| **月度电费** | <br /> | <br /> | **¥266.4**  |

**61天运维电费**：¥266.4 × 2 = **¥533/月**

## 11.3 存储成本（月度）

| 存储类型     | 容量     | 单价        | 月度成本     |
| -------- | ------ | --------- | -------- |
| SSD存储    | 200GB  | ¥0.5/GB/月 | ¥100     |
| HDD存储    | 2TB    | ¥0.1/GB/月 | ¥200     |
| 向量存储     | 100GB  | ¥0.3/GB/月 | ¥30      |
| 日志存储     | 50GB   | ¥0.2/GB/月 | ¥10      |
| **月度存储** | <br /> | <br />    | **¥340** |

**61天存储成本**：¥340 × 2 = **¥680/月**

## 11.4 软件成本

* 全部使用开源软件：**¥0**

## 11.5 人力成本

**团队配置**：6人

| 人员    | 角色               | 月成本    |
| ----- | ---------------- | ------ |
| 后端开发1 | 架构设计、核心开发、AI可观测性 | <br /> |
| 后端开发2 | 文档处理、AI集成        | <br /> |
| 后端开发3 | 权限系统、API开发       | <br /> |
| 前端开发  | 前端页面开发           | <br /> |
| 测试工程师 | 测试和质量保障          | <br /> |
| 项目经理  | 项目管理             | <br /> |

**总人力成本**：6人 × 2月 × ¥30,000 = **¥360,000**

## 11.6 总成本估算

### 方案一：使用已有服务器（推荐）

| 成本类型   | 月度成本         | 61天成本        | 说明           |
| ------ | ------------ | ------------ | ------------ |
| 电费     | ¥266.4       | ¥533         | GPU和服务器电费    |
| 存储     | ¥340         | ¥680         | SSD、HDD、日志存储 |
| 软件     | ¥0           | ¥0           | 开源软件免费       |
| 人力     | ¥180,000     | ¥360,000     | 6人团队2个月      |
| 其他     | ¥5,000       | ¥10,000      | 培训、差旅等       |
| **总计** | **¥185,606** | **¥371,213** | <br />       |

### 方案二：需要采购GPU服务器

| 成本类型   | 月度成本         | 61天成本        | 说明      |
| ------ | ------------ | ------------ | ------- |
| GPU服务器 | ¥83,000      | ¥83,000      | 一次性采购   |
| 电费     | ¥266.4       | ¥533         | 服务器电费   |
| 存储     | ¥340         | ¥680         | 存储成本    |
| 软件     | ¥0           | ¥0           | 开源软件免费  |
| 人力     | ¥180,000     | ¥360,000     | 6人团队2个月 |
| 其他     | ¥5,000       | ¥10,000      | 培训、差旅等  |
| **总计** | **¥268,606** | **¥454,213** | <br />  |

### 月度运营成本（上线后）

| 成本类型     | 月度成本        | 说明        |
| -------- | ----------- | --------- |
| 电费       | ¥266.4      | GPU和服务器电费 |
| 存储       | ¥340        | 存储增长预留    |
| 运维人力     | ¥25,000     | 0.5人运维    |
| 模型调优     | ¥30,000     | 0.5人AI优化  |
| **月度运营** | **¥55,606** | 不含人力      |

## 11.7 成本优化建议

1. **GPU利用率优化**

   * 批量处理任务集中在非高峰期

   * 使用模型量化减少显存占用

   * 目标：GPU利用率 > 80%

2. **存储优化**

   * 定期清理过期日志

   * 使用冷存储归档历史数据

   * 目标：存储增长率 < 10%/月

3. **模型优化**

   * 使用量化模型减少推理时间

   * 缓存常见查询的Embedding

   * 目标：单次推理成本降低30%

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

**文档版本**：V2.0
**编制日期**：2026-03-25
**编制人**：RAG知识库系统项目组

***

*RAG知识库系统完整高层设计文档编制完成*
