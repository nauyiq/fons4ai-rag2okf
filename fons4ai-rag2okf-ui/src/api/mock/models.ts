/**
 * 模型配置演示数据（CR-001 T025 还原两步式）。
 *
 * 忠实模拟两步式结构：
 * - 连接（Connection）：提供商凭证
 * - 档案（Profile）：挂在连接下的具体模型
 * - 目录（Catalog）：只读提供商模板 + 旗下模型清单
 * - 偏好（Preference）：默认模型配置
 *
 * 仅在 demo 模式下使用，完全在内存中，不写入 localStorage 真实 key。
 */
import type {
  ModelConnection,
  ModelProfile,
  ModelCatalog,
  CatalogProvider,
  SaveConnectionInput,
  SaveProfileInput,
  DefaultModelSettings,
  ModelTestResult,
} from '../models'
import type { ModelType } from '../../types/model'

const oneDayAgo = new Date(Date.now() - 86400_000).toISOString()
const oneWeekAgo = new Date(Date.now() - 604800_000).toISOString()

// ============ 连接 mock 数据 ============

const connections: ModelConnection[] = [
  {
    connectionKey: 'conn-demo-001',
    providerCode: 'DEEPSEEK',
    providerName: 'DeepSeek',
    displayName: 'DeepSeek 连接',
    baseUrl: 'https://api.deepseek.com/v1',
    apiKeyMask: 'sk-****...****3f2a',
    apiKeyConfigured: true,
    status: 'ACTIVE',
    updated: oneDayAgo,
  },
  {
    connectionKey: 'conn-demo-002',
    providerCode: 'QWEN',
    providerName: '通义千问',
    displayName: '通义千问连接',
    baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
    apiKeyMask: 'sk-****...****8b1c',
    apiKeyConfigured: true,
    status: 'ACTIVE',
    updated: oneDayAgo,
  },
  {
    connectionKey: 'conn-demo-003',
    providerCode: 'OPENAI',
    providerName: 'OpenAI',
    displayName: 'OpenAI 连接',
    baseUrl: 'https://api.openai.com/v1',
    apiKeyMask: 'sk-****...****a9d0',
    apiKeyConfigured: true,
    status: 'DISABLED',
    updated: oneWeekAgo,
  },
]

// ============ 档案 mock 数据 ============

const profiles: ModelProfile[] = [
  {
    profileKey: 'prof-demo-001',
    connectionKey: 'conn-demo-001',
    modelType: 'LLM',
    modelName: 'deepseek-chat',
    dimensions: null,
    contextWindowLength: 64000,
    timeoutSeconds: 30,
    temperature: 0.7,
    status: 'ACTIVE',
    lastTestStatus: 'SUCCESS',
    lastTestAt: oneDayAgo,
    updated: oneDayAgo,
  },
  {
    profileKey: 'prof-demo-002',
    connectionKey: 'conn-demo-001',
    modelType: 'EMBEDDING',
    modelName: 'deepseek-embedding',
    dimensions: 1024,
    contextWindowLength: null,
    timeoutSeconds: 60,
    temperature: null,
    status: 'ACTIVE',
    lastTestStatus: 'SUCCESS',
    lastTestAt: oneDayAgo,
    updated: oneDayAgo,
  },
  {
    profileKey: 'prof-demo-003',
    connectionKey: 'conn-demo-002',
    modelType: 'EMBEDDING',
    modelName: 'text-embedding-v3',
    dimensions: 1024,
    contextWindowLength: null,
    timeoutSeconds: 60,
    temperature: null,
    status: 'ACTIVE',
    lastTestStatus: 'SUCCESS',
    lastTestAt: oneDayAgo,
    updated: oneDayAgo,
  },
  {
    profileKey: 'prof-demo-004',
    connectionKey: 'conn-demo-003',
    modelType: 'LLM',
    modelName: 'gpt-4o',
    dimensions: null,
    contextWindowLength: 128000,
    timeoutSeconds: 45,
    temperature: 0.5,
    status: 'DISABLED',
    lastTestStatus: 'FAILED',
    lastTestAt: oneWeekAgo,
    updated: oneWeekAgo,
  },
]

