<template>
  <div class="config-form">
    <el-form :model="formData" label-width="140px" style="max-width: 600px" v-if="!loading">
      <el-form-item label="服务地址">
        <el-input v-model="formData.host" placeholder="localhost" />
      </el-form-item>
      <el-form-item label="REST API端口">
        <el-input-number v-model="formData.port" :min="1" :max="65535" />
      </el-form-item>
      <el-form-item label="gRPC端口">
        <el-input-number v-model="formData.grpcPort" :min="1" :max="65535" />
      </el-form-item>
      <el-form-item label="超时时间(s)">
        <el-input-number v-model="formData.timeout" :min="1" :max="60" />
      </el-form-item>
      <el-form-item label="最大连接数">
        <el-input-number v-model="formData.maxConnections" :min="1" :max="100" />
      </el-form-item>
      <el-form-item label="空闲超时时间(s)">
        <el-input-number v-model="formData.idleTimeout" :min="60" :max="3600" />
      </el-form-item>
      <el-divider>集合配置</el-divider>
      <el-form-item label="默认集合名称">
        <el-input v-model="formData.collectionDefaultName" />
      </el-form-item>
      <el-form-item label="向量维度">
        <el-input-number v-model="formData.vectorSize" :min="128" :max="4096" />
      </el-form-item>
      <el-form-item label="距离度量">
        <el-select v-model="formData.distance" style="width: 100%">
          <el-option label="Cosine" value="Cosine" />
          <el-option label="Euclid" value="Euclid" />
          <el-option label="Dot" value="Dot" />
        </el-select>
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
  port: 6333,
  grpcPort: 6334,
  timeout: 5,
  maxConnections: 32,
  idleTimeout: 300,
  collectionDefaultName: 'medical_documents',
  vectorSize: 1024,
  distance: 'Cosine'
})

const loadConfig = async () => {
  loading.value = true
  try {
    await configStore.loadConfigByCategory('qdrant')
    const configs = configStore.configs['qdrant'] || []
    for (const config of configs) {
      if (config.configKey === 'host') formData.host = config.value
      if (config.configKey === 'port') formData.port = parseInt(config.value)
      if (config.configKey === 'grpcPort') formData.grpcPort = parseInt(config.value)
      if (config.configKey === 'timeout') formData.timeout = parseInt(config.value)
      if (config.configKey === 'maxConnections') formData.maxConnections = parseInt(config.value)
      if (config.configKey === 'idleTimeout') formData.idleTimeout = parseInt(config.value)
      if (config.configKey === 'collection.defaultName') formData.collectionDefaultName = config.value
      if (config.configKey === 'collection.vectorSize') formData.vectorSize = parseInt(config.value)
      if (config.configKey === 'collection.distance') formData.distance = config.value
    }
  } finally {
    loading.value = false
  }
}

const handleSave = async () => {
  saving.value = true
  try {
    await configStore.batchUpdateConfig('qdrant', {
      host: formData.host,
      port: formData.port.toString(),
      grpcPort: formData.grpcPort.toString(),
      timeout: formData.timeout.toString(),
      maxConnections: formData.maxConnections.toString(),
      idleTimeout: formData.idleTimeout.toString(),
      'collection.defaultName': formData.collectionDefaultName,
      'collection.vectorSize': formData.vectorSize.toString(),
      'collection.distance': formData.distance
    })
    ElMessage.success('Qdrant配置已保存')
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