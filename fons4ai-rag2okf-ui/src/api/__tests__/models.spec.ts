import { describe, it, expect, beforeEach, vi } from 'vitest'

/**
 * 模型配置 API 层测试（CR-001 T025 还原两步式）。
 *
 * 验证点：
 * - 两步式 API：连接（Connection）+ 档案（Profile）+ 目录（Catalog）+ 偏好（Preference）
 * - 不再导出合并后的 ModelConfig 单步 CRUD 函数
 * - demo 模式下走 mock 数据，real 模式走 http.request
 * - API Key 只在创建或替换时提交
 */

vi.mock('../http', () => ({
  request: vi.fn(),
}))

import { request } from '../http'

const mockedRequest = vi.mocked(request)

describe('models API - 两步式契约导出', () => {
  beforeEach(() => {
    vi.resetModules()
    vi.stubEnv('VITE_RAG2OKF_DATA_SOURCE', 'demo')
  })

  it('导出两步式 API 函数', async () => {
    const api = await import('../models')
    // 连接
    expect(typeof api.listConnections).toBe('function')
    expect(typeof api.createConnection).toBe('function')
    expect(typeof api.updateConnection).toBe('function')
    expect(typeof api.deleteConnection).toBe('function')
    expect(typeof api.replaceConnectionApiKey).toBe('function')
    // 档案
    expect(typeof api.listProfiles).toBe('function')
    expect(typeof api.createProfile).toBe('function')
    expect(typeof api.updateProfile).toBe('function')
    expect(typeof api.deleteProfile).toBe('function')
    expect(typeof api.testProfile).toBe('function')
    // 目录
    expect(typeof api.getModelCatalog).toBe('function')
    // 偏好
    expect(typeof api.getDefaultModels).toBe('function')
    expect(typeof api.saveDefaultModels).toBe('function')
  })

  it('不再导出合并后的 ModelConfig 单步 CRUD', async () => {
    const api = await import('../models') as Record<string, unknown>
    expect(api.listModelConfigs).toBeUndefined()
    expect(api.createModelConfig).toBeUndefined()
    expect(api.updateModelConfig).toBeUndefined()
    expect(api.deleteModelConfig).toBeUndefined()
    expect(api.testModelConfig).toBeUndefined()
    expect(api.replaceModelApiKey).toBeUndefined()
    expect(api.listModelProviderTemplates).toBeUndefined()
  })
})

describe('models API - demo 模式连接 CRUD', () => {
  beforeEach(() => {
    vi.resetModules()
    vi.stubEnv('VITE_RAG2OKF_DATA_SOURCE', 'demo')
    mockedRequest.mockClear()
  })

  it('listConnections 返回连接列表', async () => {
    const { listConnections } = await import('../models')
    const result = await listConnections()
    expect(result.length).toBeGreaterThan(0)
    const first = result[0]
    expect(first).toHaveProperty('connectionKey')
    expect(first).toHaveProperty('apiKeyConfigured')
    expect(first).toHaveProperty('providerCode')
    expect(first.status).toMatch(/^(ACTIVE|DISABLED)$/)
    expect(mockedRequest).not.toHaveBeenCalled()
  })

  it('createConnection 创建连接并返回掩码 apiKey', async () => {
    const { createConnection, listConnections } = await import('../models')
    const before = await listConnections()
    const created = await createConnection({
      providerCode: 'OPENAI',
      providerName: 'OpenAI',
      displayName: '测试连接',
      baseUrl: 'https://api.openai.com/v1',
      apiKey: 'sk-test-1234567890',
    })
    expect(created.connectionKey).toBeDefined()
    expect(created.apiKeyMask).toContain('7890')
    expect(created.apiKeyConfigured).toBe(true)
    const after = await listConnections()
    expect(after.length).toBe(before.length + 1)
    expect(mockedRequest).not.toHaveBeenCalled()
  })

  it('updateConnection 更新字段时不提交 apiKey 则保留原掩码', async () => {
    const { updateConnection, listConnections } = await import('../models')
    const list = await listConnections()
    const target = list[0]
    const originalMask = target.apiKeyMask
    const updated = await updateConnection(target.connectionKey, {
      displayName: '更新后的名称',
    })
    expect(updated?.displayName).toBe('更新后的名称')
    expect(updated?.apiKeyMask).toBe(originalMask)
    expect(mockedRequest).not.toHaveBeenCalled()
  })

  it('replaceConnectionApiKey 替换密钥返回新掩码', async () => {
    const { replaceConnectionApiKey, listConnections } = await import('../models')
    const list = await listConnections()
    const target = list[0]
    const result = await replaceConnectionApiKey(target.connectionKey, 'sk-replaced-9999')
    expect(result?.apiKeyMask).toContain('9999')
    expect(mockedRequest).not.toHaveBeenCalled()
  })

  it('deleteConnection 删除连接', async () => {
    const { deleteConnection, createConnection, listConnections } = await import('../models')
    const created = await createConnection({
      providerCode: 'CUSTOM',
      providerName: '自定义',
      displayName: '待删除',
      baseUrl: 'https://example.com',
      apiKey: 'sk-del-1234',
    })
    const before = await listConnections()
    const ok = await deleteConnection(created.connectionKey)
    expect(ok).toBe(true)
    const after = await listConnections()
    expect(after.length).toBe(before.length - 1)
    expect(mockedRequest).not.toHaveBeenCalled()
  })
})

