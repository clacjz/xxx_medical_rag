# RAG Medical Knowledge Base System

> 基于 Spring AI 的企业级 RAG 知识库系统

## 项目结构

```
medical_rag/
├── backend/                    # Java后端项目 (Spring Boot 3.x)
│   ├── src/
│   │   ├── main/              # 主代码
│   │   └── test/              # 测试代码
│   ├── docker/                # local-docker 环境配置
│   │   ├── docker-compose.yml # 基础设施 (PG, Redis, Qdrant, MinIO, etcd, ES)
│   │   ├── docker-compose-backend.yml
│   │   └── .env.local.example # 本地环境变量模板 (复制为 .env.local)
│   └── pom.xml               # Maven配置
│
├── frontend/                   # Vue3 前端项目
│   └── Dockerfile             # 前端容器化
│
├── deploy/                     # 服务器部署配置
│   ├── dev/                   # dev环境
│   │   ├── docker-compose.yml
│   │   └── .env.dev.example   # 复制为 .env.dev
│   ├── prod/                  # prod环境
│   │   ├── docker-compose.yml
│   │   └── .env.prod.example  # 复制为 .env.prod
│   ├── monitoring/             # Prometheus + Grafana 配置
│   └── scripts/               # 部署脚本
│       └── deploy.sh          # 统一部署脚本
│
├── 设计文档/                    # 项目设计文档
│   ├── HLD/                  # 高层设计文档
│   ├── LLD/                  # 低层设计文档
│   └── SRS/                  # 需求规格说明
│
├── RAG知识库系统开发Checklist.md  # 开发任务清单
└── AGENTS.md                 # AI协作规范
```

## 当前进度

### 第一阶段：基础架构 (Day 1)
- [x] 项目脚手架完成
- [x] Spring AI 集成
- [x] 统一响应格式实现
- [x] 全局异常处理
- [x] 日志系统配置
- [x] 健康检查API
- [x] API文档生成
- [x] 单元测试 (69+用例)

**M1里程碑**: 2026-04-04

## 快速开始

项目支持三种部署场景，详见 [AGENTS.md](AGENTS.md#23-部署架构)。

### 统一部署脚本

```bash
# 使用方式
./deploy/scripts/deploy.sh <scenario> <command>

# 示例
./deploy/scripts/deploy.sh local-docker start   # 启动本地环境
./deploy/scripts/deploy.sh dev status           # 检查dev环境状态
./deploy/scripts/deploy.sh prod logs backend    # 查看prod后端日志
```

---

### 场景一：local-docker（本地完整开发环境）

**特点**：前后端 + 中间件都在本地 Docker

```bash
# . 启动基础设施 + 后端
cd backend/docker
cp .env.local.example .env.local   # 首次运行，按需修改
docker-compose --env-file .env.local -f docker-compose.yml up -d

# 2. 启动前端
cd frontend
npm install
npm run dev
```

**访问地址**：
- 前端：http://localhost:3000
- 后端API：http://localhost:18080
- Swagger：http://localhost:18080/swagger-ui.html
- Prometheus：http://localhost:19090
- Grafana：http://localhost:13000

---

### 场景二：dev（后端在服务器，前端本地）

**特点**：后端在服务器 Docker，前端在你本地用 npm 运行

**服务器端操作**：
```bash
ssh user@your-server-host
cd /path/to/medical_rag/deploy/dev
docker-compose --env-file .env.dev -f docker-compose.yml up -d
```

**本地操作**：
```bash
cd frontend
npm install
npm run dev
```

**访问地址**：
- 前端：http://localhost:5173
- 后端API：http://your-server-host:18080
- Swagger：http://your-server-host:18080/swagger-ui.html
- Prometheus：http://your-server-host:19090
- Grafana：http://your-server-host:13000

---

### 场景三：prod（生产环境）

**特点**：前后端 + Qdrant + ES 全部在服务器 Docker

**1. 本地构建镜像**：
```bash
./deploy/build.sh build
```

**2. 上传代码到服务器**：
```bash
scp -r deploy/ backend/ frontend/ user@your-server-host:/path/to/project/
```

**3. 服务器启动服务**：
```bash
cd /path/to/project/deploy/prod
# 编辑 .env.prod 填入真实密码和密钥
vim .env.prod
# 启动
docker-compose --env-file .env.prod -f docker-compose.yml up -d
```

**访问地址**：
- 前端：http://your-server-host:18030
- 后端API：http://your-server-host:18080
- Swagger：http://your-server-host:18080/swagger-ui.html
- Prometheus：http://your-server-host:19090
- Grafana：http://your-server-host:13000

---

### 传统方式（不推荐）

```bash
cd backend
mvn clean install
mvn spring-boot:run -P dev
```

## 设计文档

### 高层设计 (HLD)
- [完整HLD文档](设计文档%2FHLD%2FRAG知识库系统完整HLD文档_V2.md)
- [成本估算](设计文档%2FHLD%2FRAG知识库系统HLD_成本估算_单人版.md)

### 低层设计 (LLD)
- [完整LLD文档](设计文档%2FLLD%2FRAG系统完整LLD_合并版.md)
- [数据库和API设计](设计文档%2FLLD%2FRAG知识库系统LLD_第一部分_数据库和API设计.md)
- [核心模块设计](设计文档%2FLLD%2FRAG知识库系统LLD_第二部分_核心模块设计.md)
- [性能监控和安全](设计文档%2FLLD%2FRAG知识库LLD_第三部分_性能监控和安全.md)

### 需求规格 (SRS)
- [SRS文档](设计文档%2FSRS%2FRAG知识库系统SRS文档_V1.md)

## 项目计划

| 阶段 | 时间 | 目标 |
|------|------|------|
| **第一阶段** | Day 1-8 | 基础架构完成 |
| **第二阶段** | Day 9-23 | 核心功能完成 |
| **第三阶段** | Day 24-35 | AI可观测性完成 |
| **第四阶段** | Day 36-46 | 系统集成完成 |
| **第五阶段** | Day 47-61 | UAT与上线 |

**详细计划**: [开发Checklist](RAG知识库系统开发Checklist.md)

## 技术栈

### 后端技术
- **JDK**: Java 17+
- **框架**: Spring Boot 3.2.5
- **AI框架**: Spring AI 1.0.0
- **数据库**: PostgreSQL 14+
- **向量库**: Qdrant 1.7+
- **缓存**: Redis 6+
- **对象存储**: MinIO
- **构建工具**: Maven
- **API文档**: SpringDoc OpenAPI

### AI服务
- **LLM**: VLLM (Qwen3.6-27BB, 默认端口11434，可通过环境变量配置)
- **Embedding**: BGE v1.5
- **OCR**: VLLM GLM-OCR (默认端口38247)

## 性能指标

| 指标 | 目标值 |
|------|--------|
| API响应时间 (P95) | < 200ms |
| 向量化时间 (P95) | < 100ms/块 |
| 检索响应时间 (P95) | < 50ms |
| 问答首次响应 (P95) | < 3s |
| 问答完整响应 (P95) | < 10s |
| API QPS | ≥ 100 |
| 并发用户数 | ≥ 50 |

## 学习资源

### 后端开发
- [Backend README](backend/README.md)
- [快速开始](backend/QUICKSTART.md)

### AI协作规范
- [AGENTS.md](AGENTS.md) - AI助手协作规范

## 联系方式

- **开发团队**: RAG Development Team
- **邮箱**: dev@example.com
- **文档**: https://example.com

## 许可证

Apache 2.0

---

**项目版本**: 1.0.0-SNAPSHOT
**最后更新**: 2026-03-25
**项目周期**: 60个工作日
