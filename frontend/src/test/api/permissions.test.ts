import { vi, describe, it, expect, beforeEach, Mock } from 'vitest'
import { permissionsApi } from '@/api/permissions'

vi.mock('@/api/request', () => ({
  request: vi.fn()
}))

import { request } from '@/api/request'

describe('Permissions API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('checkAccess', () => {
    it('should call /documents/{id}/access-check', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200, data: { hasAccess: true } })

      await permissionsApi.checkAccess('d-1')

      expect(request).toHaveBeenCalledWith('/documents/d-1/access-check')
    })
  })

  describe('batchCheckAccess', () => {
    it('should POST array of documentIds', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200, data: {} })

      await permissionsApi.batchCheckAccess(['d-1', 'd-2'])

      expect(request).toHaveBeenCalledWith('/documents/batch-access-check', {
        method: 'POST',
        data: { documentIds: ['d-1', 'd-2'] }
      })
    })

    it('should send empty array when no ids', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200, data: {} })

      await permissionsApi.batchCheckAccess([])

      expect(request).toHaveBeenCalledWith('/documents/batch-access-check', {
        method: 'POST',
        data: { documentIds: [] }
      })
    })
  })

  describe('getDocumentPermission', () => {
    it('should call /documents/{id}/permissions', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200, data: { permissionType: 'PUBLIC' } })

      await permissionsApi.getDocumentPermission('d-1')

      expect(request).toHaveBeenCalledWith('/documents/d-1/permissions')
    })
  })

  describe('updateDocumentPermission', () => {
    it('should POST permission data', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200, data: {} })

      await permissionsApi.updateDocumentPermission('d-1', {
        permissionType: 'RESTRICTED',
        allowedUserIds: ['u-1']
      })

      expect(request).toHaveBeenCalledWith('/documents/d-1/permissions', {
        method: 'POST',
        data: { permissionType: 'RESTRICTED', allowedUserIds: ['u-1'] }
      })
    })

    it('should allow null expiresAt', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200, data: {} })

      await permissionsApi.updateDocumentPermission('d-1', {
        permissionType: 'COMPANY_WIDE',
        expiresAt: null
      })

      expect(request).toHaveBeenCalledWith('/documents/d-1/permissions', {
        method: 'POST',
        data: { permissionType: 'COMPANY_WIDE', expiresAt: null }
      })
    })
  })

  describe('addUserPermission', () => {
    it('should POST user permission data', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200 })

      await permissionsApi.addUserPermission('d-1', {
        userId: 'u-2',
        permissionLevel: 'READ'
      })

      expect(request).toHaveBeenCalledWith('/documents/d-1/permissions/users', {
        method: 'POST',
        data: { userId: 'u-2', permissionLevel: 'READ' }
      })
    })
  })

  describe('removeUserPermission', () => {
    it('should DELETE /documents/{docId}/permissions/users/{userId}', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200 })

      await permissionsApi.removeUserPermission('d-1', 'u-2')

      expect(request).toHaveBeenCalledWith('/documents/d-1/permissions/users/u-2', {
        method: 'DELETE'
      })
    })
  })

  describe('getAuditLogs', () => {
    it('should call /audit-logs/permissions with time range', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200, data: { items: [] } })

      await permissionsApi.getAuditLogs({ startTime: 0, endTime: 100 })

      expect(request).toHaveBeenCalledWith('/audit-logs/permissions', {
        method: 'GET',
        params: {
          page: undefined,
          pageSize: undefined,
          targetType: undefined,
          targetId: undefined,
          operatorId: undefined,
          operationType: undefined,
          startTime: 0,
          endTime: 100
        }
      })
    })

    it('should pass filter params', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200, data: {} })

      await permissionsApi.getAuditLogs({
        targetType: 'DOCUMENT',
        targetId: 'd-1',
        operatorId: 'u-1',
        operationType: 'GRANT',
        startTime: 0,
        endTime: 100
      })

      expect(request).toHaveBeenCalledWith('/audit-logs/permissions', {
        method: 'GET',
        params: {
          page: undefined,
          pageSize: undefined,
          targetType: 'DOCUMENT',
          targetId: 'd-1',
          operatorId: 'u-1',
          operationType: 'GRANT',
          startTime: 0,
          endTime: 100
        }
      })
    })
  })
})
