<template>
  <div class="config-form">
    <el-form :model="formData" label-width="140px" style="max-width: 600px" v-if="!loading">
      <el-form-item label="默认分块大小">
        <el-input-number v-model="formData.chunkSize" :min="100" :max="2000" />
        <span class="form-tip">字符数</span>
      </el-form-item>
      <el-form-item label="分块重叠">
        <el-input-number v-model="formData.chunkOverlap" :min="0" :max="500" />
        <span class="form-tip">字符数</span>
      </el-form-item>
      <el-form-item label="最小分块长度">
        <el-input-number v-model="formData.minChunkLength" :min="10" :max="200" />
        <span class="form-tip">字符数</span>
      </el-form-item>
      <el-form-item label="最大文件大小">
        <el-input-number v-model="formData.maxFileSize" :min="1" :max="500" />
        <span class="form-tip">MB</span>
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
  chunkSize: 500,
  chunkOverlap: 50,
  minChunkLength: 50,
  maxFileSize: 100
})

const loadConfig = async () => {
  loading.value = true
  try {
    await configStore.loadConfigByCategory('document')
    const configs = configStore.configs['document'] || []
    for (const config of configs) {
      if (config.configKey === 'chunkSize') formData.chunkSize = parseInt(config.value)
      if (config.configKey === 'chunkOverlap') formData.chunkOverlap = parseInt(config.value)
      if (config.configKey === 'minChunkLength') formData.minChunkLength = parseInt(config.value)
      if (config.configKey === 'maxFileSize') formData.maxFileSize = parseInt(config.value)
    }
  } finally {
    loading.value = false
  }
}

const handleSave = async () => {
  saving.value = true
  try {
    await configStore.batchUpdateConfig('document', {
      chunkSize: formData.chunkSize.toString(),
      chunkOverlap: formData.chunkOverlap.toString(),
      minChunkLength: formData.minChunkLength.toString(),
      maxFileSize: formData.maxFileSize.toString()
    })
    ElMessage.success('文档处理配置已保存')
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