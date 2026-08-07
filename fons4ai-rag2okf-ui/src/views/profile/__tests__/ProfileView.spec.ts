import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import ProfileView from '../ProfileView.vue'
import { getCurrentUser, updateCurrentUser } from '../../../api/auth'
import { setAuthenticationToken } from '../../../api/http'
import { hasRuntimeSession } from '../../../stores/session'

vi.mock('../../../api/auth', () => ({ login: vi.fn(), getCurrentUser: vi.fn(), logout: vi.fn(), updateCurrentUser: vi.fn() }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))

describe('ProfileView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    hasRuntimeSession.value = true
    setAuthenticationToken('runtime-only-token')
    vi.mocked(getCurrentUser).mockResolvedValue({ userKey: 'u1', email: 'me@example.com', displayName: 'Me', avatarUrl: '', preferenceJson: '{}', workspaceKey: 'ws-1', workspaceName: '个人工作空间', workspaceRole: 'ADMIN' })
  })

  it('keeps the login email read-only and saves only the profile whitelist', async () => {
    vi.mocked(updateCurrentUser).mockResolvedValue({ userKey: 'u1', email: 'me@example.com', displayName: 'New name', avatarUrl: '', preferenceJson: '{}', workspaceKey: 'ws-1', workspaceName: '个人工作空间', workspaceRole: 'ADMIN' })
    const wrapper = mount(ProfileView)
    await flushPromises()
    const inputs = wrapper.findAll('input')
    expect(inputs[0].attributes('readonly')).toBeDefined()
    await inputs[1].setValue('New name')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(updateCurrentUser).toHaveBeenCalledWith(expect.objectContaining({ displayName: 'New name', preferenceJson: '{}' }))
    expect(wrapper.text()).toContain('个人资料已保存。')
  })
})
