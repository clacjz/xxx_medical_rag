# AI SDK NER 服务扩展规格文档

> 版本: 1.1
> 日期: 2026-05-18
> 状态: 待开发

---

## 更新日志

| 版本 | 日期 | 更新内容 |
|------|------|----------|
| 1.0 | 2026-05-18 | 初始版本 |
| 1.1 | 2026-05-18 | 补充实体类型(ALLERGY/SIDE_EFFECT)、main.py修改、线程池配置、别名映射表、Java集成示例 |

---

## 一、概述

### 1.1 背景

在 AI SDK 现有架构基础上，新增医学实体识别（NER）服务，为 RAG 系统提供实体标注和查询扩展能力。

### 1.2 目标

1. 在 ai-sdk 中新增 `/api/ner` 接口
2. 支持医学实体识别（疾病、药品、症状、检查项目等）
3. 支持查询扩展（同义词/别名扩展）
4. 与现有 embedding、intent 服务保持一致的代码风格

---

## 二、系统架构

### 2.1 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                     AI SDK 服务                             │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐       │
│  │ GLM     │  │ Qwen    │  │Embedding│  │ NER      │       │
│  │ /api/glm│  │ /api/qwe│  │ /api/em │  │ /api/ner│ ← 新增 │
│  └─────────┘  └─────────┘  └─────────┘  └─────────┘       │
└─────────────────────────────────────────────────────────────┘
                           │
                           ▼
                    ┌─────────────┐
                    │ OpenMed NER │
                    │   模型      │
                    └─────────────┘
```

### 2.2 目录结构（增量文件）

```
ai-sdk/
├── gradio_app/
│   ├── routers/
│   │   └── ner_router.py          # 新增
│   ├── services/
│   │   └── ner_service.py        # 新增
│   ├── schemas/
│   │   └── ner_schema.py         # 新增
│   └── config/
│       └── settings.py           # 修改（增加NER配置）
```

---

## 三、接口设计

### 3.1 实体识别接口

**端点**: `POST /api/ner/extract`

**请求**:
```json
{
  "text": "糖尿病患者可服用二甲双胍，建议定期检测血糖",
  "entities": ["DISEASE", "DRUG"],       // 可选，指定实体类型
  "language": "zh"                        // 可选，默认 zh
}
```

**响应**:
```json
{
  "entities": [
    {
      "text": "糖尿病",
      "type": "DISEASE",
      "start": 0,
      "end": 3,
      "standard_name": "糖尿病",
      "aliases": ["DM", "消渴症"]
    },
    {
      "text": "二甲双胍",
      "type": "DRUG",
      "start": 9,
      "end": 13,
      "standard_name": "二甲双胍",
      "aliases": ["格华止", "甲福明"]
    },
    {
      "text": "血糖",
      "type": "TEST",
      "start": 23,
      "end": 25,
      "standard_name": "血糖",
      "aliases": ["blood glucose"]
    }
  ],
  "request_id": "uuid-xxx"
}
```

### 3.2 查询扩展接口

**端点**: `POST /api/ner/expand`

**请求**:
```json
{
  "query": "感冒了吃啥药",
  "expand_types": ["DISEASE", "DRUG"]
}
```

**响应**:
```json
{
  "original_query": "感冒了吃啥药",
  "expanded_queries": [
    "感冒",
    "上呼吸道感染",
    "上感",
    "流感",
    "药",
    "药品",
    "药物治疗"
  ],
  "entities": [
    {
      "text": "感冒",
      "type": "DISEASE",
      "expanded_terms": ["上呼吸道感染", "上感", "流感"]
    }
  ],
  "request_id": "uuid-xxx"
}
```

### 3.3 批量实体识别接口

**端点**: `POST /api/ner/extract-batch`

**请求**:
```json
{
  "texts": [
    "糖尿病患者可服用二甲双胍",
    "高血压患者建议服用硝苯地平"
  ],
  "entities": ["DISEASE", "DRUG"]
}
```

**响应**:
```json
{
  "results": [
    {
      "text": "糖尿病患者可服用二甲双胍",
      "entities": [...]
    },
    {
      "text": "高血压患者建议服用硝苯地平",
      "entities": [...]
    }
  ],
  "total": 2,
  "success_count": 2,
  "failed_count": 0,
  "request_id": "uuid-xxx"
}
```

### 3.4 模型状态接口

**端点**: `GET /api/ner/status`

**响应**:
```json
{
  "model_loaded": true,
  "model_name": "OpenMed-BERT-base",
  "supported_entities": ["DISEASE", "DRUG", "SYMPTOM", "TEST", "TREATMENT", "ANATOMY"],
  "gpu_available": true
}
```

---

## 四、数据模型

### 4.1 实体类型枚举

```python
class EntityType(str, Enum):
    DISEASE = "DISEASE"           # 疾病
    DRUG = "DRUG"                 # 药品
    SYMPTOM = "SYMPTOM"           # 症状
    TEST = "TEST"                 # 检查项目
    TREATMENT = "TREATMENT"       # 治疗方法
    ANATOMY = "ANATOMY"           # 解剖部位
    ALLERGY = "ALLERGY"           # 过敏（新增）
    SIDE_EFFECT = "SIDE_EFFECT"   # 副作用（新增）
