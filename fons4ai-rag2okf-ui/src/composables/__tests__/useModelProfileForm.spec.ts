import { describe, it, expect } from 'vitest'

/**
 * 模型档案表单校验测试（T030 回归）。
 *
 * 验证点（对应 CR-001 7 枚举 + 动态高级参数）：
 * - LLM 档案：temperature/contextWindowLength 齐全时有效，缺 modelName 无效
 * - EMBEDDING 档案：带 dimensions 有效，temperature 可选
 * - RERANK 档案：仅需 modelName + timeoutSeconds，dimensions 非必需
 * - 无效 modelType CHAT：不被接受（产生校验错误）
 * - dimensions 校验：正整数有效，负数/零/浮点被拒绝
 * - temperature 校验：0-2 范围有效，负数或 >2 被拒绝
 * - timeout 校验：1-120 范围有效
 */

import {
  validateProfileForm,
  createEmptyProfileForm,
  useModelForm,
  type ProfileFormState,
} from '../useModelForm'
import type { ModelType } from '../../types/model'
import type { ModelProfile } from '../../api/models'

/** 构造一个基础有效表单（LLM 类型，所有字段合法）。 */
function validBaseForm(): ProfileFormState {
  return {
    connectionKey: 'conn-001',
    modelType: 'LLM',
    modelName: 'gpt-4o',
    dimensions: null,
    contextWindowLength: 32000,
    timeoutSeconds: 60,
    temperature: 0.7,
  }
}

