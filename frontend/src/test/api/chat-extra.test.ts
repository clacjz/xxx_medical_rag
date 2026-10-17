import { vi, describe, it, expect, beforeEach, Mock } from 'vitest'
import { chatApi, chatStreamService } from '@/api/chat'

vi.mock('@/api/request', () => {
  return {
    request: vi.fn(),
    axiosInstance: {
      get: vi.fn(),
      post: vi.fn(),
      put: vi.fn(),
      delete: vi.fn()
    }
  }
})

import { request, axiosInstance } from '@/api/request'

describe('Chat API - Extended Coverage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('updateSession', () => {
    it('should PUT partial session data', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200, data: {} })

      await chatApi.updateSession('s-1', { title: 'New Title' })

      expect(request).toHaveBeenCalledWith('/chat/sessions/s-1', {
        method: 'PUT',
        data: { title: 'New Title' }
      })
    })
  })

  describe('renameSession', () => {
    it('should PUT title as query param', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200, data: {} })

      await chatApi.renameSession('s-1', 'New Title')

      expect(request).toHaveBeenCalledWith('/chat/sessions/s-1/title', {
        method: 'PUT',
        params: { title: 'New Title' }
      })
    })
  })

  describe('updateSessionCollections', () => {
    it('should PUT collectionIds array', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200, data: {} })

      await chatApi.updateSessionCollections('s-1', ['c-1', 'c-2', 'c-3'])

      expect(request).toHaveBeenCalledWith('/chat/sessions/s-1/collections', {
        method: 'PUT',
        data: ['c-1', 'c-2', 'c-3']
      })
    })

    it('should send empty array when no collections', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200, data: {} })

      await chatApi.updateSessionCollections('s-1', [])

      expect(request).toHaveBeenCalledWith('/chat/sessions/s-1/collections', {
        method: 'PUT',
        data: []
      })
    })
  })

  describe('createConversation', () => {
    it('should POST with collectionId wrapped in array', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200, data: { id: 's-1' } })

      await chatApi.createConversation({ title: 'New', collectionId: 'c-1' })

      expect(request).toHaveBeenCalledWith('/chat/sessions', {
        method: 'POST',
        data: {
          title: 'New',
          collectionIds: ['c-1']
        }
      })
    })
  })

  describe('deleteConversation', () => {
    it('should DELETE /chat/sessions/{id}', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200 })

      await chatApi.deleteConversation('s-1')

      expect(request).toHaveBeenCalledWith('/chat/sessions/s-1', { method: 'DELETE' })
    })
  })

  describe('getConversationMessages', () => {
    it('should GET messages for a conversation', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200, data: { items: [] } })

      await chatApi.getConversationMessages('s-1')

      expect(request).toHaveBeenCalledWith('/chat/sessions/s-1/messages')
    })
  })

  describe('deleteMessage', () => {
    it('should DELETE /chat/sessions/{sid}/messages/{mid}', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200 })

      await chatApi.deleteMessage('s-1', 'm-1')

      expect(request).toHaveBeenCalledWith('/chat/sessions/s-1/messages/m-1', {
        method: 'DELETE'
      })
    })
  })

  describe('regenerateResponse', () => {
    it('should POST to regenerate endpoint', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200, data: { id: 'm-1' } })

      await chatApi.regenerateResponse('s-1', 'm-1')

      expect(request).toHaveBeenCalledWith('/chat/sessions/s-1/messages/m-1/regenerate', {
        method: 'POST'
      })
    })
  })

  describe('exportSession', () => {
    it('should export as PDF with blob responseType', async () => {
      const blob = new Blob(['pdf data'], { type: 'application/pdf' })
      ;(axiosInstance.get as any).mockResolvedValue({ data: blob })

      await chatApi.exportSession('s-1', 'pdf')

      expect(axiosInstance.get).toHaveBeenCalledWith('/chat/sessions/s-1/export', {
        params: { format: 'pdf' },
        responseType: 'blob'
      })
    })

    it('should default to markdown when format is not specified', async () => {
      const blob = new Blob(['# md'], { type: 'text/markdown' })
      ;(axiosInstance.get as any).mockResolvedValue({ data: blob })

      await chatApi.exportSession('s-1')

      expect(axiosInstance.get).toHaveBeenCalledWith('/chat/sessions/s-1/export', {
        params: { format: 'markdown' },
        responseType: 'blob'
      })
    })
  })

  describe('chatStreamService.abort', () => {
    it('should be a no-op when no stream is active', () => {
      expect(() => chatStreamService.abort()).not.toThrow()
    })
  })
})
