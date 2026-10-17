import { AuditAction, AuditActionLabels, AuditLevel, AuditLevelType, AuditLogEntry, AuditActionType } from '@/types/enums'

export class AuditService {
  private static instance: AuditService
  private pendingLogs: AuditLogEntry[] = []
  private maxBatchSize = 10
  private flushInterval = 5000
  private timer: number | null = null

  private constructor() {
    this.startFlushTimer()
    window.addEventListener('beforeunload', () => {
      this.flush()
    })
  }

  static getInstance(): AuditService {
    if (!AuditService.instance) {
      AuditService.instance = new AuditService()
    }
    return AuditService.instance
  }

  private startFlushTimer() {
    if (this.timer) {
      clearInterval(this.timer)
    }
    this.timer = window.setInterval(() => {
      this.flush()
    }, this.flushInterval)
  }

  private flush() {
    if (this.pendingLogs.length === 0) {
      return
    }
    const logs = [...this.pendingLogs]
    this.pendingLogs = []
    console.debug('[Audit] Flushing logs:', logs.length)
  }

  private addLog(entry: AuditLogEntry) {
    const enrichedEntry: AuditLogEntry = {
      ...entry,
      timestamp: new Date().toISOString()
    }
    this.pendingLogs.push(enrichedEntry)

    if (this.pendingLogs.length >= this.maxBatchSize) {
      this.flush()
    }
  }

  private determineLevel(action: AuditActionType): AuditLevelType {
    const securityActions: AuditActionType[] = [
      AuditAction.LOGIN,
      AuditAction.LOGOUT,
      AuditAction.REGISTER,
      AuditAction.PASSWORD_CHANGE,
      AuditAction.PASSWORD_RESET,
      AuditAction.USER_LOCK,
      AuditAction.USER_UNLOCK,
      AuditAction.PERMISSION_GRANT,
      AuditAction.PERMISSION_REVOKE,
      AuditAction.SECURITY_LOGIN_FAILED,
      AuditAction.SECURITY_ACCOUNT_LOCKED,
      AuditAction.SECURITY_SUSPICIOUS_ACTIVITY,
      AuditAction.SECURITY_TOKEN_EXPIRED
    ]

    const warningActions: AuditActionType[] = [
      AuditAction.USER_DELETE,
      AuditAction.DOCUMENT_DELETE,
      AuditAction.COLLECTION_DELETE
    ]

    if (securityActions.includes(action)) {
      return AuditLevel.SECURITY
    }
    if (warningActions.includes(action)) {
      return AuditLevel.WARNING
    }
    return AuditLevel.INFO
  }

  log(action: AuditActionType, options: {
    resourceType?: string
    resourceId?: string
    description?: string
    details?: Record<string, any>
    status?: 'success' | 'failed'
  } = {}) {
    const level = this.determineLevel(action)
    const description = options.description || AuditActionLabels[action]

    this.addLog({
      action,
      level,
      resourceType: options.resourceType,
      resourceId: options.resourceId,
      description,
      details: options.details,
      status: options.status || 'success'
    })

    console.debug(`[Audit] Action logged: ${action}`, {
      level,
      resourceType: options.resourceType,
      resourceId: options.resourceId,
      status: options.status
    })
  }

  logLogin(options: {
    username: string
    success: boolean
    reason?: string
  }) {
    this.log(options.success ? AuditAction.LOGIN : AuditAction.SECURITY_LOGIN_FAILED, {
      resourceType: 'AUTH',
      description: options.success ? '登录成功' : `登录失败: ${options.reason || '未知原因'}`,
      details: {
        username: options.username,
        success: options.success,
        reason: options.reason
      },
      status: options.success ? 'success' : 'failed'
    })
  }

  logLogout(options: {
    userId: string
    username: string
  }) {
    this.log(AuditAction.LOGOUT, {
      resourceType: 'AUTH',
      description: '用户注销',
      details: {
        userId: options.userId,
        username: options.username
      }
    })
  }

  logRegister(options: {
    username: string
    success: boolean
    reason?: string
  }) {
    this.log(AuditAction.REGISTER, {
      resourceType: 'AUTH',
      description: options.success ? '注册成功' : `注册失败: ${options.reason || '未知原因'}`,
      details: {
        username: options.username,
        success: options.success,
        reason: options.reason
      },
      status: options.success ? 'success' : 'failed'
    })
  }

  logPasswordChange(options: {
    userId: string
    success: boolean
    reason?: string
  }) {
    this.log(AuditAction.PASSWORD_CHANGE, {
      resourceType: 'AUTH',
      resourceId: options.userId,
      description: options.success ? '密码修改成功' : `密码修改失败: ${options.reason || '未知原因'}`,
      details: {
        success: options.success,
        reason: options.reason
      },
      status: options.success ? 'success' : 'failed'
    })
  }

