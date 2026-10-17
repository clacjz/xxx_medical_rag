# RAG知识库系统增强型RBAC权限管理系统设计

> **文档类型**: 增强功能设计
> **编制日期**: 2026-03-27
> **关联文档**: HLD文档_V3.0、LLD文档、SRS文档_V1.0
> **版本**: V1.0

---

## 一、设计背景与目标

### 1.1 设计背景

随着企业知识管理的深入推进，原有的简化权限体系已无法满足精细化的权限管控需求。本次设计旨在扩展现有RBAC权限系统，增加部门维度控制，实现文档级别的精细化权限管理，确保：

- 部门间的知识隔离与共享
- 文档权限的灵活配置
- 权限变更的全程审计
- 权限验证的双重保障

### 1.2 设计目标

| 目标类型 | 目标描述 | 量化指标 |
|---------|---------|---------|
| 功能完整性 | 实现4级角色体系+3级文档权限 | 100%覆盖需求 |
| 权限精确性 | 权限边界清晰，无越权风险 | 0权限冲突 |
| 审计完整性 | 所有权限操作可追溯 | 100%记录 |
| 性能影响 | 权限验证延迟可忽略 | <5ms |
| 可维护性 | 动态配置，无需重启 | 支持 |

---

## 二、权限体系架构

### 2.1 四级角色体系

```
┌─────────────────────────────────────────────────────────────┐
│                    增强型RBAC权限体系                         │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Level 1: 系统管理员 (SYSTEM_ADMIN)                         │
│  ├─ 全系统最高权限                                           │
│  ├─ 用户管理（创建/删除/角色分配）                            │
│  ├─ 部门管理（创建/删除/部门管理员分配）                       │
│  ├─ 系统配置管理                                             │
│  └─ 审计日志查看                                             │
│                                                             │
│  Level 2: 部门管理员 (DEPT_ADMIN)                           │
│  ├─ 本部门全部权限                                           │
│  ├─ 本部门用户管理                                           │
│  ├─ 本部门文档权限配置                                        │
│  ├─ 本部门上传的文档管理                                      │
│  └─ 本部门审计日志查看                                       │
│                                                             │
│  Level 3: 部门文档上传者 (DEPT_UPLOADER)                    │
│  ├─ 本部门普通权限                                           │
│  ├─ 文档上传（上传后自动获得文档所有者权限）                    │
│  ├─ 文档权限配置（可配置本部门内/指定人员权限）                 │
│  └─ 文档删除（仅可删除自己上传的文档）                         │
│                                                             │
│  Level 4: 部门普通人员 (DEPT_USER)                           │
│  ├─ 文档查看（根据文档权限配置访问）                          │
│  ├─ 智能问答                                                │
│  ├─ 对话历史查看                                             │
│  └─ 个人设置                                                │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 角色权限矩阵

| 权限项 | 系统管理员 | 部门管理员 | 部门上传者 | 部门普通人员 |
|--------|-----------|-----------|-----------|-------------|
| 用户管理-创建 | ✅ | ❌ | ❌ | ❌ |
| 用户管理-删除 | ✅ | ❌ | ❌ | ❌ |
| 用户管理-角色分配 | ✅ | ⚠️本部门 | ❌ | ❌ |
| 部门管理-创建 | ✅ | ❌ | ❌ | ❌ |
| 部门管理-配置 | ✅ | ⚠️本部门 | ❌ | ❌ |
| 文档上传 | ✅ | ✅ | ✅ | ❌ |
| 文档删除 | ✅ | ⚠️本部门全部 | ⚠️仅自己上传 | ❌ |
| 文档权限配置 | ✅ | ⚠️本部门全部 | ⚠️仅自己上传 | ❌ |
| 文档查看 | ✅全部 | ⚠️本部门全部+有权限 | ⚠️部门/指定+有权限 | ⚠️有权限 |
| 知识库管理 | ✅全部 | ⚠️本部门 | ❌ | ❌ |
| 审计日志查看 | ✅全部 | ⚠️本部门 | ❌ | ❌ |
| 系统配置 | ✅ | ❌ | ❌ | ❌ |

> **说明**：⚠️表示受限权限，仅限特定范围

### 2.3 权限边界规则

**规则1：角色权限不可越级**
- 下级角色无法获得上级角色的任何权限
- 部门管理员无法获得系统管理员权限
- 部门普通人员无法获得部门上传者权限

**规则2：范围权限自动限制**
- 部门管理员的操作范围自动限制在本部门
- 跨部门操作需要上级角色授权

**规则3：文档所有者权限优先**
- 文档上传者作为所有者，拥有文档的完全控制权
- 部门管理员可以代为管理本部门文档

---

## 三、文档权限配置体系

### 3.1 三级访问权限模型

```
┌─────────────────────────────────────────────────────────────┐
│                   文档三级访问权限模型                         │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Level 1: 全公司可查看 (COMPANY_WIDE)                       │
│  ├─ 权限级别: ★★☆☆☆                                      │
│  ├─ 访问范围: 公司内所有认证用户                              │
│  ├─ 适用场景: 公司制度、公告、公开技术文档                      │
│  └─ 配置权限: 系统管理员、部门管理员                           │
│                                                             │
│  Level 2: 部门内公开 (DEPARTMENT_WIDE)                      │
│  ├─ 权限级别: ★★★☆☆                                      │
│  ├─ 访问范围: 文档所属部门的所有成员                           │
│  ├─ 适用场景: 部门内部资料、项目文档、会议纪要                   │
│  └─ 配置权限: 文档上传者、部门管理员                            │
│                                                             │
│  Level 3: 指定人员开放 (RESTRICTED)                          │
│  ├─ 权限级别: ★★★★★                                      │
│  ├─ 访问范围: 上传者指定的特定用户                             │
│  ├─ 适用场景: 敏感报告、机密文件、个人笔记                      │
│  └─ 配置权限: 文档上传者、部门管理员                            │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 3.2 权限继承与覆盖机制

