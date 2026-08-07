import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, reactive } from 'vue'

import Rag2OkfAppShell from '../Rag2OkfAppShell.vue'

const mockRoute = reactive({ name: 'knowledge-bases', meta: { sectionLabel: '全部知识库' } as Record<string, unknown>, params: {} as Record<string, unknown> })
const mockPush = vi.fn()

vi.mock('vue-router', () => ({
  useRoute: () => mockRoute,
  useRouter: () => ({ push: mockPush, replace: vi.fn() }),
}))

const RouterLinkStub = defineComponent({
  name: 'RouterLink',
  props: { to: { type: [String, Object], required: true } },
  setup(props, { slots, attrs }) {
    return () => h('a', { 'data-to': typeof props.to === 'string' ? props.to : props.to.name, ...attrs }, slots.default?.())
  },
})

vi.mock('../../composables/useTheme', () => ({
  useTheme: () => ({
    mode: { value: 'system' },
    setTheme: vi.fn(),
  }),
}))

vi.mock('../../stores/workspace', () => ({
  useWorkspaceStore: () => ({
    currentWorkspace: { key: 'ws-1', name: '个人工作空间', role: 'ADMIN' },
    canManage: true,
  }),
}))

vi.mock('../../stores/session', () => ({
  useSessionStore: () => ({
    profile: { displayName: '张三', email: 'zhang@example.com' },
    signOut: vi.fn().mockResolvedValue(undefined),
  }),
}))

const mountShell = () => mount(Rag2OkfAppShell, {
  slots: { default: '<div data-test="slot-content">页面内容</div>' },
  global: { stubs: { RouterLink: RouterLinkStub } },
})

describe('Rag2OkfAppShell - 顶部导航重塑（T007）', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    mockRoute.name = 'knowledge-bases'
    mockRoute.meta = { sectionLabel: '全部知识库' }
    mockRoute.params = {}
    mockPush.mockClear()
  })

  it('渲染顶部导航项：logo 首页、知识库、搜索 disabled、聊天 disabled', () => {
    const wrapper = mountShell()
    expect(wrapper.find('[data-test="nav-home"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="nav-knowledge-bases"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="nav-search"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="nav-chat"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="nav-search"]').attributes('disabled')).toBeDefined()
    expect(wrapper.find('[data-test="nav-chat"]').attributes('disabled')).toBeDefined()
  })

  it('不渲染 evidence-rail', () => {
    const wrapper = mountShell()
    expect(wrapper.find('.evidence-rail').exists()).toBe(false)
    expect(wrapper.find('aside.evidence-rail').exists()).toBe(false)
  })

  it('不渲染 contextbar', () => {
    const wrapper = mountShell()
    expect(wrapper.find('.contextbar').exists()).toBe(false)
  })

  it('不出现 OKF/问答/评测 占位', () => {
    const wrapper = mountShell()
    const text = wrapper.text()
    expect(text).not.toMatch(/OKF|问答|评测/)
  })

  it('知识库相关路由高亮"知识库"导航项', async () => {
    const wrapper = mountShell()
    expect(wrapper.find('[data-test="nav-knowledge-bases"]').classes()).toContain('active')

    mockRoute.name = 'documents'
    mockRoute.params = { knowledgeBaseKey: 'kb-1' }
    await wrapper.vm.$nextTick()
    expect(wrapper.find('[data-test="nav-knowledge-bases"]').classes()).toContain('active')

    mockRoute.name = 'document-detail'
    await wrapper.vm.$nextTick()
    expect(wrapper.find('[data-test="nav-knowledge-bases"]').classes()).toContain('active')

    mockRoute.name = 'knowledge-base-settings'
    await wrapper.vm.$nextTick()
    expect(wrapper.find('[data-test="nav-knowledge-bases"]').classes()).toContain('active')
  })

  it('非知识库路由不高亮"知识库"导航项', async () => {
    mockRoute.name = 'model-settings'
    mockRoute.meta = { sectionLabel: '模型设置' }
    const wrapper = mountShell()
    await flushPromises()
    expect(wrapper.find('[data-test="nav-knowledge-bases"]').classes()).not.toContain('active')
  })

  it('logo 首页点击跳转知识库', async () => {
    const wrapper = mountShell()
    await wrapper.find('[data-test="nav-home"]').trigger('click')
    expect(mockPush).toHaveBeenCalledWith({ name: 'knowledge-bases' })
  })

  it('主题切换按钮存在', () => {
    const wrapper = mountShell()
    expect(wrapper.find('[data-test="theme-toggle"]').exists()).toBe(true)
  })

  it('账号菜单存在且无个人偏好入口', async () => {
    const wrapper = mountShell()
    expect(wrapper.find('[data-test="account-menu"]').exists()).toBe(true)
    await wrapper.find('.avatar').trigger('click')
    const popoverText = wrapper.text()
    expect(popoverText).toContain('个人中心')
    expect(popoverText).toContain('模型设置')
    expect(popoverText).toContain('退出登录')
    expect(popoverText).not.toMatch(/个人偏好/)
  })

  it('面包屑反映当前路由 sectionLabel', async () => {
    mockRoute.meta = { sectionLabel: '文档详情' }
    const wrapper = mountShell()
    await flushPromises()
    expect(wrapper.find('[data-test="breadcrumb"]').text()).toContain('文档详情')
  })

  it('渲染默认插槽内容', () => {
    const wrapper = mountShell()
    expect(wrapper.find('[data-test="slot-content"]').exists()).toBe(true)
  })
})
