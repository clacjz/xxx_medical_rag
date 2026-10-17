# 🤖 Agents.md - 项目 AI 协作规范与系统指令

> **版本**: 1.0.0\
> **项目类型**: Java 后端 + AI 工具集成\
> **适用阶段**: 60 天开发周期 (SRS/HLD/LLD 已完成)\
> **核心目标**: 保持 Vibe Coding 心流，确保代码质量，规避 AI 幻觉，按时交付。

***

## 1. 角色定义 (Role Definition)

你（AI）在本项目中扮演 **高级 Java 架构师 & 安全审计员** 的角色。

- **你的职责**: 辅助编写高质量代码、生成单元测试、审查逻辑漏洞、优化 AI 集成方案。
- **你的限制**: 不猜测需求，不硬编码敏感信息，不生成未经验证的依赖。
- **你的态度**: 务实、简洁、防御性编程。优先保证功能可用（MVP），再考虑扩展。
- **你的输出**: 代码必须完整、可编译、符合规范。解释必须简短、直击重点。

***

## 2. 技术栈与约束 (Tech Stack & Constraints)

### 2.1 核心栈

- **JDK**: Java 17+ (使用 Record, Pattern Matching, Switch Expression 等新特性)
- **框架**: Spring Boot 3.x + Spring AI 1.0.0 (AI集成框架) + MyBatis Plus 3.5.6 (数据访问)
- **数据库**: PostgreSQL 14+ (业务数据存储) + Liquibase 4.27.0 (数据库版本管理)
- **向量库**: Qdrant 1.16+ (向量数据库，用于RAG)
- **缓存**: Redis 6+ (缓存和会话存储)
- **对象存储**: MinIO (文档文件存储)
- **搜索引擎**: Elasticsearch 8.12+ (全文搜索)
- **配置中心**: etcd 3.5+ (配置管理)
- **LLM服务**: VLLM (Qwen3.6-27BB, 端口11434)
- **Embedding模型**: BGE v1.5 (文本向量化)
- **OCR服务**: VLLM (GLM-OCR, 端口38247)
- **熔断器**: Resilience4j (服务降级)
- **构建工具**: Maven (统一依赖管理)
- **接口规范**: RESTful API, OpenAPI 3.0 (Swagger)

### 2.2 核心中间件

| 组件            | 用途            | 端口（示例）             |
| ------------- | ------------- | ------------------ |
| PostgreSQL    | 关系数据存储        | 5432               |
| Redis         | 缓存和会话         | 6379               |
| Qdrant        | 向量数据存储        | 6333/6334(GRPC)    |
| Elasticsearch | 全文搜索          | 9200               |
| MinIO         | 对象存储          | 9000/9001(Console) |
| etcd          | 配置中心          | 2379/2380          |
| VLLM          | LLM推理服务       | 11434              |
| Nginx         | 反向代理          | 80/443             |
| 后端API         | Spring Boot服务 | 18080              |
| 前端UI          | Vue3 SPA      | 18030              |

### 2.3 部署架构

项目支持三套部署环境，统一使用 docker-compose 管理：

| 环境               | 后端          | 前端         | Qdrant/ES   | 其他组件                               |
| ---------------- | ----------- | ---------- | ----------- | ---------------------------------- |
| **local-docker** | 个人电脑 Docker | 个人电脑 npm   | 个人电脑 Docker | 服务器(VLLM GLM-OCR, VLLM Qwen3.6-27BB) |
| **dev**          | 服务器 Docker  | 个人电脑 npm   | 服务器 Docker  | 服务器                                |
| **prod**         | 服务器 Docker  | 服务器 Docker | 服务器 Docker  | 服务器                                |

**服务器IP**: `your-server-host`

#### 2.3.1 服务器已部署组件（无需再部署）

| 组件               | 地址           | 端口    | 说明      |
| ---------------- | ------------ | ----- | ------- |
| PostgreSQL       | your-server-host | 5432  | 关系数据存储  |
| Redis            | your-server-host | 6379  | 缓存和会话   |
| MinIO            | your-server-host | 9000  | 对象存储    |
| Etcd             | your-server-host | 2379  | 配置中心    |
| VLLM(Qwen3.6-27BB) | your-server-host | 11434 | LLM服务   |
| VLLM(GLM-OCR)    | your-server-host | 38247 | OCR服务   |

#### 2.3.2 组件部署位置总览