**权限继承规则**

```
文档权限继承链:
知识库权限 → 集合权限 → 文档权限

优先级（高→低）:
1. 文档级别权限（最高优先级）
2. 集合级别权限
3. 知识库级别权限（最低优先级）
```

**权限覆盖规则**

| 场景 | 规则描述 | 示例 |
|------|---------|------|
| 集合开放+文档限制 | 文档权限覆盖集合权限 | 集合全公司公开，但某文档限制为部门内 |
| 知识库开放+文档限制 | 文档权限覆盖知识库权限 | 知识库全公司公开，但某文档限制为指定人员 |
| 继承+显式指定 | 显式指定权限覆盖继承权限 | 通过API明确指定的权限优先 |

**权限计算算法**

```python
def calculate_effective_permission(user, document):
    """
    计算用户对文档的有效权限
    """
    # 1. 检查用户角色
    if user.is_system_admin():
        return Permission.FULL_ACCESS

    # 2. 检查文档所有者
    if document.owner_id == user.id:
        return Permission.FULL_ACCESS

    # 3. 检查显式授权
    explicit_permission = get_explicit_permission(user.id, document.id)
    if explicit_permission:
        return explicit_permission

    # 4. 检查集合权限（继承）
    collection = document.collection
    if collection:
        collection_perm = get_collection_permission(user, collection)
        if collection_perm:
            return collection_perm

    # 5. 检查知识库权限（继承）
    knowledge_base = collection.knowledge_base
    if knowledge_base:
        kb_perm = get_knowledge_base_permission(user, knowledge_base)
        if kb_perm:
            return kb_perm

    # 6. 无权限
    return Permission.NO_ACCESS
```

### 3.3 权限优先级规则

| 优先级 | 权限类型 | 说明 |
|-------|---------|------|
| 1 | 系统管理员权限 | 全系统最高，可绕过所有限制 |
| 2 | 文档所有者权限 | 文档创建者拥有完全控制权 |
| 3 | 显式指定权限 | 通过API/界面明确配置的权限 |
| 4 | 部门管理员权限 | 部门管理员管理本部门文档 |
| 5 | 集合继承权限 | 从集合继承的权限 |
| 6 | 知识库继承权限 | 从知识库继承的权限（最低） |

---

## 四、数据库设计

### 4.1 表结构扩展

#### 4.1.1 部门表 (departments)

