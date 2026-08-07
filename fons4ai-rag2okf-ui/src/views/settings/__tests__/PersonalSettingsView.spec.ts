import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick, ref } from 'vue'

import PersonalSettingsView from '../PersonalSettingsView.vue'
import { getCurrentUser, updateCurrentUser } from '../../../api/auth'
import { setAuthenticationToken } from '../../../api/http'
import { hasRuntimeSession } from '../../../stores/session'

const setTheme = vi.fn()
const mode = ref<'light' | 'dark' | 'system'>('system')

vi.mock('../../../api/auth', () => ({
  login: vi.fn(),
  getCurrentUser: vi.fn(),
  logout: vi.fn(),
  updateCurrentUser: vi.fn(),
}))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))
vi.mock('../../../composables/useTheme', () => ({
  useTheme: () => ({ mode, setTheme }),
}))

describe('PersonalSettingsView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    hasRuntimeSession.value = true
    setAuthenticationToken('runtime-only-token')
    setTheme.mockReset()
    mode.value = 'system'
    vi.mocked(getCurrentUser).mockReset()
    vi.mocked(updateCurrentUser).mockReset()
    vi.mocked(getCurrentUser).mockResolvedValue({
      userKey: 'u1',
      email: 'me@example.com',
      displayName: 'Me',
      avatarUrl: '',
      preferenceJson: '{}',
      workspaceKey: 'ws-1',
      workspaceName: '个人工作空间',
      workspaceRole: 'ADMIN',
    })
  })

  it('加载已保存的主题与分块偏好并在挂载时应用主题', async () => {
    vi.mocked(getCurrentUser).mockResolvedValue({
      userKey: 'u1',
      email: 'me@example.com',
      displayName: 'Me',
      avatarUrl: '',
      preferenceJson: JSON.stringify({ theme: 'dark', defaultChunkSize: 1000, defaultChunkOverlap: 100 }),
      workspaceKey: 'ws-1',
      workspaceName: '个人工作空间',
      workspaceRole: 'ADMIN',
    })
    const wrapper = mount(PersonalSettingsView)
    await flushPromises()
    expect(setTheme).toHaveBeenCalledWith('dark')
    const select = wrapper.find('select')
    expect((select.element as HTMLSelectElement).value).toBe('dark')
    const inputs = wrapper.findAll('input[type="number"]')
    expect((inputs[0].element as HTMLInputElement).value).toBe('1000')
    expect((inputs[1].element as HTMLInputElement).value).toBe('100')
  })

  it('保存偏好时将表单序列化为 preferenceJson 并调用 saveProfile', async () => {
    vi.mocked(updateCurrentUser).mockResolvedValue({
      userKey: 'u1',
      email: 'me@example.com',
      displayName: 'Me',
      avatarUrl: '',
      preferenceJson: JSON.stringify({ theme: 'light', defaultChunkSize: 1200, defaultChunkOverlap: 120 }),
      workspaceKey: 'ws-1',
      workspaceName: '个人工作空间',
      workspaceRole: 'ADMIN',
    })
    const wrapper = mount(PersonalSettingsView)
    await flushPromises()
    const inputs = wrapper.findAll('input[type="number"]')
    await inputs[0].setValue('1200')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(updateCurrentUser).toHaveBeenCalledTimes(1)
    const callArg = vi.mocked(updateCurrentUser).mock.calls[0][0]
    const prefs = JSON.parse(callArg.preferenceJson)
    expect(prefs.defaultChunkSize).toBe(1200)
    expect(prefs.defaultChunkOverlap).toBe(120)
    expect(wrapper.text()).toContain('个人偏好已保存')
  })

  it('分块重叠量大于等于分块大小时拒绝保存并提示错误', async () => {
    const wrapper = mount(PersonalSettingsView)
    await flushPromises()
    const inputs = wrapper.findAll('input[type="number"]')
    await inputs[0].setValue('500')
    await nextTick()
    await inputs[1].setValue('500')
    await nextTick()
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(updateCurrentUser).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('分块重叠量必须小于分块大小')
  })

  it('展示配置层级地图包含个人偏好、知识库设置和模型设置三层', async () => {
    const wrapper = mount(PersonalSettingsView)
    await flushPromises()
    const mapSection = wrapper.find('.settings-map')
    expect(mapSection.exists()).toBe(true)
    const articles = mapSection.findAll('article')
    expect(articles).toHaveLength(3)
    expect(articles[0].text()).toContain('个人偏好')
    expect(articles[1].text()).toContain('知识库设置')
    expect(articles[2].text()).toContain('模型设置')
  })
})
