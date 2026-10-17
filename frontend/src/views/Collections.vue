<template>
  <div class="collections">
    <!-- Page Header -->
    <div class="page-header">
      <div class="header-content">
        <h1 class="page-title">知识库列表</h1>
        <p class="page-subtitle">管理和查看所有知识库</p>
      </div>
      <el-button type="primary" class="create-btn" @click="showCreateDialog = true">
        <el-icon><Plus /></el-icon> 创建知识库
      </el-button>
    </div>

    <!-- Search and Filter Bar -->
    <el-card class="filter-card" shadow="hover">
      <div class="toolbar">
        <el-input
          v-model="filters.keyword"
          placeholder="搜索知识库..."
          style="width: 260px"
          clearable
          @clear="handleSearch"
          @keyup.enter="handleSearch"
          class="search-input"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>

        <el-select
          v-model="filters.type"
          placeholder="类型"
          style="width: 160px"
          clearable
          @change="handleSearch"
          class="filter-select"
        >
          <el-option
            v-for="(label, value) in CollectionTypeLabels"
            :key="value"
            :label="label"
            :value="value"
          />
        </el-select>

        <el-select
          v-model="filters.status"
          placeholder="状态"
          style="width: 140px"
          clearable
          @change="handleSearch"
          class="filter-select"
        >
          <el-option
            v-for="(label, value) in CollectionStatusLabels"
            :key="value"
            :label="label"
            :value="value"
          />
        </el-select>

        <el-button @click="handleSearch" class="refresh-btn">
          <el-icon><Refresh /></el-icon> 刷新
        </el-button>
      </div>
    </el-card>

    <!-- Data Table -->
    <el-card class="table-card" shadow="hover">
      <el-table
        :data="collections"
        v-loading="loading"
        style="width: 100%"
        class="modern-table"
        :header-cell-style="{ background: 'var(--muted)', color: 'var(--foreground)', fontWeight: '600' }"
      >
        <el-table-column prop="name" label="名称" min-width="180">
          <template #default="{ row }">
            <div class="name-cell">
              <div class="collection-icon">
                <el-icon><Collection /></el-icon>
              </div>
              <div class="name-content">
                <span class="name">{{ row.name }}</span>
                <el-tag v-if="row.type" size="small" type="info" class="type-tag">
                  {{ CollectionTypeLabels[row.type as CollectionType] || row.type }}
                </el-tag>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column prop="documentCount" label="文档数" width="100" align="center">
          <template #default="{ row }">
            <span class="count-badge">{{ row.documentCount || 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="chunkCount" label="分块数" width="100" align="center">
          <template #default="{ row }">
            {{ formatNumber(row.chunkCount) }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small" class="status-tag">
              <span class="status-dot"></span>
              {{ CollectionStatusLabels[row.status as CollectionStatus] || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170">
          <template #default="{ row }">
            <span class="date-text">{{ formatDate(row.createdAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <div class="action-buttons">
              <el-button type="primary" text size="small" class="action-btn" @click="viewDetail(row)">
                详情
              </el-button>
              <el-button type="primary" text size="small" class="action-btn" @click="handleEdit(row)">
                编辑
              </el-button>
              <el-dropdown trigger="click" @command="(cmd: string) => handleCommand(cmd, row)" class="more-dropdown">
                <el-button type="primary" text size="small" class="action-btn">
                  更多<el-icon class="el-icon--right"><arrow-down /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu class="action-menu">
                    <el-dropdown-item command="config">
                      <el-icon><Setting /></el-icon> 配置
                    </el-dropdown-item>
                    <el-dropdown-item command="duplicate">
                      <el-icon><DocumentCopy /></el-icon> 复制
                    </el-dropdown-item>
                    <el-dropdown-item v-if="row.status === 'ACTIVE'" command="archive">
                      <el-icon><FolderDelete /></el-icon> 归档
                    </el-dropdown-item>
                    <el-dropdown-item v-if="row.status === 'ARCHIVED'" command="restore">
                      <el-icon><RefreshLeft /></el-icon> 恢复
                    </el-dropdown-item>
                    <el-dropdown-item command="delete" divided class="delete-item">
                      <el-icon><Delete /></el-icon> 删除
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.pageSize"
          :total="pagination.total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handlePageChange"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>

    <!-- Create Dialog -->
    <el-dialog
      v-model="showCreateDialog"
      title="创建知识库"
      width="650px"
      :close-on-click-modal="false"
      class="create-dialog"
    >
      <el-steps :active="createStep" finish-status="success" class="create-steps">
        <el-step title="基本信息" />
        <el-step title="类型配置" />
        <el-step title="检索配置" />
        <el-step title="权限配置" />
        <el-step title="生成配置" />
      </el-steps>

      <el-form
        ref="formRef"
        :model="createForm"
        :rules="createRules"
        label-width="110px"
        class="create-form"
      >
        <div v-show="createStep === 0">
          <el-form-item label="名称" prop="name">
            <el-input
              v-model="createForm.name"
              placeholder="请输入知识库名称"
              maxlength="128"
              show-word-limit
              class="form-input"
            />
          </el-form-item>
          <el-form-item label="描述" prop="description">
            <el-input
              v-model="createForm.description"
              type="textarea"
              :rows="3"
              placeholder="请输入知识库描述"
              maxlength="1024"
              show-word-limit
              class="form-input"
            />
          </el-form-item>
        </div>

        <div v-show="createStep === 1">
          <el-form-item label="业务分类" prop="type">
            <el-select v-model="createForm.type" placeholder="请选择业务分类" style="width: 100%" class="form-input">
              <el-option
                v-for="(label, value) in CollectionTypeLabels"
                :key="value"
                :label="label"
                :value="value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="文档处理策略" prop="docType">
            <el-select v-model="createForm.docType" placeholder="请选择文档处理策略" style="width: 100%" class="form-input">
              <el-option
                v-for="(label, value) in DocTypeLabels"
                :key="value"
                :label="label"
                :value="value"
              />
            </el-select>
            <div class="form-tip" style="color: var(--el-text-color-secondary); font-size: 12px; margin-top: 4px;">
              {{ createForm.docType === 'MANUAL' ? '说明书类文档：将强制使用OCR识别，不进行分块' : '普通文档：使用语义分块处理' }}
            </div>
          </el-form-item>

          <!-- MANUAL类型：字段模板配置 -->
          <el-form-item v-if="createForm.docType === 'MANUAL'" label="字段模板" required>
            <div class="section-template-editor">
              <div v-for="(field, index) in createForm.sectionTemplate" :key="index" class="template-field-row">
                <el-input v-model="field.field" placeholder="字段名" style="width: 120px" />
                <el-input v-model="field.label" placeholder="显示标签" style="width: 150px" />
                <el-checkbox v-model="field.required">必填</el-checkbox>
                <el-button type="danger" text @click="removeTemplateField(index)">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </div>
              <div class="template-actions">
                <el-button type="primary" text @click="addTemplateField">
                  <el-icon><Plus /></el-icon> 添加字段
                </el-button>
                <el-button type="info" text @click="showJsonImportDialog = true">
                  <el-icon><Upload /></el-icon> 导入JSON模板
                </el-button>
              </div>
            </div>
            <div class="form-tip" style="color: var(--el-color-warning); font-size: 12px; margin-top: 8px;">
              <el-icon><InfoFilled /></el-icon>
              说明书类知识库必须配置字段模板，用于OCR后提取结构化字段
            </div>
          </el-form-item>

          <!-- NORMAL类型：分块策略（推荐semantic） -->
          <el-form-item v-if="createForm.docType !== 'MANUAL'" label="分块策略" prop="config.chunking.strategy">
            <el-select v-model="createForm.config.chunking.strategy" placeholder="请选择分块策略" style="width: 100%" class="form-input">
              <el-option
                v-for="(label, value) in ChunkingStrategyLabels"
                :key="value"
                :label="label"
                :value="value"
              />
            </el-select>
            <div class="form-tip" style="color: var(--el-text-color-secondary); font-size: 12px; margin-top: 4px;">
              普通文档推荐使用语义分块或段落分块
            </div>
          </el-form-item>
          <!-- 固定大小分块配置 -->
          <el-form-item v-if="createForm.docType !== 'MANUAL' && (createForm.config.chunking.strategy === 'CHARACTER' || createForm.config.chunking.strategy === 'SENTENCE')" label="分块大小">
            <div class="slider-container">
              <el-slider
                v-model="createForm.config.chunking.chunkSize"
                :min="100"
                :max="2000"
                :step="100"
              />
              <span class="slider-value">{{ createForm.config.chunking.chunkSize }} 字符</span>
            </div>
          </el-form-item>
          <el-form-item v-if="createForm.docType !== 'MANUAL' && (createForm.config.chunking.strategy === 'CHARACTER' || createForm.config.chunking.strategy === 'SENTENCE')" label="重叠大小">
            <div class="slider-container">
              <el-slider
                v-model="createForm.config.chunking.chunkOverlap"
                :min="0"
                :max="200"
                :step="20"
              />
              <span class="slider-value">{{ createForm.config.chunking.chunkOverlap }} 字符</span>
            </div>
          </el-form-item>
          <!-- 语义分块提示 -->
          <el-form-item v-if="createForm.docType !== 'MANUAL' && (createForm.config.chunking.strategy === 'PARAGRAPH' || createForm.config.chunking.strategy === 'SEMANTIC')" >
            <div class="info-tip">
              <el-icon><InfoFilled /></el-icon>
              <span>语义分块会自动按段落/语义边界切分，无需手动设置大小</span>
            </div>
          </el-form-item>
        </div>

        <div v-show="createStep === 2">
          <el-form-item label="召回数量">
            <el-input-number
              v-model="createForm.config.retrieval.topK"
              :min="1"
              :max="100"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="相似度阈值">
            <div class="slider-container">
              <el-slider
                v-model="createForm.config.retrieval.similarityThreshold"
                :min="0"
                :max="1"
                :step="0.05"
                :format-tooltip="(val: number) => `${(val * 100).toFixed(0)}%`"
              />
              <span class="slider-value">{{ (createForm.config.retrieval.similarityThreshold * 100).toFixed(0) }}%</span>
            </div>
          </el-form-item>
          <el-form-item label="启用重排序">
            <el-switch v-model="createForm.config.retrieval.rerankEnabled" />
          </el-form-item>
          <el-form-item label="查询扩展">
            <el-switch v-model="createForm.config.retrieval.queryExpansionEnabled" />
          </el-form-item>
        </div>

        <div v-show="createStep === 3">
          <el-form-item label="权限类型" prop="permissionType">
            <el-select v-model="createForm.permissionType" placeholder="选择权限类型" style="width: 100%" class="form-input">
              <el-option label="全公司可见" value="PUBLIC" />
              <el-option label="部门内公开" value="DEPARTMENT_WIDE" />
              <el-option label="指定人员（最多10人）" value="RESTRICTED" />
              <el-option label="部门+指定人员" value="DEPARTMENT_WIDE_RESTRICTED" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="createForm.permissionType === 'RESTRICTED' || createForm.permissionType === 'DEPARTMENT_WIDE_RESTRICTED'" label="指定用户">
            <el-select v-model="createForm.allowedUserIds" multiple filterable remote :remote-method="searchUsers" placeholder="搜索并选择用户" style="width: 100%">
              <el-option v-for="u in userOptions" :key="u.id" :label="u.displayName || u.username" :value="u.id" />
            </el-select>
            <div class="form-tip" style="color: var(--el-text-color-secondary); font-size: 12px; margin-top: 4px;">
              选择可访问此知识库的用户（最多10人）
            </div>
          </el-form-item>
        </div>

        <div v-show="createStep === 4">
          <el-form-item label="最大Token">
            <el-input-number
              v-model="createForm.config.generation.maxTokens"
              :min="100"
              :max="8000"
              :step="100"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="温度参数">
            <div class="slider-container">
              <el-slider
                v-model="createForm.config.generation.temperature"
                :min="0"
                :max="1"
                :step="0.1"
              />
              <span class="slider-value">{{ createForm.config.generation.temperature.toFixed(1) }}</span>
            </div>
          </el-form-item>
          <el-form-item label="流式输出">
            <el-switch v-model="createForm.config.generation.streamEnabled" />
          </el-form-item>
          <el-form-item label="系统提示词">
            <el-input
              v-model="createForm.config.generation.systemPrompt"
              type="textarea"
              :rows="3"
              placeholder="可选，自定义系统提示词"
              class="form-input"
            />
          </el-form-item>
        </div>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button v-if="createStep > 0" @click="createStep--">上一步</el-button>
          <el-button v-if="createStep < 4" type="primary" @click="nextStep">下一步</el-button>
          <el-button v-if="createStep === 4" type="primary" :loading="submitting" @click="handleCreate">
            创建
          </el-button>
          <el-button @click="cancelCreate">取消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- Edit Dialog -->
    <el-dialog v-model="showEditDialog" title="编辑知识库" width="500px" class="edit-dialog">
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="100px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="editForm.name" placeholder="请输入知识库名称" maxlength="128" show-word-limit class="form-input" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="editForm.description"
            type="textarea"
            :rows="3"
            placeholder="请输入知识库描述"
            maxlength="1024"
            show-word-limit
            class="form-input"
          />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="editForm.status">
            <el-radio label="ACTIVE">启用</el-radio>
            <el-radio label="PAUSED">暂停</el-radio>
            <el-radio label="ARCHIVED">归档</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEditDialog = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleUpdate">确定</el-button>
      </template>
    </el-dialog>

    <!-- JSON Import Dialog -->
    <el-dialog v-model="showJsonImportDialog" title="导入JSON模板" width="500px" class="json-import-dialog">
      <el-form>
        <el-form-item label="JSON模板">
          <el-input
            v-model="jsonTemplateInput"
            type="textarea"
            :rows="10"
            placeholder="请粘贴JSON数组格式的字段模板，例如：[&#10;  {&quot;field&quot;: &quot;indications&quot;, &quot;label&quot;: &quot;适应症&quot;, &quot;required&quot;: true},&#10;  {&quot;field&quot;: &quot;dosage&quot;, &quot;label&quot;: &quot;用法用量&quot;, &quot;required&quot;: true}&#10;]"
            style="font-family: monospace; font-size: 12px;"
          />
        </el-form-item>
        <el-form-item>
          <div class="form-tip" style="color: var(--el-text-color-secondary); font-size: 12px;">
            <el-icon><InfoFilled /></el-icon>
            支持JSON数组格式，每个元素包含 field（字段名）、label（显示标签）、required（是否必填）
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showJsonImportDialog = false">取消</el-button>
        <el-button type="primary" @click="importJsonTemplate">导入</el-button>
      </template>
    </el-dialog>

    <!-- Config Dialog -->
    <el-dialog v-model="showConfigDialog" title="知识库配置" width="700px" class="config-dialog">
      <el-tabs v-model="configTab" class="config-tabs">
        <el-tab-pane label="检索配置" name="retrieval">
          <el-form :model="configForm.retrieval" label-width="120px">
            <el-form-item label="召回数量 (TopK)">
              <el-input-number v-model="configForm.retrieval.topK" :min="1" :max="100" />
            </el-form-item>
            <el-form-item label="相似度阈值">
              <div class="slider-container">
                <el-slider
                  v-model="configForm.retrieval.similarityThreshold"
                  :min="0"
                  :max="1"
                  :step="0.05"
                />
                <span class="slider-value">{{ (configForm.retrieval.similarityThreshold * 100).toFixed(0) }}%</span>
              </div>
            </el-form-item>
            <el-form-item label="启用重排序">
              <el-switch v-model="configForm.retrieval.rerankEnabled" />
            </el-form-item>
            <el-form-item label="查询扩展">
              <el-switch v-model="configForm.retrieval.queryExpansionEnabled" />
            </el-form-item>
          </el-form>
        </el-tab-pane>
        <el-tab-pane label="生成配置" name="generation">
          <el-form :model="configForm.generation" label-width="120px">
            <el-form-item label="最大Token">
              <el-input-number v-model="configForm.generation.maxTokens" :min="100" :max="8000" :step="100" />
            </el-form-item>
            <el-form-item label="温度参数">
              <div class="slider-container">
                <el-slider v-model="configForm.generation.temperature" :min="0" :max="1" :step="0.1" />
                <span class="slider-value">{{ configForm.generation.temperature.toFixed(1) }}</span>
              </div>
            </el-form-item>
            <el-form-item label="流式输出">
              <el-switch v-model="configForm.generation.streamEnabled" />
            </el-form-item>
            <el-form-item label="系统提示词">
              <el-input
                v-model="configForm.generation.systemPrompt"
                type="textarea"
                :rows="3"
                class="form-input"
              />
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>
      <template #footer>
        <el-button @click="showConfigDialog = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleUpdateConfig">保存配置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { collectionsApi } from '@/api/collections'
import { usersApi } from '@/api/users'
import { formatDate, getStatusType } from '@/utils/format'
import { Plus, Search, Refresh, ArrowDown, Setting, DocumentCopy, FolderDelete, RefreshLeft, Delete, Collection, InfoFilled, Upload } from '@element-plus/icons-vue'
import { useCollectionStore } from '@/stores/collections'
import type { MultiCollection, CollectionType, CollectionStatus, DocType, SectionTemplate } from '@/types'
import type { CollectionType as CType, CollectionStatus as CStatus, ChunkingStrategy } from '@/types'
import type { FormInstance, FormRules } from 'element-plus'
import {
  CollectionTypeLabels,
  CollectionStatusLabels,
  ChunkingStrategyLabels,
  DocTypeLabels
} from '@/types'

const router = useRouter()
const collectionStore = useCollectionStore()

const collections = computed(() => collectionStore.collections)
const loading = computed(() => collectionStore.loading)
const pagination = computed(() => collectionStore.pagination)

const filters = reactive({
  keyword: '',
  type: '' as CollectionType | '',
  status: '' as CollectionStatus | ''
})

const userOptions = ref<Array<{ id: string; username: string; displayName?: string }>>([])
let searchUserTimer: ReturnType<typeof setTimeout> | null = null

const searchUsers = async (keyword: string) => {
  if (searchUserTimer) clearTimeout(searchUserTimer)
  searchUserTimer = setTimeout(async () => {
    if (!keyword.trim()) {
      userOptions.value = []
      return
    }
    try {
      const res = await usersApi.list({ keyword: keyword.trim(), pageSize: 20 })
      if (res.code === 200 && res.data) {
        userOptions.value = res.data.items || []
      }
    } catch {
      userOptions.value = []
    }
  }, 300)
}

const showCreateDialog = ref(false)
const showEditDialog = ref(false)
const showConfigDialog = ref(false)
const showJsonImportDialog = ref(false)
const jsonTemplateInput = ref('')
const submitting = ref(false)
const formRef = ref<FormInstance>()
const editFormRef = ref<FormInstance>()
const createStep = ref(0)
const configTab = ref('retrieval')

const currentEditId = ref('')

const createForm = reactive({
  name: '',
  description: '',
  type: 'KNOWLEDGE_BASE' as CType,
  docType: 'NORMAL' as DocType,
  sectionTemplate: [] as SectionTemplate[],
  permissionType: 'PUBLIC',
  allowedUserIds: [] as string[],
  config: {
    chunking: {
      strategy: 'SEMANTIC' as ChunkingStrategy,
      chunkSize: 500,
      chunkOverlap: 50
    },
    retrieval: {
      topK: 10,
      similarityThreshold: 0.7,
      rerankEnabled: false,
      queryExpansionEnabled: false
    },
    generation: {
      maxTokens: 2000,
      temperature: 0.7,
      streamEnabled: true,
      systemPrompt: ''
    }
  }
})

// 添加字段模板
const addTemplateField = () => {
  createForm.sectionTemplate.push({ field: '', label: '', required: false })
}

// 删除字段模板
const removeTemplateField = (index: number) => {
  createForm.sectionTemplate.splice(index, 1)
}

// 导入JSON模板
const importJsonTemplate = () => {
  if (!jsonTemplateInput.value.trim()) {
    ElMessage.warning('请先粘贴JSON模板内容')
    return
  }
  try {
    const parsed = JSON.parse(jsonTemplateInput.value)
    if (!Array.isArray(parsed)) {
      throw new Error('JSON必须是数组格式')
    }
    // 验证格式
    for (const item of parsed) {
      if (!item.field || !item.label) {
        throw new Error('每个元素必须包含 field 和 label 字段')
      }
    }
    createForm.sectionTemplate = parsed
    showJsonImportDialog.value = false
    jsonTemplateInput.value = ''
    ElMessage.success('成功导入 ' + parsed.length + ' 个字段模板')
  } catch (e: any) {
    ElMessage.error('JSON格式错误：' + e.message)
  }
}

const editForm = reactive({
  name: '',
  description: '',
  status: 'ACTIVE' as CStatus
})

const configForm = reactive({
  retrieval: {
    topK: 10,
    similarityThreshold: 0.7,
    rerankEnabled: false,
    queryExpansionEnabled: false
  },
  generation: {
    maxTokens: 2000,
    temperature: 0.7,
    streamEnabled: true,
    systemPrompt: ''
  }
})

const createRules: FormRules = {
  name: [
    { required: true, message: '请输入知识库名称', trigger: 'blur' },
    { min: 1, max: 128, message: '长度在 1 到 128 个字符', trigger: 'blur' }
  ],
  type: [
    { required: true, message: '请选择知识库类型', trigger: 'change' }
  ]
}

const editRules: FormRules = {
  name: [
    { required: true, message: '请输入知识库名称', trigger: 'blur' },
    { min: 1, max: 128, message: '长度在 1 到 128 个字符', trigger: 'blur' }
  ]
}

const loadCollections = async () => {
  await collectionStore.loadCollections({
    page: pagination.value.page,
    pageSize: pagination.value.pageSize,
    keyword: filters.keyword || undefined,
    type: filters.type || undefined,
    status: filters.status || undefined
  })
}

const handleSearch = () => {
  pagination.value.page = 0
  collectionStore.setFilters({
    keyword: filters.keyword || undefined,
    type: filters.type || undefined,
    status: filters.status || undefined
  })
  loadCollections()
}

const handlePageChange = () => {
  loadCollections()
}

const nextStep = async () => {
  if (createStep.value === 0) {
    if (!formRef.value) return
    await formRef.value.validateField('name')
  }
  createStep.value++
}

const cancelCreate = () => {
  showCreateDialog.value = false
  createStep.value = 0
  resetCreateForm()
}

const resetCreateForm = () => {
  createForm.name = ''
  createForm.description = ''
  createForm.type = 'KNOWLEDGE_BASE'
  createForm.docType = 'NORMAL'
  createForm.sectionTemplate = []
  createForm.permissionType = 'PUBLIC'
  createForm.allowedUserIds = []
  createForm.config = {
    chunking: {
      strategy: 'SEMANTIC',
      chunkSize: 500,
      chunkOverlap: 50
    },
    retrieval: {
      topK: 10,
      similarityThreshold: 0.7,
      rerankEnabled: false,
      queryExpansionEnabled: false
    },
    generation: {
      maxTokens: 2000,
      temperature: 0.7,
      streamEnabled: true,
      systemPrompt: ''
    }
  }
}

const handleCreate = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    // 校验：MANUAL类型必须配置sectionTemplate
    if (createForm.docType === 'MANUAL' && createForm.sectionTemplate.length === 0) {
      ElMessage.error('说明书类知识库必须配置至少一个字段模板')
      return
    }

    submitting.value = true
    try {
      const payload: any = {
        name: createForm.name,
        description: createForm.description,
        type: createForm.type,
        docType: createForm.docType,
        sectionTemplate: createForm.docType === 'MANUAL' ? createForm.sectionTemplate : undefined,
        permissionType: createForm.permissionType,
        config: {
          retrieval: createForm.config.retrieval,
          generation: createForm.config.generation,
          chunking: createForm.docType === 'MANUAL'
            ? { strategy: 'NONE' as ChunkingStrategy }
            : createForm.config.chunking
        }
      }

      if (createForm.permissionType === 'RESTRICTED' || createForm.permissionType === 'DEPARTMENT_WIDE_RESTRICTED') {
        payload.allowedUserIds = createForm.allowedUserIds
      }

      const res = await collectionsApi.create(payload)

      if (res.code === 200) {
        ElMessage.success('创建成功')
        showCreateDialog.value = false
        createStep.value = 0
        resetCreateForm()
        loadCollections()
      }
    } finally {
      submitting.value = false
    }
  })
}

const handleEdit = (row: MultiCollection) => {
  currentEditId.value = row.id
  editForm.name = row.name
  editForm.description = row.description || ''
  editForm.status = row.status || 'ACTIVE'
  showEditDialog.value = true
}

const handleUpdate = async () => {
  if (!editFormRef.value) return

  await editFormRef.value.validate(async (valid) => {
    if (!valid) return

    submitting.value = true
    try {
      const res = await collectionsApi.update(currentEditId.value, editForm)
      if (res.code === 200) {
        ElMessage.success('更新成功')
        showEditDialog.value = false
        loadCollections()
      }
    } finally {
      submitting.value = false
    }
  })
}

const handleCommand = async (command: string, row: MultiCollection) => {
  switch (command) {
    case 'config':
      await openConfigDialog(row)
      break
    case 'duplicate':
      await handleDuplicate(row)
      break
    case 'archive':
      await handleArchive(row)
      break
    case 'restore':
      await handleRestore(row)
      break
    case 'delete':
      await handleDelete(row)
      break
  }
}

const openConfigDialog = async (row: MultiCollection) => {
  currentEditId.value = row.id
  try {
    const res = await collectionsApi.getConfig(row.id)
    if (res.code === 200 && res.data) {
      configForm.retrieval = {
        topK: res.data.retrieval?.topK ?? 10,
        similarityThreshold: res.data.retrieval?.similarityThreshold ?? 0.7,
        rerankEnabled: res.data.retrieval?.rerankEnabled ?? false,
        queryExpansionEnabled: res.data.retrieval?.queryExpansionEnabled ?? false
      }
      configForm.generation = {
        maxTokens: res.data.generation?.maxTokens ?? 2000,
        temperature: res.data.generation?.temperature ?? 0.7,
        streamEnabled: res.data.generation?.streamEnabled ?? true,
        systemPrompt: res.data.generation?.systemPrompt ?? ''
      }
      showConfigDialog.value = true
    }
  } catch (error) {
    console.error('Failed to load config:', error)
  }
}

const handleUpdateConfig = async () => {
  submitting.value = true
  try {
    const res = await collectionsApi.updateConfig(currentEditId.value, {
      retrieval: configForm.retrieval,
      generation: configForm.generation
    })
    if (res.code === 200) {
      ElMessage.success('配置更新成功')
      showConfigDialog.value = false
      loadCollections()
    }
  } finally {
    submitting.value = false
  }
}

const handleDuplicate = async (row: MultiCollection) => {
  try {
    const { value: newName } = await ElMessageBox.prompt('请输入新知识库名称', '复制知识库', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputValue: `${row.name} (副本)`
    })

    const res = await collectionsApi.duplicate(row.id, newName || `${row.name} (副本)`)
    if (res.code === 200) {
      ElMessage.success('复制成功')
      loadCollections()
    }
  } catch {
    // 用户取消
  }
}

const handleArchive = async (row: MultiCollection) => {
  try {
    await ElMessageBox.confirm(`确定要归档知识库"${row.name}"吗？归档后仍可恢复。`, '提示', {
      type: 'warning'
    })
    const res = await collectionsApi.archive(row.id)
    if (res.code === 200) {
      ElMessage.success('归档成功')
      loadCollections()
    }
  } catch {
    // 用户取消
  }
}

const handleRestore = async (row: MultiCollection) => {
  try {
    const res = await collectionsApi.restore(row.id)
    if (res.code === 200) {
      ElMessage.success('恢复成功')
      loadCollections()
    }
  } catch {
    // 用户取消
  }
}

const handleDelete = async (row: MultiCollection) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除知识库"${row.name}"吗？删除后将无法恢复，所有关联文档将解除关联。`,
      '危险操作',
      { type: 'warning' }
    )
    const res = await collectionsApi.delete(row.id)
    if (res.code === 200) {
      ElMessage.success('删除成功')
      loadCollections()
    }
  } catch {
    // 用户取消
  }
}

const viewDetail = (row: MultiCollection) => {
  router.push(`/collections/${row.id}`)
}

const formatNumber = (num: number | undefined): string => {
  if (!num) return '0'
  if (num >= 10000) {
    return (num / 10000).toFixed(1) + 'w'
  }
  return num.toLocaleString()
}

onMounted(() => {
  loadCollections()
})
</script>

<style scoped>
.collections {
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
  --el-button-bg-color: var(--accent);
  --el-button-border-color: var(--accent);
  --el-button-hover-bg-color: var(--accent-dark);
  --el-button-hover-border-color: var(--accent-dark);
  border-radius: var(--radius);
  padding: 12px 24px;
  font-weight: 500;
  color: #ffffff;
  box-shadow: 0 2px 8px rgba(99, 102, 241, 0.3);
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
  --el-input-border-radius: var(--radius);
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
  --el-select-input-border-radius: var(--radius);
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
  --el-button-hover-bg-color: var(--muted);
  border-radius: var(--radius);
  margin-left: auto;
}

/* Table Styles */
.modern-table :deep(.el-table__header th) {
  padding: 16px 12px;
  font-size: 13px;
  font-weight: 600;
  color: var(--foreground);
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

.name-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.collection-icon {
  width: 40px;
  height: 40px;
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.1) 0%, rgba(99, 102, 241, 0.05) 100%);
  border-radius: var(--radius);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--accent);
}

.name-content {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.name-content .name {
  font-weight: 500;
  color: var(--foreground);
}

.type-tag {
  width: fit-content;
  border-radius: 4px;
}

.count-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 32px;
  height: 24px;
  padding: 0 8px;
  background: linear-gradient(135deg, var(--accent) 0%, var(--accent-dark) 100%);
  color: #fff;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
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

.more-dropdown {
  margin-left: 4px;
}

.action-menu :deep(.el-dropdown-menu__item) {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px;
  border-radius: 8px;
  margin: 4px 8px;
}

.delete-item {
  color: var(--danger) !important;
}

.delete-item:hover {
  background: var(--danger-light) !important;
}

.pagination-wrapper {
  padding: 16px 20px;
  display: flex;
  justify-content: flex-end;
}

/* Dialog Styles */
.create-dialog :deep(.el-dialog) {
  border-radius: 16px;
  overflow: hidden;
}

.create-dialog :deep(.el-dialog__header) {
  padding: 20px 24px;
  background: var(--muted);
  border-bottom: 1px solid var(--border);
}

.create-dialog :deep(.el-dialog__body) {
  padding: 24px;
}

.create-steps {
  margin-bottom: 32px;
}

.create-steps :deep(.el-step__title) {
  font-size: 13px;
}

.create-form :deep(.el-form-item__label) {
  font-weight: 500;
}

.form-input :deep(.el-input__wrapper),
.form-input :deep(.el-textarea__inner) {
  border-radius: var(--radius);
  padding: 0 16px;
  box-shadow: var(--shadow-sm);
  border: 1px solid var(--border);
  transition: all var(--transition);
}

.form-input :deep(.el-input__wrapper:hover),
.form-input :deep(.el-textarea__inner:hover) {
  border-color: var(--accent-light);
}

.form-input :deep(.el-input__wrapper:focus),
.form-input :deep(.el-textarea__inner:focus) {
  border-color: var(--accent);
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.1);
}

.slider-container {
  display: flex;
  align-items: center;
  gap: 16px;
  width: 100%;
}

.slider-container :deep(.el-slider) {
  flex: 1;
  min-width: 200px;
}

.create-form :deep(.el-form-item__content) {
  display: flex;
  align-items: center;
}

.slider-value {
  min-width: 70px;
  color: var(--accent);
  font-weight: 500;
  font-size: 14px;
}

.info-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.08) 0%, rgba(99, 102, 241, 0.04) 100%);
  border: 1px solid rgba(99, 102, 241, 0.2);
  border-radius: var(--radius);
  color: var(--accent);
  font-size: 13px;
}

.info-tip .el-icon {
  font-size: 16px;
}

.dialog-footer {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
}

.edit-dialog :deep(.el-dialog),
.config-dialog :deep(.el-dialog) {
  border-radius: 16px;
}

.config-tabs :deep(.el-tabs__item) {
  font-weight: 500;
}

.config-tabs :deep(.el-tabs__item.is-active) {
  color: var(--accent);
}

.config-tabs :deep(.el-tabs__active-bar) {
  background: var(--accent);
}
</style>