import { describe, it, expect } from 'vitest'
import { formatTime, formatBytes, parseStatusLabel, publishStatusLabel, taskStatusLabel, modelTestLabel } from '../formatters'

describe('formatters', () => {
  describe('formatTime', () => {
    it('returns Chinese label for valid date string', () => {
      const result = formatTime('2026-08-07T14:30:00Z')
      expect(result).not.toBe('未知时间')
      expect(result).toMatch(/\d/)
    })

    it('returns fallback for invalid date', () => {
      expect(formatTime('not-a-date')).toBe('未知时间')
    })

    it('returns fallback for null or undefined', () => {
      expect(formatTime(null)).toBe('未知时间')
      expect(formatTime(undefined)).toBe('未知时间')
    })

    it('accepts Date object', () => {
      const result = formatTime(new Date('2026-08-07T14:30:00Z'))
      expect(result).not.toBe('未知时间')
    })
  })

  describe('formatBytes', () => {
    it('formats bytes', () => {
      expect(formatBytes(500)).toBe('500 B')
    })

    it('formats kilobytes', () => {
      expect(formatBytes(2048)).toBe('2 KB')
    })

    it('formats megabytes', () => {
      expect(formatBytes(1024 * 1024 * 5)).toBe('5.0 MB')
    })

    it('formats gigabytes', () => {
      expect(formatBytes(1024 * 1024 * 1024 * 2)).toBe('2.00 GB')
    })

    it('returns fallback for invalid values', () => {
      expect(formatBytes(null)).toBe('未知大小')
      expect(formatBytes(-1)).toBe('未知大小')
      expect(formatBytes(NaN)).toBe('未知大小')
    })
  })

  describe('parseStatusLabel', () => {
    it('maps known statuses', () => {
      expect(parseStatusLabel('NOT_STARTED')).toBe('尚未解析')
      expect(parseStatusLabel('SUCCEEDED')).toBe('已完成')
      expect(parseStatusLabel('FAILED')).toBe('处理失败')
    })

    it('returns original value for unknown status', () => {
      expect(parseStatusLabel('UNKNOWN')).toBe('UNKNOWN')
    })
  })

  describe('publishStatusLabel', () => {
    it('maps known statuses', () => {
      expect(publishStatusLabel('UNPUBLISHED')).toBe('未发布')
      expect(publishStatusLabel('PUBLISHED')).toBe('已发布')
      expect(publishStatusLabel('PUBLISHING')).toBe('发布中')
      expect(publishStatusLabel('PUBLISH_FAILED')).toBe('发布失败')
    })
  })

  describe('taskStatusLabel', () => {
    it('maps known statuses', () => {
      expect(taskStatusLabel('PENDING')).toBe('等待中')
      expect(taskStatusLabel('RUNNING')).toBe('执行中')
      expect(taskStatusLabel('SUCCEEDED')).toBe('已完成')
      expect(taskStatusLabel('FAILED')).toBe('已失败')
    })
  })

  describe('modelTestLabel', () => {
    it('maps by status first', () => {
      expect(modelTestLabel('SUCCESS')).toBe('成功')
    })

    it('falls back to errorCode', () => {
      expect(modelTestLabel(null, 'TIMEOUT')).toBe('测试超时，请稍后重试')
    })

    it('returns generic message for unknown', () => {
      expect(modelTestLabel(null, null)).toBe('测试未通过，请稍后重试。')
    })
  })
})
