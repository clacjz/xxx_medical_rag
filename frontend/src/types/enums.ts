export const AuditAction = {
  LOGIN: 'LOGIN',
  LOGOUT: 'LOGOUT',
  REGISTER: 'REGISTER',
  REFRESH_TOKEN: 'REFRESH_TOKEN',
  PASSWORD_CHANGE: 'PASSWORD_CHANGE',
  PASSWORD_RESET: 'PASSWORD_RESET',
  USER_CREATE: 'USER_CREATE',
  USER_UPDATE: 'USER_UPDATE',
  USER_DELETE: 'USER_DELETE',
  USER_DISABLE: 'USER_DISABLE',
  USER_ENABLE: 'USER_ENABLE',
  USER_LOCK: 'USER_LOCK',
  USER_UNLOCK: 'USER_UNLOCK',
  USER_VIEW: 'USER_VIEW',
  DEPT_CREATE: 'DEPT_CREATE',
  DEPT_UPDATE: 'DEPT_UPDATE',
  DEPT_DELETE: 'DEPT_DELETE',
  DEPT_VIEW: 'DEPT_VIEW',
  COLLECTION_CREATE: 'COLLECTION_CREATE',
  COLLECTION_UPDATE: 'COLLECTION_UPDATE',
  COLLECTION_DELETE: 'COLLECTION_DELETE',
  COLLECTION_VIEW: 'COLLECTION_VIEW',
  COLLECTION_CONFIG_UPDATE: 'COLLECTION_CONFIG_UPDATE',
  DOCUMENT_UPLOAD: 'DOCUMENT_UPLOAD',
  DOCUMENT_DOWNLOAD: 'DOCUMENT_DOWNLOAD',
  DOCUMENT_DELETE: 'DOCUMENT_DELETE',
  DOCUMENT_VIEW: 'DOCUMENT_VIEW',
  DOCUMENT_UPDATE: 'DOCUMENT_UPDATE',
  DOCUMENT_SHARE: 'DOCUMENT_SHARE',
  PERMISSION_GRANT: 'PERMISSION_GRANT',
  PERMISSION_REVOKE: 'PERMISSION_REVOKE',
  PERMISSION_UPDATE: 'PERMISSION_UPDATE',
  PERMISSION_VIEW: 'PERMISSION_VIEW',
  QA_QUERY: 'QA_QUERY',
  QA_CONVERSATION_CREATE: 'QA_CONVERSATION_CREATE',
  QA_CONVERSATION_DELETE: 'QA_CONVERSATION_DELETE',
  SYSTEM_CONFIG_VIEW: 'SYSTEM_CONFIG_VIEW',
  SYSTEM_CONFIG_UPDATE: 'SYSTEM_CONFIG_UPDATE',
  SYSTEM_BACKUP: 'SYSTEM_BACKUP',
  SYSTEM_RESTORE: 'SYSTEM_RESTORE',
  SECURITY_LOGIN_FAILED: 'SECURITY_LOGIN_FAILED',
  SECURITY_ACCOUNT_LOCKED: 'SECURITY_ACCOUNT_LOCKED',
  SECURITY_SUSPICIOUS_ACTIVITY: 'SECURITY_SUSPICIOUS_ACTIVITY',
  SECURITY_TOKEN_EXPIRED: 'SECURITY_TOKEN_EXPIRED',
  EXPORT: 'EXPORT',
  IMPORT: 'IMPORT',
  BATCH_OPERATION: 'BATCH_OPERATION'
} as const

export type AuditActionType = typeof AuditAction[keyof typeof AuditAction]

