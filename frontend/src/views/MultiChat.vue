<template>
  <div class="multi-chat-container">
    <div class="chat-sidebar">
      <div class="sidebar-header">
        <span>对话列表</span>
        <el-button type="primary" size="small" @click="handleNewSession">
          <el-icon><Plus /></el-icon> 新建
        </el-button>
      </div>
      
      <div class="session-search">
        <el-input
          v-model="sessionSearch"
          placeholder="搜索对话"
          clearable
          size="small"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
      </div>

      <div class="session-list" ref="sessionListRef" @scroll="onSessionListScroll">
        <div
          v-for="session in filteredSessions"
          :key="session.id"
          :class="['session-item', { active: currentSession?.id === session.id }]"
          @click="handleSelectSession(session)"
        >
          <div class="session-info">
            <span class="session-title">{{ session.title || '新对话' }}</span>
            <span class="session-meta">
              {{ session.collectionIds.length }}个知识库 · {{ formatRelativeTime(session.lastMessageAt) }}
            </span>
          </div>
          <el-dropdown trigger="click" @command="(cmd: string) => handleSessionCommand(cmd, session)">
            <el-icon class="session-actions"><MoreFilled /></el-icon>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="rename">重命名</el-dropdown-item>
                <el-dropdown-item command="export-markdown" :disabled="exporting">导出为 Markdown</el-dropdown-item>
                <el-dropdown-item command="export-pdf" :disabled="exporting">导出为 PDF</el-dropdown-item>
                <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
        
        <el-empty v-if="sessions.length === 0" description="暂无对话记录" :image-size="60" />
      </div>
    </div>

    <div class="chat-main">
      <div class="chat-header">
        <div class="header-left">
          <el-icon :size="24" color="var(--accent)"><ChatDotRound /></el-icon>
          <h2>智能问答</h2>
        </div>
        
        <div class="header-center">
          <el-select
            v-model="selectedCollectionIds"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            placeholder="选择知识库 (可多选)"
            style="min-width: 300px; max-width: 500px"
            :disabled="!!currentSession"
            @change="handleCollectionChange"
          >
            <el-option
              v-for="col in activeCollections"
              :key="col.id"
              :label="col.name"
              :value="col.id"
            >
              <div class="collection-option">
                <span>{{ col.name }}</span>
                <el-tag size="small" type="info">{{ CollectionTypeLabels[col.type || 'KNOWLEDGE_BASE'] }}</el-tag>
              </div>
            </el-option>
          </el-select>
          
          <el-tooltip :content="noExpansion ? '仅根据文档回答' : '允许AI补充回答'">
            <el-button
              :type="noExpansion ? 'primary' : 'default'"
              @click="handleToggleNoExpansion"
            >
              <el-icon><Lock /></el-icon>
              {{ noExpansion ? '仅文档' : '允许拓展' }}
            </el-button>
          </el-tooltip>
        </div>

        <div class="header-right">
          <el-button text @click="showSettings = true">
            <el-icon><Setting /></el-icon>
          </el-button>
          <el-button-group>
            <el-button :type="useThinking ? 'primary' : 'default'" @click="useThinking = true">
              <el-icon><Connection /></el-icon> 思考
            </el-button>
            <el-button :type="!useThinking ? 'primary' : 'default'" @click="useThinking = false">
              <el-icon><Document /></el-icon> 非思考
            </el-button>
          </el-button-group>
        </div>
      </div>

      <div class="chat-messages" ref="messagesContainer">
        <div v-if="messages.length === 0 && !chatState.isGenerating" class="empty-state">
          <div class="empty-icon">
            <el-icon :size="80" color="var(--muted-foreground)"><ChatLineSquare /></el-icon>
          </div>
          <p class="empty-text">开始向知识库提问</p>
          <div class="suggestions" v-if="selectedCollectionIds.length > 0">
            <div
              v-for="(suggestion, index) in suggestions"
              :key="index"
              class="suggestion"
              @click="askSuggestion(suggestion)"
            >
              {{ suggestion }}
            </div>
          </div>
          <div v-else class="empty-hint">
            请在上方选择至少一个知识库开始问答
          </div>
        </div>

        <div v-for="(msg, index) in messages" :key="index" :class="['message', msg.role]">
          <div class="message-avatar">
            <el-avatar :size="40" :class="msg.role === 'user' ? 'avatar-user' : 'avatar-assistant'">
              <el-icon :size="20">
                <User v-if="msg.role === 'user'" />
                <ChatDotRound v-else />
              </el-icon>
            </el-avatar>
          </div>
          
          <div class="message-content">
            <div v-if="msg.role === 'assistant'" class="ai-badge">
              <el-icon :size="14" color="var(--accent)"><MagicStick /></el-icon>
              <span>AI 回复</span>
              <span class="generation-info" v-if="msg.generationTimeMs">
                · {{ msg.generationTimeMs }}ms
              </span>
            </div>

            <!-- 思考过程展示 -->
            <div v-if="msg.reasoning && msg.reasoning.trim()" class="reasoning-container">
              <div class="reasoning-header" @click="toggleReasoning(msg)">
                <el-icon :size="12">
                  <Clock v-if="!getReasoningExpanded(msg)" /><CircleCheck v-else />
                </el-icon>
                <span>思考过程</span>
                <el-icon :size="12">
                  <ArrowDown v-if="!getReasoningExpanded(msg)" /><ArrowUp v-else />
                </el-icon>
              </div>
              <div v-if="getReasoningExpanded(msg)" class="reasoning-content" v-html="formatMessage(msg.reasoning)"></div>
            </div>

            <div class="message-text" v-html="formatMessage(msg.content)"></div>
            
            <div v-if="msg.sources && msg.sources.length > 0" class="sources">
              <div class="sources-header" @click="toggleSources(msg)">
                <el-icon :size="14">
                  <ArrowDown v-if="!getSourceExpanded(msg)" /><ArrowUp v-else />
                </el-icon>
                <span>{{ msg.sources.length }} 个引用来源</span>
              </div>
              <div v-if="getSourceExpanded(msg)" class="sources-list">
                <div
                  v-for="(source, sourceIndex) in msg.sources"
                  :key="sourceIndex"
                  class="source-item"
                >
                  <div class="source-header">
                    <span class="source-title" @click="openDocumentPreview(source)" style="cursor: pointer; text-decoration: underline;">
                      {{ source.title }}
                    </span>
                    <el-tag v-if="source.collectionName" size="small" type="info">
                      {{ source.collectionName }}
                    </el-tag>
                    <el-tag v-if="source.startPage && source.startPage > 0" size="small" type="warning">
                      第 {{ source.startPage }} 页
                    </el-tag>
                    <el-tag v-if="source.score || source.similarity" size="small" type="success">
                      {{ ((source.score ?? source.similarity ?? 0) * 100).toFixed(0) }}%
                    </el-tag>
                  </div>
                  <div class="source-snippet">{{ source.snippet }}</div>
                </div>
              </div>
            </div>
            
            <div class="message-footer">
              <span class="message-time">{{ formatDate(msg.createdAt, 'HH:mm') }}</span>
              <div v-if="msg.role === 'assistant'" class="message-actions">
                <el-button
                  type="primary"
                  text
                  size="small"
                  @click="handleFeedback(msg.id, 'like')"
                >
                  <el-icon><Star /></el-icon>
                </el-button>
                <el-button
                  type="primary"
                  text
                  size="small"
                  @click="handleFeedback(msg.id, 'dislike')"
                >
                  <el-icon><StarFilled /></el-icon>
                </el-button>
                <el-button
                  type="primary"
                  text
                  size="small"
                  @click="handleRegenerate(msg.id)"
                >
                  <el-icon><Refresh /></el-icon>
                </el-button>
              </div>
            </div>
          </div>
        </div>

        <div v-if="chatState.isGenerating" class="message assistant">
          <div class="message-avatar">
            <el-avatar :size="40" class="avatar-assistant">
              <el-icon :size="20"><ChatDotRound /></el-icon>
            </el-avatar>
          </div>
          <div class="message-content">
            <div class="ai-badge">
              <el-icon :size="14" color="var(--accent)"><MagicStick /></el-icon>
              <span>AI 回复</span>
              <!-- 显示当前处理状态 -->
              <span v-if="chatState.status" class="status-indicator">
                <span class="status-dot"></span>
                {{ chatState.status === 'rewrite' ? '正在优化问题...' : chatState.status === 'searching' ? '检索知识库...' : '生成回答...' }}
              </span>
              <span v-else class="generating-indicator">
                <span class="dot"></span>
                <span class="dot"></span>
                <span class="dot"></span>
              </span>
            </div>
            <div v-if="chatState.isGenerating && chatState.reasoningVisible && chatState.reasoning" class="reasoning-live" v-html="formatMessage(chatState.reasoning)"></div>
            <div class="message-text">
              <span v-if="chatState.answer">{{ chatState.answer }}</span>
              <span v-else class="typing-indicator">
                <span></span><span></span><span></span>
              </span>
            </div>
          </div>
        </div>
      </div>

      <div class="chat-input">
        <div class="input-toolbar">
          <div class="toolbar-left">
            <el-tag
              v-for="id in selectedCollectionIds"
              :key="id"
              closable
              size="small"
              @close="handleRemoveCollection(id)"
            >
              {{ getCollectionName(id) }}
            </el-tag>
          </div>
          <div class="toolbar-right">
            <el-tooltip content="清空对话">
              <el-button text size="small" @click="handleClearMessages">
                <el-icon><Delete /></el-icon>
              </el-button>
            </el-tooltip>
          </div>
        </div>

        <div class="input-wrapper">
          <el-input
            v-model="question"
            type="textarea"
            :rows="2"
            :placeholder="getInputPlaceholder()"
            resize="none"
            :disabled="selectedCollectionIds.length === 0 || chatState.isGenerating"
            @keydown.enter.exact.prevent="handleSend"
          />
          <div class="input-actions">
            <span class="char-count">{{ question.length }}/2000</span>
            <el-button
              v-if="chatState.isGenerating"
              type="warning"
              @click="handleAbort"
            >
              <el-icon><VideoPause /></el-icon> 停止
            </el-button>
            <el-button
              v-else
              type="primary"
              :disabled="!question.trim() || selectedCollectionIds.length === 0"
              @click="handleSend"
            >
              <el-icon><Promotion /></el-icon> 发送
            </el-button>
          </div>
        </div>

        <!-- LLM参数设置弹窗 -->
        <el-drawer v-model="showSettings" title="LLM 参数设置" direction="rtl" size="360px">
          <div class="settings-form">
            <el-form label-width="100px" label-position="left">
              <el-form-item label="Temperature">
                <el-slider v-model="llmParams.temperature" :min="0" :max="2" :step="0.1" show-input />
              </el-form-item>
              <el-form-item label="Top P">
                <el-slider v-model="llmParams.topP" :min="0" :max="1" :step="0.05" show-input />
              </el-form-item>
              <el-form-item label="Top K">
                <el-input-number v-model="llmParams.topK" :min="1" :max="100" />
              </el-form-item>
              <el-form-item label="Max Tokens">
                <el-input-number v-model="llmParams.maxTokens" :min="100" :max="8000" :step="100" />
              </el-form-item>
              <el-form-item label="Repetition Penalty">
                <el-slider v-model="llmParams.repetitionPenalty" :min="1" :max="2" :step="0.05" show-input />
              </el-form-item>
              <el-form-item label="Presence Penalty">
                <el-slider v-model="llmParams.presencePenalty" :min="-2" :max="2" :step="0.1" show-input />
              </el-form-item>
              <el-form-item label="启用推理">
                <el-switch v-model="llmParams.thinkingEnabled" />
              </el-form-item>
              <el-form-item v-if="llmParams.thinkingEnabled" label="思考预算">
                <div class="thinking-budget-control">
                  <div class="thinking-budget-custom">
                    <el-checkbox v-model="thinkingBudgetCustom" size="small">
                      自定义
                    </el-checkbox>
                    <span class="thinking-budget-value" v-if="!thinkingBudgetCustom">
                      服务端默认
                    </span>
                  </div>
                  <template v-if="thinkingBudgetCustom">
                    <el-slider
                      :model-value="llmParams.thinkingMaxTokens ?? 4096"
                      @update:model-value="(v: any) => { if (typeof v === 'number') llmParams.thinkingMaxTokens = v }"
                      :min="512"
                      :max="16384"
                      :step="256"
                      :show-tooltip="false"
                      style="flex: 1; margin: 0 12px;"
                    />
                    <el-input-number
                      :model-value="llmParams.thinkingMaxTokens ?? 4096"
                      @update:model-value="(v: any) => { if (v !== undefined) llmParams.thinkingMaxTokens = v }"
                      :min="512"
                      :max="16384"
                      :step="256"
                      size="small"
                      style="width: 110px;"
                    />
                  </template>
                </div>
              </el-form-item>
              <el-form-item label="会话历史">
                <el-switch v-model="llmParams.enableHistory" />
              </el-form-item>
            </el-form>
            <div class="settings-footer">
              <el-button @click="resetLlmParams">重置</el-button>
              <el-button type="primary" @click="showSettings = false">确定</el-button>
            </div>
          </div>
        </el-drawer>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, nextTick, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { chatApi, chatStreamService } from '@/api/chat'
