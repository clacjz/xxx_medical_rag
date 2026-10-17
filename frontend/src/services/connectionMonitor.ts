import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

interface ConnectionState {
  firstErrorTime: number | null
  errorCount: number
  isMonitoring: boolean
  timeoutId: number | null
}

const CONNECTION_ERROR_THRESHOLD = 120000

class ConnectionMonitor {
  private state: ConnectionState = {
    firstErrorTime: null,
    errorCount: 0,
    isMonitoring: false,
    timeoutId: null
  }

  private messageShown = false

  recordNetworkError(): void {
    const now = Date.now()

    if (this.state.firstErrorTime === null) {
      this.state.firstErrorTime = now
      this.state.errorCount = 1
      this.state.isMonitoring = true

      if (!this.messageShown) {
        ElMessage.warning({
          message: '检测到网络连接不稳定，正在尝试重新连接...',
          duration: 3000,
          showClose: true
        })
      }

      this.startMonitoring()
    } else {
      this.state.firstErrorTime = now
      this.state.errorCount++
    }
  }

  recordNetworkSuccess(): void {
    if (this.state.isMonitoring) {
      this.resetState()
    }
  }

  private startMonitoring(): void {
    if (this.state.timeoutId !== null) {
      clearTimeout(this.state.timeoutId)
    }

    this.state.timeoutId = window.setTimeout(() => {
      this.triggerAutoLogout()
    }, CONNECTION_ERROR_THRESHOLD)
  }

  private triggerAutoLogout(): void {
    if (!this.state.isMonitoring) {
      return
    }

    const userStore = useUserStore()

    if (!userStore.token) {
      this.resetState()
      return
    }

    const currentPath = window.location.pathname
    const isLoginPage = currentPath === '/login' || currentPath === '/register'

    if (isLoginPage) {
      this.resetState()
      return
    }

    console.warn('[ConnectionMonitor]', '后端服务连接异常超过30秒，执行自动登出')

    ElMessage.error({
      message: '服务连接超时，为保护您的账户安全，已自动退出登录',
      duration: 5000,
      showClose: true
    })

    userStore.token = ''
    userStore.userInfo = null
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')

    setTimeout(() => {
      window.location.href = '/login'
    }, 2000)

    this.resetState()
  }

  private resetState(): void {
    if (this.state.timeoutId !== null) {
      clearTimeout(this.state.timeoutId)
      this.state.timeoutId = null
    }

    this.state = {
      firstErrorTime: null,
      errorCount: 0,
      isMonitoring: false,
      timeoutId: null
    }

    this.messageShown = false
  }

  getConnectionStatus(): { isConnected: boolean; errorCount: number } {
    return {
      isConnected: this.state.isMonitoring,
      errorCount: this.state.errorCount
    }
  }
}

export const connectionMonitor = new ConnectionMonitor()
