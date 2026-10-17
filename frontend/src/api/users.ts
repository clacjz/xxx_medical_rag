import { request } from './request'
import { encryptPassword } from '@/utils/crypto'
import type { User, PageResult } from '@/types'

export const usersApi = {
  list: (params?: { page?: number; pageSize?: number; keyword?: string; role?: string }) => {
    return request<PageResult<User>>('/users', { method: 'GET', params })
  },

  getById: (id: string) => {
    return request<User>(`/users/${id}`)
  },

  create: async (data: { username: string; password: string; email: string; role: string }, useEncryption = true) => {
    let password = data.password

    if (useEncryption) {
      try {
        password = await encryptPassword(password)
      } catch (error) {
        console.warn('Password encryption failed, using plain text')
      }
    }

    return request<User>('/users', {
      method: 'POST',
      data: { ...data, password }
    })
  },

  update: (id: string, data: Partial<User>) => {
    return request<User>(`/users/${id}`, { method: 'POST', data })
  },

  delete: (id: string) => {
    return request(`/users/${id}`, { method: 'DELETE' })
  },

  updatePassword: async (id: string, oldPassword: string, newPassword: string, useEncryption = true) => {
    let encryptedOldPassword = oldPassword
    let encryptedNewPassword = newPassword

    if (useEncryption) {
      try {
        encryptedOldPassword = await encryptPassword(oldPassword)
        encryptedNewPassword = await encryptPassword(newPassword)
      } catch (error) {
        console.warn('Password encryption failed, using plain text')
      }
    }

    return request(`/users/${id}/password`, {
      method: 'POST',
      data: { oldPassword: encryptedOldPassword, newPassword: encryptedNewPassword }
    })
  },

  assignRole: (userId: string, roleId: string) => {
    return request(`/users/${userId}/role`, { method: 'POST', data: { roleId } })
  }
}
