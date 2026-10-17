<template>
  <div class="ocr-preview-page">
    <!-- Page Header -->
    <div class="page-header">
      <div class="header-content">
        <h1 class="page-title">OCR文档预览</h1>
        <p class="page-subtitle">上传PDF文档，实时预览OCR识别结果</p>
      </div>
    </div>

    <!-- Upload Area (when no file) -->
    <div v-if="!pdfFile" class="upload-section">
      <el-card class="upload-card" shadow="hover">
        <el-form label-width="100px" class="upload-form">
          <el-form-item label="选择知识库" required>
            <el-select v-model="selectedCollectionId" placeholder="请选择知识库" style="width: 100%">
              <el-option v-for="col in collections" :key="col.id" :label="col.name" :value="col.id" />
            </el-select>
          </el-form-item>
        </el-form>
        <el-upload
          ref="uploadRef"
          :auto-upload="false"
          :limit="1"
          :on-change="handleFileChange"
          :file-list="fileList"
          accept=".pdf"
          drag
          class="pdf-upload"
        >
          <div class="upload-content">
            <el-icon class="upload-icon"><Document /></el-icon>
            <div class="upload-text">
              <span class="text-main">将 PDF 文件拖到此处，或<em>点击上传</em></span>
              <span class="text-sub">支持 PDF 格式文件</span>
            </div>
          </div>
        </el-upload>
        <div class="upload-actions">
          <el-button type="primary" size="large" :loading="parsing || uploading" @click="startParse" :disabled="!selectedFile || !selectedCollectionId">
            <el-icon><VideoPlay /></el-icon> 开始上传
          </el-button>
        </div>
      </el-card>
    </div>

    <!-- Preview Section (when file loaded) -->
    <div v-else class="preview-section">
      <!-- Toolbar -->
      <div class="preview-toolbar">
        <div class="toolbar-left">
          <el-button @click="clearFile" size="small">
            <el-icon><RefreshLeft /></el-icon> 重新上传
          </el-button>
          <el-divider direction="vertical" />
          <span class="file-name">{{ pdfFile.name }}</span>
        </div>
        <div class="toolbar-center">
          <el-button :disabled="currentPage <= 1" @click="prevPage" size="small">
            <el-icon><ArrowLeft /></el-icon>
          </el-button>
          <span class="page-indicator">{{ currentPage }} / {{ totalPages }}</span>
          <el-button :disabled="currentPage >= totalPages" @click="nextPage" size="small">
            <el-icon><ArrowRight /></el-icon>
          </el-button>
        </div>
        <div class="toolbar-right">
          <span class="selected-collection">{{ selectedCollectionId ? collections.find(c => c.id === selectedCollectionId)?.name : '未选择知识库' }}</span>
          <el-button v-if="savedDocumentId" type="warning" @click="reSaveOcrContent" :loading="saving">
            <el-icon><DocumentChecked /></el-icon> 重新保存
          </el-button>
          <el-button v-if="savedDocumentId" type="info" @click="cancelSave">
            <el-icon><Close /></el-icon> 取消保存
          </el-button>
          <el-button v-else type="primary" :loading="saving" @click="saveToCollection">
            <el-icon><DocumentChecked /></el-icon> 保存到知识库
          </el-button>
        </div>
      </div>

      <!-- Split View -->
      <div class="preview-content">
        <!-- Left: PDF Preview -->
        <div class="pdf-panel">
          <div class="panel-header">
            <span>原始文档 (第 {{ currentPage }} 页)</span>
          </div>
          <div class="pdf-viewer" v-loading="pdfLoading" ref="pdfViewerRef">
            <canvas ref="pdfCanvas" class="pdf-canvas"></canvas>
          </div>
        </div>

        <!-- Right: Markdown Preview/Edit -->
        <div class="md-panel">
          <div class="panel-header">
            <div class="panel-header-left">
              <span>OCR识别结果</span>
              <el-tag v-if="isEdited" type="warning" size="small">已编辑</el-tag>
              <el-radio-group v-if="ocrText" v-model="editorMode" size="small" class="editor-mode-switch">
                <el-radio-button value="edit">编辑</el-radio-button>
                <el-radio-button value="preview">预览</el-radio-button>
                <el-radio-button value="split">分栏</el-radio-button>
              </el-radio-group>
            </div>
            <div class="panel-header-right">
              <el-button v-if="isEdited" type="warning" size="small" :loading="updating" @click="updateOcrContent">
                <el-icon><RefreshLeft /></el-icon> 更新保存
              </el-button>
              <el-button v-if="isEdited" size="small" @click="resetOcrContent">
                <el-icon><RefreshLeft /></el-icon> 重置
              </el-button>
              <el-tag v-if="ocrStatus" :type="getOcrStatusType(ocrStatus)" size="small">
                {{ getOcrStatusText(ocrStatus) }}
              </el-tag>
            </div>
          </div>
          <div class="md-viewer" v-loading="ocrLoading">
            <div v-if="ocrError" class="ocr-error">
              <el-alert :title="ocrError" type="error" :closable="false" show-icon />
            </div>
            <template v-else-if="ocrText">
              <div :class="['editor-area', { 'split-layout': editorMode === 'split' }]">
                <textarea
                  v-show="editorMode !== 'preview'"
                  v-model="editableOcrText"
                  class="ocr-textarea"
                  :class="{ 'split-mode': editorMode === 'split' }"
                  placeholder="在此编辑OCR识别结果..."
                  @input="onOcrTextChange"
                ></textarea>
                <div
                  v-show="editorMode !== 'edit'"
                  class="markdown-preview"
                  v-html="renderedMarkdown"
                ></div>
              </div>
            </template>
            <el-empty v-else description='点击"开始解析"按钮开始OCR识别' />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, shallowRef, reactive, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { marked } from 'marked'
