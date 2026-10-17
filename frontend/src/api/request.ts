import axios, { AxiosInstance, AxiosRequestConfig, AxiosError } from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import type { Result } from '@/types'
import { connectionMonitor } from '@/services/connectionMonitor'

let isLoggingOut = false

const service: AxiosInstance = axios.create({
  baseURL: '/api/v1',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

const handleUnauthorized = (errorInfo?: { url?: string; method?: string }) => {
  if (isLoggingOut) {
    return
  }
  isLoggingOut = true

  const timestamp = new Date().toISOString()
  const logContext = {
    timestamp,
    url: errorInfo?.url || 'unknown',
    method: errorInfo?.method || 'unknown',
    userAgent: navigator.userAgent,
    currentPath: window.location.pathname
  }
  console.warn('[401 Unauthorized]', '用户认证失败或Token已过期，正在跳转登录页...', logContext)

  const userStore = useUserStore()
  userStore.token = ''
  userStore.userInfo = null
  localStorage.removeItem('token')
  localStorage.removeItem('userInfo')

  const currentPath = window.location.pathname
  const isLoginPage = currentPath === '/login' || currentPath === '/register'
  
  if (!isLoginPage) {
    window.location.href = '/'
  }

  setTimeout(() => {
    isLoggingOut = false
  }, 1000)
}

service.interceptors.request.use(
  (config) => {
    const userStore = useUserStore()
    if (userStore.token) {
      config.headers.Authorization = `Bearer ${userStore.token}`
    }
    return config
  },
  (error) => {
    console.error('[Request Interceptor Error]', error)
    return Promise.reject(error)
  }
)

service.interceptors.response.use(
  (response) => {
    // Skip interceptor for binary/blob responses (exports, file downloads)
    const responseType = response.config?.responseType
    if (responseType === 'blob' || responseType === 'arraybuffer') {
      return response
    }

    const res = response.data as Result

    connectionMonitor.recordNetworkSuccess()

    if (res.code !== 200) {
      const message = res.message || '请求失败'
      if (res.code === 401 || res.code === 403) {
        handleUnauthorized({
          url: response.config.url,
          method: response.config.method
        })
      } else {
        ElMessage.error(message)
      }
      return Promise.reject(new Error(message))
    }
    return response
  },
  (error: AxiosError<Result>) => {
    const status = error.response?.status
    const url = error.config?.url || 'unknown'
    const method = error.config?.method || 'unknown'

    if (status === 401) {
      handleUnauthorized({ url, method })
      return Promise.reject(error)
    }

    if (status === 403) {
      console.warn('[403 Forbidden]', `访问被拒绝: ${method} ${url}`)
      ElMessage.error('您没有权限执行此操作')
      return Promise.reject(error)
    }

    if (status === 404) {
      const message = error.response?.data?.message || '请求的资源不存在'
      console.warn('[404 Not Found]', `资源未找到: ${method} ${url}`)
      ElMessage.error(message)
      return Promise.reject(error)
    }

    if (status === 500) {
      const message = error.response?.data?.message || '服务器内部错误'
      console.error('[500 Internal Server Error]', `服务器错误: ${method} ${url}`, message)
      ElMessage.error(message)
      return Promise.reject(error)
    }

    const networkError = !error.response
    if (networkError) {
      console.error('[Network Error]', '网络连接失败，请检查网络', error.message)

      const isTimeout = error.code === 'ECONNABORTED' || error.message.includes('timeout')
      const isNetworkFailure = !navigator.onLine || error.code === 'ERR_NETWORK' || error.code === 'ERR_CONNECTION_REFUSED'

      if (isTimeout || isNetworkFailure) {
        connectionMonitor.recordNetworkError()
      } else {
        ElMessage.error('网络连接失败，请检查您的网络')
      }
    } else {
      const message = error.response?.data?.message || error.message || '请求失败'
      console.error(`[HTTP ${status}]`, `${method} ${url}`, message)
      ElMessage.error(message)
    }

    return Promise.reject(error)
  }
)

export { service as axiosInstance }

export const request = async <T = any>(
  url: string,
  config?: AxiosRequestConfig
): Promise<Result<T>> => {
  const finalConfig: AxiosRequestConfig = config || {}
  if (!finalConfig.url) {
    finalConfig.url = url
  }
  const response = await service<Result<T>>(finalConfig)
  return response.data
}
