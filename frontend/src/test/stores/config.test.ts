import { describe, it, expect, beforeEach, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useConfigStore } from '@/stores/config'
import { configApi } from '@/api/config'

vi.mock('@/api/config', () => ({
  configApi: {
    getAll: vi.fn(),
    getByCategory: vi.fn(),
    getByKey: vi.fn(),
    update: vi.fn(),
    batchUpdate: vi.fn()
  }
}))

describe('useConfigStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  describe('initial state', () => {
    it('should have empty configs and loading false', () => {
      const store = useConfigStore()
      expect(store.configs).toEqual({})
      expect(store.loading).toBe(false)
    })
  })

  describe('loadConfigByCategory', () => {
    it('should store configs by category on success', async () => {
      vi.mocked(configApi.getByCategory).mockResolvedValue({
        code: 200,
        data: [{ id: '1', configKey: 'k', value: 'v' } as any]
      } as any)

      const store = useConfigStore()
      await store.loadConfigByCategory('llm')

      expect(configApi.getByCategory).toHaveBeenCalledWith('llm')
      expect(store.configs.llm).toHaveLength(1)
    })

    it('should not store when code != 200', async () => {
      vi.mocked(configApi.getByCategory).mockResolvedValue({
        code: 500,
        data: [{ id: '1' } as any]
      } as any)

      const store = useConfigStore()
      await store.loadConfigByCategory('llm')

      expect(store.configs.llm).toBeUndefined()
    })

    it('should set and reset loading flag', async () => {
      let loadingDuringCall = false
      vi.mocked(configApi.getByCategory).mockImplementation(async () => {
        loadingDuringCall = (useConfigStore() as any).loading
        return { code: 200, data: [] } as any
      })

      await useConfigStore().loadConfigByCategory('llm')

      expect(loadingDuringCall).toBe(true)
      expect(useConfigStore().loading).toBe(false)
    })

    it('should preserve configs of other categories', async () => {
      vi.mocked(configApi.getByCategory).mockImplementation(async (cat) => {
        return { code: 200, data: [{ id: cat, configKey: cat } as any] } as any
      })

      const store = useConfigStore()
      await store.loadConfigByCategory('llm')
      await store.loadConfigByCategory('embedding')

      expect(store.configs.llm).toBeDefined()
      expect(store.configs.embedding).toBeDefined()
    })
  })

  describe('updateConfig', () => {
    it('should call API and return updated config', async () => {
      vi.mocked(configApi.update).mockResolvedValue({
        code: 200,
        data: { id: '1', configKey: 'k', value: 'new' } as any
      } as any)

      const store = useConfigStore()
      const result = await store.updateConfig('k', 'new')

      expect(configApi.update).toHaveBeenCalledWith('k', 'new')
      expect(result).toEqual({ id: '1', configKey: 'k', value: 'new' })
    })

    it('should return null on failure', async () => {
      vi.mocked(configApi.update).mockResolvedValue({ code: 500 } as any)

      const store = useConfigStore()
      const result = await store.updateConfig('k', 'v')

      expect(result).toBeNull()
    })
  })

  describe('batchUpdateConfig', () => {
    it('should call API with category and values', async () => {
      vi.mocked(configApi.batchUpdate).mockResolvedValue({ code: 200, data: [] } as any)

      const store = useConfigStore()
      await store.batchUpdateConfig('llm', { 'k1': 'v1' })

      expect(configApi.batchUpdate).toHaveBeenCalledWith('llm', { 'k1': 'v1' })
    })
  })
})
