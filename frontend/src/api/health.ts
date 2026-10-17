import { request } from './request'
import type { Result, HealthStatus } from '@/types'

export const healthApi = {
  check: () => {
    return request<Result<HealthStatus>>('/health')
  }
}
