import { vi, describe, it, expect, beforeEach, Mock } from 'vitest'
import { departmentsApi } from '@/api/departments'

vi.mock('@/api/request', () => ({
  request: vi.fn()
}))

import { request } from '@/api/request'

describe('Departments API - Full Coverage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('getUsers', () => {
    it('should GET /departments/{id}/users', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200, data: [] })

      await departmentsApi.getUsers('d-1')

      expect(request).toHaveBeenCalledWith('/departments/d-1/users')
    })
  })

  describe('addUser', () => {
    it('should POST user data to /departments/{id}/users', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200 })

      await departmentsApi.addUser('d-1', {
        userId: 'u-1',
        isPrimary: true,
        position: '主任'
      })

      expect(request).toHaveBeenCalledWith('/departments/d-1/users', {
        method: 'POST',
        data: { userId: 'u-1', isPrimary: true, position: '主任' }
      })
    })

    it('should allow minimal payload without isPrimary/position', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200 })

      await departmentsApi.addUser('d-1', { userId: 'u-1' })

      expect(request).toHaveBeenCalledWith('/departments/d-1/users', {
        method: 'POST',
        data: { userId: 'u-1' }
      })
    })
  })

  describe('removeUser', () => {
    it('should DELETE /departments/{did}/users/{uid}', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200 })

      await departmentsApi.removeUser('d-1', 'u-1')

      expect(request).toHaveBeenCalledWith('/departments/d-1/users/u-1', { method: 'DELETE' })
    })
  })

  describe('update with status change', () => {
    it('should POST status change to /departments/{id}', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200, data: {} })

      await departmentsApi.update('d-1', { status: 'INACTIVE' })

      expect(request).toHaveBeenCalledWith('/departments/d-1', {
        method: 'POST',
        data: { status: 'INACTIVE' }
      })
    })
  })
})