```sql
CREATE TABLE departments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) UNIQUE NOT NULL COMMENT '部门编码',
    name VARCHAR(100) NOT NULL COMMENT '部门名称',
    parent_id UUID REFERENCES departments(id) COMMENT '上级部门ID',
    manager_id UUID REFERENCES users(id) COMMENT '部门管理员ID',
    description TEXT COMMENT '部门描述',
    status VARCHAR(20) DEFAULT 'active' COMMENT '状态: active/inactive',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_dept_code UNIQUE (code)
);

CREATE INDEX idx_departments_parent ON departments(parent_id);
CREATE INDEX idx_departments_manager ON departments(manager_id);
CREATE INDEX idx_departments_status ON departments(status);
```

#### 4.1.2 用户部门关系表 (user_departments)

```sql
CREATE TABLE user_departments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    department_id UUID NOT NULL REFERENCES departments(id) ON DELETE CASCADE,
    is_primary BOOLEAN DEFAULT true COMMENT '是否主部门',
    position VARCHAR(100) COMMENT '职位',
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_dept UNIQUE (user_id, department_id)
);

CREATE INDEX idx_user_departments_user ON user_departments(user_id);
CREATE INDEX idx_user_departments_dept ON user_departments(department_id);
```

#### 4.1.3 文档权限表 (document_permissions)

```sql
CREATE TABLE document_permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    permission_type VARCHAR(30) NOT NULL COMMENT 'COMPANY_WIDE/DEPARTMENT_WIDE/RESTRICTED',
    granted_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_doc_permission UNIQUE (document_id)
);

CREATE INDEX idx_doc_permissions_doc ON document_permissions(document_id);
```

#### 4.1.4 文档指定人员权限表 (document_user_permissions)

```sql
CREATE TABLE document_user_permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    permission_level VARCHAR(20) DEFAULT 'READ' COMMENT 'READ/COMMENT/EDIT',
    granted_by UUID NOT NULL REFERENCES users(id),
    expires_at TIMESTAMP COMMENT '权限过期时间，NULL表示永不过期',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_doc_user_perm UNIQUE (document_id, user_id)
);

CREATE INDEX idx_doc_user_perm_doc ON document_user_permissions(document_id);
CREATE INDEX idx_doc_user_perm_user ON document_user_permissions(user_id);
```

#### 4.1.5 权限变更审计表 (permission_audit_logs)

```sql
CREATE TABLE permission_audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    operator_id UUID NOT NULL REFERENCES users(id),
    operation_type VARCHAR(30) NOT NULL COMMENT 'GRANT/REVOKE/UPDATE',
    target_type VARCHAR(20) NOT NULL COMMENT 'USER/ROLE/DEPARTMENT/DOCUMENT',
    target_id UUID NOT NULL,
    old_value JSONB,
    new_value JSONB,
    ip_address VARCHAR(45),
    user_agent TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_permission_audit_operator ON permission_audit_logs(operator_id);
CREATE INDEX idx_permission_audit_target ON permission_audit_logs(target_type, target_id);
CREATE INDEX idx_permission_audit_time ON permission_audit_logs(created_at);
```

### 4.2 实体关系图

```
┌──────────────┐       ┌──────────────────┐       ┌─────────────────┐
│    users     │       │ user_departments │       │  departments    │
├──────────────┤       ├──────────────────┤       ├─────────────────┤
│ id (PK)      │◄──M:N──│ user_id (FK)     │──N:1──►│ id (PK)         │
│ username     │       │ department_id(FK) │       │ code            │
│ email        │       │ is_primary        │       │ name            │
│ role         │       │ position          │       │ parent_id (FK)  │
│ department_id│       └──────────────────┘       │ manager_id (FK) │
└──────────────┘                                     └─────────────────┘
       │
       │ 1:N
       ▼
┌──────────────┐       ┌──────────────────┐       ┌─────────────────┐
│ documents    │       │document_perm_    │       │ document_user_  │
│              │       │sions             │       │ permissions     │
├──────────────┤       ├──────────────────┤       ├─────────────────┤
│ id (PK)      │◄──1:1──│ document_id (FK) │──1:N──►│ document_id(FK)│
│ collection_id│       │ permission_type  │       │ user_id (FK)    │
│ owner_id(FK) │       │ granted_by (FK)   │       │ permission_level│
│ ...          │       └──────────────────┘       │ granted_by (FK) │
└──────────────┘                                   │ expires_at     │
       │                                          └─────────────────┘
       │ 1:N
       ▼
┌──────────────────────────┐
│ permission_audit_logs    │
├──────────────────────────┤
│ id (PK)                  │
│ operator_id (FK)          │
│ operation_type            │
│ target_type               │
│ target_id                │
│ old_value                │
│ new_value                │
│ created_at               │
└──────────────────────────┘
```

