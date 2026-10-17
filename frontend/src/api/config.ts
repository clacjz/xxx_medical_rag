import { request } from './request'

export interface SysConfig {
  id: string
  category: string
  configKey: string
  value: string
  valueType: string
  description: string
  defaultValue: string
  validValues: string
}

export const configApi = {
  getAll: () => {
    return request<SysConfig[]>('/config', {
      method: 'GET'
    })
  },

  getByCategory: (category: string) => {
    return request<SysConfig[]>(`/config/category/${category}`, {
      method: 'GET'
    })
  },

  getByKey: (configKey: string) => {
    return request<SysConfig>(`/config/${configKey}`)
  },

  update: (configKey: string, value: string) => {
    return request<SysConfig>(`/config/${configKey}`, {
      method: 'PUT',
      params: { value }
    })
  },

  batchUpdate: (category: string, values: Record<string, string>) => {
    return request<SysConfig[]>(`/config/category/${category}`, {
      method: 'PUT',
      data: values
    })
  }
}

export type ConfigCategory = 'embedding' | 'llm' | 'document' | 'retrieval' | 'qdrant' | 'minio' | 'ocr' | 'etcd' | 'elasticsearch'

export const CONFIG_CATEGORIES: { key: ConfigCategory; label: string }[] = [
  { key: 'embedding', label: 'Embedding配置' },
  { key: 'llm', label: 'LLM配置' },
  { key: 'document', label: '文档处理配置' },
  { key: 'retrieval', label: '检索配置' },
  { key: 'qdrant', label: 'Qdrant配置' },
  { key: 'minio', label: 'MinIO配置' },
  { key: 'ocr', label: 'OCR配置' },
  { key: 'etcd', label: 'Etcd配置' },
  { key: 'elasticsearch', label: 'Elasticsearch配置' }
]