// ============ 目录 mock 数据 ============

const catalog: ModelCatalog = {
  providers: [
    {
      providerCode: 'DEEPSEEK',
      providerName: 'DeepSeek',
      defaultBaseUrl: 'https://api.deepseek.com/v1',
      officialUrl: 'https://www.deepseek.com',
      models: [
        { modelName: 'deepseek-chat', modelType: 'LLM' as ModelType },
        { modelName: 'deepseek-coder', modelType: 'LLM' as ModelType },
        { modelName: 'deepseek-embedding', modelType: 'EMBEDDING' as ModelType },
      ],
    },
    {
      providerCode: 'QWEN',
      providerName: '通义千问',
      defaultBaseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
      officialUrl: 'https://help.aliyun.com/zh/dashscope',
      models: [
        { modelName: 'qwen-plus', modelType: 'LLM' as ModelType },
        { modelName: 'qwen-turbo', modelType: 'LLM' as ModelType },
        { modelName: 'text-embedding-v3', modelType: 'EMBEDDING' as ModelType },
        { modelName: 'gte-rerank', modelType: 'RERANK' as ModelType },
      ],
    },
    {
      providerCode: 'OPENAI',
      providerName: 'OpenAI',
      defaultBaseUrl: 'https://api.openai.com/v1',
      officialUrl: 'https://platform.openai.com',
      models: [
        { modelName: 'gpt-4o', modelType: 'LLM' as ModelType },
        { modelName: 'gpt-4o-mini', modelType: 'LLM' as ModelType },
        { modelName: 'text-embedding-3-small', modelType: 'EMBEDDING' as ModelType },
        { modelName: 'text-embedding-3-large', modelType: 'EMBEDDING' as ModelType },
        { modelName: 'whisper-1', modelType: 'ASR' as ModelType },
        { modelName: 'tts-1', modelType: 'TTS' as ModelType },
        { modelName: 'gpt-4o-vision', modelType: 'VLM' as ModelType },
      ],
    },
    {
      providerCode: 'CUSTOM',
      providerName: '自定义提供商',
      defaultBaseUrl: '',
      officialUrl: '',
      models: [],
    },
  ],
  typeCounts: {
    LLM: 5,
    EMBEDDING: 4,
    RERANK: 1,
    TTS: 1,
    ASR: 1,
    VLM: 1,
    OCR: 0,
  },
}

// ============ 偏好 mock 数据 ============

let defaultModels: DefaultModelSettings = {
  defaults: {
    LLM: 'prof-demo-001',
    EMBEDDING: 'prof-demo-002',
  },
}

// ============ 连接 mock 函数 ============

export function mockListConnections(): ModelConnection[] {
  return [...connections]
}

export function mockCreateConnection(input: SaveConnectionInput): ModelConnection {
  const created: ModelConnection = {
    connectionKey: `conn-demo-${Date.now()}`,
    providerCode: input.providerCode,
    providerName: input.providerName,
    displayName: input.displayName,
    baseUrl: input.baseUrl,
    apiKeyMask: input.apiKey ? `sk-****...****${input.apiKey.slice(-4)}` : 'sk-****',
    apiKeyConfigured: Boolean(input.apiKey),
    status: 'ACTIVE',
    updated: new Date().toISOString(),
  }
  connections.push(created)
  return created
}

export function mockUpdateConnection(connectionKey: string, input: Partial<SaveConnectionInput>): ModelConnection | undefined {
  const idx = connections.findIndex((c) => c.connectionKey === connectionKey)
  if (idx === -1) return undefined
  const existing = connections[idx]
  connections[idx] = {
    ...existing,
    ...input,
    apiKeyMask: input.apiKey ? `sk-****...****${input.apiKey.slice(-4)}` : existing.apiKeyMask,
    apiKeyConfigured: input.apiKey ? true : existing.apiKeyConfigured,
    updated: new Date().toISOString(),
  }
  return connections[idx]
}