```

**实体类型说明**:

| 类型 | 描述 | 示例 |
|------|------|------|
| DISEASE | 疾病 | 糖尿病、高血压、流感 |
| DRUG | 药品 | 二甲双胍、硝苯地平 |
| SYMPTOM | 症状 | 头痛、发热、咳嗽 |
| TEST | 检查项目 | 血糖、血压、肝功能 |
| TREATMENT | 治疗方法 | 手术、化疗、药物治疗 |
| ANATOMY | 解剖部位 | 心脏、肝脏、肾脏 |
| ALLERGY | 过敏 | 青霉素过敏、海鲜过敏 |
| SIDE_EFFECT | 副作用 | 恶心、脱发、肝损伤 |

### 4.2 Schema 定义

**文件**: `schemas/ner_schema.py`

```python
from pydantic import BaseModel, Field
from typing import List, Optional, Enum

class EntityType(str, Enum):
    DISEASE = "DISEASE"
    DRUG = "DRUG"
    SYMPTOM = "SYMPTOM"
    TEST = "TEST"
    TREATMENT = "TREATMENT"
    ANATOMY = "ANATOMY"
    ALLERGY = "ALLERGY"           # 过敏
    SIDE_EFFECT = "SIDE_EFFECT"   # 副作用

class NerExtractRequest(BaseModel):
    text: str = Field(..., description="待识别的文本")
    entities: Optional[List[EntityType]] = Field(
        default=None,
        description="指定实体类型，为空则识别所有类型"
    )
    language: Optional[str] = Field(default="zh", description="语言，默认中文")

class EntityInfo(BaseModel):
    text: str = Field(..., description="实体文本")
    type: EntityType = Field(..., description="实体类型")
    start: int = Field(..., description="起始位置")
    end: int = Field(..., description="结束位置")
    standard_name: Optional[str] = Field(None, description="标准名称")
    aliases: Optional[List[str]] = Field(default=[], description="别名列表")

class NerExtractResponse(BaseModel):
    entities: List[EntityInfo] = Field(..., description="识别出的实体列表")
    request_id: Optional[str] = Field(None, description="请求ID")

class NerExpandRequest(BaseModel):
    query: str = Field(..., description="查询文本")
    expand_types: Optional[List[EntityType]] = Field(
        default=None,
        description="扩展的实体类型"
    )

class ExpandedEntity(BaseModel):
    text: str = Field(..., description="原始实体文本")
    type: EntityType = Field(..., description="实体类型")
    expanded_terms: List[str] = Field(..., description="扩展后的同义词")

class NerExpandResponse(BaseModel):
    original_query: str = Field(..., description="原始查询")
    expanded_queries: List[str] = Field(..., description="扩展后的查询列表")
    entities: List[ExpandedEntity] = Field(default=[], description="实体扩展信息")
    request_id: Optional[str] = Field(None, description="请求ID")

class NerBatchRequest(BaseModel):
    texts: List[str] = Field(..., description="文本列表")
    entities: Optional[List[EntityType]] = Field(default=None, description="实体类型")

