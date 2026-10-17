import { vi, describe, it, expect, beforeEach, Mock } from 'vitest'
import { authApi } from '@/api/auth'

// Mock the crypto utilities
vi.mock('@/utils/crypto', () => ({
  encryptPassword: vi.fn().mockResolvedValue('encrypted_password'),
  getPublicKey: vi.fn().mockResolvedValue('mock-public-key')
}))

// Mock the request module
vi.mock('@/api/request', () => ({
  request: vi.fn()
}))

import { request } from '@/api/request'
import { encryptPassword } from '@/utils/crypto'

describe('Auth API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('getPublicKey', () => {
    it('should fetch public key successfully', async () => {
      const mockPublicKey = 'test-public-key-12345'
      ;(request as Mock).mockResolvedValue({
        code: 200,
        data: { publicKey: mockPublicKey }
      })

      const result = await authApi.getPublicKey()

      expect(request).toHaveBeenCalledWith('/auth/public-key', { method: 'GET' })
      expect(result).toBe(mockPublicKey)
    })

    it('should return empty string when no public key', async () => {
      ;(request as Mock).mockResolvedValue({
        code: 200,
        data: {}
      })

      const result = await authApi.getPublicKey()

      expect(result).toBe('')
    })
  })

  describe('login', () => {
    it('should login with encryption by default', async () => {
      const mockUser = {
        id: '123',
        username: 'testuser',
        displayName: 'Test User'
      }
      ;(request as Mock).mockResolvedValue({
        code: 200,
        data: mockUser
      })

      const result = await authApi.login({
        username: 'testuser',
        password: 'password123'
      })

      expect(encryptPassword).toHaveBeenCalledWith('password123')
      expect(request).toHaveBeenCalledWith('/auth/login', {
        method: 'POST',
        data: {
          username: 'testuser',
          password: 'encrypted_password'
        }
      })
      expect(result.data).toEqual(mockUser)
    })

    it('should login without encryption when useEncryption is false', async () => {
      const mockUser = {
        id: '123',
        username: 'testuser',
        displayName: 'Test User'
      }
      ;(request as Mock).mockResolvedValue({
        code: 200,
        data: mockUser
      })

      await authApi.login(
        { username: 'testuser', password: 'password123' },
        false
      )

      expect(encryptPassword).not.toHaveBeenCalled()
      expect(request).toHaveBeenCalledWith('/auth/login', {
        method: 'POST',
        data: { username: 'testuser', password: 'password123' }
      })
    })
  })

  describe('register', () => {
    it('should register with encryption by default', async () => {
      const mockUser = {
        id: '123',
        username: 'newuser',
        email: 'new@example.com'
      }
      ;(request as Mock).mockResolvedValue({
        code: 200,
        data: mockUser
      })

      const result = await authApi.register({
        username: 'newuser',
        password: 'password123',
        email: 'new@example.com'
      })

      expect(encryptPassword).toHaveBeenCalledWith('password123')
      expect(request).toHaveBeenCalledWith('/auth/register', {
        method: 'POST',
        data: {
          username: 'newuser',
          password: 'encrypted_password',
          email: 'new@example.com'
        }
      })
      expect(result.data).toEqual(mockUser)
    })

    it('should register without encryption when useEncryption is false', async () => {
      ;(request as Mock).mockResolvedValue({
        code: 200,
        data: {}
      })

      await authApi.register(
        {
          username: 'newuser',
          password: 'password123',
          email: 'new@example.com'
        },
        false
      )

      expect(encryptPassword).not.toHaveBeenCalled()
    })
  })

  describe('logout', () => {
    it('should call logout endpoint', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200 })

      await authApi.logout()

      expect(request).toHaveBeenCalledWith('/auth/logout', { method: 'POST' })
    })
  })

  describe('refreshToken', () => {
    it('should refresh token successfully', async () => {
      ;(request as Mock).mockResolvedValue({
        code: 200,
        data: { token: 'new-jwt-token' }
      })

      const result = await authApi.refreshToken()

      expect(request).toHaveBeenCalledWith('/auth/refresh', { method: 'POST' })
      expect(result.data?.token).toBe('new-jwt-token')
    })
  })

  describe('getCurrentUser', () => {
    it('should fetch current user successfully', async () => {
      const mockUser = {
        id: '123',
        username: 'currentuser',
        displayName: 'Current User'
      }
      ;(request as Mock).mockResolvedValue({
        code: 200,
        data: mockUser
      })

      const result = await authApi.getCurrentUser()

      expect(request).toHaveBeenCalledWith('/auth/current')
      expect(result.data).toEqual(mockUser)
    })
  })
})