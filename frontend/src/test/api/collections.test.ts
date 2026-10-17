import { vi, describe, it, expect, beforeEach, Mock } from 'vitest'
import { collectionsApi } from '@/api/collections'

// Mock the request module
vi.mock('@/api/request', () => ({
  request: vi.fn()
}))

import { request } from '@/api/request'

describe('Collections API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('list', () => {
    it('should list collections with default params', async () => {
      const mockResponse = {
        code: 200,
        data: {
          items: [
            { id: 'col-1', name: 'Collection 1' },
            { id: 'col-2', name: 'Collection 2' }
          ],
          total: 2
        }
      }
      ;(request as Mock).mockResolvedValue(mockResponse)

      const result = await collectionsApi.list()

      expect(request).toHaveBeenCalledWith('/collections', {
        method: 'GET',
        params: {
          page: 0,
          pageSize: 20,
          keyword: undefined,
          type: undefined,
          status: undefined,
          sortBy: 'createdAt',
          sortOrder: 'desc'
        }
      })
      expect(result.data.items).toHaveLength(2)
    })

    it('should list collections with custom params', async () => {
      const mockResponse = {
        code: 200,
        data: { items: [], total: 0 }
      }
      ;(request as Mock).mockResolvedValue(mockResponse)

      await collectionsApi.list({
        page: 1,
        pageSize: 10,
        keyword: 'test',
        type: 'KNOWLEDGE_BASE',
        status: 'ACTIVE',
        sortBy: 'name',
        sortOrder: 'asc'
      })

      expect(request).toHaveBeenCalledWith('/collections', {
        method: 'GET',
        params: {
          page: 1,
          pageSize: 10,
          keyword: 'test',
          type: 'KNOWLEDGE_BASE',
          status: 'ACTIVE',
          sortBy: 'name',
          sortOrder: 'asc'
        }
      })
    })
  })

  describe('getById', () => {
    it('should get collection by id', async () => {
      const mockCollection = {
        id: 'col-123',
        name: 'Test Collection',
        description: 'A test collection',
        status: 'ACTIVE'
      }
      ;(request as Mock).mockResolvedValue({
        code: 200,
        data: mockCollection
      })

      const result = await collectionsApi.getById('col-123')

      expect(request).toHaveBeenCalledWith('/collections/col-123')
      expect(result.data.name).toBe('Test Collection')
    })
  })

  describe('create', () => {
    it('should create collection successfully', async () => {
      const mockCollection = {
        id: 'new-col-123',
        name: 'New Collection'
      }
      ;(request as Mock).mockResolvedValue({
        code: 200,
        data: mockCollection
      })

      const result = await collectionsApi.create({
        name: 'New Collection',
        description: 'Description'
      })

      expect(request).toHaveBeenCalledWith('/collections', {
        method: 'POST',
        data: {
          name: 'New Collection',
          description: 'Description'
        }
      })
      expect(result.data.id).toBe('new-col-123')
    })
  })

  describe('update', () => {
    it('should update collection successfully', async () => {
      const mockCollection = {
        id: 'col-123',
        name: 'Updated Collection'
      }
      ;(request as Mock).mockResolvedValue({
        code: 200,
        data: mockCollection
      })

      const result = await collectionsApi.update('col-123', {
        name: 'Updated Collection',
        description: 'Updated description'
      })

      expect(request).toHaveBeenCalledWith('/collections/col-123', {
        method: 'PUT',
        data: {
          name: 'Updated Collection',
          description: 'Updated description'
        }
      })
      expect(result.data.name).toBe('Updated Collection')
    })
  })

  describe('delete', () => {
    it('should delete collection successfully', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200 })

      await collectionsApi.delete('col-123')

      expect(request).toHaveBeenCalledWith('/collections/col-123', {
        method: 'DELETE'
      })
    })
  })

  describe('archive and restore', () => {
    it('should archive collection', async () => {
      ;(request as Mock).mockResolvedValue({
        code: 200,
        data: { id: 'col-123', status: 'ARCHIVED' }
      })

      const result = await collectionsApi.archive('col-123')

      expect(request).toHaveBeenCalledWith('/collections/col-123/archive', {
        method: 'PUT'
      })
      expect(result.data.status).toBe('ARCHIVED')
    })

    it('should restore collection', async () => {
      ;(request as Mock).mockResolvedValue({
        code: 200,
        data: { id: 'col-123', status: 'ACTIVE' }
      })

      const result = await collectionsApi.restore('col-123')

      expect(request).toHaveBeenCalledWith('/collections/col-123/restore', {
        method: 'PUT'
      })
      expect(result.data.status).toBe('ACTIVE')
    })
  })

  describe('config operations', () => {
    it('should get config', async () => {
      const mockConfig = {
        retrieval: { topK: 10 },
        generation: { maxTokens: 2000 }
      }
      ;(request as Mock).mockResolvedValue({
        code: 200,
        data: mockConfig
      })

      const result = await collectionsApi.getConfig('col-123')

      expect(request).toHaveBeenCalledWith('/collections/col-123/config')
      expect(result.data.retrieval!.topK).toBe(10)
    })

    it('should update config', async () => {
      ;(request as Mock).mockResolvedValue({
        code: 200,
        data: { retrieval: { topK: 5 } }
      })

      await collectionsApi.updateConfig('col-123', {
        retrieval: { topK: 5 }
      })

      expect(request).toHaveBeenCalledWith('/collections/col-123/config', {
        method: 'PUT',
        data: { retrieval: { topK: 5 } }
      })
    })
  })

  describe('document operations', () => {
    it('should get collection documents', async () => {
      const mockResponse = {
        code: 200,
        data: {
          items: [
            { id: 'doc-1', title: 'Document 1' },
            { id: 'doc-2', title: 'Document 2' }
          ],
          total: 2
        }
      }
      ;(request as Mock).mockResolvedValue(mockResponse)

      const result = await collectionsApi.getDocuments('col-123')

      expect(request).toHaveBeenCalledWith('/collections/col-123/documents', {
        method: 'GET',
        params: {
          page: 0,
          pageSize: 20,
          status: undefined
        }
      })
      expect(result.data.items).toHaveLength(2)
    })

    it('should add documents to collection', async () => {
      ;(request as Mock).mockResolvedValue({
        code: 200,
        data: [{ id: 'doc-1' }, { id: 'doc-2' }]
      })

      await collectionsApi.addDocuments('col-123', ['doc-1', 'doc-2'])

      expect(request).toHaveBeenCalledWith('/collections/col-123/documents', {
        method: 'POST',
        data: { documentIds: ['doc-1', 'doc-2'], processingPriority: 0 }
      })
    })

    it('should remove document from collection', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200 })

      await collectionsApi.removeDocument('col-123', 'doc-1')

      expect(request).toHaveBeenCalledWith('/collections/col-123/documents/doc-1', {
        method: 'DELETE'
      })
    })

    it('should get document status', async () => {
      const mockStatus = {
        documentId: 'doc-1',
        processingStatus: 'COMPLETED',
        chunkCount: 10
      }
      ;(request as Mock).mockResolvedValue({
        code: 200,
        data: mockStatus
      })

      const result = await collectionsApi.getDocumentStatus('col-123', 'doc-1')

      expect(request).toHaveBeenCalledWith('/collections/col-123/documents/doc-1/status')
      expect(result.data.processingStatus).toBe('COMPLETED')
    })

    it('should trigger document process', async () => {
      ;(request as Mock).mockResolvedValue({
        code: 200,
        data: { documentId: 'doc-1', status: 'PROCESSING' }
      })

      await collectionsApi.triggerDocumentProcess('col-123', 'doc-1', 'SEMANTIC')

      expect(request).toHaveBeenCalledWith('/collections/col-123/documents/doc-1/process', {
        method: 'POST',
        params: { strategy: 'SEMANTIC' }
      })
    })
  })

  describe('metrics and statistics', () => {
    it('should get collection metrics', async () => {
      const mockMetrics = [
        { type: 'query_count', value: 100 },
        { type: 'avg_latency', value: 250 }
      ]
      ;(request as Mock).mockResolvedValue({
        code: 200,
        data: mockMetrics
      })

      await collectionsApi.getMetrics('col-123', {
        startDate: '2024-01-01',
        endDate: '2024-01-31'
      })

      expect(request).toHaveBeenCalledWith('/collections/col-123/metrics', {
        method: 'GET',
        params: {
          startDate: '2024-01-01',
          endDate: '2024-01-31',
          metricTypes: undefined
        }
      })
    })

    it('should get collection statistics', async () => {
      const mockStats = {
        totalQueries: 1000,
        avgLatencyMs: 250,
        p95LatencyMs: 500,
        errorRate: 0.01,
        activeUsers: 50
      }
      ;(request as Mock).mockResolvedValue({
        code: 200,
        data: mockStats
      })

      const result = await collectionsApi.getStatistics('col-123')

      expect(request).toHaveBeenCalledWith('/collections/col-123/statistics')
      expect(result.data.totalQueries).toBe(1000)
    })
  })

  describe('permission operations', () => {
    it('should get permission', async () => {
      const mockPermission = {
        permissionType: 'DEPARTMENT_WIDE',
        allowedUserIds: []
      }
      ;(request as Mock).mockResolvedValue({
        code: 200,
        data: mockPermission
      })

      const result = await collectionsApi.getPermission('col-123')

      expect(request).toHaveBeenCalledWith('/collections/col-123/permissions')
      expect(result.data.permissionType).toBe('DEPARTMENT_WIDE')
    })

    it('should update permission', async () => {
      ;(request as Mock).mockResolvedValue({
        code: 200,
        data: { permissionType: 'RESTRICTED' }
      })

      await collectionsApi.updatePermission('col-123', {
        permissionType: 'RESTRICTED',
        allowedUserIds: ['user-1', 'user-2']
      })

      expect(request).toHaveBeenCalledWith('/collections/col-123/permissions', {
        method: 'PUT',
        data: {
          permissionType: 'RESTRICTED',
          allowedUserIds: ['user-1', 'user-2'],
          expiresAt: undefined
        }
      })
    })

    it('should add user permission', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200 })

      await collectionsApi.addUserPermission('col-123', {
        userId: 'user-1',
        permissionLevel: 'READ'
      })

      expect(request).toHaveBeenCalledWith('/collections/col-123/permissions/users', {
        method: 'POST',
        data: {
          userId: 'user-1',
          permissionLevel: 'READ',
          expiresAt: undefined
        }
      })
    })

    it('should remove user permission', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200 })

      await collectionsApi.removeUserPermission('col-123', 'user-1')

      expect(request).toHaveBeenCalledWith('/collections/col-123/permissions/users/user-1', {
        method: 'DELETE'
      })
    })

    it('should check access', async () => {
      const mockResult = {
        hasAccess: true,
        permissionType: 'DEPARTMENT_WIDE'
      }
      ;(request as Mock).mockResolvedValue({
        code: 200,
        data: mockResult
      })

      const result = await collectionsApi.checkAccess('col-123')

      expect(request).toHaveBeenCalledWith('/collections/col-123/permissions/check')
      expect(result.data.hasAccess).toBe(true)
    })
  })

  describe('duplicate', () => {
    it('should duplicate collection', async () => {
      const mockCollection = {
        id: 'new-col-456',
        name: 'Copy of Test'
      }
      ;(request as Mock).mockResolvedValue({
        code: 200,
        data: mockCollection
      })

      const result = await collectionsApi.duplicate('col-123', 'Copy of Test')

      expect(request).toHaveBeenCalledWith('/collections/col-123/duplicate', {
        method: 'POST',
        data: { name: 'Copy of Test' }
      })
      expect(result.data.name).toBe('Copy of Test')
    })
  })
})