class NerBatchResponse(BaseModel):
    results: List[NerExtractResponse] = Field(..., description="批量结果")
    total: int = Field(..., description="总数")
    success_count: int = Field(..., description="成功数")
    failed_count: int = Field(..., description="失败数")
    request_id: str = Field(..., description="请求ID")
```

---

## 五、服务实现

### 5.1 NER Service

**文件**: `services/ner_service.py`

```python
from typing import List, Optional, Dict
import torch
from transformers import AutoModel, AutoTokenizer

class NerService:
    def __init__(self):
        self.model = None
        self.tokenizer = None
        self.device = "cuda" if torch.cuda.is_available() else "cpu"

    def load_model(self, model_path: str):
        """加载 NER 模型"""
        self.tokenizer = AutoTokenizer.from_pretrained(model_path)
        self.model = AutoModel.from_pretrained(model_path)
        self.model.to(self.device)
        self.model.eval()

    def extract_entities(
        self,
        text: str,
        entity_types: Optional[List[str]] = None
    ) -> List[Dict]:
        """识别文本中的实体"""
        # 实现 NER 推理逻辑
        pass

    def expand_query(
        self,
        query: str,
        expand_types: Optional[List[str]] = None
    ) -> Dict:
        """扩展查询同义词"""
        # 1. 识别查询中的实体
        # 2. 查找同义词/别名
        # 3. 返回扩展后的查询列表
        pass

    def is_model_loaded(self) -> bool:
        """检查模型是否已加载"""
        return self.model is not None
```

### 5.2 模型加载配置

**环境变量**:
```
NER_MODEL_PATH=/path/to/openmed_model
NER_BATCH_SIZE=16
NER_MAX_LENGTH=512
```

### 5.3 错误处理

```python
class NerServiceError(Exception):
    """NER 服务异常"""
    pass

class ModelNotLoadedError(NerServiceError):
    """模型未加载"""
    pass

class NerInferenceError(NerServiceError):
    """NER 推理异常"""
    pass
```

---

## 六、配置变更

### 6.1 settings.py 增量配置

```python
# NER 模型配置
NER_MODEL_PATH: str = Field(
    default="/app/models/openmed",
    description="NER 模型路径"
)
NER_BATCH_SIZE: int = Field(
    default=16,
    description="NER 批处理大小"
)
NER_MAX_LENGTH: int = Field(
    default=512,
    description="NER 最大文本长度"
)
NER_GPU_ENABLED: bool = Field(
    default=True,
    description="是否启用 GPU"
)
NER_THREAD_POOL_SIZE: int = Field(
    default=4,
    description="NER 推理线程池大小"
)
NER_CONCURRENT_REQUESTS: int = Field(
    default=10,
    description="最大并发请求数"
)
NER_FALLBACK_TO_CPU: bool = Field(
    default=True,
    description="GPU 不可用时是否 fallback 到 CPU"
)
```

### 6.2 性能优化

#### 6.2.1 线程池配置

NER 模型推理使用独立线程池处理并发请求：

```python
import concurrent.futures

class NerService:
    def __init__(self):
        self.executor = concurrent.futures.ThreadPoolExecutor(
            max_workers=settings.NER_THREAD_POOL_SIZE
        )

    async def extract_entities_async(self, text: str):
        """异步实体识别，使用线程池"""
        loop = asyncio.get_event_loop()
        return await loop.run_in_executor(
            self.executor,
            self.extract_entities,
            text
        )
```

#### 6.2.2 并发控制

- 最大并发请求数: `NER_CONCURRENT_REQUESTS=10`
- 超过限制时返回 HTTP 429 (Too Many Requests)
- 请求队列超时: 30秒

#### 6.2.3 GPU 配置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| NER_GPU_ENABLED | true | 是否启用 GPU |
| NER_BATCH_SIZE | 16 | 批处理大小 |
| 预估显存占用 | ~2GB | 取决于模型大小 |
| CPU fallback | 启用 | GPU 不可用时自动切换 |

#### 6.2.4 性能指标

| 场景 | 延迟 | 备注 |
|------|------|------|
| 单文本 (<512 tokens) | <100ms | GPU |
| 单文本 (<512 tokens) | <500ms | CPU |
| 批量 (16条) | <500ms | GPU |
| 查询扩展 | <50ms | 纯本地查表 |

### 6.3 main.py 修改

**修改点**:
1. 导入 NerService 和 ner_router
2. 在应用启动时初始化 NerService 并加载模型
3. 注册 ner_router

**修改示例**:
```python
# gradio_app/main.py 修改

