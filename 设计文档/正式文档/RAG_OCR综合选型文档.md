# RAG知识库系统OCR技术方案

## 文档信息

| 项目名称 | RAG知识库系统OCR技术方案 |
| ---- | ------------------------ |
| 文档版本 | V1.0 |
| 编制日期 | 2026-05-14 |
| 更新说明 | 简化版，仅记录当前实现状态 |

---

## 一、OCR实现方案

### 1.1 技术架构

当前OCR采用**GLM-OCR + Qwen3.5-9B回退**的混合方案：

```
OcrService.java 架构：
├── Primary OCR: GLM-OCR (http://localhost:38247/api/glm/read)
│   └── 适用：常规PDF文字识别
├── Fallback OCR: Qwen3.5-9B (http://localhost:28247/api/glm/extract)
│   └── 适用：GLM-OCR失败时的回退
└── 缓存: Redis (key: ocr:doc:{docId}:page:{page}, TTL: 30min)
```

### 1.2 配置参数

```yaml
ocr:
  primary:
    url: http://localhost:38247
    timeout: 300s
    max-pages-per-request: 10
  fallback:
    url: http://localhost:28247
    model: Qwen3.6-27B
  cache:
    ttl: 30m
```

### 1.3 已实现功能

- ✅ PDF文字提取（电子版）
- ✅ OCR识别（扫描版PDF）
- ✅ OCR结果Redis缓存
- ✅ OCR失败自动回退到Qwen
- ✅ 多页PDF批量OCR处理
- ✅ PDF真实页数检测

---

## 二、OCR处理流程

```
上传PDF → 检测是否有文字层
    │
    ├── 有文字层 → PDFBox直接提取
    │
    └── 无文字层/文字过少 → OCR识别
                              │
                              ├── GLM-OCR识别
                              │
                              └── 失败 → Qwen3.5-9B回退
```

---

## 三、待完善功能

> 以下功能在本版本中未实现：

- ❌ 表格结构识别
- ❌ 公式识别
- ❌ 目录识别
- ❌ 基于视觉的文档结构分析

---

## 四、后续优化方向

如有需要，可考虑以下增强：

1. **表格识别**：集成PaddleOCR表格识别
2. **公式识别**：集成OmniMath或类似方案
3. **目录识别**：评估轻量级视觉模型

---

**文档编制完成**
