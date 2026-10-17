import { useUserStore } from '@/stores/user'
import type { RoleLevel } from '@/stores/user'

export const PermissionCodes = {
  USER_MANAGE: 'user:manage',
  USER_CREATE: 'user:create',
  USER_UPDATE: 'user:update',
  USER_DELETE: 'user:delete',

  ROLE_MANAGE: 'role:manage',
  ROLE_CREATE: 'role:create',
  ROLE_UPDATE: 'role:update',
  ROLE_DELETE: 'role:delete',

  DEPARTMENT_MANAGE: 'department:manage',
  DEPARTMENT_CREATE: 'department:create',
  DEPARTMENT_UPDATE: 'department:update',
  DEPARTMENT_DELETE: 'department:delete',

  DOCUMENT_UPLOAD: 'document:upload',
  DOCUMENT_READ: 'document:read',
  DOCUMENT_UPDATE: 'document:update',
  DOCUMENT_DELETE: 'document:delete',

  COLLECTION_MANAGE: 'collection:manage',
  COLLECTION_CREATE: 'collection:create',
  COLLECTION_UPDATE: 'collection:update',
  COLLECTION_DELETE: 'collection:delete',

  QA_ACCESS: 'qa:access',
  QA_QUERY: 'qa:query',

  AUDIT_VIEW: 'audit:view',

  SETTINGS_VIEW: 'settings:view',
  SETTINGS_UPDATE: 'settings:update'
}

export const RoleCodeMap = {
  SYSTEM_ADMIN: 'SYSTEM_ADMIN',
  DEPT_ADMIN: 'DEPT_ADMIN',
  DEPT_UPLOADER: 'DEPT_UPLOADER',
  DEPT_USER: 'DEPT_USER'
}

export const usePermission = () => {
  const userStore = useUserStore()

  const checkRole = (roleCode: string): boolean => {
    return userStore.hasRole(roleCode)
  }

  const checkAnyRole = (roleCodes: string[]): boolean => {
    return userStore.hasAnyRole(roleCodes)
  }

  const checkLevel = (level: RoleLevel): boolean => {
    return userStore.hasLevel(level)
  }

  const checkPermission = (permission: string): boolean => {
    return userStore.hasPermission(permission)
  }

  const checkAnyPermission = (permissions: string[]): boolean => {
    return userStore.hasAnyPermission(permissions)
  }

  const isSystemAdmin = (): boolean => {
    return userStore.isAdmin
  }

  const isDepartmentAdmin = (): boolean => {
    return userStore.isDeptAdmin
  }

  const isKnowledgeManager = (): boolean => {
    return userStore.isKnowledgeAdmin
  }

  const canManageUsers = (): boolean => {
    return isSystemAdmin()
  }

  const canManageRoles = (): boolean => {
    return isSystemAdmin()
  }

  const canManageDepartments = (): boolean => {
    return checkAnyRole([RoleCodeMap.SYSTEM_ADMIN, RoleCodeMap.DEPT_ADMIN])
  }

  const canUploadDocuments = (): boolean => {
    return checkAnyRole([RoleCodeMap.SYSTEM_ADMIN, RoleCodeMap.DEPT_ADMIN, RoleCodeMap.DEPT_UPLOADER])
  }

  const canViewAuditLogs = (): boolean => {
    return isSystemAdmin()
  }

  const canAccessQA = (): boolean => {
    return true
  }

  const canManageSettings = (): boolean => {
    return isSystemAdmin()
  }

  return {
    checkRole,
    checkAnyRole,
    checkLevel,
    checkPermission,
    checkAnyPermission,
    isSystemAdmin,
    isDepartmentAdmin,
    isKnowledgeManager,
    canManageUsers,
    canManageRoles,
    canManageDepartments,
    canUploadDocuments,
    canViewAuditLogs,
    canAccessQA,
    canManageSettings
  }
}

export const usePageAccess = () => {
  const userStore = useUserStore()

  const canAccessPage = (pageName: string): boolean => {
    const pageAccessMap: Record<string, string[]> = {
      'Dashboard': [],
      'Collections': [],
      'Documents': [],
      'QA': [],
      'Conversations': [],
      'Users': [RoleCodeMap.SYSTEM_ADMIN],
      'RBAC': [RoleCodeMap.SYSTEM_ADMIN, RoleCodeMap.DEPT_ADMIN],
      'Audit': [RoleCodeMap.SYSTEM_ADMIN],
      'Settings': [RoleCodeMap.SYSTEM_ADMIN]
    }

    const requiredRoles = pageAccessMap[pageName] || []
    return userStore.canAccessPage(requiredRoles)
  }

  const getAccessiblePages = (): string[] => {
    const allPages = ['Dashboard', 'Collections', 'Documents', 'QA', 'Conversations', 'Users', 'RBAC', 'Audit', 'Settings']
    return allPages.filter(page => canAccessPage(page))
  }

  return {
    canAccessPage,
    getAccessiblePages
  }
}
