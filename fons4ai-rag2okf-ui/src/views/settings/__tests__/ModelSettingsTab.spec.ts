import { flushPromises, mount, DOMWrapper } from '@vue/test-utils'
import { message } from 'ant-design-vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'

import ModelSettingsTab from '../ModelSettingsTab.vue'
import * as modelsApi from '../../../api/models'

/**
 * ModelSettingsTab（T028 Ragflow 风格左右双栏）交互测试。
 *
 * 验证点：
 * - 左右双栏布局渲染
 * - 点击目录卡片打开连接弹窗（预填 baseUrl）
 * - 官方链接点击不触发卡片添加（事件隔离）
 * - 连接创建 → 确认添加模型 → 档案创建流程
 * - 默认模型下拉展示正确档案标签
 * - 保存默认配置调用 saveDefaultModels
 * - 类型标签过滤模型市场
 * - CHAT→LLM 默认值迁移
 */

// ============ mock 数据 ============

const mockConnections = [
  {
    connectionKey: 'conn-1',
    providerCode: 'DEEPSEEK',
    providerName: 'DeepSeek',
    displayName: 'DeepSeek 连接',
    baseUrl: 'https://api.deepseek.com/v1',
    apiKeyMask: 'sk-****3f2a',
    apiKeyConfigured: true,
    status: 'ACTIVE',
    updated: '2026-08-07T00:00:00.000Z',
  },
  {
    connectionKey: 'conn-2',
    providerCode: 'QWEN',
    providerName: '通义千问',
    displayName: '通义千问连接',
    baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
    apiKeyMask: 'sk-****8b1c',
    apiKeyConfigured: true,
    status: 'ACTIVE',
    updated: '2026-08-07T00:00:00.000Z',
  },
]

const mockProfiles = [
  {
    profileKey: 'prof-1',
    connectionKey: 'conn-1',
    modelType: 'LLM',
    modelName: 'deepseek-chat',
    dimensions: null,
    contextWindowLength: 64000,
    timeoutSeconds: 30,
    temperature: 0.7,
    status: 'ACTIVE',
    lastTestStatus: 'SUCCESS',
    lastTestAt: '2026-08-07T00:00:00.000Z',
    updated: '2026-08-07T00:00:00.000Z',
  },
  {
    profileKey: 'prof-2',
    connectionKey: 'conn-2',
    modelType: 'RERANK',
    modelName: 'gte-rerank',
    dimensions: null,
    contextWindowLength: null,
    timeoutSeconds: 60,
    temperature: null,
    status: 'ACTIVE',
    lastTestStatus: 'SUCCESS',
    lastTestAt: '2026-08-07T00:00:00.000Z',
    updated: '2026-08-07T00:00:00.000Z',
  },
]

const mockCatalog = {
  providers: [
    {
      providerCode: 'DEEPSEEK',
      providerName: 'DeepSeek',
      defaultBaseUrl: 'https://api.deepseek.com/v1',
      officialUrl: 'https://www.deepseek.com',
      models: [
        { modelName: 'deepseek-chat', modelType: 'LLM' },
        { modelName: 'deepseek-coder', modelType: 'LLM' },
      ],
    },
    {
      providerCode: 'QWEN',
      providerName: '通义千问',
      defaultBaseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
      officialUrl: 'https://help.aliyun.com/zh/dashscope',
      models: [
        { modelName: 'qwen-plus', modelType: 'LLM' },
        { modelName: 'gte-rerank', modelType: 'RERANK' },
      ],
    },
    {
      providerCode: 'OPENAI',
      providerName: 'OpenAI',
      defaultBaseUrl: 'https://api.openai.com/v1',
      officialUrl: 'https://platform.openai.com',
      models: [{ modelName: 'gpt-4o', modelType: 'LLM' }],
    },
  ],
  typeCounts: { LLM: 4, RERANK: 1 },
}

const mockDefaults = { defaults: { LLM: 'prof-1' } }

vi.mock('../../../api/models', () => ({
  listConnections: vi.fn(),
  listProfiles: vi.fn(),
  getModelCatalog: vi.fn(),
  getDefaultModels: vi.fn(),
  saveDefaultModels: vi.fn(),
  createConnection: vi.fn(),
  createProfile: vi.fn(),
  updateConnection: vi.fn(),
  updateProfile: vi.fn(),
  deleteConnection: vi.fn(),
  deleteProfile: vi.fn(),
  replaceConnectionApiKey: vi.fn(),
  testProfile: vi.fn(),
}))

const body = () => new DOMWrapper(document.body)

const mountView = () => mount(ModelSettingsTab, { attachTo: document.body })

