import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { Input, InputPassword, message } from 'ant-design-vue'

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

  async function fillRegistrationForm(
    wrapper: ReturnType<typeof mount>,
    values: { email: string; password: string; confirmPassword: string; displayName?: string },
  ): Promise<void> {
    const plainInputs = wrapper.findAllComponents(Input)
    const passwordInputs = wrapper.findAllComponents(InputPassword)
    plainInputs.find((input) => input.props('autocomplete') === 'email')?.vm.$emit('update:value', values.email)
    passwordInputs[0].vm.$emit('update:value', values.password)
    passwordInputs[1].vm.$emit('update:value', values.confirmPassword)
    if (values.displayName !== undefined) {
      plainInputs.find((input) => input.props('autocomplete') === 'nickname')?.vm.$emit('update:value', values.displayName)
    }
    await nextTick()
  }

  it('使用邮箱密码注册成功后跳转到知识库且不持久化 token', async () => {
    const wrapper = mount(RegisterView)
    await fillRegistrationForm(wrapper, {
      email: 'new@example.com',
      password: 'secure-pass',
      confirmPassword: 'secure-pass',
      displayName: '新用户',
    })
    await (wrapper.vm as unknown as { submit: () => Promise<void> }).submit()
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
    await fillRegistrationForm(wrapper, {
      email: 'new@example.com',
      password: 'secure-pass',
      confirmPassword: 'different-pass',
    })
    await (wrapper.vm as unknown as { submit: () => Promise<void> }).submit()
    await flushPromises()
    expect(register).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('两次输入的密码不一致')
  })

  it('注册失败时显示安全化错误且不暴露邮箱是否已注册', async () => {
    const { ApiRequestError } = await import('../../../api/http')
    vi.mocked(register).mockRejectedValue(new ApiRequestError('参数错误', 400))
    const wrapper = mount(RegisterView)
    await fillRegistrationForm(wrapper, {
      email: 'existing@example.com',
      password: 'secure-pass',
      confirmPassword: 'secure-pass',
    })
    await (wrapper.vm as unknown as { submit: () => Promise<void> }).submit()
    await flushPromises()
    expect(message.error).toHaveBeenCalledWith('注册失败，请更换邮箱或密码后重试。')
    expect(wrapper.text()).not.toContain('existing@example.com')
  })

  it('注册频控时提示稍后重试', async () => {
    const { ApiRequestError } = await import('../../../api/http')
    vi.mocked(register).mockRejectedValue(new ApiRequestError('请求过多', 429))
    const wrapper = mount(RegisterView)
    await fillRegistrationForm(wrapper, {
      email: 'new@example.com',
      password: 'secure-pass',
      confirmPassword: 'secure-pass',
    })
    await (wrapper.vm as unknown as { submit: () => Promise<void> }).submit()
    await flushPromises()
    expect(message.error).toHaveBeenCalledWith('注册请求过于频繁，请稍后再试。')
  })

  it('页面不出现邮箱验证状态', async () => {
    const wrapper = mount(RegisterView)
    const bodyText = wrapper.text()
    expect(bodyText).not.toMatch(/邮箱已验证|邮箱未验证|email.?verified/i)
  })

  it('不展示服务条款勾选项且无需额外确认即可提交', () => {
    const wrapper = mount(RegisterView)
    expect(wrapper.find('input[type="checkbox"]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('我已阅读并同意服务条款')
  })
})
