import { vi, describe, it, expect, beforeEach, Mock } from 'vitest'
import { documentsApi } from '@/api/documents'

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

describe('Documents API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('list', () => {
    it('should call /documents with default params', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200, data: { items: [], total: 0 } })

      await documentsApi.list()

      expect(request).toHaveBeenCalledWith('/documents', {
        method: 'GET',
        params: undefined
      })
    })

    it('should pass filters', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200, data: { items: [] } })

      await documentsApi.list({ page: 1, pageSize: 50, collectionId: 'col-1', status: 'active' })

      expect(request).toHaveBeenCalledWith('/documents', {
        method: 'GET',
        params: { page: 1, pageSize: 50, collectionId: 'col-1', status: 'active' }
      })
    })
  })

  describe('getById', () => {
    it('should call /documents/{id}', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200, data: { id: 'd-1' } })

      await documentsApi.getById('d-1')

      expect(request).toHaveBeenCalledWith('/documents/d-1')
    })
  })

  describe('upload', () => {
    it('should build FormData and call POST', async () => {
      const file = new File(['content'], 'test.pdf', { type: 'application/pdf' })
      ;(axiosInstance.post as any).mockResolvedValue({ data: { code: 200, data: { id: 'd-1' } } })

      await documentsApi.upload(file, 'col-1')

      expect(axiosInstance.post).toHaveBeenCalledWith(
        '/documents/upload',
        expect.any(FormData),
        expect.objectContaining({
          headers: { 'Content-Type': 'multipart/form-data' }
        })
      )
    })

    it('should call onProgress callback', async () => {
      const file = new File(['x'], 'a.txt', { type: 'text/plain' })
      const onProgress = vi.fn()
      ;(axiosInstance.post as any).mockImplementation((_url: string, _form: FormData, config: any) => {
        // 模拟进度事件
        config.onUploadProgress({ loaded: 50, total: 100 })
        return Promise.resolve({ data: { code: 200, data: {} } })
      })

      await documentsApi.upload(file, 'col-1', onProgress)

      expect(onProgress).toHaveBeenCalledWith(50)
    })

    it('should not call onProgress when total is 0', async () => {
      const file = new File(['x'], 'a.txt', { type: 'text/plain' })
      const onProgress = vi.fn()
      ;(axiosInstance.post as any).mockImplementation((_url: string, _form: FormData, config: any) => {
        config.onUploadProgress({ loaded: 0, total: 0 })
        return Promise.resolve({ data: { code: 200, data: {} } })
      })

      await documentsApi.upload(file, 'col-1', onProgress)

      expect(onProgress).not.toHaveBeenCalled()
    })

    it('should append permissionType and allowedUserIds when provided', async () => {
      const file = new File(['x'], 'a.txt', { type: 'text/plain' })
      ;(axiosInstance.post as any).mockResolvedValue({ data: { code: 200, data: {} } })

      await documentsApi.upload(file, 'col-1', undefined, 'PRIVATE', ['u-1', 'u-2'])

      const formDataArg = (axiosInstance.post as any).mock.calls[0][1] as FormData
      expect(formDataArg.get('permissionType')).toBe('PRIVATE')
      expect(formDataArg.getAll('allowedUserIds')).toEqual(['u-1', 'u-2'])
    })
  })

  describe('delete', () => {
    it('should call DELETE /documents/{id}', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200 })

      await documentsApi.delete('d-1')

      expect(request).toHaveBeenCalledWith('/documents/d-1', { method: 'DELETE' })
    })
  })

  describe('retry', () => {
    it('should POST to /documents/{id}/retry', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200, data: {} })

      await documentsApi.retry('d-1')

      expect(request).toHaveBeenCalledWith('/documents/d-1/retry', { method: 'POST' })
    })
  })

  describe('processDocument', () => {
    it('should POST with default strategy fixed', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200, data: {} })

      await documentsApi.processDocument('d-1')

      expect(request).toHaveBeenCalledWith('/documents/d-1/process', {
        method: 'POST',
        params: { strategy: 'fixed', collectionId: undefined }
      })
    })

    it('should pass custom strategy and collectionId', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200, data: {} })

      await documentsApi.processDocument('d-1', 'semantic', 'col-1')

      expect(request).toHaveBeenCalledWith('/documents/d-1/process', {
        method: 'POST',
        params: { strategy: 'semantic', collectionId: 'col-1' }
      })
    })
  })

  describe('getProcessStatus', () => {
    it('should call /documents/{id}/process/status', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200, data: { status: 'PENDING' } })

      await documentsApi.getProcessStatus('d-1')

      expect(request).toHaveBeenCalledWith('/documents/d-1/process/status')
    })
  })

  describe('getProcessResult', () => {
    it('should call /documents/{id}/process/result', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200, data: {} })

      await documentsApi.getProcessResult('d-1')

      expect(request).toHaveBeenCalledWith('/documents/d-1/process/result')
    })
  })

  describe('getChunks', () => {
    it('should call /documents/{id}/chunks with default strategy', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200, data: [] })

      await documentsApi.getChunks('d-1')

      expect(request).toHaveBeenCalledWith('/documents/d-1/chunks', {
        method: 'GET',
        params: { strategy: 'fixed' }
      })
    })

    it('should pass custom strategy', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200, data: [] })

      await documentsApi.getChunks('d-1', 'paragraph')

      expect(request).toHaveBeenCalledWith('/documents/d-1/chunks', {
        method: 'GET',
        params: { strategy: 'paragraph' }
      })
    })
  })

  describe('getDocumentText', () => {
    it('should call /documents/{id}/text with default truncate and maxLength', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200, data: {} })

      await documentsApi.getDocumentText('d-1')

      expect(request).toHaveBeenCalledWith('/documents/d-1/text', {
        method: 'GET',
        params: { truncate: true, maxLength: 50000 }
      })
    })

    it('should pass custom maxLength', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200, data: {} })

      await documentsApi.getDocumentText('d-1', false, 1000)

      expect(request).toHaveBeenCalledWith('/documents/d-1/text', {
        method: 'GET',
        params: { truncate: false, maxLength: 1000 }
      })
    })
  })

  describe('getOcrResult', () => {
    it('should call /documents/{id}/ocr with default page 1', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200, data: { status: 'SUCCESS' } })

      await documentsApi.getOcrResult('d-1')

      expect(request).toHaveBeenCalledWith('/documents/d-1/ocr', {
        method: 'GET',
        params: { page: 1 }
      })
    })

    it('should pass custom page', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200, data: {} })

      await documentsApi.getOcrResult('d-1', 5)

      expect(request).toHaveBeenCalledWith('/documents/d-1/ocr', {
        method: 'GET',
        params: { page: 5 }
      })
    })
  })
})