import { Document, VideoPlay, RefreshLeft, ArrowLeft, ArrowRight, DocumentChecked, Close } from '@element-plus/icons-vue'
// pdfjs-dist is imported dynamically to avoid private field issues with bundlers
import { documentsApi } from '@/api/documents'
import { collectionsApi } from '@/api/collections'
import type { MultiCollection } from '@/types'

const uploadRef = ref()
const fileList = ref<any[]>([])
const selectedFile = ref<File | null>(null)
const parsing = ref(false)
const saving = ref(false)

// PDF related
const pdfFile = shallowRef<File | null>(null)
const pdfCanvas = ref<HTMLCanvasElement | null>(null)
const pdfViewerRef = ref<HTMLDivElement | null>(null)
const pdfLoading = ref(false)
const pdfDoc = shallowRef<any>(null)
const currentPage = ref(1)
const totalPages = ref(0)

// OCR related
const ocrLoading = ref(false)
const ocrError = ref('')
const ocrText = ref('')
const ocrStatus = ref<'SUCCESS' | 'FAILED' | 'EXCEEDED' | 'PARTIAL' | ''>('')
const fullOcrResult = ref<Map<number, string>>(new Map())
const ocrDocId = ref<string | null>(null)
const ocrFilePath = ref<string | null>(null)
const uploading = ref(false)

// Edit related
const updating = ref(false)
const editorMode = ref<'edit' | 'preview' | 'split'>('edit')

// Per-page edit state management (replaces global editableOcrText/originalOcrText)
const pageEditStates = reactive<Record<number, { original: string; edited: string }>>({})

const isEdited = computed(() => {
  const state = pageEditStates[currentPage.value]
  return state ? state.edited !== state.original : false
})

const editableOcrText = computed({
  get: () => pageEditStates[currentPage.value]?.edited ?? '',
  set: (val: string) => {
    const page = currentPage.value
    if (!pageEditStates[page]) {
      pageEditStates[page] = { original: val, edited: val }
    } else {
      pageEditStates[page].edited = val
    }
  }
})

const savedOcrContent = ref<Map<number, string>>(new Map())

// Markdown preview rendering
const renderedMarkdown = computed(() => {
  const text = editableOcrText.value
  if (!text) return ''
  try {
    return marked.parse(text) as string
  } catch {
    return text
  }
})

// Collection related
const collections = ref<MultiCollection[]>([])
const selectedCollectionId = ref<string | ''>('')

// Get chunking strategy for selected collection
// Maps backend ChunkingStrategy enum to frontend API format
const getSelectedCollectionStrategy = (): string => {
  if (!selectedCollectionId.value) return 'semantic'
  const collection = collections.value.find(c => c.id === selectedCollectionId.value)
  if (collection?.config?.chunking?.strategy) {
    const strategyMap: Record<string, string> = {
      SEMANTIC: 'semantic',
      PARAGRAPH: 'paragraph',
      CHARACTER: 'character',
      SENTENCE: 'sentence',
      FIXED: 'fixed',
      NONE: 'none'
    }
    return strategyMap[collection.config.chunking.strategy] || 'semantic'
  }
  return 'semantic'
}

