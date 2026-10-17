<template>
  <div class="config-form">
    <el-form :model="formData" label-width="140px" style="max-width: 700px" v-if="!loading">
      <el-form-item label="服务地址">
        <el-input v-model="formData.host" placeholder="localhost" />
      </el-form-item>
      <el-form-item label="服务端口">
        <el-input-number v-model="formData.port" :min="1" :max="65535" />
      </el-form-item>
      <el-form-item label="模型名称">
        <el-select v-model="formData.model" style="width: 100%">
          <el-option label="Qwen3.6-27BB" value="Qwen3.6-27BB" />
          <el-option label="Qwen3.5-14B" value="Qwen3.5-14B" />
          <el-option label="Qwen3.5-32B" value="Qwen3.5-32B" />
        </el-select>
      </el-form-item>

      <el-divider content-position="left">采样参数 (Sampling Parameters)</el-divider>

      <el-form-item label="Temperature">
        <el-slider v-model="formData.sampling.temperature" :min="0" :max="2" :step="0.05" show-input />
        <div class="param-hint">控制随机性，较高值=更随机，较低值=更确定性 (推荐: 0.7)</div>
      </el-form-item>

      <el-form-item label="Top_P">
        <el-slider v-model="formData.sampling.topP" :min="0" :max="100" :step="5" show-input />
        <div class="param-hint">核采样阈值，较低值=更集中 (推荐: 80)</div>
      </el-form-item>

      <el-form-item label="Top_K">
        <el-input-number v-model="formData.sampling.topK" :min="1" :max="100" :step="5" />
        <div class="param-hint">从top_k个token中采样 (推荐: 20)</div>
      </el-form-item>

      <el-form-item label="Min_P">
        <el-slider v-model="formData.sampling.minP" :min="0" :max="1" :step="0.05" show-input />
        <div class="param-hint">最小token概率阈值 (推荐: 0)</div>
      </el-form-item>

      <el-form-item label="Presence Penalty">
        <el-slider v-model="formData.sampling.presencePenalty" :min="0" :max="2" :step="0.1" show-input />
        <div class="param-hint">惩罚已出现的token，值越高越避免重复 (推荐: 1.5)</div>
      </el-form-item>

      <el-form-item label="Repetition Penalty">
        <el-slider v-model="formData.sampling.repetitionPenalty" :min="1" :max="2" :step="0.05" show-input />
        <div class="param-hint">重复惩罚，1.0=无惩罚 (推荐: 1.0)</div>
      </el-form-item>

      <el-divider content-position="left">生成参数</el-divider>

      <el-form-item label="最大Token数">
        <el-input-number v-model="formData.maxTokens" :min="100" :max="128000" :step="256" />
        <div class="param-hint">单次生成的最大token数 (128K上下文推荐: 4096)</div>
      </el-form-item>

      <el-form-item label="超时时间(ms)">
        <el-input-number v-model="formData.timeout" :min="1000" :max="300000" :step="1000" />
      </el-form-item>

      <el-form-item>
        <el-button type="primary" @click="handleSave" :loading="saving">保存配置</el-button>
        <el-button @click="handleReset">重置为推荐值</el-button>
      </el-form-item>
    </el-form>
    <el-skeleton v-else :rows="10" animated />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useConfigStore } from '@/stores/config'

const configStore = useConfigStore()
const loading = ref(true)
const saving = ref(false)

// 知识库问答推荐配置 (Instruct mode for general tasks)
const RECOMMENDED_CONFIG = {
  host: 'localhost',
  port: 11434,
  model: 'Qwen3.6-27BB',
  sampling: {
    temperature: 0.7,
    topP: 80,
    topK: 20,
    minP: 0,
    presencePenalty: 1.5,
    repetitionPenalty: 1.0
  },
  maxTokens: 4096,
  timeout: 60000
}

const formData = reactive({
  host: 'localhost',
  port: 11434,
  model: 'Qwen3.6-27BB',
  sampling: {
    temperature: 0.7,
    topP: 80,
    topK: 20,
    minP: 0,
    presencePenalty: 1.5,
    repetitionPenalty: 1.0
  },
  maxTokens: 4096,
  timeout: 60000
})

// 配置键到formData路径的映射
const configMapper: Record<string, (v: string) => void> = {
  host: v => formData.host = v,
  port: v => formData.port = parseInt(v),
  model: v => formData.model = v,
  temperature: v => formData.sampling.temperature = parseFloat(v),
  topP: v => formData.sampling.topP = parseInt(v),
  topK: v => formData.sampling.topK = parseInt(v),
  minP: v => formData.sampling.minP = parseFloat(v),
  presencePenalty: v => formData.sampling.presencePenalty = parseFloat(v),
  repetitionPenalty: v => formData.sampling.repetitionPenalty = parseFloat(v),
  maxTokens: v => formData.maxTokens = parseInt(v),
  timeout: v => formData.timeout = parseInt(v)
}

const loadConfig = async () => {
  loading.value = true
  try {
    await configStore.loadConfigByCategory('llm')
    const configs = configStore.configs['llm'] || []
    for (const config of configs) {
      const mapper = configMapper[config.configKey]
      if (mapper) mapper(config.value)
    }
  } finally {
    loading.value = false
  }
}

const handleSave = async () => {
  saving.value = true
  try {
    await configStore.batchUpdateConfig('llm', {
      host: formData.host,
      port: formData.port.toString(),
      model: formData.model,
      temperature: formData.sampling.temperature.toString(),
      topP: formData.sampling.topP.toString(),
      topK: formData.sampling.topK.toString(),
      minP: formData.sampling.minP.toString(),
      presencePenalty: formData.sampling.presencePenalty.toString(),
      repetitionPenalty: formData.sampling.repetitionPenalty.toString(),
      maxTokens: formData.maxTokens.toString(),
      timeout: formData.timeout.toString()
    })
    ElMessage.success('LLM配置已保存')
  } catch (e) {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

const handleReset = () => {
  Object.assign(formData, JSON.parse(JSON.stringify(RECOMMENDED_CONFIG)))
  ElMessage.info('已重置为推荐配置 (知识库问答)')
}

onMounted(() => {
  loadConfig()
})
</script>

<style scoped>
.param-hint {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
</style>