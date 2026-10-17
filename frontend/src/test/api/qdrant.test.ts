import { vi, describe, it, expect, beforeEach, Mock } from 'vitest'
import { qdrantApi } from '@/api/qdrant'

vi.mock('@/api/request', () => ({
  request: vi.fn()
}))

import { request } from '@/api/request'

describe('Qdrant API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('listCollections', () => {
    it('should call /qdrant/collections', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200, data: ['col-1', 'col-2'] })

      const result = await qdrantApi.listCollections()

      expect(request).toHaveBeenCalledWith('/qdrant/collections')
      expect(result.data).toHaveLength(2)
    })
  })

  describe('searchVectors', () => {
    it('should POST to /qdrant/collections/{name}/points/search', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200, data: [] })

      await qdrantApi.searchVectors('col-1', {
        vector: [0.1, 0.2, 0.3],
        limit: 10
      })

      const expectedUrl = '/qdrant/collections/col-1/points/search'
      expect(request).toHaveBeenCalledWith(expectedUrl, {
        method: 'POST',
        data: { vector: [0.1, 0.2, 0.3], limit: 10 }
      })
    })

    it('should URL-encode collection name with special chars', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200, data: [] })

      await qdrantApi.searchVectors('col with space', { vector: [] })

      const calledUrl = (request as Mock).mock.calls[0][0]
      expect(calledUrl).toContain('col%20with%20space')
    })
  })

  describe('batchInsertVectors', () => {
    it('should POST to batch endpoint', async () => {
      ;(request as Mock).mockResolvedValue({
        code: 200,
        data: { totalCount: 2, successCount: 2, failedCount: 0, failedPointIds: [], durationMs: 100 }
      })

      await qdrantApi.batchInsertVectors('col-1', {
        items: [
          { vector: [0.1], pointId: 'p-1' },
          { vector: [0.2], pointId: 'p-2' }
        ],
        waitForCompletion: true
      })

      expect(request).toHaveBeenCalledWith(
        '/qdrant/collections/col-1/points/batch',
        {
          method: 'POST',
          data: {
            items: [
              { vector: [0.1], pointId: 'p-1' },
              { vector: [0.2], pointId: 'p-2' }
            ],
            waitForCompletion: true
          }
        }
      )
    })
  })

  describe('batchDeleteVectors', () => {
    it('should DELETE with pointIds data', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200, data: null })

      await qdrantApi.batchDeleteVectors('col-1', {
        pointIds: ['point-1', 'point-2']
      })

      expect(request).toHaveBeenCalledWith(
        '/qdrant/collections/col-1/points',
        { method: 'DELETE', data: { pointIds: ['point-1', 'point-2'] } }
      )
    })
  })

  describe('startResumableBatch', () => {
    it('should POST to resumable endpoint with batchId', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200, data: { batchId: 'b-1', status: 'RUNNING' } })

      await qdrantApi.startResumableBatch('col-1', {
        items: [{ vector: [0.1] }],
        batchId: 'b-1'
      })

      expect(request).toHaveBeenCalledWith(
        '/qdrant/collections/col-1/points/resumable',
        { method: 'POST', data: { items: [{ vector: [0.1] }], batchId: 'b-1' } }
      )
    })
  })

  describe('getBatchStatus', () => {
    it('should GET /qdrant/batches/{batchId}', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200, data: { batchId: 'b-1' } })

      await qdrantApi.getBatchStatus('b-1')

      expect(request).toHaveBeenCalledWith('/qdrant/batches/b-1')
    })
  })

  describe('pauseBatch', () => {
    it('should POST to /pause', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200, data: { status: 'PAUSED' } })

      await qdrantApi.pauseBatch('b-1')

      expect(request).toHaveBeenCalledWith('/qdrant/batches/b-1/pause', { method: 'POST' })
    })
  })

  describe('resumeBatch', () => {
    it('should POST to /resume', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200, data: { status: 'RUNNING' } })

      await qdrantApi.resumeBatch('b-1')

      expect(request).toHaveBeenCalledWith('/qdrant/batches/b-1/resume', { method: 'POST' })
    })
  })

  describe('cancelBatch', () => {
    it('should DELETE /qdrant/batches/{batchId}', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200, data: { status: 'CANCELLED' } })

      await qdrantApi.cancelBatch('b-1')

      expect(request).toHaveBeenCalledWith('/qdrant/batches/b-1', { method: 'DELETE' })
    })
  })
})
