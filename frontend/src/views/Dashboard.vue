<template>
  <div class="dashboard">
    <div class="dashboard-header">
      <div class="header-content">
        <h1 class="page-title">仪表盘</h1>
        <p class="page-subtitle">欢迎回来，今天是美好的一天</p>
      </div>
      <el-button type="primary" class="refresh-btn" @click="loadData">
        <el-icon><Refresh /></el-icon> 刷新数据
      </el-button>
    </div>

    <!-- Stats Row -->
<el-row :gutter="24" class="stats-row stagger-children">
      <el-col :xs="24" :sm="12" :lg="6" v-for="(stat, index) in stats" :key="stat.title" :style="{ '--index': index }">
        <div class="stat-card" :style="{ animationDelay: `${index * 0.1}s` }">
          <div class="stat-bg-icon">
            <el-icon :size="80" :style="{ color: stat.color }">
              <component :is="stat.icon" />
            </el-icon>
          </div>
          <div class="stat-header">
            <div class="stat-icon-wrapper" :style="{ backgroundColor: stat.bgColor }">
              <el-icon class="stat-icon" :style="{ color: stat.color }" :size="24">
                <component :is="stat.icon" />
              </el-icon>
            </div>
            <div class="stat-change" v-if="stat.change">
              <el-icon :size="14"><Top /></el-icon>
              <span>{{ stat.change }}</span>
            </div>
          </div>
          <div class="stat-value">{{ stat.value }}</div>
          <div class="stat-title">{{ stat.title }}</div>
          <div class="stat-bar">
            <div class="stat-bar-fill" :style="{ background: `linear-gradient(90deg, ${stat.color}, ${stat.color}88)` }"></div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- Content Row -->
    <el-row :gutter="24" class="content-row">
      <el-col :xs="24" :lg="12">
        <el-card class="content-card" shadow="hover">
          <template #header>
            <div class="card-header">
              <div class="card-title-wrapper">
                <el-icon class="card-icon" :color="healthStatus.status === 'UP' ? 'var(--success)' : healthStatus.status === 'DEGRADED' ? 'var(--warning)' : 'var(--danger)'"><component :is="healthStatus.status === 'UP' ? 'CircleCheck' : healthStatus.status === 'DEGRADED' ? 'WarningFilled' : 'CircleClose'" /></el-icon>
                <span class="card-title">系统健康状态</span>
              </div>
              <el-tag :type="healthStatus.status === 'UP' ? 'success' : healthStatus.status === 'DEGRADED' ? 'warning' : 'danger'" effect="light" size="small" round>
                <el-icon :size="12"><component :is="healthStatus.status === 'UP' ? 'CircleCheck' : healthStatus.status === 'DEGRADED' ? 'WarningFilled' : 'CircleClose'" /></el-icon>
                {{ healthStatus.status === 'UP' ? '全部正常' : healthStatus.status === 'DEGRADED' ? '部分异常' : '异常' }}
              </el-tag>
            </div>
          </template>
          <div class="health-list" v-loading="healthLoading">
            <div
              v-for="(service, name) in healthStatus.services"
              :key="name"
              class="health-item"
            >
              <div class="service-info">
                <img
                  v-if="getServiceIcon(String(name))"
                  :src="getServiceIcon(String(name))"
                  :alt="getServiceName(String(name))"
                  class="service-icon"
                />
                <el-icon v-else :size="20" :color="service.status === 'UP' ? '#67c23a' : '#f56c6c'">
                  <component :is="service.status === 'UP' ? 'CircleCheck' : 'CircleClose'" />
                </el-icon>
                <span class="service-name">{{ getServiceName(String(name)) }}</span>
              </div>
              <div class="service-meta">
                <span class="response-time">{{ service.responseTime }}ms</span>
                <el-tag :type="service.status === 'UP' ? 'success' : 'danger'" size="small" effect="light">
                  {{ service.status === 'UP' ? '正常' : '异常' }}
                </el-tag>
              </div>
            </div>
            <el-empty v-if="Object.keys(healthStatus.services).length === 0" description="暂无服务状态" />
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="12">
        <el-card class="content-card" shadow="hover">
          <template #header>
            <div class="card-header">
              <div class="card-title-wrapper">
                <el-icon class="card-icon" color="var(--accent)"><Document /></el-icon>
                <span class="card-title">最近文档处理</span>
              </div>
              <el-button type="primary" text @click="router.push('/documents')">查看更多 →</el-button>
            </div>
          </template>
          <div class="documents-list" v-loading="documentsLoading">
            <div
              v-for="doc in recentDocuments"
              :key="doc.id"
              class="document-item"
            >
              <div class="doc-icon-wrapper">
                <el-icon :size="20" color="var(--accent)"><Document /></el-icon>
              </div>
              <div class="doc-info">
                <span class="doc-name">{{ doc.name }}</span>
                <span class="doc-meta">{{ doc.collectionName }} · {{ formatDate(doc.createdAt) }}</span>
              </div>
              <el-tag :type="getStatusType(getStatusName(doc.status))" size="small" effect="light">
                {{ getStatusName(doc.status) }}
              </el-tag>
            </div>
            <el-empty v-if="recentDocuments.length === 0" description="暂无文档" />
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Quick Actions -->
    <el-card class="quick-actions-card" shadow="hover">
      <template #header>
        <div class="card-header">
          <div class="card-title-wrapper">
            <el-icon class="card-icon" color="var(--accent)"><Promotion /></el-icon>
            <span class="card-title">快速入口</span>
          </div>
        </div>
      </template>
      <el-row :gutter="16">
        <el-col :xs="12" :sm="6" v-for="action in quickActions" :key="action.name">
          <div class="quick-action" @click="router.push(action.path)">
            <div class="action-icon" :style="{ backgroundColor: action.bgColor }">
              <el-icon :size="28" :style="{ color: action.color }">
                <component :is="action.icon" />
              </el-icon>
            </div>
            <span class="action-name">{{ action.name }}</span>
            <div class="action-arrow">
              <el-icon><ArrowRight /></el-icon>
            </div>
          </div>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, markRaw } from 'vue'