export const AuditActionLabels: Record<AuditActionType, string> = {
  [AuditAction.LOGIN]: '登录',
  [AuditAction.LOGOUT]: '注销',
  [AuditAction.REGISTER]: '注册',
  [AuditAction.REFRESH_TOKEN]: '刷新令牌',
  [AuditAction.PASSWORD_CHANGE]: '修改密码',
  [AuditAction.PASSWORD_RESET]: '重置密码',
  [AuditAction.USER_CREATE]: '创建用户',
  [AuditAction.USER_UPDATE]: '更新用户',
  [AuditAction.USER_DELETE]: '删除用户',
  [AuditAction.USER_DISABLE]: '禁用用户',
  [AuditAction.USER_ENABLE]: '启用用户',
  [AuditAction.USER_LOCK]: '锁定用户',
  [AuditAction.USER_UNLOCK]: '解锁用户',
  [AuditAction.USER_VIEW]: '查看用户',
  [AuditAction.DEPT_CREATE]: '创建部门',
  [AuditAction.DEPT_UPDATE]: '更新部门',
  [AuditAction.DEPT_DELETE]: '删除部门',
  [AuditAction.DEPT_VIEW]: '查看部门',
  [AuditAction.COLLECTION_CREATE]: '创建知识库',
  [AuditAction.COLLECTION_UPDATE]: '更新知识库',
  [AuditAction.COLLECTION_DELETE]: '删除知识库',
  [AuditAction.COLLECTION_VIEW]: '查看知识库',
  [AuditAction.COLLECTION_CONFIG_UPDATE]: '更新知识库配置',
  [AuditAction.DOCUMENT_UPLOAD]: '上传文档',
  [AuditAction.DOCUMENT_DOWNLOAD]: '下载文档',
  [AuditAction.DOCUMENT_DELETE]: '删除文档',
  [AuditAction.DOCUMENT_VIEW]: '查看文档',
  [AuditAction.DOCUMENT_UPDATE]: '更新文档',
  [AuditAction.DOCUMENT_SHARE]: '分享文档',
  [AuditAction.PERMISSION_GRANT]: '授予权限',
  [AuditAction.PERMISSION_REVOKE]: '撤销权限',
  [AuditAction.PERMISSION_UPDATE]: '更新权限',
  [AuditAction.PERMISSION_VIEW]: '查看权限',
  [AuditAction.QA_QUERY]: '问答查询',
  [AuditAction.QA_CONVERSATION_CREATE]: '创建对话',
  [AuditAction.QA_CONVERSATION_DELETE]: '删除对话',
  [AuditAction.SYSTEM_CONFIG_VIEW]: '查看系统配置',
  [AuditAction.SYSTEM_CONFIG_UPDATE]: '更新系统配置',
  [AuditAction.SYSTEM_BACKUP]: '系统备份',
  [AuditAction.SYSTEM_RESTORE]: '系统恢复',
  [AuditAction.SECURITY_LOGIN_FAILED]: '登录失败',
  [AuditAction.SECURITY_ACCOUNT_LOCKED]: '账户锁定',
  [AuditAction.SECURITY_SUSPICIOUS_ACTIVITY]: '可疑活动',
  [AuditAction.SECURITY_TOKEN_EXPIRED]: '令牌过期',
  [AuditAction.EXPORT]: '导出数据',
  [AuditAction.IMPORT]: '导入数据',
  [AuditAction.BATCH_OPERATION]: '批量操作'
}

export const AuditActionCategory = {
  AUTH: 'AUTH',
  USER: 'USER',
  DEPT: 'DEPT',
  COLLECTION: 'COLLECTION',
  DOCUMENT: 'DOCUMENT',
  PERMISSION: 'PERMISSION',
  QA: 'QA',
  SYSTEM: 'SYSTEM',
  SECURITY: 'SECURITY',
  OTHER: 'OTHER'
} as const

export type AuditActionCategoryType = typeof AuditActionCategory[keyof typeof AuditActionCategory]

export const AuditActionCategoryLabels: Record<AuditActionCategoryType, string> = {
  [AuditActionCategory.AUTH]: '认证操作',
  [AuditActionCategory.USER]: '用户管理',
  [AuditActionCategory.DEPT]: '部门管理',
  [AuditActionCategory.COLLECTION]: '知识库管理',
  [AuditActionCategory.DOCUMENT]: '文档管理',
  [AuditActionCategory.PERMISSION]: '权限管理',
  [AuditActionCategory.QA]: '问答操作',
  [AuditActionCategory.SYSTEM]: '系统操作',
  [AuditActionCategory.SECURITY]: '安全相关',
  [AuditActionCategory.OTHER]: '其他操作'
}