import { formatDate } from '@/utils/format'
import { useCollectionStore } from '@/stores/collections'
import type {
  ChatSession,
  ChatMessage,
  SearchSource
} from '@/types'
import { CollectionTypeLabels } from '@/types'
import { Setting, Clock, CircleCheck } from '@element-plus/icons-vue'
import { marked } from 'marked'

const route = useRoute()
const router = useRouter()
const collectionStore = useCollectionStore()

const collections = computed(() => collectionStore.collections)
const activeCollections = computed(() => collectionStore.activeCollections)

const sessions = ref<ChatSession[]>([])
const currentSession = ref<ChatSession | null>(null)
const messages = ref<ChatMessage[]>([])
const messagesContainer = ref<HTMLElement>()
// Use message-role-based keys (e.g. "assistant-<uuid>") instead of array index
const expandedSourceMap = reactive<Record<string, boolean>>({})
const expandedReasoningMap = reactive<Record<string, boolean>>({})

const selectedCollectionIds = ref<string[]>([])
const question = ref('')
const useThinking = ref(true)
const noExpansion = ref(false)
const sessionSearch = ref('')
const sessionListRef = ref<HTMLElement | null>(null)
const sessionPage = ref(0)
const sessionTotal = ref(0)
const sessionLoadingMore = ref(false)
const exporting = ref(false)
const showSettings = ref(false)
const thinkingBudgetCustom = ref(false)

