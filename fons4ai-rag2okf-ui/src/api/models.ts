/**
 * 模型配置 API 层（CR-001 T025 还原两步式）。
 *
 * 设计依据：技术设计说明书 §3.1（CR-001 替换后）。
 * 恢复"模型提供商连接 + 模型档案"两步式 API，新增 /model-catalog 只读接口和 preferenceJson 读写接口。
 *
 * - 连接（Connection）：提供商凭证，仅 3 个用户可写字段（displayName/baseUrl/apiKey）
 * - 档案（Profile）：挂在连接下的具体模型，含 7 类型 + 级联选择 + 动态高级参数
 * - 目录（Catalog）：只读提供商模板 + 旗下模型清单，驱动右侧模型市场
 * - 偏好（Preference）：默认模型配置，存入 preferenceJson.defaultModels
 *
 * demo 模式下走本地 mock 数据，real 模式走 http.request。
 * API Key 只在创建或替换时提交，更新其他字段不提交。
 */
import { request } from './http'
import { isDemoMode } from '../composables/useDataSource'
import type { ModelType } from '../types/model'
import {
  mockListConnections,
  mockCreateConnection,
  mockUpdateConnection,
  mockDeleteConnection,
  mockReplaceConnectionApiKey,
  mockListProfiles,
  mockCreateProfile,
  mockUpdateProfile,
  mockDeleteProfile,
  mockTestProfile,
  mockGetModelCatalog,
  mockGetDefaultModels,
  mockSaveDefaultModels,
} from './mock/models'

// ============ 连接（Provider Connection）============

/** 模型提供商连接。 */
export interface ModelConnection {
  connectionKey: string
  /** 提供商编码（从 catalog 预填，如 OPENAI/DEEPSEEK/QWEN/CUSTOM） */
  providerCode: string
  /** 提供商名称 */
  providerName: string
  /** 用户可编辑的显示名称 */
  displayName: string
  /** Base URL */
  baseUrl: string
  /** API Key 只返回掩码，不回显原值 */
  apiKeyMask: string
  /** 是否已配置 API Key */
  apiKeyConfigured: boolean
  /** 连接状态 */
  status: 'ACTIVE' | 'DISABLED'
  updated: string
}

/** 创建连接输入（仅 3 个用户可写字段 + providerCode/providerName 从 catalog 预填）。 */
export interface SaveConnectionInput {
  providerCode: string
  providerName: string
  displayName: string
  baseUrl: string
  /** 仅创建或替换时提交 */
  apiKey?: string
}

// ============ 档案（Model Profile）============

/** 模型档案，挂在某个连接下的具体模型实例。 */
export interface ModelProfile {
  profileKey: string
  /** 所属连接 key */
  connectionKey: string
  /** 模型类型（7 类，不含 CHAT） */
  modelType: ModelType
  /** 模型名称（从 catalog 级联选择，CUSTOM 提供商可手填） */
  modelName: string
  /** 向量维度（仅 EMBEDDING） */
  dimensions: number | null
  /** 上下文窗口长度（LLM/VLM） */
  contextWindowLength: number | null
  /** 超时秒数 */
  timeoutSeconds: number
  /** 温度（LLM/VLM） */
  temperature: number | null
  /** 档案状态 */
  status: 'ACTIVE' | 'DISABLED'
  /** 最近测试状态 */
  lastTestStatus: string
  lastTestAt: string | null
  updated: string
}

/** 创建/更新档案输入。 */
export interface SaveProfileInput {
  connectionKey: string
  modelType: ModelType
  modelName: string
  dimensions?: number | null
  contextWindowLength?: number | null
  timeoutSeconds?: number
  temperature?: number | null
  status?: 'ACTIVE' | 'DISABLED'
}

/** 模型测试结果。 */
export interface ModelTestResult {
  status: string
  errorCode: string | null
  dimensions: number | null
}

// ============ 目录（Model Catalog）============

/** 目录中的单个模型信息。 */
export interface CatalogModel {
  modelName: string
  modelType: ModelType
}

/** 目录中的提供商卡片信息。 */
export interface CatalogProvider {
  providerCode: string
  providerName: string
  defaultBaseUrl: string
  /** 官方网站 URL */
  officialUrl: string
  /** 该提供商支持的模型列表 */
  models: CatalogModel[]
}

/** /model-catalog 响应。 */
export interface ModelCatalog {
  providers: CatalogProvider[]
  /** 各类型模型总数 */
  typeCounts: Record<string, number>
}

// ============ 默认模型偏好（Preference）============

/** 默认模型配置，存入 preferenceJson.defaultModels。 */
export interface DefaultModelSettings {
  /** 各类型的默认模型 profileKey */
  defaults: Partial<Record<ModelType, string | null>>
}

// ============ 连接 API ============

/** 查询当前用户的连接列表。 */
export function listConnections(): Promise<ModelConnection[]> {
  if (isDemoMode()) {
    return Promise.resolve(mockListConnections())
  }
  return request('/model-connections')
}

