import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '用户登录', requiresAuth: false }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/Register.vue'),
    meta: { title: '用户注册', requiresAuth: false }
  },
  {
    path: '/',
    component: () => import('@/layouts/Layout.vue'),
    redirect: '/dashboard',
    meta: { requiresAuth: true },
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/Dashboard.vue'),
        meta: { title: '仪表盘', icon: 'Odometer' }
      },
      {
        path: 'collections',
        name: 'Collections',
        component: () => import('@/views/Collections.vue'),
        meta: { title: '知识库管理', icon: 'Collection' }
      },
      {
        path: 'collections/:id',
        name: 'CollectionDetail',
        component: () => import('@/views/CollectionDetail.vue'),
        meta: { title: '知识库详情', icon: 'Collection' }
      },
      {
        path: 'documents',
        name: 'Documents',
        component: () => import('@/views/Documents.vue'),
        meta: { title: '文档管理', icon: 'Document' }
      },
      {
        path: 'qa',
        name: 'QA',
        component: () => import('@/views/MultiChat.vue'),
        meta: { title: '智能问答', icon: 'ChatDotRound' }
      },
      {
        path: 'multichat',
        name: 'MultiChat',
        component: () => import('@/views/MultiChat.vue'),
        meta: { title: '多知识库问答', icon: 'ChatLineSquare' }
      },
      {
        path: 'conversations',
        name: 'Conversations',
        component: () => import('@/views/Conversations.vue'),
        meta: { title: '对话历史', icon: 'ChatLineSquare' }
      },
      {
        path: 'users',
        name: 'Users',
        component: () => import('@/views/Users.vue'),
        meta: { title: '用户管理', icon: 'User' }
      },
      {
        path: 'rbac',
        name: 'RBAC',
        component: () => import('@/views/RBAC.vue'),
        meta: { title: '权限管理', icon: 'Key' }
      },
      {
        path: 'collection-permissions',
        name: 'CollectionPermissions',
        component: () => import('@/views/CollectionPermissions.vue'),
        meta: { title: '知识库权限', icon: 'Collection' }
      },
      {
        path: 'audit',
        name: 'Audit',
        component: () => import('@/views/Audit.vue'),
        meta: { title: '审计日志', icon: 'Operation' }
      },
      {
        path: 'settings',
        name: 'Settings',
        component: () => import('@/views/Settings.vue'),
        meta: { title: '系统配置', icon: 'Setting' }
      },
      {
        path: 'vectors',
        name: 'Vectors',
        component: () => import('@/views/Vector.vue'),
        meta: { title: '向量管理', icon: 'Connection' }
      },
      {
        path: 'ocr-preview',
        name: 'OcrPreview',
        component: () => import('@/views/OcrPreview.vue'),
        meta: { title: 'OCR文档预览', icon: 'DocumentCopy' }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/NotFound.vue'),
    meta: { title: '页面未找到' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, _from, next) => {
  const userStore = useUserStore()
  const token = userStore.token
  const requiresAuth = to.matched.some(record => record.meta.requiresAuth !== false)

  if (requiresAuth && !token) {
    next({ name: 'Login', query: { redirect: to.fullPath } })
  } else if (to.name === 'Login' && token) {
    next({ name: 'Dashboard' })
  } else {
    next()
  }
})

export default router
