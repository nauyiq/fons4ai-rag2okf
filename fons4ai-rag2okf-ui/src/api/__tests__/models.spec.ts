import { describe, it, expect, beforeEach, vi } from 'vitest'

/**
 * 模型配置 API 层测试（T006）。
 *
 * 验证点：
 * - 合并后 ModelConfig 契约：modelConfigKey/contextWindowLength/apiKeyConfigured 字段存在
 * - demo 模式下走 mock 数据，不发起网络请求
 * - real 模式下走 http.request，路径和方法匹配技术设计说明书 §3.1
 * - API Key 只在创建或替换时提交，更新其他字段不提交
 * - 旧的两步式 API（createModelConnection/createModelProfile 等）不再导出
 */

// mock http.request，real 模式测试验证调用参数，demo 模式不会触发
vi.mock('../http', () => ({
  request: vi.fn(),
}))

import { request } from '../http'

const mockedRequest = vi.mocked(request)

describe('models API - 合并后契约导出', () => {
  beforeEach(() => {
    vi.resetModules()
    vi.stubEnv('VITE_RAG2OKF_DATA_SOURCE', 'demo')
  })

  it('导出 ModelConfig 相关类型和单步 CRUD 函数', async () => {
    const api = await import('../models')
    expect(typeof api.listModelConfigs).toBe('function')
    expect(typeof api.createModelConfig).toBe('function')
    expect(typeof api.updateModelConfig).toBe('function')
    expect(typeof api.deleteModelConfig).toBe('function')
    expect(typeof api.testModelConfig).toBe('function')
    expect(typeof api.replaceModelApiKey).toBe('function')
    expect(typeof api.listModelProviderTemplates).toBe('function')
  })

  it('不再导出旧的两步式 API', async () => {
    const api = await import('../models') as Record<string, unknown>
    expect(api.createModelConnection).toBeUndefined()
    expect(api.createModelProfile).toBeUndefined()
    expect(api.listModelConnections).toBeUndefined()
    expect(api.listModelProfiles).toBeUndefined()
    expect(api.updateModelConnection).toBeUndefined()
    expect(api.updateModelProfile).toBeUndefined()
    expect(api.testModelProfile).toBeUndefined()
  })
})

