# RAG知识库系统完整详细设计文档（LLD）

> **文档类型**: 低层设计（LLD）
> **编制日期**: 2026-03-26
> **版本**: V0.3
> **参考**: HLD文档_V0.3、SRS文档_V0.3

---

# 目录

1. [第一部分：数据库设计](#第一部分数据库设计)
   - [一、数据库设计](#一数据库设计)
   - [二、增强RBAC权限数据库](#二增强rbac权限数据库)
2. [第二部分：API设计](#第二部分api设计)
   - [三、认证与用户API](#三认证与用户api)
   - [四、部门管理API](#四部门管理api)
   - [五、文档权限API](#五文档权限api)
   - [六、权限审计API](#六权限审计api)
   - [七、文档处理API](#七文档处理api)
   - [八、RAG与问答API](#八rag与问答api)
3. [第三部分：核心服务设计](#第三部分核心服务设计)
   - [九、认证服务](#九认证服务)
   - [十、权限服务](#十权限服务)
   - [十一、部门服务](#十一部门服务)
   - [十二、文档处理服务](#十二文档处理服务)
   - [十四、RAG服务](#十四rag服务)
4. [第四部分：安全策略](#第四部分安全策略)
   - [十五、权限验证策略](#十五权限验证策略)
   - [十六、审计日志设计](#十六审计日志设计)
   - [十七、前后端双重校验](#十七前后端双重校验)
5. [附录](#附录)
   - [A. 数据库完整DDL](#a数据库完整ddl)
   - [B. 统一响应格式](#b统一响应格式)
   - [C. 错误码定义](#c错误码定义)

---

# 第一部分：数据库设计

## 一、数据库设计

### 1.1 PostgreSQL表结构

#### 1.1.1 用户表(users)

```sql
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(100),
    role VARCHAR(20) DEFAULT 'DEPT_USER',  -- 四级角色: SYSTEM_ADMIN/DEPT_ADMIN/DEPT_UPLOADER/DEPT_USER
    status VARCHAR(20) DEFAULT 'active',
    login_fail_count INT DEFAULT 0,
    lock_until TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_status ON users(status);
CREATE INDEX idx_users_role ON users(role);
```

> **说明**：用户与部门的关系通过 `user_departments` 多对多关联表管理（见2.3节），支持用户属于多个部门。

#### 1.1.2 角色表(roles)

```sql
CREATE TABLE roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    level INT NOT NULL,  -- 角色级别 1-4
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 预定义角色
INSERT INTO roles (code, name, description, level) VALUES
('SYSTEM_ADMIN', '系统管理员', '全系统最高权限', 1),
('DEPT_ADMIN', '部门管理员', '本部门全部权限', 2),
('DEPT_UPLOADER', '部门文档上传者', '文档上传和配置权限', 3),
('DEPT_USER', '部门普通人员', '基础访问权限', 4);
```

#### 1.1.3 用户角色关系表(user_roles)

```sql
CREATE TABLE user_roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    department_id UUID,  -- 如果角色与特定部门关联
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_role UNIQUE (user_id, role_id, department_id)
);

CREATE INDEX idx_user_roles_user ON user_roles(user_id);
CREATE INDEX idx_user_roles_role ON user_roles(role_id);
```

#### 1.1.4 文档表(documents)

```sql
CREATE TABLE documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL REFERENCES users(id),
    department_id VARCHAR(500),  -- 文档所属部门（多部门支持：逗号分隔存储多部门ID）
    title VARCHAR(255) NOT NULL,
    file_path VARCHAR(500),
    file_type VARCHAR(20),
    collection_id UUID,  -- 【已废弃】单归属字段，仅作历史兼容。文档与知识库的关联完全通过 collection_documents 表管理
    status VARCHAR(20) DEFAULT 'pending',
    doc_type VARCHAR(20) DEFAULT 'NORMAL',  -- NORMAL/MANUAL
    chunking_strategy VARCHAR(20) DEFAULT 'SEMANTIC',  -- FIXED/CHARACTER/SENTENCE/PARAGRAPH/SEMANTIC/NONE
    is_deleted BOOLEAN DEFAULT false,
    deleted_at TIMESTAMP,
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_documents_status ON documents(status);
CREATE INDEX idx_documents_owner ON documents(owner_id);
CREATE INDEX idx_documents_department ON documents(department_id);
CREATE INDEX idx_documents_deleted ON documents(is_deleted);
```

#### 1.1.4.1 文档知识库关联表(collection_documents)

```sql
CREATE TABLE collection_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    collection_id UUID NOT NULL REFERENCES collections(id) ON DELETE CASCADE,
    document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_collection_document UNIQUE (collection_id, document_id)
);

CREATE INDEX idx_collection_documents_collection ON collection_documents(collection_id);
CREATE INDEX idx_collection_documents_document ON collection_documents(document_id);
```

**文档与知识库为多对多关系**，通过 `collection_documents` 表关联。

#### 1.1.5 文档块表(document_chunks)

```sql
CREATE TABLE document_chunks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    chunk_index INT NOT NULL,
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_chunks_document ON document_chunks(document_id);
```

#### 1.1.6 对话表(conversations)

```sql
CREATE TABLE conversations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    title VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_conversations_user ON conversations(user_id);
```

#### 1.1.7 消息表(messages)

```sql
CREATE TABLE messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL,  -- user/assistant/system
    content TEXT NOT NULL,
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_messages_conversation ON messages(conversation_id);
```

---

## 二、增强RBAC权限数据库

### 2.1 部门表(departments)

```sql
CREATE TABLE departments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(100) NOT NULL,
    parent_id UUID REFERENCES departments(id),
    manager_id UUID REFERENCES users(id),
    description TEXT,
    status VARCHAR(20) DEFAULT 'active',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_dept_code UNIQUE (code)
);

CREATE INDEX idx_departments_parent ON departments(parent_id);
CREATE INDEX idx_departments_manager ON departments(manager_id);
CREATE INDEX idx_departments_status ON departments(status);
```

### 2.2 用户部门关系表(user_departments)

```sql
CREATE TABLE user_departments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    department_id UUID NOT NULL REFERENCES departments(id) ON DELETE CASCADE,
    is_primary BOOLEAN DEFAULT true,
    position VARCHAR(100),
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_dept UNIQUE (user_id, department_id)
);

CREATE INDEX idx_user_departments_user ON user_departments(user_id);
CREATE INDEX idx_user_departments_dept ON user_departments(department_id);
CREATE INDEX idx_user_dept_composite ON user_departments(user_id, department_id);
```

### 2.3 文档权限表(document_permissions)

```sql
CREATE TABLE document_permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    permission_type VARCHAR(30) NOT NULL,  -- COMPANY_WIDE/DEPARTMENT_WIDE/RESTRICTED
    granted_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_doc_permission UNIQUE (document_id)
);

CREATE INDEX idx_doc_permissions_doc ON document_permissions(document_id);
CREATE INDEX idx_doc_perm_type ON document_permissions(document_id, permission_type);
```

**permission_type 可选值：**

| 值 | 说明 | 访问范围 |
|---|---|---|
| COMPANY_WIDE | 全公司可查看 | 公司内所有认证用户 |
| DEPARTMENT_WIDE | 部门内公开 | 文档所属部门成员 |
| RESTRICTED | 指定人员开放 | 上传者指定的特定用户 |
| DEPARTMENT_WIDE_RESTRICTED | 部门全员+其他部门 | 本部门全员 + 其他部门最多10名指定人员 |

### 2.4 文档指定人员权限表(document_user_permissions)

```sql
CREATE TABLE document_user_permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    permission_level VARCHAR(20) DEFAULT 'READ',  -- READ/COMMENT/EDIT
    granted_by UUID NOT NULL REFERENCES users(id),
    expires_at TIMESTAMP,  -- NULL表示永不过期
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_doc_user_perm UNIQUE (document_id, user_id)
);

CREATE INDEX idx_doc_user_perm_doc ON document_user_permissions(document_id);
CREATE INDEX idx_doc_user_perm_user ON document_user_permissions(user_id);
```

### 2.5 权限变更审计表(permission_audit_logs)

```sql
CREATE TABLE permission_audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    operator_id UUID NOT NULL REFERENCES users(id),
    operation_type VARCHAR(30) NOT NULL,  -- GRANT/REVOKE/UPDATE
    target_type VARCHAR(20) NOT NULL,  -- USER/DEPARTMENT/DOCUMENT/ROLE
    target_id UUID NOT NULL,
    old_value JSONB,
    new_value JSONB,
    ip_address VARCHAR(45),
    user_agent TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_permission_audit_operator ON permission_audit_logs(operator_id);
CREATE INDEX idx_permission_audit_target ON permission_audit_logs(target_type, target_id);
CREATE INDEX idx_audit_target_time ON permission_audit_logs(target_type, target_id, created_at DESC);
CREATE INDEX idx_permission_audit_time ON permission_audit_logs(created_at);
```

---

# 第二部分：API设计

## 三、认证与用户API

### 3.1 统一响应格式

```json
{
  "code": 200,
  "message": "success",
  "data": {},
  "timestamp": "2026-03-27T10:00:00Z"
}
```

### 3.2 认证接口

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | /api/v1/auth/register | 用户注册 | 公开 |
| POST | /api/v1/auth/login | 用户登录 | 公开 |
| POST | /api/v1/auth/refresh | 刷新Token | 已认证 |
| POST | /api/v1/auth/logout | 登出 | 已认证 |
| GET | /api/v1/auth/current | 当前用户信息 | 已认证 |

### 3.3 用户接口

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | /api/v1/users | 用户列表 | SYSTEM_ADMIN |
| GET | /api/v1/users/{id} | 用户详情 | 已认证 |
| POST | /api/v1/users/{id} | 更新用户 | SYSTEM_ADMIN或本人 |
| DELETE | /api/v1/users/{id} | 删除用户 | SYSTEM_ADMIN |
| POST | /api/v1/users/{userId}/role | 分配角色 | SYSTEM_ADMIN |
| PUT | /api/v1/users/{id}/departments | 分配部门 | SYSTEM_ADMIN/DEPT_ADMIN(本部门) |

---

## 四、部门管理API

### 4.1 部门CRUD

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | /api/v1/departments | 创建部门 | SYSTEM_ADMIN |
| GET | /api/v1/departments | 部门列表 | SYSTEM_ADMIN |
| GET | /api/v1/departments/{id} | 部门详情 | 已认证 |
| POST | /api/v1/departments/{id} | 更新部门 | SYSTEM_ADMIN |
| DELETE | /api/v1/departments/{id} | 删除部门 | SYSTEM_ADMIN |

#### 创建部门

**请求：**
```json
{
  "code": "TECH001",
  "name": "技术部",
  "parentId": null,
  "managerId": "user-uuid",
  "description": "技术研发部门"
}
```

**响应：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": "dept-uuid",
    "code": "TECH001",
    "name": "技术部",
    "parentId": null,
    "managerId": "user-uuid",
    "status": "active",
    "createdAt": "2026-03-27T10:00:00Z"
  }
}
```

### 4.2 部门用户管理

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | /api/v1/departments/{id}/users | 部门用户列表 | SYSTEM_ADMIN/DEPT_ADMIN(本部门) |
| POST | /api/v1/departments/{id}/users | 添加用户到部门 | SYSTEM_ADMIN/DEPT_ADMIN(本部门) |
| DELETE | /api/v1/departments/{id}/users/{userId} | 从部门移除用户 | SYSTEM_ADMIN/DEPT_ADMIN(本部门) |

#### 添加用户到部门

**请求：**
```json
{
  "userId": "user-uuid",
  "isPrimary": true,
  "position": "高级工程师"
}
```

---

## 五、文档权限API

### 5.1 文档权限配置

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| PUT | /api/v1/documents/{id}/permissions | 设置文档权限 | 文档所有者/DEPT_ADMIN/SYSTEM_ADMIN |
| GET | /api/v1/documents/{id}/permissions | 获取文档权限 | 文档所有者/DEPT_ADMIN/SYSTEM_ADMIN |
| POST | /api/v1/documents/{id}/permissions/users | 添加指定人员 | 文档所有者/DEPT_ADMIN/SYSTEM_ADMIN |
| DELETE | /api/v1/documents/{id}/permissions/users/{userId} | 移除指定人员 | 文档所有者/DEPT_ADMIN/SYSTEM_ADMIN |

#### 设置文档权限

**请求：**
```json
{
  "permissionType": "DEPARTMENT_WIDE",
  "allowedUserIds": [],
  "expiresAt": null
}
```

**permissionType 可选值：**
- `COMPANY_WIDE` - 全公司可查看
- `DEPARTMENT_WIDE` - 部门内公开
- `RESTRICTED` - 指定人员开放
- `DEPARTMENT_WIDE_RESTRICTED` - 部门全员+其他部门（最多10名）

**RESTRICTED类型请求示例：**
```json
{
  "permissionType": "RESTRICTED",
  "allowedUserIds": [
    "user-uuid-1",
    "user-uuid-2"
  ],
  "expiresAt": "2026-06-30T23:59:59Z"
}
```

**响应：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "documentId": "doc-uuid",
    "permissionType": "DEPARTMENT_WIDE",
    "grantedBy": "user-uuid",
    "grantedAt": "2026-03-27T10:00:00Z"
  }
}
```

### 5.2 权限验证

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | /api/v1/documents/{id}/access-check | 检查访问权限 | 已认证 |
| POST | /api/v1/documents/batch-access-check | 批量检查权限 | 已认证 |

#### 检查访问权限

**响应：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "hasAccess": true,
    "permissionLevel": "READ",
    "reason": "用户属于文档所属部门"
  }
}
```

#### 批量检查权限

**请求：**
```json
{
  "documentIds": [
    "doc-uuid-1",
    "doc-uuid-2",
    "doc-uuid-3"
  ]
}
```

**响应：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "results": [
      {"documentId": "doc-uuid-1", "hasAccess": true},
      {"documentId": "doc-uuid-2", "hasAccess": false},
      {"documentId": "doc-uuid-3", "hasAccess": true}
    ]
  }
}
```

---

## 六、权限审计API

### 6.1 审计日志查询

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | /api/v1/audit-logs/permissions | 权限审计日志 | SYSTEM_ADMIN(全部)/DEPT_ADMIN(本部门) |

**查询参数：**

| 参数 | 类型 | 说明 |
|------|------|------|
| targetType | string | USER/DEPARTMENT/DOCUMENT/ROLE |
| targetId | string | 目标ID |
| operatorId | string | 操作人ID |
| operationType | string | GRANT/REVOKE/UPDATE |
| startTime | datetime | 开始时间 |
| endTime | datetime | 结束时间 |
| page | int | 页码 |
| pageSize | int | 每页大小 |

**响应：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "total": 156,
    "items": [
      {
        "id": "audit-uuid",
        "operator": {
          "id": "user-uuid",
          "username": "admin",
          "displayName": "系统管理员"
        },
        "operationType": "GRANT",
        "targetType": "DOCUMENT",
        "targetId": "doc-uuid",
        "oldValue": null,
        "newValue": {
          "permissionType": "RESTRICTED",
          "allowedUsers": ["user-uuid-1"]
        },
        "ipAddress": "192.168.1.100",
        "createdAt": "2026-03-27T10:00:00Z"
      }
    ],
    "page": 1,
    "pageSize": 20
  }
}
```

---

## 七、文档处理API

### 7.1 文档接口

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | /api/v1/documents/upload | 上传文档 | DEPT_UPLOADER/DEPT_ADMIN/SYSTEM_ADMIN |
| GET | /api/v1/documents | 文档列表 | 已认证(根据权限过滤) |
| GET | /api/v1/documents/{id} | 文档详情 | 文档权限验证 |
| GET | /api/v1/documents/{id}/chunks | 文档块 | 文档权限验证 |
| DELETE | /api/v1/documents/{id} | 删除文档 | 文档所有者/DEPT_ADMIN/SYSTEM_ADMIN |

### 7.2 知识库接口

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | /api/v1/collections | 创建知识库 | SYSTEM_ADMIN/DEPT_ADMIN |
| GET | /api/v1/collections | 知识库列表 | 已认证 |
| GET | /api/v1/collections/{id} | 知识库详情 | 知识库权限验证 |
| PUT | /api/v1/collections/{id} | 更新知识库 | 知识库管理员 |
| DELETE | /api/v1/collections/{id} | 删除知识库 | SYSTEM_ADMIN/DEPT_ADMIN |

---

## 八、RAG与问答API

### 8.1 问答接口

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | /api/v1/chat/query | 问答查询 | 已认证 |
| POST | /api/v1/chat/stream | 流式问答 | 已认证 |
| GET | /api/v1/chat/sessions | 会话列表 | 已认证 |
| GET | /api/v1/chat/sessions/{id} | 会话详情 | 已认证 |
| DELETE | /api/v1/chat/sessions/{id} | 删除会话 | 已认证 |

### 8.2 问答请求格式

**请求：**
```json
{
  "query": "如何配置系统参数？",
  "collectionIds": ["collection-uuid-1"],
  "topK": 10,
  "similarityThreshold": 0.7
}
```

**响应（非流式）：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "answer": "根据检索结果，系统参数配置如下...",
    "sources": [
      {
        "documentId": "doc-uuid",
        "title": "系统配置指南",
        "chunk": "配置参数的方法...",
        "score": 0.85
      }
    ],
    "conversationId": "conv-uuid"
  }
}
```

---

# 第三部分：核心服务设计

## 九、认证服务

### 9.1 AuthService

```java
@Service
@Slf4j
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * 用户注册
     */
    public User register(RegisterRequest request) {
        // 1. 验证用户名和邮箱唯一性
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException("用户名已存在");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("邮箱已被使用");
        }

        // 2. 创建用户
        User user = User.builder()
            .username(request.getUsername())
            .email(request.getEmail())
            .passwordHash(passwordEncoder.encode(request.getPassword()))
            .displayName(request.getDisplayName())
            .status(UserStatus.ACTIVE)
            .build();

        return userRepository.save(user);
    }

    /**
     * 用户登录
     */
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
            .orElseThrow(() -> new BusinessException("用户名或密码错误"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException("用户名或密码错误");
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException("账户已被禁用");
        }

        // 生成Token
        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user);

        return LoginResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .expiresIn(jwtTokenProvider.getAccessTokenExpiration())
            .user(UserDTO.from(user))
            .build();
    }
}
```

---

## 十、权限服务

### 10.1 PermissionService

```java
@Service
@Slf4j
public class PermissionService {

    @Autowired
    private DocumentPermissionRepository permissionRepository;

    @Autowired
    private DocumentUserPermissionRepository userPermRepository;

    @Autowired
    private UserDepartmentRepository userDepartmentRepository;

    @Autowired
    private PermissionAuditService auditService;

    /**
     * 检查用户对文档的访问权限
     */
    public AccessCheckResult checkDocumentAccess(User user, UUID documentId) {
        // 1. 系统管理员拥有所有权限
        if (user.hasRole(Role.SYSTEM_ADMIN)) {
            return AccessCheckResult.allowed("系统管理员权限");
        }

        // 2. 获取文档信息
        Document document = documentRepository.findById(documentId)
            .orElseThrow(() -> new ResourceNotFoundException("文档不存在"));

        // 3. 文档所有者拥有完全权限
        if (document.getOwnerId().equals(user.getId())) {
            return AccessCheckResult.allowed("文档所有者");
        }

        // 4. 部门管理员检查
        if (user.hasRole(Role.DEPT_ADMIN) &&
            user.isAdminOfDepartment(document.getDepartmentId())) {
            return AccessCheckResult.allowed("部门管理员权限");
        }

        // 5. 获取文档权限配置
        DocumentPermission permission = permissionRepository
            .findByDocumentId(documentId)
            .orElse(null);

        if (permission == null) {
            return AccessCheckResult.denied("文档无权限配置");
        }

        // 6. 根据权限类型检查
        return checkByPermissionType(user, document, permission);
    }

    /**
     * 根据权限类型检查
     */
    private AccessCheckResult checkByPermissionType(User user, Document document,
            DocumentPermission permission) {
        switch (permission.getPermissionType()) {
            case COMPANY_WIDE:
                return AccessCheckResult.allowed("全公司可查看");

            case DEPARTMENT_WIDE:
                return checkDepartmentAccess(user, document);

            case RESTRICTED:
                return checkRestrictedAccess(user, document);

            default:
                return AccessCheckResult.denied("未知权限类型");
        }
    }

    /**
     * 检查部门权限
     */
    private AccessCheckResult checkDepartmentAccess(User user, Document document) {
        Set<UUID> userDeptIds = userDepartmentRepository
            .findByUserId(user.getId())
            .stream()
            .map(UserDepartment::getDepartmentId)
            .collect(Collectors.toSet());

        if (userDeptIds.contains(document.getDepartmentId())) {
            return AccessCheckResult.allowed("属于文档所属部门");
        }

        return AccessCheckResult.denied("不在文档所属部门");
    }

    /**
     * 检查指定人员权限
     */
    private AccessCheckResult checkRestrictedAccess(User user, Document document) {
        Optional<DocumentUserPermission> userPerm = userPermRepository
            .findUserPermission(document.getId(), user.getId());

        if (userPerm.isEmpty()) {
            return AccessCheckResult.denied("未在允许访问名单中");
        }

        DocumentUserPermission perm = userPerm.get();

        // 检查是否过期
        if (perm.getExpiresAt() != null &&
            perm.getExpiresAt().isBefore(LocalDateTime.now())) {
            return AccessCheckResult.denied("权限已过期");
        }

        return AccessCheckResult.allowed("在允许访问名单中");
    }
}
```

### 10.2 PermissionCacheService

```java
@Service
public class PermissionCacheService {

    private static final long CACHE_TTL_SECONDS = 300; // 5分钟

    @Cacheable(value = "userPermissions", key = "#userId", ttl = CACHE_TTL_SECONDS)
    public Set<Permission> getUserPermissions(UUID userId) {
        return calculateUserPermissions(userId);
    }

    @CacheEvict(value = "userPermissions", key = "#userId")
    public void evictUserPermissionCache(UUID userId) {
        log.info("清除用户权限缓存: {}", userId);
        applicationEventPublisher.publishEvent(
            new PermissionChangedEvent(this, userId)
        );
    }

    @CacheEvict(value = "userPermissions", allEntries = true)
    public void evictAllUserPermissionCache() {
        log.warn("清除所有用户权限缓存");
    }
}
```

---

## 十一、部门服务

### 11.1 DepartmentService

```java
@Service
@Slf4j
public class DepartmentService {

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private UserDepartmentRepository userDepartmentRepository;

    @Autowired
    private PermissionAuditService auditService;

    /**
     * 创建部门
     */
    @Transactional
    public Department createDepartment(CreateDepartmentRequest request, User operator) {
        // 验证权限
        if (!operator.hasRole(Role.SYSTEM_ADMIN)) {
            throw new AccessDeniedException("只有系统管理员可以创建部门");
        }

        // 验证部门编码唯一性
        if (departmentRepository.existsByCode(request.getCode())) {
            throw new BusinessException("部门编码已存在");
        }

        Department department = Department.builder()
            .code(request.getCode())
            .name(request.getName())
            .parentId(request.getParentId())
            .managerId(request.getManagerId())
            .description(request.getDescription())
            .status(DepartmentStatus.ACTIVE)
            .build();

        Department saved = departmentRepository.save(department);

        // 记录审计日志
        auditService.logOperation(
            operator,
            OperationType.CREATE,
            TargetType.DEPARTMENT,
            saved.getId(),
            null,
            JSONObject.toJSONString(saved)
        );

        return saved;
    }

    /**
     * 添加用户到部门
     */
    @Transactional
    public void addUserToDepartment(UUID departmentId, UUID userId,
            boolean isPrimary, String position, User operator) {
        // 验证权限
        if (!canManageDepartment(operator, departmentId)) {
            throw new AccessDeniedException("无权管理此部门");
        }

        UserDepartment userDepartment = UserDepartment.builder()
            .userId(userId)
            .departmentId(departmentId)
            .isPrimary(isPrimary)
            .position(position)
            .build();

        userDepartmentRepository.save(userDepartment);

        // 更新用户主部门
        if (isPrimary) {
            userRepository.updateUserDepartment(userId, departmentId);
        }

        // 记录审计日志
        auditService.logOperation(
            operator,
            OperationType.GRANT,
            TargetType.USER,
            userId,
            null,
            JSONObject.toJSONString(Map.of("departmentId", departmentId))
        );
    }
}
```

---

## 十二、文档处理服务

### 12.1 DocumentProcessor

```java
@Service
public class DocumentProcessor {

    @Autowired
    private OcrService ocrService;

    @Autowired
    private TextCleaner textCleaner;

    @Autowired
    private SemanticChunker semanticChunker;

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private VectorStore vectorStore;

    /**
     * 处理文档
     */
    @Transactional
    public ProcessResult processDocument(Document document, InputStream inputStream) {
        // 1. 文档分类
        DocType type = classifyDocument(inputStream);

        // 2. 文本提取/OCR
        String content;
        if (type == DocType.MANUAL) {
            // MANUAL: 结构化短文档，强制不分块，必须OCR
            content = ocrService.extractText(inputStream);
        } else {
            // NORMAL: 非结构化文档
            // 启发式检测：文本量过少时自动触发OCR
            content = extractTextWithPyMuPDF(inputStream);
            if (needOcrFallback(content)) {
                log.info("NORMAL文档文本量过少，触发OCR回退");
                content = ocrService.extractText(inputStream);
            }
        }

        // 3. 文本清洗
        content = textCleaner.clean(content);

        // 4. 分块（根据chunking_strategy）
        // NORMAL支持: FIXED/CHARACTER/SENTENCE/PARAGRAPH/SEMANTIC
        // MANUAL强制: NONE（不分块，整篇存储）
        List<TextChunk> chunks = chunkDocument(content, document.getChunkingStrategy());

        // 5. 向量化
        List<float[]> embeddings = embeddingService.embedBatch(
            chunks.stream()
                .map(TextChunk::getContent)
                .toList()
        );

        // 6. 存储
        for (int i = 0; i < chunks.size(); i++) {
            TextChunk chunk = chunks.get(i);
            chunk.setEmbedding(embeddings.get(i));
            vectorStore.insert(chunk);
        }

        return ProcessResult.builder()
            .documentId(document.getId())
            .chunks(chunks)
            .toc(toc)
            .status(ProcessStatus.COMPLETED)
            .build();
    }
}
```

---

## 十三、RAG服务

### 14.1 RAGService

```java
@Service
@Slf4j
public class RAGService {

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private LLMService llmService;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private MessageRepository messageRepository;

    /**
     * 问答查询
     */
    public QueryResult query(QueryRequest request, User user) {
        // 1. 检索相关文档
        List<SearchResult> searchResults = vectorStore.search(
            request.getQuery(),
            request.getCollectionIds(),
            request.getTopK(),
            request.getSimilarityThreshold()
        );

        // 2. 构建上下文
        String context = buildContext(searchResults);

        // 3. 调用LLM生成答案
        String answer = llmService.generate(context, request.getQuery());

        // 4. 保存对话记录
        saveConversation(user.getId(), request.getQuery(), answer, searchResults);

        return QueryResult.builder()
            .answer(answer)
            .sources(searchResults)
            .build();
    }

    /**
     * 构建上下文
     */
    private String buildContext(List<SearchResult> results) {
        StringBuilder context = new StringBuilder();
        context.append("根据以下检索到的文档片段回答问题：\n\n");

        for (int i = 0; i < results.size(); i++) {
            SearchResult result = results.get(i);
            context.append(String.format("[%d] %s\n", i + 1, result.getContent()));
            context.append(String.format("来源：%s (相似度: %.2f)\n\n",
                result.getDocumentTitle(), result.getScore()));
        }

        return context.toString();
    }
}
```

---

# 第四部分：安全策略

## 十五、权限验证策略

### 15.1 最小权限原则

```java
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> authz
                // 公开接口
                .requestMatchers("/api/v1/auth/**").permitAll()
                // 默认需要认证
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter(),
                UsernamePasswordAuthenticationFilter.class);

        return http;
    }
}
```

### 15.2 方法级权限控制

```java
@Service
public class DocumentPermissionService {

    @PreAuthorize("hasRole('SYSTEM_ADMIN') or " +
                  "@documentPermissionService.isDocumentOwner(#documentId, authentication.name)")
    public void updateDocument(UUID documentId, UpdateDocumentRequest request) {
        // 业务逻辑
    }

    @PreAuthorize("hasRole('SYSTEM_ADMIN') or " +
                  "@documentPermissionService.canAccessDocument(#documentId, authentication.name)")
    public Document getDocument(UUID documentId) {
        return documentRepository.findById(documentId)
            .orElseThrow(() -> new ResourceNotFoundException("文档不存在"));
    }

    public boolean canAccessDocument(UUID documentId, String username) {
        // 权限检查逻辑
        return permissionService.checkDocumentAccess(...)
            .isAllowed();
    }
}
```

---

## 十六、审计日志设计

### 16.1 权限变更记录

```java
@Service
public class PermissionAuditService {

    @Autowired
    private PermissionAuditLogRepository auditLogRepository;

    /**
     * 记录权限变更
     */
    public void logOperation(User operator, OperationType operationType,
            TargetType targetType, UUID targetId,
            Object oldValue, Object newValue) {
        HttpRequest request = ((ServletRequestAttributes)
            RequestContextHolder.getRequestAttributes()).getRequest();

        PermissionAuditLog log = PermissionAuditLog.builder()
            .operatorId(operator.getId())
            .operationType(operationType)
            .targetType(targetType)
            .targetId(targetId)
            .oldValue(oldValue != null ? JSONObject.toJSONString(oldValue) : null)
            .newValue(newValue != null ? JSONObject.toJSONString(newValue) : null)
            .ipAddress(getClientIp(request))
            .userAgent(request.getHeader("User-Agent"))
            .build();

        auditLogRepository.save(log);
    }
}
```

### 16.2 审计日志保留策略

| 操作类型 | 保留期限 | 说明 |
|---------|---------|------|
| 权限变更 | 永久 | 敏感操作，长期保留 |
| 用户管理 | 永久 | 安全审计需要 |
| 文档操作 | 1年 | 常规审计 |
| 系统配置 | 6个月 | 配置变更追踪 |

---

## 十七、前后端双重校验

### 17.1 后端校验

```java
// 控制器层
@PreAuthorize("hasPermission(#documentId, 'DOCUMENT', 'READ')")
@GetMapping("/documents/{documentId}")
public DocumentDTO getDocument(@PathVariable UUID documentId) {
    return documentService.getDocument(documentId);
}

// 服务层
public Document getDocument(UUID documentId) {
    // 二次权限验证
    if (!permissionService.checkDocumentAccess(
            SecurityContextHolder.getCurrentUser(),
            documentId).isAllowed()) {
        throw new AccessDeniedException("无权访问此文档");
    }
    return documentRepository.findById(documentId)
        .orElseThrow(() -> new ResourceNotFoundException("文档不存在"));
}
```

### 17.2 前端校验

```javascript
// 权限指令
const PermissionDirective = {
    mounted(el, binding) {
        const { permission, fallback } = binding.value;
        if (!hasPermission(permission)) {
            el.style.display = 'none';
        }
    }
};

// 使用示例
<button v-permission="'document:delete'" @click="handleDelete">
    删除文档
</button>

// 路由守卫
router.beforeEach((to, from, next) => {
    const requiredPermissions = to.meta.permissions || [];
    if (requiredPermissions.length > 0) {
        if (!hasAllPermissions(requiredPermissions)) {
            next({ name: 'Forbidden' });
            return;
        }
    }
    next();
});
```

---

# 附录

## A. 数据库完整DDL

```sql
-- 完整数据库脚本请参考第三部分：数据库设计

-- 主要表结构
-- users, roles, user_roles, departments, user_departments
-- documents, document_permissions, document_user_permissions
-- document_chunks, conversations, messages
-- permission_audit_logs
```

## B. 统一响应格式

```json
{
  "code": 200,
  "message": "success",
  "data": {},
  "timestamp": "2026-03-27T10:00:00Z"
}
```

## C. 错误码定义

| 错误码 | 说明 |
|--------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 401 | 未认证 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |

---

**文档版本**: V0.3
**编制日期**: 2026-03-27
**最后更新**: 增强RBAC权限系统、OCR服务集成
