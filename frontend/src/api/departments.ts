import { request } from './request'
import type {
  Department,
  UserDepartment
} from '@/types'

export const departmentsApi = {
  getTree: () => {
    return request<Department[]>('/departments', { method: 'GET' })
  },

  getById: (id: string) => {
    return request<Department>(`/departments/${id}`)
  },

  create: (data: {
    code: string
    name: string
    parentId?: string | null
    managerId?: string
    description?: string
  }) => {
    return request<Department>('/departments', {
      method: 'POST',
      data
    })
  },

  update: (id: string, data: {
    name?: string
    parentId?: string | null
    managerId?: string
    description?: string
    status?: 'ACTIVE' | 'INACTIVE'
  }) => {
    return request<Department>(`/departments/${id}`, {
      method: 'POST',
      data
    })
  },

  delete: (id: string) => {
    return request<void>(`/departments/${id}`, { method: 'DELETE' })
  },

  getUsers: (id: string) => {
    return request<UserDepartment[]>(`/departments/${id}/users`)
  },

  addUser: (departmentId: string, data: {
    userId: string
    isPrimary?: boolean
    position?: string
  }) => {
    return request<void>(`/departments/${departmentId}/users`, {
      method: 'POST',
      data
    })
  },

  removeUser: (departmentId: string, userId: string) => {
    return request<void>(`/departments/${departmentId}/users/${userId}`, {
      method: 'DELETE'
    })
  }
}
