import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import ModelSettingsView from '../ModelSettingsView.vue'
import { listModelConfigs, listModelProviderTemplates, createModelConfig, testModelConfig, updateModelConfig, replaceModelApiKey } from '../../../api/models'

vi.mock('../../../api/models', () => ({
  listModelProviderTemplates: vi.fn(),
  listModelConfigs: vi.fn(),
  createModelConfig: vi.fn(),
  testModelConfig: vi.fn(),
  updateModelConfig: vi.fn(),
  replaceModelApiKey: vi.fn(),
  deleteModelConfig: vi.fn(),
}))

const templates = [
  { code: 'ALIYUN_DASHSCOPE', providerName: '阿里云百炼', defaultBaseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1' },
  { code: 'VOLCENGINE_ARK', providerName: '火山方舟', defaultBaseUrl: 'https://ark.cn-beijing.volces.com/api/v3' },
  { code: 'TENCENT_HUNYUAN', providerName: '腾讯混元', defaultBaseUrl: 'https://api.hunyuan.cloud.tencent.com/v1' },
  { code: 'ZHIPU_BIGMODEL', providerName: '智谱 BigModel', defaultBaseUrl: 'https://open.bigmodel.cn/api/paas/v4' },
  { code: 'CUSTOM', providerName: '自定义', defaultBaseUrl: null },
]

const configs = [
  {
    modelConfigKey: 'mc-1', providerCode: 'ALIYUN_DASHSCOPE', providerName: '阿里云百炼', displayName: '阿里云百炼',
    baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1', apiKeyMask: '····7K2M', apiKeyConfigured: true,
    modelType: 'CHAT' as const, modelName: 'qwen-plus', dimensions: null, contextWindowLength: null,
    timeoutSeconds: 60, temperature: null, status: 'ACTIVE' as const, lastTestStatus: 'SUCCESS', lastTestAt: '2026-08-06T10:00:00Z', updated: '2026-08-06T10:00:00Z',
  },
  {
    modelConfigKey: 'mc-2', providerCode: 'ALIYUN_DASHSCOPE', providerName: '阿里云百炼', displayName: '阿里云百炼-Embedding',
    baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1', apiKeyMask: '····7K2M', apiKeyConfigured: true,
    modelType: 'EMBEDDING' as const, modelName: 'text-embedding-v3', dimensions: 1024, contextWindowLength: null,
    timeoutSeconds: 60, temperature: null, status: 'ACTIVE' as const, lastTestStatus: 'PENDING', lastTestAt: null, updated: '2026-08-06T10:00:00Z',
  },
]

/** 工具：在当前 wrapper 中找到第一个文本包含 subStr 的按钮 */
function findButtonByText(wrapper: ReturnType<typeof mount>, subStr: string) {
  return wrapper.findAll('button').find(btn => btn.text().includes(subStr))
}

describe('ModelSettingsView', () => {
  beforeEach(() => {
    vi.mocked(listModelProviderTemplates).mockReset()
    vi.mocked(listModelConfigs).mockReset()
    vi.mocked(createModelConfig).mockReset()
    vi.mocked(testModelConfig).mockReset()
    vi.mocked(updateModelConfig).mockReset()
    vi.mocked(replaceModelApiKey).mockReset()
    vi.mocked(listModelProviderTemplates).mockResolvedValue(templates)
    vi.mocked(listModelConfigs).mockResolvedValue(configs)
  })

  it('加载后展示模型配置列表和模板', async () => {
    const wrapper = mount(ModelSettingsView)
    await flushPromises()
    expect(listModelProviderTemplates).toHaveBeenCalled()
    expect(wrapper.text()).toContain('阿里云百炼')
    // 默认选中第一个配置，展示其 modelName
    expect(wrapper.text()).toContain('qwen-plus')
    // 切换到第二个配置后展示其 modelName
    const configButtons = wrapper.findAll('.model-row')
    expect(configButtons.length).toBe(2)
    await configButtons[1].trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('text-embedding-v3')
  })

  it('展示 API Key 掩码但不提供显示原值按钮', async () => {
    const wrapper = mount(ModelSettingsView)
    await flushPromises()
    expect(wrapper.text()).toContain('····7K2M')
    expect(wrapper.text()).not.toMatch(/显示.*原.*值|查看.*原.*Key|复制.*Key/i)
  })

  it('展示测试费用提示', async () => {
    const wrapper = mount(ModelSettingsView)
    await flushPromises()
    expect(wrapper.text()).toContain('会向模型厂商发送固定测试文本，可能产生少量费用')
  })

  it('点击添加模型后展示模板选择且预填厂商和 Base URL', async () => {
    const wrapper = mount(ModelSettingsView)
    await flushPromises()
    await wrapper.find('button.primary-action').trigger('click')
    await flushPromises()
    // 合并表单渲染在 AppDialog 内
    const form = wrapper.find('.model-form')
    expect(form.exists()).toBe(true)
    const select = form.find('select')
    expect(select.findAll('option')).toHaveLength(5)
    await select.setValue('ALIYUN_DASHSCOPE')
    const inputs = form.findAll('input')
    expect(inputs[0].element.value).toBe('阿里云百炼')
  })

  it('自定义模板的 Base URL 为空', async () => {
    const wrapper = mount(ModelSettingsView)
    await flushPromises()
    await wrapper.find('button.primary-action').trigger('click')
    await flushPromises()
    const select = wrapper.find('.model-form select')
    await select.setValue('CUSTOM')
    const urlInput = wrapper.find('.model-form input[type="url"]')
    expect((urlInput.element as HTMLInputElement).value).toBe('')
  })

  it('替换 Key 流程：展示替换输入框，提交后调用 replaceModelApiKey', async () => {
    const wrapper = mount(ModelSettingsView)
    await flushPromises()
    const replaceBtn = findButtonByText(wrapper, '替换 Key')
    expect(replaceBtn).toBeDefined()
    await replaceBtn!.trigger('click')
    expect(wrapper.find('.inline-form').exists()).toBe(true)
    const keyInput = wrapper.find('.inline-form input[type="password"]')
    await keyInput.setValue('new-secret-key')
    vi.mocked(replaceModelApiKey).mockResolvedValue({ apiKeyMask: '····9X3Z' })
    const saveBtn = wrapper.find('.inline-form button')
    await saveBtn.trigger('click')
    await flushPromises()
    expect(replaceModelApiKey).toHaveBeenCalledWith('mc-1', 'new-secret-key')
  })

  it('测试模型后展示安全化结果', async () => {
    const wrapper = mount(ModelSettingsView)
    await flushPromises()
    vi.mocked(testModelConfig).mockResolvedValue({ status: 'SUCCESS', errorCode: null, dimensions: null })
    const testBtn = findButtonByText(wrapper, '测试模型')
    await testBtn!.trigger('click')
    await flushPromises()
    expect(testModelConfig).toHaveBeenCalledWith('mc-1')
    expect(wrapper.text()).toContain('成功')
  })

  it('测试失败展示用户语言而非异常栈', async () => {
    const wrapper = mount(ModelSettingsView)
    await flushPromises()
    vi.mocked(testModelConfig).mockResolvedValue({ status: 'AUTH_FAILED', errorCode: null, dimensions: null })
    const testBtn = findButtonByText(wrapper, '测试模型')
    await testBtn!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('认证失败，请检查 API Key')
    expect(wrapper.text()).not.toMatch(/exception|stacktrace|java\./i)
  })

  it('空状态引导用户先添加模型配置', async () => {
    vi.mocked(listModelConfigs).mockResolvedValue([])
    const wrapper = mount(ModelSettingsView)
    await flushPromises()
    expect(wrapper.text()).toContain('先添加一个模型配置')
  })

  it('不展示 Sa-Token、Redis 等技术名词', async () => {
    const wrapper = mount(ModelSettingsView)
    await flushPromises()
    expect(wrapper.text()).not.toMatch(/sa-token|redis|mybatis|datasource/i)
  })

  // ===== T011 新增：合并表单（AppDialog + 基础信息 + 高级参数） =====

  it('新增模型时在一个 AppDialog 中看到所有字段（基础信息+高级参数入口）', async () => {
    const wrapper = mount(ModelSettingsView)
    await flushPromises()
    await wrapper.find('button.primary-action').trigger('click')
    await flushPromises()
    // AppDialog 渲染为 modal-panel
    const dialog = wrapper.find('.modal-panel')
    expect(dialog.exists()).toBe(true)
    const form = dialog.find('.model-form')
    expect(form.exists()).toBe(true)
    // 基础信息字段全部存在
    const formText = form.text()
    expect(formText).toContain('模板')
    expect(formText).toContain('厂商名称')
    expect(formText).toContain('显示名称')
    expect(formText).toContain('Base URL')
    expect(formText).toContain('API Key')
    expect(formText).toContain('类型')
    expect(formText).toContain('模型名称')
    // 高级参数折叠入口存在
    expect(formText).toContain('高级参数')
  })

  it('展开高级参数时看到上下文窗口长度、超时秒数、温度、向量维度（EMBEDDING 时）', async () => {
    const wrapper = mount(ModelSettingsView)
    await flushPromises()
    await wrapper.find('button.primary-action').trigger('click')
    await flushPromises()
    // 折叠时不渲染高级参数字段
    expect(wrapper.text()).not.toContain('上下文窗口长度')
    // 选择 EMBEDDING 类型（第二个 select 是类型选择）
    const selects = wrapper.findAll('.model-form select')
    await selects[1].setValue('EMBEDDING')
    // 展开高级参数
    const advBtn = findButtonByText(wrapper, '高级参数')
    expect(advBtn).toBeDefined()
    await advBtn!.trigger('click')
    await flushPromises()
    const advText = wrapper.find('.advanced-section').text()
    expect(advText).toContain('上下文窗口长度')
    expect(advText).toContain('超时秒数')
    expect(advText).toContain('温度')
    expect(advText).toContain('向量维度')
  })

  it('编辑配置时 API Key 不显示原值，通过"替换 Key"入口', async () => {
    const wrapper = mount(ModelSettingsView)
    await flushPromises()
    const editBtn = findButtonByText(wrapper, '编辑配置')
    expect(editBtn).toBeDefined()
    await editBtn!.trigger('click')
    await flushPromises()
    const form = wrapper.find('.model-form')
    expect(form.exists()).toBe(true)
    // 编辑模式不显示 API Key 输入框
    expect(form.find('input[type="password"]').exists()).toBe(false)
    // 表单内提示使用"替换 Key"入口
    expect(form.text()).toContain('替换 Key')
  })

  it('提交新增时调用 createModelConfig 单步 API 且参数包含 apiKey', async () => {
    vi.mocked(createModelConfig).mockResolvedValue(configs[0])
    const wrapper = mount(ModelSettingsView)
    await flushPromises()
    await wrapper.find('button.primary-action').trigger('click')
    await flushPromises()
    const form = wrapper.find('.model-form')
    // 选择阿里云模板预填厂商和 Base URL
    const selects = form.findAll('select')
    await selects[0].setValue('ALIYUN_DASHSCOPE')
    // 填写显示名称、API Key、模型名称
    const inputs = form.findAll('input')
    await inputs[1].setValue('测试模型')
    await inputs[3].setValue('sk-test-key')
    await inputs[4].setValue('qwen-plus')
    await form.trigger('submit')
    await flushPromises()
    expect(createModelConfig).toHaveBeenCalledTimes(1)
    const call = vi.mocked(createModelConfig).mock.calls[0][0]
    // 单步 API 调用，apiKey 包含在输入中
    expect(call.apiKey).toBe('sk-test-key')
    expect(call.providerCode).toBe('ALIYUN_DASHSCOPE')
    expect(call.modelName).toBe('qwen-plus')
    expect(call.modelType).toBe('CHAT')
  })
})