from gradio_app.routers import glm_router, qwen_router, embedding_router, intent_router, ner_router  # 新增 ner_router
from gradio_app.services.ner_service import ner_service, NerServiceError  # 新增

# 应用启动时加载 NER 模型
@app.on_event("startup")
async def startup_event():
    """启动时初始化 NER 服务"""
    try:
        # 检查 GPU 是否可用
        if settings.NER_GPU_ENABLED and not torch.cuda.is_available():
            logger.warning("GPU not available for NER, will fallback to CPU")
            ner_service.device = "cpu"

        ner_service.load_model(settings.NER_MODEL_PATH)
        logger.info(f"NER model loaded successfully: {settings.NER_MODEL_PATH}")
    except Exception as e:
        logger.error(f"Failed to load NER model: {e}")
        # 模型加载失败不影响主服务启动，但 NER 接口将返回错误

# 注册路由
app.include_router(glm_router)
app.include_router(qwen_router)
app.include_router(embedding_router)
app.include_router(intent_router)
app.include_router(ner_router)  # 新增

# 异常处理器
@app.exception_handler(NerServiceError)
async def ner_service_exception_handler(request: Request, exc: NerServiceError):
    """NER 服务异常处理器"""
    request_id = getattr(request.state, "request_id", "")
    logger.error(f"NER service error: {str(e)}", extra={"request_id": request_id})
    return JSONResponse(
        status_code=503,
        content={
            "error": "NER service error",
            "detail": str(exc),
            "request_id": request_id
        }
    )
```

---

## 七、错误码

| 错误码 | 描述 | HTTP 状态码 |
|--------|------|-------------|
| NER_001 | 模型未加载 | 503 |
| NER_002 | NER 推理失败 | 502 |
| NER_003 | 文本过长 | 400 |
| NER_004 | 不支持的实体类型 | 400 |

---

## 八、依赖变更

### 8.1 requirements.txt 增量

```
transformers>=4.30.0
torch>=2.0.0
```

---

## 九、测试用例

### 9.1 单元测试

```python
# tests/test_ner_service.py
def test_entity_extraction():
    ner_service = NerService()
    ner_service.load_model(TEST_MODEL_PATH)

    text = "糖尿病患者可服用二甲双胍"
    entities = ner_service.extract_entities(text)

    assert len(entities) >= 2
    assert any(e["type"] == "DISEASE" for e in entities)
    assert any(e["type"] == "DRUG" for e in entities)

def test_query_expansion():
    ner_service = NerService()
    result = ner_service.expand_query("感冒")

    assert "上呼吸道感染" in result["expanded_queries"]
    assert "流感" in result["expanded_queries"]
```

---

## 十、部署注意事项

1. **模型文件**: 需要提前下载 OpenMed 模型到指定路径
2. **GPU 要求**: 推荐使用 GPU 推理，配置 CUDA 环境
3. **内存**: 模型加载需要约 4GB 显存
4. **启动顺序**: NER 服务应在 FastAPI 启动时同步加载模型

---

## 十一、同义词映射表

### 11.1 映射表架构

**别名数据来源**:
- **药品别名**: 来自药品标准数据库（如药监局药品数据库）
- **疾病同义词**: 来自医学术语标准（如 ICD-10、SNOMED CT）
- **模型预测**: NER 模型输出的实体可能已包含同义词信息

**数据结构**:
```python
# 映射表 JSON 格式
{
  "DISEASE": {
    "糖尿病": {
      "standard_name": "糖尿病",
      "aliases": ["DM", "消渴症", "diabetes mellitus"],
      "icd_code": "E11"
    },
    "感冒": {
      "standard_name": "上呼吸道感染",
      "aliases": ["上感", "流感", "common cold", "URI"]
    }
  },
  "DRUG": {
    "二甲双胍": {
      "standard_name": "二甲双胍",
      "aliases": ["格华止", "甲福明", "Metformin"]
    },
    "泰诺": {
      "standard_name": "对乙酰氨基酚",
      "aliases": ["扑热息痛", "Acetaminophen", "Paracetamol"]
    }
  }
}
```

### 11.2 映射表加载

```python
class AliasMapping:
    def __init__(self, mapping_file: str):
        with open(mapping_file, 'r', encoding='utf-8') as f:
            self.mapping = json.load(f)

    def get_aliases(self, entity_type: str, text: str) -> List[str]:
        """获取实体的别名列表"""
        return self.mapping.get(entity_type, {}).get(text, {}).get("aliases", [])

    def get_standard_name(self, entity_type: str, text: str) -> str:
        """获取实体的标准名称"""
        return self.mapping.get(entity_type, {}).get(text, {}).get("standard_name", text)

    def expand_term(self, entity_type: str, text: str) -> List[str]:
        """扩展术语，返回自身 + 所有别名"""
        aliases = self.get_aliases(entity_type, text)
        return [text] + aliases
