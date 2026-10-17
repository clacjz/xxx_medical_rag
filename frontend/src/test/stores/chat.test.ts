import { describe, it, expect, beforeEach, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useChatStore } from '@/stores/chat'
import { chatApi, chatStreamService } from '@/api/chat'
import type { ChatSession, ChatMessage } from '@/types'

vi.mock('@/api/chat', () => ({
  chatApi: {
    listSessions: vi.fn(),
    getSession: vi.fn(),
    createSession: vi.fn(),
    updateSession: vi.fn(),
    deleteSession: vi.fn(),
    getSessionMessages: vi.fn(),
    deleteMessage: vi.fn(),
    feedbackMessage: vi.fn()
  },
  chatStreamService: {
    streamQuery: vi.fn(),
    abort: vi.fn()
  }
}))

describe('useChatStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  describe('initial state', () => {
    it('should have empty sessions and messages', () => {
      const store = useChatStore()
      expect(store.sessions).toEqual([])
      expect(store.messages).toEqual([])
      expect(store.currentSession).toBeNull()
    })

    it('should have empty chat state', () => {
      const store = useChatStore()
      expect(store.chatState.answer).toBe('')
      expect(store.chatState.isGenerating).toBe(false)
      expect(store.chatState.error).toBeNull()
    })

    it('should have hasActiveSession false initially', () => {
      const store = useChatStore()
      expect(store.hasActiveSession).toBe(false)
    })

    it('should have noExpansion false initially', () => {
      const store = useChatStore()
      expect(store.noExpansion).toBe(false)
    })
  })

  describe('computed', () => {
    it('recentSessions returns up to 10 most recent', () => {
      const store = useChatStore()
      const now = Date.now()
      store.sessions = [
        { id: 's1', title: 'A', updatedAt: new Date(now - 10000).toISOString() } as ChatSession,
        { id: 's2', title: 'B', updatedAt: new Date(now).toISOString() } as ChatSession,
        { id: 's3', title: 'C', updatedAt: new Date(now - 5000).toISOString() } as ChatSession
      ]

      const recent = store.recentSessions
      expect(recent[0].id).toBe('s2') // 最新
      expect(recent[1].id).toBe('s3')
      expect(recent[2].id).toBe('s1')
    })

    it('hasActiveSession reflects currentSession', () => {
      const store = useChatStore()
      expect(store.hasActiveSession).toBe(false)
      store.currentSession = { id: 's-1', title: 'T' } as ChatSession
      expect(store.hasActiveSession).toBe(true)
    })
  })

  describe('toggleNoExpansion', () => {
    it('should flip noExpansion value', () => {
      const store = useChatStore()
      expect(store.noExpansion).toBe(false)
      store.toggleNoExpansion()
      expect(store.noExpansion).toBe(true)
      store.toggleNoExpansion()
      expect(store.noExpansion).toBe(false)
    })
  })

  describe('setSelectedCollections', () => {
    it('should set selectedCollectionIds', () => {
      const store = useChatStore()
      store.setSelectedCollections(['c-1', 'c-2'])
      expect(store.selectedCollectionIds).toEqual(['c-1', 'c-2'])
    })

    it('should clear selection with empty array', () => {
      const store = useChatStore()
      store.setSelectedCollections(['c-1'])
      store.setSelectedCollections([])
      expect(store.selectedCollectionIds).toEqual([])
    })
  })

  describe('clearCurrentSession', () => {
    it('should clear currentSession', () => {
      const store = useChatStore()
      store.currentSession = { id: 's-1', title: 'T' } as ChatSession
      store.clearCurrentSession()
      expect(store.currentSession).toBeNull()
    })
  })

  describe('resetChatState', () => {
    it('should reset answer, reasoning, error', () => {
      const store = useChatStore()
      store.chatState.answer = 'some answer'
      store.chatState.reasoning = 'reasoning'
      store.chatState.error = 'err'
      store.chatState.sources = [{ id: 'src', content: 'c', score: 0.9 }] as any
      store.chatState.isGenerating = true

      store.resetChatState()

      expect(store.chatState.answer).toBe('')
      expect(store.chatState.reasoning).toBe('')
      expect(store.chatState.error).toBeNull()
      expect(store.chatState.sources).toEqual([])
      expect(store.chatState.isGenerating).toBe(false)
    })
  })

  describe('loadSessions', () => {
    it('should load sessions and update pagination', async () => {
      vi.mocked(chatApi.listSessions).mockResolvedValue({
        code: 200,
        data: {
          items: [{ id: 's-1', title: 'A', updatedAt: '2026-01-01' } as ChatSession],
          total: 1
        }
      } as any)

      const store = useChatStore()
      await store.loadSessions({ page: 0, pageSize: 20 })

      expect(chatApi.listSessions).toHaveBeenCalled()
      expect(store.sessions).toHaveLength(1)
      expect(store.pagination.total).toBe(1)
    })

    it('should set sessionsLoading during call', async () => {
      let loadingDuringCall = false
      vi.mocked(chatApi.listSessions).mockImplementation(async () => {
        loadingDuringCall = (useChatStore() as any).sessionsLoading
        return { code: 200, data: { items: [], total: 0 } } as any
      })

      await useChatStore().loadSessions()

      expect(loadingDuringCall).toBe(true)
    })
  })

  describe('createSession', () => {
    it('should create and append to sessions', async () => {
      const newSession = { id: 's-new', title: 'New' } as ChatSession
      vi.mocked(chatApi.createSession).mockResolvedValue({ code: 200, data: newSession } as any)

      const store = useChatStore()
      const result = await store.createSession(['c-1'], 'New')

      expect(result).toEqual(newSession)
      expect(store.sessions).toContainEqual(newSession)
      expect(store.currentSession).toEqual(newSession)
    })
  })

  describe('deleteSession', () => {
    it('should call API and remove from list', async () => {
      vi.mocked(chatApi.deleteSession).mockResolvedValue({ code: 200 } as any)
      const store = useChatStore()
      store.sessions = [{ id: 's-1', title: 'A' } as ChatSession, { id: 's-2', title: 'B' } as ChatSession]

      await store.deleteSession('s-1')

      expect(chatApi.deleteSession).toHaveBeenCalledWith('s-1')
      expect(store.sessions.find(s => s.id === 's-1')).toBeUndefined()
    })
  })

  describe('loadMessages', () => {
    it('should load and store messages', async () => {
      const messages = [
        { id: 'm-1', role: 'user', content: 'Hi' } as ChatMessage,
        { id: 'm-2', role: 'assistant', content: 'Hello' } as ChatMessage
      ]
      vi.mocked(chatApi.getSessionMessages).mockResolvedValue({ code: 200, data: { items: messages } } as any)

      const store = useChatStore()
      await store.loadMessages('s-1')

      expect(chatApi.getSessionMessages).toHaveBeenCalledWith('s-1')
      expect(store.messages).toHaveLength(2)
    })
  })

  describe('selectSession', () => {
    it('should set currentSession', () => {
      const store = useChatStore()
      const session = { id: 's-1', title: 'A' } as ChatSession
      store.selectSession(session)
      expect(store.currentSession).toEqual(session)
    })
  })

  describe('abortStream', () => {
    it('should delegate to chatStreamService.abort', () => {
      const store = useChatStore()
      store.abortStream()
      expect(chatStreamService.abort).toHaveBeenCalled()
    })

    it('should set isGenerating to false', () => {
      const store = useChatStore()
      store.chatState.isGenerating = true
      store.abortStream()
      expect(store.chatState.isGenerating).toBe(false)
    })
  })
})