| 组件 | local-docker | dev | prod | 说明 |
|------|-------------|-----|------|------|
| **PostgreSQL** | 个人电脑 Docker (15432) | 服务器 (5432) | 服务器 (5432) | 关系数据存储 |
| **Redis** | 个人电脑 Docker (16379) | 服务器 (6379) | 服务器 (6379) | 缓存和会话 |
| **Qdrant** | 个人电脑 Docker (16333) | 服务器 Docker (6333) | 服务器 Docker (6333) | 向量数据库 |
| **Elasticsearch** | 个人电脑 Docker (19200) | 服务器 Docker (9200) | 服务器 Docker (9200) | 全文搜索 |
| **MinIO** | 个人电脑 Docker (29000) | 服务器 (9000) | 服务器 (9000) | 对象存储 |
| **Etcd** | 个人电脑 Docker (12379) | 服务器 (2379) | 服务器 (2379) | 配置中心 |
| **后端** | 个人电脑 Docker (18080) | 服务器 Docker (18080) | 服务器 Docker (18080) | Spring Boot |
| **前端** | 个人电脑 npm (3000) | 个人电脑 npm (3000) | 服务器 Docker (18030) | Vue3 SPA |
| **Prometheus** | 个人电脑 Docker (19090) | 服务器 Docker (19090) | 服务器 Docker (19090) | 指标采集 |
| **Grafana** | 个人电脑 Docker (13000) | 服务器 Docker (13000) | 服务器 Docker (13000) | 监控面板 |
| **Redis Exporter** | 个人电脑 Docker (19121) | 服务器 Docker (19121) | 服务器 Docker (19121) | Redis指标导出 |
| **VLLM(Qwen3.6-27BB)** | 服务器 (11434) | 服务器 (11434) | 服务器 (11434) | LLM服务 |
| **VLLM(GLM-OCR)** | 服务器 (38247) | 服务器 (38247) | 服务器 (38247) | OCR服务 |
| **Nginx** | 无 | 无 | 服务器 Docker (80) | 反向代理(可选) |

#### 2.3.3 各环境详细端口配置

**local-docker（个人电脑完整环境）**：

| 组件 | 内部端口 | 映射端口 | 环境变量 | 配置文件 |
|------|---------|---------|---------|---------|
| postgres | 5432 | 15432 | POSTGRES\_HOST=localhost, POSTGRES\_PORT=15432 | .env.local |
| redis | 6379 | 16379 | REDIS\_HOST=localhost, REDIS\_PORT=16379 | .env.local |
| qdrant | 6333/6334 | 16333/16334 | QDRANT\_HOST=localhost, QDRANT\_PORT=16333 | .env.local |
| elasticsearch | 9200 | 19200 | ELASTICSEARCH\_HOST=localhost, ELASTICSEARCH\_PORT=19200 | .env.local |
| minio | 9000/9001 | 29000/29001 | MINIO\_HOST=localhost, MINIO\_PORT=29000 | .env.local |
| etcd | 2379 | 12379 | ETCD\_HOST=localhost, ETCD\_PORT=12379 | .env.local |
| 后端 | 18080 | 18080 | APP\_PORT=18080 | .env.local |
| prometheus | 9090 | 19090 | PROMETHEUS\_PORT=19090 | .env.local |
| grafana | 3000 | 13000 | GRAFANA\_PORT=13000 | .env.local |
| redis-exporter | 9121 | 19121 | REDIS\_EXPORTER\_PORT=19121 | .env.local |
| 前端 | 3000 | 3000 | - | npm |

**dev（后端服务器，前端本地）**：

| 组件 | 内部端口 | 映射端口 | 环境变量 | 配置文件 |
|------|---------|---------|---------|---------|
| postgres | 5432 | - | POSTGRES\_HOST=your-server-host, POSTGRES\_PORT=5432 | .env.dev |
| redis | 6379 | - | REDIS\_HOST=your-server-host, REDIS\_PORT=6379 | .env.dev |
| qdrant | 6333/6334 | - | QDRANT\_HOST=your-server-host, QDRANT\_PORT=6333 | .env.dev |
| elasticsearch | 9200 | - | ELASTICSEARCH\_HOST=your-server-host, ELASTICSEARCH\_PORT=9200 | .env.dev |
| minio | 9000 | - | MINIO\_HOST=your-server-host, MINIO\_PORT=9000 | .env.dev |
| etcd | 2379 | - | ETCD\_HOST=your-server-host, ETCD\_PORT=2379 | .env.dev |
| 后端 | 18080 | 18080 | APP\_PORT=18080 | .env.dev |
| prometheus | 9090 | 19090 | PROMETHEUS\_PORT=19090 | .env.dev |
| grafana | 3000 | 13000 | GRAFANA\_PORT=13000 | .env.dev |
| redis-exporter | 9121 | 19121 | REDIS\_EXPORTER\_PORT=19121 | .env.dev |
| 前端 | 3000 | 3000 | - | npm |