---

## 五、API接口规范

### 5.1 部门管理API

#### 5.1.1 创建部门

```yaml
POST /api/v1/departments
权限要求: SYSTEM_ADMIN
```

**请求参数:**
```json
{
  "code": "TECH001",
  "name": "技术部",
  "parentId": null,
  "managerId": "user-uuid",
  "description": "技术研发部门"
}
```

**响应格式:**
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

#### 5.1.2 查询部门列表

```yaml
GET /api/v1/departments
权限要求: SYSTEM_ADMIN, DEPT_ADMIN(仅本部门)
```

**响应格式:**
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": "dept-uuid",
      "code": "TECH001",
      "name": "技术部",
      "parentId": null,
      "userCount": 25,
      "children": [
        {
          "id": "dept-uuid-2",
          "code": "TECH001-1",
          "name": "前端组"
        }
      ]
    }
  ]
}
```

#### 5.1.3 部门用户管理

```yaml
GET /api/v1/departments/{departmentId}/users
权限要求: SYSTEM_ADMIN, DEPT_ADMIN(仅本部门)
```

### 5.2 文档权限API

#### 5.2.1 设置文档权限

```yaml
PUT /api/v1/documents/{documentId}/permissions
权限要求: 文档所有者, DEPT_ADMIN(本部门), SYSTEM_ADMIN
```

**请求参数:**
```json
{
  "permissionType": "DEPARTMENT_WIDE",
  "allowedUserIds": [],
  "expiresAt": null
}
```

**permissionType可选值:**
| 值 | 说明 |
|-----|------|
| COMPANY_WIDE | 全公司可查看 |
| DEPARTMENT_WIDE | 部门内公开 |
| RESTRICTED | 指定人员开放 |

**RESTRICTED类型需要配置allowedUserIds:**
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

**响应格式:**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "documentId": "doc-uuid",
    "permissionType": "DEPARTMENT_WIDE",
    "allowedUsers": [],
    "grantedBy": "user-uuid",
    "grantedAt": "2026-03-27T10:00:00Z"
  }
}
```

#### 5.2.2 查询文档权限

```yaml
GET /api/v1/documents/{documentId}/permissions
权限要求: 文档所有者, DEPT_ADMIN(本部门), SYSTEM_ADMIN
```

