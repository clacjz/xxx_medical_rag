import { vi, describe, it, expect, beforeEach, Mock } from 'vitest'
import { rolesApi } from '@/api/roles'

vi.mock('@/api/request', () => ({
  request: vi.fn()
}))

import { request } from '@/api/request'

describe('Roles API - Extended Coverage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('getByCode', () => {
    it('should GET /roles/code/{code}', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200, data: { code: 'ADMIN' } })

      await rolesApi.getByCode('ADMIN')

      expect(request).toHaveBeenCalledWith('/roles/code/ADMIN')
    })
  })

  describe('getByLevel', () => {
    it('should GET /roles/level/{level}', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200, data: [] })

      await rolesApi.getByLevel(2)

      expect(request).toHaveBeenCalledWith('/roles/level/2')
    })
  })

  describe('getUserRoles', () => {
    it('should GET /roles/users/{userId}', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200, data: [] })

      await rolesApi.getUserRoles('u-1')

      expect(request).toHaveBeenCalledWith('/roles/users/u-1')
    })
  })

  describe('assignRoleToUser', () => {
    it('should POST role assignment data', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200 })

      await rolesApi.assignRoleToUser('u-1', { roleId: 'r-1', isPrimary: true })

      expect(request).toHaveBeenCalledWith('/roles/users/u-1/assign', {
        method: 'POST',
        data: { roleId: 'r-1', isPrimary: true }
      })
    })

    it('should send isPrimary=false', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200 })

      await rolesApi.assignRoleToUser('u-1', { roleId: 'r-1', isPrimary: false })

      expect(request).toHaveBeenCalledWith('/roles/users/u-1/assign', {
        method: 'POST',
        data: { roleId: 'r-1', isPrimary: false }
      })
    })
  })

  describe('assignRoleToUserSimple', () => {
    it('should POST with roleId in URL and isPrimary as query param', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200 })

      await rolesApi.assignRoleToUserSimple('u-1', 'r-1', true)

      expect(request).toHaveBeenCalledWith('/roles/users/u-1/assign/r-1', {
        method: 'POST',
        params: { isPrimary: true }
      })
    })

    it('should default isPrimary to false', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200 })

      await rolesApi.assignRoleToUserSimple('u-1', 'r-1')

      expect(request).toHaveBeenCalledWith('/roles/users/u-1/assign/r-1', {
        method: 'POST',
        params: { isPrimary: false }
      })
    })
  })

  describe('removeRoleFromUser', () => {
    it('should DELETE /roles/users/{uid}/remove/{rid}', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200 })

      await rolesApi.removeRoleFromUser('u-1', 'r-1')

      expect(request).toHaveBeenCalledWith('/roles/users/u-1/remove/r-1', {
        method: 'DELETE'
      })
    })
  })

  describe('initializeDefaultRoles', () => {
    it('should POST to /roles/init', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200, data: 'success' })

      await rolesApi.initializeDefaultRoles()

      expect(request).toHaveBeenCalledWith('/roles/init', { method: 'POST' })
    })
  })

  describe('getAllPermissions', () => {
    it('should GET /roles/permissions', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200, data: [] })

      await rolesApi.getAllPermissions()

      expect(request).toHaveBeenCalledWith('/roles/permissions')
    })
  })

  describe('getRolePermissions', () => {
    it('should GET /roles/{id}/permissions', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200, data: {} })

      await rolesApi.getRolePermissions('r-1')

      expect(request).toHaveBeenCalledWith('/roles/r-1/permissions')
    })
  })

  describe('updateRolePermissions', () => {
    it('should PUT permissions wrapped in object', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200, data: {} })

      await rolesApi.updateRolePermissions('r-1', 'user:read,user:write')

      expect(request).toHaveBeenCalledWith('/roles/r-1/permissions', {
        method: 'PUT',
        data: { permissions: 'user:read,user:write' }
      })
    })
  })
})