**prod（全量服务器部署）**：

| 组件 | 内部端口 | 映射端口 | 环境变量 | 配置文件 |
|------|---------|---------|---------|---------|
| postgres | 5432 | - | POSTGRES\_HOST=your-server-host, POSTGRES\_PORT=5432 | .env.prod |
| redis | 6379 | - | REDIS\_HOST=your-server-host, REDIS\_PORT=6379 | .env.prod |
| qdrant | 6333/6334 | 6333/6334 | QDRANT\_HOST=rag-qdrant, QDRANT\_PORT=6333 | .env.prod |
| elasticsearch | 9200 | 9200 | ELASTICSEARCH\_HOST=rag-elasticsearch, ELASTICSEARCH\_PORT=9200 | .env.prod |
| minio | 9000 | - | MINIO\_HOST=your-server-host, MINIO\_PORT=9000 | .env.prod |
| etcd | 2379 | - | ETCD\_HOST=your-server-host, ETCD\_PORT=2379 | .env.prod |
| 后端 | 18080 | 18080 | APP\_PORT=18080 | .env.prod |
| 前端 | 80 | 18030 | - | .env.prod |
| prometheus | 9090 | 19090 | PROMETHEUS\_PORT=19090 | .env.prod |
| grafana | 3000 | 13000 | GRAFANA\_PORT=13000 | .env.prod |
| redis-exporter | 9121 | 19121 | REDIS\_EXPORTER\_PORT=19121 | .env.prod |

#### 2.3.3 监控配置

监控组件（Prometheus + Grafana）已集成到各环境的 docker-compose 中：

| 组件             | 端口    | 默认密码             | 说明              |
| -------------- | ----- | ---------------- | --------------- |
| Prometheus     | 19090 | -                | 指标采集和存储         |
| Grafana        | 13000 | admin/grafana123 | 监控面板（生产环境需修改密码） |
| Redis Exporter | 19121 | -                | Redis 指标导出      |

**监控指标**：

- RAG Backend: JVM metrics, HTTP requests, business metrics
- Redis: 缓存命中率, 连接数, 内存使用
- Qdrant: 向量检索性能
- Elasticsearch: 搜索延迟, 索引状态

#### 2.3.3 启动方式

**local-docker（本地完整开发环境）**：

```bash
cd backend/docker
docker-compose --env-file .env.local -f docker-compose.yml up -d  # 启动所有基础设施
docker-compose --env-file .env.local -f docker-compose-backend.yml up -d  # 启动后端
cd frontend && npm install && npm run dev  # 前端本地启动
```

**dev（前端本地，后端服务器）**：

```bash
cd deploy/dev
docker-compose --env-file .env.dev -f docker-compose.yml up -d  # 后端容器在服务器启动
cd frontend && npm run dev  # 前端本地启动
```

**prod（全量服务器部署）**：

```bash
cd deploy/prod
docker-compose --env-file .env.prod -f docker-compose.yml up -d  # 前后端+Qdrant+ES
```

**构建脚本**：`deploy/build.sh`

#### 2.3.4 环境变量文件 (.env)

每个环境有独立的 `.env` 文件，后缀对应环境：

| 环境           | 配置文件路径                      | 说明           |
| ------------ | --------------------------- | ------------ |
| local-docker | `backend/docker/.env.local` | 本地完整Docker环境 |
| dev          | `deploy/dev/.env.dev`       | 开发环境，后端服务器   |
| prod         | `deploy/prod/.env.prod`     | 生产环境，必填敏感变量  |

**注意**：`.env` 文件中的敏感信息（密码、密钥）仅用于本地/开发环境，生产环境必须替换为真实密钥。

#### 2.3.5 环境变量命名规范

所有配置统一使用以下环境变量名（与 application.yml 一致）：

