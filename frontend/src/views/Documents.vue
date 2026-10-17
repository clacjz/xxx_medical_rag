<template>
  <div class="documents">
    <!-- Page Header -->
    <div class="page-header">
      <div class="header-content">
        <h1 class="page-title">文档管理</h1>
        <p class="page-subtitle">管理和查看所有上传的文档</p>
      </div>
      <el-button type="primary" class="upload-btn" @click="showUploadDialog = true">
        <el-icon><Upload /></el-icon> 上传文档
      </el-button>
    </div>

    <!-- Filter Bar -->
    <el-card class="filter-card" shadow="hover">
      <div class="toolbar">
        <el-select v-model="filterCollection" placeholder="选择知识库" clearable style="width: 200px" class="filter-select">
          <el-option v-for="col in collections" :key="col.id" :label="col.name" :value="col.id" />
        </el-select>
        <el-select v-model="filterStatus" placeholder="选择状态" clearable style="width: 150px" class="filter-select">
          <el-option label="已完成" value="COMPLETED" />
          <el-option label="处理中" value="PROCESSING" />
          <el-option label="待处理" value="PENDING" />
          <el-option label="失败" value="FAILED" />
        </el-select>
        <el-input
          v-model="keyword"
          placeholder="搜索文档名称..."
          style="width: 220px"
          clearable
          @clear="loadDocuments"
          @keyup.enter="loadDocuments"
          class="search-input"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-button @click="loadDocuments" class="refresh-btn">
          <el-icon><Refresh /></el-icon> 刷新
        </el-button>
      </div>
    </el-card>

    <!-- Data Table -->
    <el-card class="table-card" shadow="hover">
      <!-- Batch Actions Bar -->
      <div v-if="selectedDocuments.length > 0" class="batch-actions-bar">
        <el-checkbox
          :indeterminate="isIndeterminate"
          v-model="isAllSelected"
          @change="(val: any) => handleSelectAll(val as boolean)"
        />
        <span class="selected-count">已选择 {{ selectedDocuments.length }} 项</span>
        <el-button type="primary" size="small" @click="batchProcess">批量解析</el-button>
        <el-button type="danger" size="small" @click="batchDelete">批量删除</el-button>
        <el-button size="small" @click="clearSelection">清除选择</el-button>
      </div>

      <el-table
        :data="documents"
        v-loading="loading"
        style="width: 100%"
        class="modern-table"
        :header-cell-style="{ background: 'var(--muted)', color: 'var(--foreground)', fontWeight: '600' }"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="50" />
        <el-table-column prop="name" label="文档名称" min-width="220" show-overflow-tooltip />
        <el-table-column prop="type" label="类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" class="type-tag">{{ row.type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="size" label="大小" width="100" align="center">
          <template #default="{ row }">
            <span class="size-text">{{ formatFileSize(row.size) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="140" align="center">
          <template #default="{ row }">
            <div class="status-cell">
              <el-tag :type="getStatusType(row.status)" size="small" class="status-tag">
                {{ getStatusName(row.status) }}
              </el-tag>
              <el-progress
                v-if="row.status === 'PROCESSING' || row.status.includes('ING')"
                :percentage="row.progress"
                :width="20"
                :stroke-width="2"
                type="circle"
                style="margin-left: 8px"
              />
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="collectionName" label="所属知识库" width="150" show-overflow-tooltip />
        <el-table-column prop="chunkCount" label="分块数" width="80" align="center">
          <template #default="{ row }">
            <span class="chunk-count">{{ row.chunkCount || 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="上传时间" width="170">
          <template #default="{ row }">
            <span class="date-text">{{ formatDate(row.createdAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <div class="action-buttons">
              <el-button
                v-if="row.status === 'FAILED'"
                type="warning"
                text
                size="small"
                class="action-btn"
                @click="handleRetry(row)"
              >
                重试
              </el-button>
                            <el-button
                v-if="row.status === 'PENDING' || row.status === 'COMPLETED'"
                type="success"
                text
                size="small"
                class="action-btn"
                @click="openProcessFromAction(row)"
              >
                解析入库
              </el-button>
              <el-button type="primary" text size="small" class="action-btn" @click="showDocumentDetail(row)">
                详情
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
          @size-change="loadDocuments"
          @current-change="loadDocuments"
        />
      </div>
    </el-card>

    <!-- Upload Dialog -->
    <el-dialog v-model="showUploadDialog" title="上传文档" width="600px" class="upload-dialog">
      <el-form :model="uploadForm" label-width="100px" class="upload-form">
        <el-form-item label="选择知识库" required>
          <el-select v-model="uploadForm.collectionId" placeholder="请选择知识库" style="width: 100%" class="form-select">
            <el-option v-for="col in collections" :key="col.id" :label="col.name" :value="col.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="上传文件">
          <el-upload
            ref="uploadRef"
            :auto-upload="false"
            :limit="10"
            multiple
            :on-change="handleFileChange"
            :on-remove="handleFileRemove"
            :file-list="fileList"
            accept=".pdf,.docx,.doc,.md,.txt,.xlsx,.xls,.ppt,.pptx"
            class="upload-area"
          >
            <div class="upload-trigger">
              <el-icon class="upload-icon"><UploadFilled /></el-icon>
              <div class="upload-text">
                <span class="text-main">将文件拖到此处，或<em>点击上传</em></span>
                <span class="text-sub">支持 PDF、Word、Markdown、TXT 格式，单个文件不超过100MB</span>
              </div>
            </div>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="showUploadDialog = false">取消</el-button>
          <el-button type="primary" :loading="uploading" @click="handleUpload">开始上传</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- Progress Dialog -->
    <el-dialog v-model="showProgressDialog" title="上传进度" width="500px" :close-on-click-modal="false" class="progress-dialog">
      <div class="upload-progress">
        <div v-for="(item, index) in uploadProgress" :key="index" class="progress-item">
          <div class="progress-info">
            <span class="file-name">{{ item.name }}</span>
            <span class="progress-percent">{{ item.percent }}%</span>
          </div>
          <el-progress :percentage="item.percent" :status="item.status" :stroke-width="8" />
          <p v-if="item.error" class="error-msg">{{ item.error }}</p>
        </div>
      </div>
      <template #footer>
        <el-button @click="closeProgressDialog" :disabled="uploading">{{ isUploadComplete ? '完成' : '关闭' }}</el-button>
      </template>
    </el-dialog>

    <!-- Detail Dialog -->
    <el-dialog v-model="showDetailDialog" title="文档详情" width="1000px" :close-on-click-modal="false" class="detail-dialog">
      <el-tabs v-model="detailActiveTab" class="detail-tabs">
        <el-tab-pane label="基本信息" name="info">
          <el-descriptions :column="2" border v-if="currentDocument" class="doc-descriptions">
            <el-descriptions-item label="文档名称">
              <span class="doc-name-truncate" :title="currentDocument.name">{{ truncateName(currentDocument.name) }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="文档类型">
              <el-tag size="small">{{ currentDocument.type }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="文件大小">{{ formatFileSize(currentDocument.size) }}</el-descriptions-item>
            <el-descriptions-item label="所属知识库">{{ currentDocument.collectionName }}</el-descriptions-item>
            <el-descriptions-item label="分块数量">{{ currentDocument.chunkCount }}</el-descriptions-item>
            <el-descriptions-item label="处理状态">
              <el-tag :type="getStatusType(currentDocument.status)" size="small">
                {{ getStatusName(currentDocument.status) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="上传时间">{{ formatDate(currentDocument.createdAt) }}</el-descriptions-item>
            <el-descriptions-item label="更新时间">{{ formatDate(currentDocument.updatedAt) }}</el-descriptions-item>
            <el-descriptions-item label="错误信息" :span="2" v-if="currentDocument.errorMessage">
              <span class="error-text">{{ currentDocument.errorMessage }}</span>
            </el-descriptions-item>
          </el-descriptions>
          <div class="detail-actions" style="margin-top: 20px;">
            <el-button
              v-if="currentDocument && (currentDocument.status === 'PENDING' || currentDocument.status === 'COMPLETED')"
              type="primary"
              @click="openProcessDialog"
            >
              处理文档
            </el-button>
            <el-button
              v-if="currentDocument && currentDocument.status === 'FAILED'"
              type="warning"
              @click="handleRetry(currentDocument!)"
            >
              重试处理
            </el-button>
          </div>
        </el-tab-pane>

        <el-tab-pane label="处理状态" name="status">
          <div v-if="processStatus" class="process-status">
            <el-descriptions :column="1" border>
              <el-descriptions-item label="当前步骤">
                <el-tag :type="getProcessStatusType(processStatus.currentStep)" size="small">
                  {{ getProcessStepName(processStatus.currentStep) }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="解析状态">
                <el-tag :type="getStatusType(processStatus.parseStatus)" size="small">
                  {{ getStatusName(processStatus.parseStatus) }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="分块状态">
                <el-tag :type="getStatusType(processStatus.chunkStatus)" size="small">
                  {{ getStatusName(processStatus.chunkStatus) }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="处理进度">
                <el-progress :percentage="processStatus.progress" :stroke-width="20" />
              </el-descriptions-item>
              <el-descriptions-item label="开始时间" v-if="processStatus.startTime">
                {{ formatDate(processStatus.startTime) }}
              </el-descriptions-item>
            </el-descriptions>
            <el-button type="primary" @click="loadProcessStatus" style="margin-top: 16px;">
              <el-icon><Refresh /></el-icon> 刷新状态
            </el-button>
          </div>
          <el-empty v-else description="暂无处理状态信息" />
        </el-tab-pane>

        <el-tab-pane label="分块列表" name="chunks">
          <div v-if="chunks.length > 0">
            <div class="chunk-toolbar">
              <el-input
                v-model="chunkKeyword"
                placeholder="搜索分块内容..."
                style="width: 300px"
                clearable
                @clear="loadChunks"
                class="search-input"
              >
                <template #prefix>
                  <el-icon><Search /></el-icon>
                </template>
              </el-input>
            </div>
            <el-table :data="filteredChunks" style="width: 100%; margin-top: 16px" height="400">
              <el-table-column prop="index" label="序号" width="80" align="center" />
              <el-table-column prop="startPos" label="起始位置" width="100" align="center" />
              <el-table-column prop="endPos" label="结束位置" width="100" align="center" />
              <el-table-column prop="sourcePages" label="来源页码" width="100" align="center">
                <template #default="{ row }">
                  {{ row.sourcePages?.join(', ') || '-' }}
                </template>
              </el-table-column>
              <el-table-column prop="text" label="分块内容" min-width="300" show-overflow-tooltip />
              <el-table-column label="操作" width="100" fixed="right">
                <template #default="{ row }">
                  <el-button type="primary" text size="small" @click="viewChunkDetail(row)">
                    详情
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-pagination
              v-model:current-page="chunkPagination.page"
              v-model:page-size="chunkPagination.pageSize"
              :total="filteredChunks.length"
              :page-sizes="[10, 20, 50]"
              layout="total, prev, pager, next"
              style="margin-top: 16px; justify-content: flex-end"
            />
          </div>
          <el-empty v-else description="暂无分块数据，请先处理文档" />
        </el-tab-pane>

        <el-tab-pane label="文件预览" name="content">
          <div v-if="currentDocument" class="file-preview">
            <!-- PDF 预览 -->
            <div v-if="isPdfFile" class="preview-container">
              <div class="preview-toolbar">
                <el-alert
                  title="PDF 文件预览（离线）"
                  type="info"
                  :closable="false"
                  show-icon
                />
              </div>
              <iframe
                :src="previewUrl"
                class="preview-iframe"
                frameborder="0"
              />
            </div>

            <!-- 图片预览 -->
            <div v-else-if="isImageFile" class="preview-container image-preview">
              <div class="preview-toolbar">
                <el-alert
                  title="图片预览（离线）"
                  type="info"
                  :closable="false"
                  show-icon
                />
              </div>
              <img :src="previewUrl" :alt="currentDocument.name" class="preview-image" />
            </div>

            <!-- 文本文件预览 -->
            <div v-else-if="isTextFile" class="preview-container text-preview">
              <div v-loading="textLoading" class="text-content-wrapper">
                <pre v-if="textContent" class="text-content">{{ textContent }}</pre>
                <el-empty v-else description="加载中..." />
              </div>
            </div>

            <!-- Word 文档预览 -->
            <div v-else-if="isWordFile" class="preview-container">
              <div class="preview-toolbar">
                <el-alert
                  title="Word 文档预览（离线）"
                  type="info"
                  :closable="false"
                  show-icon
                />
              </div>
              <div v-loading="docxLoading" class="docx-content">
                <div v-if="docxHtmlContent" v-html="docxHtmlContent" class="docx-rendered"></div>
                <el-empty v-else-if="!docxLoading" description="暂无可用预览内容" />
              </div>
            </div>

            <!-- Excel 文档预览 -->
            <div v-else-if="isExcelFile" class="preview-container">
              <div class="preview-toolbar">
                <el-alert
                  title="Excel 文档预览（离线）"
                  type="info"
                  :closable="false"
                  show-icon
                />
              </div>
              <div v-loading="excelLoading" class="excel-content">
                <div v-if="excelData" class="excel-table-wrapper">
                  <table class="excel-table">
                    <thead>
                      <tr>
                        <th v-for="(header, hIdx) in excelData.headers" :key="hIdx">{{ header }}</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr v-for="(row, rIdx) in excelData.rows" :key="rIdx">
                        <td v-for="(cell, cIdx) in row" :key="cIdx">{{ cell }}</td>
                      </tr>
                    </tbody>
                  </table>
                </div>
                <el-empty v-else-if="!excelLoading" description="暂无可用预览内容" />
              </div>
            </div>

            <!-- PPT 文档预览 -->
            <div v-else-if="isPptFile" class="preview-container">
              <div class="preview-toolbar">
                <el-alert
                  title="PPT 文档暂不支持在线预览"
                  type="warning"
                  :closable="false"
                  show-icon
                />
              </div>
              <div class="unsupported-info">
                <el-icon size="64" color="var(--el-text-color-secondary)"><DocumentIcon /></el-icon>
                <p class="unsupported-text">PowerPoint 文档暂不支持在线预览</p>
                <p class="unsupported-hint">请下载后查看</p>
                <el-button type="primary" @click="downloadFile">
                  <el-icon><Download /></el-icon> 下载文件
                </el-button>
              </div>
            </div>

            <!-- 不支持的格式 -->
            <div v-else class="preview-container unsupported-preview">
              <div class="unsupported-info">
                <el-icon size="64" color="var(--el-text-color-secondary)"><DocumentIcon /></el-icon>
                <p class="unsupported-text">该文件格式暂不支持预览</p>
                <p class="unsupported-hint">文件类型: {{ currentDocument.type }}</p>
                <el-button type="primary" @click="downloadFile">
                  <el-icon><Download /></el-icon> 下载文件
                </el-button>
              </div>
            </div>
          </div>
          <el-empty v-else description="请先选择文档" />
        </el-tab-pane>

        <el-tab-pane label="OCR预览" name="ocr">
          <div v-if="currentDocument?.type?.toLowerCase() === 'pdf'" class="ocr-preview">


            <div v-loading="ocrLoading" class="ocr-content">
              <div v-if="ocrError" class="ocr-error">
                <el-alert :title="ocrError" type="error" :closable="false" show-icon />
              </div>
              <div v-else-if="ocrResult" class="ocr-result">
                <div class="ocr-header">
                  <el-tag type="info">字符数：{{ ocrResult.ocrText?.length || 0 }}</el-tag>
                </div>
                <el-input
                  type="textarea"
                  :model-value="ocrResult.ocrText || ''"
                  :rows="20"
                  readonly
                  style="margin-top: 12px; font-family: monospace;"
                />
              </div>
              <el-empty v-else description="加载中..." />
            </div>
          </div>
          <el-empty v-else description="OCR预览仅支持PDF格式文档" />
        </el-tab-pane>
      </el-tabs>
      <template #footer>
        <el-button @click="showDetailDialog = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- Process Dialog -->
    <el-dialog v-model="showProcessDialog" title="文档处理" width="600px" :close-on-click-modal="false" class="process-dialog">
      <el-form :model="processForm" label-width="100px">
        <el-form-item label="文档名称">
          <span class="doc-name-text" :title="currentDocument?.name">{{ truncateName(currentDocument?.name) }}</span>
        </el-form-item>
        <el-form-item v-if="currentCollectionDocType !== 'MANUAL'" label="分块策略" required>
          <el-select v-model="processForm.strategy" style="width: 100%" class="form-select">
            <el-option label="固定长度分块" value="fixed" />
            <el-option label="段落分块" value="paragraph" />
            <el-option label="语义分块" value="semantic" />
            <el-option label="字符分块" value="character" />
            <el-option label="不分块" value="none" />
          </el-select>
        </el-form-item>
        <el-form-item v-else label="处理方式">
          <el-alert type="info" :closable="false" show-icon>
            <template #title>
              说明书类文档将强制使用 <strong>OCR识别</strong>，不进行分块
            </template>
          </el-alert>
        </el-form-item>
        <el-form-item label="目标知识库">
          <span class="doc-name-text">{{ currentDocument?.collectionName || '未选择' }}</span>
        </el-form-item>
        <el-form-item v-if="currentCollectionDocType" label="处理策略">
          <el-tag :type="currentCollectionDocType === 'MANUAL' ? 'warning' : 'success'" size="small">
            {{ DocTypeLabels[currentCollectionDocType] }}
          </el-tag>
        </el-form-item>
      </el-form>

      <el-divider v-if="isProcessing" />

      <div v-if="isProcessing" class="process-progress">
        <el-alert
          v-if="processStatus?.errorMessage"
          type="error"
          :closable="false"
          show-icon
          style="margin-bottom: 16px;"
        >
          {{ processStatus.errorMessage }}
        </el-alert>
        <div class="process-step">
          <span class="step-label">当前步骤：</span>
          <el-tag :type="getProcessStatusType(processStatus?.currentStep || '')" size="small">
            {{ getProcessStepName(processStatus?.currentStep || '') }}
          </el-tag>
        </div>
        <div class="process-progress-bar">
          <el-progress :percentage="processStatus?.progress || 0" :stroke-width="20" />
        </div>
        <p class="process-tip">处理中，请稍候...</p>
      </div>

      <template #footer>
        <el-button @click="closeProcessDialog" :disabled="isProcessing">取消</el-button>
        <el-button
          v-if="!isProcessing && !isProcessCompleted"
          type="primary"
          @click="startProcess"
        >
          开始处理
        </el-button>
        <el-button
          v-if="isProcessing"
          type="danger"
          @click="cancelProcess"
        >
          取消
        </el-button>
      </template>
    </el-dialog>

    <!-- Chunk Detail Dialog -->
    <el-dialog v-model="showChunkDetailDialog" title="分块详情" width="800px" class="chunk-dialog">
      <div v-if="currentChunk" class="chunk-detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="分块序号">{{ currentChunk.index }}</el-descriptions-item>
          <el-descriptions-item label="来源页码">
            {{ currentChunk.sourcePages?.join(', ') || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="起始位置">{{ currentChunk.startPos }}</el-descriptions-item>
          <el-descriptions-item label="结束位置">{{ currentChunk.endPos }}</el-descriptions-item>
        </el-descriptions>
        <div class="chunk-content" style="margin-top: 16px;">
          <h4>分块内容：</h4>
          <el-input
            type="textarea"
            v-model="currentChunk.text"
            :rows="15"
            readonly
            style="font-family: monospace;"
          />
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { documentsApi, collectionsApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { formatDate, formatFileSize, getStatusType, getStatusName } from '@/utils/format'
import { Upload, Search, Refresh, UploadFilled, Document as DocumentIcon, Download } from '@element-plus/icons-vue'
import type { Document, Collection, DocType } from '@/types'
import type { ProcessStatusDTO, ChunkDTO, DocumentTextDTO } from '@/types'
import { ProcessingStepLabels, DocTypeLabels } from '@/types'

const userStore = useUserStore()
const route = useRoute()

const documents = ref<Document[]>([])
const collections = ref<Collection[]>([])
const loading = ref(false)
const filterCollection = ref('')
const filterStatus = ref('')
const keyword = ref('')

const pagination = reactive({
  page: 0,
  pageSize: 10,
  total: 0
})

const showUploadDialog = ref(false)
const showProgressDialog = ref(false)
const uploading = ref(false)
const uploadRef = ref()
const fileList = ref<any[]>([])
const selectedFiles = ref<File[]>([])

const uploadForm = reactive({
  collectionId: ''
})

const uploadProgress = ref<Array<{
  name: string
  percent: number
  status: 'success' | 'exception' | 'warning' | ''
  error?: string
}>>([])

// 上传是否全部完成（每个文件都达到100%）
const isUploadComplete = computed(() => {
  return uploadProgress.value.length > 0 && uploadProgress.value.every(item => item.percent === 100)
})

// 关闭进度对话框，同时关闭上传对话框
const closeProgressDialog = () => {
  showProgressDialog.value = false
  showUploadDialog.value = false
  selectedFiles.value = []
  fileList.value = []
  uploadForm.collectionId = ''
}

const selectedDocuments = ref<Document[]>([])
const isAllSelected = ref(false)
const isIndeterminate = ref(false)

const showDetailDialog = ref(false)
const showProcessDialog = ref(false)
const showChunkDetailDialog = ref(false)
const detailActiveTab = ref('info')
const currentDocument = ref<Document | null>(null)

// 切换标签页时加载数据
watch(detailActiveTab, (tab) => {
  if (tab === 'ocr' && currentDocument.value) {
    ocrPage.value = 1
    ocrTotalPages.value = null
    ocrResult.value = null
    ocrText.value = ''
    loadOcrResult(1)
  }
  if (tab === 'content' && currentDocument.value) {
    if (isTextFile.value) {
      loadTextContent()
    } else if (isWordFile.value) {
      loadDocxContent()
    } else if (isExcelFile.value) {
      loadExcelContent()
    }
  }
})
const processStatus = ref<ProcessStatusDTO | null>(null)
const chunks = ref<ChunkDTO[]>([])
const documentText = ref<DocumentTextDTO | null>(null)
const currentChunk = ref<ChunkDTO | null>(null)
const chunkKeyword = ref('')
const isProcessing = ref(false)
const isProcessCompleted = ref(false)
let processPollingTimer: number | null = null
const PROCESS_TIMEOUT_MS = 60000 // 60秒超时

const processForm = reactive({
  strategy: 'fixed' as 'fixed' | 'paragraph' | 'semantic' | 'character' | 'none'
})

// 当前文档所属知识库的文档处理策略
const currentCollectionDocType = ref<DocType | null>(null)

// 截断文档名称
const truncateName = (name: string | undefined, maxLen: number = 30): string => {
  if (!name) return ''
  if (name.length <= maxLen) return name
  return name.substring(0, maxLen) + '...'
}

const chunkPagination = reactive({
  page: 1,
  pageSize: 10
})

// OCR 预览相关
const ocrResult = ref<any>(null)
const ocrText = ref('')
const ocrLoading = ref(false)
const ocrError = ref('')
const ocrPage = ref(1)
const ocrTotalPages = ref<number | null>(null)

// 文件预览相关
const textLoading = ref(false)
const textContent = ref('')
const docxLoading = ref(false)
const docxHtmlContent = ref('')
const excelLoading = ref(false)
const excelData = ref<{ headers: string[]; rows: string[][] } | null>(null)
const initialPdfPage = ref(1)  // 初始页码（从路由参数传入）

// 计算属性：判断文件类型
const isPdfFile = computed(() => currentDocument.value?.type?.toUpperCase() === 'PDF')
const isImageFile = computed(() => {
  const type = currentDocument.value?.type?.toUpperCase()
  return ['JPG', 'JPEG', 'PNG', 'GIF', 'BMP', 'WEBP', 'SVG'].includes(type || '')
})
const isTextFile = computed(() => {
  const type = currentDocument.value?.type?.toUpperCase()
  return ['TXT', 'MD', 'MARKDOWN', 'HTML', 'XML', 'JSON', 'JS', 'TS', 'PY', 'JAVA', 'C', 'CPP'].includes(type || '')
})
const isWordFile = computed(() => {
  const type = currentDocument.value?.type?.toUpperCase()
  return ['DOC', 'DOCX'].includes(type || '')
})
const isExcelFile = computed(() => {
  const type = currentDocument.value?.type?.toUpperCase()
  return ['XLS', 'XLSX'].includes(type || '')
})
const isPptFile = computed(() => {
  const type = currentDocument.value?.type?.toUpperCase()
  return ['PPT', 'PPTX'].includes(type || '')
})

// 预览URL
const previewUrl = computed(() => {
  if (!currentDocument.value) return ''
  return `/api/v1/documents/${currentDocument.value.id}/file?token=${userStore.token}`
})

const loadOcrResult = async (page: number = 1) => {
  if (!currentDocument.value) return
  ocrLoading.value = true
  ocrError.value = ''

  try {
    // 先获取PDF总页数
    if (!ocrTotalPages.value) {
      const pdfRes = await documentsApi.getPdfInfo(currentDocument.value.id)
      if (pdfRes.code === 200 && pdfRes.data) {
        ocrTotalPages.value = pdfRes.data.totalPages
      }
    }

    // 获取OCR结果
    const res = await documentsApi.getOcrResult(currentDocument.value.id, page)
    if (res.code === 200 && res.data) {
      ocrResult.value = res.data
      ocrText.value = res.data.ocrText || ''
      ocrPage.value = page
      if (res.data.totalPages) {
        ocrTotalPages.value = res.data.totalPages
      }
    } else {
      ocrError.value = res.message || '加载OCR结果失败'
      ocrText.value = ''
    }
  } catch (error: any) {
    ocrError.value = error.message || '加载OCR结果失败'
    ocrResult.value = null
    ocrText.value = ''
  } finally {
    ocrLoading.value = false
  }
}

// 加载文本文件内容
const loadTextContent = async () => {
  if (!currentDocument.value) return
  textLoading.value = true
  try {
    const res = await documentsApi.getDocumentText(currentDocument.value.id, false, 500000)
    if (res.code === 200 && res.data?.text) {
      textContent.value = res.data.text
    } else {
      textContent.value = ''
    }
  } catch (error) {
    console.error('Failed to load text content:', error)
    textContent.value = ''
  } finally {
    textLoading.value = false
  }
}

// 加载Word文档内容（通过后端转换或直接获取文本）
const loadDocxContent = async () => {
  if (!currentDocument.value) return
  docxLoading.value = true
  docxHtmlContent.value = ''
  try {
    // 先尝试获取解析后的文本内容
    const res = await documentsApi.getDocumentText(currentDocument.value.id, false, 500000)
    if (res.code === 200 && res.data?.text) {
      // 将文本内容转换为简单HTML格式显示
      const escapedText = res.data.text
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
      docxHtmlContent.value = `<div style="white-space: pre-wrap; font-family: inherit; line-height: 1.6;">${escapedText}</div>`
    } else {
      docxHtmlContent.value = '<p style="text-align: center; color: #909399;">暂无可用预览内容</p>'
    }
  } catch (error) {
    console.error('Failed to load docx content:', error)
    docxHtmlContent.value = '<p style="text-align: center; color: #909399;">加载失败</p>'
  } finally {
    docxLoading.value = false
  }
}

// 加载Excel文档内容（通过后端解析表格数据）
const loadExcelContent = async () => {
  if (!currentDocument.value) return
  excelLoading.value = true
  excelData.value = null
  try {
    // 先尝试获取解析后的文本内容
    const res = await documentsApi.getDocumentText(currentDocument.value.id, false, 500000)
    if (res.code === 200 && res.data?.text) {
      // 尝试解析表格格式的文本（CSV-like格式）
      const lines = res.data.text.split('\n').filter(line => line.trim())
      if (lines.length > 0) {
        // 简单解析：假设第一行是表头，之后是数据
        const headers = lines[0].split('\t').map(h => h.trim())
        const rows = lines.slice(1).map(line =>
          line.split('\t').map(cell => cell.trim())
        )
        excelData.value = { headers, rows }
      } else {
        excelData.value = null
      }
    } else {
      excelData.value = null
    }
  } catch (error) {
    console.error('Failed to load excel content:', error)
    excelData.value = null
  } finally {
    excelLoading.value = false
  }
}

const filteredChunks = computed(() => {
  if (!chunkKeyword.value) {
    return chunks.value
  }
  return chunks.value.filter(chunk =>
    chunk.text.toLowerCase().includes(chunkKeyword.value.toLowerCase())
  )
})

const getProcessStepName = (step: string): string => {
  return ProcessingStepLabels[step] || step
}

const getProcessStatusType = (step: string): 'success' | 'warning' | 'info' | 'danger' => {
  if (step === 'completed') return 'success'
  if (step === 'failed' || step === 'parse_failed' || step === 'chunk_failed') return 'danger'
  if (step.includes('ing')) return 'warning'
  return 'info'
}

const showDocumentDetail = async (doc: Document) => {
  currentDocument.value = doc
  showDetailDialog.value = true
  detailActiveTab.value = 'info'

  await Promise.all([
    loadProcessStatus(),
    loadChunks(),
    loadDocumentText()
  ])
}

const loadProcessStatus = async () => {
  if (!currentDocument.value) return

  try {
    const res = await documentsApi.getProcessStatus(currentDocument.value.id)
    if (res.code === 200 && res.data) {
      processStatus.value = res.data
    }
  } catch (error) {
    console.error('Failed to load process status:', error)
  }
}

const loadChunks = async () => {
  if (!currentDocument.value) return

  try {
    const res = await documentsApi.getChunks(currentDocument.value.id, processForm.strategy)
    if (res.code === 200 && res.data) {
      chunks.value = res.data
    } else {
      chunks.value = []
    }
  } catch (error) {
    console.error('Failed to load chunks:', error)
    chunks.value = []
  }
}

const loadDocumentText = async () => {
  if (!currentDocument.value) return

  try {
    const res = await documentsApi.getDocumentText(currentDocument.value.id)
    if (res.code === 200 && res.data) {
      documentText.value = res.data
    } else {
      documentText.value = null
    }
  } catch (error) {
    console.error('Failed to load document text:', error)
    documentText.value = null
  }
}

const openProcessDialog = () => {
  showProcessDialog.value = true
  isProcessing.value = false
  isProcessCompleted.value = false
  processStatus.value = null
}

// 从操作列直接打开处理对话框
const openProcessFromAction = async (doc: Document) => {
  currentDocument.value = doc
  // 获取文档所属知识库的docType
  const collection = collections.value.find(c => c.id === doc.collectionId)
  currentCollectionDocType.value = (collection as any)?.docType || null
  showDetailDialog.value = false
  openProcessDialog()
}

const closeProcessDialog = () => {
  if (processPollingTimer) {
    clearInterval(processPollingTimer)
    processPollingTimer = null
  }
  showProcessDialog.value = false
  isProcessing.value = false
}

const startProcess = async () => {
  if (!currentDocument.value) return

  isProcessing.value = true
  isProcessCompleted.value = false
  processStatus.value = null

  // 根据知识库类型确定分块策略
  // MANUAL类型强制使用 none（不分块）
  // NORMAL类型使用用户选择的策略（默认semantic）
  const strategy = currentCollectionDocType.value === 'MANUAL'
    ? 'none'
    : processForm.strategy

  try {
    const res = await documentsApi.processDocument(currentDocument.value.id, strategy as any, currentDocument.value.collectionId)

    if (res.code === 200) {
      ElMessage.success('文档处理已启动')

      // 记录开始时间用于超时检测
      processStartTime = Date.now()

      // 启动轮询和超时检测
      processPollingTimer = window.setInterval(async () => {
        await pollProcessStatus()
        checkProcessTimeout()
      }, 2000)
    } else {
      ElMessage.error(res.message || '启动处理失败')
      isProcessing.value = false
    }
  } catch (error: any) {
    ElMessage.error(error.message || '启动处理失败')
    isProcessing.value = false
  }
}

const pollProcessStatus = async () => {
  if (!currentDocument.value || !isProcessing.value) {
    if (processPollingTimer) {
      clearInterval(processPollingTimer)
      processPollingTimer = null
    }
    return
  }

  try {
    const res = await documentsApi.getProcessStatus(currentDocument.value.id)

    if (res.code === 200 && res.data) {
      processStatus.value = res.data

      // 立即刷新页面状态，让用户看到进度更新
      loadDocuments()

      // Check for failure conditions
      if (res.data.parseStatus === 'failed' || res.data.chunkStatus === 'chunk_failed') {
        if (processPollingTimer) {
          clearInterval(processPollingTimer)
          processPollingTimer = null
        }
        isProcessing.value = false
        ElMessage.error('处理失败: ' + (res.data.errorMessage || (res.data.chunkStatus === 'chunk_failed' ? '分块失败' : '解析失败')))
        return
      }

      if (res.data.progress >= 100 || res.data.currentStep === 'completed') {
        if (processPollingTimer) {
          clearInterval(processPollingTimer)
          processPollingTimer = null
        }
        isProcessing.value = false
        isProcessCompleted.value = true
        ElMessage.success('文档处理完成')

        await loadChunks()
        await loadDocumentText()
        await loadDocuments()
      }
    }
  } catch (error) {
    console.error('Failed to poll process status:', error)
  }
}

// 超时检测
let processStartTime = 0

const checkProcessTimeout = () => {
  if (!isProcessing.value) return

  const elapsed = Date.now() - processStartTime
  if (elapsed >= PROCESS_TIMEOUT_MS) {
    if (processPollingTimer) {
      clearInterval(processPollingTimer)
      processPollingTimer = null
    }
    isProcessing.value = false
    ElMessage.warning({
      message: '文档处理超时（已等待60秒），文档可能仍在后台执行中。请关闭对话框，5分钟后再刷新页面查看处理结果。',
      duration: 8000
    })
  }
}

const cancelProcess = () => {
  if (processPollingTimer) {
    clearInterval(processPollingTimer)
    processPollingTimer = null
  }
  isProcessing.value = false
  ElMessage.info('已取消处理')
}

const viewChunkDetail = (chunk: ChunkDTO) => {
  currentChunk.value = chunk
  showChunkDetailDialog.value = true
}

const handleSelectionChange = (selection: Document[]) => {
  selectedDocuments.value = selection
  isIndeterminate.value = selection.length > 0 && selection.length < documents.value.length
  isAllSelected.value = selection.length === documents.value.length
}

const handleSelectAll = (val: boolean) => {
  if (val) {
    selectedDocuments.value = [...documents.value]
  } else {
    selectedDocuments.value = []
  }
  isIndeterminate.value = false
}

const clearSelection = () => {
  selectedDocuments.value = []
  isAllSelected.value = false
  isIndeterminate.value = false
}

const batchProcess = async () => {
  if (selectedDocuments.value.length === 0) {
    ElMessage.warning('请先选择要处理的文档')
    return
  }
  const docsToProcess = selectedDocuments.value.filter(doc => doc.status === 'PENDING' || doc.status === 'COMPLETED')
  await Promise.all(docsToProcess.map(doc => documentsApi.processDocument(doc.id, 'fixed').catch(error => {
    console.error('Failed to process document:', doc.id, error)
  })))
  ElMessage.success('已启动批量处理')
  clearSelection()
  loadDocuments()
}

const batchDelete = async () => {
  if (selectedDocuments.value.length === 0) {
    ElMessage.warning('请先选择要删除的文档')
    return
  }
  try {
    await ElMessageBox.confirm(`确定要删除选中的 ${selectedDocuments.value.length} 个文档吗？`, '批量删除', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await Promise.all(selectedDocuments.value.map(doc => documentsApi.delete(doc.id).catch(error => {
      console.error('Failed to delete document:', doc.id, error)
    })))
    ElMessage.success('已删除选中文档')
    clearSelection()
    loadDocuments()
  } catch {
    // User cancelled
  }
}

const loadDocuments = async () => {
  loading.value = true
  try {
    const res = await documentsApi.list({
      page: pagination.page,
      pageSize: pagination.pageSize,
      collectionId: filterCollection.value || undefined,
      status: filterStatus.value || undefined
    })
    if (res.code === 200 && res.data) {
      // Map API response fields to frontend Document type
      // Look up collection names from the loaded collections
      const collectionMap = new Map(collections.value.map(c => [c.id, c.name]))
      documents.value = res.data.items.map((item: any) => ({
        id: item.id,
        name: item.title || item.fileName || item.name,
        type: (item.fileType || item.type || 'PDF').toUpperCase(),
        size: item.fileSize || item.size || 0,
        status: item.uploadStatus === 'completed'
          ? (item.parseStatus === 'completed'
            ? (item.chunkStatus === 'completed' ? 'COMPLETED' : item.chunkStatus === 'failed' ? 'FAILED' : 'PENDING')
            : item.parseStatus === 'failed' ? 'FAILED' : 'PENDING')
          : item.uploadStatus === 'failed' ? 'FAILED' : 'UPLOADING',
        progress: item.progress || 0,
        collectionId: item.collectionId,
        collectionName: collectionMap.get(item.collectionId) || item.collectionName || '',
        chunkCount: item.chunkCount || 0,
        expectedChunkCount: item.expectedChunkCount,
        errorMessage: item.parseErrorMessage || item.errorMessage,
        createdAt: item.createdAt,
        updatedAt: item.updatedAt
      }))
      pagination.total = res.data.total
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

const handleFileChange = (file: any) => {
  selectedFiles.value.push(file.raw)
  fileList.value.push(file)
}

const handleFileRemove = (file: any) => {
  const index = selectedFiles.value.findIndex(f => f.name === file.name)
  if (index > -1) {
    selectedFiles.value.splice(index, 1)
  }
}

const handleUpload = async () => {
  if (!uploadForm.collectionId) {
    ElMessage.warning('请选择知识库')
    return
  }
  if (selectedFiles.value.length === 0) {
    ElMessage.warning('请选择文件')
    return
  }

  uploading.value = true
  showProgressDialog.value = true
  uploadProgress.value = selectedFiles.value.map(f => ({
    name: f.name,
    percent: 0,
    status: '' as const
  }))

  for (let i = 0; i < selectedFiles.value.length; i++) {
    const file = selectedFiles.value[i]
    try {
      await documentsApi.upload(
        file,
        uploadForm.collectionId,
        (percent) => {
          uploadProgress.value[i].percent = percent
          if (percent === 100) {
            uploadProgress.value[i].status = 'success'
          }
        }
      )
      uploadProgress.value[i].status = 'success'
      uploadProgress.value[i].percent = 100
    } catch (error: any) {
      uploadProgress.value[i].status = 'exception'
      uploadProgress.value[i].error = error.message || '上传失败'
    }
  }

  uploading.value = false
  ElMessage.success('上传完成')
  loadDocuments()
}

const handleRetry = async (row: Document) => {
  try {
    const res = await documentsApi.retry(row.id)
    if (res.code === 200) {
      ElMessage.success('已重新处理')
      loadDocuments()
    }
  } catch (error) {
    console.error('Failed to retry:', error)
  }
}

const handleDelete = async (row: Document) => {
  try {
    await ElMessageBox.confirm(`确定要删除文档"${row.name}"吗？`, '提示', {
      type: 'warning'
    })
    const res = await documentsApi.delete(row.id)
    if (res.code === 200) {
      ElMessage.success('删除成功')
      loadDocuments()
    }
  } catch {
    // 用户取消
  }
}

// 下载文件
const downloadFile = () => {
  if (!currentDocument.value) return
  const link = document.createElement('a')
  link.href = previewUrl.value
  link.download = currentDocument.value.name
  link.target = '_blank'
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
}

onMounted(async () => {
  // Load collections first so we can map names for documents
  await loadCollections()
  await loadDocuments()

  // 检查路由参数是否有预览请求
  const previewDocId = route.query.preview as string
  const previewPage = route.query.page as string
  if (previewDocId) {
    // 直接等待文档加载完成后打开预览（使用 watch 监听 documents 变化）
    const unwatch = watch(documents, (newDocs) => {
      const doc = newDocs.find(d => d.id === previewDocId)
      if (doc) {
        currentDocument.value = doc
        showDetailDialog.value = true
        if (previewPage) {
          initialPdfPage.value = parseInt(previewPage) || 1
        }
        unwatch()  // 找到后取消监听
      }
    }, { immediate: true })
  }
})

onUnmounted(() => {
  if (processPollingTimer) {
    clearInterval(processPollingTimer)
    processPollingTimer = null
  }
})
</script>

<style scoped>
.documents {
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

.upload-btn {
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

.table-card {
  border-radius: var(--radius-lg);
  border: 1px solid var(--border);
  box-shadow: var(--shadow-sm);
}

.table-card :deep(.el-card__body) {
  padding: 0;
}

.batch-actions-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  background: var(--muted);
  border-bottom: 1px solid var(--border);
}

.selected-count {
  font-size: 14px;
  color: var(--muted-foreground);
  margin-right: 8px;
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

.type-tag {
  border-radius: 6px;
  padding: 4px 8px;
}

.size-text {
  font-size: 13px;
  color: var(--muted-foreground);
}

.status-cell {
  display: flex;
  align-items: center;
}

.status-tag {
  border-radius: 6px;
  padding: 4px 10px;
}

.chunk-count {
  font-weight: 500;
  color: var(--accent);
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

/* Upload Dialog */
.upload-dialog :deep(.el-dialog),
.progress-dialog :deep(.el-dialog),
.detail-dialog :deep(.el-dialog),
.process-dialog :deep(.el-dialog),
.chunk-dialog :deep(.el-dialog) {
  border-radius: 16px;
  overflow: hidden;
}

.upload-dialog :deep(.el-dialog__header),
.progress-dialog :deep(.el-dialog__header),
.detail-dialog :deep(.el-dialog__header),
.process-dialog :deep(.el-dialog__header),
.chunk-dialog :deep(.el-dialog__header) {
  padding: 20px 24px;
  background: var(--muted);
  border-bottom: 1px solid var(--border);
}

.upload-area :deep(.el-upload) {
  width: 100%;
}

.upload-area :deep(.el-upload-dragger) {
  width: 100%;
  padding: 32px;
  border-radius: var(--radius-lg);
  border: 2px dashed var(--border);
  background: var(--muted);
  transition: all var(--transition);
}

.upload-area :deep(.el-upload-dragger:hover) {
  border-color: var(--accent);
  background: rgba(99, 102, 241, 0.05);
}

.upload-trigger {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.upload-icon {
  font-size: 48px;
  color: var(--accent);
}

.upload-text {
  text-align: center;
}

.text-main {
  font-size: 14px;
  color: var(--muted-foreground);
}

.text-main em {
  color: var(--accent);
  font-style: normal;
}

.text-sub {
  display: block;
  font-size: 12px;
  color: var(--muted-foreground);
  margin-top: 8px;
}

.upload-progress {
  max-height: 400px;
  overflow-y: auto;
}

.progress-item {
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--border);
}

.progress-item:last-child {
  border-bottom: none;
}

.progress-info {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
}

.file-name {
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.progress-percent {
  color: var(--accent);
  font-weight: 500;
}

.error-msg {
  color: var(--danger);
  font-size: 12px;
  margin-top: 4px;
}

.detail-tabs :deep(.el-tabs__item) {
  font-weight: 500;
}

.detail-tabs :deep(.el-tabs__item.is-active) {
  color: var(--accent);
}

.detail-tabs :deep(.el-tabs__active-bar) {
  background: var(--accent);
}

.doc-descriptions :deep(.el-descriptions__label) {
  font-weight: 500;
  background: var(--muted);
}

.detail-actions {
  display: flex;
  gap: 12px;
}

.chunk-toolbar {
  display: flex;
  gap: 12px;
  align-items: center;
}

.text-viewer {
  padding: 16px;
}

.file-viewer {
  width: 100%;
  height: 70vh;
  min-height: 500px;
}

.file-iframe {
  width: 100%;
  height: 100%;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
}

.text-info {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 12px;
}

.process-status {
  padding: 16px;
}

.process-step {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-bottom: 16px;
}

.step-label {
  font-weight: 500;
}

.process-progress-bar {
  margin: 16px 0;
}

.process-tip {
  color: var(--el-text-color-secondary);
  font-size: 14px;
  margin-top: 12px;
  text-align: center;
}

.chunk-detail {
  padding: 16px;
}

.chunk-detail h4 {
  margin-bottom: 12px;
  font-size: 14px;
  font-weight: 500;
  color: var(--foreground);
}

.dialog-footer {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
}

.doc-name-text {
  font-weight: 500;
  color: var(--foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 400px;
  display: inline-block;
}

.doc-name-truncate {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
}

.form-select :deep(.el-select__wrapper) {
  border-radius: var(--radius);
}

/* OCR Preview Styles */
.ocr-preview {
  padding: 16px;
}

.ocr-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding: 12px 16px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
}

.page-nav {
  display: flex;
  align-items: center;
  gap: 12px;
}

.page-info {
  font-weight: 500;
  color: var(--el-text-color-primary);
  min-width: 80px;
  text-align: center;
}

.page-jump {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: var(--el-text-color-regular);
}

.page-jump .el-input-number {
  width: 100px;
}

.ocr-content {
  min-height: 400px;
}

.ocr-error {
  margin-bottom: 16px;
}

.ocr-result {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.ocr-markdown h4 {
  margin: 0;
  font-size: 14px;
  font-weight: 500;
  color: var(--el-text-color-primary);
}

.ocr-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.markdown-body {
  padding: 16px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
  font-family: monospace;
  font-size: 13px;
  line-height: 1.8;
  max-height: 500px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-all;
}

.markdown-body code {
  background: var(--el-fill-color);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 12px;
}

.markdown-body strong {
  color: var(--el-text-color-primary);
}

/* 文件预览样式 */
.file-preview {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.preview-container {
  display: flex;
  flex-direction: column;
  height: 70vh;
  min-height: 400px;
}

.preview-toolbar {
  padding: 12px 16px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
  margin-bottom: 12px;
}

.preview-iframe {
  flex: 1;
  width: 100%;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  background: white;
}

.image-preview {
  justify-content: center;
  align-items: center;
  background: var(--el-fill-color-lighter);
}

.preview-image {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  border-radius: 8px;
}

.text-preview {
  height: 70vh;
}

.text-content-wrapper {
  height: 100%;
  overflow: auto;
  background: var(--el-fill-color-light);
  border-radius: 8px;
  padding: 16px;
}

.text-content {
  white-space: pre-wrap;
  word-wrap: break-word;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', 'Consolas', monospace;
  font-size: 13px;
  line-height: 1.6;
  margin: 0;
}

/* Word 文档预览样式 */
.docx-content {
  flex: 1;
  overflow: auto;
  background: var(--el-fill-color-light);
  border-radius: 8px;
  padding: 20px;
}

.docx-rendered {
  background: white;
  padding: 24px;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  line-height: 1.8;
}

/* Excel 文档预览样式 */
.excel-content {
  flex: 1;
  overflow: auto;
  background: var(--el-fill-color-light);
  border-radius: 8px;
  padding: 16px;
}

.excel-table-wrapper {
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  overflow: auto;
}

.excel-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.excel-table th {
  background: var(--el-fill-color);
  color: var(--el-text-color-primary);
  font-weight: 600;
  text-align: left;
  padding: 12px 16px;
  border-bottom: 2px solid var(--el-border-color);
  white-space: nowrap;
}

.excel-table td {
  padding: 10px 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  max-width: 300px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.excel-table tr:hover td {
  background: var(--el-fill-color-light);
}

/* 不支持的格式样式 */
.unsupported-preview {
  display: flex;
  justify-content: center;
  align-items: center;
}

.unsupported-info {
  text-align: center;
  padding: 40px;
}

.unsupported-info .el-icon {
  margin-bottom: 16px;
}

.unsupported-text {
  font-size: 16px;
  color: var(--el-text-color-primary);
  margin: 12px 0;
}

.unsupported-hint {
  font-size: 14px;
  color: var(--el-text-color-secondary);
  margin-bottom: 20px;
}
</style>
