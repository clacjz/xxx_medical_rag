import { request } from './request'
import { encryptPassword } from '@/utils/crypto'
import type { User, LoginRequest, RegisterRequest } from '@/types'

export const authApi = {
  getPublicKey: async () => {
    const res = await request<{ publicKey: string }>('/auth/public-key', {
      method: 'GET'
    })
    return res.data?.publicKey || ''
  },

  login: async (data: LoginRequest, useEncryption = true) => {
    if (useEncryption) {
      try {
        const encryptedPassword = await encryptPassword(data.password)
        return request<User>('/auth/login', {
          method: 'POST',
          data: {
            username: data.username,
            password: encryptedPassword
          }
        })
      } catch (error) {
        console.error('Encryption failed:', error)
        throw error
      }
    } else {
      return request<User>('/auth/login', { method: 'POST', data })
    }
  },

  register: async (data: RegisterRequest, useEncryption = true) => {
    if (useEncryption) {
      try {
        const encryptedPassword = await encryptPassword(data.password)
        return request<User>('/auth/register', {
          method: 'POST',
          data: {
            username: data.username,
            password: encryptedPassword,
            email: data.email
          }
        })
      } catch (error) {
        console.error('Encryption failed:', error)
        throw error
      }
    } else {
      return request<User>('/auth/register', { method: 'POST', data })
    }
  },

  logout: () => {
    return request('/auth/logout', { method: 'POST' })
  },

  refreshToken: () => {
    return request<{ token: string }>('/auth/refresh', { method: 'POST' })
  },

  getCurrentUser: () => {
    return request<User>('/auth/current')
  }
}