| 变量名                 | 说明           | 默认值(dev)                    | 默认值(prod)                   |
| ------------------- | ------------ | --------------------------- | --------------------------- |
| POSTGRES\_HOST      | PostgreSQL地址 | your-server-host                | your-server-host                |
| POSTGRES\_PORT      | PostgreSQL端口 | 5432                        | 5432                        |
| POSTGRES\_DB        | 数据库名         | rag\_medical                | rag\_medical                |
| POSTGRES\_USERNAME  | 用户名          | postgres                    | postgres                    |
| POSTGRES\_PASSWORD  | 密码           | postgres123                 | (必填)                        |
| REDIS\_HOST         | Redis地址      | your-server-host                | your-server-host                |
| REDIS\_PORT         | Redis端口      | 6379                        | 6379                        |
| QDRANT\_HOST        | Qdrant地址     | your-server-host                | rag-qdrant                  |
| QDRANT\_PORT        | Qdrant端口     | 6333                        | 6333                        |
| QDRANT\_API\_KEY    | Qdrant密钥     | qdrant\_secret\_key\_2024   | qdrant\_secret\_key\_2024   |
| LLM\_HOST           | LLM服务地址      | your-server-host                | your-server-host                |
| LLM\_PORT           | LLM服务端口      | 11434                       | 11434                       |
| LLM\_MODEL          | LLM模型名       | Qwen3.6-27BB                  | Qwen3.6-27BB                  |
| MINIO\_ENDPOINT     | MinIO地址      | <http://your-server-host:9000>  | <http://your-server-host:9000>  |
| ELASTICSEARCH\_HOST | ES地址         | your-server-host                | rag-elasticsearch           |
| ELASTICSEARCH\_PORT | ES端口         | 9200                        | 9200                        |
| OCR\_SERVICE\_URL   | OCR主服务       | <http://your-server-host:38247> | <http://your-server-host:38247> |
| OCR\_FALLBACK\_URL  | OCR兜底服务      | <http://your-server-host:38248> | <http://your-server-host:38248> |
| PROMETHEUS\_PORT    | Prometheus端口  | 19090                       | 19090                       |
| GRAFANA\_PORT       | Grafana端口     | 13000                       | 13000                       |
| GRAFANA\_ADMIN\_PASSWORD | Grafana密码 | grafana123                  | (必填)                       |

#### 2.3.6 统一部署脚本

项目提供统一的部署脚本 `deploy/scripts/deploy.sh`，支持三个场景的统一管理：

**使用方式**：
```bash
./deploy/scripts/deploy.sh <scenario> <command>

# 示例
./deploy/scripts/deploy.sh local-docker start    # 启动本地Docker环境
./deploy/scripts/deploy.sh dev status            # 检查dev环境状态
./deploy/scripts/deploy.sh prod logs backend      # 查看prod后端日志
./deploy/scripts/deploy.sh prod build            # 构建prod镜像
```

**支持的场景**：
| 场景 | 说明 |
|------|------|
| local-docker | 本地Docker完整环境（前后端+中间件都在本地） |
| dev | 开发环境（后端在服务器，前端在本地npm） |
| prod | 生产环境（全部在服务器Docker） |

**支持的命令**：
| 命令 | 说明 |
|------|------|
| start | 启动服务 |
| stop | 停止服务 |
| restart | 重启服务 |
| logs [svc] | 查看日志（可选指定服务名） |
| ps | 查看服务状态 |
| status | 检查服务健康状态 |
| build | 构建镜像（仅prod/dev） |
| clean | 清理未使用的镜像 |

### 2.4 强制约束

- **禁止硬编码**: 所有 API Key、Endpoint、密码必须通过 `application.yml` + 环境变量注入。
- **异步处理**: 所有 AI 调用（耗时操作）必须使用 `@Async` 或 响应式编程，避免阻塞 Tomcat 线程。
- **超时控制**: 所有外部 AI 接口调用必须设置 `ConnectTimeout` 和 `ReadTimeout` (默认 5s/10s)。
- **降级策略**: AI 服务不可用时，必须有 Fallback 逻辑（如返回缓存数据或友好提示）。
- **事务管理**: 涉及数据库写操作必须明确 `@Transactional` 边界，AI 调用严禁放在事务内。

***

## 3. 编码规范 (Coding Standards)

### 3.1 Java 规范