export function mockReplaceConnectionApiKey(connectionKey: string, apiKey: string): { apiKeyMask: string } | undefined {
  const idx = connections.findIndex((c) => c.connectionKey === connectionKey)
  if (idx === -1) return undefined
  const mask = `sk-****...****${apiKey.slice(-4)}`
  connections[idx] = {
    ...connections[idx],
    apiKeyMask: mask,
    apiKeyConfigured: true,
    updated: new Date().toISOString(),
  }
  return { apiKeyMask: mask }
}

export function mockDeleteConnection(connectionKey: string): boolean {
  const idx = connections.findIndex((c) => c.connectionKey === connectionKey)
  if (idx === -1) return false
  connections.splice(idx, 1)
  // 同时删除关联的档案
  for (let i = profiles.length - 1; i >= 0; i--) {
    if (profiles[i].connectionKey === connectionKey) {
      profiles.splice(i, 1)
    }
  }
  return true
}

// ============ 档案 mock 函数 ============

export function mockListProfiles(connectionKey?: string): ModelProfile[] {
  if (connectionKey) {
    return profiles.filter((p) => p.connectionKey === connectionKey)
  }
  return [...profiles]
}

export function mockCreateProfile(input: SaveProfileInput): ModelProfile {
  const created: ModelProfile = {
    profileKey: `prof-demo-${Date.now()}`,
    connectionKey: input.connectionKey,
    modelType: input.modelType,
    modelName: input.modelName,
    dimensions: input.dimensions ?? null,
    contextWindowLength: input.contextWindowLength ?? null,
    timeoutSeconds: input.timeoutSeconds ?? 60,
    temperature: input.temperature ?? null,
    status: input.status ?? 'ACTIVE',
    lastTestStatus: 'UNTESTED',
    lastTestAt: null,
    updated: new Date().toISOString(),
  }
  profiles.push(created)
  return created
}

export function mockUpdateProfile(profileKey: string, input: Partial<SaveProfileInput>): ModelProfile | undefined {
  const idx = profiles.findIndex((p) => p.profileKey === profileKey)
  if (idx === -1) return undefined
  const existing = profiles[idx]
  profiles[idx] = {
    ...existing,
    ...input,
    updated: new Date().toISOString(),
  }
  return profiles[idx]
}

export function mockDeleteProfile(profileKey: string): boolean {
  const idx = profiles.findIndex((p) => p.profileKey === profileKey)
  if (idx === -1) return false
  profiles.splice(idx, 1)
  return true
}

export function mockTestProfile(profileKey: string): ModelTestResult {
  const profile = profiles.find((p) => p.profileKey === profileKey)
  if (!profile) {
    return { status: 'FAILED', errorCode: 'PROFILE_NOT_FOUND', dimensions: null }
  }
  if (profile.status === 'DISABLED') {
    return { status: 'FAILED', errorCode: 'PROFILE_DISABLED', dimensions: null }
  }
  return {
    status: 'SUCCESS',
    errorCode: null,
    dimensions: profile.modelType === 'EMBEDDING' ? profile.dimensions : null,
  }
}

// ============ 目录 mock 函数 ============

export function mockGetModelCatalog(): ModelCatalog {
  return JSON.parse(JSON.stringify(catalog))
}

// ============ 偏好 mock 函数 ============

export function mockGetDefaultModels(): DefaultModelSettings {
  return JSON.parse(JSON.stringify(defaultModels))
}

export function mockSaveDefaultModels(settings: DefaultModelSettings): void {
  defaultModels = JSON.parse(JSON.stringify(settings))
}

// ============ 调试辅助 ============

/** 获取全部 mock 连接（供调试引用）。 */
export function mockAllConnections(): ModelConnection[] {
  return connections
}

/** 获取全部 mock 档案（供调试引用）。 */
export function mockAllProfiles(): ModelProfile[] {
  return profiles
}

/** 获取 catalog 提供商列表（供调试引用）。 */
export function mockCatalogProviders(): CatalogProvider[] {
  return catalog.providers
}
