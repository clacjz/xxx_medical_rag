<template>
  <div class="document-permissions">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>文档权限管理</span>
          <el-button type="primary" @click="handleRefresh">
            <el-icon><Refresh /></el-icon> 刷新
          </el-button>
        </div>
      </template>

      <div class="toolbar">
        <el-select v-model="filterCollection" placeholder="选择知识库" clearable style="width: 200px">
          <el-option v-for="col in collections" :key="col.id" :label="col.name" :value="col.id" />
        </el-select>
        <el-select v-model="filterPermissionType" placeholder="权限类型" clearable style="width: 150px">
          <el-option label="全公司可查看" value="COMPANY_WIDE" />
          <el-option label="部门内公开" value="DEPARTMENT_WIDE" />
          <el-option label="指定人员开放" value="RESTRICTED" />
          <el-option label="部门全员+指定人员" value="DEPARTMENT_WIDE_RESTRICTED" />
        </el-select>
        <el-input
          v-model="keyword"
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
          <el-icon><Refresh /></el-icon>
        </el-button>
      </div>

      <el-table :data="filteredDocuments" v-loading="loading" style="width: 100%; margin-top: 16px">
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
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" text size="small" @click="openPermissionDialog(row)">
              配置权限
            </el-button>
            <el-button type="primary" text size="small" @click="openDetailDialog(row)">
              详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.pageSize"
        :total="pagination.total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        style="margin-top: 16px; justify-content: flex-end"
        @size-change="loadDocuments"
        @current-change="loadDocuments"
      />
    </el-card>

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
            <el-option label="部门全员+指定人员" value="DEPARTMENT_WIDE_RESTRICTED" />
          </el-select>
        </el-form-item>
        <el-form-item label="指定用户" v-if="['RESTRICTED', 'DEPARTMENT_WIDE_RESTRICTED'].includes(permissionForm.permissionType)">
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

    <el-dialog v-model="showDetailDialog" title="文档权限详情" width="700px">
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
        <el-button @click="showDetailDialog = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { documentsApi, collectionsApi, permissionsApi, usersApi } from '@/api'
import { formatDate, getPermissionTypeTag, getPermissionTypeName, getPermissionLevelName } from '@/utils/format'
import { Search, Refresh } from '@element-plus/icons-vue'
import type { Collection } from '@/types'
import type { PermissionType } from '@/types'

const documents = ref<any[]>([])
const collections = ref<Collection[]>([])
const allUsers = ref<any[]>([])
const loading = ref(false)
const submitting = ref(false)

const filterCollection = ref('')
const filterPermissionType = ref('')
const keyword = ref('')

const pagination = reactive({
  page: 0,
  pageSize: 10,
  total: 0
})

const showPermissionDialog = ref(false)
const showDetailDialog = ref(false)

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

const filteredDocuments = computed(() => {
  let result = documents.value

  if (filterPermissionType.value) {
    result = result.filter(doc => doc.permissionType === filterPermissionType.value)
  }

  if (keyword.value) {
    const lowerKeyword = keyword.value.toLowerCase()
    result = result.filter(doc => doc.name.toLowerCase().includes(lowerKeyword))
  }

  return result
})

const loadDocuments = async () => {
  loading.value = true
  try {
    const res = await documentsApi.list({
      page: pagination.page,
      pageSize: pagination.pageSize,
      collectionId: filterCollection.value || undefined
    })
    if (res.code === 200 && res.data) {
      documents.value = res.data.items
      pagination.total = res.data.total

      for (const doc of documents.value) {
        try {
          const permRes = await permissionsApi.getDocumentPermission(doc.id)
          if (permRes.code === 200 && permRes.data) {
            doc.permissionType = permRes.data.permissionType
            doc.ownerName = permRes.data.ownerName
          }
        } catch (error) {
          doc.permissionType = 'RESTRICTED'
          doc.ownerName = '未知'
        }
      }
    }
  } catch (error) {
    console.error('Failed to load documents:', error)
  } finally {
    loading.value = false
  }
}

const loadCollections = async () => {
  try {
    const res = await collectionsApi.list({ page: 0, pageSize: 100 })
    if (res.code === 200 && res.data) {
      collections.value = res.data.items as Collection[]
    }
  } catch (error) {
    console.error('Failed to load collections:', error)
  }
}

const loadAllUsers = async () => {
  try {
    const res = await usersApi.list({ page: 0, pageSize: 1000 })
    if (res.code === 200 && res.data) {
      allUsers.value = res.data.items
    }
  } catch (error) {
    console.error('Failed to load users:', error)
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

const openDetailDialog = async (doc: any) => {
  try {
    const res = await permissionsApi.getDocumentPermission(doc.id)
    if (res.code === 200 && res.data) {
      const data = res.data as any
      permissionDetail.documentName = doc.name
      permissionDetail.ownerName = data.ownerName
      permissionDetail.permissionType = data.permissionType
      permissionDetail.updatedAt = data.updatedAt
      permissionDetail.allowedUsers = data.allowedUsers || []
      permissionDetail.effectivePermissions = data.effectivePermissions || null
    }
  } catch (error) {
    console.error('Failed to load document permission detail:', error)
  }

  showDetailDialog.value = true
}

const handleRefresh = () => {
  loadDocuments()
}

onMounted(() => {
  loadDocuments()
  loadCollections()
  loadAllUsers()
})
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.toolbar {
  display: flex;
  gap: 12px;
  margin-top: 16px;
  flex-wrap: wrap;
}

h4 {
  margin: 0 0 12px 0;
  color: var(--foreground);
}
</style>
