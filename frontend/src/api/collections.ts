import { request } from './request'
import type { 
  PageResult, 
  MultiCollection, 
  CollectionCreateRequest,
  CollectionUpdateRequest,
  CollectionConfig,
  CollectionDocument,
  CollectionMetrics,
  CollectionPermission,
  CollectionAccessCheckResult,
  CollectionPermissionType,
  CollectionPermissionLevel
} from '@/types'

export interface CollectionListParams {
  page?: number
  pageSize?: number
  keyword?: string
  type?: string
  status?: string
  sortBy?: string
  sortOrder?: 'asc' | 'desc'
}

export interface CollectionDocumentListParams {
  page?: number
  pageSize?: number
  status?: string
}

export interface CollectionMetricsParams {
  startDate?: string
  endDate?: string
  metricTypes?: string
}

export const collectionsApi = {
  list: (params?: CollectionListParams) => {
    return request<PageResult<MultiCollection>>('/collections', { 
      method: 'GET', 
      params: {
        page: params?.page ?? 0,
        pageSize: params?.pageSize ?? 20,
        keyword: params?.keyword,
        type: params?.type,
        status: params?.status,
        sortBy: params?.sortBy ?? 'createdAt',
        sortOrder: params?.sortOrder ?? 'desc'
      }
    })
  },

  getById: (id: string) => {
    return request<MultiCollection>(`/collections/${id}`)
  },

  create: (data: CollectionCreateRequest) => {
    return request<MultiCollection>('/collections', { 
      method: 'POST', 
      data 
    })
  },

  update: (id: string, data: CollectionUpdateRequest) => {
    return request<MultiCollection>(`/collections/${id}`, { 
      method: 'PUT', 
      data 
    })
  },

  delete: (id: string) => {
    return request(`/collections/${id}`, { method: 'DELETE' })
  },

  archive: (id: string) => {
    return request<MultiCollection>(`/collections/${id}/archive`, { 
      method: 'PUT' 
    })
  },

  restore: (id: string) => {
    return request<MultiCollection>(`/collections/${id}/restore`, { 
      method: 'PUT' 
    })
  },

  updateConfig: (id: string, config: Partial<CollectionConfig>) => {
    return request<MultiCollection>(`/collections/${id}/config`, { 
      method: 'PUT', 
      data: config 
    })
  },

  getConfig: (id: string) => {
    return request<CollectionConfig>(`/collections/${id}/config`)
  },

  getDocuments: (id: string, params?: CollectionDocumentListParams) => {
    return request<PageResult<CollectionDocument>>(`/collections/${id}/documents`, { 
      method: 'GET', 
      params: {
        page: params?.page ?? 0,
        pageSize: params?.pageSize ?? 20,
        status: params?.status
      }
    })
  },

  addDocuments: (id: string, documentIds: string[], processingPriority = 0) => {
    return request<CollectionDocument[]>(`/collections/${id}/documents`, { 
      method: 'POST', 
      data: { documentIds, processingPriority } 
    })
  },

  removeDocument: (id: string, documentId: string) => {
    return request(`/collections/${id}/documents/${documentId}`, { 
      method: 'DELETE' 
    })
  },

  getMetrics: (id: string, params?: CollectionMetricsParams) => {
    return request<CollectionMetrics[]>(`/collections/${id}/metrics`, { 
      method: 'GET', 
      params: {
        startDate: params?.startDate,
        endDate: params?.endDate,
        metricTypes: params?.metricTypes
      } 
    })
  },

  getStatistics: (id: string) => {
    return request<{
      totalQueries: number
      avgLatencyMs: number
      p95LatencyMs: number
      errorRate: number
      activeUsers: number
    }>(`/collections/${id}/statistics`)
  },

  duplicate: (id: string, newName: string) => {
    return request<MultiCollection>(`/collections/${id}/duplicate`, { 
      method: 'POST', 
      data: { name: newName } 
    })
  },

  getPermission: (collectionId: string) => {
    return request<CollectionPermission>(`/collections/${collectionId}/permissions`)
  },

  updatePermission: (
    collectionId: string,
    data: {
      permissionType: CollectionPermissionType
      allowedUserIds?: string[]
      expiresAt?: string | null
    }
  ) => {
    return request<CollectionPermission>(`/collections/${collectionId}/permissions`, {
      method: 'PUT',
      data
    })
  },

  addUserPermission: (
    collectionId: string,
    data: {
      userId: string
      permissionLevel: CollectionPermissionLevel
      expiresAt?: string | null
    }
  ) => {
    return request<void>(`/collections/${collectionId}/permissions/users`, {
      method: 'POST',
      data
    })
  },

  removeUserPermission: (collectionId: string, userId: string) => {
    return request<void>(`/collections/${collectionId}/permissions/users/${userId}`, {
      method: 'DELETE'
    })
  },

  checkAccess: (collectionId: string) => {
    return request<CollectionAccessCheckResult>(`/collections/${collectionId}/permissions/check`)
  },

  batchUploadDocuments: (
    collectionId: string,
    files: File[],
    options?: {
      autoProcess?: boolean
      strategy?: string
      permissionType?: string
      description?: string
    }
  ) => {
    const formData = new FormData()
    files.forEach(file => {
      formData.append('files', file)
    })
    if (options?.autoProcess !== undefined) {
      formData.append('autoProcess', String(options.autoProcess))
    }
    if (options?.strategy) {
      formData.append('strategy', options.strategy)
    }
    if (options?.permissionType) {
      formData.append('permissionType', options.permissionType)
    }
    if (options?.description) {
      formData.append('description', options.description)
    }

    return request<{
      totalFiles: number
      successCount: number
      failedCount: number
      autoProcess: boolean
      results: Array<{
        success: boolean
        documentId: string
        fileName: string
        fileSize: number
        chunkCount: number
        charCount: number
        errorMessage: string
      }>
    }>(`/collections/${collectionId}/documents/upload`, {
      method: 'POST',
      data: formData,
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
  },

  getDocumentStatus: (collectionId: string, documentId: string) => {
    return request<{
      documentId: string
      collectionId: string
      processingStatus: string
      processingError: string
      processedAt: string
      addedAt: string
      uploadStatus: string
      parseStatus: string
      chunkStatus: string
      chunkCount: number
      parseErrorMessage: string
      title: string
      fileType: string
      fileSize: number
    }>(`/collections/${collectionId}/documents/${documentId}/status`)
  },

  triggerDocumentProcess: (collectionId: string, documentId: string, strategy = 'fixed') => {
    return request<{
      documentId: string
      status: string
      message: string
    }>(`/collections/${collectionId}/documents/${documentId}/process`, {
      method: 'POST',
      params: { strategy }
    })
  }
}