const llmParams = reactive({
  temperature: 0.7,
  topP: 0.9,
  topK: 50,
  maxTokens: 4096,
  repetitionPenalty: 1.1,
  presencePenalty: 0.0,
  thinkingEnabled: false,
  thinkingMaxTokens: undefined as number | undefined,  // undefined 表示使用服务端默认值
  enableHistory: false
})

// 监视思考预算自定义开关：开启时设为默认值 4096，关闭时恢复 undefined
watch(thinkingBudgetCustom, (val) => {
  if (val && llmParams.thinkingMaxTokens === undefined) {
    llmParams.thinkingMaxTokens = 4096
  } else if (!val) {
    llmParams.thinkingMaxTokens = undefined
  }
})

const chatState = reactive({
  answer: '',
  reasoning: '',     // 思考过程
  sources: [] as SearchSource[],
  isGenerating: false,
  error: null as string | null,
  reasoningVisible: false,  // 思考过程是否展开
  status: '' as '' | 'rewrite' | 'searching' | 'generating'  // 当前处理状态
})

const suggestions = [
  '这个知识库包含哪些内容？',
  '最近的文档更新了什么？',
  '如何上传新文档？',
  '文档支持哪些格式？'
]

const filteredSessions = computed(() => {
  if (!sessionSearch.value) return sessions.value
  const search = sessionSearch.value.toLowerCase()
  return sessions.value.filter(s => 
    s.title.toLowerCase().includes(search)
  )
})