**响应格式:**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "documentId": "doc-uuid",
    "ownerId": "owner-uuid",
    "ownerName": "张三",
    "permissionType": "RESTRICTED",
    "allowedUsers": [
      {
        "userId": "user-uuid-1",
        "username": "李四",
        "departmentName": "技术部"
      }
    ],
    "effectivePermissions": {
      "hasReadAccess": true,
      "hasEditAccess": false,
      "hasDeleteAccess": false
    }
  }
}
```

#### 5.2.3 添加指定人员权限

```yaml
POST /api/v1/documents/{documentId}/permissions/users
权限要求: 文档所有者, DEPT_ADMIN(本部门), SYSTEM_ADMIN
```

**请求参数:**
```json
{
  "userId": "target-user-uuid",
  "permissionLevel": "READ",
  "expiresAt": null
}
```

#### 5.2.4 移除指定人员权限

```yaml
DELETE /api/v1/documents/{documentId}/permissions/users/{userId}
权限要求: 文档所有者, DEPT_ADMIN(本部门), SYSTEM_ADMIN
```

### 5.3 权限验证API

#### 5.3.1 检查文档访问权限

```yaml
GET /api/v1/documents/{documentId}/access-check
权限要求: 已认证用户
```

**响应格式:**
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

#### 5.3.2 批量检查文档权限

```yaml
POST /api/v1/documents/batch-access-check
权限要求: 已认证用户
```

**请求参数:**
```json
{
  "documentIds": [
    "doc-uuid-1",
    "doc-uuid-2",
    "doc-uuid-3"
  ]
}
```

**响应格式:**
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

### 5.4 审计日志API

#### 5.4.1 查询权限变更日志

```yaml
GET /api/v1/audit-logs/permissions
权限要求: SYSTEM_ADMIN(全部), DEPT_ADMIN(本部门)
```

**查询参数:**
| 参数 | 类型 | 说明 |
|------|------|------|
| targetType | string | USER/DEPARTMENT/DOCUMENT |
| targetId | string | 目标ID |
| operatorId | string | 操作人ID |
| operationType | string | GRANT/REVOKE/UPDATE |
| startTime | datetime | 开始时间 |
| endTime | datetime | 结束时间 |
| page | int | 页码 |
| pageSize | int | 每页大小 |

**响应格式:**
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
        "targetName": "2026年度技术报告.pdf",
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

## 六、权限验证流程

### 6.1 权限验证流程图

```
┌─────────────────────────────────────────────────────────────┐
│                   文档访问权限验证流程                          │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  用户请求访问文档                                             │
│         │                                                   │
│         ▼                                                   │
│  ┌─────────────────┐                                        │
│  │  1. JWT验证    │                                        │
│  │  Token有效?    │                                        │
│  └────────┬────────┘                                        │
│           │                                                 │
│     ┌─────┴─────┐                                           │
│     │            │                                           │
│    是           否                                          │
│     │            │                                           │
│     ▼            ▼                                           │
│  ┌────────┐  ┌─────────────────┐                             │
│  │ 2.角色  │  │ 返回401未认证   │                             │
│  │ 检查    │  └─────────────────┘                             │
│  └────┬───┘                                                  │
│       │                                                      │
│       ▼                                                      │
│  ┌─────────────────┐                                        │
│  │ 3.系统管理员?  │                                        │
│  │ 直接放行       │                                        │
│  └────────┬────────┘                                        │
│           │                                                 │
│     ┌─────┴─────┐                                           │
│     │            │                                           │
│    是           否                                          │
│     │            │                                           │
│     ▼            ▼                                           │
│  ┌───────┐  ┌─────────────────┐                             │
│  │放行    │  │ 4.所有者检查   │                             │
│  └───────┘  └────────┬────────┘                             │
│                     │                                         │
│               ┌─────┴─────┐                                  │
│               │            │                                  │
│              是           否                                  │
│               │            │                                  │
│               ▼            ▼                                  │
│           ┌───────┐  ┌─────────────────┐                      │
│           │放行    │  │ 5.显式权限检查 │                      │
│           └───────┘  └────────┬────────┘                    │
│                                │                              │
│                                ▼                              │
│                      ┌─────────────────┐                     │
│                      │ 6.部门权限检查  │                      │
│                      └────────┬────────┘                     │
│                               │                               │
│                               ▼                              │
│                      ┌─────────────────┐                     │
│                      │ 7.返回结果     │                      │
│                      └─────────────────┘                     │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 6.2 权限验证算法

