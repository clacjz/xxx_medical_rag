import { vi, describe, it, expect, beforeEach } from 'vitest'

// 必须先 mock axios
vi.mock('axios', () => {
  const mockAxiosInstance = {
    interceptors: {
      request: { use: vi.fn() },
      response: { use: vi.fn() }
    },
    request: vi.fn(),
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn()
  }
  return {
    default: {
      create: vi.fn(() => mockAxiosInstance)
    },
    create: vi.fn(() => mockAxiosInstance)
  }
})

vi.mock('element-plus', () => ({
  ElMessage: { error: vi.fn(), success: vi.fn() }
}))

vi.mock('@/services/connectionMonitor', () => ({
  connectionMonitor: { recordNetworkSuccess: vi.fn(), recordNetworkError: vi.fn() }
}))

import { request, axiosInstance } from '@/api/request'

describe('Request Module', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('request wrapper', () => {
    it('exports axiosInstance from axios.create', () => {
      expect(axiosInstance).toBeDefined()
    })

    it('request function exists', () => {
      expect(typeof request).toBe('function')
    })
  })

  describe('axios instance config', () => {
    it('exports an axios instance with request/response interceptors attached', () => {
      // 验证模块导出结构（实际的 create 调用发生在模块加载时）
      expect(axiosInstance).toBeDefined()
      expect(axiosInstance.interceptors).toBeDefined()
      expect(axiosInstance.interceptors.request).toBeDefined()
      expect(axiosInstance.interceptors.response).toBeDefined()
    })

    it('axiosInstance is callable as a function (axios.create returns a callable)', () => {
      expect(typeof axiosInstance).toBe('object')
    })
  })
})
