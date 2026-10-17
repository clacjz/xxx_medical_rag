<template>
  <div class="vector-management">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>向量数据库管理</span>
          <el-button type="primary" @click="loadCollections">
            <el-icon><Refresh /></el-icon> 刷新
          </el-button>
        </div>
      </template>

      <el-tabs v-model="activeTab" class="main-tabs">
        <el-tab-pane label="Collection管理" name="collections">
          <div class="tab-content">
            <div class="toolbar">
              <el-button type="primary" @click="showCreateCollectionDialog = true">
                <el-icon><Plus /></el-icon> 创建Collection
              </el-button>
              <el-input
                v-model="collectionSearch"
                placeholder="搜索Collection"
                style="width: 240px"
                clearable
                @clear="loadCollections"
                @keyup.enter="loadCollections"
              >
                <template #prefix>
                  <el-icon><Search /></el-icon>
                </template>
              </el-input>
            </div>

            <el-table
              :data="filteredCollections"
              v-loading="collectionsLoading"
              style="width: 100%; margin-top: 16px"
            >
              <el-table-column prop="name" label="Collection名称" min-width="180">
                <template #default="{ row }">
                  <span class="collection-name">{{ row.name }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="vectorsCount" label="向量数" width="120" align="center">
                <template #default="{ row }">
                  {{ formatNumber(row.vectorsCount) }}
                </template>
              </el-table-column>
              <el-table-column prop="pointsCount" label="点数" width="120" align="center">
                <template #default="{ row }">
                  {{ formatNumber(row.pointsCount) }}
                </template>
              </el-table-column>
              <el-table-column prop="indexedVectorsCount" label="已索引数" width="120" align="center">
                <template #default="{ row }">
                  {{ formatNumber(row.indexedVectorsCount) }}
                </template>
              </el-table-column>
              <el-table-column prop="status" label="状态" width="100" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.status === 'GREEN' ? 'success' : 'info'" size="small">
                    {{ row.status }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="createdAt" label="创建时间" width="160">
                <template #default="{ row }">
                  {{ formatTimestamp(row.createdAt) }}
                </template>
              </el-table-column>
              <el-table-column label="操作" width="200" fixed="right">
                <template #default="{ row }">
                  <el-button type="primary" text size="small" @click="selectCollection(row.name)">
                    选择
                  </el-button>
                  <el-button type="primary" text size="small" @click="viewCollectionStats(row.name)">
                    详情
                  </el-button>
                  <el-button
                    type="danger"
                    text
                    size="small"
                    @click="handleDeleteCollection(row.name)"
                    :loading="deletingCollection === row.name"
                  >
                    删除
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>

        <el-tab-pane label="向量检索" name="search">
          <div class="tab-content">
            <el-row :gutter="24">
              <el-col :span="8">
                <el-card shadow="hover" class="collection-selector">
                  <template #header>
                    <div class="card-header-small">
                      <span>选择Collection</span>
                      <el-button type="primary" text size="small" @click="activeTab = 'collections'">
                        管理Collection
                      </el-button>
                    </div>
                  </template>
                  <el-select
                    v-model="selectedCollection"
                    placeholder="请选择Collection"
                    style="width: 100%"
                    @change="onCollectionChange"
                  >
                    <el-option
                      v-for="col in collections"
                      :key="col.name"
                      :label="col.name"
                      :value="col.name"
                    />
                  </el-select>

                  <div v-if="selectedCollection" class="search-config">
                    <el-divider content-position="left">检索参数</el-divider>
                    <el-form label-width="80px" label-position="left">
                      <el-form-item label="返回数量">
                        <el-input-number
                          v-model="searchParams.limit"
                          :min="1"
                          :max="100"
                          controls-position="right"
                          style="width: 100%"
                        />
                      </el-form-item>
                      <el-form-item label="相似度阈值">
                        <el-input-number
                          v-model="searchParams.scoreThreshold"
                          :min="0"
                          :max="1"
                          :step="0.01"
                          :precision="2"
                          controls-position="right"
                          style="width: 100%"
                        />
                      </el-form-item>
                      <el-form-item label="EF参数">
                        <el-input-number
                          v-model="searchParams.ef"
                          :min="1"
                          :max="1024"
                          controls-position="right"
                          style="width: 100%"
                        />
                      </el-form-item>
                    </el-form>
                  </div>
                </el-card>
              </el-col>

              <el-col :span="16">
                <el-card shadow="hover">
                  <template #header>
                    <div class="card-header-small">
                      <span>向量检索</span>
                      <span v-if="selectedCollection" class="current-collection">
                        当前: {{ selectedCollection }}
                      </span>
                    </div>
                  </template>

                  <el-form label-width="100px">
                    <el-form-item label="查询向量">
                      <el-input
                        v-model="searchVectorInput"
                        type="textarea"
                        :rows="4"
                        placeholder="请输入向量数组，格式: [0.1, 0.2, 0.3, ...]"
                      />
                    </el-form-item>
                    <el-form-item label="快捷示例">
                      <el-button-group>
                        <el-button size="small" @click="loadRandomVector">加载随机向量</el-button>
                        <el-button size="small" @click="loadLastVector" :disabled="!searchHistory.length">
                          使用上次查询
                        </el-button>
                      </el-button-group>
                    </el-form-item>
                    <el-form-item>
                      <el-button
                        type="primary"
                        @click="handleSearch"
                        :loading="searchLoading"
                        :disabled="!selectedCollection || !searchVectorInput"
                      >
                        <el-icon><Search /></el-icon> 检索
                      </el-button>
                      <el-button @click="clearSearch">清空</el-button>
                    </el-form-item>
                  </el-form>

                  <el-divider />

                  <div v-if="searchResults.length > 0" class="search-results">
                    <div class="results-header">
                      <span>检索结果</span>
                      <el-tag type="info">{{ searchResults.length }} 条结果</el-tag>
                    </div>
                    <el-table :data="searchResults" style="width: 100%; margin-top: 12px" max-height="400">
                      <el-table-column prop="id" label="ID" width="180" show-overflow-tooltip />
                      <el-table-column prop="score" label="相似度" width="100" align="center">
                        <template #default="{ row }">
                          <el-tag :type="getScoreType(row.score)" size="small">
                            {{ (row.score * 100).toFixed(2) }}%
                          </el-tag>
                        </template>
                      </el-table-column>
                      <el-table-column label="Payload" min-width="200">
                        <template #default="{ row }">
                          <el-input
                            v-model="row.payloadDisplay"
                            type="textarea"
                            :rows="2"
                            readonly
                            size="small"
                            @focus="editPayload(row)"
                          />
                        </template>
                      </el-table-column>
                      <el-table-column label="操作" width="100" align="center">
                        <template #default="{ row }">
                          <el-button type="primary" text size="small" @click="viewVectorDetail(row)">
                            详情
                          </el-button>
                        </template>
                      </el-table-column>
                    </el-table>
                  </div>
                  <el-empty v-else-if="!searchLoading" description="请输入向量进行检索" />
                </el-card>
              </el-col>
            </el-row>
          </div>
        </el-tab-pane>

        <el-tab-pane label="向量操作" name="crud">
          <div class="tab-content">
            <el-row :gutter="24">
              <el-col :span="8">
                <el-card shadow="hover" class="collection-selector">
                  <template #header>
                    <span>选择Collection</span>
                  </template>
                  <el-select
                    v-model="crudCollection"
                    placeholder="请选择Collection"
                    style="width: 100%"
                  >
                    <el-option
                      v-for="col in collections"
                      :key="col.name"
                      :label="col.name"
                      :value="col.name"
                    />
                  </el-select>
                </el-card>
              </el-col>
              <el-col :span="16">
                <el-card shadow="hover">
                  <template #header>
                    <div class="card-header-small">
                      <span>插入向量</span>
                    </div>
                  </template>
                  <el-form label-width="100px">
                    <el-form-item label="向量数据">
                      <el-input
                        v-model="insertForm.vector"
                        type="textarea"
                        :rows="3"
                        placeholder="请输入向量数组，格式: [0.1, 0.2, 0.3, ...]"
                      />
                    </el-form-item>
                    <el-form-item label="Payload">
                      <el-input
                        v-model="insertForm.payload"
                        type="textarea"
                        :rows="3"
                        placeholder='JSON格式: {"key": "value"}'
                      />
                    </el-form-item>
                    <el-form-item>
                      <el-button
                        type="primary"
                        @click="handleInsertVector"
                        :loading="insertLoading"
                        :disabled="!crudCollection || !insertForm.vector"
                      >
                        插入
                      </el-button>
                    </el-form-item>
                  </el-form>
                </el-card>

                <el-card shadow="hover" style="margin-top: 16px">
                  <template #header>
                    <div class="card-header-small">
                      <span>查询向量</span>
                    </div>
                  </template>
                  <el-form label-width="100px">
                    <el-form-item label="向量ID">
                      <el-input
                        v-model="queryPointId"
                        placeholder="请输入向量ID"
                        clearable
                      />
                    </el-form-item>
                    <el-form-item>
                      <el-button
                        type="primary"
                        @click="handleGetVector"
                        :loading="queryLoading"
                        :disabled="!crudCollection || !queryPointId"
                      >
                        查询
                      </el-button>
                    </el-form-item>
                  </el-form>
                  <div v-if="queryResult" class="query-result">
                    <el-input
                      v-model="queryResultDisplay"
                      type="textarea"
                      :rows="4"
                      readonly
                    />
                  </div>
                </el-card>

                <el-card shadow="hover" style="margin-top: 16px">
                  <template #header>
                    <div class="card-header-small">
                      <span>删除向量</span>
                    </div>
                  </template>
                  <el-form label-width="100px">
                    <el-form-item label="向量ID">
                      <el-input
                        v-model="deletePointId"
                        placeholder="请输入要删除的向量ID"
                      />
                    </el-form-item>
                    <el-form-item>
                      <el-button
                        type="danger"
                        @click="handleDeleteVector"
                        :loading="deleteLoading"
                        :disabled="!crudCollection || !deletePointId"
                      >
                        删除
                      </el-button>
                    </el-form-item>
                  </el-form>
                </el-card>
              </el-col>
            </el-row>
          </div>
        </el-tab-pane>

        <el-tab-pane label="批量操作" name="batch">
          <div class="tab-content">
            <el-row :gutter="24">
              <el-col :span="12">
                <el-card shadow="hover">
                  <template #header>
                    <div class="card-header-small">
                      <span>批量插入</span>
                    </div>
                  </template>
                  <el-form label-width="100px">
                    <el-form-item label="Collection">
                      <el-select
                        v-model="batchInsertCollection"
                        placeholder="选择Collection"
                        style="width: 100%"
                      >
                        <el-option
                          v-for="col in collections"
                          :key="col.name"
                          :label="col.name"
                          :value="col.name"
                        />
                      </el-select>
                    </el-form-item>
                    <el-form-item label="向量数据">
                      <el-input
                        v-model="batchInsertData"
                        type="textarea"
                        :rows="8"
                        placeholder='JSON数组格式:\n[\n  {"vector": [0.1, 0.2, ...], "payload": {...}},\n  {"vector": [0.3, 0.4, ...], "payload": {...}}\n]'
                      />
                    </el-form-item>
                    <el-form-item>
                      <el-button
                        type="primary"
                        @click="handleBatchInsert"
                        :loading="batchInsertLoading"
                        :disabled="!batchInsertCollection || !batchInsertData"
                      >
                        批量插入
                      </el-button>
                      <el-button @click="loadSampleBatchData">
                        加载示例
                      </el-button>
                    </el-form-item>
                  </el-form>
                </el-card>

                <el-card shadow="hover" style="margin-top: 16px">
                  <template #header>
                    <div class="card-header-small">
                      <span>批量删除</span>
                    </div>
                  </template>
                  <el-form label-width="100px">
                    <el-form-item label="Collection">
                      <el-select
                        v-model="batchDeleteCollection"
                        placeholder="选择Collection"
                        style="width: 100%"
                      >
                        <el-option
                          v-for="col in collections"
                          :key="col.name"
                          :label="col.name"
                          :value="col.name"
                        />
                      </el-select>
                    </el-form-item>
                    <el-form-item label="向量ID列表">
                      <el-input
                        v-model="batchDeleteIds"
                        type="textarea"
                        :rows="4"
                        placeholder='JSON数组格式:\n["id1", "id2", "id3"]'
                      />
                    </el-form-item>
                    <el-form-item>
                      <el-button
                        type="danger"
                        @click="handleBatchDelete"
                        :loading="batchDeleteLoading"
                        :disabled="!batchDeleteCollection || !batchDeleteIds"
                      >
                        批量删除
                      </el-button>
                    </el-form-item>
                  </el-form>
                </el-card>
              </el-col>

              <el-col :span="12">
                <el-card shadow="hover">
                  <template #header>
                    <div class="card-header-small">
                      <span>可恢复批量操作</span>
                      <el-button type="primary" text size="small" @click="loadBatchStatuses">
                        <el-icon><Refresh /></el-icon> 刷新
                      </el-button>
                    </div>
                  </template>

                  <div class="resumable-section">
                    <el-button
                      type="primary"
                      @click="handleStartResumableBatch"
                      :disabled="!batchInsertCollection"
                    >
                      启动可恢复批量操作
                    </el-button>
                  </div>

                  <el-divider />

                  <div class="batch-status-list">
                    <div v-if="activeBatches.length === 0" class="empty-batches">
                      <el-empty description="暂无进行中的批量操作" :image-size="80" />
                    </div>
                    <div
                      v-for="batch in activeBatches"
                      :key="batch.batchId"
                      class="batch-item"
                    >
                      <div class="batch-header">
                        <span class="batch-id">{{ batch.batchId }}</span>
                        <el-tag :type="(BatchStatusTypeMap[batch.status] as 'success' | 'primary' | 'warning' | 'danger' | 'info')" size="small">
                          {{ BatchStatusLabels[batch.status] }}
                        </el-tag>
                      </div>
                      <el-progress
                        :percentage="getBatchProgress(batch)"
                        :status="getProgressStatus(batch.status)"
                        :stroke-width="8"
                      />
                      <div class="batch-info">
                        <span>已处理: {{ batch.processedItems }} / {{ batch.totalItems }}</span>
                        <span>批次: {{ batch.currentBatchIndex }} / {{ batch.totalBatches }}</span>
                      </div>
                      <div class="batch-actions">
                        <el-button
                          v-if="batch.status === 'RUNNING'"
                          type="warning"
                          size="small"
                          @click="handlePauseBatch(batch.batchId)"
                          :loading="batchActionLoading === batch.batchId + '_pause'"
                        >
                          暂停
                        </el-button>
                        <el-button
                          v-if="batch.status === 'PAUSED'"
                          type="success"
                          size="small"
                          @click="handleResumeBatch(batch.batchId)"
                          :loading="batchActionLoading === batch.batchId + '_resume'"
                        >
                          恢复
                        </el-button>
                        <el-button
                          v-if="batch.status === 'RUNNING' || batch.status === 'PAUSED'"
                          type="danger"
                          size="small"
                          @click="handleCancelBatch(batch.batchId)"
                          :loading="batchActionLoading === batch.batchId + '_cancel'"
                        >
                          取消
                        </el-button>
                        <el-button
                          type="info"
                          size="small"
                          @click="loadBatchStatus(batch.batchId)"
                        >
                          刷新
                        </el-button>
                      </div>
                    </div>
                  </div>
                </el-card>
              </el-col>
            </el-row>

            <el-card v-if="batchResult" shadow="hover" style="margin-top: 16px">
              <template #header>
                <div class="card-header-small">
                  <span>批量操作结果</span>
                  <el-button type="primary" text size="small" @click="batchResult = null">
                    关闭
                  </el-button>
                </div>
              </template>
              <div class="batch-result">
                <el-descriptions :column="2" border>
                  <el-descriptions-item label="总数">{{ batchResult.totalCount }}</el-descriptions-item>
                  <el-descriptions-item label="成功">
                    <span class="success-text">{{ batchResult.successCount }}</span>
                  </el-descriptions-item>
                  <el-descriptions-item label="失败">
                    <span class="error-text">{{ batchResult.failedCount }}</span>
                  </el-descriptions-item>
                  <el-descriptions-item label="耗时">{{ batchResult.durationMs }}ms</el-descriptions-item>
                </el-descriptions>
                <div v-if="batchResult.failedPointIds && batchResult.failedPointIds.length > 0" class="failed-ids">
                  <span class="failed-label">失败的ID:</span>
                  <el-tag
                    v-for="id in batchResult.failedPointIds"
                    :key="id"
                    type="danger"
                    size="small"
                    style="margin: 4px"
                  >
                    {{ id }}
                  </el-tag>
                </div>
              </div>
            </el-card>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog
      v-model="showCreateCollectionDialog"
      title="创建Collection"
      width="500px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="createCollectionFormRef"
        :model="createCollectionForm"
        :rules="createCollectionRules"
        label-width="100px"
      >
        <el-form-item label="名称" prop="collectionName">
          <el-input
            v-model="createCollectionForm.collectionName"
            placeholder="请输入Collection名称"
            maxlength="128"
          />
        </el-form-item>
        <el-form-item label="向量维度" prop="vectorSize">
          <el-input-number
            v-model="createCollectionForm.vectorSize"
            :min="1"
            :max="4096"
            controls-position="right"
            style="width: 100%"
          />
          <div class="form-tip">
            BGE模型默认维度为768，请根据实际使用的Embedding模型设置
          </div>
        </el-form-item>
        <el-form-item label="距离度量" prop="distance">
          <el-select v-model="createCollectionForm.distance" style="width: 100%">
            <el-option label="余弦相似度 (Cosine)" value="Cosine" />
            <el-option label="点积 (Dot)" value="Dot" />
            <el-option label="欧氏距离 (Euclidean)" value="Euclidean" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateCollectionDialog = false">取消</el-button>
        <el-button type="primary" @click="handleCreateCollection" :loading="createLoading">
          创建
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="showVectorDetailDialog"
      title="向量详情"
      width="600px"
    >
      <el-descriptions v-if="vectorDetail" :column="1" border>
        <el-descriptions-item label="ID">{{ vectorDetail.id }}</el-descriptions-item>
        <el-descriptions-item label="相似度" v-if="vectorDetail.score">
          {{ (vectorDetail.score * 100).toFixed(2) }}%
        </el-descriptions-item>
        <el-descriptions-item label="Payload">
          <pre class="payload-display">{{ formatPayload(vectorDetail.payload) }}</pre>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { qdrantApi } from '@/api'
import type {
  CollectionStats,
  VectorSearchResult,
  BatchInsertResult,
  ResumeState,
  BatchStatus
} from '@/types'
import { BatchStatusLabels, BatchStatusTypeMap } from '@/types'
import type { VectorInsertResponse } from '@/api/qdrant'

const activeTab = ref('collections')
const collections = ref<CollectionStats[]>([])
const collectionsLoading = ref(false)
const collectionSearch = ref('')
const deletingCollection = ref('')

const showCreateCollectionDialog = ref(false)
const createCollectionFormRef = ref<FormInstance>()
const createCollectionForm = reactive({
  collectionName: '',
  vectorSize: 768,
  distance: 'Cosine'
})
const createCollectionRules: FormRules = {
  collectionName: [
    { required: true, message: '请输入Collection名称', trigger: 'blur' },
    { min: 1, max: 128, message: '名称长度在1-128个字符', trigger: 'blur' }
  ],
  vectorSize: [
    { required: true, message: '请输入向量维度', trigger: 'change' }
  ]
}
const createLoading = ref(false)

const selectedCollection = ref('')
const crudCollection = ref('')
const searchVectorInput = ref('')
const searchParams = reactive({
  limit: 10,
  scoreThreshold: 0.7,
  ef: 128
})
const searchLoading = ref(false)
const searchResults = ref<(VectorSearchResult & { payloadDisplay: string })[]>([])
const searchHistory = ref<string[]>([])

const insertForm = reactive({
  vector: '',
  payload: ''
})
const insertLoading = ref(false)

const queryPointId = ref('')
const queryLoading = ref(false)
const queryResult = ref<Record<string, unknown> | null>(null)
const queryResultDisplay = ref('')

const deletePointId = ref('')
const deleteLoading = ref(false)

const batchInsertCollection = ref('')
const batchInsertData = ref('')
const batchInsertLoading = ref(false)

const batchDeleteCollection = ref('')
const batchDeleteIds = ref('')
const batchDeleteLoading = ref(false)

const activeBatches = ref<ResumeState[]>([])
const batchActionLoading = ref('')
const batchResult = ref<BatchInsertResult | null>(null)

const showVectorDetailDialog = ref(false)
const vectorDetail = ref<VectorSearchResult | null>(null)

const filteredCollections = computed(() => {
  if (!collectionSearch.value) return collections.value
  return collections.value.filter(col =>
    col.name.toLowerCase().includes(collectionSearch.value.toLowerCase())
  )
})

const loadCollections = async () => {
  collectionsLoading.value = true
  try {
    const res = await qdrantApi.listCollections()
    if (res.code === 200 && res.data) {
      const collectionNames = res.data as unknown as string[]
      const statsPromises = collectionNames.map(name => qdrantApi.getCollectionInfo(name))
      const statsResults = await Promise.allSettled(statsPromises)
      collections.value = (statsResults as unknown as Array<PromiseFulfilledResult<{ data: CollectionStats }>>)
        .filter(r => r.status === 'fulfilled')
        .map(r => r.value.data)
        .filter(Boolean) as CollectionStats[]
    }
  } catch (error) {
    console.error('Failed to load collections:', error)
  } finally {
    collectionsLoading.value = false
  }
}

const handleCreateCollection = async () => {
  if (!createCollectionFormRef.value) return
  await createCollectionFormRef.value.validate(async (valid) => {
    if (valid) {
      createLoading.value = true
      try {
        await qdrantApi.createCollection({
          collectionName: createCollectionForm.collectionName,
          vectorSize: createCollectionForm.vectorSize,
          distance: createCollectionForm.distance
        })
        ElMessage.success('Collection创建成功')
        showCreateCollectionDialog.value = false
        createCollectionFormRef.value?.resetFields()
        await loadCollections()
      } catch (error) {
        console.error('Failed to create collection:', error)
      } finally {
        createLoading.value = false
      }
    }
  })
}

const handleDeleteCollection = async (name: string) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除Collection "${name}" 吗？此操作将删除所有向量数据且不可恢复！`,
      '警告',
      {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        confirmButtonClass: 'el-button--danger'
      }
    )
    deletingCollection.value = name
    await qdrantApi.deleteCollection(name)
    ElMessage.success('Collection删除成功')
    await loadCollections()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('Failed to delete collection:', error)
    }
  } finally {
    deletingCollection.value = ''
  }
}

const viewCollectionStats = async (name: string) => {
  try {
    const res = await qdrantApi.getCollectionInfo(name)
    if (res.code === 200 && res.data) {
      const data = res.data as unknown as CollectionStats
      ElMessageBox.alert(
        `Collection: ${data.name}\n向量数: ${data.vectorsCount}\n点数: ${data.pointsCount}\n已索引数: ${data.indexedVectorsCount}\n状态: ${data.status}`,
        'Collection详情'
      )
    }
  } catch (error) {
    console.error('Failed to get collection stats:', error)
  }
}

const selectCollection = (name: string) => {
  selectedCollection.value = name
  crudCollection.value = name
  activeTab.value = 'search'
}

const onCollectionChange = () => {
  searchResults.value = []
  searchVectorInput.value = ''
}

const handleSearch = async () => {
  if (!selectedCollection.value || !searchVectorInput.value) return

  let vector: number[]
  try {
    vector = JSON.parse(searchVectorInput.value)
    if (!Array.isArray(vector) || vector.length === 0) {
      ElMessage.error('请输入有效的向量数组')
      return
    }
  } catch {
    ElMessage.error('向量格式错误，请输入JSON数组格式')
    return
  }

  searchLoading.value = true
  try {
    const res = await qdrantApi.searchVectors(selectedCollection.value, {
      vector,
      limit: searchParams.limit,
      scoreThreshold: searchParams.scoreThreshold,
      ef: searchParams.ef
    })

    if (res.code === 200 && res.data) {
      const results = res.data as unknown as VectorSearchResult[]
      searchResults.value = results.map((item: VectorSearchResult) => ({
        ...item,
        payloadDisplay: JSON.stringify(item.payload, null, 2)
      }))
      searchHistory.value.unshift(searchVectorInput.value)
      if (searchHistory.value.length > 10) {
        searchHistory.value.pop()
      }
      ElMessage.success(`检索完成，返回 ${results.length} 条结果`)
    }
  } catch (error) {
    console.error('Failed to search vectors:', error)
  } finally {
    searchLoading.value = false
  }
}

const loadRandomVector = () => {
  const dimension = createCollectionForm.vectorSize || 768
  const vector = Array.from({ length: dimension }, () => Math.random() * 2 - 1)
  searchVectorInput.value = JSON.stringify(vector)
}

const loadLastVector = () => {
  if (searchHistory.value.length > 0) {
    searchVectorInput.value = searchHistory.value[0]
  }
}

const clearSearch = () => {
  searchVectorInput.value = ''
  searchResults.value = []
}

const getScoreType = (score: number): 'success' | 'primary' | 'warning' | 'danger' => {
  if (score >= 0.9) return 'success'
  if (score >= 0.7) return 'primary'
  if (score >= 0.5) return 'warning'
  return 'danger'
}

const editPayload = (row: VectorSearchResult & { payloadDisplay: string }) => {
  try {
    row.payload = JSON.parse(row.payloadDisplay)
  } catch {
    // ignore
  }
}

const viewVectorDetail = (row: VectorSearchResult) => {
  vectorDetail.value = row
  showVectorDetailDialog.value = true
}

const formatPayload = (payload: Record<string, unknown>): string => {
  return JSON.stringify(payload, null, 2)
}

const handleInsertVector = async () => {
  if (!crudCollection.value || !insertForm.vector) return

  let vector: number[]
  let payload: Record<string, unknown> | undefined

  try {
    vector = JSON.parse(insertForm.vector)
  } catch {
    ElMessage.error('向量格式错误')
    return
  }

  if (insertForm.payload) {
    try {
      payload = JSON.parse(insertForm.payload)
    } catch {
      ElMessage.error('Payload格式错误')
      return
    }
  }

  insertLoading.value = true
  try {
    const res = await qdrantApi.insertVector(crudCollection.value, {
      vector,
      payload
    })
    if (res.code === 200 && res.data) {
      const data = res.data as unknown as VectorInsertResponse
      ElMessage.success(`向量插入成功，ID: ${data.pointId}`)
      insertForm.vector = ''
      insertForm.payload = ''
    }
  } catch (error) {
    console.error('Failed to insert vector:', error)
  } finally {
    insertLoading.value = false
  }
}

const handleGetVector = async () => {
  if (!crudCollection.value || !queryPointId.value) return

  queryLoading.value = true
  queryResult.value = null
  try {
    const res = await qdrantApi.getVector(crudCollection.value, queryPointId.value)
    if (res.code === 200 && res.data) {
      queryResult.value = res.data as unknown as Record<string, unknown>
      queryResultDisplay.value = JSON.stringify(res.data, null, 2)
    }
  } catch (error) {
    console.error('Failed to get vector:', error)
  } finally {
    queryLoading.value = false
  }
}

const handleDeleteVector = async () => {
  if (!crudCollection.value || !deletePointId.value) return

  try {
    await ElMessageBox.confirm(
      `确定要删除向量 "${deletePointId.value}" 吗？`,
      '确认',
      { type: 'warning' }
    )
    deleteLoading.value = true
    await qdrantApi.deleteVector(crudCollection.value, deletePointId.value)
    ElMessage.success('向量删除成功')
    deletePointId.value = ''
  } catch (error) {
    if (error !== 'cancel') {
      console.error('Failed to delete vector:', error)
    }
  } finally {
    deleteLoading.value = false
  }
}

const handleBatchInsert = async () => {
  if (!batchInsertCollection.value || !batchInsertData.value) return

  let items
  try {
    items = JSON.parse(batchInsertData.value)
  } catch {
    ElMessage.error('数据格式错误，请输入JSON数组格式')
    return
  }

  if (!Array.isArray(items)) {
    ElMessage.error('数据必须为数组格式')
    return
  }

  batchInsertLoading.value = true
  batchResult.value = null
  try {
    const res = await qdrantApi.batchInsertVectors(batchInsertCollection.value, {
      items: items.map((item: { vector: number[]; payload?: Record<string, unknown> }) => ({
        vector: item.vector,
        payload: item.payload
      })),
      waitForCompletion: true
    })
    if (res.code === 200 && res.data) {
      const data = res.data as unknown as BatchInsertResult
      batchResult.value = data
      ElMessage.success(`批量插入完成: 成功 ${data.successCount}, 失败 ${data.failedCount}`)
      await loadCollections()
    }
  } catch (error) {
    console.error('Failed to batch insert:', error)
  } finally {
    batchInsertLoading.value = false
  }
}

const loadSampleBatchData = () => {
  const dimension = createCollectionForm.vectorSize || 768
  const sampleData = [
    {
      vector: Array.from({ length: dimension }, () => Math.random() * 2 - 1),
      payload: { text: '示例文本1', category: 'sample' }
    },
    {
      vector: Array.from({ length: dimension }, () => Math.random() * 2 - 1),
      payload: { text: '示例文本2', category: 'sample' }
    },
    {
      vector: Array.from({ length: dimension }, () => Math.random() * 2 - 1),
      payload: { text: '示例文本3', category: 'sample' }
    }
  ]
  batchInsertData.value = JSON.stringify(sampleData, null, 2)
}

const handleBatchDelete = async () => {
  if (!batchDeleteCollection.value || !batchDeleteIds.value) return

  let pointIds
  try {
    pointIds = JSON.parse(batchDeleteIds.value)
  } catch {
    ElMessage.error('ID列表格式错误，请输入JSON数组格式')
    return
  }

  if (!Array.isArray(pointIds)) {
    ElMessage.error('ID列表必须为数组格式')
    return
  }

  try {
    await ElMessageBox.confirm(
      `确定要删除 ${pointIds.length} 个向量吗？`,
      '确认',
      { type: 'warning' }
    )
    batchDeleteLoading.value = true
    await qdrantApi.batchDeleteVectors(batchDeleteCollection.value, { pointIds })
    ElMessage.success('批量删除成功')
    batchDeleteIds.value = ''
    await loadCollections()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('Failed to batch delete:', error)
    }
  } finally {
    batchDeleteLoading.value = false
  }
}

const handleStartResumableBatch = async () => {
  if (!batchInsertCollection.value || !batchInsertData.value) return

  let items
  try {
    items = JSON.parse(batchInsertData.value)
  } catch {
    ElMessage.error('数据格式错误')
    return
  }

  try {
    const res = await qdrantApi.startResumableBatch(batchInsertCollection.value, {
      items: items.map((item: { vector: number[]; payload?: Record<string, unknown> }) => ({
        vector: item.vector,
        payload: item.payload
      })),
      waitForCompletion: false
    })
    if (res.code === 200 && res.data) {
      ElMessage.success('可恢复批量操作已启动')
      await loadBatchStatuses()
    }
  } catch (error) {
    console.error('Failed to start resumable batch:', error)
  }
}

const loadBatchStatuses = async () => {
  // Note: This would require a list-batches endpoint from backend
  // For now, we poll active batches from the in-memory state
}

const loadBatchStatus = async (batchId: string) => {
  try {
    const res = await qdrantApi.getBatchStatus(batchId)
    if (res.code === 200 && res.data) {
      const data = res.data as unknown as ResumeState
      const index = activeBatches.value.findIndex(b => b.batchId === batchId)
      if (index >= 0) {
        activeBatches.value[index] = data
      }
    }
  } catch (error) {
    console.error('Failed to load batch status:', error)
  }
}

const handlePauseBatch = async (batchId: string) => {
  batchActionLoading.value = batchId + '_pause'
  try {
    const res = await qdrantApi.pauseBatch(batchId)
    if (res.code === 200 && res.data) {
      ElMessage.success('批量操作已暂停')
      await loadBatchStatus(batchId)
    }
  } catch (error) {
    console.error('Failed to pause batch:', error)
  } finally {
    batchActionLoading.value = ''
  }
}

const handleResumeBatch = async (batchId: string) => {
  batchActionLoading.value = batchId + '_resume'
  try {
    const res = await qdrantApi.resumeBatch(batchId)
    if (res.code === 200 && res.data) {
      ElMessage.success('批量操作已恢复')
      await loadBatchStatus(batchId)
    }
  } catch (error) {
    console.error('Failed to resume batch:', error)
  } finally {
    batchActionLoading.value = ''
  }
}

const handleCancelBatch = async (batchId: string) => {
  try {
    await ElMessageBox.confirm('确定要取消批量操作吗？', '确认', { type: 'warning' })
    batchActionLoading.value = batchId + '_cancel'
    const res = await qdrantApi.cancelBatch(batchId)
    if (res.code === 200 && res.data) {
      ElMessage.success('批量操作已取消')
      await loadBatchStatus(batchId)
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('Failed to cancel batch:', error)
    }
  } finally {
    batchActionLoading.value = ''
  }
}

const getBatchProgress = (batch: ResumeState): number => {
  if (batch.totalItems === 0) return 0
  return Math.round((batch.processedItems / batch.totalItems) * 100)
}

const getProgressStatus = (status: BatchStatus): 'success' | 'warning' | 'exception' | undefined => {
  const statusMap: Record<BatchStatus, 'success' | 'warning' | 'exception' | undefined> = {
    RUNNING: undefined,
    PAUSED: 'warning',
    COMPLETED: 'success',
    FAILED: 'exception',
    CANCELLED: 'warning'
  }
  return statusMap[status]
}

const formatNumber = (num: number): string => {
  if (num >= 1000000) return (num / 1000000).toFixed(1) + 'M'
  if (num >= 1000) return (num / 1000).toFixed(1) + 'K'
  return String(num || 0)
}

const formatTimestamp = (timestamp: number): string => {
  if (!timestamp) return '-'
  return new Date(timestamp).toLocaleString('zh-CN')
}

onMounted(() => {
  loadCollections()
})
</script>

<style scoped>
.vector-management {
  padding: 0;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-header-small {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.main-tabs {
  margin-top: 0;
}

.tab-content {
  padding: 16px 0;
}

.toolbar {
  display: flex;
  gap: 12px;
  align-items: center;
}

.collection-name {
  font-weight: 500;
  color: var(--accent);
}

.collection-selector {
  position: sticky;
  top: 0;
}

.search-config {
  margin-top: 20px;
}

.current-collection {
  color: var(--success);
  font-weight: 500;
}

.search-results {
  margin-top: 16px;
}

.results-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.query-result {
  margin-top: 12px;
}

.resumable-section {
  padding: 12px 0;
}

.batch-status-list {
  max-height: 400px;
  overflow-y: auto;
}

.empty-batches {
  padding: 24px 0;
}

.batch-item {
  padding: 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  margin-bottom: 12px;
}

.batch-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.batch-id {
  font-weight: 500;
  font-size: 13px;
  color: var(--foreground);
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.batch-info {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: var(--muted-foreground);
  margin: 8px 0;
}

.batch-actions {
  display: flex;
  gap: 8px;
}

.batch-result {
  padding: 12px 0;
}

.success-text {
  color: var(--success);
  font-weight: 500;
}

.error-text {
  color: var(--danger);
  font-weight: 500;
}

.failed-ids {
  margin-top: 12px;
}

.failed-label {
  display: block;
  margin-bottom: 8px;
  color: var(--danger);
  font-weight: 500;
}

.payload-display {
  background: var(--muted);
  padding: 12px;
  border-radius: var(--radius);
  font-size: 12px;
  max-height: 200px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
}

.form-tip {
  font-size: 12px;
  color: var(--muted-foreground);
  margin-top: 4px;
  line-height: 1.4;
}

:deep(.el-card__header) {
  padding: 12px 16px;
}

:deep(.el-divider--horizontal) {
  margin: 16px 0;
}
</style>
