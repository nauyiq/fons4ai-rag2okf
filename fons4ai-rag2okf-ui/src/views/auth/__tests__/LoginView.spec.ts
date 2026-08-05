import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

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
    vi.mocked(getCurrentUser).mockResolvedValue({ userKey: 'u1', email: 'me@example.com', displayName: 'Me', avatarUrl: '', preferenceJson: '{}' })
  })

  it('uses email/password login and does not persist its token in browser storage', async () => {
    const wrapper = mount(LoginView)
    const inputs = wrapper.findAll('input')
    await inputs[0].setValue(' Me@Example.com ')
    await inputs[1].setValue('password')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(login).toHaveBeenCalledWith({ email: 'Me@Example.com', password: 'password', rememberMe: false })
    expect(replace).toHaveBeenCalledWith('/knowledge-bases')
    expect(localStorage.getItem('token')).toBeNull()
    expect(sessionStorage.getItem('token')).toBeNull()
  })
})
