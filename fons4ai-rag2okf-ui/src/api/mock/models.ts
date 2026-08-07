/**
 * 模型配置演示数据。
 * 忠实模拟合并后 ModelConfig 结构（Provider 连接 + 模型档案合并为单一配置）。
 * 字段严格对齐技术设计说明书 §3.1 的 ModelConfig 契约。
 * 仅在 demo 模式下使用，完全在内存中，不写入 localStorage 真实 key。
 */
import type { ModelConfig, SaveModelConfigInput, ModelProviderTemplateInfo } from '../models'

/** 创建/更新模型配置时可选提交的输入（apiKey 仅创建或替换时提交）。 */
type MockSaveInput = SaveModelConfigInput

const oneDayAgo = new Date(Date.now() - 86400_000).toISOString()
const oneWeekAgo = new Date(Date.now() - 604800_000).toISOString()

const providerTemplates: ModelProviderTemplateInfo[] = [
  { code: 'OPENAI', providerName: 'OpenAI', defaultBaseUrl: 'https://api.openai.com/v1' },
  { code: 'DEEPSEEK', providerName: 'DeepSeek', defaultBaseUrl: 'https://api.deepseek.com/v1' },
  { code: 'QWEN', providerName: '通义千问', defaultBaseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1' },
  { code: 'CUSTOM', providerName: '自定义厂商', defaultBaseUrl: null },
]

const modelConfigs: ModelConfig[] = [
  {
    modelConfigKey: 'mc-demo-001',
    providerCode: 'DEEPSEEK',
    providerName: 'DeepSeek',
    displayName: 'DeepSeek 对话模型',
    baseUrl: 'https://api.deepseek.com/v1',
    apiKeyMask: 'sk-****...****3f2a',
    apiKeyConfigured: true,
    modelType: 'CHAT',
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
    modelConfigKey: 'mc-demo-002',
    providerCode: 'QWEN',
    providerName: '通义千问',
    displayName: '通义千问向量化模型',
    baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
    apiKeyMask: 'sk-****...****8b1c',
    apiKeyConfigured: true,
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
    modelConfigKey: 'mc-demo-003',
    providerCode: 'OPENAI',
    providerName: 'OpenAI',
    displayName: 'GPT-4o 对话模型',
    baseUrl: 'https://api.openai.com/v1',
    apiKeyMask: 'sk-****...****a9d0',
    apiKeyConfigured: true,
    modelType: 'CHAT',
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

/** 模拟查询厂商模板列表。 */
export function mockListModelProviderTemplates(): ModelProviderTemplateInfo[] {
  return [...providerTemplates]
}

/** 模拟查询模型配置列表（合并后单列表）。 */
export function mockListModelConfigs(): ModelConfig[] {
  return [...modelConfigs]
}

/** 模拟查询单个模型配置。 */
export function mockGetModelConfig(modelConfigKey: string): ModelConfig | undefined {
  return modelConfigs.find((mc) => mc.modelConfigKey === modelConfigKey)
}

/** 模拟创建模型配置（合并后单步创建）。 */
export function mockCreateModelConfig(input: MockSaveInput): ModelConfig {
  const created: ModelConfig = {
    modelConfigKey: `mc-demo-${Date.now()}`,
    providerCode: input.providerCode,
    providerName: input.providerName,
    displayName: input.displayName,
    baseUrl: input.baseUrl,
    apiKeyMask: input.apiKey ? `sk-****...****${input.apiKey.slice(-4)}` : 'sk-****',
    apiKeyConfigured: Boolean(input.apiKey),
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
  modelConfigs.push(created)
  return created
}

/** 模拟更新模型配置（不提交 apiKey 时保留原掩码）。 */
export function mockUpdateModelConfig(modelConfigKey: string, input: MockSaveInput): ModelConfig | undefined {
  const idx = modelConfigs.findIndex((mc) => mc.modelConfigKey === modelConfigKey)
  if (idx === -1) return undefined
  const existing = modelConfigs[idx]
  modelConfigs[idx] = {
    ...existing,
    providerCode: input.providerCode,
    providerName: input.providerName,
    displayName: input.displayName,
    baseUrl: input.baseUrl,
    apiKeyMask: input.apiKey ? `sk-****...****${input.apiKey.slice(-4)}` : existing.apiKeyMask,
    apiKeyConfigured: input.apiKey ? true : existing.apiKeyConfigured,
    modelType: input.modelType,
    modelName: input.modelName,
    dimensions: input.dimensions ?? null,
    contextWindowLength: input.contextWindowLength ?? null,
    timeoutSeconds: input.timeoutSeconds ?? existing.timeoutSeconds,
    temperature: input.temperature ?? null,
    status: input.status ?? existing.status,
    updated: new Date().toISOString(),
  }
  return modelConfigs[idx]
}

/** 模拟替换 API Key。 */
export function mockReplaceModelApiKey(modelConfigKey: string, apiKey: string): { apiKeyMask: string } | undefined {
  const idx = modelConfigs.findIndex((mc) => mc.modelConfigKey === modelConfigKey)
  if (idx === -1) return undefined
  const mask = `sk-****...****${apiKey.slice(-4)}`
  modelConfigs[idx] = {
    ...modelConfigs[idx],
    apiKeyMask: mask,
    apiKeyConfigured: true,
    updated: new Date().toISOString(),
  }
  return { apiKeyMask: mask }
}

/** 模拟删除模型配置。 */
export function mockDeleteModelConfig(modelConfigKey: string): boolean {
  const idx = modelConfigs.findIndex((mc) => mc.modelConfigKey === modelConfigKey)
  if (idx === -1) return false
  modelConfigs.splice(idx, 1)
  return true
}

/** 模拟测试模型配置连通性。 */
export function mockTestModelConfig(modelConfigKey: string): { status: string; errorCode: string | null; dimensions: number | null } {
  const config = modelConfigs.find((mc) => mc.modelConfigKey === modelConfigKey)
  if (!config) {
    return { status: 'FAILED', errorCode: 'CONFIG_NOT_FOUND', dimensions: null }
  }
  // 演示模式下 DISABLED 配置测试失败，其余成功
  if (config.status === 'DISABLED') {
    return { status: 'FAILED', errorCode: 'CONFIG_DISABLED', dimensions: null }
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
