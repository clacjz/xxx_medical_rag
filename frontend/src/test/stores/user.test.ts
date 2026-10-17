import { describe, it, expect, beforeEach, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useUserStore } from '@/stores/user'
import { authApi } from '@/api/auth'
import type { User } from '@/types'

// Mock dependencies
vi.mock('@/api/auth', () => ({
  authApi: {
    login: vi.fn(),
    logout: vi.fn(),
    getCurrentUser: vi.fn()
  }
}))

vi.mock('@/api/roles', () => ({
  rolesApi: {
    getUserRoles: vi.fn()
  }
}))

vi.mock('@/services/auditLog', () => ({
  auditService: {
    logLogin: vi.fn(),
    logLogout: vi.fn()
  }
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: vi.fn()
  })
}))

vi.mock('@/services/auditLog', () => ({
  auditService: {
    logLogout: vi.fn()
  }
}))

describe('useUserStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    vi.clearAllMocks()
  })

  describe('initial state', () => {
    it('should have empty token initially', () => {
      const store = useUserStore()
      expect(store.token).toBe('')
    })

    it('should have null userInfo initially', () => {
      const store = useUserStore()
      expect(store.userInfo).toBeNull()
    })

    it('should not be logged in initially', () => {
      const store = useUserStore()
      expect(store.isLoggedIn).toBe(false)
    })
  })

  describe('setToken', () => {
    it('should update token and localStorage', () => {
      const store = useUserStore()
      store.setToken('test-token-123')

      expect(store.token).toBe('test-token-123')
      expect(localStorage.getItem('token')).toBe('test-token-123')
    })
  })

  describe('setUserInfo', () => {
    it('should update userInfo and localStorage', () => {
      const store = useUserStore()
      const userInfo: User = {
        id: '123',
        username: 'testuser',
        email: 'test@example.com',
        role: 'DEPT_USER',
        status: 'ACTIVE',
        createdAt: '2026-01-01T00:00:00Z',
        updatedAt: '2026-01-01T00:00:00Z'
      }

      store.setUserInfo(userInfo)

      expect(store.userInfo).toEqual(userInfo)
      expect(localStorage.getItem('userInfo')).toBeTruthy()
    })

    it('should set userRoles from info.roles if available', () => {
      const store = useUserStore()
      const userInfo: User = {
        id: '123',
        username: 'testuser',
        email: 'test@example.com',
        role: 'DEPT_USER',
        status: 'ACTIVE',
        createdAt: '2026-01-01T00:00:00Z',
        updatedAt: '2026-01-01T00:00:00Z',
        roles: [
          { id: '1', code: 'DEPT_USER', name: 'Dept User', level: 4, isPrimary: true }
        ]
      }

      store.setUserInfo(userInfo)

      expect(store.userRoles).toHaveLength(1)
      expect(store.userRoles[0].code).toBe('DEPT_USER')
    })
  })

  describe('hasRole', () => {
    it('should return true when user has the role', () => {
      const store = useUserStore()
      store.userRoles = [
        { id: '1', code: 'SYSTEM_ADMIN', name: 'Admin', level: 1, isPrimary: true }
      ]

      expect(store.hasRole('SYSTEM_ADMIN')).toBe(true)
    })

    it('should return false when user does not have the role', () => {
      const store = useUserStore()
      store.userRoles = [
        { id: '1', code: 'DEPT_USER', name: 'Dept User', level: 4, isPrimary: true }
      ]

      expect(store.hasRole('SYSTEM_ADMIN')).toBe(false)
    })

    it('should check legacy role when userRoles is empty', () => {
      const store = useUserStore()
      store.userInfo = {
        id: '123',
        username: 'testuser',
        email: 'test@example.com',
        role: 'SYSTEM_ADMIN' as const,
        status: 'ACTIVE' as const,
        createdAt: '2026-01-01T00:00:00Z',
        updatedAt: '2026-01-01T00:00:00Z'
      }
      store.userRoles = []

      expect(store.hasRole('SYSTEM_ADMIN')).toBe(true)
    })
  })

  describe('hasAnyRole', () => {
    it('should return true when user has any of the roles', () => {
      const store = useUserStore()
      store.userRoles = [
        { id: '1', code: 'DEPT_USER', name: 'Dept User', level: 4, isPrimary: true }
      ]

      expect(store.hasAnyRole(['SYSTEM_ADMIN', 'DEPT_USER'])).toBe(true)
    })

    it('should return false when user has none of the roles', () => {
      const store = useUserStore()
      store.userRoles = [
        { id: '1', code: 'DEPT_USER', name: 'Dept User', level: 4, isPrimary: true }
      ]

      expect(store.hasAnyRole(['SYSTEM_ADMIN', 'DEPT_ADMIN'])).toBe(false)
    })
  })

  describe('hasLevel', () => {
    it('should return true when user level is less than or equal', () => {
      const store = useUserStore()
      store.userRoles = [
        { id: '1', code: 'DEPT_ADMIN', name: 'Dept Admin', level: 2, isPrimary: true }
      ]

      expect(store.hasLevel(2)).toBe(true)
      expect(store.hasLevel(3)).toBe(true)
    })

    it('should return false when user level is greater', () => {
      const store = useUserStore()
      store.userRoles = [
        { id: '1', code: 'DEPT_USER', name: 'Dept User', level: 4, isPrimary: true }
      ]

      expect(store.hasLevel(1)).toBe(false)
      expect(store.hasLevel(2)).toBe(false)
    })
  })

  describe('primaryRoleLevel', () => {
    it('should return level from primary role', () => {
      const store = useUserStore()
      store.userRoles = [
        { id: '1', code: 'DEPT_ADMIN', name: 'Dept Admin', level: 2, isPrimary: false },
        { id: '2', code: 'SYSTEM_ADMIN', name: 'Admin', level: 1, isPrimary: true }
      ]

      expect(store.primaryRoleLevel).toBe(1)
    })

    it('should return first role level when no primary', () => {
      const store = useUserStore()
      store.userRoles = [
        { id: '1', code: 'DEPT_USER', name: 'Dept User', level: 4, isPrimary: false }
      ]

      expect(store.primaryRoleLevel).toBe(4)
    })
  })

  describe('isAdmin', () => {
    it('should return true for SYSTEM_ADMIN', () => {
      const store = useUserStore()
      store.userRoles = [
        { id: '1', code: 'SYSTEM_ADMIN', name: 'Admin', level: 1, isPrimary: true }
      ]

      expect(store.isAdmin).toBe(true)
    })

    it('should return false for non-admin roles', () => {
      const store = useUserStore()
      store.userRoles = [
        { id: '1', code: 'DEPT_USER', name: 'Dept User', level: 4, isPrimary: true }
      ]

      expect(store.isAdmin).toBe(false)
    })
  })

  describe('login', () => {
    it('should login successfully and set token', async () => {
      const store = useUserStore()
      vi.mocked(authApi.login).mockResolvedValue({
        code: 200,
        data: {
          id: '123',
          username: 'testuser',
          token: 'jwt-token-123'
        }
      } as any)

      const result = await store.login('testuser', 'password123')

      expect(result).toBe(true)
      expect(store.token).toBe('jwt-token-123')
      expect(store.userInfo).toBeTruthy()
    })

    it('should return false on login failure', async () => {
      const store = useUserStore()
      vi.mocked(authApi.login).mockRejectedValue(new Error('Login failed'))

      const result = await store.login('testuser', 'wrongpassword')

      expect(result).toBe(false)
      expect(store.token).toBe('')
    })
  })

  describe('logout', () => {
    it('should clear user data and call logout API', async () => {
      const store = useUserStore()
      store.token = 'test-token'
      store.userInfo = {
        id: '123',
        username: 'testuser',
        email: 'test@example.com',
        role: 'DEPT_USER' as const,
        status: 'ACTIVE' as const,
        createdAt: '2026-01-01T00:00:00Z',
        updatedAt: '2026-01-01T00:00:00Z'
      }
      vi.mocked(authApi.logout).mockResolvedValue({ code: 200 } as any)

      await store.logout()

      expect(authApi.logout).toHaveBeenCalled()
      expect(store.token).toBe('')
      expect(store.userInfo).toBeNull()
      expect(localStorage.getItem('token')).toBeNull()
    })
  })

  describe('fetchUserInfo', () => {
    it('should fetch and set user info', async () => {
      const store = useUserStore()
      vi.mocked(authApi.getCurrentUser).mockResolvedValue({
        code: 200,
        data: { id: '123', username: 'testuser', role: 'DEPT_USER' as const }
      } as any)

      await store.fetchUserInfo()

      expect(store.userInfo).toBeTruthy()
      expect(store.userInfo?.username).toBe('testuser')
    })
  })

  describe('setUserRolesFromLegacyRole', () => {
    it('should convert role string to userRoles', () => {
      const store = useUserStore()
      store.setUserRolesFromLegacyRole('SYSTEM_ADMIN')

      expect(store.userRoles).toHaveLength(1)
      expect(store.userRoles[0].code).toBe('SYSTEM_ADMIN')
      expect(store.userRoles[0].level).toBe(1)
    })

    it('should clear roles for unknown role', () => {
      const store = useUserStore()
      store.userRoles = [{ id: '1', code: 'TEST', name: 'Test', level: 5, isPrimary: true }]

      store.setUserRolesFromLegacyRole('UNKNOWN_ROLE')

      expect(store.userRoles).toHaveLength(0)
    })
  })
})