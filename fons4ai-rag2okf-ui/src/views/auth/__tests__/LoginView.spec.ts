import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { Input, InputPassword } from 'ant-design-vue'

import LoginView from '../LoginView.vue'
import { getCurrentUser, login } from '../../../api/auth'

const replace = vi.fn()
vi.mock('../../../api/auth', () => ({ login: vi.fn(), getCurrentUser: vi.fn(), logout: vi.fn(), updateCurrentUser: vi.fn() }))
vi.mock('vue-router', () => ({ useRoute: () => ({ query: {} }), useRouter: () => ({ replace }) }))

describe('LoginView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    replace.mockReset()
    vi.mocked(login).mockResolvedValue({ token: 'runtime-only-token' })
    vi.mocked(getCurrentUser).mockResolvedValue({ userKey: 'u1', email: 'me@example.com', displayName: 'Me', avatarUrl: '', preferenceJson: '{}', workspaceKey: 'ws-1', workspaceName: '个人工作空间', workspaceRole: 'ADMIN' })
  })

  it('uses email/password login and does not persist its token in browser storage', async () => {
    const wrapper = mount(LoginView)
    wrapper.findAllComponents(Input).find((input) => input.props('autocomplete') === 'email')?.vm.$emit('update:value', 'Me@Example.com')
    wrapper.findComponent(InputPassword).vm.$emit('update:value', 'password')
    await nextTick()
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(login).toHaveBeenCalledWith({ email: 'Me@Example.com', password: 'password', rememberMe: false })
    expect(replace).toHaveBeenCalledWith('/knowledge-bases')
    // token 持久化到 localStorage 以支持刷新页面保持登录
    expect(localStorage.getItem('rag2okf_auth_token')).toBe('runtime-only-token')
    expect(sessionStorage.getItem('rag2okf_auth_token')).toBeNull()
    // 不存储密码
    expect(localStorage.getItem('password')).toBeNull()
  })
})
