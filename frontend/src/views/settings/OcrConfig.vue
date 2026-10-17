<template>
  <div class="config-form">
    <el-form :model="formData" label-width="140px" style="max-width: 600px" v-if="!loading">
      <el-form-item label="GLM-OCR服务地址">
        <el-input v-model="formData.serviceUrl" placeholder="http://localhost:8860" />
      </el-form-item>
      <el-form-item label="Qwen兜底服务地址">
        <el-input v-model="formData.fallbackUrl" placeholder="http://localhost:8860" />
      </el-form-item>
      <el-form-item label="请求超时(ms)">
        <el-input-number v-model="formData.timeout" :min="1000" :max="120000" :step="1000" />
      </el-form-item>
      <el-form-item label="缓存TTL(分钟)">
        <el-input-number v-model="formData.maxCacheTtl" :min="1" :max="1440" />
      </el-form-item>
      <el-form-item label="最大预览页数">
        <el-input-number v-model="formData.maxPages" :min="1" :max="100" />
      </el-form-item>
      <el-form-item label="启用OCR功能">
        <el-switch v-model="formData.enabled" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleSave" :loading="saving">保存配置</el-button>
      </el-form-item>
    </el-form>
    <el-skeleton v-else :rows="5" animated />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useConfigStore } from '@/stores/config'

const configStore = useConfigStore()
const loading = ref(true)
const saving = ref(false)

const formData = reactive({
  serviceUrl: 'http://localhost:8860',
  fallbackUrl: 'http://localhost:8860',
  timeout: 30000,
  maxCacheTtl: 30,
  maxPages: 10,
  enabled: true
})

const loadConfig = async () => {
  loading.value = true
  try {
    await configStore.loadConfigByCategory('ocr')
    const configs = configStore.configs['ocr'] || []
    for (const config of configs) {
      if (config.configKey === 'serviceUrl') formData.serviceUrl = config.value
      if (config.configKey === 'fallbackUrl') formData.fallbackUrl = config.value
      if (config.configKey === 'timeout') formData.timeout = parseInt(config.value)
      if (config.configKey === 'maxCacheTtl') formData.maxCacheTtl = parseInt(config.value)
      if (config.configKey === 'maxPages') formData.maxPages = parseInt(config.value)
      if (config.configKey === 'enabled') formData.enabled = config.value === 'true'
    }
  } finally {
    loading.value = false
  }
}

const handleSave = async () => {
  saving.value = true
  try {
    await configStore.batchUpdateConfig('ocr', {
      serviceUrl: formData.serviceUrl,
      fallbackUrl: formData.fallbackUrl,
      timeout: formData.timeout.toString(),
      maxCacheTtl: formData.maxCacheTtl.toString(),
      maxPages: formData.maxPages.toString(),
      enabled: formData.enabled.toString()
    })
    ElMessage.success('OCR配置已保存')
  } catch (e) {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadConfig()
})
</script>