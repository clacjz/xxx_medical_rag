# RAG Medical Backend - 快速入门指南

## 前置要求

在开始之前，请确保您的系统满足以下要求：

### 必需软件

1. **JDK 17 或更高版本**
   ```bash
   # 检查Java版本
   java -version
   # 输出应类似: openjdk version "17.0.x"
   ```

2. **Maven 3.8 或更高版本**
   ```bash
   # 检查Maven版本
   mvn -version
   # 输出应类似: Apache Maven 3.8.x
   ```

3. **数据库和缓存服务** (开发环境)
   - PostgreSQL 14+ (端口: 5432)
   - Redis 6+ (端口: 6379)
   - Qdrant 1.7+ (端口: 6333)
   - MinIO (端口: 9000)

## 安装步骤

### . 克隆或下载项目

```bash
cd /your/project/path
# 如果使用Git
git clone <repository-url> backend
cd backend
```

### 2. 安装JDK 17 (如果未安装)

**Windows:**
- 下载地址: https://adoptium.net/
- 安装并配置 JAVA_HOME 环境变量

**macOS:**
```bash
brew install openjdk@17
```

**Linux (Ubuntu):**
```bash
sudo apt update
sudo apt install openjdk-17-jdk
```

### 3. 安装Maven (如果未安装)

**Windows:**
- 下载地址: https://maven.apache.org/download.cgi
- 解压并配置 M2_HOME 环境变量

**macOS:**
```bash
brew install maven
```

**Linux (Ubuntu):**
```bash
sudo apt install maven
```

### 4. 启动依赖服务 (开发环境)

使用Docker启动所有依赖服务：

```bash
docker run -d --name postgres \
  -e POSTGRES_DB=rag_medical_dev \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  postgres:14

docker run -d --name redis \
  -p 6379:6379 \
  redis:6

docker run -d --name qdrant \
  -p 6333:6333 \
  -p 6334:6334 \
  qdrant/qdrant:v1.16.3

docker run -d --name minio \
  -p 9000:9000 \
  -p 9001:9001 \
  -e MINIO_ROOT_USER=minioadmin \
  -e MINIO_ROOT_PASSWORD=minioadmin \
  minio/minio server /data --console-address ":9001"
```

### 5. 编译项目

```bash
cd backend
mvn clean compile
```

### 6. 运行测试

```bash
mvn test
```

如果一切正常，您应该看到类似输出：

```
Tests run: 69, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### 7. 启动应用 (开发模式)

```bash
mvn spring-boot:run -P dev
```

应用将在 http://localhost:8080 启动

### 8. 访问API文档

启动应用后，打开浏览器访问：

- Swagger UI: http://localhost:8080/api/v1/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/api/v1/v3/api-docs

## 测试API

### 健康检查

```bash
# 综合健康检查
curl http://localhost:8080/api/v1/health

# PostgreSQL健康检查
curl http://localhost:8080/api/v1/health/postgres

# Redis健康检查
curl http://localhost:8080/api/v1/health/redis
```

### 示例API

```bash
# 健康检查响应
curl http://localhost:8080/api/v1/sample/health

# 获取用户
curl http://localhost:8080/api/v1/sample/user/1

# 计算
curl -X POST "http://localhost:8080/api/v1/sample/calculate?a=10&b=5&operation=add"
```

## 查看测试覆盖率

```bash
mvn test
mvn jacoco:report
```

报告将生成在: `target/site/jacoco/index.html`

## IDE配置

### IntelliJ IDEA

1. File → Open → 选择 `backend` 文件夹
2. IDEA会自动识别Maven项目
3. 安装Lombok插件 (Settings → Plugins → Lombok)
4. 启用注解处理器 (Settings → Build → Compiler → Annotation Processors → Enable)

### Eclipse

1. File → Import → Maven → Existing Maven Projects
2. 选择 `backend` 文件夹
3. 安装Lombok (双击 `lombok.jar` → Install)
4. 重启Eclipse

## 常见问题

### . Maven构建失败

**问题**: `Could not resolve dependencies`

**解决方案**:
```bash
mvn clean install -U
```

### 2. 端口被占用

**问题**: `Port 8080 was already in use`

**解决方案**:
```bash
# 修改端口
# 在 application.yml 中添加:
server:
  port: 8081

# 或查找并终止占用进程
netstat -ano | findstr :8080
taskkill /PID <pid> /F
```

### 3. 数据库连接失败

**问题**: `Connection refused`

**解决方案**:
- 确认PostgreSQL服务已启动
- 检查 `application-dev.yml` 中的连接信息
- 确认网络连接和防火墙设置

### 4. Lombok不工作

**问题**: 类找不到getter/setter方法

**解决方案**:
- 确认Lombok插件已安装
- 确认注解处理器已启用
- 尝试: File → Invalidate Caches → Restart

## 下一步

完成基础脚手架后，您可以继续开发Checklist中的以下模块：

### Day 9-23: 核心功能
- [ ] 用户认证模块 (注册/登录/JWT)
- [ ] 文档管理模块 (上传/解析/分块)
- [ ] 知识库管理模块
- [ ] RAG问答模块

### Day 24-35: AI可观测性
- [ ] 链路追踪
- [ ] 质量评估
- [ ] 监控告警

### Day 36-46: 系统集成
- [ ] 前端开发
- [ ] API集成测试
- [ ] E2E测试

## 获取帮助

- 查看 [README.md](./README.md) 了解项目详情
- 查看 [PROJECT_STRUCTURE.md](./PROJECT_STRUCTURE.md) 了解项目结构
- 查看 [RAG知识库系统开发Checklist.md](../RAG知识库系统开发Checklist.md) 了解开发计划

## 验收标准

脚手架完成后应满足：

- [x] 项目结构清晰，符合多层架构
- [x] 所有单元测试通过 (覆盖率≥80%)
- [x] 应用成功启动
- [x] 健康检查API可用
- [x] Swagger文档可访问
- [x] 日志系统正常工作
- [x] 异常处理机制完善

---

**准备好开始了吗？** 按照以上步骤配置环境，然后运行 `mvn spring-boot:run -P dev` 启动应用！