```java
@Service
@Slf4j
public class PermissionService {

    @Autowired
    private DocumentPermissionRepository permissionRepository;

    @Autowired
    private UserDepartmentRepository userDepartmentRepository;

    @Autowired
    private AuditLogService auditLogService;

    /**
     * 检查用户对文档的访问权限
     */
    public AccessCheckResult checkDocumentAccess(User user, UUID documentId) {
        // 1. 检查是否为系统管理员
        if (user.hasRole(Role.SYSTEM_ADMIN)) {
            return AccessCheckResult.allowed("系统管理员权限");
        }

        // 2. 获取文档信息
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("文档不存在"));

        // 3. 检查是否为文档所有者
        if (document.getOwnerId().equals(user.getId())) {
            return AccessCheckResult.allowed("文档所有者");
        }

        // 4. 获取文档权限配置
        DocumentPermission permission = permissionRepository
                .findByDocumentId(documentId)
                .orElse(null);

        // 5. 无权限配置，默认拒绝
        if (permission == null) {
            // 检查是否为部门管理员
            if (isDepartmentAdminOfDocument(user, document)) {
                return AccessCheckResult.allowed("部门管理员权限");
            }
            return AccessCheckResult.denied("文档无权限配置");
        }

        // 6. 根据权限类型检查
        switch (permission.getPermissionType()) {
            case COMPANY_WIDE:
                return AccessCheckResult.allowed("全公司可查看");

            case DEPARTMENT_WIDE:
                return checkDepartmentAccess(user, document, permission);

            case RESTRICTED:
                return checkRestrictedAccess(user, document, permission);

            default:
                return AccessCheckResult.denied("未知权限类型");
        }
    }

    /**
     * 检查部门权限
     */
    private AccessCheckResult checkDepartmentAccess(User user, Document document,
            DocumentPermission permission) {
        // 检查用户是否属于文档所属部门
        Set<UUID> userDeptIds = getUserDepartmentIds(user.getId());
        UUID docDeptId = document.getDepartmentId();

        if (userDeptIds.contains(docDeptId)) {
            return AccessCheckResult.allowed("属于文档所属部门");
        }

        // 检查是否为部门管理员
        if (isDepartmentAdminOfDocument(user, document)) {
            return AccessCheckResult.allowed("部门管理员");
        }

        return AccessCheckResult.denied("不在文档所属部门");
    }

    /**
     * 检查指定人员权限
     */
    private AccessCheckResult checkRestrictedAccess(User user, Document document,
            DocumentPermission permission) {
        // 查询指定用户权限
        Optional<DocumentUserPermission> userPerm = permissionRepository
                .findUserPermission(document.getId(), user.getId());

        if (userPerm.isEmpty()) {
            return AccessCheckResult.denied("未在允许访问名单中");
        }

        DocumentUserPermission perm = userPerm.get();

        // 检查是否过期
        if (perm.getExpiresAt() != null && perm.getExpiresAt().isBefore(LocalDateTime.now())) {
            return AccessCheckResult.denied("权限已过期");
        }

        return AccessCheckResult.allowed("在允许访问名单中");
    }

    /**
     * 检查是否为文档所属部门的部门管理员
     */
    private boolean isDepartmentAdminOfDocument(User user, Document document) {
        if (!user.hasRole(Role.DEPT_ADMIN)) {
            return false;
        }

        Set<UUID> userDeptIds = getUserDepartmentIds(user.getId());
        return userDeptIds.contains(document.getDepartmentId());
    }

    /**
     * 获取用户所属的所有部门ID
     */
    private Set<UUID> getUserDepartmentIds(UUID userId) {
        return userDepartmentRepository.findByUserId(userId)
                .stream()
                .map(UserDepartment::getDepartmentId)
                .collect(Collectors.toSet());
    }
}
```

### 6.3 权限变更流程

```
┌─────────────────────────────────────────────────────────────┐
│                   权限变更操作流程                           │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  权限变更请求 ──┬──► 权限校验 ──► 变更执行 ──► 审计记录     │
│                │       │                                   │
│                │       ▼                                   │
│                │  ┌────────────────┐                        │
│                │  │ 检查操作权限   │                        │
│                │  └───────┬────────┘                        │
│                │          │                                 │
│                │    ┌─────┴─────┐                            │
│                │    │           │                           │
│               否   有权限      无权限                        │
│                │    │           │                           │
│                │    ▼           ▼                            │
│                │ 返回400     返回403                        │
│                │                                            
│                ▼                                            │
│         异常处理 + 日志记录                                 │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## 七、安全策略

### 7.1 最小权限原则

**原则定义**：用户仅获得完成工作所需的最低权限，不多也不少。

**实施措施**：

1. **角色权限预定义**
   - 每个角色的权限在系统设计时明确定义
   - 不提供超越角色定义的权限
   - 权限变更需经过审批流程

2. **权限验证多层检查**
   ```java
   // 控制器层
   @PreAuthorize("hasRole('DEPT_ADMIN')")
   public void updateDepartment(...)

   // 服务层
   public void updateDepartment(Department dept, User operator) {
       // 服务层二次验证
       if (!operator.canManageDepartment(dept.getId())) {
           throw new AccessDeniedException("无权管理此部门");
       }
       // 业务逻辑
   }

   // 持久层
   @Query("SELECT ... WHERE dept_id IN (SELECT department_id FROM user_departments WHERE user_id = :userId)")
   List<Department> findManagedDepartments(@Param("userId") UUID userId);
   ```

3. **默认拒绝策略**
   ```java
   @Bean
   public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
       http
           .authorizeHttpRequests(authz -> authz
               // 默认拒绝未明确授权的请求
               .anyRequest().authenticated()
           );
       return http;
   }
   ```

### 7.2 审计日志规范

**记录内容**：

| 操作类型 | 记录字段 | 示例 |
|---------|---------|------|
| GRANT | 操作人、被授权人、权限类型、权限范围 | admin授予user1文档读权限 |
| REVOKE | 操作人、被授权人、原权限 | admin撤销user1文档读权限 |
| UPDATE | 操作人、变更前后对比 | admin修改user1权限为读写 |

**日志保留**：
- 敏感操作日志：永久保留
- 普通操作日志：保留180天
- 日志归档：按月归档压缩存储

**审计查询**：
- 支持按时间范围、操作用户、目标对象等维度查询
- 支持导出CSV格式审计报告
- 审计日志不可删除、不可修改

### 7.3 双重校验机制

**前端校验**：
```javascript
// 前端权限指令
const PermissionDirective = {
    mounted(el, binding) {
        const { permission, fallback } = binding.value;
        if (!hasPermission(permission)) {
            el.style.display = 'none';
        }
    }
};

