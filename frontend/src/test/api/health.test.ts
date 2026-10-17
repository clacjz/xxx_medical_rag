import { vi, describe, it, expect, beforeEach, Mock } from 'vitest'
import { healthApi } from '@/api/health'

vi.mock('@/api/request', () => ({
  request: vi.fn()
}))

import { request } from '@/api/request'

describe('Health API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('should call /health endpoint', async () => {
    ;(request as Mock).mockResolvedValue({
      code: 200,
      data: { status: 'UP', services: {} }
    } as any)

    const result = await healthApi.check() as any

    expect(request).toHaveBeenCalledWith('/health')
    expect(result.data.status).toBe('UP')
  })

  it('should return DOWN status gracefully', async () => {
    ;(request as Mock).mockResolvedValue({
      code: 200,
      data: { status: 'DOWN', services: { db: { status: 'DOWN', message: '', responseTime: 0 } } }
    } as any)

    const result = await healthApi.check() as any

    expect(result.data.status).toBe('DOWN')
  })

  it('should propagate error', async () => {
    ;(request as Mock).mockRejectedValue(new Error('Network error'))

    await expect(healthApi.check()).rejects.toThrow('Network error')
  })
})
