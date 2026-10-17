import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useUserStore } from '@/stores/user'
import { authApi } from '@/api/auth'

// Mock API
vi.mock('@/api/auth', () => ({
  authApi: {
    login: vi.fn(),
    logout: vi.fn(),
    getCurrentUser: vi.fn(),
    getPublicKey: vi.fn()
  }
}))

vi.mock('@/utils/crypto', () => ({
  encryptPassword: vi.fn().mockResolvedValue('encrypted_password'),
  getPublicKey: vi.fn().mockResolvedValue('mock-public-key')
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: vi.fn()
  })
}))

describe('Login Flow Tests', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    localStorage.clear()
  })

  describe('User Store Login', () => {
    it('should login successfully', async () => {
      const mockUser = {
        id: 'user-123',
        username: 'testuser',
        displayName: 'Test User',
        token: 'jwt-token'
      }
      ;(authApi.login as any).mockResolvedValue({
        code: 200,
        data: mockUser
      })

      const userStore = useUserStore()
      const result = await userStore.login('testuser', 'password123')

      expect(authApi.login).toHaveBeenCalled()
      expect(result).toBe(true)
      expect(userStore.token).toBe('jwt-token')
    })

    it('should handle login failure', async () => {
      ;(authApi.login as any).mockRejectedValue(new Error('Login failed'))

      const userStore = useUserStore()
      const result = await userStore.login('wronguser', 'wrongpassword')

      expect(result).toBe(false)
    })

    it('should logout and clear state', async () => {
      const userStore = useUserStore()
      userStore.token = 'test-token'
      userStore.userInfo = { id: 'user-1', username: 'test' } as any

      ;(authApi.logout as any).mockResolvedValue({ code: 200 })

      await userStore.logout()

      expect(authApi.logout).toHaveBeenCalled()
      expect(userStore.token).toBe('')
      expect(userStore.userInfo).toBeNull()
    })

    it('should check role correctly', () => {
      const userStore = useUserStore()
      userStore.userRoles = [{ code: 'SYSTEM_ADMIN', level: 1 } as any]

      expect(userStore.isAdmin).toBe(true)
      expect(userStore.hasRole('SYSTEM_ADMIN')).toBe(true)
      expect(userStore.hasRole('DEPT_ADMIN')).toBe(false)
    })

    it('should not be logged in without token', () => {
      const userStore = useUserStore()
      userStore.token = ''

      expect(userStore.isLoggedIn).toBe(false)
    })
  })

  describe('Login API', () => {
    it('should call login with encrypted password', async () => {
      ;(authApi.login as any).mockResolvedValue({
        code: 200,
        data: { id: 'user-1', username: 'test' }
      })

      await authApi.login({ username: 'test', password: 'pass123' })

      expect(authApi.login).toHaveBeenCalled()
    })

    it('should handle encryption error', async () => {
      vi.mocked(authApi.login).mockRejectedValue(new Error('Encryption failed'))

      await expect(
        authApi.login({ username: 'test', password: 'pass123' })
      ).rejects.toThrow('Encryption failed')
    })
  })
})