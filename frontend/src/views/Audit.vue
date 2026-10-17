<template>
  <div class="audit">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>审计日志</span>
          <el-dropdown trigger="click" @command="handleExport">
            <el-button type="primary">
              <el-icon><Download /></el-icon> 导出
              <el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="csv">
                  <el-icon><Document /></el-icon> 导出为 CSV
                </el-dropdown-item>
                <el-dropdown-item command="json">
                  <el-icon><Document /></el-icon> 导出为 JSON
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </template>

      <div class="toolbar">
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="x"
          style="width: 300px"
        />
        <el-select v-model="filterCategory" placeholder="选择操作分类" clearable style="width: 180px" @change="onCategoryChange">
          <el-option
            v-for="cat in AuditActionCategoryList"
            :key="cat.value"
            :label="cat.label"
            :value="cat.value"
          />
        </el-select>
        <el-select v-model="filterAction" placeholder="选择操作类型" clearable style="width: 200px">
          <el-option
            v-for="action in filteredActionList"
            :key="action.value"
            :label="action.label"
            :value="action.value"
          />
        </el-select>
        <el-input
          v-model="keyword"
          placeholder="搜索用户名"
          style="width: 200px"
          clearable
          @clear="loadLogs"
          @keyup.enter="loadLogs"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-button @click="loadLogs">
          <el-icon><Refresh /></el-icon> 刷新
        </el-button>
      </div>

      <el-table :data="logs" v-loading="loading" style="width: 100%; margin-top: 16px">
        <el-table-column prop="username" label="用户" width="120" />
        <el-table-column prop="action" label="操作" width="120" align="center">
          <template #default="{ row }">
            <el-tag size="small">{{ row.actionDescription || AuditActionLabels[row.action as keyof typeof AuditActionLabels] || row.action }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="resourceType" label="资源类型" width="120">
          <template #default="{ row }">
            {{ row.resourceType || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="description" label="详情" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.description || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="ipAddress" label="IP地址" width="140">
          <template #default="{ row }">
            {{ row.ipAddress || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'success' ? 'success' : 'danger'" size="small">
              {{ row.status === 'success' ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="timestamp" label="时间" width="180">
          <template #default="{ row }">
            {{ formatDate(row.timestamp) }}
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.pageSize"
        :total="pagination.total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        style="margin-top: 16px; justify-content: flex-end"
        @size-change="loadLogs"
        @current-change="loadLogs"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { auditApi, type ExportFormat } from '@/api/audit'
import { formatDate } from '@/utils/format'
import { Download, Search, Refresh, Document, ArrowDown } from '@element-plus/icons-vue'
import type { AuditLog } from '@/types'
import { AuditAction, AuditActionLabels, AuditActionCategory, AuditActionCategoryLabels, AuditActionCategoryMap } from '@/types/enums'

const logs = ref<AuditLog[]>([])
const loading = ref(false)
const dateRange = ref<number[]>([])
const filterCategory = ref('')
const filterAction = ref('')
const keyword = ref('')

const AuditActionCategoryList = computed(() => {
  const categories = Object.values(AuditActionCategory)
  return categories.map(cat => ({
    value: cat,
    label: AuditActionCategoryLabels[cat as keyof typeof AuditActionCategoryLabels]
  }))
})

const filteredActionList = computed(() => {
  const actions = Object.values(AuditAction)
  if (filterCategory.value) {
    return actions
      .filter(action => AuditActionCategoryMap[action as keyof typeof AuditActionCategoryMap] === filterCategory.value)
      .map(action => ({
        value: action,
        label: AuditActionLabels[action as keyof typeof AuditActionLabels]
      }))
  }
  return actions.map(action => ({
    value: action,
    label: AuditActionLabels[action as keyof typeof AuditActionLabels]
  }))
})

const onCategoryChange = () => {
  filterAction.value = ''
}

const getDefaultDateRange = (): number[] => {
  const end = new Date()
  const start = new Date()
  start.setDate(end.getDate() - 7)
  return [start.getTime(), end.getTime()]
}

onMounted(() => {
  dateRange.value = getDefaultDateRange()
  loadLogs()
})

const pagination = reactive({
  page: 0,
  pageSize: 10,
  total: 0
})

const loadLogs = async () => {
  if (!dateRange.value || dateRange.value.length !== 2) {
    ElMessage.warning('请选择查询时间范围（默认最近7天）')
    return
  }

  loading.value = true
  try {
    const res = await auditApi.list({
      page: pagination.page,
      pageSize: pagination.pageSize,
      action: filterAction.value || undefined,
      startTime: dateRange.value[0],
      endTime: dateRange.value[1]
    })
    if (res.code === 200 && res.data) {
      logs.value = res.data.items
      pagination.total = res.data.total
    } else {
      ElMessage.error(res.message || '查询失败')
    }
  } catch (error) {
    console.error('Failed to load logs:', error)
    ElMessage.error('查询审计日志失败')
  } finally {
    loading.value = false
  }
}

const handleExport = async (format: ExportFormat) => {
  if (!dateRange.value || dateRange.value.length !== 2) {
    ElMessage.warning('请选择导出日期范围')
    return
  }

  try {
    ElMessage.info('正在导出...')
    const startDate = new Date(dateRange.value[0])
    const endDate = new Date(dateRange.value[1])
    const timestamp = `${startDate.toISOString().split('T')[0]}_${endDate.toISOString().split('T')[0]}`
    
    const res = await auditApi.export({
      startTime: dateRange.value[0],
      endTime: dateRange.value[1],
      format: format
    })
    
    const blob = new Blob([res as unknown as BlobPart], { 
      type: format === 'json' ? 'application/json' : 'text/csv;charset=utf-8' 
    })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `audit_logs_${timestamp}.${format}`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch (error) {
    console.error('Failed to export:', error)
    ElMessage.error('导出失败')
  }
}
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.toolbar {
  display: flex;
  gap: 12px;
  margin-top: 16px;
  flex-wrap: wrap;
}
</style>
