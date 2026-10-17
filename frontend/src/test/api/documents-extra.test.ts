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

describe('Documents API - Extended Coverage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('getPdfInfo', () => {
    it('should GET /documents/{id}/pdf/info', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200, data: { totalPages: 5 } })

      const result = await documentsApi.getPdfInfo('d-1')

      expect(request).toHaveBeenCalledWith('/documents/d-1/pdf/info', { method: 'GET' })
      expect(result.data?.totalPages).toBe(5)
    })
  })

  describe('uploadForOcr', () => {
    it('should POST FormData to /documents/upload-for-ocr', async () => {
      const file = new File(['pdf data'], 'doc.pdf', { type: 'application/pdf' })
      ;(axiosInstance.post as any).mockResolvedValue({
        data: { code: 200, data: { id: 'd-1', fileName: 'doc.pdf' } }
      })

      await documentsApi.uploadForOcr(file)

      expect(axiosInstance.post).toHaveBeenCalledWith(
        '/documents/upload-for-ocr',
        expect.any(FormData),
        expect.objectContaining({ headers: { 'Content-Type': 'multipart/form-data' } })
      )
    })
  })

  describe('ocrPreview', () => {
    it('should call /documents/ocr-preview with all params', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200, data: { status: 'SUCCESS' } })

      await documentsApi.ocrPreview('d-1', '/path/file.pdf', 3)

      expect(request).toHaveBeenCalledWith('/documents/ocr-preview', {
        method: 'GET',
        params: { documentId: 'd-1', filePath: '/path/file.pdf', page: 3 }
      })
    })

    it('should default page to 1', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200, data: {} })

      await documentsApi.ocrPreview('d-1', '/path/file.pdf')

      expect(request).toHaveBeenCalledWith('/documents/ocr-preview', {
        method: 'GET',
        params: { documentId: 'd-1', filePath: '/path/file.pdf', page: 1 }
      })
    })
  })

  describe('saveOcrMarkdown', () => {
    it('should POST to /documents/ocr/save', async () => {
      ;(request as Mock).mockResolvedValue({
        code: 200,
        data: { documentId: 'd-1', collectionId: 'c-1', storedChunks: 5, title: 'T' }
      })

      await documentsApi.saveOcrMarkdown({
        collectionId: 'c-1',
        title: 'T',
        markdownContent: '# hi',
        strategy: 'fixed',
        updateContent: false
      })

      expect(request).toHaveBeenCalledWith('/documents/ocr/save', {
        method: 'POST',
        data: {
          collectionId: 'c-1',
          title: 'T',
          markdownContent: '# hi',
          strategy: 'fixed',
          updateContent: false
        }
      })
    })

    it('should allow minimal payload without strategy/updateContent', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200, data: {} })

      await documentsApi.saveOcrMarkdown({
        collectionId: 'c-1',
        title: 'T',
        markdownContent: 'x'
      })

      expect(request).toHaveBeenCalledWith('/documents/ocr/save', {
        method: 'POST',
        data: {
          collectionId: 'c-1',
          title: 'T',
          markdownContent: 'x'
        }
      })
    })
  })

  describe('cancelOcrSave', () => {
    it('should DELETE with collectionId as query param', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200 })

      await documentsApi.cancelOcrSave('d-1', 'c-1')

      expect(request).toHaveBeenCalledWith('/documents/d-1/ocr-save', {
        method: 'DELETE',
        params: { collectionId: 'c-1' }
      })
    })
  })
})
