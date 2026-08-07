/**
 * 模型配置演示数据。
 * 忠实模拟合并后 ModelConfig 结构（Provider 连接 + 模型档案合并为单一配置）。
 * 仅在 demo 模式下使用，完全在内存中，不写入 localStorage 真实 key。
 */

/** 模型配置合并后结构（对应 kb_model_config 表合并设计）。 */
export interface ModelConfig {
  configKey: string
  ownerUserId: number
  providerCode: string
  providerName: string
  displayName: string
  protocolType: string
  baseUrl: string
  apiKeyMask: string
  modelType: 'CHAT' | 'EMBEDDING'
  modelName: string
  dimensions: number | null
  timeoutSeconds: number
  temperature: number | null
  contextWindow: number | null
  status: string
  lastTestStatus: string
  lastTestAt: string | null
  created: string
  updated: string
}

/** 厂商模板信息（供新建模型配置时选择）。 */
export interface ModelProviderTemplate {
  code: string
  providerName: string
  defaultBaseUrl: string | null
}

const oneDayAgo = new Date(Date.now() - 86400_000).toISOString()
const oneWeekAgo = new Date(Date.now() - 604800_000).toISOString()

const providerTemplates: ModelProviderTemplate[] = [
  { code: 'OPENAI', providerName: 'OpenAI', defaultBaseUrl: 'https://api.openai.com/v1' },
  { code: 'DEEPSEEK', providerName: 'DeepSeek', defaultBaseUrl: 'https://api.deepseek.com/v1' },
  { code: 'QWEN', providerName: '通义千问', defaultBaseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1' },
  { code: 'CUSTOM', providerName: '自定义厂商', defaultBaseUrl: null },
]

const modelConfigs: ModelConfig[] = [
  {
    configKey: 'mc-demo-001',
    ownerUserId: 1,
    providerCode: 'DEEPSEEK',
    providerName: 'DeepSeek',
    displayName: 'DeepSeek 对话模型',
    protocolType: 'OPENAI_COMPATIBLE',
    baseUrl: 'https://api.deepseek.com/v1',
    apiKeyMask: 'sk-****...****3f2a',
    modelType: 'CHAT',
    modelName: 'deepseek-chat',
    dimensions: null,
    timeoutSeconds: 30,
    temperature: 0.7,
    contextWindow: 64000,
    status: 'ACTIVE',
    lastTestStatus: 'SUCCESS',
    lastTestAt: oneDayAgo,
    created: oneWeekAgo,
    updated: oneDayAgo,
  },
  {
    configKey: 'mc-demo-002',
    ownerUserId: 1,
    providerCode: 'QWEN',
    providerName: '通义千问',
    displayName: '通义千问向量化模型',
    protocolType: 'OPENAI_COMPATIBLE',
    baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
    apiKeyMask: 'sk-****...****8b1c',
    modelType: 'EMBEDDING',
    modelName: 'text-embedding-v3',
    dimensions: 1024,
    timeoutSeconds: 60,
    temperature: null,
    contextWindow: null,
    status: 'ACTIVE',
    lastTestStatus: 'SUCCESS',
    lastTestAt: oneDayAgo,
    created: oneWeekAgo,
    updated: oneDayAgo,
  },
  {
    configKey: 'mc-demo-003',
    ownerUserId: 1,
    providerCode: 'OPENAI',
    providerName: 'OpenAI',
    displayName: 'GPT-4o 对话模型',
    protocolType: 'OPENAI_COMPATIBLE',
    baseUrl: 'https://api.openai.com/v1',
    apiKeyMask: 'sk-****...****a9d0',
    modelType: 'CHAT',
    modelName: 'gpt-4o',
    dimensions: null,
    timeoutSeconds: 45,
    temperature: 0.5,
    contextWindow: 128000,
    status: 'INACTIVE',
    lastTestStatus: 'FAILED',
    lastTestAt: oneWeekAgo,
    created: oneWeekAgo,
    updated: oneWeekAgo,
  },
]

/** 模拟查询厂商模板列表。 */
export function mockListModelProviderTemplates(): ModelProviderTemplate[] {
  return [...providerTemplates]
}

/** 模拟查询模型配置列表（合并后单列表）。 */
export function mockListModelConfigs(): ModelConfig[] {
  return [...modelConfigs]
}

/** 模拟查询单个模型配置。 */
export function mockGetModelConfig(configKey: string): ModelConfig | undefined {
  return modelConfigs.find((mc) => mc.configKey === configKey)
}

/** 模拟创建模型配置（合并后单步创建）。 */
export function mockCreateModelConfig(input: Omit<ModelConfig, 'configKey' | 'created' | 'updated' | 'apiKeyMask'> & { apiKey?: string }): ModelConfig {
  const created: ModelConfig = {
    ...input,
    configKey: `mc-demo-${Date.now()}`,
    apiKeyMask: input.apiKey ? `sk-****...****${input.apiKey.slice(-4)}` : 'sk-****',
    created: new Date().toISOString(),
    updated: new Date().toISOString(),
  }
  modelConfigs.push(created)
  return created
}

/** 模拟更新模型配置。 */
export function mockUpdateModelConfig(configKey: string, input: Partial<ModelConfig> & { apiKey?: string }): ModelConfig | undefined {
  const idx = modelConfigs.findIndex((mc) => mc.configKey === configKey)
  if (idx === -1) return undefined
  const { apiKey, ...rest } = input
  modelConfigs[idx] = {
    ...modelConfigs[idx],
    ...rest,
    apiKeyMask: apiKey ? `sk-****...****${apiKey.slice(-4)}` : modelConfigs[idx].apiKeyMask,
    updated: new Date().toISOString(),
  }
  return modelConfigs[idx]
}

/** 模拟删除模型配置。 */
export function mockDeleteModelConfig(configKey: string): boolean {
  const idx = modelConfigs.findIndex((mc) => mc.configKey === configKey)
  if (idx === -1) return false
  modelConfigs.splice(idx, 1)
  return true
}

/** 模拟测试模型配置连通性。 */
export function mockTestModelConfig(configKey: string): { status: string; errorCode: string | null; dimensions: number | null } {
  const config = modelConfigs.find((mc) => mc.configKey === configKey)
  if (!config) {
    return { status: 'FAILED', errorCode: 'CONFIG_NOT_FOUND', dimensions: null }
  }
  // 模拟测试成功（演示模式下始终成功，除非配置为 INACTIVE）
  if (config.status === 'INACTIVE') {
    return { status: 'FAILED', errorCode: 'CONFIG_INACTIVE', dimensions: null }
  }
  return {
    status: 'SUCCESS',
    errorCode: null,
    dimensions: config.modelType === 'EMBEDDING' ? config.dimensions : null,
  }
}

/** 获取全部 mock 模型配置（供调试引用）。 */
export function mockAllModelConfigs(): ModelConfig[] {
  return modelConfigs
}
