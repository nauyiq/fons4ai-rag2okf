import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'

/**
 * Demo 模式集成测试（T029）。
 *
 * 验证 demo 模式下模型配置全链路交互流程，
 * 聚焦 CR-001 两步式 API（Connection + Profile + Catalog + Preference）。
 *
 * - demo 模式由 VITE_RAG2OKF_DATA_SOURCE=demo 激活
 * - demo 模式走本地 mock 数据，不发起网络请求
 * - 覆盖连接 CRUD、档案 CRUD、目录搜索过滤、偏好读写、CHAT→LLM 迁移、级联删除
 */

// Mock http.request —— demo 模式下永远不会调用，mock 后可用于验证隔离性
vi.mock('../api/http', () => ({
  request: vi.fn(),
}))

import { request } from '../api/http'

const mockedRequest = vi.mocked(request)

describe('demo 模式集成测试（T029）', () => {
  beforeEach(() => {
    vi.resetModules()
    vi.stubEnv('VITE_RAG2OKF_DATA_SOURCE', 'demo')
    mockedRequest.mockClear()
  })

  // ============ 场景 1：API 数据完整性 ============

  describe('API 数据完整性', () => {
    it('demo mock 数据返回有效的连接、档案、目录和默认配置', async () => {
      const { listConnections, listProfiles, getModelCatalog, getDefaultModels } =
        await import('../api/models')

      // listConnections 返回 3+ 连接，字段完整
      const connections = await listConnections()
      expect(connections.length).toBeGreaterThanOrEqual(3)
      for (const c of connections) {
        expect(c.connectionKey).toBeTruthy()
        expect(c.providerCode).toBeTruthy()
        expect(c.providerName).toBeTruthy()
        expect(c.displayName).toBeTruthy()
        expect(c.baseUrl).toBeTruthy()
        expect(c.apiKeyMask).toBeDefined()
        expect(typeof c.apiKeyConfigured).toBe('boolean')
        expect(['ACTIVE', 'DISABLED']).toContain(c.status)
        expect(c.updated).toBeTruthy()
      }

      // listProfiles 返回 4+ 档案，覆盖多种类型（LLM、EMBEDDING）
      const profiles = await listProfiles()
      expect(profiles.length).toBeGreaterThanOrEqual(4)
      const types = new Set(profiles.map((p) => p.modelType))
      expect(types.has('LLM')).toBe(true)
      expect(types.has('EMBEDDING')).toBe(true)
      for (const p of profiles) {
        expect(p.profileKey).toBeTruthy()
        expect(p.connectionKey).toBeTruthy()
        expect(p.modelName).toBeTruthy()
        expect(['ACTIVE', 'DISABLED']).toContain(p.status)
      }

      // getModelCatalog 返回 3+ 提供商，每个提供商有模型列表
      const catalog = await getModelCatalog()
      expect(catalog.providers.length).toBeGreaterThanOrEqual(3)
      for (const p of catalog.providers) {
        expect(p.providerCode).toBeTruthy()
        expect(p.providerName).toBeTruthy()
        expect(p.defaultBaseUrl).toBeDefined()
        expect(Array.isArray(p.models)).toBe(true)
      }

      // getDefaultModels 返回至少包含 LLM 和 EMBEDDING 的默认配置
      const defaults = await getDefaultModels()
      expect(defaults.defaults.LLM).toBeTruthy()
      expect(defaults.defaults.EMBEDDING).toBeTruthy()
    })
  })

  // ============ 场景 2：连接创建→档案创建→保存默认配置流程 ============

  describe('连接创建→档案创建→保存默认配置流程', () => {
    it('创建连接→创建档案→保存默认配置→读回一致', async () => {
      const { createConnection, createProfile, saveDefaultModels, getDefaultModels } =
        await import('../api/models')

      // 1. 创建新连接
      const conn = await createConnection({
        providerCode: 'CUSTOM',
        providerName: '集成测试提供商',
        displayName: '集成测试连接',
        baseUrl: 'https://integration.example.com/v1',
        apiKey: 'sk-integration-1234',
      })
      expect(conn.connectionKey).toBeTruthy()
      expect(conn.status).toBe('ACTIVE')
      expect(conn.apiKeyConfigured).toBe(true)

      // 2. 在该连接下创建档案
      const profile = await createProfile({
        connectionKey: conn.connectionKey,
        modelType: 'LLM',
        modelName: 'integration-llm',
        contextWindowLength: 32000,
        timeoutSeconds: 30,
        temperature: 0.7,
      })
      expect(profile.profileKey).toBeTruthy()
      expect(profile.connectionKey).toBe(conn.connectionKey)
      expect(profile.modelType).toBe('LLM')
      expect(profile.modelName).toBe('integration-llm')

      // 3. 保存默认配置，使用新创建的档案
      await saveDefaultModels({
        defaults: { LLM: profile.profileKey, EMBEDDING: 'prof-demo-002' },
      })

      // 4. 读回并验证一致性
      const defaults = await getDefaultModels()
      expect(defaults.defaults.LLM).toBe(profile.profileKey)
      expect(defaults.defaults.EMBEDDING).toBe('prof-demo-002')
    })
  })

  // ============ 场景 3：目录搜索与类型过滤 ============

  describe('目录搜索与类型过滤', () => {
    it('目录加载后支持关键词搜索与类型标签过滤', async () => {
      const { useModelCatalog } = await import('../composables/useModelCatalog')
      const {
        catalog,
        fetchCatalog,
        searchKeyword,
        activeTypeFilter,
        filteredProviders,
      } = useModelCatalog()

      // 目录加载，返回提供商
      await fetchCatalog()
      expect(catalog.value.length).toBeGreaterThanOrEqual(3)

      // 初始无过滤，显示全部有模型的提供商
      expect(filteredProviders.value.length).toBeGreaterThanOrEqual(3)

      // 关键词搜索 "deepseek" —— 仅匹配 DeepSeek（提供商名）
      searchKeyword.value = 'deepseek'
      expect(filteredProviders.value.length).toBe(1)
      expect(filteredProviders.value[0].providerCode).toBe('DEEPSEEK')

      // 关键词搜索 "qwen" —— 匹配 QWEN 旗下模型名（qwen-plus/qwen-turbo）
      searchKeyword.value = 'qwen'
      expect(filteredProviders.value.length).toBe(1)
      expect(filteredProviders.value[0].providerCode).toBe('QWEN')

      // 清除关键词
      searchKeyword.value = ''
      expect(filteredProviders.value.length).toBeGreaterThanOrEqual(3)

      // 类型过滤 EMBEDDING —— DeepSeek/QWEN/OpenAI 都有嵌入模型
      activeTypeFilter.value = 'EMBEDDING'
      const embeddingProviders = filteredProviders.value
      expect(embeddingProviders.length).toBe(3)
      for (const p of embeddingProviders) {
        expect(p.models.some((m) => m.modelType === 'EMBEDDING')).toBe(true)
      }

      // 类型过滤 RERANK —— 仅 QWEN 有 rerank 模型
      activeTypeFilter.value = 'RERANK'
      expect(filteredProviders.value.length).toBe(1)
      expect(filteredProviders.value[0].providerCode).toBe('QWEN')

      // 清除类型过滤
      activeTypeFilter.value = ''
      expect(filteredProviders.value.length).toBeGreaterThanOrEqual(3)
    })
  })

  // ============ 场景 4：CHAT→LLM 迁移 ============

  describe('CHAT→LLM 默认值迁移', () => {
    afterEach(() => {
      vi.doUnmock('../api/models')
    })

    it('加载历史 CHAT 默认值时迁移为 LLM 并清理 CHAT 键', async () => {
      // Mock getDefaultModels 返回历史 CHAT 键
      vi.doMock('../api/models', async (importOriginal) => {
        const actual = (await importOriginal()) as typeof import('../api/models')
        return {
          ...actual,
          getDefaultModels: vi
            .fn()
            .mockResolvedValue({ defaults: { CHAT: 'prof-001' } }),
        } as typeof import('../api/models')
      })

      const { useDefaultModels } = await import('../composables/useDefaultModels')
      const { defaults, load } = useDefaultModels()
      await load()

      // CHAT 值迁移到 LLM
      expect(defaults.value.defaults.LLM).toBe('prof-001')
      // CHAT 键被清理
      expect((defaults.value.defaults as Record<string, unknown>).CHAT).toBeUndefined()
    })
  })

  // ============ 场景 5：demo 模式隔离 ============

  describe('demo 模式隔离', () => {
    it('demo 模式下所有模型 API 函数均不调用 http.request', async () => {
      const api = await import('../api/models')

      // 连接 API
      await api.listConnections()
      const conn = await api.createConnection({
        providerCode: 'CUSTOM',
        providerName: '隔离测试',
        displayName: '隔离测试连接',
        baseUrl: 'https://isolation.example.com',
        apiKey: 'sk-iso-1234',
      })
      await api.updateConnection(conn.connectionKey, { displayName: '更新名称' })
      await api.replaceConnectionApiKey(conn.connectionKey, 'sk-replaced-5678')

      // 档案 API
      await api.listProfiles()
      await api.listProfiles(conn.connectionKey)
      const profile = await api.createProfile({
        connectionKey: conn.connectionKey,
        modelType: 'LLM',
        modelName: 'iso-model',
      })
      await api.updateProfile(profile.profileKey, { modelName: 'updated-model' })
      await api.testProfile(profile.profileKey)

      // 目录 API
      await api.getModelCatalog()

      // 偏好 API
      await api.getDefaultModels()
      await api.saveDefaultModels({ defaults: { LLM: profile.profileKey } })

      // 删除操作
      await api.deleteProfile(profile.profileKey)
      await api.deleteConnection(conn.connectionKey)

      // 验证 http.request 从未被调用
      expect(mockedRequest).not.toHaveBeenCalled()
    })
  })

  // ============ 场景 6：档案测试流程 ============

  describe('档案测试流程', () => {
    it('ACTIVE 档案测试返回 SUCCESS，DISABLED 档案测试返回 FAILED', async () => {
      const { testProfile, listProfiles } = await import('../api/models')
      const profiles = await listProfiles()

      // 找到 ACTIVE 档案
      const active = profiles.find((p) => p.status === 'ACTIVE')
      expect(active).toBeDefined()
      const activeResult = await testProfile(active!.profileKey)
      expect(activeResult.status).toBe('SUCCESS')
      expect(activeResult.errorCode).toBeNull()

      // 找到 DISABLED 档案
      const disabled = profiles.find((p) => p.status === 'DISABLED')
      expect(disabled).toBeDefined()
      const disabledResult = await testProfile(disabled!.profileKey)
      expect(disabledResult.status).toBe('FAILED')
      expect(disabledResult.errorCode).toBe('PROFILE_DISABLED')
    })
  })

  // ============ 场景 7：连接删除级联清理档案 ============

  describe('连接删除级联清理档案', () => {
    it('删除连接后关联档案也被清除', async () => {
      const { createConnection, createProfile, deleteConnection, listProfiles } =
        await import('../api/models')

      // 创建连接 + 档案
      const conn = await createConnection({
        providerCode: 'CUSTOM',
        providerName: '级联测试',
        displayName: '级联测试连接',
        baseUrl: 'https://cascade.example.com',
        apiKey: 'sk-cascade-1234',
      })
      const profile = await createProfile({
        connectionKey: conn.connectionKey,
        modelType: 'LLM',
        modelName: 'cascade-model',
      })

      // 档案存在
      const profilesBefore = await listProfiles(conn.connectionKey)
      expect(profilesBefore.find((p) => p.profileKey === profile.profileKey)).toBeDefined()

      // 删除连接
      const ok = await deleteConnection(conn.connectionKey)
      expect(ok).toBe(true)

      // 档案也被删除（按 connectionKey 过滤返回空）
      const profilesAfter = await listProfiles(conn.connectionKey)
      expect(profilesAfter.find((p) => p.profileKey === profile.profileKey)).toBeUndefined()

      // 全局档案列表中也不存在
      const allProfiles = await listProfiles()
      expect(allProfiles.find((p) => p.profileKey === profile.profileKey)).toBeUndefined()
    })
  })
})