/** 创建连接。 */
export function createConnection(input: SaveConnectionInput): Promise<ModelConnection> {
  if (isDemoMode()) {
    return Promise.resolve(mockCreateConnection(input))
  }
  return request('/model-connections', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

/** 更新连接（不提交 apiKey 时保留原密钥）。 */
export function updateConnection(connectionKey: string, input: Partial<SaveConnectionInput>): Promise<ModelConnection | undefined> {
  if (isDemoMode()) {
    return Promise.resolve(mockUpdateConnection(connectionKey, input))
  }
  return request(`/model-connections/${encodeURIComponent(connectionKey)}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

/** 替换连接的 API Key（独立入口）。 */
export function replaceConnectionApiKey(connectionKey: string, apiKey: string): Promise<{ apiKeyMask: string } | undefined> {
  if (isDemoMode()) {
    return Promise.resolve(mockReplaceConnectionApiKey(connectionKey, apiKey))
  }
  return request(`/model-connections/${encodeURIComponent(connectionKey)}/api-key`, {
    method: 'PATCH',
    body: JSON.stringify({ apiKey }),
  })
}

/** 删除连接。 */
export function deleteConnection(connectionKey: string): Promise<boolean> {
  if (isDemoMode()) {
    return Promise.resolve(mockDeleteConnection(connectionKey))
  }
  return request(`/model-connections/${encodeURIComponent(connectionKey)}`, {
    method: 'DELETE',
  })
}

// ============ 档案 API ============

/** 查询档案列表（可按 connectionKey 过滤）。 */
export function listProfiles(connectionKey?: string): Promise<ModelProfile[]> {
  if (isDemoMode()) {
    return Promise.resolve(mockListProfiles(connectionKey))
  }
  let url = '/model-profiles'
  if (connectionKey) {
    url += `?connectionKey=${encodeURIComponent(connectionKey)}`
  }
  return request(url)
}

/** 创建档案。 */
export function createProfile(input: SaveProfileInput): Promise<ModelProfile> {
  if (isDemoMode()) {
    return Promise.resolve(mockCreateProfile(input))
  }
  return request('/model-profiles', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

/** 更新档案。 */
export function updateProfile(profileKey: string, input: Partial<SaveProfileInput>): Promise<ModelProfile | undefined> {
  if (isDemoMode()) {
    return Promise.resolve(mockUpdateProfile(profileKey, input))
  }
  return request(`/model-profiles/${encodeURIComponent(profileKey)}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

/** 删除档案。 */
export function deleteProfile(profileKey: string): Promise<boolean> {
  if (isDemoMode()) {
    return Promise.resolve(mockDeleteProfile(profileKey))
  }
  return request(`/model-profiles/${encodeURIComponent(profileKey)}`, {
    method: 'DELETE',
  })
}

/** 测试模型档案连通性。 */
export function testProfile(profileKey: string): Promise<ModelTestResult> {
  if (isDemoMode()) {
    return Promise.resolve(mockTestProfile(profileKey))
  }
  return request(`/model-profiles/${encodeURIComponent(profileKey)}/test`, {
    method: 'POST',
  })
}

// ============ 目录 API ============

/** 获取模型市场目录（只读）。 */
export function getModelCatalog(): Promise<ModelCatalog> {
  if (isDemoMode()) {
    return Promise.resolve(mockGetModelCatalog())
  }
  return request('/model-catalog')
}

// ============ 偏好 API ============

/** 获取当前用户的默认模型配置（从 GET /users/me 的 preferenceJson 解析）。 */
export function getDefaultModels(): Promise<DefaultModelSettings> {
  if (isDemoMode()) {
    return Promise.resolve(mockGetDefaultModels())
  }
  // real 模式从 /users/me 获取 preferenceJson（后端返回为 JSON 字符串）后前端解析
  return request('/users/me').then((user: unknown) => {
    const u = user as { preferenceJson?: string | null | { defaultModels?: DefaultModelSettings } }
    // 后端 preferenceJson 字段为 JSON 字符串，需先 parse 为对象；兼容 null/undefined/对象
    const rawPreference = (typeof u.preferenceJson === 'string' && u.preferenceJson
      ? JSON.parse(u.preferenceJson)
      : (u.preferenceJson ?? {})) as { defaultModels?: DefaultModelSettings }
    return rawPreference.defaultModels ?? { defaults: {} }
  })
}

/** 保存默认模型配置（通过 PATCH /users/me 局部合并 preferenceJson.defaultModels）。 */
export function saveDefaultModels(settings: DefaultModelSettings): Promise<void> {
  if (isDemoMode()) {
    return Promise.resolve(mockSaveDefaultModels(settings))
  }
  // 后端 preferenceJson 字段为 JSON 字符串：将 defaultModels 子对象序列化后发送
  // 后端 T027 实现局部合并：仅替换提交的顶层 key（defaultModels），保留其他偏好键
  return request('/users/me', {
    method: 'PATCH',
    body: JSON.stringify({
      preferenceJson: JSON.stringify({ defaultModels: settings }),
    }),
  }).then(() => undefined)
}
