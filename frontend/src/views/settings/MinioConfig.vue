<template>
  <div class="config-form">
    <el-form :model="formData" label-width="140px" style="max-width: 600px" v-if="!loading">
      <el-form-item label="服务地址">
        <el-input v-model="formData.host" placeholder="localhost" />
      </el-form-item>
      <el-form-item label="服务端口">
        <el-input-number v-model="formData.port" :min="1" :max="65535" />
      </el-form-item>
      <el-form-item label="Access Key">
        <el-input v-model="formData.accessKey" />
      </el-form-item>
      <el-form-item label="Secret Key">
        <el-input v-model="formData.secretKey" show-password />
      </el-form-item>
      <el-divider>桶配置</el-divider>
      <el-form-item label="文档存储桶">
        <el-input v-model="formData.bucketDocuments" />
      </el-form-item>
      <el-form-item label="临时文件桶">
        <el-input v-model="formData.bucketTemp" />
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
  host: 'localhost',
  port: 9000,
  accessKey: 'minioadmin',
  secretKey: 'minioadmin',
  bucketDocuments: 'rag-documents',
  bucketTemp: 'rag-temp'
})

const loadConfig = async () => {
  loading.value = true
  try {
    await configStore.loadConfigByCategory('minio')
    const configs = configStore.configs['minio'] || []
    for (const config of configs) {
      if (config.configKey === 'host') formData.host = config.value
      if (config.configKey === 'port') formData.port = parseInt(config.value)
      if (config.configKey === 'accessKey') formData.accessKey = config.value
      if (config.configKey === 'secretKey') formData.secretKey = config.value
      if (config.configKey === 'bucket.documents') formData.bucketDocuments = config.value
      if (config.configKey === 'bucket.temp') formData.bucketTemp = config.value
    }
  } finally {
    loading.value = false
  }
}

const handleSave = async () => {
  saving.value = true
  try {
    await configStore.batchUpdateConfig('minio', {
      host: formData.host,
      port: formData.port.toString(),
      accessKey: formData.accessKey,
      secretKey: formData.secretKey,
      'bucket.documents': formData.bucketDocuments,
      'bucket.temp': formData.bucketTemp
    })
    ElMessage.success('MinIO配置已保存')
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