describe('models API - demo 模式走 mock 数据', () => {
  beforeEach(() => {
    vi.resetModules()
    vi.stubEnv('VITE_RAG2OKF_DATA_SOURCE', 'demo')
    mockedRequest.mockClear()
  })

  it('listModelProviderTemplates 返回厂商模板列表', async () => {
    const { listModelProviderTemplates } = await import('../models')
    const result = await listModelProviderTemplates()
    expect(result.length).toBeGreaterThan(0)
    expect(result[0]).toHaveProperty('code')
    expect(result[0]).toHaveProperty('providerName')
    expect(mockedRequest).not.toHaveBeenCalled()
  })

  it('listModelConfigs 返回 ModelConfig[] 且字段对齐合并契约', async () => {
    const { listModelConfigs } = await import('../models')
    const result = await listModelConfigs()
    expect(result.length).toBeGreaterThan(0)
    const first = result[0]
    expect(first).toHaveProperty('modelConfigKey')
    expect(first).toHaveProperty('contextWindowLength')
    expect(first).toHaveProperty('apiKeyConfigured')
    expect(first).toHaveProperty('providerCode')
    expect(first).toHaveProperty('modelType')
    expect(first.status).toMatch(/^(ACTIVE|DISABLED)$/)
    expect(mockedRequest).not.toHaveBeenCalled()
  })

  it('createModelConfig 创建配置并返回掩码 apiKey', async () => {
    const { createModelConfig, listModelConfigs } = await import('../models')
    const before = await listModelConfigs()
    const created = await createModelConfig({
      providerCode: 'OPENAI',
      providerName: 'OpenAI',
      displayName: '测试模型',
      baseUrl: 'https://api.openai.com/v1',
      apiKey: 'sk-test-1234567890',
      modelType: 'CHAT',
      modelName: 'gpt-4o-mini',
    })
    expect(created.modelConfigKey).toBeDefined()
    expect(created.apiKeyMask).toContain('7890')
    expect(created.apiKeyConfigured).toBe(true)
    const after = await listModelConfigs()
    expect(after.length).toBe(before.length + 1)
    expect(mockedRequest).not.toHaveBeenCalled()
  })

  it('updateModelConfig 更新字段时不提交 apiKey 则保留原掩码', async () => {
    const { updateModelConfig, listModelConfigs } = await import('../models')
    const list = await listModelConfigs()
    const target = list[0]
    const originalMask = target.apiKeyMask
    const updated = await updateModelConfig(target.modelConfigKey, {
      providerCode: target.providerCode,
      providerName: target.providerName,
      displayName: '更新后的名称',
      baseUrl: target.baseUrl,
      modelType: target.modelType,
      modelName: target.modelName,
    })
    expect(updated?.displayName).toBe('更新后的名称')
    expect(updated?.apiKeyMask).toBe(originalMask)
    expect(mockedRequest).not.toHaveBeenCalled()
  })

  it('updateModelConfig 提交 apiKey 时更新掩码', async () => {
    const { updateModelConfig, listModelConfigs } = await import('../models')
    const list = await listModelConfigs()
    const target = list[0]
    const updated = await updateModelConfig(target.modelConfigKey, {
      providerCode: target.providerCode,
      providerName: target.providerName,
      displayName: target.displayName,
      baseUrl: target.baseUrl,
      apiKey: 'sk-newkey-abcdef',
      modelType: target.modelType,
      modelName: target.modelName,
    })
    // mock 取 apiKey 最后 4 位作为掩码后缀
    expect(updated?.apiKeyMask).toContain('cdef')
    expect(mockedRequest).not.toHaveBeenCalled()
  })

  it('replaceModelApiKey 替换密钥返回新掩码', async () => {
    const { replaceModelApiKey, listModelConfigs } = await import('../models')
    const list = await listModelConfigs()
    const target = list[0]
    const result = await replaceModelApiKey(target.modelConfigKey, 'sk-replaced-9999')
    expect(result?.apiKeyMask).toContain('9999')
    expect(mockedRequest).not.toHaveBeenCalled()
  })

  it('testModelConfig ACTIVE 配置返回 SUCCESS', async () => {
    const { testModelConfig, listModelConfigs } = await import('../models')
    const list = await listModelConfigs()
    const active = list.find((mc) => mc.status === 'ACTIVE' && mc.modelType === 'EMBEDDING')
    expect(active).toBeDefined()
    const result = await testModelConfig(active!.modelConfigKey)
    expect(result.status).toBe('SUCCESS')
    expect(mockedRequest).not.toHaveBeenCalled()
  })

  it('testModelConfig DISABLED 配置返回 FAILED', async () => {
    const { testModelConfig, listModelConfigs } = await import('../models')
    const list = await listModelConfigs()
    const disabled = list.find((mc) => mc.status === 'DISABLED')
    expect(disabled).toBeDefined()
    const result = await testModelConfig(disabled!.modelConfigKey)
    expect(result.status).toBe('FAILED')
    expect(mockedRequest).not.toHaveBeenCalled()
  })

  it('deleteModelConfig 删除配置', async () => {
    const { deleteModelConfig, listModelConfigs, createModelConfig } = await import('../models')
    const created = await createModelConfig({
      providerCode: 'CUSTOM',
      providerName: '自定义',
      displayName: '待删除',
      baseUrl: 'https://example.com',
      apiKey: 'sk-del-1234',
      modelType: 'CHAT',
      modelName: 'test-model',
    })
    const before = await listModelConfigs()
    const ok = await deleteModelConfig(created.modelConfigKey)
    expect(ok).toBe(true)
    const after = await listModelConfigs()
    expect(after.length).toBe(before.length - 1)
    expect(mockedRequest).not.toHaveBeenCalled()
  })
})

