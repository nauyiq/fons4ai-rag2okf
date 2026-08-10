import { describe, it, expect, beforeEach, vi } from 'vitest'

/**
 * Real 模式 API 契约回归（T031，传输层使用 mock）。
 *
 * 验证 real 模式下模型域全链路 API 契约：基于实际后端 DTO 结构 mock http 层，
 * 验证前端 API 函数正确发送请求（URL/method/body）并解包响应数据。
 * 真实浏览器到本地后端的联调证据单独记录在实施报告，避免把契约测试误称为真实网络测试。
 *
 * 覆盖：
 * - 连接 CRUD + API Key 替换（DELETE 返回 R<Void>，data 为 null 但 success=true）
 * - 档案 CRUD + 测试 + connectionKey 过滤
 * - 目录加载（providers + typeCounts）
 * - 偏好读写（preferenceJson 为 JSON 字符串：写入时序列化、读取时反序列化）
 * - 知识库列表含 ownerUserKey/canDelete 字段
 *
 * 所有 mock 响应模拟后端 R<T> 包装的 data 部分（http.request 已在内部解包 envelope.data）。
 */

// Mock http.request —— real 模式下捕获请求参数并返回模拟的后端响应数据
vi.mock('../api/http', () => ({
  request: vi.fn(),
  ApiRequestError: class extends Error {
    constructor(
      message: string,
      readonly status: number,
      readonly code?: string,
    ) {
      super(message)
      this.name = 'ApiRequestError'
    }
  },
}))

import { request } from '../api/http'

const mockedRequest = vi.mocked(request)