const getCollectionName = (id: string): string => {
  const col = collections.value.find(c => c.id === id)
  return col?.name || id
}

const loadCollections = async () => {
  try {
    await collectionStore.loadCollections({ page: 0, pageSize: 100 })
    if (collections.value.length > 0 && selectedCollectionIds.value.length === 0) {
      selectedCollectionIds.value = [collections.value[0].id]
    }
  } catch (error) {
    console.error('Failed to load collections:', error)
  }
}

const loadSessions = async (reset = true) => {
  try {
    if (reset) {
      sessionPage.value = 0
    }
    const res = await chatApi.listSessions({ page: sessionPage.value, pageSize: 20 })
    if (res.code === 200 && res.data) {
      const items = res.data.items || []
      if (reset) {
        sessions.value = items
      } else {
        sessions.value.push(...items)
      }
      sessionTotal.value = res.data.total || 0
    }
  } catch (error) {
    console.error('Failed to load sessions:', error)
  }
}

const loadMoreSessions = async () => {
  if (sessionLoadingMore.value) return
  const loadedCount = sessions.value.length
  if (loadedCount >= sessionTotal.value) return

  sessionLoadingMore.value = true
  sessionPage.value++
  await loadSessions(false)
  sessionLoadingMore.value = false
}

const onSessionListScroll = () => {
  const el = sessionListRef.value
  if (!el || sessionLoadingMore.value) return
  const scrollPercent = (el.scrollTop + el.clientHeight) / el.scrollHeight
  if (scrollPercent > 0.8) {
    loadMoreSessions()
  }
}

const handleSelectSession = async (session: ChatSession) => {
  currentSession.value = session
  selectedCollectionIds.value = session.collectionIds
  
  try {
    const res = await chatApi.getSessionMessages(session.id)
    if (res.code === 200 && res.data) {
      messages.value = res.data.items
      scrollToBottom()
    }
  } catch (error) {
    console.error('Failed to load messages:', error)
  }
}

