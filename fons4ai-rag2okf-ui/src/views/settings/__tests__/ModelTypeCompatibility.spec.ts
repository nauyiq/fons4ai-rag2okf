import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'

/**
 * ModelType 枚举兼容性测试（T030 回归）。
 *
 * 验证点（对应 CR-001 7 枚举替换 CHAT）：
 * - MODEL_TYPES 恰好 7 项，不含 CHAT
 * - isValidModelType 对 7 类型返回 true，对 CHAT 和无效字符串返回 false
 * - normalizeModelType 将 CHAT 映射为 LLM，7 类型原样返回
 * - MODEL_TYPE_LABELS 覆盖 7 类型，不含 CHAT
 * - Profile modelType：demo mock 数据中所有 profile 均为合法 7 类型
 */

vi.mock('../../../api/http', () => ({
  request: vi.fn(),
}))

import {
  MODEL_TYPES,
  MODEL_TYPE_LABELS,
  isValidModelType,
  normalizeModelType,
  type ModelType,
} from '../../../types/model'

const SEVEN_TYPES: ModelType[] = ['LLM', 'EMBEDDING', 'RERANK', 'TTS', 'ASR', 'VLM', 'OCR']

describe('ModelType 枚举兼容性', () => {
  // ============ 7 枚举白名单 ============

  describe('7 枚举白名单', () => {
    it('MODEL_TYPES 数组恰好 7 项', () => {
      expect(MODEL_TYPES).toHaveLength(7)
    })

    it('MODEL_TYPES 不包含 CHAT', () => {
      expect(MODEL_TYPES).not.toContain('CHAT' as ModelType)
    })

    it('MODEL_TYPES 包含全部 7 种类型', () => {
      for (const type of SEVEN_TYPES) {
        expect(MODEL_TYPES).toContain(type)
      }
    })

    it('MODEL_TYPES 顺序为 LLM/EMBEDDING/RERANK/TTS/ASR/VLM/OCR', () => {
      expect(MODEL_TYPES).toEqual(SEVEN_TYPES)
    })
  })

  // ============ isValidModelType ============

  describe('isValidModelType', () => {
    it('7 种类型均返回 true', () => {
      for (const type of SEVEN_TYPES) {
        expect(isValidModelType(type)).toBe(true)
      }
    })

    it('CHAT 返回 false', () => {
      expect(isValidModelType('CHAT')).toBe(false)
    })

    it('其他无效字符串返回 false', () => {
      expect(isValidModelType('')).toBe(false)
      expect(isValidModelType('chat')).toBe(false)
      expect(isValidModelType('Chat')).toBe(false)
      expect(isValidModelType('TEXT')).toBe(false)
      expect(isValidModelType('IMAGE')).toBe(false)
      expect(isValidModelType('WHATEVER')).toBe(false)
    })
  })

  // ============ normalizeModelType ============

  describe('normalizeModelType', () => {
    it('CHAT 映射为 LLM', () => {
      expect(normalizeModelType('CHAT')).toBe('LLM')
    })

    it('7 种类型原样返回', () => {
      for (const type of SEVEN_TYPES) {
        expect(normalizeModelType(type)).toBe(type)
      }
    })
  })

  // ============ MODEL_TYPE_LABELS ============

  describe('MODEL_TYPE_LABELS', () => {
    it('7 种类型均有标签', () => {
      for (const type of SEVEN_TYPES) {
        expect(MODEL_TYPE_LABELS[type]).toBeTruthy()
        expect(typeof MODEL_TYPE_LABELS[type]).toBe('string')
      }
    })

    it('不含 CHAT 标签', () => {
      expect(MODEL_TYPE_LABELS).not.toHaveProperty('CHAT')
    })

    it('标签数量恰好 7 项', () => {
      expect(Object.keys(MODEL_TYPE_LABELS)).toHaveLength(7)
    })
  })
})

// ============ Profile modelType 值（需要 demo 模式） ============

describe('Profile modelType 值', () => {
  beforeEach(() => {
    vi.resetModules()
    vi.stubEnv('VITE_RAG2OKF_DATA_SOURCE', 'demo')
  })

  afterEach(() => {
    vi.unstubAllEnvs()
  })

  it('demo mock 数据中所有 profile 的 modelType 为合法 7 类型', async () => {
    const { listProfiles } = await import('../../../api/models')
    const { isValidModelType } = await import('../../../types/model')

    const profiles = await listProfiles()
    expect(profiles.length).toBeGreaterThan(0)

    for (const p of profiles) {
      expect(isValidModelType(p.modelType)).toBe(true)
      expect(p.modelType).not.toBe('CHAT')
    }
  })

  it('demo mock 数据中不存在 modelType 为 CHAT 的 profile', async () => {
    const { listProfiles } = await import('../../../api/models')
    const profiles = await listProfiles()

    const chatProfiles = profiles.filter((p) => p.modelType === ('CHAT' as ModelType))
    expect(chatProfiles.length).toBe(0)
  })
})
