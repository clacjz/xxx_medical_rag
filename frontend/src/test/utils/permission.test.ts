import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useUserStore } from '@/stores/user'

// 直接测试 PermissionCodes / RoleCodeMap 常量，因为 usePermission 需要 Vue 环境
import { PermissionCodes, RoleCodeMap } from '@/utils/permission'

describe('Permission Utils', () => {
  describe('PermissionCodes', () => {
    it('should include user management codes', () => {
      expect(PermissionCodes.USER_MANAGE).toBe('user:manage')
      expect(PermissionCodes.USER_CREATE).toBe('user:create')
      expect(PermissionCodes.USER_UPDATE).toBe('user:update')
      expect(PermissionCodes.USER_DELETE).toBe('user:delete')
    })

    it('should include role management codes', () => {
      expect(PermissionCodes.ROLE_MANAGE).toBe('role:manage')
      expect(PermissionCodes.ROLE_CREATE).toBe('role:create')
    })

    it('should include department codes', () => {
      expect(PermissionCodes.DEPARTMENT_MANAGE).toBe('department:manage')
      expect(PermissionCodes.DEPARTMENT_CREATE).toBe('department:create')
    })

    it('should include document codes', () => {
      expect(PermissionCodes.DOCUMENT_UPLOAD).toBe('document:upload')
      expect(PermissionCodes.DOCUMENT_READ).toBe('document:read')
    })

    it('should include collection codes', () => {
      expect(PermissionCodes.COLLECTION_MANAGE).toBe('collection:manage')
      expect(PermissionCodes.COLLECTION_CREATE).toBe('collection:create')
      expect(PermissionCodes.COLLECTION_UPDATE).toBe('collection:update')
      expect(PermissionCodes.COLLECTION_DELETE).toBe('collection:delete')
    })

    it('should include QA codes', () => {
      expect(PermissionCodes.QA_ACCESS).toBe('qa:access')
      expect(PermissionCodes.QA_QUERY).toBe('qa:query')
    })

    it('should include audit and settings codes', () => {
      expect(PermissionCodes.AUDIT_VIEW).toBe('audit:view')
      expect(PermissionCodes.SETTINGS_VIEW).toBe('settings:view')
      expect(PermissionCodes.SETTINGS_UPDATE).toBe('settings:update')
    })
  })

  describe('RoleCodeMap', () => {
    it('should map role codes', () => {
      expect(RoleCodeMap.SYSTEM_ADMIN).toBe('SYSTEM_ADMIN')
      expect(RoleCodeMap.DEPT_ADMIN).toBe('DEPT_ADMIN')
      expect(RoleCodeMap.DEPT_UPLOADER).toBe('DEPT_UPLOADER')
      expect(RoleCodeMap.DEPT_USER).toBe('DEPT_USER')
    })
  })

  describe('useUserStore integration', () => {
    beforeEach(() => {
      setActivePinia(createPinia())
    })

    it('user store hasRole delegates correctly', () => {
      const store = useUserStore()
      store.userRoles = [
        { id: '1', code: 'SYSTEM_ADMIN', name: 'Admin', level: 1, isPrimary: true }
      ]

      expect(store.hasRole('SYSTEM_ADMIN')).toBe(true)
      expect(store.hasRole('DEPT_USER')).toBe(false)
    })

    it('user store hasAnyRole supports multiple codes', () => {
      const store = useUserStore()
      store.userRoles = [
        { id: '1', code: 'DEPT_ADMIN', name: 'Dept Admin', level: 2, isPrimary: true }
      ]

      expect(store.hasAnyRole(['SYSTEM_ADMIN', 'DEPT_ADMIN'])).toBe(true)
      expect(store.hasAnyRole(['DEPT_USER', 'DEPT_UPLOADER'])).toBe(false)
    })

    it('user store hasLevel uses role level', () => {
      const store = useUserStore()
      store.userRoles = [
        { id: '1', code: 'DEPT_USER', name: 'User', level: 4, isPrimary: true }
      ]

      // level=4 用户只能满足 level >= 4 的检查
      expect(store.hasLevel(4)).toBe(true)
      expect(store.hasLevel(2)).toBe(false)
      expect(store.hasLevel(3)).toBe(false)
    })
  })
})