/** 在 teleported 弹窗中按 data-test 设置输入框值（兼容 input 直接挂载或 wrapper 包裹两种情况）。 */
async function setFieldValue(selector: string, value: string): Promise<void> {
  const field = body().get(selector)
  const input = field.find('input')
  if (input.exists()) {
    await input.setValue(value)
  } else {
    await field.setValue(value)
  }
}

describe('ModelSettingsTab', () => {
  beforeEach(() => {
    vi.mocked(modelsApi.listConnections).mockResolvedValue(JSON.parse(JSON.stringify(mockConnections)))
    vi.mocked(modelsApi.listProfiles).mockResolvedValue(JSON.parse(JSON.stringify(mockProfiles)))
    vi.mocked(modelsApi.getModelCatalog).mockResolvedValue(JSON.parse(JSON.stringify(mockCatalog)))
    vi.mocked(modelsApi.getDefaultModels).mockResolvedValue(JSON.parse(JSON.stringify(mockDefaults)))
    vi.mocked(modelsApi.saveDefaultModels).mockResolvedValue(undefined)
    vi.mocked(modelsApi.createConnection).mockResolvedValue({
      connectionKey: 'conn-new',
      providerCode: 'CUSTOM',
      providerName: 'My Custom',
      displayName: 'My Custom',
      baseUrl: 'https://custom.api/v1',
      apiKeyMask: 'sk-****stom',
      apiKeyConfigured: true,
      status: 'ACTIVE',
      updated: '2026-08-08T00:00:00.000Z',
    })
    vi.mocked(modelsApi.createProfile).mockResolvedValue({
      profileKey: 'prof-new',
      connectionKey: 'conn-new',
      modelType: 'LLM',
      modelName: 'my-model',
      dimensions: null,
      contextWindowLength: null,
      timeoutSeconds: 60,
      temperature: null,
      status: 'ACTIVE',
      lastTestStatus: 'UNTESTED',
      lastTestAt: null,
      updated: '2026-08-08T00:00:00.000Z',
    })
    vi.mocked(modelsApi.updateConnection).mockResolvedValue(undefined)
    vi.mocked(modelsApi.updateProfile).mockResolvedValue(undefined)
    vi.mocked(modelsApi.deleteConnection).mockResolvedValue(true)
    vi.mocked(modelsApi.deleteProfile).mockResolvedValue(true)
    vi.mocked(modelsApi.replaceConnectionApiKey).mockResolvedValue({ apiKeyMask: 'sk-****new1' })
    vi.mocked(modelsApi.testProfile).mockResolvedValue({ status: 'SUCCESS', errorCode: null, dimensions: null })
  })

  it('渲染左右双栏布局', async () => {
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.find('[data-test="left-pane"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="right-pane"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="defaults-section"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="market-search"]').exists()).toBe(true)
  })

  it('点击目录卡片打开连接弹窗并预填 Base URL', async () => {
    const wrapper = mountView()
    await flushPromises()
    // 初始无弹窗
    expect(body().find('[role="dialog"]').exists()).toBe(false)
    // 点击 DeepSeek 目录卡片
    await wrapper.get('[data-test="catalog-card-DEEPSEEK"]').trigger('click')
    await nextTick()
    // 连接弹窗打开
    expect(body().find('[role="dialog"]').exists()).toBe(true)
    // 标题为"添加连接"
    expect(body().text()).toContain('添加连接')
    // Base URL 预填 DeepSeek 默认地址（data-test 直接落在 input 上）
    const baseUrlInput = body().get('[data-test="connection-base-url"]')
    expect((baseUrlInput.element as HTMLInputElement).value).toBe('https://api.deepseek.com/v1')
  })

  it('点击官方链接不触发卡片添加（事件隔离）', async () => {
    const wrapper = mountView()
    await flushPromises()
    expect(body().find('[role="dialog"]').exists()).toBe(false)
    // 点击官方链接 ↗
    await wrapper.get('[data-test="official-link-DEEPSEEK"]').trigger('click')
    await nextTick()
    // @click.stop 阻止冒泡，卡片 click 不触发，弹窗不打开
    expect(body().find('[role="dialog"]').exists()).toBe(false)
    expect(modelsApi.createConnection).not.toHaveBeenCalled()
  })

  it('连接创建 → 确认添加模型 → 档案创建流程', async () => {
    const wrapper = mountView()
    await flushPromises()

    // 通过自定义提供商入口打开连接弹窗（CUSTOM 连接下模型名称为自由输入，便于填写）
    await wrapper.get('[data-test="custom-provider-card"]').trigger('click')
    await nextTick()
    expect(body().find('[role="dialog"]').exists()).toBe(true)

    await setFieldValue('[data-test="connection-display-name"]', 'My Custom')
    await setFieldValue('[data-test="connection-base-url"]', 'https://custom.api/v1')
    await setFieldValue('[data-test="connection-api-key"]', 'sk-custom')
    await body().get('[data-test="save-connection"]').trigger('click')
    await flushPromises()

    // 连接已创建：providerName 用 displayName 兜底
    expect(modelsApi.createConnection).toHaveBeenCalledWith(
      expect.objectContaining({
        providerCode: 'CUSTOM',
        providerName: 'My Custom',
        displayName: 'My Custom',
        baseUrl: 'https://custom.api/v1',
        apiKey: 'sk-custom',
      }),
    )
    // 连接弹窗关闭，弹出"是否立即添加模型"确认
    expect(vi.mocked(message.success)).toHaveBeenCalledWith('连接已创建')
    expect(body().text()).toContain('是否立即添加模型')

    // 点击"添加模型"打开档案弹窗
    await body().get('[data-test="confirm-add-model-yes"]').trigger('click')
    await nextTick()
    expect(body().text()).toContain('添加模型档案')

    // 档案弹窗：连接已预选（CUSTOM），模型名称为自由输入
    await setFieldValue('[data-test="profile-model-name"]', 'my-model')
    await body().get('[data-test="save-profile"]').trigger('click')
    await flushPromises()

    expect(modelsApi.createProfile).toHaveBeenCalledWith(
      expect.objectContaining({
        connectionKey: 'conn-new',
        modelType: 'LLM',
        modelName: 'my-model',
      }),
    )
  })

  it('默认模型下拉展示正确档案标签', async () => {
    const wrapper = mountView()
    await flushPromises()
    // 打开 LLM 默认下拉以校验选项标签
    const llmSelect = wrapper.get('[data-test="default-model-LLM"]')
    await llmSelect.find('.ant-select-selector').trigger('mousedown')
    await nextTick()
    const options = body().findAll('.ant-select-item-option')
    expect(options.length).toBe(1)
    expect(options[0].text()).toContain('deepseek-chat')
    expect(options[0].text()).toContain('DeepSeek 连接')
  })

  it('保存默认配置调用 saveDefaultModels', async () => {
    const wrapper = mountView()
    await flushPromises()
    await wrapper.get('[data-test="save-defaults"]').trigger('click')
    await flushPromises()
    expect(modelsApi.saveDefaultModels).toHaveBeenCalledWith(
      expect.objectContaining({ defaults: expect.objectContaining({ LLM: 'prof-1' }) }),
    )
  })

  it('类型标签过滤模型市场', async () => {
    const wrapper = mountView()
    await flushPromises()
    // 初始展示 3 个提供商卡片（CUSTOM 单独渲染，不计入网格）
    expect(wrapper.find('[data-test="catalog-card-DEEPSEEK"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="catalog-card-QWEN"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="catalog-card-OPENAI"]').exists()).toBe(true)

    // 点击 RERANK 标签：仅 QWEN 有 RERANK 模型
    await wrapper.get('[data-test="type-tag-RERANK"]').trigger('click')
    await nextTick()
    expect(wrapper.find('[data-test="catalog-card-DEEPSEEK"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="catalog-card-QWEN"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="catalog-card-OPENAI"]').exists()).toBe(false)

    // 点击"全部"恢复
    await wrapper.get('[data-test="type-tag-all"]').trigger('click')
    await nextTick()
    expect(wrapper.find('[data-test="catalog-card-DEEPSEEK"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="catalog-card-QWEN"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="catalog-card-OPENAI"]').exists()).toBe(true)
  })

  it('CHAT→LLM 默认值迁移', async () => {
    // 后端返回历史 CHAT 键
    vi.mocked(modelsApi.getDefaultModels).mockResolvedValue({
      defaults: { CHAT: 'prof-1' },
    } as unknown as { defaults: { LLM: string } })
    const wrapper = mountView()
    await flushPromises()
    // 保存时应迁移为 LLM 且无 CHAT 键
    await wrapper.get('[data-test="save-defaults"]').trigger('click')
    await flushPromises()
    expect(modelsApi.saveDefaultModels).toHaveBeenCalledWith(
      expect.objectContaining({
        defaults: expect.objectContaining({ LLM: 'prof-1' }),
      }),
    )
    const savedArg = vi.mocked(modelsApi.saveDefaultModels).mock.calls[0][0]
    expect((savedArg.defaults as Record<string, unknown>).CHAT).toBeUndefined()
    expect((savedArg.defaults as Record<string, unknown>).LLM).toBe('prof-1')
  })
})