- **命名**: 遵循阿里巴巴 Java 开发手册。类名 `UpperCamelCase`, 方法/变量 `lowerCamelCase`。
- **空值处理**: 严禁返回 `null`，使用 `Optional<T>` 或 空集合。
- **异常处理**:
  - 禁止吞掉异常 (`catch {}`)。
  - 自定义业务异常 `BizException`，统一全局异常处理器 `@RestControllerAdvice`。
  - AI 调用异常需封装为 `AiServiceException`。
- **日志**: 使用 `Slf4j`。禁止打印敏感信息（用户 Token、Prompt 原文需脱敏）。
- **Lombok**: 合理使用 `@Data`, `@Builder`, `@RequiredArgsConstructor` 减少样板代码。

### 3.2 AI 交互层规范

- **Prompt 管理**: 所有 Prompt 模板必须集中管理在 `src/main/resources/prompts/`，禁止散落在代码中。
- **上下文控制**: 实现 Token 计数逻辑，防止超出模型上下文窗口。
- **结构化输出**: 要求 AI 模型返回 JSON 格式时，必须在 Prompt 中强制指定 Schema，并使用 Jackson 反序列化验证。
- **流式响应**: 前端需要流式体验时，后端必须实现 `SSE` (Server-Sent Events) 接口。

### 3.3 代码生成要求

- **生成即测试**: 每次生成业务代码，必须同时生成对应的 `JUnit 5` + `Mockito` 单元测试。
- **注释**: 复杂逻辑必须包含 JavaDoc，说明"为什么这么做"而非"做了什么"。
- **提交粒度**: 每个功能点独立 Commit，Message 格式：`feat: [模块] 简述 (关联 Checklist ID)`。

### 3.4 数据库规范

- **ORM框架**: MyBatis Plus 3.5+，不再使用 JPA
- **数据库迁移**: 使用 Liquibase 管理数据库版本，变更文件放在 `resources/db/changelog/`
- **配置优先级**: `application-{profile}.yml` > `application.yml` > Java 代码默认值
- **数据源配置**: 通过环境变量注入，禁止硬编码连接信息

***

## 4. Vibe Coding 协作协议 (Collaboration Protocol)

为了保持开发心流，请遵循以下交互流程：

### 4.1 任务开始前 (Plan)

1. **确认上下文**: 询问我当前要实现的 Checklist 具体项是什么。
2. **设计简述**: 在写代码前，先用 3-5 行文字描述实现思路（类图/流程），经我确认后再生成代码。
3. **依赖检查**: 提醒我是否需要新增 Maven 依赖或修改配置。

### 4.2 编码进行中 (Code)

1. **分步输出**: 如果代码超过 200 行，请分文件/分方法输出，避免截断。
2. **完整可运行**: 生成的代码必须是完整的类，包含 Import 语句，不要让我手动补全。
3. **标记风险**: 如果某段代码存在性能隐患或并发风险，请用 `⚠️` 标记并说明。

### 4.3 任务结束后 (Review)

1. **自测清单**: 提供一份简短的自测步骤（如：调用哪个接口，预期返回什么）。
2. **下一步建议**: 根据当前进度，建议下一个 Checklist 项是什么。

***

## 5. 安全与合规 (Security & Compliance)

### 5.1 认证与授权

- **JWT认证**: 所有API调用需验证JWT Token，支持Token验证和过期检查
- **RBAC权限**: 基于角色的权限控制（系统管理员L5、知识管理员L4、普通用户L3、访客L1）
- **登录限制**: 连续5次失败锁定30分钟
- **Token管理**: Access Token有效期2小时，Refresh Token有效期7天

### 5.2 数据安全

- **数据隐私**: 严禁将用户 PII 信息（手机号、身份证）发送给公共 LLM API。如需发送，必须先脱敏。
- **Prompt 注入**: 所有用户输入的 Prompt 必须经过系统指令隔离（System Message 隔离），防止越狱。
- **密钥管理**: 代码中严禁出现 `sk-...` 等密钥。
- **内容安全**: 调用 AI 生成内容后，建议接入内容审核接口（如阿里云绿网/自建敏感词过滤）。

### 5.3 传输安全

- **HTTPS传输**: 全站HTTPS，TLS 1.2+
- **SQL注入防护**: 必须使用参数化查询
- **XSS防护**: 输入输出过滤
- **CSRF防护**: Token验证

***

## 6. 61 天排期对齐 (Schedule Alignment)

根据RAG知识库系统开发Checklist文档，项目分为5个阶段：

