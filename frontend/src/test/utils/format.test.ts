import { describe, it, expect } from 'vitest'
import { formatDate, formatFileSize, formatDuration, getStatusType, getStatusName } from '@/utils/format'

describe('Format Utils', () => {
  describe('formatDate', () => {
    it('should return "-" for null/undefined/empty', () => {
      expect(formatDate(null)).toBe('-')
      expect(formatDate(undefined)).toBe('-')
    })

    it('should return "-" for invalid date', () => {
      expect(formatDate('not a date')).toBe('-')
    })

    it('should format valid date with default format', () => {
      const result = formatDate('2026-01-15T10:30:00Z')
      expect(result).toMatch(/^2026-01-1[45] \d{2}:30:00$/)
    })

    it('should support custom format', () => {
      const result = formatDate('2026-01-15T10:30:00Z', 'YYYY/MM/DD')
      expect(result).toBe('2026/01/15')
    })

    it('should accept Date object', () => {
      const date = new Date('2026-06-01T00:00:00Z')
      const result = formatDate(date, 'YYYY-MM-DD')
      expect(result).toMatch(/^2026-0[56]-\d{2}$/)
    })
  })

  describe('formatFileSize', () => {
    it('should return 0 B for 0', () => {
      expect(formatFileSize(0)).toBe('0 B')
    })

    it('should format bytes', () => {
      expect(formatFileSize(500)).toMatch(/500 B/)
    })

    it('should format KB', () => {
      expect(formatFileSize(1024)).toMatch(/1 KB/)
    })

    it('should format MB', () => {
      expect(formatFileSize(1024 * 1024)).toMatch(/1 MB/)
    })

    it('should format GB', () => {
      expect(formatFileSize(1024 * 1024 * 1024)).toMatch(/1 GB/)
    })

    it('should format TB', () => {
      expect(formatFileSize(1024 * 1024 * 1024 * 1024)).toMatch(/1 TB/)
    })

    it('should round to 2 decimal places', () => {
      const result = formatFileSize(1500)
      expect(result).toMatch(/1\.46 KB/)
    })
  })

  describe('formatDuration', () => {
    it('should format ms for < 1000', () => {
      expect(formatDuration(500)).toBe('500ms')
      expect(formatDuration(0)).toBe('0ms')
    })

    it('should format seconds for < 60s', () => {
      expect(formatDuration(1500)).toMatch(/1\.5s/)
      expect(formatDuration(59999)).toMatch(/60\.0s/)
    })

    it('should format minutes for >= 60s', () => {
      expect(formatDuration(60000)).toMatch(/1\.0min/)
      expect(formatDuration(120000)).toMatch(/2\.0min/)
    })
  })

  describe('getStatusType', () => {
    it('should map success statuses', () => {
      expect(getStatusType('ACTIVE')).toBe('success')
      expect(getStatusType('COMPLETED')).toBe('success')
      expect(getStatusType('SUCCESS')).toBe('success')
      expect(getStatusType('UP')).toBe('success')
    })

    it('should map warning statuses', () => {
      expect(getStatusType('PENDING')).toBe('warning')
      expect(getStatusType('PROCESSING')).toBe('warning')
      expect(getStatusType('VECTORIZING')).toBe('warning')
    })

    it('should map info statuses', () => {
      expect(getStatusType('UPLOADING')).toBe('info')
      expect(getStatusType('INACTIVE')).toBe('info')
    })

    it('should map danger statuses', () => {
      expect(getStatusType('DELETED')).toBe('danger')
      expect(getStatusType('FAILED')).toBe('danger')
      expect(getStatusType('ERROR')).toBe('danger')
      expect(getStatusType('DOWN')).toBe('danger')
    })

    it('should return undefined for unknown status', () => {
      expect(getStatusType('UNKNOWN_STATUS')).toBeUndefined()
    })
  })

  describe('getStatusName', () => {
    it('should return Chinese name for known status', () => {
      expect(getStatusName('ACTIVE')).toBe('启用')
      expect(getStatusName('INACTIVE')).toBe('停用')
      expect(getStatusName('PENDING')).toBe('待处理')
      expect(getStatusName('COMPLETED')).toBe('已完成')
    })

    it('should return original status for unknown', () => {
      expect(getStatusName('CUSTOM_STATUS')).toBe('CUSTOM_STATUS')
    })
  })
})
