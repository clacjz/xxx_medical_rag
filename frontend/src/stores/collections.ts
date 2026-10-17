import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { collectionsApi } from '@/api/collections'
import type { 
  MultiCollection, 
  CollectionCreateRequest,
  CollectionUpdateRequest,
  CollectionConfig,
  CollectionDocument,
  CollectionMetrics,
  CollectionType,
  CollectionStatus
} from '@/types'

export interface CollectionFilters {
  keyword?: string
  type?: CollectionType
  status?: CollectionStatus
}

export const useCollectionStore = defineStore('collections', () => {
  const collections = ref<MultiCollection[]>([])
  const currentCollection = ref<MultiCollection | null>(null)
  const currentCollectionDocuments = ref<CollectionDocument[]>([])
  const currentCollectionMetrics = ref<CollectionMetrics[]>([])
  const currentCollectionConfig = ref<CollectionConfig | null>(null)
  
  const loading = ref(false)
  const documentsLoading = ref(false)
  const metricsLoading = ref(false)
  
  const pagination = ref({
    page: 0,
    pageSize: 20,
    total: 0,
    totalPages: 0
  })

  const documentsPagination = ref({
    page: 0,
    pageSize: 20,
    total: 0,
    totalPages: 0
  })

  const filters = ref<CollectionFilters>({})

  const activeCollections = computed(() => 
    collections.value.filter(c => c.status === 'ACTIVE')
  )

  const collectionsByType = computed(() => {
    const grouped: Record<string, MultiCollection[]> = {}
    for (const col of collections.value) {
      const type = col.type || 'UNKNOWN'
      if (!grouped[type]) {
        grouped[type] = []
      }
      grouped[type].push(col)
    }
    return grouped
  })

  const loadCollections = async (params?: {
    page?: number
    pageSize?: number
    keyword?: string
    type?: CollectionType
    status?: CollectionStatus
  }) => {
    loading.value = true
    try {
      const res = await collectionsApi.list({
        page: params?.page ?? pagination.value.page,
        pageSize: params?.pageSize ?? pagination.value.pageSize,
        keyword: params?.keyword ?? filters.value.keyword,
        type: params?.type ?? filters.value.type,
        status: params?.status ?? filters.value.status
      })
      
      if (res.code === 200 && res.data) {
        collections.value = res.data.items
        pagination.value.total = res.data.total
        pagination.value.totalPages = res.data.totalPages
      }
    } catch (error) {
      console.error('Failed to load collections:', error)
      throw error
    } finally {
      loading.value = false
    }
  }

  const loadCollection = async (id: string) => {
    loading.value = true
    try {
      const res = await collectionsApi.getById(id)
      if (res.code === 200 && res.data) {
        currentCollection.value = res.data
        return res.data
      }
      return null
    } catch (error) {
      console.error('Failed to load collection:', error)
      throw error
    } finally {
      loading.value = false
    }
  }

  const createCollection = async (data: CollectionCreateRequest) => {
    loading.value = true
    try {
      const res = await collectionsApi.create(data)
      if (res.code === 200 && res.data) {
        collections.value.unshift(res.data)
        pagination.value.total++
        return res.data
      }
      return null
    } catch (error) {
      console.error('Failed to create collection:', error)
      throw error
    } finally {
      loading.value = false
    }
  }

  const updateCollection = async (id: string, data: CollectionUpdateRequest) => {
    loading.value = true
    try {
      const res = await collectionsApi.update(id, data)
      if (res.code === 200 && res.data) {
        const index = collections.value.findIndex(c => c.id === id)
        if (index !== -1) {
          collections.value[index] = res.data
        }
        if (currentCollection.value?.id === id) {
          currentCollection.value = res.data
        }
        return res.data
      }
      return null
    } catch (error) {
      console.error('Failed to update collection:', error)
      throw error
    } finally {
      loading.value = false
    }
  }

  const deleteCollection = async (id: string) => {
    loading.value = true
    try {
      const res = await collectionsApi.delete(id)
      if (res.code === 200) {
        collections.value = collections.value.filter(c => c.id !== id)
        pagination.value.total--
        if (currentCollection.value?.id === id) {
          currentCollection.value = null
        }
        return true
      }
      return false
    } catch (error) {
      console.error('Failed to delete collection:', error)
      throw error
    } finally {
      loading.value = false
    }
  }

  const archiveCollection = async (id: string) => {
    try {
      const res = await collectionsApi.archive(id)
      if (res.code === 200 && res.data) {
        await refreshCollection(id)
        return true
      }
      return false
    } catch (error) {
      console.error('Failed to archive collection:', error)
      throw error
    }
  }

  const restoreCollection = async (id: string) => {
    try {
      const res = await collectionsApi.restore(id)
      if (res.code === 200 && res.data) {
        await refreshCollection(id)
        return true
      }
      return false
    } catch (error) {
      console.error('Failed to restore collection:', error)
      throw error
    }
  }

  const refreshCollection = async (id: string) => {
    const index = collections.value.findIndex(c => c.id === id)
    if (index !== -1) {
      const updated = await loadCollection(id)
      if (updated) {
        collections.value[index] = updated
      }
    }
  }

  const loadCollectionConfig = async (id: string) => {
    try {
      const res = await collectionsApi.getConfig(id)
      if (res.code === 200 && res.data) {
        currentCollectionConfig.value = res.data
        return res.data
      }
      return null
    } catch (error) {
      console.error('Failed to load collection config:', error)
      throw error
    }
  }

  const updateCollectionConfig = async (id: string, config: Partial<CollectionConfig>) => {
    try {
      const res = await collectionsApi.updateConfig(id, config)
      if (res.code === 200 && res.data) {
        currentCollectionConfig.value = res.data.config || null
        await refreshCollection(id)
        return true
      }
      return false
    } catch (error) {
      console.error('Failed to update collection config:', error)
      throw error
    }
  }

  const loadCollectionDocuments = async (id: string, params?: { page?: number; pageSize?: number; status?: string }) => {
    documentsLoading.value = true
    try {
      const res = await collectionsApi.getDocuments(id, {
        page: params?.page ?? documentsPagination.value.page,
        pageSize: params?.pageSize ?? documentsPagination.value.pageSize,
        status: params?.status
      })
      
      if (res.code === 200 && res.data) {
        currentCollectionDocuments.value = res.data.items
        documentsPagination.value.total = res.data.total
        documentsPagination.value.totalPages = res.data.totalPages
      }
    } catch (error) {
      console.error('Failed to load collection documents:', error)
      throw error
    } finally {
      documentsLoading.value = false
    }
  }

  const addDocumentsToCollection = async (collectionId: string, documentIds: string[], priority = 0) => {
    try {
      const res = await collectionsApi.addDocuments(collectionId, documentIds, priority)
      if (res.code === 200) {
        await loadCollectionDocuments(collectionId)
        await refreshCollection(collectionId)
        return true
      }
      return false
    } catch (error) {
      console.error('Failed to add documents to collection:', error)
      throw error
    }
  }

  const removeDocumentFromCollection = async (collectionId: string, documentId: string) => {
    try {
      const res = await collectionsApi.removeDocument(collectionId, documentId)
      if (res.code === 200) {
        currentCollectionDocuments.value = currentCollectionDocuments.value.filter(
          d => d.documentId !== documentId
        )
        await refreshCollection(collectionId)
        return true
      }
      return false
    } catch (error) {
      console.error('Failed to remove document from collection:', error)
      throw error
    }
  }

  const loadCollectionMetrics = async (id: string, params?: { startDate?: string; endDate?: string; metricTypes?: string }) => {
    metricsLoading.value = true
    try {
      const res = await collectionsApi.getMetrics(id, params)
      if (res.code === 200 && res.data) {
        currentCollectionMetrics.value = res.data
        return res.data
      }
      return []
    } catch (error) {
      console.error('Failed to load collection metrics:', error)
      throw error
    } finally {
      metricsLoading.value = false
    }
  }

  const duplicateCollection = async (id: string, newName: string) => {
    try {
      const res = await collectionsApi.duplicate(id, newName)
      if (res.code === 200 && res.data) {
        collections.value.unshift(res.data)
        pagination.value.total++
        return res.data
      }
      return null
    } catch (error) {
      console.error('Failed to duplicate collection:', error)
      throw error
    }
  }

  const setFilters = (newFilters: CollectionFilters) => {
    filters.value = { ...newFilters }
  }

  const clearFilters = () => {
    filters.value = {}
  }

  const clearCurrentCollection = () => {
    currentCollection.value = null
    currentCollectionDocuments.value = []
    currentCollectionMetrics.value = []
    currentCollectionConfig.value = null
  }

  return {
    collections,
    currentCollection,
    currentCollectionDocuments,
    currentCollectionMetrics,
    currentCollectionConfig,
    loading,
    documentsLoading,
    metricsLoading,
    pagination,
    documentsPagination,
    filters,
    activeCollections,
    collectionsByType,
    loadCollections,
    loadCollection,
    createCollection,
    updateCollection,
    deleteCollection,
    archiveCollection,
    restoreCollection,
    refreshCollection,
    loadCollectionConfig,
    updateCollectionConfig,
    loadCollectionDocuments,
    addDocumentsToCollection,
    removeDocumentFromCollection,
    loadCollectionMetrics,
    duplicateCollection,
    setFilters,
    clearFilters,
    clearCurrentCollection
  }
})
