import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import type { User, Role, UserRole } from '@/types'
import { authApi } from '@/api/auth'
import { rolesApi } from '@/api/roles'
import { auditService } from '@/services/auditLog'

export interface LoginResponse extends User {
  token?: string
  roles?: UserRole[]
}

export type RoleLevel = 1 | 2 | 3 | 4

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem('token') || '')
  const userInfo = ref<User | null>(null)
  const userRoles = ref<UserRole[]>([])

  const isLoggedIn = computed(() => !!token.value)

  const primaryRole = computed<UserRole | null>(() => {
    return userRoles.value.find(role => role.isPrimary) || userRoles.value[0] || null
  })

  const primaryRoleLevel = computed<RoleLevel | null>(() => {
    if (primaryRole.value) {
      return primaryRole.value.level as RoleLevel
    }

    if (userInfo.value?.role) {
      const roleToLevel: Record<string, RoleLevel> = {
        'SYSTEM_ADMIN': 1,
        'DEPT_ADMIN': 2,
        'DEPT_UPLOADER': 3,
        'DEPT_USER': 4
      }
      return roleToLevel[userInfo.value.role] || null
    }

    return null
  })

  const isAdmin = computed(() => {
    const level = primaryRoleLevel.value
    return level === 1
  })

  const isDeptAdmin = computed(() => {
    const level = primaryRoleLevel.value
    return level === 2
  })

  const isKnowledgeAdmin = computed(() => {
    const level = primaryRoleLevel.value
    return level !== null && level <= 3
  })

  const hasRole = (roleCode: string): boolean => {
    if (userRoles.value.length > 0) {
      return userRoles.value.some(role => role.code === roleCode)
    }
    return userInfo.value?.role === roleCode
  }

  const hasAnyRole = (roleCodes: string[]): boolean => {
    return roleCodes.some(code => hasRole(code))
  }

  const hasLevel = (level: RoleLevel): boolean => {
    const currentLevel = primaryRoleLevel.value
    return currentLevel !== null && currentLevel <= level
  }

  const hasPermission = (permission: string): boolean => {
    if (isAdmin.value) return true

    for (const role of userRoles.value) {
      const rolePermissions = getRolePermissions(role)
      if (rolePermissions && rolePermissions.includes(permission)) {
        return true
      }
    }
    return false
  }

  const hasAnyPermission = (permissions: string[]): boolean => {
    return permissions.some(perm => hasPermission(perm))
  }

  const canAccessPage = (pageRoles: string[]): boolean => {
    if (pageRoles.length === 0) return true
    return hasAnyRole(pageRoles)
  }

  const getMaxLevel = computed<RoleLevel | null>(() => {
    if (userRoles.value.length > 0) {
      return Math.min(...userRoles.value.map(r => r.level)) as RoleLevel
    }

    if (userInfo.value?.role) {
      const roleToLevel: Record<string, RoleLevel> = {
        'SYSTEM_ADMIN': 1,
        'DEPT_ADMIN': 2,
        'DEPT_UPLOADER': 3,
        'DEPT_USER': 4
      }
      return roleToLevel[userInfo.value.role] || null
    }

    return null
  })

  const getRolePermissions = (role: UserRole | Role): string[] | null => {
    if ('permissions' in role && role.permissions) {
      try {
        if (typeof role.permissions === 'string') {
          return JSON.parse(role.permissions)
        }
        return role.permissions as unknown as string[]
      } catch {
        return null
      }
    }
    return null
  }

  const setToken = (newToken: string) => {
    token.value = newToken
    localStorage.setItem('token', newToken)
  }

  const setUserInfo = (info: User) => {
    userInfo.value = info

    if (info.roles && info.roles.length > 0) {
      setUserRoles(info.roles)
    } else if (!userRoles.value.length) {
      setUserRolesFromLegacyRole(info.role)
    }

    localStorage.setItem('userInfo', JSON.stringify(info))
  }

  const setUserRoles = (roles: (UserRole | Role)[]) => {
    userRoles.value = roles.map(r => ({
      id: r.id,
      code: r.code,
      name: r.name,
      level: r.level,
      isPrimary: 'isPrimary' in r ? r.isPrimary : true,
      roleId: 'roleId' in r ? r.roleId : r.id,
      assignedAt: 'assignedAt' in r ? r.assignedAt : new Date().toISOString()
    }))
    localStorage.setItem('userRoles', JSON.stringify(userRoles.value))
  }

  const setUserRolesFromLegacyRole = (role: string | undefined) => {
    if (!role) {
      userRoles.value = []
      return
    }

    const roleToLevel: Record<string, { code: string; name: string; level: RoleLevel }> = {
      'SYSTEM_ADMIN': { code: 'SYSTEM_ADMIN', name: '系统管理员', level: 1 },
      'DEPT_ADMIN': { code: 'DEPT_ADMIN', name: '部门管理员', level: 2 },
      'DEPT_UPLOADER': { code: 'DEPT_UPLOADER', name: '部门上传者', level: 3 },
      'DEPT_USER': { code: 'DEPT_USER', name: '部门普通人员', level: 4 }
    }

    const roleInfo = roleToLevel[role]
    if (roleInfo) {
      userRoles.value = [{
        id: roleInfo.code,
        code: roleInfo.code,
        name: roleInfo.name,
        level: roleInfo.level,
        isPrimary: true,
        roleId: roleInfo.code,
        assignedAt: new Date().toISOString()
      }]
    } else {
      userRoles.value = []
    }
    localStorage.setItem('userRoles', JSON.stringify(userRoles.value))
  }

  const login = async (username: string, password: string, useEncryption = true) => {
    try {
      const res = await authApi.login({ username, password }, useEncryption)
      if (res.code === 200 && res.data) {
        const loginData = res.data as LoginResponse
        if (loginData.token) {
          setToken(loginData.token)
        } else {
          setToken(loginData.id || '')
        }

        const { token: _, roles: __, ...userWithoutToken } = loginData
        setUserInfo({ ...userWithoutToken, roles: loginData.roles })

        return true
      }
      return false
    } catch (error) {
      console.error('Login failed:', error)
      return false
    }
  }

  const logout = async () => {
    const router = useRouter()
    const currentUser = userInfo.value
    try {
      await authApi.logout()
      if (currentUser) {
        auditService.logLogout({
          userId: currentUser.id,
          username: currentUser.username
        })
      }
    } catch (error) {
      console.error('Logout failed:', error)
    } finally {
      token.value = ''
      userInfo.value = null
      userRoles.value = []
      localStorage.removeItem('token')
      localStorage.removeItem('userInfo')
      localStorage.removeItem('userRoles')
      router.push('/')
    }
  }

  const fetchUserInfo = async () => {
    try {
      const res = await authApi.getCurrentUser()
      if (res.code === 200 && res.data) {
        setUserInfo(res.data)

        if (userInfo.value?.id && userRoles.value.length === 0) {
          try {
            const roleRes = await rolesApi.getUserRoles(userInfo.value.id)
            if (roleRes.code === 200 && roleRes.data) {
              setUserRoles(roleRes.data)
            }
          } catch (error) {
            console.error('Failed to fetch user roles from API:', error)
          }
        }
      }
    } catch (error) {
      console.error('Failed to fetch user info:', error)
    }
  }

  const loadStoredRoles = () => {
    const stored = localStorage.getItem('userRoles')
    if (stored) {
      try {
        const parsed = JSON.parse(stored)
        if (Array.isArray(parsed) && parsed.length > 0) {
          userRoles.value = parsed
        }
      } catch {
        userRoles.value = []
      }
    }
  }

  const clearUserRoles = () => {
    userRoles.value = []
    localStorage.removeItem('userRoles')
  }

  loadStoredRoles()

  return {
    token,
    userInfo,
    userRoles,
    primaryRole,
    primaryRoleLevel,
    isLoggedIn,
    isAdmin,
    isDeptAdmin,
    isKnowledgeAdmin,
    getMaxLevel,
    hasRole,
    hasAnyRole,
    hasLevel,
    hasPermission,
    hasAnyPermission,
    canAccessPage,
    setToken,
    setUserInfo,
    setUserRoles,
    setUserRolesFromLegacyRole,
    login,
    logout,
    fetchUserInfo,
    loadStoredRoles,
    clearUserRoles
  }
})