// Save state for cancel support
const saveInProgress = ref(false)
const savedDocumentId = ref<string | null>(null)
const savedCollectionId = ref<string | null>(null)

const handleFileChange = (file: any) => {
  selectedFile.value = file.raw
  fileList.value = [file]
  ocrDocId.value = null
  ocrFilePath.value = null
}

const clearFile = () => {
  pdfFile.value = null
  selectedFile.value = null
  fileList.value = []
  currentPage.value = 1
  totalPages.value = 0
  ocrText.value = ''
  for (const key of Object.keys(pageEditStates)) {
    delete pageEditStates[Number(key)]
  }
  ocrError.value = ''
  ocrStatus.value = ''
  pdfDoc.value = null
  fullOcrResult.value.clear()
  savedOcrContent.value.clear()
  ocrDocId.value = null
  ocrFilePath.value = null
}

const startParse = async () => {
  if (!selectedFile.value) {
    ElMessage.warning('请先选择PDF文件')
    return
  }

  parsing.value = true
  pdfLoading.value = true
  ocrLoading.value = true
  uploading.value = true
  ocrError.value = ''
  ocrText.value = ''

  try {
    // First upload file to get temp document ID and file path
    const uploadRes = await documentsApi.uploadForOcr(selectedFile.value)
    if (uploadRes.data.code !== 200 || !uploadRes.data.data) {
      throw new Error(uploadRes.data.message || '上传失败')
    }
    ocrDocId.value = uploadRes.data.data.id
    ocrFilePath.value = uploadRes.data.data.filePath

    // Then load PDF for preview
    const pdfjsLib = await import('pdfjs-dist')
    pdfjsLib.GlobalWorkerOptions.workerSrc = '/js/pdf.worker.min.js'

    const arrayBuffer = await selectedFile.value.arrayBuffer()
    const loadingTask = pdfjsLib.getDocument({ data: arrayBuffer })
    const pdfDocument = await loadingTask.promise
    pdfDoc.value = pdfDocument
    totalPages.value = pdfDocument.numPages
    pdfFile.value = selectedFile.value

    // Render first page for preview
    await renderPdfPage(1)

    // OCR all pages
    for (let page = 1; page <= pdfDocument.numPages; page++) {
      await ocrPage(page)
    }
  } catch (error: any) {
    ElMessage.error('加载PDF失败: ' + error.message)
  } finally {
    parsing.value = false
    pdfLoading.value = false
    ocrLoading.value = false
    uploading.value = false
  }
}

const renderPdfPage = async (pageNum: number) => {
  if (!pdfDoc.value) return

  try {
    const page = await pdfDoc.value.getPage(pageNum)
    const canvas = pdfCanvas.value
    const viewer = pdfViewerRef.value
    if (!canvas || !viewer) return

    const context = canvas.getContext('2d')
    if (!context) return

    // Calculate scale to fit container
    const containerWidth = viewer.clientWidth - 20 // padding
    const containerHeight = viewer.clientHeight - 20
    const originalViewport = page.getViewport({ scale: 1.0 })
    const scaleX = containerWidth / originalViewport.width
    const scaleY = containerHeight / originalViewport.height
    const scale = Math.min(scaleX, scaleY, 2) // cap at 2x

    const viewport = page.getViewport({ scale })
    canvas.height = viewport.height
    canvas.width = viewport.width

    const renderContext = {
      canvasContext: context,
      viewport: viewport
    }

    await page.render(renderContext).promise
    currentPage.value = pageNum
  } catch (error: any) {
    console.error('Render PDF page failed:', error)
  }
}

const prevPage = async () => {
  if (currentPage.value > 1) {
    await goToPage(currentPage.value - 1)
  }
}

const nextPage = async () => {
  if (currentPage.value < totalPages.value) {
    await goToPage(currentPage.value + 1)
  }
}