describe('real 模式 API 契约回归（T031，mock 传输层）', () => {
  beforeEach(() => {
    vi.resetModules()
    vi.stubEnv('VITE_RAG2OKF_DATA_SOURCE', 'real')
    mockedRequest.mockClear()
  })

  // ============ 场景 1：连接列表 ============

  it('GET /model-connections 返回连接列表且前端正确解包', async () => {
    // 后端 R<List<ModelConnectionResponse>> 解包后的 data
    const backendConnections = [
      {
        connectionKey: 'conn-001',
        providerCode: 'OPENAI',
        providerName: 'OpenAI',
        displayName: 'OpenAI 连接',
        baseUrl: 'https://api.openai.com/v1',
        apiKeyMask: 'sk-****...****a9d0',
        apiKeyConfigured: true,
        status: 'ACTIVE',
        updated: '2026-08-09T10:00:00Z',
      },
      {
        connectionKey: 'conn-002',
        providerCode: 'DEEPSEEK',
        providerName: 'DeepSeek',
        displayName: 'DeepSeek 连接',
        baseUrl: 'https://api.deepseek.com/v1',
        apiKeyMask: 'sk-****...****3f2a',
        apiKeyConfigured: true,
        status: 'DISABLED',
        updated: '2026-08-08T10:00:00Z',
      },
    ]
    mockedRequest.mockResolvedValueOnce(backendConnections as never)

    const { listConnections } = await import('../api/models')
    const result = await listConnections()

    // 验证请求：GET /model-connections，无 body
    expect(mockedRequest).toHaveBeenCalledTimes(1)
    const [path, init] = mockedRequest.mock.calls[0]
    expect(path).toBe('/model-connections')
    expect(init?.method).toBeUndefined()

    // 验证响应解包：data 数组直接转为 ModelConnection[]
    expect(result).toHaveLength(2)
    expect(result[0].connectionKey).toBe('conn-001')
    expect(result[0].providerCode).toBe('OPENAI')
    expect(result[0].apiKeyConfigured).toBe(true)
    expect(result[1].status).toBe('DISABLED')
  })

  // ============ 场景 2：创建连接 ============

  it('POST /model-connections 创建连接，请求体使用 providerCode（非 templateCode）', async () => {
    const createdConnection = {
      connectionKey: 'conn-new-001',
      providerCode: 'OPENAI',
      providerName: 'OpenAI',
      displayName: '我的 OpenAI 连接',
      baseUrl: 'https://api.openai.com/v1',
      apiKeyMask: 'sk-****...****1234',
      apiKeyConfigured: true,
      status: 'ACTIVE',
      updated: '2026-08-09T10:00:00Z',
    }
    mockedRequest.mockResolvedValueOnce(createdConnection as never)

    const { createConnection } = await import('../api/models')
    const result = await createConnection({
      providerCode: 'OPENAI',
      providerName: 'OpenAI',
      displayName: '我的 OpenAI 连接',
      baseUrl: 'https://api.openai.com/v1',
      apiKey: 'sk-test-key-1234',
    })

    // 验证请求路径与方法
    const [path, init] = mockedRequest.mock.calls[0]
    expect(path).toBe('/model-connections')
    expect(init?.method).toBe('POST')

    // 验证请求体字段名：providerCode 而非 templateCode，且 apiKey 包含在内
    const body = JSON.parse(init?.body as string)
    expect(body.providerCode).toBe('OPENAI')
    expect(body.templateCode).toBeUndefined()
    expect(body.apiKey).toBe('sk-test-key-1234')
    expect(body.displayName).toBe('我的 OpenAI 连接')

    // 验证返回值正确解包
    expect(result.connectionKey).toBe('conn-new-001')
    expect(result.apiKeyMask).toContain('1234')
    expect(result.apiKeyConfigured).toBe(true)
  })

  // ============ 场景 3：提供商模板 ============

  it('GET /model-provider-templates 返回公开模板及 officialUrl', async () => {
    const backendTemplates = [
      {
        code: 'OPENAI',
        providerName: 'OpenAI',
        defaultBaseUrl: 'https://api.openai.com/v1',
        officialUrl: 'https://platform.openai.com',
      },
      {
        code: 'DEEPSEEK',
        providerName: 'DeepSeek',
        defaultBaseUrl: 'https://api.deepseek.com/v1',
        officialUrl: 'https://platform.deepseek.com',
      },
    ]
    mockedRequest.mockResolvedValueOnce(backendTemplates as never)

    const { listModelProviderTemplates } = await import('../api/models')
    const templates = await listModelProviderTemplates()

    expect(mockedRequest).toHaveBeenCalledWith('/model-provider-templates')
    expect(templates).toHaveLength(2)
    expect(templates[0]).toEqual(expect.objectContaining({
      code: 'OPENAI',
      officialUrl: 'https://platform.openai.com',
    }))
    expect(templates[0]).not.toHaveProperty('models')
  })

  // ============ 场景 4：创建档案 ============

  it('POST /model-profiles 创建档案，请求体字段对齐后端契约', async () => {
    const createdProfile = {
      profileKey: 'prof-new-001',
      connectionKey: 'conn-001',
      modelType: 'LLM',
      modelName: 'gpt-4o',
      dimensions: null,
      contextWindowLength: 128000,
      timeoutSeconds: 45,
      temperature: 0.5,
      status: 'ACTIVE',
      lastTestStatus: 'UNTESTED',
      lastTestAt: null,
      updated: '2026-08-09T10:00:00Z',
    }
    mockedRequest.mockResolvedValueOnce(createdProfile as never)

    const { createProfile } = await import('../api/models')
    const result = await createProfile({
      connectionKey: 'conn-001',
      modelType: 'LLM',
      modelName: 'gpt-4o',
      contextWindowLength: 128000,
      timeoutSeconds: 45,
      temperature: 0.5,
    })

    const [path, init] = mockedRequest.mock.calls[0]
    expect(path).toBe('/model-profiles')
    expect(init?.method).toBe('POST')

    // 验证请求体字段：contextWindowLength 可选字段，未提交时由后端默认处理
    const body = JSON.parse(init?.body as string)
    expect(body.connectionKey).toBe('conn-001')
    expect(body.modelType).toBe('LLM')
    expect(body.modelName).toBe('gpt-4o')
    // 此处显式提交了 contextWindowLength，验证透传
    expect(body.contextWindowLength).toBe(128000)

    // 验证返回的档案字段解包正确
    expect(result.profileKey).toBe('prof-new-001')
    expect(result.modelType).toBe('LLM')
    expect(result.contextWindowLength).toBe(128000)
    expect(result.status).toBe('ACTIVE')
  })

  it('POST /model-profiles 创建档案不提交 contextWindowLength 时后端忽略', async () => {
    // 后端返回时填入默认 contextWindowLength=null
    const createdProfile = {
      profileKey: 'prof-new-002',
      connectionKey: 'conn-001',
      modelType: 'EMBEDDING',
      modelName: 'text-embedding-3-small',
      dimensions: 1536,
      contextWindowLength: null,
      timeoutSeconds: 60,
      temperature: null,
      status: 'ACTIVE',
      lastTestStatus: 'UNTESTED',
      lastTestAt: null,
      updated: '2026-08-09T10:00:00Z',
    }
    mockedRequest.mockResolvedValueOnce(createdProfile as never)

    const { createProfile } = await import('../api/models')
    const result = await createProfile({
      connectionKey: 'conn-001',
      modelType: 'EMBEDDING',
      modelName: 'text-embedding-3-small',
    })

    const [_, init] = mockedRequest.mock.calls[0]
    const body = JSON.parse(init?.body as string)
    // 前端不主动写入 contextWindowLength
    expect(body.contextWindowLength).toBeUndefined()
    // 后端返回 contextWindowLength=null
    expect(result.contextWindowLength).toBeNull()
    expect(result.dimensions).toBe(1536)
  })

  // ============ 场景 5：测试档案 ============

  it('POST /model-profiles/{key}/test 测试档案连通性', async () => {
    const testResult = {
      status: 'SUCCEEDED',
      errorCode: null,
      dimensions: null,
    }
    mockedRequest.mockResolvedValueOnce(testResult as never)

    const { testProfile } = await import('../api/models')
    const result = await testProfile('prof-001')

    const [path, init] = mockedRequest.mock.calls[0]
    expect(path).toBe('/model-profiles/prof-001/test')
    expect(init?.method).toBe('POST')

    expect(result.status).toBe('SUCCEEDED')
    expect(result.errorCode).toBeNull()
    expect(result.dimensions).toBeNull()
  })

  it('POST /model-profiles/{key}/test EMBEDDING 档案返回 dimensions', async () => {
    const testResult = {
      status: 'SUCCEEDED',
      errorCode: null,
      dimensions: 1024,
    }
    mockedRequest.mockResolvedValueOnce(testResult as never)

    const { testProfile } = await import('../api/models')
    const result = await testProfile('prof-emb-001')

    expect(result.status).toBe('SUCCEEDED')
    expect(result.dimensions).toBe(1024)
  })

  // ============ 场景 6：保存偏好 ============

  it('PATCH /users/me 保存偏好，preferenceJson 被序列化为字符串发送', async () => {
    // 后端 R<UserProfile> 解包后的 data
    const updatedUser = {
      userKey: 'user-001',
      email: 'admin@rag2okf.cn',
      displayName: '管理员',
      avatarUrl: '',
      preferenceJson: JSON.stringify({
        defaultModels: { defaults: { LLM: 'prof-001', EMBEDDING: 'prof-002' } },
        theme: 'dark',
      }),
      workspaceKey: 'ws-001',
      workspaceName: '默认工作空间',
      workspaceRole: 'ADMIN',
    }
    mockedRequest.mockResolvedValueOnce(updatedUser as never)

    const { saveDefaultModels } = await import('../api/models')
    await saveDefaultModels({ defaults: { LLM: 'prof-001', EMBEDDING: 'prof-002' } })

    const [path, init] = mockedRequest.mock.calls[0]
    expect(path).toBe('/users/me')
    expect(init?.method).toBe('PATCH')

    // 验证请求体：preferenceJson 必须是 JSON 字符串（与后端存储类型一致）
    const body = JSON.parse(init?.body as string)
    expect(typeof body.preferenceJson).toBe('string')
    const parsedPreference = JSON.parse(body.preferenceJson)
    expect(parsedPreference.defaultModels.defaults.LLM).toBe('prof-001')
    expect(parsedPreference.defaultModels.defaults.EMBEDDING).toBe('prof-002')
    // 仅提交 defaultModels 子键，由后端局部合并保留其他偏好键
    expect(parsedPreference.defaultModels).toBeDefined()
    expect(parsedPreference.theme).toBeUndefined()
  })

  // ============ 场景 7：读取偏好 ============

  it('GET /users/me 读取偏好，preferenceJson 为字符串时前端正确 parse 并提取 defaultModels', async () => {
    const backendUser = {
      userKey: 'user-001',
      email: 'admin@rag2okf.cn',
      displayName: '管理员',
      avatarUrl: '',
      preferenceJson: JSON.stringify({
        theme: 'dark',
        defaultModels: { defaults: { LLM: 'prof-001', EMBEDDING: 'prof-002', RERANK: 'prof-003' } },
      }),
      workspaceKey: 'ws-001',
      workspaceName: '默认工作空间',
      workspaceRole: 'ADMIN',
    }
    mockedRequest.mockResolvedValueOnce(backendUser as never)

    const { getDefaultModels } = await import('../api/models')
    const result = await getDefaultModels()

    expect(mockedRequest).toHaveBeenCalledWith('/users/me')
    // 正确解析 JSON 字符串并提取 defaultModels
    expect(result.defaults.LLM).toBe('prof-001')
    expect(result.defaults.EMBEDDING).toBe('prof-002')
    expect(result.defaults.RERANK).toBe('prof-003')
  })

  it('GET /users/me preferenceJson 为空字符串时返回空 defaults', async () => {
    mockedRequest.mockResolvedValueOnce({
      preferenceJson: '{}',
    } as never)

    const { getDefaultModels } = await import('../api/models')
    const result = await getDefaultModels()

    expect(result).toEqual({ defaults: {} })
  })

  it('GET /users/me preferenceJson 缺失 defaultModels 键时返回空 defaults', async () => {
    mockedRequest.mockResolvedValueOnce({
      preferenceJson: JSON.stringify({ theme: 'dark' }),
    } as never)

    const { getDefaultModels } = await import('../api/models')
    const result = await getDefaultModels()

    expect(result).toEqual({ defaults: {} })
  })

  // ============ 场景 8：删除连接（R<Void>） ============

  it('DELETE /model-connections/{key} 返回 R<Void>（data=null）时 deleteConnection 正确处理', async () => {
    // 后端 R<Void> 解包后 data 为 null（success=true 已在 http 层校验）
    mockedRequest.mockResolvedValueOnce(null as never)

    const { deleteConnection } = await import('../api/models')
    // 不应抛错：http.request 在 envelope.success=true 时不抛错
    const result = await deleteConnection('conn-001')

    const [path, init] = mockedRequest.mock.calls[0]
    expect(path).toBe('/model-connections/conn-001')
    expect(init?.method).toBe('DELETE')
    // 后端返回 null（R<Void> 包装），调用方依赖不抛错而非具体返回值
    expect(result).toBeNull()
  })

  it('DELETE /model-connections/{key} 路径中的 key 被正确 URL 编码', async () => {
    mockedRequest.mockResolvedValueOnce(null as never)

    const { deleteConnection } = await import('../api/models')
    await deleteConnection('conn with space/特殊')

    const [path] = mockedRequest.mock.calls[0]
    expect(path).toBe('/model-connections/conn%20with%20space%2F%E7%89%B9%E6%AE%8A')
  })

  // ============ 场景 9：删除档案（R<Void>） ============

  it('DELETE /model-profiles/{key} 返回 R<Void>（data=null）时 deleteProfile 正确处理', async () => {
    mockedRequest.mockResolvedValueOnce(null as never)

    const { deleteProfile } = await import('../api/models')
    const result = await deleteProfile('prof-001')

    const [path, init] = mockedRequest.mock.calls[0]
    expect(path).toBe('/model-profiles/prof-001')
    expect(init?.method).toBe('DELETE')
    expect(result).toBeNull()
  })

  // ============ 场景 10：替换 API Key ============

  it('PATCH /model-connections/{key}/api-key 替换密钥后返回更新后的连接，apiKeyMask 更新', async () => {
    // 后端返回更新后的完整连接对象（与新 apiKeyMask 一致）
    const updatedConnection = {
      connectionKey: 'conn-001',
      providerCode: 'OPENAI',
      providerName: 'OpenAI',
      displayName: 'OpenAI 连接',
      baseUrl: 'https://api.openai.com/v1',
      apiKeyMask: 'sk-****...****9999',
      apiKeyConfigured: true,
      status: 'ACTIVE',
      updated: '2026-08-09T11:00:00Z',
    }
    mockedRequest.mockResolvedValueOnce(updatedConnection as never)

    const { replaceConnectionApiKey } = await import('../api/models')
    const result = await replaceConnectionApiKey('conn-001', 'sk-new-secret-9999')

    const [path, init] = mockedRequest.mock.calls[0]
    expect(path).toBe('/model-connections/conn-001/api-key')
    expect(init?.method).toBe('PATCH')

    const body = JSON.parse(init?.body as string)
    expect(body.apiKey).toBe('sk-new-secret-9999')

    // 验证返回的 apiKeyMask 反映新密钥末 4 位
    expect(result!.apiKeyMask).toContain('9999')
  })

  // ============ 场景 11：档案列表按 connectionKey 过滤 ============

  it('GET /model-profiles?connectionKey=xxx 正确传递过滤参数', async () => {
    const backendProfiles = [
      {
        profileKey: 'prof-001',
        connectionKey: 'conn-001',
        modelType: 'LLM',
        modelName: 'gpt-4o',
        dimensions: null,
        contextWindowLength: 128000,
        timeoutSeconds: 45,
        temperature: 0.5,
        status: 'ACTIVE',
        lastTestStatus: 'SUCCEEDED',
        lastTestAt: '2026-08-09T10:00:00Z',
        updated: '2026-08-09T10:00:00Z',
      },
      {
        profileKey: 'prof-002',
        connectionKey: 'conn-001',
        modelType: 'EMBEDDING',
        modelName: 'text-embedding-3-small',
        dimensions: 1536,
        contextWindowLength: null,
        timeoutSeconds: 60,
        temperature: null,
        status: 'ACTIVE',
        lastTestStatus: 'SUCCEEDED',
        lastTestAt: '2026-08-09T10:00:00Z',
        updated: '2026-08-09T10:00:00Z',
      },
    ]
    mockedRequest.mockResolvedValueOnce(backendProfiles as never)

    const { listProfiles } = await import('../api/models')
    const result = await listProfiles('conn-001')

    // 验证 URL 含 connectionKey 查询参数
    expect(mockedRequest).toHaveBeenCalledWith('/model-profiles?connectionKey=conn-001')
    // 验证返回的档案都属于该连接
    expect(result).toHaveLength(2)
    expect(result.every((p) => p.connectionKey === 'conn-001')).toBe(true)
  })

  it('GET /model-profiles 不传 connectionKey 时不附加查询参数', async () => {
    mockedRequest.mockResolvedValueOnce([] as never)

    const { listProfiles } = await import('../api/models')
    await listProfiles()

    expect(mockedRequest).toHaveBeenCalledWith('/model-profiles')
  })

  it('GET /model-profiles?connectionKey= 含特殊字符时正确 URL 编码', async () => {
    mockedRequest.mockResolvedValueOnce([] as never)

    const { listProfiles } = await import('../api/models')
    await listProfiles('conn 001/special')

    expect(mockedRequest).toHaveBeenCalledWith(
      '/model-profiles?connectionKey=conn%20001%2Fspecial',
    )
  })

  // ============ 场景 12：知识库列表含 ownerUserKey/canDelete ============

  it('GET /workspaces/{key}/knowledge-bases 列表含 ownerUserKey 与 canDelete 字段', async () => {
    const backendPage = {
      records: [
        {
          knowledgeBaseKey: 'kb-001',
          name: '风险策略知识库',
          description: '存放风险策略文档',
          autoParse: true,
          autoPublish: false,
          updated: '2026-08-09T10:00:00Z',
          ownerUserKey: 'user-001',
          canDelete: true,
        },
        {
          knowledgeBaseKey: 'kb-002',
          name: '产品文档库',
          description: '团队共享产品文档',
          autoParse: false,
          autoPublish: false,
          updated: '2026-08-08T10:00:00Z',
          ownerUserKey: 'user-002',
          canDelete: false,
        },
      ],
      total: 2,
      page: 0,
      size: 20,
    }
    mockedRequest.mockResolvedValueOnce(backendPage as never)

    const { listKnowledgeBases } = await import('../api/knowledge-bases')
    const result = await listKnowledgeBases('ws-001')

    // 验证请求路径含 workspaceKey
    const [path] = mockedRequest.mock.calls[0]
    expect(path).toContain('/workspaces/ws-001/knowledge-bases')

    // 验证返回的列表含 ownerUserKey 和 canDelete 字段
    expect(result.records).toHaveLength(2)
    expect(result.total).toBe(2)
    const first = result.records[0]
    expect(first).toHaveProperty('ownerUserKey')
    expect(first).toHaveProperty('canDelete')
    expect(first.ownerUserKey).toBe('user-001')
    expect(first.canDelete).toBe(true)
    const second = result.records[1]
    expect(second.ownerUserKey).toBe('user-002')
    expect(second.canDelete).toBe(false)
  })

  // ============ 场景 13：删除知识库（R<Void>） ============

  it('DELETE /knowledge-bases/{key} 返回 R<Void>（data=null）时 deleteKnowledgeBase 正确处理', async () => {
    mockedRequest.mockResolvedValueOnce(null as never)

    const { deleteKnowledgeBase } = await import('../api/knowledge-bases')
    // 不应抛错：http.request 在 success=true 时不抛错
    const result = await deleteKnowledgeBase('kb-001')

    const [path, init] = mockedRequest.mock.calls[0]
    expect(path).toBe('/knowledge-bases/kb-001')
    expect(init?.method).toBe('DELETE')
    expect(result).toBeNull()
  })

  // ============ 场景 14：偏好读写完整流程 ============

  it('偏好读写完整流程：保存后读取，defaultModels 数据一致', async () => {
    // 第一次调用：PATCH /users/me 返回更新后的 user
    const updatedUser = {
      userKey: 'user-001',
      email: 'admin@rag2okf.cn',
      displayName: '管理员',
      avatarUrl: '',
      preferenceJson: JSON.stringify({
        defaultModels: { defaults: { LLM: 'prof-001', EMBEDDING: 'prof-002' } },
        theme: 'dark',
      }),
      workspaceKey: 'ws-001',
      workspaceName: '默认工作空间',
      workspaceRole: 'ADMIN',
    }
    mockedRequest.mockResolvedValueOnce(updatedUser as never)

    const { saveDefaultModels, getDefaultModels } = await import('../api/models')

    // 保存：发送 preferenceJson 字符串
    await saveDefaultModels({ defaults: { LLM: 'prof-001', EMBEDDING: 'prof-002' } })
    const saveCall = mockedRequest.mock.calls[0]
    const saveBody = JSON.parse(saveCall[1]?.body as string)
    expect(typeof saveBody.preferenceJson).toBe('string')

    // 第二次调用：GET /users/me 返回 preferenceJson 字符串
    mockedRequest.mockResolvedValueOnce({
      preferenceJson: JSON.stringify({
        defaultModels: { defaults: { LLM: 'prof-001', EMBEDDING: 'prof-002' } },
        theme: 'dark', // 后端局部合并保留的其他偏好键
      }),
    } as never)

    // 读取：前端 parse 字符串并提取 defaultModels
    const read = await getDefaultModels()
    expect(read.defaults.LLM).toBe('prof-001')
    expect(read.defaults.EMBEDDING).toBe('prof-002')
  })

  // ============ 场景 15：完整连接→档案创建流程 ============

  it('完整流程：创建连接 → 创建档案 → 测试档案 → 删除档案 → 删除连接', async () => {
    const { createConnection, createProfile, testProfile, deleteProfile, deleteConnection } =
      await import('../api/models')

    // 1. 创建连接
    mockedRequest.mockResolvedValueOnce({
      connectionKey: 'conn-flow-001',
      providerCode: 'OPENAI',
      providerName: 'OpenAI',
      displayName: '流程测试连接',
      baseUrl: 'https://api.openai.com/v1',
      apiKeyMask: 'sk-****...****flow',
      apiKeyConfigured: true,
      status: 'ACTIVE',
      updated: '2026-08-09T10:00:00Z',
    } as never)
    const conn = await createConnection({
      providerCode: 'OPENAI',
      providerName: 'OpenAI',
      displayName: '流程测试连接',
      baseUrl: 'https://api.openai.com/v1',
      apiKey: 'sk-flow-key',
    })
    expect(conn.connectionKey).toBe('conn-flow-001')

    // 2. 在连接下创建档案
    mockedRequest.mockResolvedValueOnce({
      profileKey: 'prof-flow-001',
      connectionKey: 'conn-flow-001',
      modelType: 'LLM',
      modelName: 'gpt-4o',
      dimensions: null,
      contextWindowLength: 128000,
      timeoutSeconds: 45,
      temperature: 0.5,
      status: 'ACTIVE',
      lastTestStatus: 'UNTESTED',
      lastTestAt: null,
      updated: '2026-08-09T10:00:00Z',
    } as never)
    const profile = await createProfile({
      connectionKey: conn.connectionKey,
      modelType: 'LLM',
      modelName: 'gpt-4o',
      contextWindowLength: 128000,
    })
    expect(profile.profileKey).toBe('prof-flow-001')
    // 验证创建档案时 connectionKey 透传正确
    const createProfileCall = mockedRequest.mock.calls[1]
    const createProfileBody = JSON.parse(createProfileCall[1]?.body as string)
    expect(createProfileBody.connectionKey).toBe('conn-flow-001')

    // 3. 测试档案
    mockedRequest.mockResolvedValueOnce({
      status: 'SUCCEEDED',
      errorCode: null,
      dimensions: null,
    } as never)
    const testResult = await testProfile(profile.profileKey)
    expect(testResult.status).toBe('SUCCEEDED')
    expect(mockedRequest.mock.calls[2][0]).toBe('/model-profiles/prof-flow-001/test')

    // 4. 删除档案（R<Void>）
    mockedRequest.mockResolvedValueOnce(null as never)
    await deleteProfile(profile.profileKey)
    expect(mockedRequest.mock.calls[3][0]).toBe('/model-profiles/prof-flow-001')
    expect(mockedRequest.mock.calls[3][1]?.method).toBe('DELETE')

    // 5. 删除连接（R<Void>）
    mockedRequest.mockResolvedValueOnce(null as never)
    await deleteConnection(conn.connectionKey)
    expect(mockedRequest.mock.calls[4][0]).toBe('/model-connections/conn-flow-001')
    expect(mockedRequest.mock.calls[4][1]?.method).toBe('DELETE')

    // 共发起 5 次请求
    expect(mockedRequest).toHaveBeenCalledTimes(5)
  })
})