// 使用示例
<button v-permission="{ permission: 'document:delete' }">
    删除文档
</button>
```

**前端路由守卫**：
```javascript
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

**后端校验**：
```java
// 方法级权限注解
@RequiresPermissions(value = {
    "document:read",
    "document:department:${departmentId}"
}, logical = Logical.AND)
public Document getDocument(@PathVariable UUID documentId) {
    // 服务层实现
}

// 返回结果过滤
@PostFilter("hasPermission(filterObject, 'READ')")
public List<Document> getAccessibleDocuments() {
    return documentRepository.findAll();
}
```

### 7.4 动态权限配置

**配置刷新机制**：
```java
@Service
public class PermissionCacheService {

    private static final long CACHE_TTL_SECONDS = 300; // 5分钟

    @Cacheable(value = "userPermissions", key = "#userId", ttl = CACHE_TTL_SECONDS)
    public Set<Permission> getUserPermissions(UUID userId) {
        return calculateUserPermissions(userId);
    }

    /**
     * 权限变更时清除缓存
     */
    @CacheEvict(value = "userPermissions", key = "#userId")
    public void evictUserPermissionCache(UUID userId) {
        log.info("清除用户权限缓存: {}", userId);
        // 发布权限变更事件
        applicationEventPublisher.publishEvent(
            new PermissionChangedEvent(this, userId)
        );
    }

    /**
     * 批量清除缓存
     */
    @CacheEvict(value = "userPermissions", allEntries = true)
    public void evictAllUserPermissionCache() {
        log.warn("清除所有用户权限缓存");
    }
}
```

**配置监听**：
```java
@Component
public class PermissionConfigListener {

    @EventListener
    public void onConfigChange(ConfigChangeEvent event) {
        if (event.getKey().startsWith("permission.")) {
            permissionCacheService.evictAllUserPermissionCache();
            log.info("权限配置变更，重新加载权限缓存");
        }
    }
}
```

---

## 八、前端集成设计

### 8.1 权限控制组件

```vue
<!-- PermissionGate.vue -->
<template>
  <div v-if="hasPermission" class="permission-gate">
    <slot></slot>
  </div>
  <div v-else-if="showFallback" class="permission-fallback">
    <slot name="fallback"></slot>
  </div>
</template>

<script setup>
import { computed } from 'vue';
import { usePermission } from '@/composables/usePermission';

const props = defineProps({
  permission: {
    type: [String, Array],
    required: true
  },
  mode: {
    type: String,
    default: 'any', // 'any' | 'all'
    validator: (v) => ['any', 'all'].includes(v)
  },
  showFallback: {
    type: Boolean,
    default: false
  }
});

const { checkPermission, checkPermissions } = usePermission();

const hasPermission = computed(() => {
  const perm = Array.isArray(props.permission) ? props.permission : [props.permission];
  return props.mode === 'all'
    ? checkPermissions(perm, 'all')
    : checkPermissions(perm, 'any');
});
</script>
```

### 8.2 使用示例

```vue
<template>
  <div class="document-page">
    <!-- 所有人都能看到文档 -->
    <DocumentViewer :document="document" />

    <!-- 需要上传权限 -->
    <PermissionGate permission="document:upload">
      <UploadButton @upload="handleUpload" />
    </PermissionGate>

    <!-- 需要删除权限（所有者或管理员） -->
    <PermissionGate permission="document:delete">
      <DeleteButton @delete="handleDelete" />
    </PermissionGate>

    <!-- 需要配置权限 -->
    <PermissionGate :permission="['document:permission:update', 'document:owner']" mode="any">
      <PermissionConfigPanel :document="document" />
    </PermissionGate>
  </div>
</template>
```

### 8.3 API权限封装