const goToPage = async (pageNum: number) => {
  await renderPdfPage(pageNum)

  // Initialize page edit state if not yet loaded
  if (!pageEditStates[pageNum]) {
    if (savedOcrContent.value.has(pageNum)) {
      const savedText = savedOcrContent.value.get(pageNum) || ''
      ocrText.value = savedText
      pageEditStates[pageNum] = { original: savedText, edited: savedText }
      ocrStatus.value = 'SUCCESS'
      ocrError.value = ''
    } else if (fullOcrResult.value.has(pageNum)) {
      const ocrResult = fullOcrResult.value.get(pageNum) || ''
      ocrText.value = ocrResult
      pageEditStates[pageNum] = { original: ocrResult, edited: ocrResult }
      ocrStatus.value = 'SUCCESS'
      ocrError.value = ''
    } else {
      await ocrPage(pageNum)
    }
  }
}

const ocrPage = async (pageNum: number) => {
  if (!ocrDocId.value || !ocrFilePath.value) return

  ocrLoading.value = true
  ocrError.value = ''

  try {
    // Get OCR result using the pre-uploaded document ID and file path
    const ocrRes = await documentsApi.ocrPreview(ocrDocId.value, ocrFilePath.value, pageNum)

    if (ocrRes.code === 200 && ocrRes.data) {
      ocrText.value = ocrRes.data.ocrText || ''
      pageEditStates[pageNum] = { original: ocrRes.data.ocrText || '', edited: ocrRes.data.ocrText || '' }
      ocrStatus.value = ocrRes.data.status
      if (ocrRes.data.errorMessage) {
        ocrError.value = ocrRes.data.errorMessage
      }
      fullOcrResult.value.set(pageNum, ocrText.value)
      savedOcrContent.value.set(pageNum, ocrText.value)
    } else {
      ocrError.value = ocrRes.message || 'OCR识别失败'
      ocrStatus.value = 'FAILED'
    }
  } catch (error: any) {
    ocrError.value = error.message || 'OCR识别失败'
    ocrStatus.value = 'FAILED'
    ocrText.value = ''
  } finally {
    ocrLoading.value = false
  }
}

const getOcrStatusType = (status: string): 'success' | 'warning' | 'danger' | 'info' => {
  switch (status) {
    case 'SUCCESS': return 'success'
    case 'FAILED': return 'danger'
    case 'EXCEEDED': return 'warning'
    case 'PARTIAL': return 'warning'
    default: return 'info'
  }
}

const getOcrStatusText = (status: string): string => {
  switch (status) {
    case 'SUCCESS': return '识别成功'
    case 'FAILED': return '识别失败'
    case 'EXCEEDED': return '超出页数限制'
    case 'PARTIAL': return '部分识别成功'
    default: return ''
  }
}

/**
 * 检测OCR文本是否被编辑
 */
const onOcrTextChange = () => {
  // editableOcrText 变化时会自动更新 isEdited computed
}

/**
 * 重置OCR内容为原始识别结果
 */
const resetOcrContent = () => {
  const page = currentPage.value
  if (pageEditStates[page]) {
    pageEditStates[page].edited = pageEditStates[page].original
  }
}

/**
 * 更新当前页OCR内容到内存（不落盘）
 */
const updateOcrContent = async () => {
  const page = currentPage.value
  if (!page) return

  // Accept current edits: mark original = edited so isEdited becomes false
  if (pageEditStates[page]) {
    pageEditStates[page].original = pageEditStates[page].edited
  }

  // Keep savedOcrContent synchronized for save-to-collection
  savedOcrContent.value.set(page, editableOcrText.value)
  fullOcrResult.value.set(page, editableOcrText.value)

  ElMessage.success('已更新当前页内容（请点击"保存到知识库"按钮落盘）')
}

/**
 * 保存到知识库
 */
