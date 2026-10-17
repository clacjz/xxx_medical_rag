<template>
  <div class="rbac-container">
    <el-tabs v-model="activeTab" class="rbac-tabs">
      <el-tab-pane label="角色管理" name="roles">
        <el-card class="tab-card">
          <template #header>
            <div class="card-header">
              <span>系统角色</span>
              <div class="header-actions">
                <el-button type="success" size="small" @click="handleInitializeRoles" :loading="initializing">
                  <el-icon><Refresh /></el-icon> 初始化默认角色
                </el-button>
                <el-button type="primary" size="small" @click="openRoleDialog()">
                  <el-icon><Plus /></el-icon> 添加角色
                </el-button>
              </div>
            </div>
          </template>

          <div class="toolbar">
            <el-input
              v-model="roleKeyword"
              placeholder="搜索角色名称/代码"
              style="width: 200px"
              clearable
              @clear="loadRoles"
              @keyup.enter="loadRoles"
            >
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>
            <el-select v-model="filterLevel" placeholder="选择级别" clearable style="width: 150px">
              <el-option label="系统管理员 (L1)" :value="1" />
              <el-option label="部门管理员 (L2)" :value="2" />
              <el-option label="部门上传者 (L3)" :value="3" />
              <el-option label="部门普通人员 (L4)" :value="4" />
            </el-select>
            <el-button @click="loadRoles">
              <el-icon><Refresh /></el-icon> 刷新
            </el-button>
          </div>

          <el-table :data="filteredRoles" v-loading="loadingRoles" style="width: 100%; margin-top: 16px">
            <el-table-column prop="code" label="角色代码" width="150">
              <template #default="{ row }">
                <el-tag type="info" size="small">{{ row.code }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="name" label="角色名称" width="150" />
            <el-table-column prop="level" label="级别" width="120" align="center">
              <template #default="{ row }">
                <el-tag :type="getLevelType(row.level)" size="small">
                  L{{ row.level }} - {{ getLevelName(row.level) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
            <el-table-column prop="userCount" label="用户数" width="100" align="center">
              <template #default="{ row }">
                <el-badge :value="row.userCount || 0" type="primary" :max="99" />
              </template>
            </el-table-column>
            <el-table-column prop="active" label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="row.active ? 'success' : 'info'" size="small">
                  {{ row.active ? '启用' : '禁用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" text size="small" @click="openRoleDialog(row)">
                  编辑
                </el-button>
                <el-button type="primary" text size="small" @click="openRolePermissionDialog(row)">
                  权限
                </el-button>
                <el-button type="danger" text size="small" @click="deleteRole(row)" :disabled="(row.userCount || 0) > 0">
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="用户角色分配" name="user-roles">
        <el-card class="tab-card">
          <template #header>
            <div class="card-header">
              <span>用户角色管理</span>
            </div>
          </template>

          <div class="toolbar">
            <el-input
              v-model="userRoleKeyword"
              placeholder="搜索用户名/邮箱"
              style="width: 200px"
              clearable
              @clear="loadUsers"
              @keyup.enter="loadUsers"
            >
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>
            <el-select v-model="filterRole" placeholder="选择角色" clearable style="width: 150px">
              <el-option label="系统管理员" value="SYSTEM_ADMIN" />
              <el-option label="部门管理员" value="DEPT_ADMIN" />
              <el-option label="部门上传者" value="DEPT_UPLOADER" />
              <el-option label="部门普通人员" value="DEPT_USER" />
            </el-select>
            <el-select v-model="filterDepartment" placeholder="选择部门" clearable style="width: 150px">
              <el-option v-for="dept in flatDepartments" :key="dept.id" :label="dept.name" :value="dept.id" />
            </el-select>
            <el-button @click="loadUsers">
              <el-icon><Refresh /></el-icon> 刷新
            </el-button>
          </div>

          <el-table :data="users" v-loading="loadingUsers" style="width: 100%; margin-top: 16px">
            <el-table-column prop="username" label="用户名" width="150" />
            <el-table-column prop="email" label="邮箱" min-width="180" />
            <el-table-column label="角色" width="280">
              <template #default="{ row }">
                <div class="role-tags">
                  <el-tag
                    v-for="role in row.roles || []"
                    :key="role.id"
                    :type="getRoleType(role.code)"
                    size="small"
                    class="role-tag"
                  >
                    {{ role.name }}
                    <span v-if="role.isPrimary" class="primary-badge">主</span>
                  </el-tag>
                  <span v-if="!row.roles || row.roles.length === 0" class="text-muted">未分配</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="departments" label="所属部门" width="180">
              <template #default="{ row }">
                <span v-if="(row as any).departments && (row as any).departments.length > 0">
                  {{ (row as any).departments.map((d: any) => d.name).join(', ') }}
                </span>
                <span v-else class="text-muted">未分配</span>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="getStatusType(row.status)" size="small">
                  {{ getStatusName(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" text size="small" @click="openUserRoleDialog(row)">
                  分配角色
                </el-button>
                <el-button type="primary" text size="small" @click="openAssignDepartmentDialog(row)">
                  分配部门
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            v-model:current-page="userPagination.page"
            v-model:page-size="userPagination.pageSize"
            :total="userPagination.total"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next, jumper"
            style="margin-top: 16px; justify-content: flex-end"
            @size-change="loadUsers"
            @current-change="loadUsers"
          />
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="部门管理" name="departments">
        <el-card class="tab-card">
          <template #header>
            <div class="card-header">
              <span>部门结构</span>
              <el-button type="primary" size="small" @click="resetDepartmentForm">
                <el-icon><Plus /></el-icon> 添加部门
              </el-button>
            </div>
          </template>

          <div class="tree-container">
            <el-tree
              :data="departmentTree"
              :props="departmentTreeProps"
              node-key="id"
              default-expand-all
              :expand-on-click-node="false"
              v-loading="loadingDepartments"
            >
              <template #default="{ node: _node, data }">
                <span class="tree-node">
                  <span class="node-label">{{ data.name }}</span>
                  <span class="node-code">({{ data.code }})</span>
                  <span class="node-count" v-if="data.userCount">- {{ data.userCount }}人</span>
                  <span class="node-actions">
                    <el-button type="primary" text size="small" @click.stop="editDepartment(data)">
                      编辑
                    </el-button>
                    <el-button type="danger" text size="small" @click.stop="deleteDepartment(data)">
                      删除
                    </el-button>
                  </span>
                </span>
              </template>
            </el-tree>
          </div>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="权限配置" name="permissions">
        <el-card class="tab-card">
          <template #header>
            <div class="card-header">
              <span>文档权限管理</span>
            </div>
          </template>

          <div class="toolbar">
            <el-select v-model="permissionCollection" placeholder="选择知识库" clearable style="width: 200px">
              <el-option v-for="col in collections" :key="col.id" :label="col.name" :value="col.id" />
            </el-select>
            <el-input
              v-model="permissionKeyword"
              placeholder="搜索文档名称"
              style="width: 200px"
              clearable
              @clear="loadDocuments"
              @keyup.enter="loadDocuments"
            >
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>
            <el-button @click="loadDocuments">
              <el-icon><Refresh /></el-icon> 刷新
            </el-button>
          </div>

          <el-table :data="documents" v-loading="loadingDocuments" style="width: 100%; margin-top: 16px">
            <el-table-column prop="name" label="文档名称" min-width="200" show-overflow-tooltip />
            <el-table-column prop="collectionName" label="所属知识库" width="150" show-overflow-tooltip />
            <el-table-column prop="ownerName" label="所有者" width="120" />
            <el-table-column prop="permissionType" label="当前权限" width="150" align="center">
              <template #default="{ row }">
                <el-tag :type="getPermissionTypeTag(row.permissionType)" size="small">
                  {{ getPermissionTypeName(row.permissionType) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="updatedAt" label="更新时间" width="160">
              <template #default="{ row }">
                {{ formatDate(row.updatedAt) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" text size="small" @click="openPermissionDialog(row)">
                  配置权限
                </el-button>
                <el-button type="primary" text size="small" @click="openPermissionDetailDialog(row)">
                  详情
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            v-model:current-page="documentPagination.page"
            v-model:page-size="documentPagination.pageSize"
            :total="documentPagination.total"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next, jumper"
            style="margin-top: 16px; justify-content: flex-end"
            @size-change="loadDocuments"
            @current-change="loadDocuments"
          />
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="审计日志" name="audit-logs">
        <el-card class="tab-card">
          <template #header>
            <div class="card-header">
              <span>权限变更审计</span>
              <el-button type="primary" size="small" @click="handleExportAuditLogs">
                <el-icon><Download /></el-icon> 导出
              </el-button>
            </div>
          </template>

          <div class="toolbar">
            <el-date-picker
              v-model="auditDateRange"
              type="daterange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              value-format="x"
              style="width: 300px"
            />
            <el-select v-model="auditOperationType" placeholder="操作类型" clearable style="width: 150px">
              <el-option label="授予权限" value="GRANT" />
              <el-option label="撤销权限" value="REVOKE" />
              <el-option label="更新权限" value="UPDATE" />
            </el-select>
            <el-select v-model="auditTargetType" placeholder="目标类型" clearable style="width: 150px">
              <el-option label="用户" value="USER" />
              <el-option label="部门" value="DEPARTMENT" />
              <el-option label="文档" value="DOCUMENT" />
              <el-option label="角色" value="ROLE" />
            </el-select>
            <el-input
              v-model="auditKeyword"
              placeholder="搜索操作人/目标"
              style="width: 200px"
              clearable
              @clear="loadAuditLogs"
              @keyup.enter="loadAuditLogs"
            >
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>
            <el-button @click="loadAuditLogs">
              <el-icon><Refresh /></el-icon> 刷新
            </el-button>
          </div>

          <el-table :data="auditLogs" v-loading="loadingAuditLogs" style="width: 100%; margin-top: 16px">
            <el-table-column prop="operatorName" label="操作人" width="120" />
            <el-table-column prop="operationType" label="操作类型" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="getOperationTypeTag(row.operationType)" size="small">
                  {{ getOperationTypeName(row.operationType) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="targetType" label="目标类型" width="100" align="center">
              <template #default="{ row }">
                <el-tag type="info" size="small">{{ getTargetTypeName(row.targetType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="targetName" label="目标对象" min-width="180" show-overflow-tooltip />
            <el-table-column prop="ipAddress" label="IP地址" width="140" />
            <el-table-column prop="createdAt" label="操作时间" width="160">
              <template #default="{ row }">
                {{ formatDate(row.createdAt) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" text size="small" @click="openAuditDetailDialog(row)">
                  详情
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            v-model:current-page="auditPagination.page"
            v-model:page-size="auditPagination.pageSize"
            :total="auditPagination.total"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next, jumper"
            style="margin-top: 16px; justify-content: flex-end"
            @size-change="loadAuditLogs"
            @current-change="loadAuditLogs"
          />
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="showRoleDialog" :title="isEditRole ? '编辑角色' : '添加角色'" width="600px">
      <el-form ref="roleFormRef" :model="roleForm" :rules="roleRules" label-width="100px">
        <el-form-item label="角色代码" prop="code">
          <el-input v-model="roleForm.code" placeholder="请输入角色代码" :disabled="isEditRole" />
        </el-form-item>
        <el-form-item label="角色名称" prop="name">
          <el-input v-model="roleForm.name" placeholder="请输入角色名称" />
        </el-form-item>
        <el-form-item label="角色级别" prop="level">
          <el-select v-model="roleForm.level" placeholder="请选择角色级别" style="width: 100%" :disabled="isEditRole">
            <el-option label="L1 - 系统管理员" :value="1" />
            <el-option label="L2 - 部门管理员" :value="2" />
            <el-option label="L3 - 部门上传者" :value="3" />
            <el-option label="L4 - 部门普通人员" :value="4" />
          </el-select>
        </el-form-item>
        <el-form-item label="角色描述" prop="description">
          <el-input v-model="roleForm.description" type="textarea" :rows="3" placeholder="请输入角色描述" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showRoleDialog = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSaveRole">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showRolePermissionDialog" title="角色权限配置" width="700px">
      <el-form label-width="120px">
        <el-form-item label="角色名称">
          <span>{{ currentRole?.name }}</span>
        </el-form-item>
        <el-form-item label="角色代码">
          <el-tag type="info" size="small">{{ currentRole?.code }}</el-tag>
        </el-form-item>
        <el-form-item label="权限配置">
          <el-input
            v-model="rolePermissionJson"
            type="textarea"
            :rows="8"
            placeholder='请输入权限JSON数组，如: ["doc:read", "doc:write"]'
          />
          <div class="permission-tip">
            当前权限：{{ currentRole?.permissions || '无' }}
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showRolePermissionDialog = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSaveRolePermission">保存权限</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showUserRoleDialog" title="分配角色" width="600px">
      <el-form label-width="100px">
        <el-form-item label="用户名">
          <span class="username-text">{{ userRoleForm.username }}</span>
        </el-form-item>
        <el-form-item label="当前角色">
          <div class="role-tags" v-if="userRoleForm.currentRoles && userRoleForm.currentRoles.length > 0">
            <el-tag
              v-for="role in userRoleForm.currentRoles"
              :key="role.id"
              :type="getRoleType(role.code)"
              size="small"
              class="role-tag"
              closable
              @close="handleRemoveRole(role)"
            >
              {{ role.name }}
              <span v-if="(role as any).isPrimary" class="primary-badge">主</span>
            </el-tag>
          </div>
          <span v-else class="text-muted">未分配</span>
        </el-form-item>
        <el-form-item label="添加角色" required>
          <div class="role-assign-form">
            <el-select v-model="userRoleForm.newRoleId" placeholder="请选择角色" style="width: 200px">
              <el-option
                v-for="role in availableRoles"
                :key="role.id"
                :label="`${role.name} (L${role.level})`"
                :value="role.id"
              />
            </el-select>
            <el-checkbox v-model="userRoleForm.isPrimary" style="margin-left: 12px">
              设为主角色
            </el-checkbox>
            <el-button type="primary" size="small" @click="handleAddRole" :disabled="!userRoleForm.newRoleId">
              添加
            </el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showUserRoleDialog = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showDepartmentDialog" :title="isEditDepartment ? '编辑部门' : '添加部门'" width="500px">
      <el-form ref="departmentFormRef" :model="departmentForm" :rules="departmentRules" label-width="100px">
        <el-form-item label="部门编码" prop="code">
          <el-input v-model="departmentForm.code" placeholder="请输入部门编码" :disabled="isEditDepartment" />
        </el-form-item>
        <el-form-item label="部门名称" prop="name">
          <el-input v-model="departmentForm.name" placeholder="请输入部门名称" />
        </el-form-item>
        <el-form-item label="上级部门" prop="parentId">
          <el-select v-model="departmentForm.parentId" placeholder="请选择上级部门" clearable style="width: 100%">
            <el-option v-for="dept in flatDepartments" :key="dept.id" :label="dept.name" :value="dept.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="部门管理员" prop="managerId">
          <el-select v-model="departmentForm.managerId" placeholder="请选择部门管理员" clearable filterable style="width: 100%">
            <el-option v-for="user in allUsers" :key="user.id" :label="user.username" :value="user.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="部门描述" prop="description">
          <el-input v-model="departmentForm.description" type="textarea" :rows="3" placeholder="请输入部门描述" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDepartmentDialog = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSaveDepartment">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showDepartmentAssignmentDialog" title="分配部门" width="600px">
      <el-form :model="departmentAssignmentForm" label-width="100px">
        <el-form-item label="用户名">
          <span>{{ departmentAssignmentForm.username }}</span>
        </el-form-item>
        <el-form-item label="当前部门">
          <span v-if="departmentAssignmentForm.currentDepartments && departmentAssignmentForm.currentDepartments.length > 0">
            {{ departmentAssignmentForm.currentDepartments.map(d => d.name).join(', ') }}
          </span>
          <span v-else class="text-muted">未分配</span>
        </el-form-item>
        <el-form-item label="分配部门" required>
          <el-select v-model="departmentAssignmentForm.selectedDepartments" multiple placeholder="请选择部门" style="width: 100%">
            <el-option v-for="dept in flatDepartments" :key="dept.id" :label="dept.name" :value="dept.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="职位">
          <el-input v-model="departmentAssignmentForm.position" placeholder="请输入职位" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDepartmentAssignmentDialog = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleAssignDepartment">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showPermissionDialog" title="配置文档权限" width="600px">
      <el-form :model="permissionForm" label-width="120px">
        <el-form-item label="文档名称">
          <span>{{ permissionForm.documentName }}</span>
        </el-form-item>
        <el-form-item label="当前权限">
          <el-tag :type="getPermissionTypeTag(permissionForm.currentPermissionType)" size="small">
            {{ getPermissionTypeName(permissionForm.currentPermissionType) }}
          </el-tag>
        </el-form-item>
        <el-form-item label="权限类型" required>
          <el-select v-model="permissionForm.permissionType" placeholder="请选择权限类型" style="width: 100%">
            <el-option label="全公司可查看" value="COMPANY_WIDE" />
            <el-option label="部门内公开" value="DEPARTMENT_WIDE" />
            <el-option label="指定人员开放" value="RESTRICTED" />
          </el-select>
        </el-form-item>
        <el-form-item label="指定用户" v-if="permissionForm.permissionType === 'RESTRICTED'">
          <el-select
            v-model="permissionForm.allowedUserIds"
            multiple
            filterable
            placeholder="请选择可访问的用户"
            style="width: 100%"
          >
            <el-option v-for="user in allUsers" :key="user.id" :label="user.username" :value="user.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="权限过期时间">
          <el-date-picker
            v-model="permissionForm.expiresAt"
            type="datetime"
            placeholder="选择过期时间（可选）"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showPermissionDialog = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleUpdatePermission">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showPermissionDetailDialog" title="文档权限详情" width="700px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="文档名称">{{ permissionDetail.documentName }}</el-descriptions-item>
        <el-descriptions-item label="所有者">{{ permissionDetail.ownerName }}</el-descriptions-item>
        <el-descriptions-item label="权限类型">
          <el-tag :type="getPermissionTypeTag(permissionDetail.permissionType)" size="small">
            {{ getPermissionTypeName(permissionDetail.permissionType) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ formatDate(permissionDetail.updatedAt) }}</el-descriptions-item>
      </el-descriptions>

      <div v-if="permissionDetail.allowedUsers && permissionDetail.allowedUsers.length > 0" style="margin-top: 20px;">
        <h4>已授权用户</h4>
        <el-table :data="permissionDetail.allowedUsers" size="small">
          <el-table-column prop="username" label="用户名" width="120" />
          <el-table-column prop="email" label="邮箱" width="180" />
          <el-table-column prop="departmentName" label="部门" width="120" />
          <el-table-column prop="permissionLevel" label="权限级别" width="100" align="center">
            <template #default="{ row }">
              <el-tag size="small">{{ getPermissionLevelName(row.permissionLevel) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="expiresAt" label="过期时间" width="160">
            <template #default="{ row }">
              {{ row.expiresAt ? formatDate(row.expiresAt) : '永不过期' }}
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div v-if="permissionDetail.effectivePermissions" style="margin-top: 20px;">
        <h4>我的有效权限</h4>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="读取权限">
            <el-tag :type="permissionDetail.effectivePermissions.hasReadAccess ? 'success' : 'danger'" size="small">
              {{ permissionDetail.effectivePermissions.hasReadAccess ? '有权限' : '无权限' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="编辑权限">
            <el-tag :type="permissionDetail.effectivePermissions.hasEditAccess ? 'success' : 'danger'" size="small">
              {{ permissionDetail.effectivePermissions.hasEditAccess ? '有权限' : '无权限' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="删除权限">
            <el-tag :type="permissionDetail.effectivePermissions.hasDeleteAccess ? 'success' : 'danger'" size="small">
              {{ permissionDetail.effectivePermissions.hasDeleteAccess ? '有权限' : '无权限' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="管理权限">
            <el-tag :type="permissionDetail.effectivePermissions.hasManageAccess ? 'success' : 'danger'" size="small">
              {{ permissionDetail.effectivePermissions.hasManageAccess ? '有权限' : '无权限' }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>
      </div>
      <template #footer>
        <el-button @click="showPermissionDetailDialog = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showAuditDetailDialog" title="审计详情" width="700px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="操作人">{{ auditDetail.operatorName }}</el-descriptions-item>
        <el-descriptions-item label="操作时间">{{ formatDate(auditDetail.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="操作类型">
          <el-tag :type="getOperationTypeTag(auditDetail.operationType)" size="small">
            {{ getOperationTypeName(auditDetail.operationType) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="目标类型">
          <el-tag type="info" size="small">{{ getTargetTypeName(auditDetail.targetType) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="目标对象" :span="2">{{ auditDetail.targetName }}</el-descriptions-item>
        <el-descriptions-item label="IP地址">{{ auditDetail.ipAddress }}</el-descriptions-item>
        <el-descriptions-item label="User Agent" :span="2" :width="200">{{ auditDetail.userAgent }}</el-descriptions-item>
      </el-descriptions>

      <div v-if="auditDetail.oldValue || auditDetail.newValue" style="margin-top: 20px;">
        <h4>变更详情</h4>
        <div v-if="auditDetail.oldValue" style="margin-bottom: 10px;">
          <strong>原值：</strong>
          <pre style="background: var(--muted); padding: 10px; border-radius: var(--radius); overflow-x: auto;">{{ JSON.stringify(auditDetail.oldValue, null, 2) }}</pre>
        </div>
        <div v-if="auditDetail.newValue">
          <strong>新值：</strong>
          <pre style="background: var(--muted); padding: 10px; border-radius: var(--radius); overflow-x: auto;">{{ JSON.stringify(auditDetail.newValue, null, 2) }}</pre>
        </div>
      </div>
      <template #footer>
        <el-button @click="showAuditDetailDialog = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { rolesApi } from '@/api/roles'
import { departmentsApi, usersApi, documentsApi, collectionsApi, permissionsApi } from '@/api'
import { formatDate, getRoleType, getLevelName, getLevelType, getStatusType, getStatusName, getPermissionTypeName, getPermissionLevelName, getPermissionTypeTag, getOperationTypeName, getTargetTypeName } from '@/utils/format'
import { Plus, Search, Refresh, Download } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import type { Role, Department, User, MultiCollection, PermissionAuditLog, PermissionType } from '@/types'

const activeTab = ref('roles')

const loadingRoles = ref(false)
const loadingDepartments = ref(false)
const loadingUsers = ref(false)
const loadingDocuments = ref(false)
const loadingAuditLogs = ref(false)
const submitting = ref(false)
const initializing = ref(false)

const roles = ref<Role[]>([])
const departmentTree = ref<Department[]>([])
const flatDepartments = ref<Department[]>([])
const users = ref<any[]>([])
const allUsers = ref<User[]>([])
const documents = ref<any[]>([])
const collections = ref<MultiCollection[]>([])
const auditLogs = ref<PermissionAuditLog[]>([])

const showRoleDialog = ref(false)
const isEditRole = ref(false)
const showRolePermissionDialog = ref(false)
const currentRole = ref<Role | null>(null)
const rolePermissionJson = ref('')
const showUserRoleDialog = ref(false)
const showDepartmentDialog = ref(false)
const isEditDepartment = ref(false)
const showDepartmentAssignmentDialog = ref(false)
const showPermissionDialog = ref(false)
const showPermissionDetailDialog = ref(false)
const showAuditDetailDialog = ref(false)

const roleFormRef = ref<FormInstance>()
const departmentFormRef = ref<FormInstance>()

const roleForm = reactive({
  id: '',
  code: '',
  name: '',
  level: 4,
  description: ''
})

const roleRules: FormRules = {
  code: [{ required: true, message: '请输入角色代码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  level: [{ required: true, message: '请选择角色级别', trigger: 'change' }]
}

const departmentForm = reactive({
  id: '',
  code: '',
  name: '',
  parentId: null as string | null,
  managerId: '',
  description: ''
})

const departmentRules: FormRules = {
  code: [{ required: true, message: '请输入部门编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入部门名称', trigger: 'blur' }]
}

const departmentTreeProps = {
  children: 'children',
  label: 'name'
}

const roleKeyword = ref('')
const filterLevel = ref<number | null>(null)
const userRoleKeyword = ref('')
const filterRole = ref('')
const filterDepartment = ref('')

const userPagination = reactive({
  page: 0,
  pageSize: 10,
  total: 0
})

const userRoleForm = reactive({
  userId: '',
  username: '',
  currentRoles: [] as Role[],
  newRoleId: '',
  isPrimary: false
})

const departmentAssignmentForm = reactive({
  userId: '',
  username: '',
  currentDepartments: [] as any[],
  selectedDepartments: [] as string[],
  position: ''
})

const permissionCollection = ref('')
const permissionKeyword = ref('')

const documentPagination = reactive({
  page: 0,
  pageSize: 10,
  total: 0
})

const permissionForm = reactive({
  documentId: '',
  documentName: '',
  currentPermissionType: '' as PermissionType | '',
  permissionType: '' as PermissionType | '',
  allowedUserIds: [] as string[],
  expiresAt: null as Date | null
})

const permissionDetail = reactive({
  documentName: '',
  ownerName: '',
  permissionType: '' as PermissionType | '',
  updatedAt: '',
  allowedUsers: [] as any[],
  effectivePermissions: null as any
})

const auditDateRange = ref<number[]>([])
const auditOperationType = ref('')
const auditTargetType = ref('')
const auditKeyword = ref('')

const getAuditDefaultDateRange = (): number[] => {
  const end = new Date()
  const start = new Date()
  start.setDate(end.getDate() - 7)
  return [start.getTime(), end.getTime()]
}

const auditPagination = reactive({
  page: 0,
  pageSize: 10,
  total: 0
})

const auditDetail = reactive({
  operatorName: '',
  operationType: '',
  targetType: '',
  targetName: '',
  ipAddress: '',
  userAgent: '',
  createdAt: '',
  oldValue: null as any,
  newValue: null as any
})

const filteredRoles = computed(() => {
  let result = roles.value

  if (roleKeyword.value) {
    const keyword = roleKeyword.value.toLowerCase()
    result = result.filter(role =>
      role.name.toLowerCase().includes(keyword) ||
      role.code.toLowerCase().includes(keyword)
    )
  }

  if (filterLevel.value !== null) {
    result = result.filter(role => role.level === filterLevel.value)
  }

  return result
})

const availableRoles = computed(() => {
  const currentRoleIds = userRoleForm.currentRoles.map(r => r.id)
  return roles.value.filter(role => !currentRoleIds.includes(role.id))
})

const getOperationTypeTag = (type: string): 'success' | 'warning' | 'danger' | 'info' | undefined => {
  const typeMap: Record<string, 'success' | 'warning' | 'danger' | 'info'> = {
    GRANT: 'success',
    REVOKE: 'danger',
    UPDATE: 'warning'
  }
  return typeMap[type] || undefined
}

const flattenDepartments = (departments: Department[]): Department[] => {
  const result: Department[] = []
  const flatten = (depts: Department[]) => {
    for (const dept of depts) {
      result.push(dept)
      if (dept.children && dept.children.length > 0) {
        flatten(dept.children)
      }
    }
  }
  flatten(departments)
  return result
}

const loadRoles = async () => {
  loadingRoles.value = true
  try {
    const res = await rolesApi.list()
    if (res.code === 200 && res.data) {
      roles.value = res.data
    }
  } catch (error) {
    console.error('Failed to load roles:', error)
    ElMessage.error('加载角色列表失败')
  } finally {
    loadingRoles.value = false
  }
}

const handleInitializeRoles = async () => {
  try {
    await ElMessageBox.confirm(
      '是否初始化系统默认角色？这将创建4个默认角色（L1-L4）。',
      '初始化确认',
      { type: 'info' }
    )
    initializing.value = true
    const res = await rolesApi.initializeDefaultRoles()
    if (res.code === 200) {
      ElMessage.success('默认角色初始化成功')
      loadRoles()
    }
  } catch {
    // 用户取消
  } finally {
    initializing.value = false
  }
}

const openRoleDialog = (role?: Role) => {
  if (role) {
    isEditRole.value = true
    roleForm.id = role.id
    roleForm.code = role.code
    roleForm.name = role.name
    roleForm.level = role.level
    roleForm.description = role.description || ''
  } else {
    isEditRole.value = false
    roleForm.id = ''
    roleForm.code = ''
    roleForm.name = ''
    roleForm.level = 4
    roleForm.description = ''
  }
  showRoleDialog.value = true
}

const handleSaveRole = async () => {
  if (!roleFormRef.value) return

  await roleFormRef.value.validate(async (valid) => {
    if (!valid) return

    submitting.value = true
    try {
      let res
      if (isEditRole.value) {
        res = await rolesApi.update(roleForm.id, {
          name: roleForm.name,
          description: roleForm.description || undefined
        })
      } else {
        res = await rolesApi.create({
          code: roleForm.code,
          name: roleForm.name,
          level: roleForm.level,
          description: roleForm.description || undefined
        })
      }

      if (res.code === 200) {
        ElMessage.success(isEditRole.value ? '更新成功' : '创建成功')
        showRoleDialog.value = false
        loadRoles()
      }
    } finally {
      submitting.value = false
    }
  })
}

const openRolePermissionDialog = (role: Role) => {
  currentRole.value = role
  rolePermissionJson.value = role.permissions || '[]'
  showRolePermissionDialog.value = true
}

const handleSaveRolePermission = async () => {
  if (!currentRole.value) return

  let permissions: string[] = []
  try {
    permissions = JSON.parse(rolePermissionJson.value)
    if (!Array.isArray(permissions)) {
      throw new Error('权限必须是数组')
    }
  } catch {
    ElMessage.error('权限JSON格式不正确，请输入有效的JSON数组')
    return
  }

  submitting.value = true
  try {
    const res = await rolesApi.update(currentRole.value.id, { permissions })
    if (res.code === 200) {
      ElMessage.success('权限保存成功')
      showRolePermissionDialog.value = false
      loadRoles()
    }
  } finally {
    submitting.value = false
  }
}

const deleteRole = async (role: Role) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除角色"${role.name}"吗？`,
      '警告',
      { type: 'warning' }
    )
    const res = await rolesApi.delete(role.id)
    if (res.code === 200) {
      ElMessage.success('删除成功')
      loadRoles()
    }
  } catch {
    // 用户取消
  }
}

const openUserRoleDialog = async (user: any) => {
  userRoleForm.userId = user.id
  userRoleForm.username = user.username
  userRoleForm.newRoleId = ''
  userRoleForm.isPrimary = false

  try {
    const res = await rolesApi.getUserRoles(user.id)
    if (res.code === 200 && res.data) {
      userRoleForm.currentRoles = res.data.map((role: any) => ({
        ...role,
        isPrimary: role.isPrimary || role.level === 1
      }))
    } else {
      userRoleForm.currentRoles = []
    }
  } catch (error) {
    console.error('Failed to load user roles:', error)
    userRoleForm.currentRoles = []
  }

  showUserRoleDialog.value = true
}

const handleAddRole = async () => {
  if (!userRoleForm.newRoleId) {
    ElMessage.warning('请选择要添加的角色')
    return
  }

  submitting.value = true
  try {
    const res = await rolesApi.assignRoleToUserSimple(
      userRoleForm.userId,
      userRoleForm.newRoleId,
      userRoleForm.isPrimary
    )

    if (res.code === 200) {
      ElMessage.success('角色添加成功')

      const role = roles.value.find(r => r.id === userRoleForm.newRoleId)
      if (role) {
        if (userRoleForm.isPrimary) {
          userRoleForm.currentRoles = userRoleForm.currentRoles.map(r => ({ ...r, isPrimary: false }))
        }
        userRoleForm.currentRoles.push({
          ...role,
          isPrimary: userRoleForm.isPrimary
        } as any)
      }

      userRoleForm.newRoleId = ''
      userRoleForm.isPrimary = false
      loadRoles()
    }
  } finally {
    submitting.value = false
  }
}

const handleRemoveRole = async (role: Role) => {
  try {
    await ElMessageBox.confirm(
      `确定要从用户移除角色"${role.name}"吗？`,
      '确认',
      { type: 'warning' }
    )

    submitting.value = true
    try {
      const res = await rolesApi.removeRoleFromUser(userRoleForm.userId, role.id)
      if (res.code === 200) {
        ElMessage.success('角色移除成功')
        userRoleForm.currentRoles = userRoleForm.currentRoles.filter(r => r.id !== role.id)
        loadRoles()
      }
    } finally {
      submitting.value = false
    }
  } catch {
    // 用户取消
  }
}

const loadDepartments = async () => {
  loadingDepartments.value = true
  try {
    const res = await departmentsApi.getTree()
    if (res.code === 200 && res.data) {
      departmentTree.value = res.data
      flatDepartments.value = flattenDepartments(res.data)
    }
  } catch (error) {
    console.error('Failed to load departments:', error)
  } finally {
    loadingDepartments.value = false
  }
}

const loadUsers = async () => {
  loadingUsers.value = true
  try {
    const res = await usersApi.list({
      page: userPagination.page,
      pageSize: userPagination.pageSize,
      keyword: userRoleKeyword.value || undefined,
      role: filterRole.value || undefined
    })
    if (res.code === 200 && res.data) {
      users.value = res.data.items
      userPagination.total = res.data.total

      for (const user of users.value) {
        try {
          const roleRes = await rolesApi.getUserRoles(user.id)
          if (roleRes.code === 200 && roleRes.data) {
            user.roles = roleRes.data.map((role: any) => ({
              ...role,
              isPrimary: role.isPrimary || role.level === 1
            }))
          } else {
            user.roles = []
          }
        } catch {
          user.roles = []
        }
      }
    }
  } catch (error) {
    console.error('Failed to load users:', error)
  } finally {
    loadingUsers.value = false
  }
}

const loadAllUsers = async () => {
  try {
    const res = await usersApi.list({ page: 0, pageSize: 1000 })
    if (res.code === 200 && res.data) {
      allUsers.value = res.data.items
    }
  } catch (error) {
    console.error('Failed to load all users:', error)
  }
}

const loadCollections = async () => {
  try {
    const res = await collectionsApi.list({ page: 0, pageSize: 100 })
    if (res.code === 200 && res.data) {
      collections.value = res.data.items
    }
  } catch (error) {
    console.error('Failed to load collections:', error)
  }
}

const loadDocuments = async () => {
  loadingDocuments.value = true
  try {
    const res = await documentsApi.list({
      page: documentPagination.page,
      pageSize: documentPagination.pageSize,
      collectionId: permissionCollection.value || undefined
    })
    if (res.code === 200 && res.data) {
      documents.value = res.data.items
      documentPagination.total = res.data.total

      for (const doc of documents.value) {
        try {
          const permRes = await permissionsApi.getDocumentPermission(doc.id)
          if (permRes.code === 200 && permRes.data) {
            doc.permissionType = permRes.data.permissionType
          }
        } catch {
          doc.permissionType = 'RESTRICTED'
        }
      }
    }
  } catch (error) {
    console.error('Failed to load documents:', error)
  } finally {
    loadingDocuments.value = false
  }
}

const loadAuditLogs = async () => {
  if (!auditDateRange.value || auditDateRange.value.length !== 2) {
    ElMessage.warning('请选择查询时间范围（默认最近7天）')
    return
  }

  loadingAuditLogs.value = true
  try {
    const res = await permissionsApi.getAuditLogs({
      page: auditPagination.page,
      pageSize: auditPagination.pageSize,
      operationType: auditOperationType.value || undefined,
      targetType: auditTargetType.value || undefined,
      startTime: auditDateRange.value[0],
      endTime: auditDateRange.value[1]
    })
    if (res.code === 200 && res.data) {
      auditLogs.value = res.data.items
      auditPagination.total = res.data.total
    } else {
      ElMessage.error(res.message || '查询失败')
    }
  } catch (error) {
    console.error('Failed to load audit logs:', error)
    ElMessage.error('查询权限变更日志失败')
  } finally {
    loadingAuditLogs.value = false
  }
}

const resetDepartmentForm = () => {
  showDepartmentDialog.value = true
  isEditDepartment.value = false
  departmentForm.id = ''
  departmentForm.code = ''
  departmentForm.name = ''
  departmentForm.parentId = null
  departmentForm.managerId = ''
  departmentForm.description = ''
}

const editDepartment = (dept: Department) => {
  departmentForm.id = dept.id
  departmentForm.code = dept.code
  departmentForm.name = dept.name
  departmentForm.parentId = dept.parentId
  departmentForm.managerId = dept.managerId || ''
  departmentForm.description = dept.description || ''
  isEditDepartment.value = true
  showDepartmentDialog.value = true
}

const deleteDepartment = async (dept: Department) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除部门"${dept.name}"吗？删除后将无法恢复。`,
      '警告',
      { type: 'warning' }
    )
    const res = await departmentsApi.delete(dept.id)
    if (res.code === 200) {
      ElMessage.success('删除成功')
      loadDepartments()
    }
  } catch {
    // 用户取消
  }
}

const handleSaveDepartment = async () => {
  if (!departmentFormRef.value) return

  await departmentFormRef.value.validate(async (valid) => {
    if (!valid) return

    submitting.value = true
    try {
      let res
      if (isEditDepartment.value) {
        res = await departmentsApi.update(departmentForm.id, {
          name: departmentForm.name,
          parentId: departmentForm.parentId,
          managerId: departmentForm.managerId || undefined,
          description: departmentForm.description || undefined
        })
      } else {
        res = await departmentsApi.create({
          code: departmentForm.code,
          name: departmentForm.name,
          parentId: departmentForm.parentId,
          managerId: departmentForm.managerId || undefined,
          description: departmentForm.description || undefined
        })
      }

      if (res.code === 200) {
        ElMessage.success(isEditDepartment.value ? '更新成功' : '创建成功')
        showDepartmentDialog.value = false
        loadDepartments()
      }
    } finally {
      submitting.value = false
    }
  })
}

const openAssignDepartmentDialog = async (user: User) => {
  departmentAssignmentForm.userId = user.id
  departmentAssignmentForm.username = user.username
  departmentAssignmentForm.currentDepartments = []
  departmentAssignmentForm.selectedDepartments = []
  departmentAssignmentForm.position = ''
  showDepartmentAssignmentDialog.value = true
}

const handleAssignDepartment = async () => {
  if (departmentAssignmentForm.selectedDepartments.length === 0) {
    ElMessage.warning('请选择至少一个部门')
    return
  }

  submitting.value = true
  try {
    const currentDeptIds = (departmentAssignmentForm.currentDepartments || []).map((d: any) => d.id)
    const newDeptIds = departmentAssignmentForm.selectedDepartments

    const toAdd = newDeptIds.filter((id: string) => !currentDeptIds.includes(id))
    const toRemove = currentDeptIds.filter((id: string) => !newDeptIds.includes(id))

    for (const deptId of toAdd) {
      await departmentsApi.addUser(deptId, {
        userId: departmentAssignmentForm.userId,
        isPrimary: departmentAssignmentForm.selectedDepartments.indexOf(deptId) === 0,
        position: departmentAssignmentForm.position || undefined
      })
    }

    for (const deptId of toRemove) {
      await departmentsApi.removeUser(deptId, departmentAssignmentForm.userId)
    }

    ElMessage.success('部门分配成功')
    showDepartmentAssignmentDialog.value = false
    loadUsers()
  } catch (error) {
    console.error('Failed to assign departments:', error)
  } finally {
    submitting.value = false
  }
}

const openPermissionDialog = async (doc: any) => {
  permissionForm.documentId = doc.id
  permissionForm.documentName = doc.name
  permissionForm.currentPermissionType = doc.permissionType || ''
  permissionForm.permissionType = doc.permissionType || 'RESTRICTED'
  permissionForm.allowedUserIds = []
  permissionForm.expiresAt = null

  try {
    const res = await permissionsApi.getDocumentPermission(doc.id)
    if (res.code === 200 && res.data) {
      permissionForm.currentPermissionType = res.data.permissionType
      permissionForm.permissionType = res.data.permissionType
      if (res.data.allowedUsers) {
        permissionForm.allowedUserIds = res.data.allowedUsers.map((u: any) => u.userId)
      }
    }
  } catch (error) {
    console.error('Failed to load document permission:', error)
  }

  showPermissionDialog.value = true
}

const handleUpdatePermission = async () => {
  if (!permissionForm.permissionType) {
    ElMessage.warning('请选择权限类型')
    return
  }

  submitting.value = true
  try {
    const res = await permissionsApi.updateDocumentPermission(permissionForm.documentId, {
      permissionType: permissionForm.permissionType,
      allowedUserIds: permissionForm.permissionType === 'RESTRICTED' ? permissionForm.allowedUserIds : undefined,
      expiresAt: permissionForm.expiresAt ? permissionForm.expiresAt.toISOString() : null
    })

    if (res.code === 200) {
      ElMessage.success('权限配置成功')
      showPermissionDialog.value = false
      loadDocuments()
    }
  } finally {
    submitting.value = false
  }
}

const openPermissionDetailDialog = async (doc: any) => {
  try {
    const res = await permissionsApi.getDocumentPermission(doc.id)
    if (res.code === 200 && res.data) {
      permissionDetail.documentName = doc.name
      permissionDetail.ownerName = (res.data as any).ownerName
      permissionDetail.permissionType = (res.data as any).permissionType
      permissionDetail.updatedAt = (res.data as any).updatedAt
      permissionDetail.allowedUsers = (res.data as any).allowedUsers || []
      permissionDetail.effectivePermissions = (res.data as any).effectivePermissions || null
    }
  } catch (error) {
    console.error('Failed to load document permission detail:', error)
  }

  showPermissionDetailDialog.value = true
}

const openAuditDetailDialog = (log: PermissionAuditLog) => {
  auditDetail.operatorName = log.operatorName
  auditDetail.operationType = log.operationType
  auditDetail.targetType = log.targetType
  auditDetail.targetName = log.targetName
  auditDetail.ipAddress = log.ipAddress
  auditDetail.userAgent = log.userAgent || ''
  auditDetail.createdAt = log.createdAt
  auditDetail.oldValue = log.oldValue || null
  auditDetail.newValue = log.newValue || null
  showAuditDetailDialog.value = true
}

const handleExportAuditLogs = async () => {
  if (!auditDateRange.value || auditDateRange.value.length !== 2) {
    ElMessage.warning('请选择导出日期范围')
    return
  }

  ElMessage.info('导出功能开发中...')
}

onMounted(() => {
  auditDateRange.value = getAuditDefaultDateRange()
  loadRoles()
  loadDepartments()
  loadUsers()
  loadAllUsers()
  loadCollections()
  loadDocuments()
  loadAuditLogs()
})
</script>

<style scoped>
.rbac-container {
  height: 100%;
}

.rbac-tabs {
  height: 100%;
}

.rbac-tabs :deep(.el-tabs__content) {
  height: calc(100vh - 140px);
  overflow-y: auto;
}

.tab-card {
  height: 100%;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.toolbar {
  display: flex;
  gap: 12px;
  margin-top: 16px;
  flex-wrap: wrap;
}

.tree-container {
  padding: 16px 0;
}

.tree-node {
  display: flex;
  align-items: center;
  width: 100%;
  gap: 8px;
}

.node-label {
  font-weight: 500;
}

.node-code {
  color: var(--muted-foreground);
  font-size: 12px;
}

.node-count {
  color: var(--success);
  font-size: 12px;
}

.node-actions {
  margin-left: auto;
  display: flex;
  gap: 4px;
}

.text-muted {
  color: var(--muted-foreground);
  font-size: 13px;
}

h4 {
  margin: 0 0 12px 0;
  color: var(--foreground);
}

.role-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.role-tag {
  margin-right: 4px;
}

.primary-badge {
  font-size: 10px;
  background: rgba(255, 255, 255, 0.3);
  padding: 0 4px;
  border-radius: 2px;
  margin-left: 4px;
}

.username-text {
  font-weight: 500;
}

.role-assign-form {
  display: flex;
  align-items: center;
  gap: 8px;
}

.permission-tip {
  margin-top: 8px;
  font-size: 12px;
  color: var(--muted-foreground);
  word-break: break-all;
}
</style>