describe('useModelProfileForm 校验', () => {
  // ============ LLM 档案 ============

  describe('LLM 档案', () => {
    it('temperature + contextWindowLength 齐全时有效', () => {
      const form = validBaseForm()
      const errors = validateProfileForm(form)
      expect(errors).toHaveLength(0)
    })

    it('缺少 modelName 时无效', () => {
      const form = validBaseForm()
      form.modelName = ''
      const errors = validateProfileForm(form)
      expect(errors).toContain('请填写模型名称')
    })

    it('temperature 为 null 时仍有效（可选字段）', () => {
      const form = validBaseForm()
      form.temperature = null
      const errors = validateProfileForm(form)
      expect(errors).toHaveLength(0)
    })

    it('contextWindowLength 为 null 时仍有效（可选字段）', () => {
      const form = validBaseForm()
      form.contextWindowLength = null
      const errors = validateProfileForm(form)
      expect(errors).toHaveLength(0)
    })
  })

  // ============ EMBEDDING 档案 ============

  describe('EMBEDDING 档案', () => {
    it('带 dimensions 时有效', () => {
      const form: ProfileFormState = {
        connectionKey: 'conn-001',
        modelType: 'EMBEDDING',
        modelName: 'text-embedding-3-large',
        dimensions: 1024,
        contextWindowLength: null,
        timeoutSeconds: 60,
        temperature: null,
      }
      const errors = validateProfileForm(form)
      expect(errors).toHaveLength(0)
    })

    it('temperature 为 null 时有效（可选）', () => {
      const form: ProfileFormState = {
        connectionKey: 'conn-001',
        modelType: 'EMBEDDING',
        modelName: 'text-embedding-v3',
        dimensions: 1024,
        contextWindowLength: null,
        timeoutSeconds: 60,
        temperature: null,
      }
      const errors = validateProfileForm(form)
      expect(errors).toHaveLength(0)
    })

    it('不填 dimensions 时仍有效（dimensions 非必填）', () => {
      const form: ProfileFormState = {
        connectionKey: 'conn-001',
        modelType: 'EMBEDDING',
        modelName: 'text-embedding-v3',
        dimensions: null,
        contextWindowLength: null,
        timeoutSeconds: 60,
        temperature: null,
      }
      const errors = validateProfileForm(form)
      expect(errors).toHaveLength(0)
    })
  })

  // ============ RERANK 档案 ============

  describe('RERANK 档案', () => {
    it('仅需 modelName + timeoutSeconds，dimensions 非必需', () => {
      const form: ProfileFormState = {
        connectionKey: 'conn-001',
        modelType: 'RERANK',
        modelName: 'gte-rerank',
        dimensions: null,
        contextWindowLength: null,
        timeoutSeconds: 30,
        temperature: null,
      }
      const errors = validateProfileForm(form)
      expect(errors).toHaveLength(0)
    })

    it('RERANK 不填 dimensions 也不报 dimensions 错误', () => {
      const form: ProfileFormState = {
        connectionKey: 'conn-001',
        modelType: 'RERANK',
        modelName: 'gte-rerank',
        dimensions: null,
        contextWindowLength: null,
        timeoutSeconds: 30,
        temperature: null,
      }
      const errors = validateProfileForm(form)
      expect(errors.some((e) => e.includes('向量维度'))).toBe(false)
    })
  })

  // ============ 无效 modelType CHAT ============

  describe('无效 modelType CHAT', () => {
    it('CHAT 不被接受（产生校验错误）', () => {
      const form = validBaseForm()
      ;(form as { modelType: string }).modelType = 'CHAT'
      const errors = validateProfileForm(form)
      expect(errors).toContain('请选择模型类型')
    })

    it('prepareEditProfile 将历史 CHAT 归一化为 LLM', () => {
      const { prepareEditProfile, profileForm } = useModelForm()

      const chatProfile: ModelProfile = {
        profileKey: 'prof-001',
        connectionKey: 'conn-001',
        modelType: 'CHAT' as ModelType,
        modelName: 'gpt-3.5-turbo',
        dimensions: null,
        contextWindowLength: 16000,
        timeoutSeconds: 30,
        temperature: 0.7,
        status: 'ACTIVE',
        lastTestStatus: 'SUCCESS',
        lastTestAt: null,
        updated: '2024-01-01T00:00:00Z',
      }

      prepareEditProfile(chatProfile)

      expect(profileForm.value.modelType).toBe('LLM')
      expect(profileForm.value.modelType).not.toBe('CHAT')
    })
  })

  // ============ dimensions 校验 ============

  describe('dimensions 校验', () => {
    it('正整数有效', () => {
      const form = validBaseForm()
      form.modelType = 'EMBEDDING'
      form.dimensions = 1024
      const errors = validateProfileForm(form)
      expect(errors.some((e) => e.includes('向量维度'))).toBe(false)
    })

    it('负数被拒绝', () => {
      const form = validBaseForm()
      form.dimensions = -1
      const errors = validateProfileForm(form)
      expect(errors).toContain('向量维度需为正整数')
    })

    it('零被拒绝', () => {
      const form = validBaseForm()
      form.dimensions = 0
      const errors = validateProfileForm(form)
      expect(errors).toContain('向量维度需为正整数')
    })

    it('浮点数被拒绝', () => {
      const form = validBaseForm()
      form.dimensions = 1.5
      const errors = validateProfileForm(form)
      expect(errors).toContain('向量维度需为正整数')
    })

    it('null 时不报错（可选字段）', () => {
      const form = validBaseForm()
      form.dimensions = null
      const errors = validateProfileForm(form)
      expect(errors.some((e) => e.includes('向量维度'))).toBe(false)
    })
  })

  // ============ temperature 校验 ============

  describe('temperature 校验', () => {
    it('0 有效（边界值）', () => {
      const form = validBaseForm()
      form.temperature = 0
      const errors = validateProfileForm(form)
      expect(errors.some((e) => e.includes('温度'))).toBe(false)
    })

    it('2 有效（边界值）', () => {
      const form = validBaseForm()
      form.temperature = 2
      const errors = validateProfileForm(form)
      expect(errors.some((e) => e.includes('温度'))).toBe(false)
    })

    it('1.5 有效（范围内）', () => {
      const form = validBaseForm()
      form.temperature = 1.5
      const errors = validateProfileForm(form)
      expect(errors.some((e) => e.includes('温度'))).toBe(false)
    })

    it('负数被拒绝', () => {
      const form = validBaseForm()
      form.temperature = -0.1
      const errors = validateProfileForm(form)
      expect(errors).toContain('温度需在 0 到 2 之间')
    })

    it('大于 2 被拒绝', () => {
      const form = validBaseForm()
      form.temperature = 2.1
      const errors = validateProfileForm(form)
      expect(errors).toContain('温度需在 0 到 2 之间')
    })

    it('null 时不报错（可选字段）', () => {
      const form = validBaseForm()
      form.temperature = null
      const errors = validateProfileForm(form)
      expect(errors.some((e) => e.includes('温度'))).toBe(false)
    })
  })

  // ============ timeout 校验 ============

  describe('timeout 校验', () => {
    it('1 有效（下界）', () => {
      const form = validBaseForm()
      form.timeoutSeconds = 1
      const errors = validateProfileForm(form)
      expect(errors.some((e) => e.includes('超时'))).toBe(false)
    })

    it('120 有效（上界）', () => {
      const form = validBaseForm()
      form.timeoutSeconds = 120
      const errors = validateProfileForm(form)
      expect(errors.some((e) => e.includes('超时'))).toBe(false)
    })

    it('60 有效（默认值）', () => {
      const form = createEmptyProfileForm('conn-001')
      form.modelName = 'test-model'
      expect(form.timeoutSeconds).toBe(60)
      const errors = validateProfileForm(form)
      expect(errors.some((e) => e.includes('超时'))).toBe(false)
    })

    it('0 被拒绝（小于下界）', () => {
      const form = validBaseForm()
      form.timeoutSeconds = 0
      const errors = validateProfileForm(form)
      expect(errors).toContain('超时秒数需在 1 到 120 之间')
    })

    it('121 被拒绝（大于上界）', () => {
      const form = validBaseForm()
      form.timeoutSeconds = 121
      const errors = validateProfileForm(form)
      expect(errors).toContain('超时秒数需在 1 到 120 之间')
    })

    it('负数被拒绝', () => {
      const form = validBaseForm()
      form.timeoutSeconds = -1
      const errors = validateProfileForm(form)
      expect(errors).toContain('超时秒数需在 1 到 120 之间')
    })
  })

  // ============ 其他必填字段 ============

  describe('其他必填字段', () => {
    it('缺少 connectionKey 时无效', () => {
      const form = validBaseForm()
      form.connectionKey = ''
      const errors = validateProfileForm(form)
      expect(errors).toContain('请选择所属连接')
    })

    it('modelName 仅有空格时无效', () => {
      const form = validBaseForm()
      form.modelName = '   '
      const errors = validateProfileForm(form)
      expect(errors).toContain('请填写模型名称')
    })

    it('contextWindowLength 负数被拒绝', () => {
      const form = validBaseForm()
      form.contextWindowLength = -1
      const errors = validateProfileForm(form)
      expect(errors).toContain('上下文窗口长度需为正整数')
    })

    it('contextWindowLength 浮点被拒绝', () => {
      const form = validBaseForm()
      form.contextWindowLength = 1.5
      const errors = validateProfileForm(form)
      expect(errors).toContain('上下文窗口长度需为正整数')
    })
  })
})
