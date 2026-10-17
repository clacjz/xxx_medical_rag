import { describe, it, expect, beforeEach, Mock, vi } from 'vitest'
import { rolesApi } from '@/api/roles'

// Mock the request module
vi.mock('@/api/request', () => ({
  request: vi.fn()
}))

import { request } from '@/api/request'

describe('Roles API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('list', () => {
    it('should fetch all roles', async () => {
      const mockRoles = [
        { id: '1', code: 'SYSTEM_ADMIN', name: 'System Admin', level: 1 },
        { id: '2', code: 'DEPT_ADMIN', name: 'Department Admin', level: 2 }
      ]
      ;(request as Mock).mockResolvedValue({
        code: 200,
        data: mockRoles
      })

      const result = await rolesApi.list()

      expect(request).toHaveBeenCalledWith('/roles', { method: 'GET' })
      expect(result.data).toEqual(mockRoles)
    })
  })

  describe('listActive', () => {
    it('should fetch active roles', async () => {
      const mockRoles = [
        { id: '1', code: 'SYSTEM_ADMIN', name: 'System Admin', active: true }
      ]
      ;(request as Mock).mockResolvedValue({
        code: 200,
        data: mockRoles
      })

      const result = await rolesApi.listActive()

      expect(request).toHaveBeenCalledWith('/roles/active', { method: 'GET' })
      expect(result.data).toEqual(mockRoles)
    })
  })

  describe('getById', () => {
    it('should fetch role by id', async () => {
      const mockRole = { id: '1', code: 'SYSTEM_ADMIN', name: 'System Admin' }
      ;(request as Mock).mockResolvedValue({
        code: 200,
        data: mockRole
      })

      const result = await rolesApi.getById('1')

      expect(request).toHaveBeenCalledWith('/roles/1')
      expect(result.data).toEqual(mockRole)
    })
  })

  describe('create', () => {
    it('should create a new role', async () => {
      const roleData = {
        code: 'TEST_ROLE',
        name: 'Test Role',
        level: 3,
        permissions: ['test:*']
      }
      const mockCreated = { id: '3', ...roleData }
      ;(request as Mock).mockResolvedValue({
        code: 200,
        data: mockCreated
      })

      const result = await rolesApi.create(roleData)

      expect(request).toHaveBeenCalledWith('/roles', {
        method: 'POST',
        data: roleData
      })
      expect(result.data).toEqual(mockCreated)
    })
  })

  describe('update', () => {
    it('should update role', async () => {
      const roleData = { name: 'Updated Role' }
      ;(request as Mock).mockResolvedValue({
        code: 200,
        data: { id: '1', ...roleData }
      })

      await rolesApi.update('1', roleData)

      expect(request).toHaveBeenCalledWith('/roles/1', {
        method: 'PUT',
        data: roleData
      })
    })
  })

  describe('delete', () => {
    it('should delete role', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200 })

      await rolesApi.delete('1')

      expect(request).toHaveBeenCalledWith('/roles/1', { method: 'DELETE' })
    })
  })

  describe('assignRoleToUserSimple', () => {
    it('should assign role to user', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200 })

      await rolesApi.assignRoleToUserSimple('user1', 'role1', true)

      expect(request).toHaveBeenCalledWith('/roles/users/user1/assign/role1', {
        method: 'POST',
        params: { isPrimary: true }
      })
    })
  })

  describe('removeRoleFromUser', () => {
    it('should remove role from user', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200 })

      await rolesApi.removeRoleFromUser('user1', 'role1')

      expect(request).toHaveBeenCalledWith('/roles/users/user1/remove/role1', {
        method: 'DELETE'
      })
    })
  })

  describe('getUserRoles', () => {
    it('should fetch user roles', async () => {
      const mockRoles = [
        { id: '1', code: 'SYSTEM_ADMIN', name: 'System Admin' }
      ]
      ;(request as Mock).mockResolvedValue({
        code: 200,
        data: mockRoles
      })

      const result = await rolesApi.getUserRoles('user1')

      expect(request).toHaveBeenCalledWith('/roles/users/user1')
      expect(result.data).toEqual(mockRoles)
    })
  })

  describe('getAllPermissions', () => {
    it('should fetch all role permissions', async () => {
      const mockPermissions = [
        { id: '1', code: 'user:*', name: 'User Permission' }
      ]
      ;(request as Mock).mockResolvedValue({
        code: 200,
        data: mockPermissions
      })

      const result = await rolesApi.getAllPermissions()

      expect(request).toHaveBeenCalledWith('/roles/permissions')
      expect(result.data).toEqual(mockPermissions)
    })
  })
})