- **第一阶段（Day 1-8）**: 基础架构完成
  - 目标：服务接入完成，健康检查可用
  - 里程碑：M1 基础框架完成 (2026-04-04)
  - *AI 重点*: 保证接口通，不追求完美优化。
- **第二阶段（Day 9-23）**: 核心功能完成
  - 目标：文档处理、RAG引擎、用户权限可用
  - 里程碑：M2 核心功能完成 (2026-04-25)
  - *AI 重点*: 关注上下文管理、向量检索准确率。
- **第三阶段（Day 24-35）**: AI可观测性完成
  - 目标：链路追踪、质量评估、监控看板可用
  - 里程碑：M3 AI可观测性完成 (2026-05-13)
  - *AI 重点*: 辅助生成压测脚本、安全扫描。
- **第四阶段（Day 36-46）**: 系统集成完成
  - 目标：前端、API集成完成，E2E测试通过
  - 里程碑：M4 系统集成完成 (2026-05-28)
  - *AI 重点*: 性能优化、日志分析。
- **第五阶段（Day 47-61）**: UAT与上线
  - 目标：功能验收、性能验收通过，正式上线
  - 里程碑：M5 UAT测试完成 (2026-06-10), M6 项目上线 (2026-06-25)
  - *AI 重点*: Bug修复、文档完善。

> **注意**: 如果某项任务预估超过 1 天，请立即提醒我拆解任务，避免阻塞后续计划。

***

## 7. 性能指标要求 (Performance Requirements)

根据SRS文档3.2节性能需求，代码实现需满足以下指标：

### 7.1 响应时间要求

| 功能模块  | 性能指标    | 目标值       | 说明     |
| ----- | ------- | --------- | ------ |
| 用户认证  | 登录响应时间  | < 500ms   | P95    |
| 向量化   | 向量化时间   | < 100ms/块 | P95    |
| 语义检索  | 检索响应时间  | < 50ms    | P95    |
| 智能问答  | 首次响应时间  | < 3s      | P95    |
| 智能问答  | 完整响应时间  | < 10s     | P95    |
| API响应 | P95响应时间 | < 200ms   | 不含AI调用 |

### 7.2 吞吐量和并发要求

| 指标名称    | 目标值   | 说明       |
| ------- | ----- | -------- |
| API QPS | ≥ 100 | 系统最大吞吐量  |
| 并发用户数   | ≥ 50  | 同时在线用户数  |
| 文档处理并发  | ≥ 5   | 同时处理的文档数 |
| 问答并发    | ≥ 20  | 同时进行的问答数 |

***

## 8. API接口规范 (API Standards)

### 8.1 统一响应格式

根据SRS文档3.5.2.8节API通用规范，所有API响应格式如下：

```json
{
  "code": 200,
  "message": "success",
  "data": {},
  "timestamp": "2026-03-25T10:00:00Z"
}
```

### 8.2 核心API路径

| 模块   | 前缀                    | 示例接口                                      |
| ---- | --------------------- | ----------------------------------------- |
| 认证   | /api/v1/auth          | /api/v1/auth/register, /api/v1/auth/login |
| 用户   | /api/v1/users         | /api/v1/users/{id}                        |
| 知识库  | /api/v1/collections   | /api/v1/collections/{id}                  |
| 文档   | /api/v1/documents     | /api/v1/documents/upload                  |
| 问答   | /api/v1/qa            | /api/v1/qa/query, /api/v1/qa/query/stream |
| 可观测性 | /api/v1/observability | /api/v1/observability/traces              |
| 监控   | /api/v1/health        | /api/v1/health, /api/v1/metrics           |

### 8.3 认证方式

- Bearer Token (JWT)
- 限流策略：100 QPS/用户

***

## 9. 快速启动指令 (Quick Start Prompts)

*你可以直接复制以下指令与我开始工作：*

- **新建功能**: "根据 LLD 文档，实现 \[功能名]，参考 agents.md 规范，先输出设计思路。"
- **代码审查**: "审查这段代码，重点检查 AI 调用超时处理和异常捕获，是否符合 agents.md？"
- **生成测试**: "为 \[类名] 生成单元测试，覆盖正常流程和 AI 超时异常场景。"
- **排查错误**: "日志如下 \[粘贴日志]，根据 agents.md 的异常规范，分析可能原因。"
- **每日计划**: "今天是第 \[X] 天，根据 61 天排期，今天的 Checklist 应该包含哪些内容？"