const handleNewSession = async () => {
  if (selectedCollectionIds.value.length === 0) {
    ElMessage.warning('请先选择知识库')
    return
  }

  try {
    const { value: title } = await ElMessageBox.prompt('请输入对话标题', '新建对话', {
      confirmButtonText: '确定',
      cancelButtonText: '取消'
    })

    const res = await chatApi.createSession({
      title: title || '新对话',
      collectionIds: selectedCollectionIds.value
    })
    
    if (res.code === 200 && res.data) {
      sessions.value.unshift(res.data)
      handleSelectSession(res.data)
    }
  } catch {
    // 用户取消
  }
}

const handleSessionCommand = async (command: string, session: ChatSession) => {
  switch (command) {
    case 'rename':
      await handleRenameSession(session)
      break
    case 'export-markdown':
      await handleExportSession(session, 'markdown')
      break
    case 'export-pdf':
      await handleExportSession(session, 'pdf')
      break
    case 'delete':
      await handleDeleteSession(session)
      break
  }
}

const handleRenameSession = async (session: ChatSession) => {
  try {
    const { value: title } = await ElMessageBox.prompt('请输入新标题', '重命名', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputValue: session.title
    })

    const res = await chatApi.renameSession(session.id, title || '')
    if (res.code === 200) {
      const index = sessions.value.findIndex(s => s.id === session.id)
      if (index !== -1) {
        sessions.value[index] = { ...sessions.value[index], title: title || '' }
      }
    }
  } catch {
    // 用户取消
  }
}