import { useRouter } from 'vue-router'
import { healthApi, documentsApi, collectionsApi, chatApi, usersApi } from '@/api'
import { formatDate, getStatusName, getStatusType } from '@/utils/format'
import {
  Collection,
  Document,
  ChatDotRound,
  User,
  Refresh,
  Top,
  Upload,
  ChatLineSquare,
  ArrowRight,
  Promotion
} from '@element-plus/icons-vue'
import type { HealthStatus, Document as DocType } from '@/types'
import postgresqlIcon from '@/assets/icons/postgresql.svg'
import redisIcon from '@/assets/icons/redis.svg'
import qdrantIcon from '@/assets/icons/qdrant.svg'
import minioIcon from '@/assets/icons/minio.svg'
import elasticsearchIcon from '@/assets/icons/elasticsearch-svg.png'
import etcdIcon from '@/assets/icons/etcd.svg'

const router = useRouter()

const stats = reactive([
  { title: '知识库数量', value: '0', icon: markRaw(Collection), color: '#409eff', bgColor: 'rgba(64, 158, 255, 0.1)', change: '' },
  { title: '文档总数', value: '0', icon: markRaw(Document), color: '#67c23a', bgColor: 'rgba(103, 194, 58, 0.1)', change: '' },
  { title: '问答次数', value: '0', icon: markRaw(ChatDotRound), color: '#e6a23c', bgColor: 'rgba(230, 162, 60, 0.1)', change: '' },
  { title: '用户数量', value: '0', icon: markRaw(User), color: '#f56c6c', bgColor: 'rgba(245, 108, 108, 0.1)', change: '' }
])

const quickActions = [
  { name: '知识库管理', icon: markRaw(Collection), color: '#409eff', bgColor: 'rgba(64, 158, 255, 0.1)', path: '/collections' },
  { name: '文档上传', icon: markRaw(Upload), color: '#67c23a', bgColor: 'rgba(103, 194, 58, 0.1)', path: '/documents' },
  { name: '智能问答', icon: markRaw(ChatDotRound), color: '#e6a23c', bgColor: 'rgba(230, 162, 60, 0.1)', path: '/qa' },
  { name: '对话历史', icon: markRaw(ChatLineSquare), color: '#722ed1', bgColor: 'rgba(114, 46, 209, 0.1)', path: '/conversations' }
]

const serviceIcons: Record<string, string> = {
  'postgresql': postgresqlIcon,
  'redis': redisIcon,
  'qdrant': qdrantIcon,
  'minio': minioIcon,
  'llm': minioIcon,
  'elasticsearch': elasticsearchIcon,
  'etcd': etcdIcon
}

const getServiceIcon = (name: string) => {
  return serviceIcons[name] || ''
}

const healthStatus = reactive<HealthStatus>({ status: 'UP', services: {} })
const healthLoading = ref(false)
const recentDocuments = ref<DocType[]>([])
const documentsLoading = ref(false)
const collectionsLoading = ref(false)
const totalCollections = ref(0)
const totalDocuments = ref(0)
const totalQA = ref(0)
const totalUsers = ref(0)

