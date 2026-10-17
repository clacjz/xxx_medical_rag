import { defineStore } from 'pinia'
import { ref } from 'vue'
import { configApi } from '@/api/config'
import type { SysConfig } from '@/api/config'

export const useConfigStore = defineStore('config', () => {
  const configs = ref<Record<string, SysConfig[]>>({})
  const loading = ref(false)

  const loadConfigByCategory = async (category: string) => {
    loading.value = true
    try {
      const res = await configApi.getByCategory(category)
      if (res.code === 200 && res.data) {
        configs.value[category] = res.data
      }
    } finally {
      loading.value = false
    }
  }

  const updateConfig = async (configKey: string, value: string) => {
    const res = await configApi.update(configKey, value)
    if (res.code === 200 && res.data) {
      return res.data
    }
    return null
  }

  const batchUpdateConfig = async (category: string, values: Record<string, string>) => {
    const res = await configApi.batchUpdate(category, values)
    if (res.code === 200 && res.data) {
      configs.value[category] = res.data
      return res.data
    }
    return null
  }

  const getConfigValue = (category: string, configKey: string): string | null => {
    const categoryConfigs = configs.value[category]
    if (!categoryConfigs) return null
    const config = categoryConfigs.find(c => c.configKey === configKey)
    return config?.value ?? null
  }

  return {
    configs,
    loading,
    loadConfigByCategory,
    updateConfig,
    batchUpdateConfig,
    getConfigValue
  }
})