<template>
  <div class="config-form">
    <el-form :model="formData" label-width="140px" style="max-width: 600px" v-if="!loading">
      <el-form-item label="Top-K">
        <el-input-number v-model="formData.topK" :min="1" :max="20" />
        <span class="form-tip">返回的检索结果数量</span>
      </el-form-item>
      <el-form-item label="相似度阈值">
        <el-input-number v-model="formData.similarityThreshold" :min="0" :max="1" :step="0.05" :precision="2" />
        <span class="form-tip">0-1之间的值</span>
      </el-form-item>
      <el-form-item label="启用Rerank">
        <el-switch v-model="formData.enableRerank" />
        <span class="form-tip">对检索结果进行重排序</span>
      </el-form-item>
      <el-form-item label="历史对话轮数">
        <el-input-number v-model="formData.historyTurns" :min="0" :max="10" />
        <span class="form-tip">多轮对话携带的历史轮数</span>
      </el-form-item>
      <el-form-item label="历史窗口大小">
        <el-input-number v-model="formData.historyWindow" :min="500" :max="5000" :step="100" />
        <span class="form-tip">历史内容的Token上限</span>
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
  topK: 5,
  similarityThreshold: 0.7,
  enableRerank: true,
  historyTurns: 3,
  historyWindow: 2000
})

const loadConfig = async () => {
  loading.value = true
  try {
    await configStore.loadConfigByCategory('retrieval')
    const configs = configStore.configs['retrieval'] || []
    for (const config of configs) {
      if (config.configKey === 'topK') formData.topK = parseInt(config.value)
      if (config.configKey === 'similarityThreshold') formData.similarityThreshold = parseFloat(config.value)
      if (config.configKey === 'enableRerank') formData.enableRerank = config.value === 'true'
      if (config.configKey === 'historyTurns') formData.historyTurns = parseInt(config.value)
      if (config.configKey === 'historyWindow') formData.historyWindow = parseInt(config.value)
    }
  } finally {
    loading.value = false
  }
}

const handleSave = async () => {
  saving.value = true
  try {
    await configStore.batchUpdateConfig('retrieval', {
      topK: formData.topK.toString(),
      similarityThreshold: formData.similarityThreshold.toString(),
      enableRerank: formData.enableRerank.toString(),
      historyTurns: formData.historyTurns.toString(),
      historyWindow: formData.historyWindow.toString()
    })
    ElMessage.success('检索配置已保存')
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