<template>
  <div class="config-form">
    <el-form :model="formData" label-width="140px" style="max-width: 600px" v-if="!loading">
      <el-form-item label="服务地址">
        <el-input v-model="formData.host" placeholder="localhost" />
      </el-form-item>
      <el-form-item label="服务端口">
        <el-input-number v-model="formData.port" :min="1" :max="65535" />
      </el-form-item>
      <el-form-item label="超时时间(ms)">
        <el-input-number v-model="formData.timeout" :min="1000" :max="60000" :step="1000" />
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
  port: 2379,
  timeout: 5000
})

const loadConfig = async () => {
  loading.value = true
  try {
    await configStore.loadConfigByCategory('etcd')
    const configs = configStore.configs['etcd'] || []
    for (const config of configs) {
      if (config.configKey === 'host') formData.host = config.value
      if (config.configKey === 'port') formData.port = parseInt(config.value)
      if (config.configKey === 'timeout') formData.timeout = parseInt(config.value)
    }
  } finally {
    loading.value = false
  }
}

const handleSave = async () => {
  saving.value = true
  try {
    await configStore.batchUpdateConfig('etcd', {
      host: formData.host,
      port: formData.port.toString(),
      timeout: formData.timeout.toString()
    })
    ElMessage.success('Etcd配置已保存')
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