const serviceNameMap: Record<string, string> = {
  'postgresql': 'PostgreSQL',
  'redis': 'Redis',
  'qdrant': 'Qdrant',
  'minio': 'MinIO',
  'llm': 'VLLM',
  'elasticsearch': 'Elasticsearch',
  'etcd': 'etcd'
}

const getServiceName = (key: string): string => {
  return serviceNameMap[key] || key
}

const loadCollectionStats = async () => {
  collectionsLoading.value = true
  try {
    const res = await collectionsApi.list({ page: 0, pageSize: 1 })
    if (res.code === 200 && res.data) {
      totalCollections.value = res.data.total || 0
      stats[0].value = String(totalCollections.value)
    }
  } catch (error) {
    console.error('Failed to load collection stats:', error)
  } finally {
    collectionsLoading.value = false
  }
}

const loadDocumentStats = async () => {
  documentsLoading.value = true
  try {
    const res = await documentsApi.list({ page: 0, pageSize: 1 })
    if (res.code === 200 && res.data) {
      totalDocuments.value = res.data.total || 0
      stats[1].value = String(totalDocuments.value)
    }
  } catch (error) {
    console.error('Failed to load document stats:', error)
  } finally {
    documentsLoading.value = false
  }
}

const loadQAStats = async () => {
  try {
    const res = await chatApi.listSessions({ page: 0, pageSize: 1 })
    if (res.code === 200 && res.data) {
      totalQA.value = res.data.total || 0
      stats[2].value = String(totalQA.value)
    }
  } catch (error) {
    console.error('Failed to load QA stats:', error)
  }
}

const loadUserStats = async () => {
  try {
    const res = await usersApi.list({ page: 0, pageSize: 1 })
    if (res.code === 200 && res.data) {
      totalUsers.value = res.data.total || 0
      stats[3].value = String(totalUsers.value)
    }
  } catch (error) {
    console.error('Failed to load user stats:', error)
  }
}

const loadHealth = async () => {
  healthLoading.value = true
  try {
    const res = await healthApi.check()
    if (res.code === 200 && res.data) {
      Object.assign(healthStatus, res.data)
    }
  } catch (error) {
    console.error('Failed to load health status:', error)
  } finally {
    healthLoading.value = false
  }
}

const loadRecentDocuments = async () => {
  documentsLoading.value = true
  try {
    const res = await documentsApi.list({ page: 0, pageSize: 5 })
    if (res.code === 200 && res.data) {
      recentDocuments.value = res.data.items
    }
  } catch (error) {
    console.error('Failed to load documents:', error)
  } finally {
    documentsLoading.value = false
  }
}

const loadData = () => {
  Promise.all([
    loadHealth(),
    loadRecentDocuments(),
    loadCollectionStats(),
    loadDocumentStats(),
    loadQAStats(),
    loadUserStats()
  ])
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.dashboard {
  padding: 0;
  animation: fadeIn 0.5s ease;
}

.dashboard-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 32px;
  padding: 24px 0;
}

.header-content {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.page-title {
  font-size: 28px;
  font-weight: 600;
  color: var(--foreground);
  margin: 0;
  letter-spacing: var(--tracking-tight);
}

.page-subtitle {
  font-size: 14px;
  color: var(--muted-foreground);
  margin: 0;
}

.refresh-btn {
  --el-button-bg-color: var(--accent);
  --el-button-border-color: var(--accent);
  --el-button-hover-bg-color: var(--accent-dark);
  --el-button-hover-border-color: var(--accent-dark);
  border-radius: var(--radius);
  padding: 12px 20px;
}

.stats-row {
  margin-bottom: 24px;
}

.stat-card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius-xl);
  padding: 24px;
  margin-bottom: 16px;
  transition: all var(--transition-slow);
  position: relative;
  overflow: hidden;
  animation: slideUp 0.5s ease both;
}

.stat-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 4px;
  height: 100%;
  background: linear-gradient(180deg, var(--accent), var(--accent-dark));
  opacity: 0;
  transition: opacity var(--transition);
}

@keyframes slideUp {
  from { opacity: 0; transform: translateY(20px); }
  to { opacity: 1; transform: translateY(0); }
}

.stat-card:hover {
  transform: translateY(-6px);
  box-shadow: var(--shadow-lg);
  border-color: var(--accent-light);
}

.stat-card:hover::before {
  opacity: 1;
}