const handleExportSession = async (session: ChatSession, format: 'markdown' | 'pdf' = 'markdown') => {
  exporting.value = true
  try {
    ElMessage.info('正在导出...')
    const res = await chatApi.exportSession(session.id, format)

    let mimeType: string
    let fileExtension: string

    if (format === 'pdf') {
      mimeType = 'application/pdf'
      fileExtension = 'pdf'
    } else {
      mimeType = 'text/markdown'
      fileExtension = 'md'
    }

    const blob = new Blob([(res as any).data as BlobPart], { type: mimeType })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${session.title || '对话'}.${fileExtension}`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch (error) {
    console.error('Failed to export session:', error)
    ElMessage.error('导出失败')
  } finally {
    exporting.value = false
  }
}

const handleDeleteSession = async (session: ChatSession) => {
  try {
    await ElMessageBox.confirm('确定要删除此对话吗？', '提示', { type: 'warning' })
    const res = await chatApi.deleteSession(session.id)
    if (res.code === 200) {
      sessions.value = sessions.value.filter(s => s.id !== session.id)
      if (currentSession.value?.id === session.id) {
        currentSession.value = null
        messages.value = []
      }
      ElMessage.success('删除成功')
    }
  } catch {
    // 用户取消
  }
}

const handleCollectionChange = () => {
  if (currentSession.value && selectedCollectionIds.value.length > 0) {
    chatApi.updateSessionCollections(currentSession.value.id, selectedCollectionIds.value)
  }
}

const handleToggleNoExpansion = () => {
  noExpansion.value = !noExpansion.value
}

const handleRemoveCollection = (id: string) => {
  selectedCollectionIds.value = selectedCollectionIds.value.filter(cid => cid !== id)
}

const getSourceKey = (msg: ChatMessage) => `${msg.role}-${msg.id}`
const getSourceExpanded = (msg: ChatMessage) => !!expandedSourceMap[getSourceKey(msg)]
const toggleSources = (msg: ChatMessage) => {
  const key = getSourceKey(msg)
  expandedSourceMap[key] = !expandedSourceMap[key]
}

const getReasoningKey = (msg: ChatMessage) => `reasoning-${msg.id}`
const getReasoningExpanded = (msg: ChatMessage) => !!expandedReasoningMap[getReasoningKey(msg)]
const toggleReasoning = (msg: ChatMessage) => {
  const key = getReasoningKey(msg)
  expandedReasoningMap[key] = !expandedReasoningMap[key]
}

// 打开文档预览
const openDocumentPreview = (source: SearchSource) => {
  router.push({
    name: 'Documents',
    query: {
      preview: source.documentId,
      page: source.startPage || 1
    }
  })
}

const handleSend = async () => {
  if (!question.value.trim() || selectedCollectionIds.value.length === 0 || chatState.isGenerating) return

  const userMessage: ChatMessage = {
    id: `temp-${Date.now()}`,
    sessionId: currentSession.value?.id || '',
    departmentId: '',
    role: 'user',
    content: question.value.trim(),
    createdAt: new Date().toISOString()
  }

  question.value = ''
  messages.value.push(userMessage)
  scrollToBottom()

  if (!currentSession.value) {
    try {
      const res = await chatApi.createSession({
        collectionIds: selectedCollectionIds.value
      })
      if (!res || res.code !== 200 || !res.data) {
        ElMessage.error('创建会话失败，请重试')
        chatState.isGenerating = false
        return
      }
      currentSession.value = res.data
      userMessage.sessionId = res.data.id
      sessions.value.unshift(res.data)
    } catch (error) {
      console.error('Failed to create session:', error)
      ElMessage.error('创建会话失败')
      chatState.isGenerating = false
      return
    }
  }

  await handleStreamQuery(userMessage)
}

const handleStreamQuery = async (userMessage: ChatMessage) => {
  chatState.isGenerating = true
  chatState.error = null
  chatState.answer = ''
  chatState.sources = []
  // 初始状态，等待后端返回的状态
  chatState.status = 'searching'

  const assistantMessageId = `assistant-${Date.now()}`
  const assistantMessage: ChatMessage = {
    id: assistantMessageId,
    sessionId: currentSession.value!.id,
    departmentId: '',
    role: 'assistant',
    content: '',
    sources: [],
    createdAt: new Date().toISOString()
  }

  try {
    await chatStreamService.streamQuery(
      {
        sessionId: currentSession.value!.id,
        query: userMessage.content,
        collectionIds: selectedCollectionIds.value,
        noExpansion: noExpansion.value,
        similarityThreshold: 0.7,
        ...llmParams,
        thinkingEnabled: useThinking.value
      },
      {
        onToken: (content) => {
          chatState.answer += content
          chatState.status = 'generating'
          assistantMessage.content = chatState.answer
        },
        onReasoning: (content) => {
          chatState.reasoning += content
          chatState.reasoningVisible = true
        },
        onSource: (source) => {
          chatState.status = 'generating'
          if (!chatState.sources.find(s => s.documentId === source.documentId)) {
            chatState.sources.push(source)
            assistantMessage.sources = [...chatState.sources]
          }
        },
        onRewrite: () => {
          // 后端返回重写后的query时切换状态
          chatState.status = 'generating'
        },
        onDone: (messageId, tokensUsed, generationTimeMs) => {
          assistantMessage.id = messageId
          assistantMessage.tokenUsage = tokensUsed ? {
            promptTokens: 0,
            completionTokens: tokensUsed,
            totalTokens: tokensUsed
          } : undefined
          assistantMessage.generationTimeMs = generationTimeMs
          assistantMessage.reasoning = chatState.reasoning // 保存思考过程
          // 思考结束后自动折叠
          chatState.reasoningVisible = false

          const tempIndex = messages.value.findIndex(m => m.id === userMessage.id)
          if (tempIndex !== -1) {
            messages.value[tempIndex] = userMessage
          }
          messages.value.push(assistantMessage)

          resetChatState()
          scrollToBottom()
          loadSessions()
        },
        onError: (error) => {
          chatState.error = error
          chatState.isGenerating = false
          ElMessage.error(error)
        }
      }
    )
  } catch (error) {
    console.error('Stream query failed:', error)
    chatState.isGenerating = false
  }
}

const handleAbort = () => {
  chatStreamService.abort()
  resetChatState()
  ElMessage.warning('已停止生成')
}

const handleFeedback = async (messageId: string, feedback: 'like' | 'dislike') => {
  try {
    const res = await chatApi.feedbackMessage(currentSession.value!.id, messageId, feedback)
    if (res.code === 200) {
      ElMessage.success(feedback === 'like' ? '感谢您的反馈' : '感谢您的反馈')
    }
  } catch (error) {
    console.error('Failed to submit feedback:', error)
  }
}

const handleRegenerate = async (messageId: string) => {
  try {
    const res = await chatApi.regenerateResponse(currentSession.value!.id, messageId)
    if (res.code === 200 && res.data) {
      const index = messages.value.findIndex(m => m.id === messageId)
      if (index !== -1) {
        messages.value[index] = res.data
      }
    }
  } catch (error) {
    console.error('Failed to regenerate:', error)
  }
}

const handleClearMessages = () => {
  messages.value = []
  resetChatState()
}

const resetChatState = () => {
  chatState.answer = ''
  chatState.reasoning = ''
  chatState.sources = []
  chatState.isGenerating = false
  chatState.error = null
  chatState.reasoningVisible = false
  chatState.status = ''
}

const askSuggestion = (suggestion: string) => {
  question.value = suggestion
  handleSend()
}

const resetLlmParams = () => {
  llmParams.temperature = 0.7
  llmParams.topP = 0.9
  llmParams.topK = 50
  llmParams.maxTokens = 2000
  llmParams.repetitionPenalty = 1.1
  llmParams.presencePenalty = 0.0
  llmParams.thinkingEnabled = false
  llmParams.thinkingMaxTokens = undefined
  thinkingBudgetCustom.value = false
  llmParams.enableHistory = false
}

const escapeHtml = (text: string): string => {
  const div = document.createElement('div')
  div.textContent = text
  return div.innerHTML
}

const formatMessage = (content: string): string => {
  if (!content) return ''
  const html = marked.parse(escapeHtml(content), { breaks: true, async: false }) as string
  return html.replace(/\[\[(.*?)\]\]/g, '<span class="citation-ref">[$1]</span>')
}

const formatRelativeTime = (dateStr: string | undefined): string => {
  if (!dateStr) return '未知'
  const date = new Date(dateStr)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const minutes = Math.floor(diff / 60000)
  const hours = Math.floor(diff / 3600000)
  const days = Math.floor(diff / 86400000)

  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes}分钟前`
  if (hours < 24) return `${hours}小时前`
  if (days < 7) return `${days}天前`
  return formatDate(dateStr, 'MM-DD HH:mm')
}

const getInputPlaceholder = (): string => {
  if (selectedCollectionIds.value.length === 0) {
    return '请先选择知识库'
  }
  return '请输入您的问题，按Enter发送，Shift+Enter换行'
}

const scrollToBottom = () => {
  nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    }
  })
}

