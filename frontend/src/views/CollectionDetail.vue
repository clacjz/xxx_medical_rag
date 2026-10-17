<template>
  <div class="collection-detail">
    <el-card v-loading="loading">
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <el-button text @click="router.push('/collections')">
              <el-icon><ArrowLeft /></el-icon>
              返回
            </el-button>
            <span class="title">{{ collection?.name }}</span>
            <el-tag v-if="collection?.type" size="small" type="info">
              {{ CollectionTypeLabels[collection.type] || collection.type }}
            </el-tag>
          </div>
          <div class="header-right">
            <el-button @click="loadCollection">
              <el-icon><Refresh /></el-icon> 刷新
            </el-button>
            <el-button type="primary" @click="router.push(`/documents?collectionId=${route.params.id}`)">
              <el-icon><Upload /></el-icon> 上传文档
            </el-button>
          </div>
        </div>
      </template>

      <el-descriptions :column="3" border v-if="collection">
        <el-descriptions-item label="名称">{{ collection.name }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="getStatusType(collection.status || '')" size="small">
            {{ collection.status ? (CollectionStatusLabels[collection.status] || collection.status) : collection.status }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="描述" :span="2">{{ collection.description || '无' }}</el-descriptions-item>
        <el-descriptions-item label="文档处理策略">
          <el-tag :type="collection?.docType === 'MANUAL' ? 'warning' : 'success'" size="small">
            {{ DocTypeLabels[collection?.docType as DocType] || collection?.docType || 'NORMAL' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="业务分类">
          <el-tag size="small">{{ collection.type ? (CollectionTypeLabels[collection.type] || collection.type) : '-' }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="文档数">
          <el-statistic :value="collection.documentCount" />
        </el-descriptions-item>
        <el-descriptions-item label="分块数">
          <el-statistic :value="collection.chunkCount" />
        </el-descriptions-item>
        <el-descriptions-item label="总大小">
          {{ formatFileSize(collection.totalSize || 0) }}
        </el-descriptions-item>
        <el-descriptions-item label="检索TopK">
          {{ collection.config?.retrieval?.topK || 10 }}
        </el-descriptions-item>
        <el-descriptions-item label="相似度阈值">
          {{ ((collection.config?.retrieval?.similarityThreshold || 0.7) * 100).toFixed(0) }}%
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatDate(collection.createdAt) }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-tabs v-model="activeTab" class="detail-tabs">
      <el-tab-pane label="关联文档" name="documents">
        <el-card>
          <template #header>
            <div class="tab-header">
              <span>文档列表</span>
              <div class="tab-actions">
                <el-select
                  v-model="docFilters.status"
                  placeholder="处理状态"
                  style="width: 140px"
                  clearable
                  @change="loadDocuments"
                >
                  <el-option label="全部" value="" />
                  <el-option label="待处理" value="PENDING" />
                  <el-option label="处理中" value="PROCESSING" />
                  <el-option label="已完成" value="COMPLETED" />
                  <el-option label="失败" value="FAILED" />
                </el-select>
                <el-button type="primary" size="small" @click="showUploadDialog = true">
                  <el-icon><Upload /></el-icon> 上传文档
                </el-button>
              </div>
            </div>
          </template>

          <el-table :data="documents" v-loading="documentsLoading" style="width: 100%">
            <el-table-column prop="documentName" label="文档名称" min-width="200" show-overflow-tooltip>
              <template #default="{ row }">
                <span v-if="row.documentName">{{ row.documentName }}</span>
                <span v-else class="text-muted">未知文档</span>
              </template>
            </el-table-column>
            <el-table-column prop="documentType" label="类型" width="100" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.documentType" size="small">{{ row.documentType }}</el-tag>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column prop="processingStatus" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="getProcessingStatusType(row.processingStatus)" size="small">
                  {{ getProcessingStatusName(row.processingStatus) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="addedAt" label="添加时间" width="160">
              <template #default="{ row }">
                {{ formatDate(row.addedAt) }}
              </template>
            </el-table-column>
            <el-table-column prop="processingError" label="错误信息" width="200" show-overflow-tooltip>
              <template #default="{ row }">
                <span v-if="row.processingError" class="error-text">{{ row.processingError }}</span>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="row.processingStatus === 'PENDING' || row.processingStatus === 'COMPLETED' || row.processingStatus === 'FAILED'"
                  type="primary"
                  text
                  size="small"
                  @click="processDocument(row)"
                >
                  解析入库
                </el-button>
                <el-button
                  v-if="row.processingStatus === 'FAILED'"
                  type="warning"
                  text
                  size="small"
                  @click="retryDocument(row)"
                >
                  重试
                </el-button>
                <el-button
                  type="danger"
                  text
                  size="small"
                  @click="removeDocument(row)"
                >
                  移除
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            v-model:current-page="docsPagination.page"
            v-model:page-size="docsPagination.pageSize"
            :total="docsPagination.total"
            :page-sizes="[10, 20, 50]"
            layout="total, prev, pager, next"
            style="margin-top: 16px; justify-content: flex-end"
            @size-change="loadDocuments"
            @current-change="loadDocuments"
          />
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="统计指标" name="metrics">
        <el-card>
          <template #header>
            <div class="tab-header">
              <span>性能指标</span>
              <div class="tab-actions">
                <el-date-picker
                  v-model="metricsDateRange"
                  type="daterange"
                  range-separator="至"
                  start-placeholder="开始日期"
                  end-placeholder="结束日期"
                  value-format="YYYY-MM-DD"
                  format="YYYY-MM-DD"
                  @change="loadMetrics"
                />
                <el-button type="primary" size="small" @click="loadMetrics">
                  <el-icon><Refresh /></el-icon> 刷新
                </el-button>
              </div>
            </div>
          </template>

          <div class="metrics-grid" v-loading="metricsLoading">
            <el-row :gutter="16">
              <el-col :span="6">
                <el-statistic title="总查询量" :value="statistics.totalQueries">
                  <template #suffix>次</template>
                </el-statistic>
              </el-col>
              <el-col :span="6">
                <el-statistic title="平均延迟" :value="statistics.avgLatencyMs">
                  <template #suffix>ms</template>
                </el-statistic>
              </el-col>
              <el-col :span="6">
                <el-statistic title="P95延迟" :value="statistics.p95LatencyMs">
                  <template #suffix>ms</template>
                </el-statistic>
              </el-col>
              <el-col :span="6">
                <el-statistic title="错误率" :value="Number((statistics.errorRate * 100).toFixed(2))">
                  <template #suffix>%</template>
                </el-statistic>
              </el-col>
            </el-row>

            <el-table
              :data="metrics"
              style="width: 100%; margin-top: 24px"
              v-if="metrics.length > 0"
            >
              <el-table-column prop="metricType" label="指标类型" width="150">
                <template #default="{ row }">
                  {{ getMetricTypeName(row.metricType) }}
                </template>
              </el-table-column>
              <el-table-column prop="metricValue" label="指标值" width="150">
                <template #default="{ row }">
                  {{ row.metricValue.toFixed(2) }}
                </template>
              </el-table-column>
              <el-table-column prop="sampleSize" label="样本数" width="100" />
              <el-table-column prop="recordedAt" label="记录时间" width="180">
                <template #default="{ row }">
                  {{ formatDate(row.recordedAt) }}
                </template>
              </el-table-column>
            </el-table>

            <el-empty v-else description="暂无统计数据" />
          </div>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="快速问答" name="qa">
        <el-card>
          <template #header>
            <span>快速问答</span>
          </template>
          <div class="quick-qa">
            <el-select
              v-model="quickQuestion"
              placeholder="请选择或输入问题"
              filterable
              allow-create
              clearable
              style="width: 100%; margin-bottom: 16px"
            >
              <el-option label="这个知识库包含哪些内容？" value="这个知识库包含哪些内容？" />
              <el-option label="主要文档是什么格式？" value="主要文档是什么格式？" />
              <el-option label="最近上传了哪些文档？" value="最近上传了哪些文档？" />
              <el-option label="文档的分块策略是什么？" value="文档的分块策略是什么？" />
            </el-select>
            <el-button
              type="primary"
              :disabled="!quickQuestion"
              @click="startQA"
            >
              开始问答
            </el-button>
          </div>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="权限配置" name="permissions">
        <el-card>
          <template #header>
            <div class="tab-header">
              <span>知识库访问权限</span>
            </div>
          </template>

          <el-descriptions :column="2" border v-if="collectionPermission">
            <el-descriptions-item label="权限类型">
              <el-tag :type="getCollectionPermissionTypeTag(collectionPermission.permissionType)" size="small">
                {{ getCollectionPermissionTypeName(collectionPermission.permissionType) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="所有者">{{ collectionPermission.ownerName }}</el-descriptions-item>
            <el-descriptions-item label="授权人">{{ collectionPermission.granterName }}</el-descriptions-item>
            <el-descriptions-item label="更新时间">{{ formatDate(collectionPermission.updatedAt) }}</el-descriptions-item>
          </el-descriptions>

          <div v-if="collectionPermission?.allowedUsers?.length" style="margin-top: 20px;">
            <h4>已授权用户</h4>
            <el-table :data="collectionPermission.allowedUsers" size="small">
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
              <el-table-column label="操作" width="120" v-if="canManagePermission">
                <template #default="{ row }">
                  <el-button type="danger" text size="small" @click="handleRemoveUserPermission(row)">
                    移除
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>

          <div style="margin-top: 20px;" v-if="canManagePermission">
            <h4>添加用户权限</h4>
            <el-form :inline="true" :model="addUserForm">
              <el-form-item label="选择用户">
                <el-select v-model="addUserForm.userId" filterable placeholder="请选择用户" style="width: 200px">
                  <el-option v-for="user in allUsers" :key="user.id" :label="user.username" :value="user.id" />
                </el-select>
              </el-form-item>
              <el-form-item label="权限级别">
                <el-select v-model="addUserForm.permissionLevel" placeholder="请选择权限级别" style="width: 150px">
                  <el-option label="查看" value="READ" />
                  <el-option label="编辑" value="EDIT" />
                  <el-option label="管理" value="MANAGE" />
                  <el-option label="管理员" value="ADMIN" />
                </el-select>
              </el-form-item>
              <el-form-item label="过期时间">
                <el-date-picker v-model="addUserForm.expiresAt" type="datetime" placeholder="可选" />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="handleAddUserPermission">添加</el-button>
              </el-form-item>
            </el-form>
          </div>

          <div style="margin-top: 20px;">
            <h4>修改权限类型</h4>
            <el-form :inline="true" :model="permissionTypeForm">
              <el-form-item label="权限类型">
                <el-select v-model="permissionTypeForm.permissionType" placeholder="请选择权限类型" style="width: 200px">
                  <el-option label="公开访问" value="PUBLIC" />
                  <el-option label="部门内公开" value="DEPARTMENT_WIDE" />
                  <el-option label="指定人员开放" value="RESTRICTED" />
                  <el-option label="部门全员+指定人员" value="DEPARTMENT_WIDE_RESTRICTED" />
                </el-select>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="handleUpdatePermissionType">更新权限类型</el-button>
              </el-form-item>
            </el-form>
          </div>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <el-dialog
      v-model="showUploadDialog"
      title="批量上传文档"
      width="700px"
      :close-on-click-modal="false"
    >
      <div class="upload-dialog-content">
        <el-form :model="uploadForm" label-width="120px">
          <el-form-item label="上传文件">
            <el-upload
              ref="uploadRef"
              :auto-upload="false"
              :limit="50"
              :on-change="handleFileChange"
              :on-remove="handleFileRemove"
              :file-list="uploadFileList"
              accept=".pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.md,.txt"
              multiple
              drag
              class="batch-upload"
            >
              <div class="upload-content">
                <el-icon class="upload-icon"><Upload /></el-icon>
                <div class="upload-text">
                  <span class="text-main">将文件拖到此处，或<em>点击上传</em></span>
                  <span class="text-sub">支持 PDF、Word、Excel、PPT、Markdown、TXT 格式，单次最多50个文件</span>
                </div>
              </div>
            </el-upload>
          </el-form-item>

          <el-form-item label="处理选项">
            <el-checkbox v-model="uploadForm.autoProcess">上传后自动解析和向量化</el-checkbox>
            <div class="form-tip">启用后文件上传完成会立即开始处理，适合小文件快速入库</div>
          </el-form-item>

          <el-form-item v-if="uploadForm.autoProcess && collection?.docType !== 'MANUAL'" label="分块策略">
            <el-select v-model="uploadForm.strategy" style="width: 200px">
              <el-option label="固定长度分块" value="fixed" />
              <el-option label="段落分块" value="paragraph" />
              <el-option label="语义分块" value="semantic" />
              <el-option label="字符分块" value="character" />
              <el-option label="不分块" value="none" />
            </el-select>
          </el-form-item>

          <el-form-item v-if="uploadForm.autoProcess && collection?.docType === 'MANUAL'" label="处理方式">
            <el-alert type="info" :closable="false" show-icon>
              <template #title>
                说明书类文档将强制使用 <strong>OCR识别</strong>，不进行分块
              </template>
            </el-alert>
          </el-form-item>

          <el-form-item label="文档描述">
            <el-input
              v-model="uploadForm.description"
              type="textarea"
              :rows="2"
              placeholder="可选，为这批文档添加描述"
              style="width: 400px"
            />
          </el-form-item>
        </el-form>

        <el-divider v-if="uploadResults.length > 0" />

        <div v-if="uploadResults.length > 0" class="upload-results">
          <h4>上传结果</h4>
          <el-alert
            :title="`成功 ${uploadSuccessCount} 个，失败 ${uploadFailedCount} 个`"
            :type="uploadFailedCount > 0 ? 'warning' : 'success'"
            :closable="false"
            show-icon
            style="margin-bottom: 16px"
          />
          <el-table :data="uploadResults" size="small" max-height="300">
            <el-table-column prop="fileName" label="文件名" min-width="200" show-overflow-tooltip />
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.success ? 'success' : 'danger'" size="small">
                  {{ row.success ? '成功' : '失败' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="chunkCount" label="分块数" width="80" align="center">
              <template #default="{ row }">
                {{ row.chunkCount || '-' }}
              </template>
            </el-table-column>
            <el-table-column prop="errorMessage" label="错误" width="150" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="error-text">{{ row.errorMessage || '-' }}</span>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="closeUploadDialog">关闭</el-button>
          <el-button
            type="primary"
            :loading="uploading"
            :disabled="selectedFiles.length === 0"
            @click="handleBatchUpload"
          >
            上传 {{ selectedFiles.length > 0 ? `(${selectedFiles.length})` : '' }}
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { collectionsApi } from '@/api/collections'
import { documentsApi } from '@/api/documents'
import { usersApi } from '@/api/users'
import { formatDate, formatFileSize, getStatusType } from '@/utils/format'
import { ArrowLeft, Refresh, Upload } from '@element-plus/icons-vue'
import { useCollectionStore } from '@/stores/collections'
import type { CollectionDocument, CollectionMetrics, DocType } from '@/types'
import { CollectionTypeLabels, CollectionStatusLabels, DocTypeLabels } from '@/types'

interface Statistics {
  totalQueries: number
  avgLatencyMs: number
  p95LatencyMs: number
  errorRate: number
  activeUsers: number
}

const route = useRoute()
const router = useRouter()
const collectionStore = useCollectionStore()

const collectionId = computed(() => route.params.id as string)

const collection = computed(() => collectionStore.currentCollection)
const documents = computed(() => collectionStore.currentCollectionDocuments)
const documentsLoading = computed(() => collectionStore.documentsLoading)
const metricsLoading = computed(() => collectionStore.metricsLoading)
const docsPagination = computed(() => collectionStore.documentsPagination)

const loading = ref(false)
const activeTab = ref('documents')
const quickQuestion = ref('')

const docFilters = reactive({
  status: ''
})

const metricsDateRange = ref<any>(null)
const metrics = ref<CollectionMetrics[]>([])
const statistics = reactive<Statistics>({
  totalQueries: 0,
  avgLatencyMs: 0,
  p95LatencyMs: 0,
  errorRate: 0,
  activeUsers: 0
})

const showUploadDialog = ref(false)
const uploadRef = ref()
const selectedFiles = ref<File[]>([])
const uploadFileList = ref<any[]>([])
const uploading = ref(false)
const uploadResults = ref<Array<{
  fileName: string
  success: boolean
  documentId?: string
  chunkCount?: number
  errorMessage?: string
}>>([])

const uploadForm = reactive({
  autoProcess: false,
  strategy: 'fixed',
  description: ''
})

const uploadSuccessCount = computed(() => uploadResults.value.filter(r => r.success).length)
const uploadFailedCount = computed(() => uploadResults.value.filter(r => !r.success).length)

const handleFileChange = (_file: any, fileList: any[]) => {
  selectedFiles.value = fileList.map(f => f.raw)
  uploadFileList.value = fileList
}

const handleFileRemove = (_file: any, fileList: any[]) => {
  selectedFiles.value = fileList.map(f => f.raw)
  uploadFileList.value = fileList
}

const handleBatchUpload = async () => {
  if (selectedFiles.value.length === 0) {
    ElMessage.warning('请选择要上传的文件')
    return
  }

  uploading.value = true
  uploadResults.value = []

  // 根据知识库类型确定分块策略
  // MANUAL类型强制使用 none（不分块）
  // 使用 || 'NORMAL' 确保即使 collection.value 为 null 或 docType 为 undefined 时也有默认值
  const collectionDocType = collection.value?.docType || 'NORMAL'
  const effectiveStrategy = collectionDocType === 'MANUAL'
    ? 'none'
    : uploadForm.strategy

  try {
    const res = await collectionsApi.batchUploadDocuments(
      collectionId.value,
      selectedFiles.value,
      {
        autoProcess: uploadForm.autoProcess,
        strategy: effectiveStrategy,
        description: uploadForm.description || undefined
      }
    )

    if (res.code === 200 && res.data) {
      uploadResults.value = res.data.results.map((r: any) => ({
        fileName: r.fileName,
        success: r.success,
        documentId: r.documentId,
        chunkCount: r.chunkCount,
        errorMessage: r.errorMessage
      }))

      const msg = `上传完成：成功 ${res.data.successCount} 个，失败 ${res.data.failedCount} 个`
      if (res.data.autoProcess) {
        ElMessage.success(msg + '，文档已加入处理队列')
      } else {
        ElMessage.success(msg)
      }

      loadDocuments()
      loadCollection()
    } else {
      ElMessage.error(res.message || '上传失败')
    }
  } catch (error: any) {
    ElMessage.error('上传失败: ' + (error.message || '未知错误'))
  } finally {
    uploading.value = false
  }
}

const closeUploadDialog = () => {
  showUploadDialog.value = false
  selectedFiles.value = []
  uploadFileList.value = []
  uploadResults.value = []
  uploadForm.autoProcess = false
  uploadForm.strategy = 'fixed'
  uploadForm.description = ''
}

const collectionPermission = ref<any>(null)
const allUsers = ref<any[]>([])
const canManagePermission = ref(false)

const addUserForm = reactive({
  userId: '',
  permissionLevel: 'READ' as 'NONE' | 'READ' | 'EDIT' | 'MANAGE' | 'ADMIN',
  expiresAt: null as Date | null
})

const permissionTypeForm = reactive({
  permissionType: 'RESTRICTED' as 'PUBLIC' | 'DEPARTMENT_WIDE' | 'RESTRICTED' | 'DEPARTMENT_WIDE_RESTRICTED'
})

const loadCollection = async () => {
  loading.value = true
  try {
    await collectionStore.loadCollection(collectionId.value)
  } catch (error) {
    console.error('Failed to load collection:', error)
  } finally {
    loading.value = false
  }
}

const loadDocuments = async () => {
  await collectionStore.loadCollectionDocuments(collectionId.value, {
    page: docsPagination.value.page,
    pageSize: docsPagination.value.pageSize,
    status: docFilters.status || undefined
  })
}

const loadMetrics = async () => {
  try {
    const [startDate, endDate] = metricsDateRange.value || [undefined, undefined]
    
    const metricsRes = await collectionsApi.getMetrics(collectionId.value, {
      startDate,
      endDate,
      metricTypes: 'QUERY_COUNT,LATENCY_P50,LATENCY_P95,LATENCY_P99,ERROR_RATE,USER_COUNT'
    })
    
    if (metricsRes.code === 200 && metricsRes.data) {
      metrics.value = metricsRes.data
    }

    const statsRes = await collectionsApi.getStatistics(collectionId.value)
    if (statsRes.code === 200 && statsRes.data) {
      Object.assign(statistics, statsRes.data)
    }
  } catch (error) {
    console.error('Failed to load metrics:', error)
  }
}

const retryDocument = async (row: CollectionDocument) => {
  try {
    await documentsApi.retry(row.documentId)
    ElMessage.success('已提交重试任务')
    loadDocuments()
  } catch (error) {
    console.error('Failed to retry document:', error)
  }
}

// 解析入库文档
const processDocument = async (row: CollectionDocument) => {
  try {
    await documentsApi.processDocument(row.documentId)
    ElMessage.success('已提交处理任务')
    loadDocuments()
  } catch (error) {
    console.error('Failed to process document:', error)
  }
}

const removeDocument = async (row: CollectionDocument) => {
  try {
    await ElMessageBox.confirm(
      `确定要从知识库移除文档"${row.documentName}"吗？`,
      '提示',
      { type: 'warning' }
    )
    const res = await collectionsApi.removeDocument(collectionId.value, row.documentId)
    if (res.code === 200) {
      ElMessage.success('移除成功')
      loadDocuments()
      loadCollection()
    }
  } catch {
    // 用户取消
  }
}

const startQA = () => {
  if (quickQuestion.value) {
    router.push({
      path: '/qa',
      query: {
        collectionId: collectionId.value,
        question: quickQuestion.value
      }
    })
  }
}

const getProcessingStatusType = (status: string | undefined): 'success' | 'warning' | 'danger' | undefined => {
  if (!status) return undefined
  const typeMap: Record<string, 'success' | 'warning' | 'danger'> = {
    PENDING: 'warning',
    PROCESSING: 'warning',
    COMPLETED: 'success',
    FAILED: 'danger'
  }
  return typeMap[status]
}

const getProcessingStatusName = (status: string): string => {
  const nameMap: Record<string, string> = {
    PENDING: '待处理',
    PROCESSING: '处理中',
    COMPLETED: '已完成',
    FAILED: '失败'
  }
  return nameMap[status] || status
}

const getMetricTypeName = (type: string): string => {
  const nameMap: Record<string, string> = {
    QUERY_COUNT: '查询次数',
    LATENCY_P50: 'P50延迟',
    LATENCY_P95: 'P95延迟',
    LATENCY_P99: 'P99延迟',
    ERROR_RATE: '错误率',
    RETRIEVAL_RECALL: '召回率',
    TOKEN_USAGE: 'Token消耗',
    USER_COUNT: '活跃用户'
  }
  return nameMap[type] || type
}

const getCollectionPermissionTypeTag = (type: string): 'success' | 'warning' | 'danger' | 'info' => {
  const typeMap: Record<string, 'success' | 'warning' | 'danger' | 'info'> = {
    PUBLIC: 'success',
    DEPARTMENT_WIDE: 'warning',
    RESTRICTED: 'danger',
    DEPARTMENT_WIDE_RESTRICTED: 'warning'
  }
  return typeMap[type] || 'info'
}

const getCollectionPermissionTypeName = (type: string): string => {
  const nameMap: Record<string, string> = {
    PUBLIC: '公开访问',
    DEPARTMENT_WIDE: '部门内公开',
    RESTRICTED: '指定人员开放',
    DEPARTMENT_WIDE_RESTRICTED: '部门全员+指定人员'
  }
  return nameMap[type] || type
}

const getPermissionLevelName = (level: string): string => {
  const nameMap: Record<string, string> = {
    NONE: '无权限',
    READ: '查看',
    EDIT: '编辑',
    MANAGE: '管理',
    ADMIN: '管理员'
  }
  return nameMap[level] || level
}

const loadCollectionPermission = async () => {
  try {
    const res = await collectionsApi.getPermission(collectionId.value)
    if (res.code === 200 && res.data) {
      collectionPermission.value = res.data
      permissionTypeForm.permissionType = res.data.permissionType
    }
  } catch (error) {
    console.error('Failed to load collection permission:', error)
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

const checkPermissionAccess = async () => {
  try {
    const res = await collectionsApi.checkAccess(collectionId.value)
    if (res.code === 200 && res.data) {
      canManagePermission.value = res.data.permissionLevel === 'ADMIN' || res.data.permissionLevel === 'MANAGE'
    }
  } catch (error) {
    console.error('Failed to check permission access:', error)
  }
}

const handleAddUserPermission = async () => {
  if (!addUserForm.userId || !addUserForm.permissionLevel) {
    ElMessage.warning('请选择用户和权限级别')
    return
  }

  try {
    const res = await collectionsApi.addUserPermission(collectionId.value, {
      userId: addUserForm.userId,
      permissionLevel: addUserForm.permissionLevel,
      expiresAt: addUserForm.expiresAt ? addUserForm.expiresAt.toISOString() : null
    })

    if (res.code === 200) {
      ElMessage.success('用户权限添加成功')
      addUserForm.userId = ''
      addUserForm.permissionLevel = 'READ'
      addUserForm.expiresAt = null
      await loadCollectionPermission()
    }
  } catch (error) {
    console.error('Failed to add user permission:', error)
    ElMessage.error('添加用户权限失败')
  }
}

const handleRemoveUserPermission = async (user: any) => {
  try {
    await ElMessageBox.confirm(`确定要移除用户 ${user.username} 的权限吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    const res = await collectionsApi.removeUserPermission(collectionId.value, user.userId)
    if (res.code === 200) {
      ElMessage.success('用户权限已移除')
      await loadCollectionPermission()
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('Failed to remove user permission:', error)
      ElMessage.error('移除用户权限失败')
    }
  }
}

const handleUpdatePermissionType = async () => {
  try {
    const res = await collectionsApi.updatePermission(collectionId.value, {
      permissionType: permissionTypeForm.permissionType,
      expiresAt: null
    })

    if (res.code === 200) {
      ElMessage.success('权限类型更新成功')
      await loadCollectionPermission()
    }
  } catch (error) {
    console.error('Failed to update permission type:', error)
    ElMessage.error('更新权限类型失败')
  }
}

const handleTabChange = async (tab: string) => {
  if (tab === 'metrics' && metrics.value.length === 0) {
    await loadMetrics()
  }
  if (tab === 'permissions' && !collectionPermission.value) {
    await Promise.all([
      loadCollectionPermission(),
      loadAllUsers(),
      checkPermissionAccess()
    ])
  }
}

watch(activeTab, handleTabChange)

onMounted(() => {
  loadCollection()
  loadDocuments()
})
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-left .title {
  font-size: 18px;
  font-weight: bold;
}

.header-right {
  display: flex;
  gap: 8px;
}

.detail-tabs {
  margin-top: 16px;
}

.tab-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.tab-actions {
  display: flex;
  gap: 12px;
  align-items: center;
}

.quick-qa {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.error-text {
  color: var(--el-color-danger);
  font-size: 12px;
}

.metrics-grid {
  padding: 16px 0;
}

:deep(.el-descriptions__label) {
  width: 120px;
}

:deep(.el-transfer-panel) {
  width: 240px;
}

.upload-dialog-content {
  padding: 10px 0;
}

.batch-upload {
  width: 100%;
}

.batch-upload :deep(.el-upload-dragger) {
  width: 100%;
  padding: 40px 20px;
  border-radius: 12px;
  border: 2px dashed var(--border);
  background: var(--muted);
}

.upload-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}

.upload-icon {
  font-size: 48px;
  color: var(--primary);
}

.upload-text {
  text-align: center;
}

.text-main {
  font-size: 16px;
  color: var(--foreground);
}

.text-main em {
  color: var(--primary);
  font-style: normal;
}

.text-sub {
  display: block;
  font-size: 14px;
  color: var(--muted-foreground);
  margin-top: 8px;
}

.form-tip {
  font-size: 12px;
  color: var(--muted-foreground);
  margin-top: 4px;
}

.upload-results {
  margin-top: 16px;
}

.upload-results h4 {
  margin: 0 0 12px 0;
  font-size: 14px;
  font-weight: 500;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
