import { vi, describe, it, expect, beforeEach, Mock } from 'vitest'
import { chatApi, chatStreamService } from '@/api/chat'

// Mock the request module - return object with get method
vi.mock('@/api/request', () => {
  const mockGet = vi.fn()
  return {
    request: vi.fn(),
    axiosInstance: {
      get: mockGet
    }
  }
})

import { request, axiosInstance } from '@/api/request'

describe('Chat API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('chatApi.query', () => {
    it('should send query request successfully', async () => {
      const mockResponse = {
        code: 200,
        data: {
          answer: 'Test answer',
          sources: [],
          conversationId: 'conv-123',
          messageId: 'msg-123'
        }
      }
      ;(request as Mock).mockResolvedValue(mockResponse)

      const result = await chatApi.query({
        collectionIds: ['col-1'],
        query: 'test question'
      })

      expect(request).toHaveBeenCalledWith('/chat/query', {
        method: 'POST',
        data: {
          collectionIds: ['col-1'],
          query: 'test question'
        }
      })
      expect(result.data.answer).toBe('Test answer')
    })
  })

  describe('chatApi.listSessions', () => {
    it('should list sessions with default params', async () => {
      const mockResponse = {
        code: 200,
        data: {
          items: [
            { id: 'session-1', title: 'Session 1' },
            { id: 'session-2', title: 'Session 2' }
          ],
          total: 2
        }
      }
      ;(request as Mock).mockResolvedValue(mockResponse)

      const result = await chatApi.listSessions()

      expect(request).toHaveBeenCalledWith('/chat/sessions', {
        method: 'GET',
        params: {
          page: 0,
          pageSize: 20,
          collectionId: undefined,
          status: undefined
        }
      })
      expect(result.data.items).toHaveLength(2)
    })

    it('should list sessions with custom params', async () => {
      const mockResponse = {
        code: 200,
        data: { items: [], total: 0 }
      }
      ;(request as Mock).mockResolvedValue(mockResponse)

      await chatApi.listSessions({
        page: 1,
        pageSize: 10,
        collectionId: 'col-123',
        status: 'active'
      })

      expect(request).toHaveBeenCalledWith('/chat/sessions', {
        method: 'GET',
        params: {
          page: 1,
          pageSize: 10,
          collectionId: 'col-123',
          status: 'active'
        }
      })
    })
  })

  describe('chatApi.getSession', () => {
    it('should get session by id', async () => {
      const mockSession = {
        id: 'session-123',
        title: 'Test Session',
        messages: []
      }
      ;(request as Mock).mockResolvedValue({
        code: 200,
        data: mockSession
      })

      const result = await chatApi.getSession('session-123')

      expect(request).toHaveBeenCalledWith('/chat/sessions/session-123')
      expect(result.data.id).toBe('session-123')
    })
  })

  describe('chatApi.createSession', () => {
    it('should create session successfully', async () => {
      const mockSession = {
        id: 'new-session-123',
        title: 'New Session'
      }
      ;(request as Mock).mockResolvedValue({
        code: 200,
        data: mockSession
      })

      const result = await chatApi.createSession({
        title: 'New Session',
        collectionIds: ['col-1']
      })

      expect(request).toHaveBeenCalledWith('/chat/sessions', {
        method: 'POST',
        data: {
          title: 'New Session',
          collectionIds: ['col-1']
        }
      })
      expect(result.data.id).toBe('new-session-123')
    })
  })

  describe('chatApi.deleteSession', () => {
    it('should delete session successfully', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200 })

      await chatApi.deleteSession('session-123')

      expect(request).toHaveBeenCalledWith('/chat/sessions/session-123', {
        method: 'DELETE'
      })
    })
  })

  describe('chatApi.getSessionMessages', () => {
    it('should get session messages', async () => {
      const mockMessages = {
        items: [
          { id: 'msg-1', role: 'user', content: 'Hello' },
          { id: 'msg-2', role: 'assistant', content: 'Hi there!' }
        ]
      }
      ;(request as Mock).mockResolvedValue({
        code: 200,
        data: mockMessages
      })

      const result = await chatApi.getSessionMessages('session-123')

      expect(request).toHaveBeenCalledWith('/chat/sessions/session-123/messages')
      expect(result.data.items).toHaveLength(2)
    })
  })

  describe('chatApi.feedbackMessage', () => {
    it('should send positive feedback', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200 })

      await chatApi.feedbackMessage('session-123', 'msg-456', 'like')

      expect(request).toHaveBeenCalledWith('/chat/sessions/session-123/messages/msg-456/feedback', {
        method: 'POST',
        params: { feedback: 'like', comment: undefined }
      })
    })

    it('should send negative feedback with comment', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200 })

      await chatApi.feedbackMessage('session-123', 'msg-456', 'dislike', 'Not helpful')

      expect(request).toHaveBeenCalledWith('/chat/sessions/session-123/messages/msg-456/feedback', {
        method: 'POST',
        params: { feedback: 'dislike', comment: 'Not helpful' }
      })
    })
  })

  describe('chatApi.exportSession', () => {
    it('should export session as markdown', async () => {
      const mockBlob = new Blob(['# Export'], { type: 'text/markdown' })
      ;(axiosInstance.get as any).mockResolvedValue({
        data: mockBlob
      })

      await chatApi.exportSession('session-123', 'markdown')

      expect(axiosInstance.get).toHaveBeenCalledWith('/chat/sessions/session-123/export', {
        params: { format: 'markdown' },
        responseType: 'blob'
      })
    })

    it('should export session as json', async () => {
      const mockBlob = new Blob(['{"messages":[]}'], { type: 'application/json' })
      ;(axiosInstance.get as any).mockResolvedValue({
        data: mockBlob
      })

      await chatApi.exportSession('session-123', 'json')

      expect(axiosInstance.get).toHaveBeenCalledWith('/chat/sessions/session-123/export', {
        params: { format: 'json' },
        responseType: 'blob'
      })
    })
  })
})

