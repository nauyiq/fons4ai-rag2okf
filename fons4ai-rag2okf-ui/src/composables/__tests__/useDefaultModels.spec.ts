import { describe, it, expect, beforeEach, vi } from 'vitest'

/**
 * useDefaultModels composable 测试（T030 回归）。
 *
 * 验证点（对应 CR-001 枚举兼容 + 默认模型偏好）：
 * - CHAT→LLM 迁移：历史 CHAT 键迁移到 LLM 并清理
 * - 迁移幂等性：重复加载不产生重复 LLM 条目
 * - 保存隔离：仅发送 defaultModels，不触碰其他 preferenceJson 键
 * - 空默认值：所有 7 类型槽位为空
 */

vi.mock('../../api/models', () => ({
  getDefaultModels: vi.fn(),
  saveDefaultModels: vi.fn(),
}))

import { getDefaultModels, saveDefaultModels, type DefaultModelSettings } from '../../api/models'
import { useDefaultModels } from '../useDefaultModels'
import { MODEL_TYPES } from '../../types/model'

const mockedGetDefaultModels = vi.mocked(getDefaultModels)
const mockedSaveDefaultModels = vi.mocked(saveDefaultModels)

describe('useDefaultModels', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  // ============ CHAT→LLM 迁移 ============

  describe('CHAT→LLM 迁移', () => {
    it('加载历史 CHAT 默认值时迁移为 LLM 并清理 CHAT 键', async () => {
      mockedGetDefaultModels.mockResolvedValue({
        defaults: { CHAT: 'prof-001' },
      } as unknown as DefaultModelSettings)

      const { defaults, load } = useDefaultModels()
      await load()

      expect(defaults.value.defaults.LLM).toBe('prof-001')
      expect((defaults.value.defaults as Record<string, unknown>).CHAT).toBeUndefined()
    })

    it('迁移是幂等的：重复加载不产生重复 LLM 条目', async () => {
      mockedGetDefaultModels.mockResolvedValue({
        defaults: { CHAT: 'prof-001' },
      } as unknown as DefaultModelSettings)

      const { defaults, load } = useDefaultModels()

      // 第一次加载
      await load()
      expect(defaults.value.defaults.LLM).toBe('prof-001')
      expect((defaults.value.defaults as Record<string, unknown>).CHAT).toBeUndefined()

      // 第二次加载（模拟重复加载场景）
      await load()
      expect(defaults.value.defaults.LLM).toBe('prof-001')
      expect((defaults.value.defaults as Record<string, unknown>).CHAT).toBeUndefined()

      // LLM 键只出现一次，不产生重复
      const llmKeys = Object.keys(defaults.value.defaults).filter((k) => k === 'LLM')
      expect(llmKeys.length).toBe(1)
    })

    it('migrateChatKey 对已迁移对象不再修改', () => {
      const { migrateChatKey } = useDefaultModels()

      const migrated = migrateChatKey({
        defaults: { CHAT: 'prof-001' },
      } as unknown as DefaultModelSettings)
      expect(migrated.defaults.LLM).toBe('prof-001')
      expect((migrated.defaults as Record<string, unknown>).CHAT).toBeUndefined()

      // 对已迁移对象再次调用（幂等）
      const twice = migrateChatKey(migrated)
      expect(twice.defaults.LLM).toBe('prof-001')
      expect((twice.defaults as Record<string, unknown>).CHAT).toBeUndefined()
      expect(Object.keys(twice.defaults).length).toBe(1)
    })

    it('CHAT 与 LLM 同时存在时保留 LLM 值并清理 CHAT', async () => {
      mockedGetDefaultModels.mockResolvedValue({
        defaults: { CHAT: 'prof-old', LLM: 'prof-new' },
      } as unknown as DefaultModelSettings)

      const { defaults, load } = useDefaultModels()
      await load()

      // LLM 已有值时不被 CHAT 覆盖
      expect(defaults.value.defaults.LLM).toBe('prof-new')
      expect((defaults.value.defaults as Record<string, unknown>).CHAT).toBeUndefined()
    })
  })

  // ============ 保存隔离 ============

  describe('保存行为', () => {
    it('保存仅发送 defaultModels，不触碰其他 preferenceJson 键', async () => {
      mockedGetDefaultModels.mockResolvedValue({ defaults: { LLM: 'prof-001' } })

      const { load, save } = useDefaultModels()
      await load()
      await save()

      expect(mockedSaveDefaultModels).toHaveBeenCalledTimes(1)
      const arg = mockedSaveDefaultModels.mock.calls[0][0]

      // 仅包含 defaults 键，不含其他 preferenceJson 键
      expect(arg).toEqual({ defaults: { LLM: 'prof-001' } })
      expect(Object.keys(arg)).toEqual(['defaults'])
    })
  })

  // ============ 空默认值 ============

  describe('空默认值', () => {
    it('空 defaults 加载后所有 7 类型槽位为空', async () => {
      mockedGetDefaultModels.mockResolvedValue({ defaults: {} })

      const { defaults, load } = useDefaultModels()
      await load()

      expect(defaults.value.defaults).toEqual({})
      // 所有 7 类型槽位均为 undefined
      for (const type of MODEL_TYPES) {
        expect(defaults.value.defaults[type]).toBeUndefined()
      }
    })

    it('空 defaults 迁移后不产生任何键', async () => {
      mockedGetDefaultModels.mockResolvedValue({ defaults: {} })

      const { defaults, load } = useDefaultModels()
      await load()

      expect(Object.keys(defaults.value.defaults).length).toBe(0)
    })
  })

  // ============ loading 状态 ============

  describe('loading 状态', () => {
    it('加载过程中 loading 为 true，完成后为 false', async () => {
      mockedGetDefaultModels.mockResolvedValue({ defaults: { LLM: 'prof-001' } })

      const { loading, load } = useDefaultModels()
      expect(loading.value).toBe(false)

      const promise = load()
      expect(loading.value).toBe(true)

      await promise
      expect(loading.value).toBe(false)
    })
  })
})
