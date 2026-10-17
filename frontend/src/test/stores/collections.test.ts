import { describe, it, expect, beforeEach, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useCollectionStore } from '@/stores/collections'
import { collectionsApi } from '@/api/collections'
import type { MultiCollection } from '@/types'

vi.mock('@/api/collections', () => ({
  collectionsApi: {
    list: vi.fn(),
    getById: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    delete: vi.fn(),
    getConfig: vi.fn(),
    updateConfig: vi.fn()
  }
}))

describe('useCollectionStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  describe('initial state', () => {
    it('should have empty collections and currentCollection null', () => {
      const store = useCollectionStore()
      expect(store.collections).toEqual([])
      expect(store.currentCollection).toBeNull()
    })

    it('should have default pagination', () => {
      const store = useCollectionStore()
      expect(store.pagination.page).toBe(0)
      expect(store.pagination.pageSize).toBe(20)
    })
  })

  describe('computed', () => {
    it('activeCollections filters by ACTIVE status', () => {
      const store = useCollectionStore()
      store.collections = [
        { id: 'c-1', name: 'A', status: 'ACTIVE' } as MultiCollection,
        { id: 'c-2', name: 'B', status: 'INACTIVE' } as MultiCollection,
        { id: 'c-3', name: 'C', status: 'ACTIVE' } as MultiCollection
      ]

      expect(store.activeCollections).toHaveLength(2)
      expect(store.activeCollections.map(c => c.id)).toEqual(['c-1', 'c-3'])
    })

    it('collectionsByType groups by type', () => {
      const store = useCollectionStore()
      store.collections = [
        { id: 'c-1', name: 'A', type: 'PUBLIC', documentCount: 0, chunkCount: 0, createdAt: '', updatedAt: '' } as unknown as MultiCollection,
        { id: 'c-2', name: 'B', type: 'PRIVATE', documentCount: 0, chunkCount: 0, createdAt: '', updatedAt: '' } as unknown as MultiCollection,
        { id: 'c-3', name: 'C', type: 'PUBLIC', documentCount: 0, chunkCount: 0, createdAt: '', updatedAt: '' } as unknown as MultiCollection,
        { id: 'c-4', name: 'D', documentCount: 0, chunkCount: 0, createdAt: '', updatedAt: '' } as unknown as MultiCollection
      ]

      const grouped = store.collectionsByType
      expect(grouped.PUBLIC).toHaveLength(2)
      expect(grouped.PRIVATE).toHaveLength(1)
      expect(grouped.UNKNOWN).toHaveLength(1)
    })
  })

  describe('loadCollections', () => {
    it('should fetch and store collections', async () => {
      vi.mocked(collectionsApi.list).mockResolvedValue({
        code: 200,
        data: {
          items: [{ id: 'c-1', name: 'A' } as MultiCollection],
          total: 1
        }
      } as any)

      const store = useCollectionStore()
      await store.loadCollections({ page: 0, pageSize: 10 })

      expect(collectionsApi.list).toHaveBeenCalled()
      expect(store.collections).toHaveLength(1)
      expect(store.pagination.total).toBe(1)
    })

    it('should set and reset loading flag', async () => {
      let loadingDuringCall = false
      vi.mocked(collectionsApi.list).mockImplementation(async () => {
        loadingDuringCall = (useCollectionStore() as any).loading
        return { code: 200, data: { items: [], total: 0 } } as any
      })

      await useCollectionStore().loadCollections()

      expect(loadingDuringCall).toBe(true)
      expect(useCollectionStore().loading).toBe(false)
    })
  })

  describe('createCollection', () => {
    it('should create and add to collections list', async () => {
      const created = { id: 'c-new', name: 'New' } as MultiCollection
      vi.mocked(collectionsApi.create).mockResolvedValue({ code: 200, data: created } as any)

      const store = useCollectionStore()
      const result = await store.createCollection({ name: 'New', type: 'PUBLIC' } as any)

      expect(result).toEqual(created)
      expect(store.collections[0]).toEqual(created)
    })
  })

  describe('updateCollection', () => {
    it('should call API and update collection in list', async () => {
      const updated = { id: 'c-1', name: 'Updated' } as MultiCollection
      vi.mocked(collectionsApi.update).mockResolvedValue({ code: 200, data: updated } as any)
      const store = useCollectionStore()
      store.collections = [{ id: 'c-1', name: 'A' } as MultiCollection]

      await store.updateCollection('c-1', { name: 'Updated' } as any)

      expect(collectionsApi.update).toHaveBeenCalled()
      expect(store.collections[0].name).toBe('Updated')
    })
  })

  describe('deleteCollection', () => {
    it('should call API and remove from list', async () => {
      vi.mocked(collectionsApi.delete).mockResolvedValue({ code: 200 } as any)
      const store = useCollectionStore()
      store.collections = [{ id: 'c-1', name: 'A' } as MultiCollection, { id: 'c-2', name: 'B' } as MultiCollection]

      await store.deleteCollection('c-1')

      expect(collectionsApi.delete).toHaveBeenCalledWith('c-1')
      expect(store.collections.find(c => c.id === 'c-1')).toBeUndefined()
    })

    it('should clear currentCollection if deleted', async () => {
      vi.mocked(collectionsApi.delete).mockResolvedValue({ code: 200 } as any)
      const store = useCollectionStore()
      store.currentCollection = { id: 'c-1', name: 'A' } as MultiCollection

      await store.deleteCollection('c-1')

      expect(store.currentCollection).toBeNull()
    })
  })
})