const saveToCollection = async () => {
  if (!selectedCollectionId.value) {
    ElMessage.warning('请先选择知识库')
    return
  }

  if (savedOcrContent.value.size === 0 && !editableOcrText.value) {
    ElMessage.warning('没有可保存的OCR结果')
    return
  }

  // Combine all saved OCR results (prioritize edited content)
  const allOcrText = Array.from(savedOcrContent.value.entries())
    .sort((a, b) => a[0] - b[0])
    .map(([page, text]) => `## 第 ${page} 页\n\n${text}`)
    .join('\n\n---\n\n')

  try {
    saving.value = true
    saveInProgress.value = true
    const title = `${pdfFile.value?.name || 'document'}_ocr`

    // 调用保存API（documentId 由后端自动生成并返回）
    const strategy = getSelectedCollectionStrategy()
    const res = await documentsApi.saveOcrMarkdown({
      collectionId: selectedCollectionId.value,
      title: title,
      markdownContent: allOcrText,
      strategy: strategy,
      updateContent: false // 首次保存
    })

    if (res.code === 200) {
      // 使用后端返回的 documentId
      const documentId = res.data.documentId
      savedDocumentId.value = documentId
      savedCollectionId.value = selectedCollectionId.value
      ElMessage.success(`OCR结果已保存到知识库（${res.data.storedChunks} 个向量块）`)
    } else {
      ElMessage.error(res.message || '保存失败')
      saveInProgress.value = false
    }
  } catch (error: any) {
    ElMessage.error('保存失败: ' + error.message)
    saveInProgress.value = false
  } finally {
    saving.value = false
  }
}

/**
 * 重新保存编辑后的OCR内容到知识库（更新已有文档）
 */
const reSaveOcrContent = async () => {
  if (!selectedCollectionId.value || !savedDocumentId.value) {
    ElMessage.warning('请先保存到知识库后再更新')
    return
  }

  if (savedOcrContent.value.size === 0 && !editableOcrText.value) {
    ElMessage.warning('没有可保存的OCR结果')
    return
  }

  // Combine all saved OCR results (prioritize edited content)
  const allOcrText = Array.from(savedOcrContent.value.entries())
    .sort((a, b) => a[0] - b[0])
    .map(([page, text]) => `## 第 ${page} 页\n\n${text}`)
    .join('\n\n---\n\n')

  try {
    saving.value = true
    const strategy = getSelectedCollectionStrategy()
    const res = await documentsApi.saveOcrMarkdown({
      collectionId: selectedCollectionId.value,
      documentId: savedDocumentId.value,
      title: `${pdfFile.value?.name || 'document'}_ocr`,
      markdownContent: allOcrText,
      strategy: strategy,
      updateContent: true // 更新操作
    })

    if (res.code === 200) {
      ElMessage.success(`OCR内容已更新（${res.data.storedChunks} 个向量块）`)
    } else {
      ElMessage.error(res.message || '更新失败')
    }
  } catch (error: any) {
    ElMessage.error('更新失败: ' + error.message)
  } finally {
    saving.value = false
  }
}

/**
 * 取消保存并清理向量
 */
const cancelSave = async () => {
  if (!savedDocumentId.value || !savedCollectionId.value) {
    return
  }

  try {
    const res = await documentsApi.cancelOcrSave(savedDocumentId.value, savedCollectionId.value)
    if (res.code === 200) {
      ElMessage.success('已取消保存并清理向量')
    } else {
      ElMessage.error(res.message || '取消保存失败')
    }
  } catch (error: any) {
    ElMessage.error('取消保存失败: ' + error.message)
  } finally {
    savedDocumentId.value = null
    savedCollectionId.value = null
    saveInProgress.value = false
  }
}

onUnmounted(() => {
  if (pdfDoc.value) {
    pdfDoc.value.destroy()
  }
})

let resizeObserver: ResizeObserver | null = null

// Load collections on mount
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

onMounted(() => {
  loadCollections()
  if (pdfViewerRef.value) {
    resizeObserver = new ResizeObserver(() => {
      if (pdfDoc.value && currentPage.value > 0) {
        renderPdfPage(currentPage.value)
      }
    })
    resizeObserver.observe(pdfViewerRef.value)
  }
})
</script>

<style scoped>
.ocr-preview-page {
  padding: 20px;
  height: calc(100vh - 120px);
  display: flex;
  flex-direction: column;
}

