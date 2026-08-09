import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'

/**
 * ModelType 枚举兼容性测试（T030 回归）。
 *
 * 验证点（对应 CR-001 7 枚举替换 CHAT）：
 * - MODEL_TYPES 恰好 7 项，不含 CHAT
 * - isValidModelType 对 7 类型返回 true，对 CHAT 和无效字符串返回 false
 * - normalizeModelType 将 CHAT 映射为 LLM，7 类型原样返回
 * - MODEL_TYPE_LABELS 覆盖 7 类型，不含 CHAT
 * - 目录类型过滤：7 类型均可过滤，0 结果也合法
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

// ============ 目录类型过滤（需要 demo 模式） ============

describe('目录类型过滤', () => {
  beforeEach(() => {
    vi.resetModules()
    vi.stubEnv('VITE_RAG2OKF_DATA_SOURCE', 'demo')
  })

  afterEach(() => {
    vi.unstubAllEnvs()
  })

  it('按每种类型过滤返回正确结果', async () => {
    const { useModelCatalog } = await import('../../../composables/useModelCatalog')
    const { catalog, fetchCatalog, filteredProviders, activeTypeFilter, providerModels } =
      useModelCatalog()

    await fetchCatalog()
    expect(catalog.value.length).toBeGreaterThanOrEqual(3)

    // 无过滤时显示所有有模型的提供商
    const allProviders = filteredProviders.value
    expect(allProviders.length).toBeGreaterThanOrEqual(3)

    // LLM: DeepSeek/QWEN/OpenAI 都有
    activeTypeFilter.value = 'LLM'
    expect(filteredProviders.value.length).toBe(3)
    for (const p of filteredProviders.value) {
      expect(p.models.some((m) => m.modelType === 'LLM')).toBe(true)
    }

    // EMBEDDING: DeepSeek/QWEN/OpenAI 都有
    activeTypeFilter.value = 'EMBEDDING'
    expect(filteredProviders.value.length).toBe(3)
    for (const p of filteredProviders.value) {
      expect(p.models.some((m) => m.modelType === 'EMBEDDING')).toBe(true)
    }

    // RERANK: 仅 QWEN 有
    activeTypeFilter.value = 'RERANK'
    expect(filteredProviders.value.length).toBe(1)
    expect(filteredProviders.value[0].providerCode).toBe('QWEN')

    // TTS: 仅 OpenAI 有
    activeTypeFilter.value = 'TTS'
    expect(filteredProviders.value.length).toBe(1)
    expect(filteredProviders.value[0].providerCode).toBe('OPENAI')

    // ASR: 仅 OpenAI 有
    activeTypeFilter.value = 'ASR'
    expect(filteredProviders.value.length).toBe(1)
    expect(filteredProviders.value[0].providerCode).toBe('OPENAI')

    // VLM: 仅 OpenAI 有
    activeTypeFilter.value = 'VLM'
    expect(filteredProviders.value.length).toBe(1)
    expect(filteredProviders.value[0].providerCode).toBe('OPENAI')
  })

  it('OCR 类型返回 0 提供商（合法）', async () => {
    const { useModelCatalog } = await import('../../../composables/useModelCatalog')
    const { fetchCatalog, filteredProviders, activeTypeFilter } = useModelCatalog()

    await fetchCatalog()

    activeTypeFilter.value = 'OCR'
    expect(filteredProviders.value.length).toBe(0)
  })

  it('providerModels 按类型过滤提供商内模型列表', async () => {
    const { useModelCatalog } = await import('../../../composables/useModelCatalog')
    const { fetchCatalog, activeTypeFilter, providerModels, catalog } = useModelCatalog()

    await fetchCatalog()

    // 找到 OpenAI 提供商
    const openai = catalog.value.find((p) => p.providerCode === 'OPENAI')
    expect(openai).toBeDefined()

    // LLM 过滤：gpt-4o, gpt-4o-mini
    activeTypeFilter.value = 'LLM'
    const llmModels = providerModels(openai!)
    expect(llmModels.length).toBe(2)
    expect(llmModels.every((m) => m.modelType === 'LLM')).toBe(true)

    // EMBEDDING 过滤
    activeTypeFilter.value = 'EMBEDDING'
    const embModels = providerModels(openai!)
    expect(embModels.length).toBe(2)
    expect(embModels.every((m) => m.modelType === 'EMBEDDING')).toBe(true)

    // ASR 过滤
    activeTypeFilter.value = 'ASR'
    const asrModels = providerModels(openai!)
    expect(asrModels.length).toBe(1)
    expect(asrModels[0].modelName).toBe('whisper-1')

    // TTS 过滤
    activeTypeFilter.value = 'TTS'
    const ttsModels = providerModels(openai!)
    expect(ttsModels.length).toBe(1)
    expect(ttsModels[0].modelName).toBe('tts-1')

    // VLM 过滤
    activeTypeFilter.value = 'VLM'
    const vlmModels = providerModels(openai!)
    expect(vlmModels.length).toBe(1)
    expect(vlmModels[0].modelName).toBe('gpt-4o-vision')

    // OCR 过滤：OpenAI 无 OCR 模型
    activeTypeFilter.value = 'OCR'
    const ocrModels = providerModels(openai!)
    expect(ocrModels.length).toBe(0)
  })

  it('catalog typeCounts 包含全部 7 类型', async () => {
    const { getModelCatalog } = await import('../../../api/models')
    const catalog = await getModelCatalog()

    for (const type of SEVEN_TYPES) {
      expect(catalog.typeCounts).toHaveProperty(type)
    }
    expect(catalog.typeCounts.OCR).toBe(0)
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
