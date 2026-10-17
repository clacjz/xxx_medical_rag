<template>
  <div class="users">
    <!-- Page Header -->
    <div class="page-header">
      <div class="header-content">
        <h1 class="page-title">用户管理</h1>
        <p class="page-subtitle">管理系统用户和权限</p>
      </div>
      <el-button type="primary" class="create-btn" @click="showCreateDialog = true">
        <el-icon><Plus /></el-icon> 添加用户
      </el-button>
    </div>

    <!-- Filter Bar -->
    <el-card class="filter-card" shadow="hover">
      <div class="toolbar">
        <el-input
          v-model="keyword"
          placeholder="搜索用户名/邮箱..."
          style="width: 260px"
          clearable
          @clear="loadUsers"
          @keyup.enter="loadUsers"
          class="search-input"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>

        <el-select v-model="filterRole" placeholder="选择角色" clearable style="width: 180px" class="filter-select">
          <el-option label="系统管理员" value="SYSTEM_ADMIN" />
          <el-option label="部门管理员" value="DEPT_ADMIN" />
          <el-option label="部门上传者" value="DEPT_UPLOADER" />
          <el-option label="部门普通人员" value="DEPT_USER" />
        </el-select>

        <el-button @click="loadUsers" class="refresh-btn">
          <el-icon><Refresh /></el-icon> 刷新
        </el-button>
      </div>
    </el-card>

    <!-- Data Table -->
    <el-card class="table-card" shadow="hover">
      <el-table
        :data="users"
        v-loading="loading"
        style="width: 100%"
        class="modern-table"
        :header-cell-style="{ background: 'var(--muted)', color: 'var(--foreground)', fontWeight: '600' }"
      >
        <el-table-column prop="username" label="用户名" width="180">
          <template #default="{ row }">
            <div class="user-cell">
              <el-avatar :size="36" class="user-avatar-icon">
                {{ row.username?.charAt(0)?.toUpperCase() }}
              </el-avatar>
              <span class="username">{{ row.username }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="email" label="邮箱" min-width="220" />
        <el-table-column prop="role" label="角色" width="150" align="center">
          <template #default="{ row }">
            <el-tag :type="getRoleType(row.role)" size="small" class="role-tag">
              {{ getRoleName(row.role) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small" class="status-tag">
              <span class="status-dot"></span>
              {{ getStatusName(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180">
          <template #default="{ row }">
            <span class="date-text">{{ formatDate(row.createdAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <div class="action-buttons">
              <el-button type="primary" text size="small" class="action-btn" @click="handleEdit(row)">
                编辑
              </el-button>
              <el-button type="danger" text size="small" class="action-btn delete-btn" @click="handleDelete(row)">
                删除
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.pageSize"
          :total="pagination.total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadUsers"
          @current-change="loadUsers"
        />
      </div>
    </el-card>

    <!-- Create Dialog -->
    <el-dialog v-model="showCreateDialog" title="添加用户" width="500px" class="user-dialog">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" class="user-form">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" class="form-input" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="请输入邮箱" class="form-input" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" show-password class="form-input" />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-select v-model="form.role" placeholder="请选择角色" style="width: 100%" class="form-input">
            <el-option label="系统管理员" value="SYSTEM_ADMIN" />
            <el-option label="部门管理员" value="DEPT_ADMIN" />
            <el-option label="部门上传者" value="DEPT_UPLOADER" />
            <el-option label="部门普通人员" value="DEPT_USER" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="showCreateDialog = false">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="handleCreate">确定</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- Edit Dialog -->
    <el-dialog v-model="showEditDialog" title="编辑用户" width="500px" class="user-dialog">
      <el-form ref="editFormRef" :model="editForm" :rules="rules" label-width="100px" class="user-form">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="editForm.username" disabled class="form-input" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="editForm.email" class="form-input" />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-select v-model="editForm.role" style="width: 100%" class="form-input">
            <el-option label="系统管理员" value="SYSTEM_ADMIN" />
            <el-option label="部门管理员" value="DEPT_ADMIN" />
            <el-option label="部门上传者" value="DEPT_UPLOADER" />
            <el-option label="部门普通人员" value="DEPT_USER" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="editForm.status">
            <el-radio label="ACTIVE">启用</el-radio>
            <el-radio label="DISABLED">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="showEditDialog = false">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="handleUpdate">确定</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { usersApi } from '@/api'
import { formatDate, getStatusType, getRoleName, getRoleType, getStatusName } from '@/utils/format'
import { Plus, Search, Refresh } from '@element-plus/icons-vue'
import type { User } from '@/types'
import type { FormInstance, FormRules } from 'element-plus'

const users = ref<User[]>([])
const loading = ref(false)
const keyword = ref('')
const filterRole = ref('')

const pagination = reactive({
  page: 0,
  pageSize: 10,
  total: 0
})

const showCreateDialog = ref(false)
const showEditDialog = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const editFormRef = ref<FormInstance>()

const form = reactive({
  username: '',
  email: '',
  password: '',
  role: 'DEPT_USER'
})

const editForm = reactive({
  id: '',
  username: '',
  email: '',
  role: 'DEPT_USER' as 'SYSTEM_ADMIN' | 'DEPT_ADMIN' | 'DEPT_UPLOADER' | 'DEPT_USER',
  status: 'ACTIVE' as 'ACTIVE' | 'DISABLED' | 'LOCKED'
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度至少6位', trigger: 'blur' }
  ],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }]
}

const loadUsers = async () => {
  loading.value = true
  try {
    const res = await usersApi.list({
      page: pagination.page,
      pageSize: pagination.pageSize,
      keyword: keyword.value || undefined,
      role: filterRole.value || undefined
    })
    if (res.code === 200 && res.data) {
      users.value = res.data.items
      pagination.total = res.data.total
    }
  } catch (error) {
    console.error('Failed to load users:', error)
  } finally {
    loading.value = false
  }
}

const handleCreate = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    submitting.value = true
    try {
      const res = await usersApi.create(form)
      if (res.code === 200) {
        ElMessage.success('添加成功')
        showCreateDialog.value = false
        form.username = ''
        form.email = ''
        form.password = ''
        form.role = 'DEPT_USER'
        loadUsers()
      }
    } finally {
      submitting.value = false
    }
  })
}

const handleEdit = (row: User) => {
  editForm.id = row.id
  editForm.username = row.username
  editForm.email = row.email
  editForm.role = row.role
  editForm.status = row.status
  showEditDialog.value = true
}

const handleUpdate = async () => {
  if (!editFormRef.value) return

  await editFormRef.value.validate(async (valid) => {
    if (!valid) return

    submitting.value = true
    try {
      const res = await usersApi.update(editForm.id, editForm)
      if (res.code === 200) {
        ElMessage.success('更新成功')
        showEditDialog.value = false
        loadUsers()
      }
    } finally {
      submitting.value = false
    }
  })
}

const handleDelete = async (row: User) => {
  try {
    await ElMessageBox.confirm(`确定要删除用户"${row.username}"吗？`, '提示', {
      type: 'warning'
    })
    const res = await usersApi.delete(row.id)
    if (res.code === 200) {
      ElMessage.success('删除成功')
      loadUsers()
    }
  } catch {
    // 用户取消
  }
}

onMounted(() => {
  loadUsers()
})
</script>

<style scoped>
.users {
  animation: fadeIn 0.4s ease;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.header-content {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.page-title {
  font-size: 26px;
  font-weight: 600;
  color: var(--foreground);
  margin: 0;
}

.page-subtitle {
  font-size: 14px;
  color: var(--muted-foreground);
  margin: 0;
}

.create-btn {
  border-radius: var(--radius);
  padding: 12px 24px;
  font-weight: 500;
}

.filter-card {
  border-radius: var(--radius-lg);
  border: 1px solid var(--border);
  margin-bottom: 20px;
  box-shadow: var(--shadow-sm);
}

.filter-card :deep(.el-card__body) {
  padding: 16px 20px;
}

.toolbar {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  align-items: center;
}

.search-input {
  --el-input-border-radius: 10px;
}

.search-input :deep(.el-input__wrapper) {
  border-radius: var(--radius);
  padding: 0 16px;
  box-shadow: var(--shadow-sm);
  border: 1px solid var(--border);
  transition: all var(--transition);
}

.search-input :deep(.el-input__wrapper:hover),
.search-input :deep(.el-input__wrapper:focus) {
  border-color: var(--accent);
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.1);
}

.filter-select {
  --el-select-border-radius: 10px;
}

.table-card {
  border-radius: var(--radius-lg);
  border: 1px solid var(--border);
  box-shadow: var(--shadow-sm);
}

.table-card :deep(.el-card__body) {
  padding: 0;
}

.refresh-btn {
  --el-button-bg-color: var(--card);
  --el-button-border-color: var(--border);
  border-radius: var(--radius);
  margin-left: auto;
}

/* Table Styles */
.modern-table :deep(.el-table__header th) {
  padding: 16px 12px;
  font-size: 13px;
  font-weight: 600;
  color: var(--muted-foreground);
}

.modern-table :deep(.el-table__body td) {
  padding: 16px 12px;
}

.modern-table :deep(.el-table__row) {
  transition: all var(--transition);
}

.modern-table :deep(.el-table__row:hover > td) {
  background: var(--muted) !important;
}

.user-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-avatar-icon {
  background: linear-gradient(135deg, var(--accent) 0%, var(--accent-dark) 100%);
  color: #fff;
  font-weight: 600;
}

.username {
  font-weight: 500;
  color: var(--foreground);
}

.role-tag {
  border-radius: 6px;
  padding: 4px 10px;
  border: none;
}

.status-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border-radius: 6px;
  padding: 4px 10px;
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

.date-text {
  font-size: 13px;
  color: var(--muted-foreground);
}

.action-buttons {
  display: flex;
  gap: 4px;
  align-items: center;
}

.action-btn {
  padding: 6px 10px;
  border-radius: 6px;
  font-size: 13px;
}

.delete-btn {
  color: var(--danger) !important;
}

.delete-btn:hover {
  background: var(--danger-light) !important;
}

.pagination-wrapper {
  padding: 16px 20px;
  display: flex;
  justify-content: flex-end;
}

/* Dialog Styles */
.user-dialog :deep(.el-dialog) {
  border-radius: 16px;
  overflow: hidden;
}

.user-dialog :deep(.el-dialog__header) {
  padding: 20px 24px;
  background: var(--muted);
  border-bottom: 1px solid var(--border);
}

.user-dialog :deep(.el-dialog__body) {
  padding: 24px;
}

.user-form :deep(.el-form-item__label) {
  font-weight: 500;
}

.form-input :deep(.el-input__wrapper),
.form-input :deep(.el-select__wrapper) {
  border-radius: var(--radius);
  padding: 0 16px;
  box-shadow: var(--shadow-sm);
  border: 1px solid var(--border);
  transition: all var(--transition);
}

.form-input :deep(.el-input__wrapper:hover),
.form-input :deep(.el-select__wrapper:hover) {
  border-color: var(--accent);
}

.form-input :deep(.el-input.is-disabled .el-input__wrapper) {
  background: var(--muted);
  border-color: var(--border);
}

.dialog-footer {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
}
</style>