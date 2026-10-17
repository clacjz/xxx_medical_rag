<template>
  <div class="settings">
    <el-card>
      <template #header>
        <span>系统配置</span>
      </template>

      <el-tabs v-model="activeTab">
        <el-tab-pane label="Embedding配置" name="embedding">
          <embedding-config />
        </el-tab-pane>

        <el-tab-pane label="LLM配置" name="llm">
          <llm-config />
        </el-tab-pane>

        <el-tab-pane label="文档处理配置" name="document">
          <document-config />
        </el-tab-pane>

        <el-tab-pane label="检索配置" name="retrieval">
          <retrieval-config />
        </el-tab-pane>

        <el-tab-pane label="Qdrant配置" name="qdrant">
          <qdrant-config />
        </el-tab-pane>

        <el-tab-pane label="MinIO配置" name="minio">
          <minio-config />
        </el-tab-pane>

        <el-tab-pane label="OCR配置" name="ocr">
          <ocr-config />
        </el-tab-pane>

        <el-tab-pane label="Etcd配置" name="etcd">
          <etcd-config />
        </el-tab-pane>

        <el-tab-pane label="Elasticsearch配置" name="elasticsearch">
          <elasticsearch-config />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import EmbeddingConfig from './settings/EmbeddingConfig.vue'
import LlmConfig from './settings/LlmConfig.vue'
import DocumentConfig from './settings/DocumentConfig.vue'
import RetrievalConfig from './settings/RetrievalConfig.vue'
import QdrantConfig from './settings/QdrantConfig.vue'
import MinioConfig from './settings/MinioConfig.vue'
import OcrConfig from './settings/OcrConfig.vue'
import EtcdConfig from './settings/EtcdConfig.vue'
import ElasticsearchConfig from './settings/ElasticsearchConfig.vue'

const activeTab = ref('embedding')
</script>

<style scoped>
.settings {
  padding: 20px;
}
</style>