```

### 11.3 映射表维护

| 维护方式 | 说明 |
|----------|------|
| 初始加载 | 启动时从 JSON 文件加载映射表 |
| 热更新 | 支持通过 API 动态更新映射表 |
| 持久化 | 映射表变更自动写回文件 |
| 格式校验 | 启动时校验 JSON 格式，格式错误拒绝启动 |

### 11.4 API 接口

**更新映射表**:
```
POST /api/ner/aliases
{
  "entity_type": "DRUG",
  "term": "泰诺",
  "standard_name": "对乙酰氨基酚",
  "aliases": ["扑热息痛", "Acetaminophen"]
}
```

---

## 十二、Java Backend 集成

### 12.1 调用场景

| 场景 | 接口 | 用途 |
|------|------|------|
| 阶段1: 文档解析时 | `POST /api/ner/extract-batch` | 实体标注，存入Qdrant payload |
| 阶段2: 用户查询时 | `POST /api/ner/expand` | 查询扩展，提升召回 |

### 12.2 Java 客户端示例

```java
// Java 调用 ai-sdk NER 接口
@Service
public class NerClient {
    @Value("${ai-sdk.url}")
    private String aiSdkUrl;

    public List<EntityInfo> extractEntities(String text) {
        RestTemplate restTemplate = new RestTemplate();

        NerExtractRequest request = NerExtractRequest.builder()
                .text(text)
                .build();

        ResponseEntity<NerExtractResponse> response = restTemplate.postForEntity(
                aiSdkUrl + "/api/ner/extract",
                request,
                NerExtractResponse.class
        );

        return response.getBody().getEntities();
    }

    public NerExpandResponse expandQuery(String query) {
        RestTemplate restTemplate = new RestTemplate();

        NerExpandRequest request = NerExpandRequest.builder()
                .query(query)
                .expandTypes(Arrays.asList("DISEASE", "DRUG"))
                .build();

        return restTemplate.postForEntity(
                aiSdkUrl + "/api/ner/expand",
                request,
                NerExpandResponse.class
        ).getBody();
    }
}
```

### 12.3 文档解析集成

```java
// DocumentParseService 中集成 NER
public void parseWithNer(String documentId, String content) {
    // 1. OCR 识别
    OcrResultDTO ocrResult = ocrService.recognizePage(documentId, filePath, page);

    // 2. NER 实体识别
    List<EntityInfo> entities = nerClient.extractEntities(ocrResult.getOcrText());

    // 3. 分块时携带实体信息
    List<Chunk> chunks = textChunker.chunk(ocrResult.getOcrText());
    for (Chunk chunk : chunks) {
        chunk.setEntities(entities.stream()
                .filter(e -> isEntityInChunk(e, chunk))
                .collect(Collectors.toList()));
    }

    // 4. 存入 Qdrant，实体作为 payload
    documentVectorizationService.vectorize(chunks);
}
```

### 12.4 查询扩展集成

```java
// RAG 查询前增强
public List<String> expandQuery(String userQuery) {
    NerExpandResponse expandResult = nerClient.expandQuery(userQuery);
    return expandResult.getExpandedQueries();
}
```

---

## 十三、后续扩展

1. 支持实体链接（Entity Linking）到知识库
2. 支持自定义实体类型
3. 支持中文医学术语标准化映射表