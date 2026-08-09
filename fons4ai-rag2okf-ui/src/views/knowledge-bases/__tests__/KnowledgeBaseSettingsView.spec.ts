import { flushPromises, mount, DOMWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'

import KnowledgeBaseSettingsView from '../KnowledgeBaseSettingsView.vue'
import AppDialog from '../../../components/ui/AppDialog.vue'
import { getKnowledgeBase, updateKnowledgeBase } from '../../../api/knowledge-bases'
import { useWorkspaceStore } from '../../../stores/workspace'
import { listProfiles } from '../../../api/models'

// 通过 vi.hoisted 暴露 router.push，便于断言跨页直达模型设置
const { routerPush } = vi.hoisted(() => ({ routerPush: vi.fn() }))

vi.mock('../../../api/knowledge-bases', () => ({
  getKnowledgeBase: vi.fn(),
  updateKnowledgeBase: vi.fn(),
}))
vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { knowledgeBaseKey: 'loan-policy' } }),
  useRouter: () => ({ push: routerPush }),
}))
vi.mock('../../../api/models', () => ({ listProfiles: vi.fn() }))

const mountView = () => mount(KnowledgeBaseSettingsView, { attachTo: document.body })

/** AppDialog 内容 Teleport 到 body，通过 DOMWrapper 检索 */
const body = () => new DOMWrapper(document.body)

/**
 * KnowledgeBaseSettingsView 弹窗化测试。
 * 验证点（对应 T012 AC-006 / AC-014）：
 * - "只影响后续操作"提示与保存流程不回归
 * - 编辑操作改为 AppDialog 中央弹窗，不再使用自实现 drawer-backdrop
 * - 弹窗内修改配置后保存调用 updateKnowledgeBase
 * - 模型绑定下拉为空时提供直达模型设置入口
 * - 操作反馈归属化（局部 success message 停留在触发位置）
 */
describe('KnowledgeBaseSettingsView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    useWorkspaceStore().setWorkspace({ key: 'personal-space', name: '个人工作空间', role: 'ADMIN' })
    routerPush.mockClear()
    vi.mocked(getKnowledgeBase).mockResolvedValue({
      knowledgeBaseKey: 'loan-policy', workspaceKey: 'personal-space', name: '贷款政策资料库', description: '政策材料。',
      autoParse: true, autoPublish: false, parserProfile: 'standard',
      chunkProfile: { strategy: 'SEMANTIC', chunkSize: 800, overlap: 120, titleLevel: null },
      modelBindings: [], revision: 3, updated: '2026-08-05T09:00:00.000+08:00',
      ownerUserKey: 'user-001', canDelete: true,
    })
    vi.mocked(listProfiles).mockResolvedValue([])
    vi.mocked(updateKnowledgeBase).mockResolvedValue({
      knowledgeBaseKey: 'loan-policy', workspaceKey: 'personal-space', name: '贷款政策资料库', description: '政策材料。',
      autoParse: true, autoPublish: false, parserProfile: 'standard',
      chunkProfile: { strategy: 'SEMANTIC', chunkSize: 800, overlap: 120, titleLevel: null },
      modelBindings: [], revision: 4, updated: '2026-08-05T09:05:00.000+08:00',
      ownerUserKey: 'user-001', canDelete: true,
    })
  })

  it('explains that settings only apply to future work and saves the edited defaults', async () => {
    vi.mocked(updateKnowledgeBase).mockImplementation(async (_key, input) => ({
      knowledgeBaseKey: 'loan-policy', workspaceKey: 'personal-space', name: input.name ?? '', description: input.description ?? '',
      autoParse: input.autoParse ?? false, autoPublish: input.autoPublish ?? false, parserProfile: input.parserProfile ?? 'standard',
      chunkProfile: input.chunkProfile!, modelBindings: [], revision: 4, updated: '2026-08-05T09:05:00.000+08:00',
      ownerUserKey: 'user-001', canDelete: true,
    }))

    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('只影响后续操作')
    // 打开解析与分块弹窗，修改分块大小后保存
    await wrapper.get('[data-test="edit-processing"]').trigger('click')
    await nextTick()
    await body().get('[data-test="chunk-size-input"]').setValue('1000')
    await body().get('[data-test="save-processing"]').trigger('click')
    await flushPromises()

    expect(updateKnowledgeBase).toHaveBeenCalledWith('loan-policy', expect.objectContaining({
      revision: 3,
      chunkProfile: expect.objectContaining({ chunkSize: 1000 }),
    }))
    expect(wrapper.text()).toContain('设置已保存，仅应用于之后发起的上传和处理操作。')
  })

  it('点击编辑按钮弹出 AppDialog 中央弹窗', async () => {
    const wrapper = mountView()
    await flushPromises()
    // 初始无可见弹窗（a-modal destroy-on-close，未打开时 body 中无 dialog）
    expect(body().find('[role="dialog"]').exists()).toBe(false)
    // 点击编辑后弹出中央模态（aria-modal=true，aria-labelledby 指向标题）
    await wrapper.get('[data-test="edit-processing"]').trigger('click')
    await nextTick()
    const dialog = body().get('[role="dialog"]')
    expect(dialog.attributes('aria-modal')).toBe('true')
    expect(dialog.attributes('aria-labelledby')).toBe('dialog-title')
  })

  it('不再使用自实现 drawer-backdrop，统一用 AppDialog', async () => {
    const wrapper = mountView()
    await flushPromises()
    // 三个编辑入口均由 AppDialog 承载
    expect(wrapper.findAllComponents(AppDialog)).toHaveLength(3)
    // 未使用旧的右侧抽屉容器
    expect(wrapper.find('.settings-drawer').exists()).toBe(false)
  })

  it('在弹窗中修改配置后保存调用 updateKnowledgeBase', async () => {
    const wrapper = mountView()
    await flushPromises()
    await wrapper.get('[data-test="edit-automation"]').trigger('click')
    await nextTick()
    await body().get('[data-test="auto-parse-input"]').setValue(false)
    await body().get('[data-test="save-automation"]').trigger('click')
    await flushPromises()

    expect(updateKnowledgeBase).toHaveBeenCalledWith('loan-policy', expect.objectContaining({
      autoParse: false, autoPublish: false, revision: 3,
    }))
  })

  it('模型绑定下拉为空时显示前往模型设置入口并跳转', async () => {
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('前往模型设置')
    await wrapper.get('[data-test="goto-model-settings"]').trigger('click')
    expect(routerPush).toHaveBeenCalledWith({ name: 'settings-models' })
  })

  it('保存后反馈停留在触发位置（局部 success message）', async () => {
    const wrapper = mountView()
    await flushPromises()
    await wrapper.get('[data-test="edit-automation"]').trigger('click')
    await nextTick()
    await body().get('[data-test="save-automation"]').trigger('click')
    await flushPromises()

    // 弹窗关闭
    expect(body().find('[role="dialog"]').exists()).toBe(false)
    // 反馈出现在自动化卡片内（局部归属）
    const card = wrapper.get('[data-test="automation-card"]')
    expect(card.text()).toContain('设置已保存')
    // 其他卡片不包含该反馈
    expect(wrapper.get('[data-test="processing-card"]').text()).not.toContain('设置已保存')
  })
})