describe('models API - demo 模式档案 CRUD', () => {
  beforeEach(() => {
    vi.resetModules()
    vi.stubEnv('VITE_RAG2OKF_DATA_SOURCE', 'demo')
    mockedRequest.mockClear()
  })

  it('listProfiles 返回档案列表', async () => {
    const { listProfiles } = await import('../models')
    const result = await listProfiles()
    expect(result.length).toBeGreaterThan(0)
    const first = result[0]
    expect(first).toHaveProperty('profileKey')
    expect(first).toHaveProperty('connectionKey')
    expect(first).toHaveProperty('modelType')
    expect(first).toHaveProperty('contextWindowLength')
    expect(mockedRequest).not.toHaveBeenCalled()
  })

  it('listProfiles 按 connectionKey 过滤', async () => {
    const { listProfiles, listConnections } = await import('../models')
    const conns = await listConnections()
    const firstConn = conns[0]
    const filtered = await listProfiles(firstConn.connectionKey)
    expect(filtered.every((p) => p.connectionKey === firstConn.connectionKey)).toBe(true)
    expect(mockedRequest).not.toHaveBeenCalled()
  })

  it('createProfile 创建档案', async () => {
    const { createProfile, listConnections } = await import('../models')
    const conns = await listConnections()
    const conn = conns[0]
    const created = await createProfile({
      connectionKey: conn.connectionKey,
      modelType: 'RERANK',
      modelName: 'test-rerank',
    })
    expect(created.profileKey).toBeDefined()
    expect(created.modelType).toBe('RERANK')
    expect(mockedRequest).not.toHaveBeenCalled()
  })

  it('testProfile ACTIVE 档案返回 SUCCESS', async () => {
    const { testProfile, listProfiles } = await import('../models')
    const list = await listProfiles()
    const active = list.find((p) => p.status === 'ACTIVE' && p.modelType === 'EMBEDDING')
    expect(active).toBeDefined()
    const result = await testProfile(active!.profileKey)
    expect(result.status).toBe('SUCCESS')
    expect(mockedRequest).not.toHaveBeenCalled()
  })

  it('testProfile DISABLED 档案返回 FAILED', async () => {
    const { testProfile, listProfiles } = await import('../models')
    const list = await listProfiles()
    const disabled = list.find((p) => p.status === 'DISABLED')
    expect(disabled).toBeDefined()
    const result = await testProfile(disabled!.profileKey)
    expect(result.status).toBe('FAILED')
    expect(mockedRequest).not.toHaveBeenCalled()
  })

  it('deleteProfile 删除档案', async () => {
    const { deleteProfile, createProfile, listConnections } = await import('../models')
    const conns = await listConnections()
    const created = await createProfile({
      connectionKey: conns[0].connectionKey,
      modelType: 'TTS',
      modelName: 'test-tts',
    })
    const ok = await deleteProfile(created.profileKey)
    expect(ok).toBe(true)
    expect(mockedRequest).not.toHaveBeenCalled()
  })
})

