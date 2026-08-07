import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import ModelSettingsView from '../ModelSettingsView.vue'
import { listModelConnections, listModelProfiles, listModelProviderTemplates, createModelConnection, testModelProfile, updateModelConnection } from '../../../api/models'

vi.mock('../../../api/models', () => ({
  listModelProviderTemplates: vi.fn(),
  listModelConnections: vi.fn(),
  listModelProfiles: vi.fn(),
  createModelConnection: vi.fn(),
  createModelProfile: vi.fn(),
  testModelProfile: vi.fn(),
  updateModelConnection: vi.fn(),
  updateModelProfile: vi.fn(),
}))

const templates = [
  { code: 'ALIYUN_DASHSCOPE', providerName: '阿里云百炼', defaultBaseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1' },
  { code: 'VOLCENGINE_ARK', providerName: '火山方舟', defaultBaseUrl: 'https://ark.cn-beijing.volces.com/api/v3' },
  { code: 'TENCENT_HUNYUAN', providerName: '腾讯混元', defaultBaseUrl: 'https://api.hunyuan.cloud.tencent.com/v1' },
  { code: 'ZHIPU_BIGMODEL', providerName: '智谱 BigModel', defaultBaseUrl: 'https://open.bigmodel.cn/api/paas/v4' },
  { code: 'CUSTOM', providerName: '自定义', defaultBaseUrl: null },
]

const connections = [
  { connectionKey: 'conn-1', providerCode: 'ALIYUN_DASHSCOPE', providerName: '阿里云百炼', displayName: '阿里云百炼', baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1', apiKeyMask: '····7K2M', status: 'ACTIVE', lastTestStatus: 'SUCCESS', lastTestAt: '2026-08-06T10:00:00Z' },
]

const profiles = [
  { profileKey: 'prof-1', connectionKey: 'conn-1', modelName: 'qwen-plus', modelType: 'CHAT' as const, dimensions: null, timeoutSeconds: 60, temperature: null, status: 'ACTIVE', lastTestStatus: 'SUCCESS', lastTestAt: '2026-08-06T10:00:00Z' },
  { profileKey: 'prof-2', connectionKey: 'conn-1', modelName: 'text-embedding-v3', modelType: 'EMBEDDING' as const, dimensions: 1024, timeoutSeconds: 60, temperature: null, status: 'ACTIVE', lastTestStatus: 'PENDING', lastTestAt: null },
]

describe('ModelSettingsView', () => {
  beforeEach(() => {
    vi.mocked(listModelProviderTemplates).mockReset()
    vi.mocked(listModelConnections).mockReset()
    vi.mocked(listModelProfiles).mockReset()
    vi.mocked(createModelConnection).mockReset()
    vi.mocked(testModelProfile).mockReset()
    vi.mocked(updateModelConnection).mockReset()
    vi.mocked(listModelProviderTemplates).mockResolvedValue(templates)
    vi.mocked(listModelConnections).mockResolvedValue(connections)
    vi.mocked(listModelProfiles).mockResolvedValue(profiles)
  })

  it('加载后展示连接列表、模板和档案', async () => {
    const wrapper = mount(ModelSettingsView)
    await flushPromises()
    expect(listModelProviderTemplates).toHaveBeenCalled()
    expect(wrapper.text()).toContain('阿里云百炼')
    expect(wrapper.text()).toContain('qwen-plus')
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

  it('点击添加连接后展示模板选择且预填厂商和 Base URL', async () => {
    const wrapper = mount(ModelSettingsView)
    await flushPromises()
    await wrapper.find('button.primary-action').trigger('click')
    await flushPromises()
    const drawer = wrapper.find('.settings-drawer')
    expect(drawer.exists()).toBe(true)
    const select = drawer.find('select')
    expect(select.findAll('option')).toHaveLength(5)
    // 选择阿里云百炼模板
    await select.setValue('ALIYUN_DASHSCOPE')
    expect(drawer.find('input[required]').element).toBeDefined()
    // 厂商名称应被预填
    const inputs = drawer.findAll('input')
    expect(inputs[0].element.value).toBe('阿里云百炼')
  })

  it('自定义模板的 Base URL 为空', async () => {
    const wrapper = mount(ModelSettingsView)
    await flushPromises()
    await wrapper.find('button.primary-action').trigger('click')
    await flushPromises()
    const select = wrapper.find('.settings-drawer select')
    await select.setValue('CUSTOM')
    const urlInput = wrapper.find('.settings-drawer input[type="url"]')
    expect((urlInput.element as HTMLInputElement).value).toBe('')
  })

  it('替换 Key 流程：展示替换输入框，提交后调用 updateModelConnection', async () => {
    const wrapper = mount(ModelSettingsView)
    await flushPromises()
    // 点击"替换 Key"
    const replaceBtn = wrapper.findAll('button').find(btn => btn.text().includes('替换 Key'))
    expect(replaceBtn).toBeDefined()
    await replaceBtn!.trigger('click')
    expect(wrapper.find('.inline-form').exists()).toBe(true)
    // 输入新 Key
    const keyInput = wrapper.find('.inline-form input[type="password"]')
    await keyInput.setValue('new-secret-key')
    // 点击保存
    vi.mocked(updateModelConnection).mockResolvedValue({ ...connections[0], apiKeyMask: '····9X3Z' })
    const saveBtn = wrapper.find('.inline-form button')
    await saveBtn.trigger('click')
    await flushPromises()
    expect(updateModelConnection).toHaveBeenCalledWith('conn-1', expect.objectContaining({ apiKey: 'new-secret-key' }))
  })

  it('测试模型后展示安全化结果', async () => {
    const wrapper = mount(ModelSettingsView)
    await flushPromises()
    vi.mocked(testModelProfile).mockResolvedValue({ status: 'SUCCESS', errorCode: null, dimensions: null })
    const testBtn = wrapper.findAll('button').find(btn => btn.text().includes('测试模型'))
    await testBtn!.trigger('click')
    await flushPromises()
    expect(testModelProfile).toHaveBeenCalledWith('prof-1')
    expect(wrapper.text()).toContain('成功')
  })

  it('测试失败展示用户语言而非异常栈', async () => {
    const wrapper = mount(ModelSettingsView)
    await flushPromises()
    vi.mocked(testModelProfile).mockResolvedValue({ status: 'AUTH_FAILED', errorCode: null, dimensions: null })
    const testBtn = wrapper.findAll('button').find(btn => btn.text().includes('测试模型'))
    await testBtn!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('认证失败，请检查 API Key')
    expect(wrapper.text()).not.toMatch(/exception|stacktrace|java\./i)
  })

  it('空状态引导用户先添加连接', async () => {
    vi.mocked(listModelConnections).mockResolvedValue([])
    vi.mocked(listModelProfiles).mockResolvedValue([])
    const wrapper = mount(ModelSettingsView)
    await flushPromises()
    expect(wrapper.text()).toContain('先添加一个 Provider 连接')
  })

  it('不展示 Sa-Token、Redis 等技术名词', async () => {
    const wrapper = mount(ModelSettingsView)
    await flushPromises()
    expect(wrapper.text()).not.toMatch(/sa-token|redis|mybatis|datasource/i)
  })
})
