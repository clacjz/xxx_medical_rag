<template>
  <div class="collection-permissions">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>知识库权限管理</span>
          <el-button type="primary" @click="handleRefresh">
            <el-icon><Refresh /></el-icon> 刷新
          </el-button>
        </div>
      </template>

      <div class="toolbar">
        <el-input
          v-model="keyword"
          placeholder="搜索知识库名称"
          style="width: 200px"
          clearable
          @clear="loadCollections"
          @keyup.enter="loadCollections"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-select v-model="filterPermissionType" placeholder="权限类型" clearable style="width: 150px">
          <el-option label="公开访问" value="PUBLIC" />
          <el-option label="部门内公开" value="DEPARTMENT_WIDE" />
          <el-option label="指定人员开放" value="RESTRICTED" />
          <el-option label="部门全员+指定人员" value="DEPARTMENT_WIDE_RESTRICTED" />
        </el-select>
        <el-button @click="loadCollections">
          <el-icon><Refresh /></el-icon>
        </el-button>
      </div>

      <el-table :data="filteredCollections" v-loading="loading" style="width: 100%; margin-top: 16px">
        <el-table-column prop="name" label="知识库名称" min-width="200" show-overflow-tooltip />
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
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
    </el-card>

    <el-dialog v-model="showPermissionDialog" title="配置知识库权限" width="600px">
      <el-form :model="permissionForm" label-width="120px">
        <el-form-item label="知识库名称">
          <span>{{ permissionForm.collectionName }}</span>
        </el-form-item>
        <el-form-item label="当前权限">
          <el-tag :type="getPermissionTypeTag(permissionForm.currentPermissionType)" size="small">
            {{ getPermissionTypeName(permissionForm.currentPermissionType) }}
          </el-tag>
        </el-form-item>
        <el-form-item label="权限类型" required>
          <el-select v-model="permissionForm.permissionType" placeholder="请选择权限类型" style="width: 100%">
            <el-option label="公开访问 - 所有认证用户可查看" value="PUBLIC" />
            <el-option label="部门内公开 - 本部门成员可查看" value="DEPARTMENT_WIDE" />
            <el-option label="指定人员开放 - 仅指定用户可查看" value="RESTRICTED" />
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

    <el-dialog v-model="showDetailDialog" title="知识库权限详情" width="700px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="知识库名称">{{ permissionDetail.collectionName }}</el-descriptions-item>
        <el-descriptions-item label="所有者">{{ permissionDetail.ownerName }}</el-descriptions-item>
        <el-descriptions-item label="权限类型">
          <el-tag :type="getPermissionTypeTag(permissionDetail.permissionType)" size="small">
            {{ getPermissionTypeName(permissionDetail.permissionType) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="授权人">{{ permissionDetail.granterName }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ formatDate(permissionDetail.updatedAt) }}</el-descriptions-item>
        <el-descriptions-item label="授权时间">{{ formatDate(permissionDetail.grantedAt) }}</el-descriptions-item>
      </el-descriptions>

      <div v-if="permissionDetail.allowedUsers && permissionDetail.allowedUsers.length > 0" style="margin-top: 20px;">
        <h4>已授权用户</h4>
        <el-table :data="permissionDetail.allowedUsers" size="small">
          <el-table-column prop="username" label="用户名" width="120" />
          <el-table-column prop="displayName" label="显示名称" width="120" />
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

      <div v-if="permissionDetail.accessCheck" style="margin-top: 20px;">
        <h4>我的访问权限</h4>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="是否有权限">
            <el-tag :type="permissionDetail.accessCheck.hasAccess ? 'success' : 'danger'" size="small">
              {{ permissionDetail.accessCheck.hasAccess ? '有权限' : '无权限' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="权限级别">
            <el-tag size="small">{{ getPermissionLevelName(permissionDetail.accessCheck.permissionLevel) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="权限原因" :span="2">
            {{ permissionDetail.accessCheck.reason }}
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
import { collectionsApi, usersApi } from '@/api'
import { formatDate, getPermissionTypeTag, getPermissionTypeName, getPermissionLevelName } from '@/utils/format'
import { Search, Refresh } from '@element-plus/icons-vue'
import type { CollectionPermissionType } from '@/types'

const collections = ref<any[]>([])
const allUsers = ref<any[]>([])
const loading = ref(false)
const submitting = ref(false)

const keyword = ref('')
const filterPermissionType = ref('')

const showPermissionDialog = ref(false)
const showDetailDialog = ref(false)

const permissionForm = reactive({
  collectionId: '',
  collectionName: '',
  currentPermissionType: '' as CollectionPermissionType | '',
  permissionType: '' as CollectionPermissionType | '',
  allowedUserIds: [] as string[],
  expiresAt: null as Date | null
})

const permissionDetail = reactive({
  collectionName: '',
  ownerName: '',
  permissionType: '' as CollectionPermissionType | '',
  granterName: '',
  grantedAt: '',
  updatedAt: '',
  allowedUsers: [] as any[],
  accessCheck: null as any
})

const filteredCollections = computed(() => {
  let result = collections.value

  if (filterPermissionType.value) {
    result = result.filter(col => col.permissionType === filterPermissionType.value)
  }

  if (keyword.value) {
    const lowerKeyword = keyword.value.toLowerCase()
    result = result.filter(col => col.name.toLowerCase().includes(lowerKeyword))
  }

  return result
})

const loadCollections = async () => {
  loading.value = true
  try {
    const res = await collectionsApi.list({ page: 0, pageSize: 100 })
    if (res.code === 200 && res.data) {
      collections.value = res.data.items

      for (const collection of collections.value) {
        try {
          const permRes = await collectionsApi.getPermission(collection.id)
          if (permRes.code === 200 && permRes.data) {
            collection.permissionType = permRes.data.permissionType
            collection.ownerName = permRes.data.ownerName
            collection.updatedAt = permRes.data.updatedAt
          }
        } catch (error) {
          collection.permissionType = 'RESTRICTED'
          collection.ownerName = '未知'
        }
      }
    }
  } catch (error) {
    console.error('Failed to load collections:', error)
  } finally {
    loading.value = false
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

const openPermissionDialog = async (collection: any) => {
  permissionForm.collectionId = collection.id
  permissionForm.collectionName = collection.name
  permissionForm.currentPermissionType = collection.permissionType || ''
  permissionForm.permissionType = collection.permissionType || 'RESTRICTED'
  permissionForm.allowedUserIds = []
  permissionForm.expiresAt = null

  try {
    const res = await collectionsApi.getPermission(collection.id)
    if (res.code === 200 && res.data) {
      permissionForm.currentPermissionType = res.data.permissionType
      permissionForm.permissionType = res.data.permissionType
      if (res.data.allowedUsers) {
        permissionForm.allowedUserIds = res.data.allowedUsers.map((u: any) => u.userId)
      }
    }
  } catch (error) {
    console.error('Failed to load collection permission:', error)
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
    const res = await collectionsApi.updatePermission(permissionForm.collectionId, {
      permissionType: permissionForm.permissionType,
      allowedUserIds: permissionForm.permissionType === 'RESTRICTED' ? permissionForm.allowedUserIds : undefined,
      expiresAt: permissionForm.expiresAt ? permissionForm.expiresAt.toISOString() : null
    })

    if (res.code === 200) {
      ElMessage.success('权限配置成功')
      showPermissionDialog.value = false
      loadCollections()
    }
  } finally {
    submitting.value = false
  }
}

const openDetailDialog = async (collection: any) => {
  try {
    const res = await collectionsApi.getPermission(collection.id)
    if (res.code === 200 && res.data) {
      permissionDetail.collectionName = collection.name
      permissionDetail.ownerName = res.data.ownerName ?? ''
      permissionDetail.permissionType = res.data.permissionType
      permissionDetail.granterName = res.data.granterName ?? ''
      permissionDetail.grantedAt = res.data.grantedAt ?? ''
      permissionDetail.updatedAt = res.data.updatedAt ?? ''
      permissionDetail.allowedUsers = res.data.allowedUsers || []
    }

    const checkRes = await collectionsApi.checkAccess(collection.id)
    if (checkRes.code === 200 && checkRes.data) {
      permissionDetail.accessCheck = checkRes.data
    }
  } catch (error) {
    console.error('Failed to load collection permission detail:', error)
  }

  showDetailDialog.value = true
}

const handleRefresh = () => {
  loadCollections()
}

onMounted(() => {
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