watch(() => route.query, (query) => {
  if (query.collectionId) {
    selectedCollectionIds.value = [query.collectionId as string]
  }
  if (query.question) {
    question.value = query.question as string
    nextTick(() => handleSend())
  }
}, { immediate: true })

onMounted(() => {
  loadCollections()
  loadSessions()
})
</script>

<style scoped>
.multi-chat-container {
  display: flex;
  gap: 24px;
  height: calc(100vh - 120px);
  background: var(--card);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.chat-sidebar {
  width: 280px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.sidebar-header {
  padding: 16px;
  border-bottom: 1px solid var(--border);
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
  color: var(--foreground);
}

.session-search {
  padding: 12px;
  border-bottom: 1px solid var(--border);
}

.session-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.session-item {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 12px;
  border-radius: 8px;
  cursor: pointer;
  margin-bottom: 4px;
  transition: all 0.3s;
}

.session-item:hover {
  background: var(--muted);
}

.session-item.active {
  background: rgba(99, 102, 241, 0.1);
}

.session-info {
  flex: 1;
  overflow: hidden;
  padding-right: 8px;
}

.session-title {
  display: block;
  font-size: 14px;
  font-weight: 500;
  color: var(--foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-bottom: 4px;
}

.session-meta {
  display: block;
  font-size: 12px;
  color: var(--muted-foreground);
}

.session-actions {
  opacity: 0;
  transition: opacity var(--transition);
  color: var(--muted-foreground);
}

.session-item:hover .session-actions {
  opacity: 1;
}

.chat-main {
  flex: 1;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.chat-header {
  padding: 16px;
  border-bottom: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-left h2 {
  font-size: 18px;
  font-weight: 600;
  color: var(--foreground);
  margin: 0;
}

.header-center {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12px;
  justify-content: center;
}

.header-right {
  display: flex;
  align-items: center;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
}

.empty-state {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--muted-foreground);
}

.empty-icon {
  margin-bottom: 16px;
}

.empty-text {
  margin: 0 0 24px 0;
  font-size: 16px;
  color: var(--muted-foreground);
}

.empty-hint {
  font-size: 14px;
  color: var(--muted-foreground);
}

.suggestions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  justify-content: center;
  max-width: 600px;
}

.suggestion {
  padding: 12px 24px;
  background: var(--muted);
  border: 1px solid var(--border);
  border-radius: 20px;
  cursor: pointer;
  font-size: 14px;
  color: var(--muted-foreground);
  transition: all var(--transition);
}

.suggestion:hover {
  background: rgba(99, 102, 241, 0.1);
  border-color: var(--accent);
  color: var(--accent);
}

.message {
  display: flex;
  gap: 12px;
  margin-bottom: 24px;
}

.message.user {
  flex-direction: row-reverse;
}

.message-avatar {
  flex-shrink: 0;
}

.avatar-user {
  background: var(--muted-foreground) !important;
}

.avatar-assistant {
  background: linear-gradient(135deg, var(--accent) 0%, var(--accent-dark) 100%) !important;
}

.message-content {
  max-width: 80%;
  display: flex;
  flex-direction: column;
}

.message.user .message-content {
  align-items: flex-end;
}

.ai-badge {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
  font-size: 12px;
  font-weight: 500;
  color: var(--accent);
}

.generation-info {
  color: var(--muted-foreground);
  font-weight: normal;
}

.generating-indicator {
  display: inline-flex;
  gap: 3px;
  margin-left: 8px;
}

.generating-indicator .dot {
  width: 6px;
  height: 6px;
  background: var(--accent);
  border-radius: 50%;
  animation: bounce 1.4s infinite ease-in-out;
}

.generating-indicator .dot:nth-child(1) { animation-delay: -0.32s; }
.generating-indicator .dot:nth-child(2) { animation-delay: -0.16s; }

@keyframes bounce {
  0%, 80%, 100% { transform: scale(0); opacity: 0.5; }
  40% { transform: scale(1); opacity: 1; }
}

/* 状态指示器样式 */
.status-indicator {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-left: 8px;
  font-size: 12px;
  color: var(--accent);
  font-weight: normal;
}

.status-dot {
  width: 6px;
  height: 6px;
  background: var(--accent);
  border-radius: 50%;
  animation: pulse 1.5s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.5; transform: scale(0.8); }
}

.message-text {
  padding: 12px 16px;
  border-radius: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  font-size: 14px;
}

.message.user .message-text {
  background: var(--accent);
  color: #fff;
}

.message.assistant .message-text {
  background: var(--muted);
  color: var(--foreground);
}

.sources {
  margin-top: 12px;
}

.sources-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--accent);
  cursor: pointer;
  padding: 4px 0;
}

