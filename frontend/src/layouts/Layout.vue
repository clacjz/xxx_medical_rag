<template>
  <el-container class="layout-container">
    <!-- Modern Sidebar -->
    <el-aside :width="isCollapse ? '72px' : '240px'" class="aside">
      <div class="logo">
        <div class="logo-icon-wrapper">
          <el-icon :size="28"><Monitor /></el-icon>
        </div>
        <span v-show="!isCollapse" class="logo-text">RAG知识库</span>
      </div>

      <el-menu
        :default-active="route.path"
        :collapse="isCollapse"
        :collapse-transition="false"
        router
        class="menu"
        background-color="transparent"
        text-color="#a8abb2"
        active-text-color="#ffffff"
      >
        <el-menu-item index="/dashboard" class="menu-item">
          <el-icon><Odometer /></el-icon>
          <template #title>仪表盘</template>
        </el-menu-item>
        <el-menu-item index="/collections" class="menu-item">
          <el-icon><Collection /></el-icon>
          <template #title>知识库</template>
        </el-menu-item>
        <el-menu-item index="/documents" class="menu-item">
          <el-icon><Document /></el-icon>
          <template #title>文档管理</template>
        </el-menu-item>
        <el-menu-item index="/ocr-preview" class="menu-item">
          <el-icon><DocumentCopy /></el-icon>
          <template #title>OCR文档解析</template>
        </el-menu-item>
        <el-menu-item index="/qa" class="menu-item">
          <el-icon><ChatDotRound /></el-icon>
          <template #title>智能问答</template>
        </el-menu-item>
        <el-menu-item index="/conversations" class="menu-item">
          <el-icon><ChatLineSquare /></el-icon>
          <template #title>对话历史</template>
        </el-menu-item>
        <el-sub-menu index="admin" v-if="userStore.isAdmin || userStore.isDeptAdmin" class="menu-item">
          <template #title>
            <el-icon><Setting /></el-icon>
            <span>系统管理</span>
          </template>
          <el-menu-item index="/users" v-if="userStore.isAdmin">用户管理</el-menu-item>
          <el-menu-item index="/rbac">权限管理</el-menu-item>
          <el-menu-item index="/audit">审计日志</el-menu-item>
          <el-menu-item index="/settings">系统配置</el-menu-item>
          <el-menu-item index="/vectors" v-if="userStore.isAdmin || userStore.isDeptAdmin">向量管理</el-menu-item>
        </el-sub-menu>
      </el-menu>

      <!-- Collapse Button at bottom -->
      <div class="collapse-section">
        <div class="collapse-btn" @click="isCollapse = !isCollapse">
          <el-icon :size="18">
            <Fold v-if="!isCollapse" />
            <Expand v-else />
          </el-icon>
          <span v-show="!isCollapse" class="collapse-text">收起</span>
        </div>
      </div>
    </el-aside>

    <!-- Main Container -->
    <el-container class="main-container">
      <!-- Modern Header -->
      <el-header class="header">
        <div class="header-left">
          <el-breadcrumb separator="/" class="breadcrumb">
            <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-if="route.meta.title">{{ route.meta.title }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <div class="header-right">
          <!-- Role Tags -->
          <div class="user-roles" v-if="!isCollapse && userStore.userRoles.length > 0">
            <el-tag
              v-for="role in displayedRoles"
              :key="role.id"
              :type="getRoleType(role.code)"
              size="small"
              class="role-tag"
              effect="light"
            >
              {{ role.name }}
              <span v-if="role.isPrimary" class="primary-indicator">主</span>
            </el-tag>
          </div>

          <!-- User Dropdown -->
          <el-dropdown @command="handleCommand" trigger="click" class="user-dropdown">
            <div class="user-info">
              <el-avatar :size="36" :icon="UserFilled" class="user-avatar" />
              <div class="user-details">
                <span class="username">{{ userStore.userInfo?.username || '用户' }}</span>
                <span class="user-role-text">{{ userStore.userRoles[0]?.name || '未分配角色' }}</span>
              </div>
              <el-icon class="dropdown-arrow"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu class="dropdown-menu">
                <el-dropdown-item command="profile">
                  <el-icon><User /></el-icon>
                  <span>个人中心</span>
                </el-dropdown-item>
                <el-dropdown-item command="settings">
                  <el-icon><Setting /></el-icon>
                  <span>系统配置</span>
                </el-dropdown-item>
                <el-dropdown-item divided command="logout" class="logout-item">
                  <el-icon><SwitchButton /></el-icon>
                  <span>退出登录</span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- Main Content -->
      <el-main class="main">
        <router-view v-slot="{ Component }">
          <transition name="fade-slide" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { authApi } from '@/api/auth'
import { getRoleType } from '@/utils/format'
import {
  Fold,
  Expand,
  UserFilled,
  Monitor,
  User,
  Setting,
  SwitchButton
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const isCollapse = ref(false)

const displayedRoles = computed(() => {
  return userStore.userRoles.slice(0, 3)
})

onMounted(() => {
  if (userStore.isLoggedIn && !userStore.userInfo) {
    userStore.fetchUserInfo()
  }
})

const handleCommand = async (command: string) => {
  switch (command) {
    case 'logout':
      try {
        await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
          type: 'warning'
        })
        await authApi.logout()
      } catch {
        // 用户取消
      }
      userStore.logout()
      router.push('/login')
      break
    case 'settings':
      router.push('/settings')
      break
    case 'profile':
      ElMessageBox.alert('个人中心功能开发中...', '提示')
      break
  }
}
</script>

<style scoped>
.layout-container {
  height: 100vh;
}