describe('models API - demo 模式目录与偏好', () => {
  beforeEach(() => {
    vi.resetModules()
    vi.stubEnv('VITE_RAG2OKF_DATA_SOURCE', 'demo')
    mockedRequest.mockClear()
  })

  it('getModelCatalog 返回提供商列表和类型计数', async () => {
    const { getModelCatalog } = await import('../models')
    const catalog = await getModelCatalog()
    expect(catalog.providers.length).toBeGreaterThan(0)
    expect(catalog.providers[0]).toHaveProperty('providerCode')
    expect(catalog.providers[0]).toHaveProperty('models')
    expect(catalog.typeCounts).toHaveProperty('LLM')
    expect(catalog.typeCounts).toHaveProperty('EMBEDDING')
    expect(mockedRequest).not.toHaveBeenCalled()
  })

  it('getDefaultModels 返回默认模型配置', async () => {
    const { getDefaultModels } = await import('../models')
    const prefs = await getDefaultModels()
    expect(prefs).toHaveProperty('defaults')
    expect(Object.keys(prefs.defaults).length).toBeGreaterThan(0)
    expect(mockedRequest).not.toHaveBeenCalled()
  })

  it('saveDefaultModels 保存后读取一致', async () => {
    const { saveDefaultModels, getDefaultModels } = await import('../models')
    const newPrefs = { defaults: { LLM: 'prof-demo-001', RERANK: 'prof-demo-003' } }
    await saveDefaultModels(newPrefs)
    const read = await getDefaultModels()
    expect(read.defaults.LLM).toBe('prof-demo-001')
    expect(read.defaults.RERANK).toBe('prof-demo-003')
    expect(mockedRequest).not.toHaveBeenCalled()
  })
})