.stat-bg-icon {
  position: absolute;
  right: -10px;
  bottom: -10px;
  opacity: 0.06;
  transform: rotate(-15deg);
}

.stat-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 20px;
  position: relative;
  z-index: 1;
}

.stat-icon-wrapper {
  width: 52px;
  height: 52px;
  border-radius: var(--radius);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--shadow-sm);
}

.stat-change {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--success);
  background: var(--success-light);
  padding: 4px 10px;
  border-radius: 20px;
}

.stat-value {
  font-size: 36px;
  font-weight: 700;
  color: var(--foreground);
  margin-bottom: 8px;
  letter-spacing: -1px;
  position: relative;
  z-index: 1;
}

.stat-title {
  font-size: 14px;
  color: var(--muted-foreground);
  font-weight: 500;
  position: relative;
  z-index: 1;
}

.stat-bar {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 4px;
  background: var(--muted);
}

.stat-bar-fill {
  height: 100%;
  border-radius: 0 2px 2px 0;
  transition: width 1s ease;
}

.content-row {
  margin-bottom: 24px;
}

.content-card {
  border-radius: var(--radius-xl);
  border: 1px solid var(--border);
  box-shadow: var(--shadow-sm);
  transition: all var(--transition);
}

.content-card:hover {
  box-shadow: var(--shadow-md);
}

.content-card :deep(.el-card__header) {
  padding: 20px 24px;
  border-bottom: 1px solid var(--border);
}

.content-card :deep(.el-card__body) {
  padding: 20px 24px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title-wrapper {
  display: flex;
  align-items: center;
  gap: 10px;
}

.card-icon {
  font-size: 20px;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--foreground);
}

.health-icon {
  color: var(--success);
}

.health-list {
  min-height: 120px;
}

.health-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  background: var(--muted);
  border-radius: var(--radius);
  margin-bottom: 12px;
  transition: all var(--transition);
  border: 1px solid transparent;
}

.health-item:hover {
  background: var(--card);
  border-color: var(--border);
  transform: translateX(4px);
}

.health-item:last-child {
  margin-bottom: 0;
}

.service-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.service-icon {
  width: 24px;
  height: 24px;
  object-fit: contain;
}

.service-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--foreground);
}

.service-meta {
  display: flex;
  align-items: center;
  gap: 12px;
}

.response-time {
  font-size: 12px;
  color: var(--muted-foreground);
}

.documents-list {
  min-height: 120px;
}

.document-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  border-radius: var(--radius);
  transition: all var(--transition);
  margin-bottom: 8px;
  background: var(--muted);
}

.document-item:hover {
  background: var(--card);
  transform: translateX(4px);
  box-shadow: var(--shadow-sm);
}

.document-item:last-child {
  margin-bottom: 0;
}

.doc-icon-wrapper {
  width: 40px;
  height: 40px;
  background: rgba(64, 158, 255, 0.1);
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.doc-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
  min-width: 0;
}

.doc-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--foreground);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.doc-meta {
  font-size: 12px;
  color: var(--muted-foreground);
}

.quick-actions-card {
  border-radius: var(--radius-xl);
  border: 1px solid var(--border);
  box-shadow: var(--shadow-sm);
}

.quick-actions-card :deep(.el-card__header) {
  padding: 20px 24px;
  border-bottom: 1px solid var(--border);
}

.quick-actions-card :deep(.el-card__body) {
  padding: 20px 24px;
}

.quick-action {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 28px 20px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius-xl);
  cursor: pointer;
  transition: all var(--transition-slow);
  position: relative;
  overflow: hidden;
}

.quick-action::before {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, transparent 0%, rgba(255, 255, 255, 0.8) 100%);
  opacity: 0;
  transition: opacity var(--transition);
}

.quick-action:hover {
  border-color: var(--accent-light);
  transform: translateY(-6px);
  box-shadow: var(--shadow-lg);
}

.quick-action:hover::before {
  opacity: 1;
}

.action-icon {
  width: 60px;
  height: 60px;
  border-radius: var(--radius-xl);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform var(--transition-slow);
  position: relative;
  z-index: 1;
}

.quick-action:hover .action-icon {
  transform: scale(1.1) rotate(5deg);
}

.action-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--foreground);
  position: relative;
  z-index: 1;
}

.action-arrow {
  position: absolute;
  right: 16px;
  top: 50%;
  transform: translateY(-50%);
  opacity: 0;
  transition: all var(--transition);
  color: var(--accent);
}

.quick-action:hover .action-arrow {
  opacity: 1;
  transform: translateY(-50%) translateX(4px);
}
</style>