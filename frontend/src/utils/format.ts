import dayjs from 'dayjs'

export const formatDate = (date: string | Date | null | undefined, format: string = 'YYYY-MM-DD HH:mm:ss'): string => {
  if (!date) return '-'
  const d = dayjs(date)
  if (!d.isValid()) return '-'
  return d.format(format)
}

export const formatFileSize = (bytes: number): string => {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

export const formatDuration = (ms: number): string => {
  if (ms < 1000) return `${ms}ms`
  if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`
  return `${(ms / 60000).toFixed(1)}min`
}

export const getStatusType = (status: string): 'success' | 'warning' | 'info' | 'danger' | 'primary' | undefined => {
  const statusMap: Record<string, 'success' | 'warning' | 'info' | 'danger'> = {
    ACTIVE: 'success',
    COMPLETED: 'success',
    SUCCESS: 'success',
    UP: 'success',
    NORMAL: 'success',
    PENDING: 'warning',
    PROCESSING: 'warning',
    PARSING: 'warning',
    CHUNKING: 'warning',
    VECTORIZING: 'warning',
    CREATING: 'warning',
    UPLOADING: 'info',
    INACTIVE: 'info',
    DISABLED: 'info',
    PAUSED: 'info',
    ARCHIVED: 'info',
    DELETED: 'danger',
    FAILED: 'danger',
    DOWN: 'danger',
    ERROR: 'danger',
    DEGRADED: 'warning',
    LOCKED: 'warning'
  }
  return statusMap[status] || undefined
}

export const getStatusName = (status: string): string => {
  const nameMap: Record<string, string> = {
    ACTIVE: '启用',
    INACTIVE: '停用',
    PAUSED: '暂停',
    ARCHIVED: '归档',
    COMPLETED: '已完成',
    SUCCESS: '成功',
    PENDING: '待处理',
    PROCESSING: '处理中',
    PARSING: '解析中',
    CHUNKING: '分块中',
    VECTORIZING: '向量化',
    UPLOADING: '上传中',
    FAILED: '失败',
    ERROR: '错误',
    DISABLED: '禁用',
    LOCKED: '锁定',
    UP: '正常',
    DOWN: '异常',
    NORMAL: '正常'
  }
  return nameMap[status] || status
}

export const RoleCode = {
  SYSTEM_ADMIN: 'SYSTEM_ADMIN',
  DEPT_ADMIN: 'DEPT_ADMIN',
  DEPT_UPLOADER: 'DEPT_UPLOADER',
  DEPT_USER: 'DEPT_USER'
} as const

export type RoleCodeType = typeof RoleCode[keyof typeof RoleCode]

export const RoleLevel = {
  L1_SYSTEM_ADMIN: 1,
  L2_DEPT_ADMIN: 2,
  L3_DEPT_UPLOADER: 3,
  L4_DEPT_USER: 4
} as const

export const RoleColorMap: Record<string, 'success' | 'warning' | 'danger' | 'info' | ''> = {
  SYSTEM_ADMIN: 'danger',
  DEPT_ADMIN: 'warning',
  DEPT_UPLOADER: 'info',
  DEPT_USER: 'success',
  L1: 'danger',
  L2: 'warning',
  L3: 'info',
  L4: 'success'
}

export const RoleNameMap: Record<string, string> = {
  SYSTEM_ADMIN: '系统管理员',
  DEPT_ADMIN: '部门管理员',
  DEPT_UPLOADER: '部门上传者',
  DEPT_USER: '部门普通人员',
  L1: '系统管理员',
  L2: '部门管理员',
  L3: '部门上传者',
  L4: '部门普通人员'
}

export const getRoleName = (role: string | number): string => {
  if (typeof role === 'number') {
    return RoleNameMap[`L${role}`] || `L${role}`
  }
  return RoleNameMap[role] || role
}

export const getRoleType = (role: string | number): 'success' | 'warning' | 'danger' | 'info' => {
  if (typeof role === 'number') {
    return RoleColorMap[`L${role}`] || 'info'
  }
  return RoleColorMap[role] || 'info'
}

export const getLevelName = (level: number): string => {
  return RoleNameMap[`L${level}`] || `L${level}`
}

export const getLevelType = (level: number): 'success' | 'warning' | 'danger' | 'info' => {
  return RoleColorMap[`L${level}`] || 'info'
}

export const getDocumentTypeIcon = (type: string): string => {
  const iconMap: Record<string, string> = {
    PDF: 'Document',
    WORD: 'Document',
    MARKDOWN: 'Document',
    TXT: 'Document'
  }
  return iconMap[type] || 'Document'
}

export const getPermissionTypeName = (type: string): string => {
  const nameMap: Record<string, string> = {
    COMPANY_WIDE: '全公司可查看',
    DEPARTMENT_WIDE: '部门内公开',
    RESTRICTED: '指定人员开放',
    DEPARTMENT_WIDE_RESTRICTED: '部门全员+指定人员'
  }
  return nameMap[type] || type
}

export const getPermissionLevelName = (level: string): string => {
  const nameMap: Record<string, string> = {
    READ: '读取',
    COMMENT: '评论',
    EDIT: '编辑',
    FULL: '完全控制'
  }
  return nameMap[level] || level
}

export const getOperationTypeName = (type: string): string => {
  const nameMap: Record<string, string> = {
    GRANT: '授予权限',
    REVOKE: '撤销权限',
    UPDATE: '更新权限'
  }
  return nameMap[type] || type
}

export const getTargetTypeName = (type: string): string => {
  const nameMap: Record<string, string> = {
    USER: '用户',
    DEPARTMENT: '部门',
    DOCUMENT: '文档',
    ROLE: '角色'
  }
  return nameMap[type] || type
}

export const getPermissionTypeTag = (type: string): 'success' | 'warning' | 'danger' | 'info' => {
  const typeMap: Record<string, 'success' | 'warning' | 'danger' | 'info'> = {
    PUBLIC: 'success',
    COMPANY_WIDE: 'success',
    DEPARTMENT_WIDE: 'warning',
    RESTRICTED: 'danger',
    DEPARTMENT_WIDE_RESTRICTED: 'warning'
  }
  return typeMap[type] || 'info'
}
