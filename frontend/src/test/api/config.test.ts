import { vi, describe, it, expect, beforeEach, Mock } from 'vitest'
import { configApi, CONFIG_CATEGORIES } from '@/api/config'

vi.mock('@/api/request', () => ({
  request: vi.fn()
}))

import { request } from '@/api/request'

describe('Config API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('configApi.getAll', () => {
    it('should call /config endpoint', async () => {
      ;(request as Mock).mockResolvedValue({
        code: 200,
        data: [{ id: '1', configKey: 'k', value: 'v' }]
      })

      const result = await configApi.getAll()

      expect(request).toHaveBeenCalledWith('/config', { method: 'GET' })
      expect(result.data).toHaveLength(1)
    })
  })

  describe('configApi.getByCategory', () => {
    it('should call /config/category/{category}', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200, data: [] })

      await configApi.getByCategory('llm')

      expect(request).toHaveBeenCalledWith('/config/category/llm', { method: 'GET' })
    })
  })

  describe('configApi.getByKey', () => {
    it('should call /config/{key}', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200, data: { configKey: 'k' } })

      await configApi.getByKey('embedding.model')

      expect(request).toHaveBeenCalledWith('/config/embedding.model')
    })
  })

  describe('configApi.update', () => {
    it('should PUT with value as query param', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200 })

      await configApi.update('embedding.model', 'bge-large')

      expect(request).toHaveBeenCalledWith('/config/embedding.model', {
        method: 'PUT',
        params: { value: 'bge-large' }
      })
    })
  })

  describe('configApi.batchUpdate', () => {
    it('should PUT data to /config/category/{category}', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200, data: [] })

      await configApi.batchUpdate('llm', { 'llm.temperature': '0.7' })

      expect(request).toHaveBeenCalledWith('/config/category/llm', {
        method: 'PUT',
        data: { 'llm.temperature': '0.7' }
      })
    })

    it('should send empty values object', async () => {
      ;(request as Mock).mockResolvedValue({ code: 200, data: [] })

      await configApi.batchUpdate('llm', {})

      expect(request).toHaveBeenCalledWith('/config/category/llm', {
        method: 'PUT',
        data: {}
      })
    })
  })

  describe('CONFIG_CATEGORIES', () => {
    it('should include all 9 categories', () => {
      expect(CONFIG_CATEGORIES).toHaveLength(9)
    })

    it('should have unique keys', () => {
      const keys = CONFIG_CATEGORIES.map(c => c.key)
      const uniqueKeys = new Set(keys)
      expect(uniqueKeys.size).toBe(keys.length)
    })

    it('should have non-empty labels', () => {
      CONFIG_CATEGORIES.forEach(c => {
        expect(c.label.length).toBeGreaterThan(0)
      })
    })
  })
})