describe('models API - real 模式走 http.request', () => {
  beforeEach(() => {
    vi.resetModules()
    vi.stubEnv('VITE_RAG2OKF_DATA_SOURCE', 'real')
    mockedRequest.mockClear()
  })

  it('listModelConfigs 调用 GET /model-configs', async () => {
    mockedRequest.mockResolvedValueOnce([])
    const { listModelConfigs } = await import('../models')
    await listModelConfigs()
    expect(mockedRequest).toHaveBeenCalledWith('/model-configs')
  })

  it('listModelProviderTemplates 调用 GET /model-provider-templates', async () => {
    mockedRequest.mockResolvedValueOnce([])
    const { listModelProviderTemplates } = await import('../models')
    await listModelProviderTemplates()
    expect(mockedRequest).toHaveBeenCalledWith('/model-provider-templates')
  })

  it('createModelConfig 调用 POST /model-configs 且请求体含 apiKey', async () => {
    mockedRequest.mockResolvedValueOnce({} as never)
    const { createModelConfig } = await import('../models')
    await createModelConfig({
      providerCode: 'OPENAI',
      providerName: 'OpenAI',
      displayName: '测试',
      baseUrl: 'https://api.openai.com/v1',
      apiKey: 'sk-test',
      modelType: 'CHAT',
      modelName: 'gpt-4o',
    })
    expect(mockedRequest).toHaveBeenCalledTimes(1)
    const [path, init] = mockedRequest.mock.calls[0]
    expect(path).toBe('/model-configs')
    expect(init?.method).toBe('POST')
    const body = JSON.parse(init?.body as string)
    expect(body.apiKey).toBe('sk-test')
  })

  it('updateModelConfig 调用 PATCH /model-configs/{key} 且不提交 apiKey', async () => {
    mockedRequest.mockResolvedValueOnce({} as never)
    const { updateModelConfig } = await import('../models')
    await updateModelConfig('mc-001', {
      providerCode: 'OPENAI',
      providerName: 'OpenAI',
      displayName: '更新',
      baseUrl: 'https://api.openai.com/v1',
      modelType: 'CHAT',
      modelName: 'gpt-4o',
    })
    expect(mockedRequest).toHaveBeenCalledTimes(1)
    const [path, init] = mockedRequest.mock.calls[0]
    expect(path).toBe('/model-configs/mc-001')
    expect(init?.method).toBe('PATCH')
    const body = JSON.parse(init?.body as string)
    expect(body.apiKey).toBeUndefined()
  })

  it('replaceModelApiKey 调用 PATCH /model-configs/{key}/api-key', async () => {
    mockedRequest.mockResolvedValueOnce({ apiKeyMask: 'sk-****' } as never)
    const { replaceModelApiKey } = await import('../models')
    await replaceModelApiKey('mc-001', 'sk-new')
    expect(mockedRequest).toHaveBeenCalledTimes(1)
    const [path, init] = mockedRequest.mock.calls[0]
    expect(path).toBe('/model-configs/mc-001/api-key')
    expect(init?.method).toBe('PATCH')
    const body = JSON.parse(init?.body as string)
    expect(body.apiKey).toBe('sk-new')
  })

  it('testModelConfig 调用 POST /model-configs/{key}/test', async () => {
    mockedRequest.mockResolvedValueOnce({ status: 'SUCCESS' } as never)
    const { testModelConfig } = await import('../models')
    await testModelConfig('mc-001')
    expect(mockedRequest).toHaveBeenCalledTimes(1)
    const [path, init] = mockedRequest.mock.calls[0]
    expect(path).toBe('/model-configs/mc-001/test')
    expect(init?.method).toBe('POST')
  })

  it('deleteModelConfig 调用 DELETE /model-configs/{key}', async () => {
    mockedRequest.mockResolvedValueOnce(true as never)
    const { deleteModelConfig } = await import('../models')
    await deleteModelConfig('mc-001')
    expect(mockedRequest).toHaveBeenCalledTimes(1)
    const [path, init] = mockedRequest.mock.calls[0]
    expect(path).toBe('/model-configs/mc-001')
    expect(init?.method).toBe('DELETE')
  })
})