describe('ChatStreamService', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.setItem('token', 'test-token')
  })

  describe('streamQuery', () => {
    it('should handle successful stream response', async () => {
      const mockStream = new ReadableStream({
        start(controller) {
          controller.enqueue(new TextEncoder().encode('data: {"type":"token","content":"Hello"}\n'))
          controller.enqueue(new TextEncoder().encode('data: {"type":"done","messageId":"msg-1"}\n'))
          controller.close()
        }
      })

      const mockFetch = vi.fn().mockResolvedValue({
        ok: true,
        body: mockStream
      })
      global.fetch = mockFetch

      const callbacks = {
        onToken: vi.fn(),
        onSource: vi.fn(),
        onDone: vi.fn(),
        onError: vi.fn()
      }

      await chatStreamService.streamQuery(
        { collectionIds: ['col-1'], query: 'test' },
        callbacks
      )

      expect(callbacks.onToken).toHaveBeenCalledWith('Hello')
      expect(callbacks.onDone).toHaveBeenCalled()
    })

    it('should handle stream error', async () => {
      const mockStream = new ReadableStream({
        start(controller) {
          controller.enqueue(new TextEncoder().encode('data: {"type":"error","message":"Server error"}\n'))
          controller.close()
        }
      })

      const mockFetch = vi.fn().mockResolvedValue({
        ok: true,
        body: mockStream
      })
      global.fetch = mockFetch

      const callbacks = {
        onToken: vi.fn(),
        onSource: vi.fn(),
        onDone: vi.fn(),
        onError: vi.fn()
      }

      await chatStreamService.streamQuery(
        { collectionIds: ['col-1'], query: 'test' },
        callbacks
      )

      expect(callbacks.onError).toHaveBeenCalledWith('Server error')
    })

    it('should abort request when abort is called', async () => {
      const mockAbort = vi.fn()
      vi.stubGlobal('AbortController', vi.fn().mockImplementation(() => ({
        signal: { aborted: false },
        abort: mockAbort
      })))

      const mockFetch = vi.fn().mockImplementation(() => new Promise(() => {}))
      global.fetch = mockFetch

      chatStreamService.streamQuery(
        { collectionIds: ['col-1'], query: 'test' },
        { onToken: vi.fn(), onSource: vi.fn(), onDone: vi.fn(), onError: vi.fn() }
      )

      chatStreamService.abort()

      expect(mockAbort).toHaveBeenCalled()
    })
  })
})