.page-header {
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

/* Upload Section */
.upload-section {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.upload-card {
  width: 600px;
  border-radius: 16px;
}

.pdf-upload {
  width: 100%;
}

.pdf-upload :deep(.el-upload-dragger) {
  width: 100%;
  padding: 60px 40px;
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
  font-size: 64px;
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

.upload-actions {
  display: flex;
  justify-content: center;
  margin-top: 24px;
}

/* Preview Section */
.preview-section {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.preview-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 12px 12px 0 0;
}

.toolbar-left,
.toolbar-center,
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.file-name {
  font-weight: 500;
  color: var(--foreground);
  max-width: 300px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.selected-collection {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  padding: 4px 8px;
  background: var(--el-fill-color-light);
  border-radius: 4px;
  max-width: 150px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.page-indicator {
  font-weight: 500;
  color: var(--foreground);
  min-width: 80px;
  text-align: center;
}

/* Split View */
.preview-content {
  flex: 1;
  display: flex;
  min-height: 0;
  border: 1px solid var(--border);
  border-top: none;
  border-radius: 0 0 12px 12px;
  overflow: hidden;
}

.pdf-panel,
.md-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.pdf-panel {
  border-right: 1px solid var(--border);
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: var(--muted);
  border-bottom: 1px solid var(--border);
  font-weight: 500;
  font-size: 14px;
  color: var(--foreground);
}

.panel-header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.panel-header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.pdf-viewer {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--muted);
  overflow: auto;
  padding: 20px;
}

.pdf-viewer canvas {
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
  border-radius: 4px;
  max-width: 100%;
  max-height: 100%;
}

.pdf-canvas {
  object-fit: contain;
}

.md-viewer {
  flex: 1;
  overflow: auto;
  padding: 20px;
  background: var(--card);
}

.ocr-error {
  margin-bottom: 16px;
}

.markdown-content {
  font-family: monospace;
  font-size: 14px;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-all;
  color: var(--foreground);
}

.ocr-textarea {
  width: 100%;
  height: 100%;
  min-height: 400px;
  padding: 12px 16px;
  font-family: monospace;
  font-size: 14px;
  line-height: 1.8;
  color: var(--foreground);
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 8px;
  resize: vertical;
  box-sizing: border-box;
}

.ocr-textarea:focus {
  outline: none;
  border-color: var(--primary);
  box-shadow: 0 0 0 2px var(--primary-light);
}

.ocr-textarea.split-mode {
  min-height: 200px;
  resize: none;
}

.editor-mode-switch {
  margin-left: 12px;
}

.editor-area {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.editor-area.split-layout {
  flex-direction: row;
  gap: 8px;
}

.editor-area.split-layout .ocr-textarea,
.editor-area.split-layout .markdown-preview {
  width: 50%;
  min-height: 400px;
}

.markdown-preview {
  padding: 12px 16px;
  font-size: 14px;
  line-height: 1.8;
  color: var(--foreground);
  overflow-y: auto;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 8px;
}

.markdown-preview :deep(h1),
.markdown-preview :deep(h2),
.markdown-preview :deep(h3),
.markdown-preview :deep(h4) {
  margin-top: 1em;
  margin-bottom: 0.5em;
  font-weight: 600;
}

.markdown-preview :deep(p) {
  margin-bottom: 0.8em;
}

.markdown-preview :deep(code) {
  background: var(--muted);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 12px;
}

.markdown-preview :deep(pre) {
  background: var(--muted);
  padding: 12px;
  border-radius: 8px;
  overflow-x: auto;
}

.markdown-preview :deep(pre code) {
  background: none;
  padding: 0;
}

.markdown-preview :deep(blockquote) {
  border-left: 4px solid var(--primary);
  padding-left: 12px;
  margin-left: 0;
  color: var(--muted-foreground);
}

.markdown-preview :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin-bottom: 1em;
}

.markdown-preview :deep(th),
.markdown-preview :deep(td) {
  border: 1px solid var(--border);
  padding: 8px 12px;
  text-align: left;
}

.markdown-preview :deep(th) {
  background: var(--muted);
  font-weight: 600;
}

.markdown-content :deep(strong) {
  font-weight: 600;
}

.markdown-content :deep(code) {
  background: var(--muted);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 12px;
}

/* Collection Dialog Styles */
.collection-dialog-content {
  padding: 10px 0;
}

.dialog-tip {
  margin-bottom: 16px;
  color: var(--muted-foreground);
  font-size: 14px;
}

.collection-radio-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}

.collection-radio-item {
  width: 100%;
  padding: 12px 16px;
  border: 1px solid var(--border);
  border-radius: 8px;
  transition: all 0.2s;
}

.collection-radio-item:hover {
  border-color: var(--primary);
  background: var(--muted);
}

.collection-radio-item .el-radio__label {
  flex: 1;
}

.collection-item-content {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.collection-name {
  font-weight: 500;
  color: var(--foreground);
}

.collection-desc {
  font-size: 12px;
  color: var(--muted-foreground);
}
</style>