describe('models API - real 模式走 http.request', () => {
  beforeEach(() => {
    vi.resetModules()
    vi.stubEnv('VITE_RAG2OKF_DATA_SOURCE', 'real')
    mockedRequest.mockClear()
  })

  it('listConnections 调用 GET /model-connections', async () => {
    mockedRequest.mockResolvedValueOnce([])
    const { listConnections } = await import('../models')
    await listConnections()
    expect(mockedRequest).toHaveBeenCalledWith('/model-connections')
  })

  it('createConnection 调用 POST /model-connections 且请求体含 apiKey', async () => {
    mockedRequest.mockResolvedValueOnce({} as never)
    const { createConnection } = await import('../models')
    await createConnection({
      providerCode: 'OPENAI',
      providerName: 'OpenAI',
      displayName: '测试',
      baseUrl: 'https://api.openai.com/v1',
      apiKey: 'sk-test',
    })
    const [path, init] = mockedRequest.mock.calls[0]
    expect(path).toBe('/model-connections')
    expect(init?.method).toBe('POST')
    const body = JSON.parse(init?.body as string)
    expect(body.apiKey).toBe('sk-test')
  })

  it('updateConnection 调用 PATCH /model-connections/{key} 且不提交 apiKey', async () => {
    mockedRequest.mockResolvedValueOnce({} as never)
    const { updateConnection } = await import('../models')
    await updateConnection('conn-001', { displayName: '更新' })
    const [path, init] = mockedRequest.mock.calls[0]
    expect(path).toBe('/model-connections/conn-001')
    expect(init?.method).toBe('PATCH')
    const body = JSON.parse(init?.body as string)
    expect(body.apiKey).toBeUndefined()
  })

  it('replaceConnectionApiKey 调用 PATCH /model-connections/{key}/api-key', async () => {
    mockedRequest.mockResolvedValueOnce({ apiKeyMask: 'sk-****' } as never)
    const { replaceConnectionApiKey } = await import('../models')
    await replaceConnectionApiKey('conn-001', 'sk-new')
    const [path, init] = mockedRequest.mock.calls[0]
    expect(path).toBe('/model-connections/conn-001/api-key')
    expect(init?.method).toBe('PATCH')
    const body = JSON.parse(init?.body as string)
    expect(body.apiKey).toBe('sk-new')
  })

  it('listProfiles 调用 GET /model-profiles', async () => {
    mockedRequest.mockResolvedValueOnce([])
    const { listProfiles } = await import('../models')
    await listProfiles()
    expect(mockedRequest).toHaveBeenCalledWith('/model-profiles')
  })

  it('listProfiles 带 connectionKey 调用 GET /model-profiles?connectionKey=', async () => {
    mockedRequest.mockResolvedValueOnce([])
    const { listProfiles } = await import('../models')
    await listProfiles('conn-001')
    expect(mockedRequest).toHaveBeenCalledWith('/model-profiles?connectionKey=conn-001')
  })

  it('createProfile 调用 POST /model-profiles', async () => {
    mockedRequest.mockResolvedValueOnce({} as never)
    const { createProfile } = await import('../models')
    await createProfile({
      connectionKey: 'conn-001',
      modelType: 'LLM',
      modelName: 'gpt-4o',
    })
    const [path, init] = mockedRequest.mock.calls[0]
    expect(path).toBe('/model-profiles')
    expect(init?.method).toBe('POST')
  })

  it('testProfile 调用 POST /model-profiles/{key}/test', async () => {
    mockedRequest.mockResolvedValueOnce({ status: 'SUCCESS' } as never)
    const { testProfile } = await import('../models')
    await testProfile('prof-001')
    const [path, init] = mockedRequest.mock.calls[0]
    expect(path).toBe('/model-profiles/prof-001/test')
    expect(init?.method).toBe('POST')
  })

  it('getModelCatalog 调用 GET /model-catalog', async () => {
    mockedRequest.mockResolvedValueOnce({ providers: [], typeCounts: {} } as never)
    const { getModelCatalog } = await import('../models')
    await getModelCatalog()
    expect(mockedRequest).toHaveBeenCalledWith('/model-catalog')
  })

  it('getDefaultModels 调用 GET /users/me 并解析 preferenceJson（字符串形式）', async () => {
    // 后端 preferenceJson 字段在数据库中以 JSON 字符串形式存储
    mockedRequest.mockResolvedValueOnce({
      preferenceJson: JSON.stringify({ defaultModels: { defaults: { LLM: 'prof-001' } } }),
    } as never)
    const { getDefaultModels } = await import('../models')
    const result = await getDefaultModels()
    expect(result.defaults.LLM).toBe('prof-001')
    expect(mockedRequest).toHaveBeenCalledWith('/users/me')
  })

  it('getDefaultModels 兼容空 preferenceJson 字符串', async () => {
    mockedRequest.mockResolvedValueOnce({ preferenceJson: '{}' } as never)
    const { getDefaultModels } = await import('../models')
    const result = await getDefaultModels()
    expect(result).toEqual({ defaults: {} })
  })

  it('getDefaultModels 兼容 null preferenceJson', async () => {
    mockedRequest.mockResolvedValueOnce({ preferenceJson: null } as never)
    const { getDefaultModels } = await import('../models')
    const result = await getDefaultModels()
    expect(result).toEqual({ defaults: {} })
  })

  it('saveDefaultModels 调用 PATCH /users/me 且 preferenceJson 序列化为字符串', async () => {
    mockedRequest.mockResolvedValueOnce(undefined as never)
    const { saveDefaultModels } = await import('../models')
    await saveDefaultModels({ defaults: { LLM: 'prof-001' } })
    const [path, init] = mockedRequest.mock.calls[0]
    expect(path).toBe('/users/me')
    expect(init?.method).toBe('PATCH')
    const body = JSON.parse(init?.body as string)
    // preferenceJson 必须是 JSON 字符串（与后端存储类型一致）
    expect(typeof body.preferenceJson).toBe('string')
    const parsed = JSON.parse(body.preferenceJson)
    expect(parsed.defaultModels.defaults.LLM).toBe('prof-001')
  })
})
