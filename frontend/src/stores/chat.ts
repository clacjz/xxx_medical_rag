import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { chatApi, chatStreamService } from '@/api/chat'
import type {
  ChatSession,
  ChatMessage,
  SearchSource
} from '@/types'

export interface ChatState {
  answer: string
  reasoning: string
  sources: SearchSource[]
  isGenerating: boolean
  error: string | null
}

export const useChatStore = defineStore('chat', () => {
  const sessions = ref<ChatSession[]>([])
  const currentSession = ref<ChatSession | null>(null)
  const messages = ref<ChatMessage[]>([])
  
  const chatState = ref<ChatState>({
    answer: '',
    reasoning: '',
    sources: [],
    isGenerating: false,
    error: null
  })

  const selectedCollectionIds = ref<string[]>([])
  const noExpansion = ref(false)

  const loading = ref(false)
  const messagesLoading = ref(false)
  const sessionsLoading = ref(false)

  const pagination = ref({
    page: 0,
    pageSize: 20,
    total: 0,
    totalPages: 0
  })

  const recentSessions = computed(() => {
    return [...sessions.value]
      .sort((a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime())
      .slice(0, 10)
  })

  const hasActiveSession = computed(() => currentSession.value !== null)

  const loadSessions = async (params?: { page?: number; pageSize?: number; collectionId?: string }) => {
    sessionsLoading.value = true
    try {
      const res = await chatApi.listSessions({
        page: params?.page ?? pagination.value.page,
        pageSize: params?.pageSize ?? pagination.value.pageSize,
        collectionId: params?.collectionId
      })
      
      if (res.code === 200 && res.data) {
        sessions.value = res.data.items
        pagination.value.total = res.data.total
      }
    } catch (error) {
      console.error('Failed to load sessions:', error)
      throw error
    } finally {
      sessionsLoading.value = false
    }
  }

  const loadSession = async (sessionId: string) => {
    loading.value = true
    try {
      const res = await chatApi.getSession(sessionId)
      if (res.code === 200 && res.data) {
        currentSession.value = res.data
        selectedCollectionIds.value = res.data.collectionIds
        return res.data
      }
      return null
    } catch (error) {
      console.error('Failed to load session:', error)
      throw error
    } finally {
      loading.value = false
    }
  }

  const createSession = async (collectionIds: string[], title?: string) => {
    try {
      const res = await chatApi.createSession({ collectionIds, title })
      if (res.code === 200 && res.data) {
        sessions.value.unshift(res.data)
        currentSession.value = res.data
        selectedCollectionIds.value = collectionIds
        messages.value = []
        return res.data
      }
      return null
    } catch (error) {
      console.error('Failed to create session:', error)
      throw error
    }
  }

  const updateSession = async (sessionId: string, data: Partial<ChatSession>) => {
    try {
      const res = await chatApi.updateSession(sessionId, data)
      if (res.code === 200 && res.data) {
        const index = sessions.value.findIndex(s => s.id === sessionId)
        if (index !== -1) {
          sessions.value[index] = res.data
        }
        if (currentSession.value?.id === sessionId) {
          currentSession.value = res.data
        }
        return res.data
      }
      return null
    } catch (error) {
      console.error('Failed to update session:', error)
      throw error
    }
  }

  const deleteSession = async (sessionId: string) => {
    try {
      const res = await chatApi.deleteSession(sessionId)
      if (res.code === 200) {
        sessions.value = sessions.value.filter(s => s.id !== sessionId)
        if (currentSession.value?.id === sessionId) {
          currentSession.value = null
          messages.value = []
        }
        return true
      }
      return false
    } catch (error) {
      console.error('Failed to delete session:', error)
      throw error
    }
  }

  const loadMessages = async (sessionId: string) => {
    messagesLoading.value = true
    try {
      const res = await chatApi.getSessionMessages(sessionId)
      if (res.code === 200 && res.data) {
        messages.value = res.data.items
      }
    } catch (error) {
      console.error('Failed to load messages:', error)
      throw error
    } finally {
      messagesLoading.value = false
    }
  }

  const selectSession = async (session: ChatSession) => {
    currentSession.value = session
    selectedCollectionIds.value = session.collectionIds
    await loadMessages(session.id)
  }

  const clearCurrentSession = () => {
    currentSession.value = null
    messages.value = []
    resetChatState()
  }

  const resetChatState = () => {
    chatState.value = {
      answer: '',
      reasoning: '',
      sources: [],
      isGenerating: false,
      error: null
    }
  }

  const sendMessage = async (query: string) => {
    if (!selectedCollectionIds.value.length) {
      chatState.value.error = '请先选择知识库'
      return null
    }

    chatState.value.isGenerating = true
    chatState.value.error = null
    chatState.value.answer = ''
    chatState.value.sources = []

    const userMessage: ChatMessage = {
      id: `temp-${Date.now()}`,
      sessionId: currentSession.value?.id || '',
      departmentId: '',
      role: 'user',
      content: query,
      createdAt: new Date().toISOString()
    }

    if (!currentSession.value) {
      const session = await createSession(selectedCollectionIds.value)
      if (session) {
        userMessage.sessionId = session.id
      }
    }

    messages.value.push(userMessage)

    try {
      if (currentSession.value?.id) {
        const res = await chatApi.query({
          sessionId: currentSession.value.id,
          query,
          collectionIds: selectedCollectionIds.value,
          noExpansion: noExpansion.value,
          topK: 5,
          similarityThreshold: 0.7
        })

        if (res.code === 200 && res.data) {
          const assistantMessage: ChatMessage = {
            id: res.data.messageId || `assistant-${Date.now()}`,
            sessionId: currentSession.value.id,
            departmentId: '',
            role: 'assistant',
            content: res.data.answer,
            sources: res.data.sources || [],
            tokenUsage: res.data.tokenUsage,
            generationTimeMs: res.data.generationTimeMs,
            createdAt: new Date().toISOString()
          }

          const tempIndex = messages.value.findIndex(m => m.id === userMessage.id)
          if (tempIndex !== -1) {
            messages.value[tempIndex] = userMessage
          }
          messages.value.push(assistantMessage)
        }
      }

      return { success: true }
    } catch (error) {
      chatState.value.error = '发送消息失败'
      throw error
    } finally {
      chatState.value.isGenerating = false
    }
  }

  const sendMessageStream = async (query: string) => {
    if (!selectedCollectionIds.value.length) {
      chatState.value.error = '请先选择知识库'
      return null
    }

    chatState.value.isGenerating = true
    chatState.value.error = null
    chatState.value.answer = ''
    chatState.value.sources = []

    const userMessage: ChatMessage = {
      id: `temp-${Date.now()}`,
      sessionId: currentSession.value?.id || '',
      departmentId: '',
      role: 'user',
      content: query,
      createdAt: new Date().toISOString()
    }

    if (!currentSession.value) {
      const session = await createSession(selectedCollectionIds.value)
      if (session) {
        userMessage.sessionId = session.id
      }
    }

    messages.value.push(userMessage)

    try {
      await chatStreamService.streamQuery(
        {
          sessionId: currentSession.value?.id,
          query,
          collectionIds: selectedCollectionIds.value,
          noExpansion: noExpansion.value,
          topK: 5,
          similarityThreshold: 0.7
        },
        {
          onToken: (content) => {
            chatState.value.answer += content
          },
          onReasoning: (content) => {
            chatState.value.reasoning = (chatState.value.reasoning || '') + content
          },
          onSource: (source) => {
            if (!chatState.value.sources.find(s => s.documentId === source.documentId)) {
              chatState.value.sources.push(source)
            }
          },
          onDone: (messageId, tokensUsed, generationTimeMs) => {
            const assistantMessage: ChatMessage = {
              id: messageId,
              sessionId: currentSession.value?.id || '',
              departmentId: '',
              role: 'assistant',
              content: chatState.value.answer,
              sources: [...chatState.value.sources],
              tokenUsage: tokensUsed ? {
                promptTokens: 0,
                completionTokens: tokensUsed,
                totalTokens: tokensUsed
              } : undefined,
              generationTimeMs,
              createdAt: new Date().toISOString()
            }
            
            const tempIndex = messages.value.findIndex(m => m.id === userMessage.id)
            if (tempIndex !== -1) {
              messages.value[tempIndex] = userMessage
            }
            messages.value.push(assistantMessage)
            
            resetChatState()
          },
          onError: (error) => {
            chatState.value.error = error
            chatState.value.isGenerating = false
          }
        }
      )

      return { success: true }
    } catch (error) {
      chatState.value.error = '流式发送消息失败'
      throw error
    }
  }

  const abortStream = () => {
    chatStreamService.abort()
    chatState.value.isGenerating = false
    chatState.value.error = '已取消'
  }

  const deleteMessage = async (messageId: string) => {
    if (!currentSession.value?.id) return false
    
    try {
      const res = await chatApi.deleteMessage(currentSession.value.id, messageId)
      if (res.code === 200) {
        messages.value = messages.value.filter(m => m.id !== messageId)
        return true
      }
      return false
    } catch (error) {
      console.error('Failed to delete message:', error)
      throw error
    }
  }

  const regenerateResponse = async (messageId: string) => {
    if (!currentSession.value?.id) return false
    
    try {
      const res = await chatApi.regenerateResponse(currentSession.value.id, messageId)
      if (res.code === 200 && res.data) {
        const index = messages.value.findIndex(m => m.id === messageId)
        if (index !== -1) {
          messages.value[index] = res.data
        }
        return true
      }
      return false
    } catch (error) {
      console.error('Failed to regenerate response:', error)
      throw error
    }
  }

  const feedbackMessage = async (messageId: string, feedback: 'like' | 'dislike', comment?: string) => {
    if (!currentSession.value?.id) return false
    
    try {
      const res = await chatApi.feedbackMessage(currentSession.value.id, messageId, feedback, comment)
      return res.code === 200
    } catch (error) {
      console.error('Failed to feedback message:', error)
      throw error
    }
  }

  const setSelectedCollections = (ids: string[]) => {
    selectedCollectionIds.value = ids
  }

  const toggleNoExpansion = () => {
    noExpansion.value = !noExpansion.value
  }

  return {
    sessions,
    currentSession,
    messages,
    chatState,
    selectedCollectionIds,
    noExpansion,
    loading,
    messagesLoading,
    sessionsLoading,
    pagination,
    recentSessions,
    hasActiveSession,
    loadSessions,
    loadSession,
    createSession,
    updateSession,
    deleteSession,
    loadMessages,
    selectSession,
    clearCurrentSession,
    resetChatState,
    sendMessage,
    sendMessageStream,
    abortStream,
    deleteMessage,
    regenerateResponse,
    feedbackMessage,
    setSelectedCollections,
    toggleNoExpansion
  }
})
