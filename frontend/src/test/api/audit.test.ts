import { vi, describe, it, expect, beforeEach, Mock } from 'vitest'
import { auditApi } from '@/api/audit'

vi.mock('@/api/request', () => {
  return {
    request: vi.fn(),
    axiosInstance: {
      get: vi.fn()
    }
  }
})

import { request, axiosInstance } from '@/api/request'

describe('Audit API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('auditApi.list', () => {
    it('should call /audit with startTime and endTime', async () => {
      ;(request as Mock).mockResolvedValue({
        code: 200,
        data: { items: [], total: 0 }
      })

      await auditApi.list({
        page: 0,
        pageSize: 20,
        startTime: 1000,
        endTime: 2000
      })

      expect(request).toHaveBeenCalledWith('/audit', {
        method: 'GET',
        params: {
          page: 0,
          pageSize: 20,
          userId: undefined,
          action: undefined,
          startTime: 1000,
          endTime: 2000
        }
      })
    })

    it('should pass userId and action filters', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200, data: { items: [] } })

      await auditApi.list({
        userId: 'u-1',
        action: 'LOGIN',
        startTime: 0,
        endTime: 1
      })

      expect(request).toHaveBeenCalledWith('/audit', {
        method: 'GET',
        params: {
          page: undefined,
          pageSize: undefined,
          userId: 'u-1',
          action: 'LOGIN',
          startTime: 0,
          endTime: 1
        }
      })
    })
  })

  describe('auditApi.export', () => {
    it('should export as CSV with blob responseType', async () => {
      const blob = new Blob(['csv data'], { type: 'text/csv' })
      ;(axiosInstance.get as any).mockResolvedValue({ data: blob })

      await auditApi.export({ startTime: 0, endTime: 100, format: 'csv' })

      expect(axiosInstance.get).toHaveBeenCalledWith('/audit/export', {
        params: { startTime: 0, endTime: 100, format: 'csv' },
        responseType: 'blob'
      })
    })

    it('should export as JSON with blob responseType', async () => {
      const blob = new Blob(['{}'], { type: 'application/json' })
      ;(axiosInstance.get as any).mockResolvedValue({ data: blob })

      await auditApi.export({ startTime: 0, endTime: 100, format: 'json' })

      expect(axiosInstance.get).toHaveBeenCalledWith('/audit/export', {
        params: { startTime: 0, endTime: 100, format: 'json' },
        responseType: 'blob'
      })
    })
  })
})