.sources-header:hover {
  color: var(--accent-dark);
}

.sources-list {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.source-item {
  padding: 12px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
}

.source-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.source-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--foreground);
}

.source-snippet {
  font-size: 12px;
  color: var(--muted-foreground);
  line-height: 1.5;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.message-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}

.message-time {
  font-size: 12px;
  color: var(--muted-foreground);
}

.message-actions {
  display: flex;
  gap: 4px;
  opacity: 0;
  transition: opacity var(--transition);
}

.message:hover .message-actions {
  opacity: 1;
}

.chat-input {
  padding: 16px;
  border-top: 1px solid var(--border);
}

.input-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  min-height: 28px;
}

.toolbar-left {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.toolbar-right {
  display: flex;
  gap: 8px;
}

.input-wrapper {
  display: flex;
  gap: 12px;
  align-items: flex-end;
}

.input-wrapper :deep(.el-textarea) {
  flex: 1;
}

.input-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.char-count {
  font-size: 12px;
  color: var(--muted-foreground);
}

.typing-indicator {
  display: inline-flex;
  gap: 4px;
}

.typing-indicator span {
  width: 8px;
  height: 8px;
  background: var(--muted-foreground);
  border-radius: 50%;
  animation: typing 1.4s infinite ease-in-out;
}

.typing-indicator span:nth-child(1) { animation-delay: -0.32s; }
.typing-indicator span:nth-child(2) { animation-delay: -0.16s; }

@keyframes typing {
  0%, 80%, 100% { transform: scale(0); opacity: 0.5; }
  40% { transform: scale(1); opacity: 1; }
}

.collection-option {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

:deep(.el-select-dropdown__item) {
  padding: 8px 12px;
}

.settings-form {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.settings-form .el-form-item {
  margin-bottom: 20px;
}

.settings-footer {
  margin-top: auto;
  padding-top: 20px;
  border-top: 1px solid var(--border);
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

/* 思考过程样式 */
.reasoning-container {
  margin-bottom: 8px;
  background: var(--muted);
  border-radius: 8px;
  overflow: hidden;
}

.reasoning-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  cursor: pointer;
  font-size: 12px;
  color: var(--muted-foreground);
  transition: background var(--transition);
}

.reasoning-header:hover {
  background: rgba(0, 0, 0, 0.05);
}

.reasoning-content {
  padding: 12px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--muted-foreground);
  white-space: pre-wrap;
  word-break: break-word;
  border-top: 1px solid var(--border);
  background: rgba(255, 255, 255, 0.5);
}

/* 实时显示思考过程时的样式 */
.reasoning-live {
  margin-bottom: 12px;
  padding: 8px 12px;
  background: var(--muted);
  border-radius: 8px;
  font-size: 12px;
  color: var(--muted-foreground);
  max-height: 200px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-word;
}

/* 思考预算控制样式 */
.thinking-budget-control {
  width: 100%;
}

.thinking-budget-custom {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.thinking-budget-value {
  font-size: 12px;
  color: var(--muted-foreground);
}
</style>