export const AuditActionCategoryMap: Record<AuditActionType, AuditActionCategoryType> = {
  [AuditAction.LOGIN]: AuditActionCategory.AUTH,
  [AuditAction.LOGOUT]: AuditActionCategory.AUTH,
  [AuditAction.REGISTER]: AuditActionCategory.AUTH,
  [AuditAction.REFRESH_TOKEN]: AuditActionCategory.AUTH,
  [AuditAction.PASSWORD_CHANGE]: AuditActionCategory.AUTH,
  [AuditAction.PASSWORD_RESET]: AuditActionCategory.AUTH,
  [AuditAction.USER_CREATE]: AuditActionCategory.USER,
  [AuditAction.USER_UPDATE]: AuditActionCategory.USER,
  [AuditAction.USER_DELETE]: AuditActionCategory.USER,
  [AuditAction.USER_DISABLE]: AuditActionCategory.USER,
  [AuditAction.USER_ENABLE]: AuditActionCategory.USER,
  [AuditAction.USER_LOCK]: AuditActionCategory.USER,
  [AuditAction.USER_UNLOCK]: AuditActionCategory.USER,
  [AuditAction.USER_VIEW]: AuditActionCategory.USER,
  [AuditAction.DEPT_CREATE]: AuditActionCategory.DEPT,
  [AuditAction.DEPT_UPDATE]: AuditActionCategory.DEPT,
  [AuditAction.DEPT_DELETE]: AuditActionCategory.DEPT,
  [AuditAction.DEPT_VIEW]: AuditActionCategory.DEPT,
  [AuditAction.COLLECTION_CREATE]: AuditActionCategory.COLLECTION,
  [AuditAction.COLLECTION_UPDATE]: AuditActionCategory.COLLECTION,
  [AuditAction.COLLECTION_DELETE]: AuditActionCategory.COLLECTION,
  [AuditAction.COLLECTION_VIEW]: AuditActionCategory.COLLECTION,
  [AuditAction.COLLECTION_CONFIG_UPDATE]: AuditActionCategory.COLLECTION,
  [AuditAction.DOCUMENT_UPLOAD]: AuditActionCategory.DOCUMENT,
  [AuditAction.DOCUMENT_DOWNLOAD]: AuditActionCategory.DOCUMENT,
  [AuditAction.DOCUMENT_DELETE]: AuditActionCategory.DOCUMENT,
  [AuditAction.DOCUMENT_VIEW]: AuditActionCategory.DOCUMENT,
  [AuditAction.DOCUMENT_UPDATE]: AuditActionCategory.DOCUMENT,
  [AuditAction.DOCUMENT_SHARE]: AuditActionCategory.DOCUMENT,
  [AuditAction.PERMISSION_GRANT]: AuditActionCategory.PERMISSION,
  [AuditAction.PERMISSION_REVOKE]: AuditActionCategory.PERMISSION,
  [AuditAction.PERMISSION_UPDATE]: AuditActionCategory.PERMISSION,
  [AuditAction.PERMISSION_VIEW]: AuditActionCategory.PERMISSION,
  [AuditAction.QA_QUERY]: AuditActionCategory.QA,
  [AuditAction.QA_CONVERSATION_CREATE]: AuditActionCategory.QA,
  [AuditAction.QA_CONVERSATION_DELETE]: AuditActionCategory.QA,
  [AuditAction.SYSTEM_CONFIG_VIEW]: AuditActionCategory.SYSTEM,
  [AuditAction.SYSTEM_CONFIG_UPDATE]: AuditActionCategory.SYSTEM,
  [AuditAction.SYSTEM_BACKUP]: AuditActionCategory.SYSTEM,
  [AuditAction.SYSTEM_RESTORE]: AuditActionCategory.SYSTEM,
  [AuditAction.SECURITY_LOGIN_FAILED]: AuditActionCategory.SECURITY,
  [AuditAction.SECURITY_ACCOUNT_LOCKED]: AuditActionCategory.SECURITY,
  [AuditAction.SECURITY_SUSPICIOUS_ACTIVITY]: AuditActionCategory.SECURITY,
  [AuditAction.SECURITY_TOKEN_EXPIRED]: AuditActionCategory.SECURITY,
  [AuditAction.EXPORT]: AuditActionCategory.OTHER,
  [AuditAction.IMPORT]: AuditActionCategory.OTHER,
  [AuditAction.BATCH_OPERATION]: AuditActionCategory.OTHER
}

export const AuditLevel = {
  INFO: 'INFO',
  WARNING: 'WARNING',
  SECURITY: 'SECURITY',
  CRITICAL: 'CRITICAL'
} as const

export type AuditLevelType = typeof AuditLevel[keyof typeof AuditLevel]

export const AuditLevelLabels: Record<AuditLevelType, string> = {
  [AuditLevel.INFO]: '普通操作',
  [AuditLevel.WARNING]: '警告操作',
  [AuditLevel.SECURITY]: '安全审计',
  [AuditLevel.CRITICAL]: '关键操作'
}

export interface AuditLogEntry {
  action: AuditActionType
  level?: AuditLevelType
  resourceType?: string
  resourceId?: string
  description?: string
  details?: Record<string, any>
  status?: 'success' | 'failed'
  timestamp?: string
}
