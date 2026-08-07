/**
 * 模型配置 API 层（合并后单步契约）。
 *
 * 设计依据：技术设计说明书 §3.1。
 * 将原"Provider 连接 + 模型档案"两步式 API 合并为单一 ModelConfig 概念，
 * 一个模型配置包含连接信息、档案信息与高级参数。
 *
 * demo 模式下走本地 mock 数据，real 模式走 http.request。
 * API Key 只在创建或替换时提交，更新其他字段不提交。
 */
import { request } from './http'
import { isDemoMode } from '../composables/useDataSource'
import {
  mockListModelProviderTemplates,
  mockListModelConfigs,
  mockCreateModelConfig,
  mockUpdateModelConfig,
  mockReplaceModelApiKey,
  mockTestModelConfig,
  mockDeleteModelConfig,
} from './mock/models'

/** 厂商模板信息，供新建模型配置时选择厂商和填充默认 baseUrl。 */
export interface ModelProviderTemplateInfo {
  code: string
  providerName: string
  defaultBaseUrl: string | null
}

/** 合并后的单一模型配置概念，包含连接信息、档案信息与高级参数。 */
export interface ModelConfig {
  modelConfigKey: string
  /** 基础信息 */
  providerCode: string
  providerName: string
  displayName: string
  baseUrl: string
  /** API Key 只返回掩码，不回显原值（延续 BR-019） */
  apiKeyMask: string
  /** 是否已配置 API Key，用于前端判断是否需要提示替换 */
  apiKeyConfigured: boolean
  /** 模型档案信息 */
  modelType: 'CHAT' | 'EMBEDDING'
  modelName: string
  dimensions: number | null
  /** 高级参数 */
  contextWindowLength: number | null
  timeoutSeconds: number
  temperature: number | null
  /** 状态 */
  status: 'ACTIVE' | 'DISABLED'
  lastTestStatus: string
  lastTestAt: string | null
  updated: string
}

/** 创建/更新模型配置输入。
 *  apiKey 仅在创建或替换时提交，更新其他字段时不提交。 */
export interface SaveModelConfigInput {
  providerCode: string
  providerName: string
  displayName: string
  baseUrl: string
  /** 仅创建或替换时提交，更新其他字段时不提交 */
  apiKey?: string
  modelType: 'CHAT' | 'EMBEDDING'
  modelName: string
  dimensions?: number | null
  contextWindowLength?: number | null
  timeoutSeconds?: number
  temperature?: number | null
  status?: 'ACTIVE' | 'DISABLED'
}

/** 模型配置测试结果。 */
export interface ModelTestResult {
  status: string
  errorCode: string | null
  dimensions: number | null
}

/** 查询厂商模板列表。 */
export function listModelProviderTemplates(): Promise<ModelProviderTemplateInfo[]> {
  if (isDemoMode()) {
    return Promise.resolve(mockListModelProviderTemplates())
  }
  return request('/model-provider-templates')
}

/** 查询模型配置列表（合并后单列表）。 */
export function listModelConfigs(): Promise<ModelConfig[]> {
  if (isDemoMode()) {
    return Promise.resolve(mockListModelConfigs())
  }
  return request('/model-configs')
}

/** 创建模型配置（合并后单步创建）。 */
export function createModelConfig(input: SaveModelConfigInput): Promise<ModelConfig> {
  if (isDemoMode()) {
    return Promise.resolve(mockCreateModelConfig(input))
  }
  return request('/model-configs', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

/** 更新模型配置（不提交 apiKey 时保留原密钥）。 */
export function updateModelConfig(modelConfigKey: string, input: SaveModelConfigInput): Promise<ModelConfig | undefined> {
  if (isDemoMode()) {
    return Promise.resolve(mockUpdateModelConfig(modelConfigKey, input))
  }
  return request(`/model-configs/${encodeURIComponent(modelConfigKey)}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

/** 替换 API Key（独立入口，不更新其他字段）。 */
export function replaceModelApiKey(modelConfigKey: string, apiKey: string): Promise<{ apiKeyMask: string } | undefined> {
  if (isDemoMode()) {
    return Promise.resolve(mockReplaceModelApiKey(modelConfigKey, apiKey))
  }
  return request(`/model-configs/${encodeURIComponent(modelConfigKey)}/api-key`, {
    method: 'PATCH',
    body: JSON.stringify({ apiKey }),
  })
}

/** 测试模型配置连通性。 */
export function testModelConfig(modelConfigKey: string): Promise<ModelTestResult> {
  if (isDemoMode()) {
    return Promise.resolve(mockTestModelConfig(modelConfigKey))
  }
  return request(`/model-configs/${encodeURIComponent(modelConfigKey)}/test`, {
    method: 'POST',
  })
}

/** 删除模型配置。 */
export function deleteModelConfig(modelConfigKey: string): Promise<boolean> {
  if (isDemoMode()) {
    return Promise.resolve(mockDeleteModelConfig(modelConfigKey))
  }
  return request(`/model-configs/${encodeURIComponent(modelConfigKey)}`, {
    method: 'DELETE',
  })
}
