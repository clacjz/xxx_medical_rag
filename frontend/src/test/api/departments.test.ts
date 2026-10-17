import { describe, it, expect, beforeEach, Mock, vi } from 'vitest'
import { departmentsApi } from '@/api/departments'

// Mock the request module
vi.mock('@/api/request', () => ({
  request: vi.fn()
}))

import { request } from '@/api/request'

describe('Departments API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('getTree', () => {
    it('should fetch department tree', async () => {
      const mockDepartments = [
        { id: '1', code: 'IT', name: 'IT Department' },
        { id: '2', code: 'HR', name: 'HR Department' }
      ]
      ;(request as Mock).mockResolvedValue({
        code: 200,
        data: mockDepartments
      })

      const result = await departmentsApi.getTree()

      expect(request).toHaveBeenCalledWith('/departments', { method: 'GET' })
      expect(result.data).toEqual(mockDepartments)
    })

    it('should return empty array when no departments', async () => {
      ;(request as Mock).mockResolvedValue({
        code: 200,
        data: []
      })

      const result = await departmentsApi.getTree()

      expect(result.data).toEqual([])
    })
  })

  describe('getById', () => {
    it('should fetch department by id', async () => {
      const mockDepartment = { id: '1', code: 'IT', name: 'IT Department' }
      ;(request as Mock).mockResolvedValue({
        code: 200,
        data: mockDepartment
      })

      const result = await departmentsApi.getById('1')

      expect(request).toHaveBeenCalledWith('/departments/1')
      expect(result.data).toEqual(mockDepartment)
    })
  })

  describe('create', () => {
    it('should create a new department', async () => {
      const deptData = { code: 'IT', name: 'IT Department' }
      const mockCreated = { id: '1', ...deptData }
      ;(request as Mock).mockResolvedValue({
        code: 200,
        data: mockCreated
      })

      const result = await departmentsApi.create(deptData)

      expect(request).toHaveBeenCalledWith('/departments', {
        method: 'POST',
        data: deptData
      })
      expect(result.data).toEqual(mockCreated)
    })
  })

  describe('update', () => {
    it('should update department', async () => {
      const deptData = { name: 'Updated IT Department' }
      ;(request as Mock).mockResolvedValue({
        code: 200,
        data: { id: '1', ...deptData }
      })

      await departmentsApi.update('1', deptData)

      expect(request).toHaveBeenCalledWith('/departments/1', {
        method: 'POST',
        data: deptData
      })
    })
  })

  describe('delete', () => {
    it('should delete department', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200 })

      await departmentsApi.delete('1')

      expect(request).toHaveBeenCalledWith('/departments/1', { method: 'DELETE' })
    })
  })

  describe('addUser', () => {
    it('should add user to department', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200 })

      await departmentsApi.addUser('dept1', { userId: 'user1', isPrimary: true })

      expect(request).toHaveBeenCalledWith('/departments/dept1/users', {
        method: 'POST',
        data: { userId: 'user1', isPrimary: true }
      })
    })
  })

  describe('removeUser', () => {
    it('should remove user from department', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200 })

      await departmentsApi.removeUser('dept1', 'user1')

      expect(request).toHaveBeenCalledWith('/departments/dept1/users/user1', {
        method: 'DELETE'
      })
    })
  })
})
