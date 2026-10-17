import { ElMessage } from 'element-plus'
import { connectionMonitor } from './connectionMonitor'

class NetworkStatusListener {
  private isInitialized = false

  init(): void {
    if (this.isInitialized) {
      return
    }

    window.addEventListener('online', this.handleOnline.bind(this))
    window.addEventListener('offline', this.handleOffline.bind(this))

    this.isInitialized = true
  }

  private handleOnline(): void {
    ElMessage.success({
      message: '网络连接已恢复',
      duration: 2000,
      showClose: true
    })
  }

  private handleOffline(): void {
    console.warn('[NetworkStatusListener]', '检测到网络连接已断开')

    ElMessage.error({
      message: '网络连接已断开，请检查您的网络设置',
      duration: 3000,
      showClose: true
    })

    connectionMonitor.recordNetworkError()
  }

  destroy(): void {
    if (!this.isInitialized) {
      return
    }

    window.removeEventListener('online', this.handleOnline.bind(this))
    window.removeEventListener('offline', this.handleOffline.bind(this))

    this.isInitialized = false
  }
}

export const networkStatusListener = new NetworkStatusListener()