/* Modern Sidebar */
.aside {
  background: var(--dark-bg);
  background: linear-gradient(180deg, var(--dark-bg) 0%, var(--dark-bg-2) 100%);
  transition: width var(--transition-slow);
  overflow-x: hidden;
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--dark-border);
  position: relative;
  z-index: 100;
}

.aside::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 200px;
  background: linear-gradient(180deg, rgba(99, 102, 241, 0.08) 0%, transparent 100%);
  pointer-events: none;
}

.logo {
  height: 72px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: var(--dark-fg);
  font-size: 18px;
  font-weight: 600;
  border-bottom: 1px solid var(--dark-border);
  padding: 0 16px;
  margin-bottom: 16px;
}

.logo-icon-wrapper {
  width: 44px;
  height: 44px;
  background: linear-gradient(135deg, var(--accent) 0%, var(--accent-dark) 100%);
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.3);
}

.logo-text {
  white-space: nowrap;
  letter-spacing: 1px;
}

/* Menu Styles */
.menu {
  border-right: none;
  background: transparent;
  flex: 1;
  overflow-y: auto;
}

.menu:not(.el-menu--collapse) {
  width: 240px;
}

.menu :deep(.el-menu-item),
.menu :deep(.el-sub-menu__title) {
  height: 52px;
  line-height: 52px;
  margin: 4px 12px;
  padding: 0 16px !important;
  border-radius: var(--radius);
  transition: all var(--transition);
}

.menu :deep(.el-menu-item:hover),
.menu :deep(.el-sub-menu__title:hover) {
  background: rgba(255, 255, 255, 0.08) !important;
}

.menu :deep(.el-menu-item.is-active) {
  background: var(--accent) !important;
  box-shadow: 0 2px 8px rgba(99, 102, 241, 0.3);
}

.menu :deep(.el-sub-menu .el-menu-item) {
  height: 44px;
  line-height: 44px;
  margin: 2px 12px;
  padding: 0 16px !important;
  font-size: 13px;
}

.menu :deep(.el-sub-menu .el-menu-item.is-active) {
  background: rgba(99, 102, 241, 0.2) !important;
}

/* Collapse Section */
.collapse-section {
  padding: 16px;
  border-top: 1px solid var(--dark-border);
}

.collapse-btn {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  color: var(--dark-muted);
  cursor: pointer;
  border-radius: var(--radius);
  transition: all var(--transition);
}

.collapse-btn:hover {
  background: rgba(255, 255, 255, 0.08);
  color: var(--dark-fg);
}

.collapse-text {
  font-size: 14px;
  white-space: nowrap;
}

/* Main Container */
.main-container {
  flex-direction: column;
  background: var(--background);
}

/* Modern Header - Glass Effect */
.header {
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-bottom: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  height: 72px;
  transition: all var(--transition);
}

.header-left {
  display: flex;
  align-items: center;
}

.breadcrumb {
  font-size: 14px;
}

.breadcrumb :deep(.el-breadcrumb__inner) {
  color: var(--muted-foreground);
}

.breadcrumb :deep(.el-breadcrumb__inner.is-link:hover) {
  color: var(--accent);
}

.breadcrumb :deep(.el-breadcrumb__separator) {
  color: var(--border);
}

.header-right {
  display: flex;
  align-items: center;
  gap: 20px;
}

.user-roles {
  display: flex;
  align-items: center;
  gap: 8px;
}

.role-tag {
  border-radius: var(--radius-sm);
  padding: 4px 10px;
  border: none;
}

.primary-indicator {
  font-size: 10px;
  background: rgba(255, 255, 255, 0.3);
  padding: 0 4px;
  border-radius: 2px;
  margin-left: 4px;
}

/* User Dropdown */
.user-dropdown {
  cursor: pointer;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 16px;
  border-radius: var(--radius);
  transition: all var(--transition);
  background: var(--muted);
}

.user-info:hover {
  background: var(--border);
}

.user-avatar {
  background: linear-gradient(135deg, var(--accent) 0%, var(--accent-dark) 100%);
}

.user-details {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.username {
  font-size: 14px;
  font-weight: 500;
  color: var(--foreground);
}

.user-role-text {
  font-size: 12px;
  color: var(--muted-foreground);
}

.dropdown-arrow {
  color: var(--muted-foreground);
  margin-left: 4px;
  transition: transform var(--transition);
}

.user-dropdown:hover .dropdown-arrow {
  transform: rotate(180deg);
}

/* Dropdown Menu */
.dropdown-menu {
  border-radius: var(--radius);
  padding: 8px;
  box-shadow: var(--shadow-lg);
  border: 1px solid var(--border);
}

.dropdown-menu :deep(.el-dropdown-menu__item) {
  padding: 10px 16px;
  border-radius: var(--radius-sm);
  margin: 4px 0;
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  color: var(--foreground);
  transition: all var(--transition);
}

.dropdown-menu :deep(.el-dropdown-menu__item:hover) {
  background: var(--muted);
  color: var(--foreground);
}

.logout-item {
  color: var(--danger) !important;
}

.logout-item:hover {
  background: var(--danger-light) !important;
  color: var(--danger) !important;
}

/* Main Content */
.main {
  background: transparent;
  padding: 24px;
  overflow-y: auto;
}

/* Transitions */
.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: all var(--transition);
}

.fade-slide-enter-from {
  opacity: 0;
  transform: translateX(20px);
}

.fade-slide-leave-to {
  opacity: 0;
  transform: translateX(-20px);
}

/* Responsive */
@media (max-width: 768px) {
  .logo-text {
    display: none;
  }

  .username,
  .user-role-text,
  .user-roles {
    display: none;
  }

  .user-info {
    padding: 8px;
  }
}
</style>