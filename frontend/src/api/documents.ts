import { request, axiosInstance } from './request'
import type { Result, Document, PageResult, ProcessStatusDTO, DocumentProcessDTO, ChunkDTO, DocumentTextDTO, DocumentChunkingStrategy } from '@/types'

export interface OcrResultDTO {
  documentId: string
  totalPages: number
  currentPage: number
  ocrText: string
  htmlTable: string
  status: 'SUCCESS' | 'FAILED' | 'EXCEEDED' | 'PARTIAL'
  errorMessage?: string
}

export const documentsApi = {
  list: (params?: { page?: number; pageSize?: number; collectionId?: string; status?: string }) => {
    return request<PageResult<Document>>('/documents', { method: 'GET', params })
  },

  getById: (id: string) => {
    return request<Document>(`/documents/${id}`)
  },

  upload: (file: File, collectionId: string, onProgress?: (percent: number) => void, permissionType?: string, allowedUserIds?: string[]) => {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('collectionId', collectionId)
    if (permissionType) {
      formData.append('permissionType', permissionType)
    }
    if (allowedUserIds && allowedUserIds.length > 0) {
      allowedUserIds.forEach(userId => formData.append('allowedUserIds', userId))
    }

    return axiosInstance.post<Result<Document>>('/documents/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      },
      onUploadProgress: (progressEvent) => {
        if (progressEvent.total && onProgress) {
          const percent = Math.round((progressEvent.loaded * 100) / progressEvent.total)
          onProgress(percent)
        }
      }
    })
  },

  delete: (id: string) => {
    return request(`/documents/${id}`, { method: 'DELETE' })
  },

  retry: (id: string) => {
    return request<Document>(`/documents/${id}/retry`, { method: 'POST' })
  },

  processDocument: (id: string, strategy: DocumentChunkingStrategy = 'fixed', collectionId?: string) => {
    return request<DocumentProcessDTO>(`/documents/${id}/process`, {
      method: 'POST',
      params: { strategy, collectionId }
    })
  },

  getProcessStatus: (id: string) => {
    return request<ProcessStatusDTO>(`/documents/${id}/process/status`)
  },

  getProcessResult: (id: string) => {
    return request<DocumentProcessDTO>(`/documents/${id}/process/result`)
  },

  getChunks: (id: string, strategy: DocumentChunkingStrategy = 'fixed') => {
    return request<ChunkDTO[]>(`/documents/${id}/chunks`, {
      method: 'GET',
      params: { strategy }
    })
  },

  getDocumentText: (id: string, truncate: boolean = true, maxLength: number = 50000) => {
    return request<DocumentTextDTO>(`/documents/${id}/text`, {
      method: 'GET',
      params: { truncate, maxLength }
    })
  },

  getOcrResult: (id: string, page: number = 1) => {
    return request<OcrResultDTO>(`/documents/${id}/ocr`, {
      method: 'GET',
      params: { page }
    })
  },

  getPdfInfo: (id: string) => {
    return request<{ totalPages: number }>(`/documents/${id}/pdf/info`, { method: 'GET' })
  },

  uploadForOcr: (file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    return axiosInstance.post<Result<{ id: string; fileName: string; filePath: string; fileType: string; fileSize: number }>>('/documents/upload-for-ocr', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
  },

  ocrPreview: (documentId: string, filePath: string, page: number = 1) => {
    return request<OcrResultDTO>('/documents/ocr-preview', {
      method: 'GET',
      params: { documentId, filePath, page }
    })
  },

  /**
   * 保存OCR结果到知识库（向量化存储到Qdrant）
   * @param data 包含 collectionId, documentId, title, markdownContent, strategy, updateContent
   */
  saveOcrMarkdown: (data: {
    collectionId: string
    documentId?: string
    title: string
    markdownContent: string
    strategy?: string
    updateContent?: boolean
  }) => {
    return request<{ documentId: string; collectionId: string; storedChunks: number; title: string }>('/documents/ocr/save', {
      method: 'POST',
      data
    })
  },

  /**
   * 取消OCR保存，清理已生成的向量
   */
  cancelOcrSave: (documentId: string, collectionId: string) => {
    return request(`/documents/${documentId}/ocr-save`, {
      method: 'DELETE',
      params: { collectionId }
    })
  }
}