```typescript
// api/permission.ts
import { apiClient } from './client';

export const permissionApi = {
  // 检查文档访问权限
  checkDocumentAccess: (documentId: string) =>
    apiClient.get(`/documents/${documentId}/access-check`),

  // 批量检查文档访问权限
  batchCheckAccess: (documentIds: string[]) =>
    apiClient.post('/documents/batch-access-check', { documentIds }),

  // 获取文档权限配置
  getDocumentPermissions: (documentId: string) =>
    apiClient.get(`/documents/${documentId}/permissions`),

  // 更新文档权限
  updateDocumentPermissions: (documentId: string, data: PermissionUpdateDto) =>
    apiClient.put(`/documents/${documentId}/permissions`, data),

  // 审计日志
  getAuditLogs: (params: AuditLogQuery) =>
    apiClient.get('/audit-logs/permissions', { params }),
};
```

---

## 九、性能优化

### 9.1 权限缓存策略

| 缓存类型 | TTL | 更新策略 |
|---------|-----|---------|
| 用户角色 | 5分钟 | 权限变更时主动清除 |
| 用户部门 | 10分钟 | 部门变更时主动清除 |
| 文档权限 | 2分钟 | 权限变更时主动清除 |
| 权限计算结果 | 1分钟 | 自动过期 |

### 9.2 批量优化

```java
@Service
public class BatchPermissionService {

    /**
     * 批量检查文档访问权限
     * 优化点：使用IN查询 + 缓存
     */
    public Map<UUID, Boolean> batchCheckAccess(UUID userId, List<UUID> documentIds) {
        // 1. 批量查询文档权限配置
        Map<UUID, DocumentPermission> permissionMap =
            permissionRepository.findByDocumentIds(documentIds)
                .stream()
                .collect(Collectors.toMap(DocumentPermission::getDocumentId, p -> p));

        // 2. 获取用户部门信息（单次查询）
        Set<UUID> userDeptIds = getUserDepartmentIds(userId);

        // 3. 批量计算权限
        Map<UUID, Boolean> results = new HashMap<>();
        for (UUID docId : documentIds) {
            results.put(docId, calculateAccess(userId, docId, permissionMap.get(docId), userDeptIds));
        }

        return results;
    }
}
```

### 9.3 索引优化

```sql
-- 用户部门复合索引
CREATE INDEX idx_user_dept_composite ON user_departments(user_id, department_id);

-- 文档权限查询优化索引
CREATE INDEX idx_doc_perm_type ON document_permissions(document_id, permission_type);

-- 审计日志查询优化索引
CREATE INDEX idx_audit_target_time ON permission_audit_logs(target_type, target_id, created_at DESC);
```

---

## 十、验收标准

### 10.1 功能验收

| 验收项 | 验收标准 | 测试方法 |
|--------|---------|---------|
| 角色权限正确 | 4级角色权限边界清晰，无越权 | 权限矩阵测试 |
| 文档权限配置 | 3级访问权限配置正常生效 | 功能测试 |
| 权限继承正确 | 继承链优先级正确 | 边界测试 |
| 权限变更生效 | 权限变更后立即生效 | 实时测试 |
| 审计日志完整 | 所有权限操作有记录 | 日志审查 |

### 10.2 安全验收

| 验收项 | 验收标准 | 测试方法 |
|--------|---------|---------|
| 越权防护 | 所有越权请求被拒绝 | 渗透测试 |
| 前端校验 | 前端正确隐藏无权限内容 | UI测试 |
| 后端校验 | 后端正确拦截无权限请求 | API测试 |
| 审计可追溯 | 所有权限操作可追溯 | 日志审查 |
| 缓存一致性 | 权限变更后缓存正确更新 | 缓存测试 |

### 10.3 性能验收

| 验收项 | 验收标准 | 测试方法 |
|--------|---------|---------|
| 单次权限验证 | < 10ms | 性能测试 |
| 批量权限验证(100条) | < 100ms | 性能测试 |
| 权限配置保存 | < 500ms | 响应时间测试 |
| 审计日志查询 | < 2s (1000条) | 查询性能测试 |

---

**文档编制完成**

本设计文档定义了增强型RBAC权限管理系统的完整技术方案，包括权限体系架构、文档权限配置、数据库设计、API规范、安全策略等内容。后续将基于本设计进行详细开发和测试。
