import { request } from './request'
import type { Result } from '@/types'

export interface CollectionStats {
  name: string
  vectorsCount: number
  pointsCount: number
  indexedVectorsCount: number
  status: string
  createdAt: number
}

export interface VectorSearchRequest {
  vector: number[]
  limit?: number
  scoreThreshold?: number
  ef?: number
}

export interface VectorSearchResult {
  id: string
  score: number
  payload: Record<string, unknown>
}

export interface BatchInsertRequest {
  collectionName?: string
  items: VectorBatchItem[]
  waitForCompletion?: boolean
}

export interface VectorBatchItem {
  vector: number[]
  payload?: Record<string, unknown>
  pointId?: string
}

export interface BatchInsertResult {
  totalCount: number
  successCount: number
  failedCount: number
  failedPointIds: string[]
  durationMs: number
}

export interface ResumableBatchRequest {
  collectionName?: string
  items: VectorBatchItem[]
  batchId?: string
  waitForCompletion?: boolean
}

export interface ResumableBatchResponse {
  batchId: string
  status: string
}

export type BatchStatus = 'RUNNING' | 'PAUSED' | 'COMPLETED' | 'FAILED' | 'CANCELLED'

export interface ResumeState {
  batchId: string
  collectionName: string
  currentBatchIndex: number
  totalBatches: number
  processedItems: number
  totalItems: number
  status: BatchStatus
  startTimeMs: number
  lastUpdatedMs: number
  processedPointIds: string[]
  errorMessage?: string
}

export interface VectorInsertRequest {
  vector: number[]
  payload?: Record<string, unknown>
}

export interface VectorInsertResponse {
  pointId: string
}

export interface UpdatePayloadRequest {
  payload: Record<string, unknown>
}

export interface CreateCollectionRequest {
  collectionName: string
  vectorSize: number
  distance: string
}

export interface BatchDeleteRequest {
  pointIds: string[]
}

export const qdrantApi = {
  listCollections: () => {
    return request<Result<string[]>>('/qdrant/collections')
  },

  createCollection: (data: CreateCollectionRequest) => {
    return request<Result<void>>('/qdrant/collections', {
      method: 'POST',
      data
    })
  },

  getCollectionInfo: (collectionName: string) => {
    return request<Result<CollectionStats>>(`/qdrant/collections/${encodeURIComponent(collectionName)}`)
  },

  deleteCollection: (collectionName: string) => {
    return request<Result<void>>(`/qdrant/collections/${encodeURIComponent(collectionName)}`, {
      method: 'DELETE'
    })
  },

  insertVector: (collectionName: string, data: VectorInsertRequest) => {
    return request<Result<VectorInsertResponse>>(`/qdrant/collections/${encodeURIComponent(collectionName)}/points`, {
      method: 'POST',
      data
    })
  },

  getVector: (collectionName: string, pointId: string) => {
    return request<Result<Record<string, unknown>>>(`/qdrant/collections/${encodeURIComponent(collectionName)}/points/${encodeURIComponent(pointId)}`)
  },

  updateVectorPayload: (collectionName: string, pointId: string, data: UpdatePayloadRequest) => {
    return request<Result<void>>(`/qdrant/collections/${encodeURIComponent(collectionName)}/points/${encodeURIComponent(pointId)}/payload`, {
      method: 'PUT',
      data
    })
  },

  deleteVector: (collectionName: string, pointId: string) => {
    return request<Result<void>>(`/qdrant/collections/${encodeURIComponent(collectionName)}/points/${encodeURIComponent(pointId)}`, {
      method: 'DELETE'
    })
  },

  searchVectors: (collectionName: string, data: VectorSearchRequest) => {
    return request<Result<VectorSearchResult[]>>(`/qdrant/collections/${encodeURIComponent(collectionName)}/points/search`, {
      method: 'POST',
      data
    })
  },

  batchInsertVectors: (collectionName: string, data: BatchInsertRequest) => {
    return request<Result<BatchInsertResult>>(`/qdrant/collections/${encodeURIComponent(collectionName)}/points/batch`, {
      method: 'POST',
      data
    })
  },

  batchDeleteVectors: (collectionName: string, data: BatchDeleteRequest) => {
    return request<Result<void>>(`/qdrant/collections/${encodeURIComponent(collectionName)}/points`, {
      method: 'DELETE',
      data
    })
  },

  startResumableBatch: (collectionName: string, data: ResumableBatchRequest) => {
    return request<Result<ResumableBatchResponse>>(`/qdrant/collections/${encodeURIComponent(collectionName)}/points/resumable`, {
      method: 'POST',
      data
    })
  },

  getBatchStatus: (batchId: string) => {
    return request<Result<ResumeState>>(`/qdrant/batches/${encodeURIComponent(batchId)}`)
  },

  pauseBatch: (batchId: string) => {
    return request<Result<ResumeState>>(`/qdrant/batches/${encodeURIComponent(batchId)}/pause`, {
      method: 'POST'
    })
  },

  resumeBatch: (batchId: string) => {
    return request<Result<ResumeState>>(`/qdrant/batches/${encodeURIComponent(batchId)}/resume`, {
      method: 'POST'
    })
  },

  cancelBatch: (batchId: string) => {
    return request<Result<ResumeState>>(`/qdrant/batches/${encodeURIComponent(batchId)}`, {
      method: 'DELETE'
    })
  }
}