  logDocumentOperation(operation: 'upload' | 'download' | 'delete' | 'view' | 'update' | 'share', options: {
    documentId: string
    documentName: string
    success: boolean
    reason?: string
  }) {
    const actionMap: Record<string, AuditActionType> = {
      upload: AuditAction.DOCUMENT_UPLOAD,
      download: AuditAction.DOCUMENT_DOWNLOAD,
      delete: AuditAction.DOCUMENT_DELETE,
      view: AuditAction.DOCUMENT_VIEW,
      update: AuditAction.DOCUMENT_UPDATE,
      share: AuditAction.DOCUMENT_SHARE
    }

    this.log(actionMap[operation], {
      resourceType: 'DOCUMENT',
      resourceId: options.documentId,
      description: `${this.getOperationLabel(operation)}: ${options.documentName}`,
      details: {
        documentName: options.documentName,
        success: options.success,
        reason: options.reason
      },
      status: options.success ? 'success' : 'failed'
    })
  }

  logCollectionOperation(operation: 'create' | 'update' | 'delete' | 'view', options: {
    collectionId: string
    collectionName: string
    success: boolean
    reason?: string
  }) {
    const actionMap: Record<string, AuditActionType> = {
      create: AuditAction.COLLECTION_CREATE,
      update: AuditAction.COLLECTION_UPDATE,
      delete: AuditAction.COLLECTION_DELETE,
      view: AuditAction.COLLECTION_VIEW
    }

    this.log(actionMap[operation], {
      resourceType: 'COLLECTION',
      resourceId: options.collectionId,
      description: `${this.getCollectionOperationLabel(operation)}: ${options.collectionName}`,
      details: {
        collectionName: options.collectionName,
        success: options.success,
        reason: options.reason
      },
      status: options.success ? 'success' : 'failed'
    })
  }

  logUserOperation(operation: 'create' | 'update' | 'delete' | 'disable' | 'enable' | 'lock' | 'unlock' | 'view', options: {
    targetUserId: string
    targetUsername: string
    success: boolean
    reason?: string
  }) {
    const actionMap: Record<string, AuditActionType> = {
      create: AuditAction.USER_CREATE,
      update: AuditAction.USER_UPDATE,
      delete: AuditAction.USER_DELETE,
      disable: AuditAction.USER_DISABLE,
      enable: AuditAction.USER_ENABLE,
      lock: AuditAction.USER_LOCK,
      unlock: AuditAction.USER_UNLOCK,
      view: AuditAction.USER_VIEW
    }

    this.log(actionMap[operation], {
      resourceType: 'USER',
      resourceId: options.targetUserId,
      description: `${this.getUserOperationLabel(operation)}: ${options.targetUsername}`,
      details: {
        targetUsername: options.targetUsername,
        success: options.success,
        reason: options.reason
      },
      status: options.success ? 'success' : 'failed'
    })
  }

  logPermissionOperation(operation: 'grant' | 'revoke' | 'update' | 'view', options: {
    targetType: string
    targetId: string
    targetName: string
    success: boolean
    reason?: string
  }) {
    const actionMap: Record<string, AuditActionType> = {
      grant: AuditAction.PERMISSION_GRANT,
      revoke: AuditAction.PERMISSION_REVOKE,
      update: AuditAction.PERMISSION_UPDATE,
      view: AuditAction.PERMISSION_VIEW
    }

    this.log(actionMap[operation], {
      resourceType: 'PERMISSION',
      resourceId: options.targetId,
      description: `${this.getPermissionOperationLabel(operation)}: ${options.targetName}`,
      details: {
        targetType: options.targetType,
        targetName: options.targetName,
        success: options.success,
        reason: options.reason
      },
      status: options.success ? 'success' : 'failed'
    })
  }

  logQuery(options: {
    collectionId: string
    collectionName: string
    questionLength: number
    responseLength?: number
    success: boolean
  }) {
    this.log(AuditAction.QA_QUERY, {
      resourceType: 'QA',
      resourceId: options.collectionId,
      description: `问答查询: ${options.collectionName}`,
      details: {
        collectionName: options.collectionName,
        questionLength: options.questionLength,
        responseLength: options.responseLength,
        success: options.success
      },
      status: options.success ? 'success' : 'failed'
    })
  }

  private getOperationLabel(operation: string): string {
    const labels: Record<string, string> = {
      upload: '上传文档',
      download: '下载文档',
      delete: '删除文档',
      view: '查看文档',
      update: '更新文档',
      share: '分享文档'
    }
    return labels[operation] || operation
  }

  private getCollectionOperationLabel(operation: string): string {
    const labels: Record<string, string> = {
      create: '创建知识库',
      update: '更新知识库',
      delete: '删除知识库',
      view: '查看知识库'
    }
    return labels[operation] || operation
  }

  private getUserOperationLabel(operation: string): string {
    const labels: Record<string, string> = {
      create: '创建用户',
      update: '更新用户',
      delete: '删除用户',
      disable: '禁用用户',
      enable: '启用用户',
      lock: '锁定用户',
      unlock: '解锁用户',
      view: '查看用户'
    }
    return labels[operation] || operation
  }

  private getPermissionOperationLabel(operation: string): string {
    const labels: Record<string, string> = {
      grant: '授予权限',
      revoke: '撤销权限',
      update: '更新权限',
      view: '查看权限'
    }
    return labels[operation] || operation
  }
}

export const auditService = AuditService.getInstance()
