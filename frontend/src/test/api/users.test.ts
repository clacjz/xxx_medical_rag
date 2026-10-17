import { vi, describe, it, expect, beforeEach, Mock } from 'vitest'

// 必须在导入 usersApi 之前 mock 依赖
vi.mock('@/api/request', () => ({
  request: vi.fn()
}))

vi.mock('@/utils/crypto', () => ({
  encryptPassword: vi.fn().mockImplementation(async (pwd: string) => `enc(${pwd})`)
}))

import { usersApi } from '@/api/users'
import { request } from '@/api/request'
import { encryptPassword } from '@/utils/crypto'

describe('Users API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('list', () => {
    it('should call /users with default params', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200, data: { items: [], total: 0 } })

      await usersApi.list()

      expect(request).toHaveBeenCalledWith('/users', { method: 'GET', params: undefined })
    })

    it('should pass filters', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200, data: { items: [] } })

      await usersApi.list({ page: 1, pageSize: 10, keyword: 'admin', role: 'SYSTEM_ADMIN' })

      expect(request).toHaveBeenCalledWith('/users', {
        method: 'GET',
        params: { page: 1, pageSize: 10, keyword: 'admin', role: 'SYSTEM_ADMIN' }
      })
    })
  })

  describe('getById', () => {
    it('should call /users/{id}', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200, data: { id: 'u-1' } })

      await usersApi.getById('u-1')

      expect(request).toHaveBeenCalledWith('/users/u-1')
    })
  })

  describe('create', () => {
    it('should encrypt password and POST', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200, data: { id: 'u-new' } })

      await usersApi.create({
        username: 'alice',
        password: 'secret',
        email: 'alice@example.com',
        role: 'DEPT_USER'
      })

      expect(encryptPassword).toHaveBeenCalledWith('secret')
      expect(request).toHaveBeenCalledWith('/users', {
        method: 'POST',
        data: {
          username: 'alice',
          password: 'enc(secret)',
          email: 'alice@example.com',
          role: 'DEPT_USER'
        }
      })
    })

    it('should skip encryption when useEncryption=false', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200, data: {} })

      await usersApi.create(
        { username: 'bob', password: 'plain', email: 'b@e.com', role: 'DEPT_USER' },
        false
      )

      expect(encryptPassword).not.toHaveBeenCalled()
      expect(request).toHaveBeenCalledWith('/users', {
        method: 'POST',
        data: expect.objectContaining({ password: 'plain' })
      })
    })

    it('should fall back to plain text when encryption fails', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200, data: {} })
      ;(encryptPassword as any).mockRejectedValueOnce(new Error('crypto fail'))
      const consoleSpy = vi.spyOn(console, 'warn').mockImplementation(() => {})

      await usersApi.create({
        username: 'eve',
        password: 'plain',
        email: 'e@e.com',
        role: 'DEPT_USER'
      })

      expect(consoleSpy).toHaveBeenCalled()
      expect(request).toHaveBeenCalledWith('/users', {
        method: 'POST',
        data: expect.objectContaining({ password: 'plain' })
      })
      consoleSpy.mockRestore()
    })
  })

  describe('update', () => {
    it('should POST partial update', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200, data: {} })

      await usersApi.update('u-1', { email: 'new@e.com' })

      expect(request).toHaveBeenCalledWith('/users/u-1', {
        method: 'POST',
        data: { email: 'new@e.com' }
      })
    })
  })

  describe('delete', () => {
    it('should DELETE /users/{id}', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200 })

      await usersApi.delete('u-1')

      expect(request).toHaveBeenCalledWith('/users/u-1', { method: 'DELETE' })
    })
  })

  describe('updatePassword', () => {
    it('should encrypt both passwords and POST', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200, data: {} })

      await usersApi.updatePassword('u-1', 'old', 'new')

      expect(encryptPassword).toHaveBeenCalledWith('old')
      expect(encryptPassword).toHaveBeenCalledWith('new')
      expect(request).toHaveBeenCalledWith('/users/u-1/password', {
        method: 'POST',
        data: { oldPassword: 'enc(old)', newPassword: 'enc(new)' }
      })
    })

    it('should fall back to plain text when encryption fails', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200, data: {} })
      ;(encryptPassword as any).mockRejectedValueOnce(new Error('crypto fail'))
      const consoleSpy = vi.spyOn(console, 'warn').mockImplementation(() => {})

      await usersApi.updatePassword('u-1', 'old', 'new')

      expect(consoleSpy).toHaveBeenCalled()
      expect(request).toHaveBeenCalledWith('/users/u-1/password', {
        method: 'POST',
        data: { oldPassword: 'old', newPassword: 'new' }
      })
      consoleSpy.mockRestore()
    })
  })
})
