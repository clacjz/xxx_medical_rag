import { request, axiosInstance } from './request'
import type { AuditLog, PageResult } from '@/types'

export type ExportFormat = 'csv' | 'json'

export const auditApi = {
  list: (params: {
    page?: number
    pageSize?: number
    userId?: string
    action?: string
    startTime: number
    endTime: number
  }) => {
    return request<PageResult<AuditLog>>('/audit', { method: 'GET', params })
  },

  export: (params: { startTime: number; endTime: number; format: ExportFormat }) => {
    return axiosInstance.get('/audit/export', {
      params: {
        startTime: params.startTime,
        endTime: params.endTime,
        format: params.format
      },
      responseType: 'blob'
    })
  }
}
