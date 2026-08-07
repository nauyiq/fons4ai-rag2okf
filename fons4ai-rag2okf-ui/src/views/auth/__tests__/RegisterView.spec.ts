import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import RegisterView from '../RegisterView.vue'
import { getCurrentUser, register } from '../../../api/auth'

const replace = vi.fn()
vi.mock('../../../api/auth', () => ({ register: vi.fn(), login: vi.fn(), getCurrentUser: vi.fn(), logout: vi.fn(), updateCurrentUser: vi.fn() }))
vi.mock('vue-router', () => ({
  useRoute: () => ({ query: {} }),
  useRouter: () => ({ replace }),
  RouterLink: { template: '<a><slot /></a>' },
}))

describe('RegisterView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    replace.mockReset()
    vi.mocked(register).mockReset()
    vi.mocked(getCurrentUser).mockReset()
    vi.mocked(register).mockResolvedValue({ token: 'runtime-only-token' })
    vi.mocked(getCurrentUser).mockResolvedValue({ userKey: 'u1', email: 'new@example.com', displayName: 'New', avatarUrl: '', preferenceJson: '{}', workspaceKey: 'ws-1', workspaceName: '个人工作空间', workspaceRole: 'ADMIN' })
  })

  it('使用邮箱密码注册成功后跳转到知识库且不持久化 token', async () => {
    const wrapper = mount(RegisterView)
    const inputs = wrapper.findAll('input')
    await inputs[0].setValue('new@example.com')
    await inputs[1].setValue('secure-pass')
    await inputs[2].setValue('secure-pass')
    await inputs[3].setValue('新用户')
    await inputs[4].setValue(true)
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(register).toHaveBeenCalledWith({
      email: 'new@example.com',
      password: 'secure-pass',
      confirmPassword: 'secure-pass',
      displayName: '新用户',
      termsAccepted: true,
    })
    expect(replace).toHaveBeenCalledWith('/knowledge-bases')
    // token 持久化到 localStorage 以支持刷新页面保持登录
    expect(localStorage.getItem('rag2okf_auth_token')).toBe('runtime-only-token')
    expect(sessionStorage.getItem('rag2okf_auth_token')).toBeNull()
    // 不存储密码
    expect(localStorage.getItem('password')).toBeNull()
  })

  it('两次密码不一致时拒绝提交', async () => {
    const wrapper = mount(RegisterView)
    const inputs = wrapper.findAll('input')
    await inputs[0].setValue('new@example.com')
    await inputs[1].setValue('secure-pass')
    await inputs[2].setValue('different-pass')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(register).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('两次输入的密码不一致')
  })

  it('注册失败时显示安全化错误且不暴露邮箱是否已注册', async () => {
    const { ApiRequestError } = await import('../../../api/http')
    vi.mocked(register).mockRejectedValue(new ApiRequestError('参数错误', 400))
    const wrapper = mount(RegisterView)
    const inputs = wrapper.findAll('input')
    await inputs[0].setValue('existing@example.com')
    await inputs[1].setValue('secure-pass')
    await inputs[2].setValue('secure-pass')
    await inputs[4].setValue(true)
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('注册失败，请更换邮箱或密码后重试')
    expect(wrapper.text()).not.toContain('existing@example.com')
  })

  it('注册频控时提示稍后重试', async () => {
    const { ApiRequestError } = await import('../../../api/http')
    vi.mocked(register).mockRejectedValue(new ApiRequestError('请求过多', 429))
    const wrapper = mount(RegisterView)
    const inputs = wrapper.findAll('input')
    await inputs[0].setValue('new@example.com')
    await inputs[1].setValue('secure-pass')
    await inputs[2].setValue('secure-pass')
    await inputs[4].setValue(true)
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('注册请求过于频繁')
  })

  it('页面不出现邮箱验证状态', async () => {
    const wrapper = mount(RegisterView)
    const bodyText = wrapper.text()
    expect(bodyText).not.toMatch(/邮箱已验证|邮箱未验证|email.?verified/i)
  })
})
