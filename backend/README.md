# RAG Medical Knowledge Base Backend

> 基于 Spring Boot + Spring AI 的企业级 RAG 系统后端服务

## 技术栈

- **JDK**: Java 17+
- **框架**: Spring Boot 3.x + Spring AI 1.0.0 + MyBatis Plus 3.5.6
- **数据库**: PostgreSQL 14+ (Liquibase 迁移) + Qdrant 1.16+ (向量数据库)
- **缓存**: Redis 6+
- **对象存储**: MinIO
- **全文检索**: Elasticsearch 8.12+
- **AI 服务**: VLLM (LLM / Embedding / OCR, 统一经 AI SDK 网关 :28347)
- **API文档**: SpringDoc OpenAPI 3.0
- **测试**: JUnit 5 + Mockito + JaCoCo

## 项目结构

```
backend/
├── src/
│   ├── main/
│   │   ├── java/com/clacjz/medical/rag/
│   │   │   ├── config/          # 配置类
│   │   │   ├── constant/        # 常量定义
│   │   │   ├── context/         # 多租户 / 请求上下文
│   │   │   ├── controller/      # 控制器层
│   │   │   ├── exception/       # 异常处理
│   │   │   ├── health/          # 健康检查
│   │   │   ├── mapper/          # 数据访问层 (MyBatis Plus)
│   │   │   ├── model/           # 数据模型
│   │   │   │   ├── config/     # 模型层配置
│   │   │   │   ├── dto/        # 数据传输对象
│   │   │   │   ├── entity/      # 实体类
│   │   │   │   ├── enums/       # 枚举
│   │   │   │   └── vo/          # 视图对象
│   │   │   ├── monitor/         # 可观测性 / 指标
│   │   │   ├── security/        # 认证授权 (JWT)
│   │   │   ├── service/         # 服务层 (含 chunking/ parser/ 子包)
│   │   │   └── util/            # 工具类
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-dev.yml
│   │       ├── application-test.yml
│   │       ├── application-docker.yml
│   │       ├── application-prod.yml
│   │       ├── logback-spring.xml
│   │       ├── db/changelog/    # Liquibase 数据库迁移
│   │       └── swagger/         # 接口文档资源
│   └── test/
│       └── java/               # 单元测试
└── pom.xml
```

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+
- Docker & Docker Compose
- PostgreSQL 14+
- Redis 6+
- Qdrant 1.7+
- MinIO

### Docker 部署 (推荐)

```bash
# 1. 打包镜像
docker build -t rag-medical-backend:latest -f backend/Dockerfile .

# 2. 启动基础设施服务 (PostgreSQL, Redis, Qdrant, MinIO 等)
docker compose -f backend/docker/docker-compose.yml up -d

# 3. 启动后端应用
docker compose -f backend/docker/docker-compose-backend.yml up -d

# 4. 查看服务状态
docker compose -f backend/docker/docker-compose.yml ps

# 5. 查看后端日志
docker compose -f backend/docker/docker-compose-backend.yml logs -f

# 6. 停止所有服务
docker compose -f backend/docker/docker-compose-backend.yml down
docker compose -f backend/docker/docker-compose.yml down
```

### 传统方式

#### 构建项目

```bash
cd backend
mvn clean install
```

#### 运行测试

```bash
mvn test
```

#### 运行应用

```bash
# 开发环境
mvn spring-boot:run -P dev

# 测试环境
mvn spring-boot:run -P test

# 生产环境
mvn spring-boot:run -P prod
```

## 配置说明

### 开发环境配置 (application-dev.yml)

主要服务配置：

- **PostgreSQL**: localhost:5432
- **Redis**: localhost:6379
- **Qdrant**: localhost:6333
- **MinIO**: localhost:9000
- **AI SDK 统一服务（LLM / Embedding / OCR）**: localhost:28347

### 生产环境配置

通过环境变量注入敏感信息：

```bash
export POSTGRES_HOST=production-db-host
export POSTGRES_PORT=5432
export POSTGRES_DB=rag_medical
export POSTGRES_USERNAME=your_username
export POSTGRES_PASSWORD=your_password
export REDIS_HOST=production-redis-host
export REDIS_PASSWORD=your_redis_password
export QDRANT_HOST=production-qdrant-host
export LLM_HOST=production-llm-host
export LLM_PORT=11434
export JWT_SECRET=your-production-jwt-secret-key-256-bits
```

## API文档

启动应用后访问：

- Swagger UI: http://localhost:18080/api/v1/swagger-ui.html
- OpenAPI JSON: http://localhost:18080/api/v1/v3/api-docs

## 核心功能

### 1. 统一响应格式

所有API响应遵循统一格式：

```json
{
  "code": 200,
  "message": "success",
  "data": {},
  "timestamp": "2026-03-25T10:00:00.000Z"
}
```

### 2. 异常处理

内置异常类型：

- `BizException`: 业务异常
- `AiServiceException`: AI服务异常

全局异常处理器自动处理并返回统一格式的错误响应。

### 3. 日志管理

- 统一日志格式
- 敏感信息自动脱敏
- 分级日志输出
- 异步日志记录
- 日志文件滚动

### 4. 健康检查

- `/api/v1/health`: 综合健康检查
- `/api/v1/health/postgres`: PostgreSQL健康检查
- `/api/v1/health/redis`: Redis健康检查

## 测试覆盖率

运行测试并生成覆盖率报告：

```bash
mvn test
mvn jacoco:report
```

覆盖率报告位置：`target/site/jacoco/index.html`

## 开发规范

### 代码规范

- 遵循阿里巴巴Java开发手册
- 使用Lombok减少样板代码
- 所有API必须返回统一响应格式
- 异常必须被捕获和处理
- 日志必须脱敏敏感信息

### 提交规范

Commit Message格式：

```
feat: [模块] 简述 (关联 Checklist ID)
fix: [模块] 修复描述 (关联 Issue ID)
docs: 更新文档
test: 添加测试
refactor: 重构代码
```

### 分支策略

- `main`: 主分支（生产代码）
- `dev`: 开发分支
- `feature/*`: 功能分支
- `fix/*`: 修复分支

## 性能指标

根据SRS文档要求：

| 指标 | 目标值 |
|------|--------|
| API响应时间 (P95) | < 200ms |
| 向量化时间 (P95) | < 100ms/块 |
| 检索响应时间 (P95) | < 50ms |
| 问答首次响应 (P95) | < 3s |
| 问答完整响应 (P95) | < 10s |
| API QPS | ≥ 100 |
| 并发用户数 | ≥ 50 |

## 文档

详细设计文档位于项目根目录：

- `设计文档/LLD/RAG系统完整LLD_合并版.md`
- `RAG知识库系统开发Checklist.md`

## License

Apache 2.0
