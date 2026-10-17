import { request } from './request'
import type { Role, RolePermission } from '@/types'

export interface CreateRoleRequest {
  code: string
  name: string
  level: number
  description?: string
  permissions?: string[]
}

export interface UpdateRoleRequest {
  name?: string
  description?: string
  permissions?: string[]
}

export interface AssignRoleRequest {
  roleId: string
  isPrimary?: boolean
}

export const rolesApi = {
  list: () => {
    return request<Role[]>('/roles', { method: 'GET' })
  },

  listActive: () => {
    return request<Role[]>('/roles/active', { method: 'GET' })
  },

  getById: (id: string) => {
    return request<Role>(`/roles/${id}`)
  },

  getByCode: (code: string) => {
    return request<Role>(`/roles/code/${code}`)
  },

  getByLevel: (level: number) => {
    return request<Role[]>(`/roles/level/${level}`)
  },

  create: (data: CreateRoleRequest) => {
    return request<Role>('/roles', {
      method: 'POST',
      data
    })
  },

  update: (id: string, data: UpdateRoleRequest) => {
    return request<Role>(`/roles/${id}`, {
      method: 'PUT',
      data
    })
  },

  delete: (id: string) => {
    return request<void>(`/roles/${id}`, { method: 'DELETE' })
  },

  getUserRoles: (userId: string) => {
    return request<Role[]>(`/roles/users/${userId}`)
  },

  assignRoleToUser: (userId: string, data: AssignRoleRequest) => {
    return request<void>(`/roles/users/${userId}/assign`, {
      method: 'POST',
      data
    })
  },

  assignRoleToUserSimple: (userId: string, roleId: string, isPrimary = false) => {
    return request<void>(`/roles/users/${userId}/assign/${roleId}`, {
      method: 'POST',
      params: { isPrimary }
    })
  },

  removeRoleFromUser: (userId: string, roleId: string) => {
    return request<void>(`/roles/users/${userId}/remove/${roleId}`, {
      method: 'DELETE'
    })
  },

  initializeDefaultRoles: () => {
    return request<string>('/roles/init', { method: 'POST' })
  },

  getAllPermissions: () => {
    return request<RolePermission[]>('/roles/permissions')
  },

  getRolePermissions: (roleId: string) => {
    return request<RolePermission>(`/roles/${roleId}/permissions`)
  },

  updateRolePermissions: (roleId: string, permissions: string) => {
    return request<RolePermission>(`/roles/${roleId}/permissions`, {
      method: 'PUT',
      data: { permissions }
    })
  }
}
