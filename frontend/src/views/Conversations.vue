<template>
  <div class="conversations">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>对话历史</span>
          <el-input
            v-model="searchKeyword"
            placeholder="搜索对话标题..."
            clearable
            size="small"
            style="width: 240px"
            @input="onSearchInput"
          />
        </div>
      </template>

      <el-table :data="filteredConversations" v-loading="loading" style="width: 100%">
        <el-table-column prop="title" label="对话标题" min-width="200" />
        <el-table-column prop="collectionName" label="所属知识库" width="150" />
        <el-table-column prop="messageCount" label="消息数" width="100" align="center" />
        <el-table-column prop="lastMessageAt" label="最后消息" width="180">
          <template #default="{ row }">
            {{ formatDate(row.lastMessageAt) }}
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180">
          <template #default="{ row }">
            {{ formatDate(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" text size="small" @click="viewConversation(row)">
              查看
            </el-button>
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
        @size-change="loadConversations"
        @current-change="loadConversations"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { chatApi } from '@/api/chat'
import { formatDate } from '@/utils/format'
import type { ChatSession } from '@/types'

const router = useRouter()

const conversations = ref<ChatSession[]>([])
const loading = ref(false)

const pagination = reactive({
  page: 0,
  pageSize: 10,
  total: 0
})

const searchKeyword = ref('')
let searchTimer: ReturnType<typeof setTimeout> | null = null

const filteredConversations = computed(() => {
  const keyword = searchKeyword.value.trim().toLowerCase()
  if (!keyword) return conversations.value
  return conversations.value.filter(c => c.title?.toLowerCase().includes(keyword))
})

const onSearchInput = () => {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    // After filtering, no API call needed — filteredConversations handles it
  }, 300)
}

const loadConversations = async () => {
  loading.value = true
  try {
    const res = await chatApi.listConversations({
      page: pagination.page,
      pageSize: pagination.pageSize
    })
    if (res.code === 200 && res.data) {
      conversations.value = res.data.items
      pagination.total = res.data.total
    }
  } catch (error) {
    console.error('Failed to load conversations:', error)
  } finally {
    loading.value = false
  }
}

const viewConversation = (row: ChatSession) => {
  router.push({ path: '/multi-chat', query: { sessionId: row.id } })
}

onMounted(() => {
  loadConversations()
})
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
