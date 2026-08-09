import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { Checkbox } from 'ant-design-vue'

import LoginView from '../LoginView.vue'
import RegisterView from '../RegisterView.vue'
import { getCurrentUser, login, register } from '../../../api/auth'

const replace = vi.fn()
vi.mock('../../../api/auth', () => ({
  register: vi.fn(),
  login: vi.fn(),
  getCurrentUser: vi.fn(),
  logout: vi.fn(),
  updateCurrentUser: vi.fn(),
}))
vi.mock('vue-router', () => ({
  useRoute: () => ({ query: {} }),
  useRouter: () => ({ replace }),
  RouterLink: { template: '<a><slot /></a>' },
}))

describe('邮箱注册边界（T029）', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    replace.mockReset()
    vi.mocked(register).mockReset()
    vi.mocked(login).mockReset()
    vi.mocked(getCurrentUser).mockReset()
    vi.mocked(register).mockResolvedValue({ token: 'runtime-only-token' })
    vi.mocked(login).mockResolvedValue({ token: 'runtime-only-token' })
    vi.mocked(getCurrentUser).mockResolvedValue({
      userKey: 'u1',
      email: 'new@example.com',
      displayName: 'New',
      avatarUrl: '',
      preferenceJson: '{}',
      workspaceKey: 'ws-1',
      workspaceName: '个人工作空间',
      workspaceRole: 'ADMIN',
    })
  })

  /** a-checkbox 通过 v-model:checked 绑定，直接 emit update:checked 确保表单模型更新 */
  async function checkTerms(wrapper: ReturnType<typeof mount>): Promise<void> {
    wrapper.findComponent(Checkbox).vm.$emit('update:checked', true)
    await nextTick()
  }

  describe('零邮箱验证残留', () => {
    it('注册页不展示邮箱验证状态', () => {
      const wrapper = mount(RegisterView)
      const text = wrapper.text()
      expect(text).not.toMatch(/邮箱已验证|邮箱未验证|email.?verified|verify.?email/i)
    })

    it('登录页不展示邮箱验证状态', () => {
      const wrapper = mount(LoginView)
      const text = wrapper.text()
      expect(text).not.toMatch(/邮箱已验证|邮箱未验证|email.?verified|verify.?email/i)
    })

    it('注册页不展示邮箱验证码输入框', () => {
      const wrapper = mount(RegisterView)
      const inputs = wrapper.findAll('input')
      const inputNames = inputs.map((i) => i.attributes('name') ?? i.attributes('autocomplete') ?? '')
      expect(inputNames).not.toContain('verification-code')
      expect(inputNames).not.toContain('verify-code')
      expect(inputNames).not.toContain('sms-code')
      // 注册页只有 5 个输入框：邮箱、密码、确认密码、展示名称、条款同意
      expect(inputs).toHaveLength(5)
    })
  })

  describe('零密码找回入口', () => {
    it('登录页不提供密码找回或重置入口', () => {
      const wrapper = mount(LoginView)
      const text = wrapper.text()
      const html = wrapper.html()
      expect(text).not.toMatch(/忘记密码|找回密码|重置密码|reset.?password|forgot.?password/i)
      expect(html).not.toMatch(/\/auth\/forgot|\/auth\/reset|\/auth\/recover|\/auth\/password-reset/i)
    })

    it('注册页不提供密码找回或重置入口', () => {
      const wrapper = mount(RegisterView)
      const text = wrapper.text()
      const html = wrapper.html()
      expect(text).not.toMatch(/忘记密码|找回密码|重置密码|reset.?password|forgot.?password/i)
      expect(html).not.toMatch(/\/auth\/forgot|\/auth\/reset|\/auth\/recover|\/auth\/password-reset/i)
    })

    it('登录页和注册页均不展示邮箱发送相关文案', () => {
      const loginWrapper = mount(LoginView)
      const registerWrapper = mount(RegisterView)
      for (const text of [loginWrapper.text(), registerWrapper.text()]) {
        expect(text).not.toMatch(/验证邮件|发送邮件|邮件已发|check.?your.?email|verification.?email/i)
      }
    })
  })

  describe('注册冲突防枚举', () => {
    it('注册冲突时显示安全化错误，不暴露邮箱是否已注册', async () => {
      const { ApiRequestError } = await import('../../../api/http')
      vi.mocked(register).mockRejectedValue(new ApiRequestError('参数错误', 400))
      const wrapper = mount(RegisterView)
      const inputs = wrapper.findAll('input')
      await inputs[0].setValue('Existing@Example.com')
      await inputs[1].setValue('secure-pass')
      await inputs[2].setValue('secure-pass')
      await checkTerms(wrapper)
      await wrapper.get('form').trigger('submit')
      await flushPromises()

      const errorText = wrapper.text()
      // 安全化错误提示
      expect(errorText).toContain('注册失败')
      // 不暴露输入的邮箱地址
      expect(errorText).not.toContain('Existing@Example.com')
      expect(errorText).not.toContain('existing@example.com')
      // 不暴露"已注册"等差异化信息
      expect(errorText).not.toMatch(/已注册|已存在|already.?registered/i)
    })
  })

  describe('登录统一错误', () => {
    it('登录失败时显示统一错误，不区分账号不存在或密码错误', async () => {
      const { ApiRequestError } = await import('../../../api/http')
      vi.mocked(login).mockRejectedValue(new ApiRequestError('未认证', 401))
      const wrapper = mount(LoginView)
      const inputs = wrapper.findAll('input')
      await inputs[0].setValue('unknown@example.com')
      await inputs[1].setValue('any-pass')
      await wrapper.get('form').trigger('submit')
      await flushPromises()

      const errorText = wrapper.text()
      // 不暴露差异化的失败原因
      expect(errorText).not.toMatch(/账号不存在|用户不存在|密码错误|账号已禁用|not.?found|wrong.?password/i)
      // 不回显输入的邮箱
      expect(errorText).not.toContain('unknown@example.com')
    })
  })

  describe('敏感信息不进入浏览器存储', () => {
    it('注册成功后 token 持久化到 localStorage，密码不存储', async () => {
      const wrapper = mount(RegisterView)
      const inputs = wrapper.findAll('input')
      await inputs[0].setValue('new@example.com')
      await inputs[1].setValue('secure-pass')
      await inputs[2].setValue('secure-pass')
      await inputs[3].setValue('新用户')
      await checkTerms(wrapper)
      await wrapper.get('form').trigger('submit')
      await flushPromises()

      // token 持久化到 localStorage 以支持刷新页面保持登录
      expect(localStorage.getItem('rag2okf_auth_token')).toBe('runtime-only-token')
      expect(sessionStorage.getItem('rag2okf_auth_token')).toBeNull()
      // 不存储密码
      expect(localStorage.getItem('password')).toBeNull()
      expect(sessionStorage.getItem('password')).toBeNull()
    })
  })
})
