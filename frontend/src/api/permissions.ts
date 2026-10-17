import { request } from './request'
import type {
  DocumentPermission,
  AccessCheckResult,
  BatchAccessCheckResult,
  PermissionAuditLog,
  PermissionType,
  PermissionLevel,
  PageResult
} from '@/types'

export const permissionsApi = {
  checkAccess: (documentId: string) => {
    return request<AccessCheckResult>(`/documents/${documentId}/access-check`)
  },

  batchCheckAccess: (documentIds: string[]) => {
    return request<BatchAccessCheckResult>('/documents/batch-access-check', {
      method: 'POST',
      data: { documentIds }
    })
  },

  getDocumentPermission: (documentId: string) => {
    return request<DocumentPermission>(`/documents/${documentId}/permissions`)
  },

  updateDocumentPermission: (
    documentId: string,
    data: {
      permissionType: PermissionType
      allowedUserIds?: string[]
      expiresAt?: string | null
    }
  ) => {
    return request<DocumentPermission>(`/documents/${documentId}/permissions`, {
      method: 'POST',
      data
    })
  },

  addUserPermission: (
    documentId: string,
    data: {
      userId: string
      permissionLevel: PermissionLevel
      expiresAt?: string | null
    }
  ) => {
    return request<void>(`/documents/${documentId}/permissions/users`, {
      method: 'POST',
      data
    })
  },

  removeUserPermission: (documentId: string, targetUserId: string) => {
    return request<void>(`/documents/${documentId}/permissions/users/${targetUserId}`, {
      method: 'DELETE'
    })
  },

  getAuditLogs: (params: {
    page?: number
    pageSize?: number
    targetType?: string
    targetId?: string
    operatorId?: string
    operationType?: string
    startTime: number
    endTime: number
  }) => {
    return request<PageResult<PermissionAuditLog>>('/audit-logs/permissions', {
      method: 'GET',
      params
    })
  }
}
