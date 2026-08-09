import { flushPromises, mount, DOMWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'

import KnowledgeBaseListView from '../KnowledgeBaseListView.vue'
import {
  createKnowledgeBase,
  deleteKnowledgeBase,
  listKnowledgeBases,
  updateKnowledgeBase,
} from '../../../api/knowledge-bases'
import { useWorkspaceStore } from '../../../stores/workspace'

vi.mock('../../../api/knowledge-bases', () => ({
  createKnowledgeBase: vi.fn(),
  deleteKnowledgeBase: vi.fn(),
  listKnowledgeBases: vi.fn(),
  updateKnowledgeBase: vi.fn(),
}))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))

const mountView = () => mount(KnowledgeBaseListView, { attachTo: document.body })

/** AppDialog 内容 Teleport 到 body，通过 DOMWrapper 检索 */
const body = () => new DOMWrapper(document.body)

/**
 * KnowledgeBaseListView 测试。
 * 验证点（对应 T008 Verification 与 Quality）：
 * - 现有加载/创建/权限/错误恢复流程不回归
 * - canDelete=true 的卡片显示操作菜单，含重命名和删除
 * - canDelete=false 的卡片无删除入口
 * - 重命名弹出 AppDialog，输入新名称后列表即时更新
 * - 删除弹出确认弹窗，输入名称匹配后删除，取消不删除
 * - 操作反馈归属化（局部 loading/error）
 */
describe('KnowledgeBaseListView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    useWorkspaceStore().setWorkspace({
      key: 'personal-space',
      name: '个人工作空间',
      role: 'ADMIN',
    })
    vi.mocked(listKnowledgeBases).mockResolvedValue({
      records: [
        {
          knowledgeBaseKey: 'loan-policy',
          name: '贷款政策资料库',
          description: '授信、贷后与政策变更材料。',
          autoParse: true,
          autoPublish: true,
          updated: '2026-08-05T08:30:00.000+08:00',
          ownerUserKey: 'user-001',
          canDelete: true,
        },
        {
          knowledgeBaseKey: 'shared-kb',
          name: '共享知识库',
          description: '由他人创建，当前用户无删除权限。',
          autoParse: false,
          autoPublish: false,
          updated: '2026-08-04T10:00:00.000+08:00',
          ownerUserKey: 'user-002',
          canDelete: false,
        },
      ],
      total: 2,
      page: 0,
      size: 20,
    })
    vi.mocked(createKnowledgeBase).mockReset()
    vi.mocked(updateKnowledgeBase).mockReset()
    vi.mocked(deleteKnowledgeBase).mockReset()
  })

  it('loads knowledge bases and lets an administrator create one', async () => {
    vi.mocked(createKnowledgeBase).mockResolvedValue({
      knowledgeBaseKey: 'risk-research',
      workspaceKey: 'personal-space',
      name: '风险策略研究库',
      description: '风险研究材料。',
      autoParse: true,
      autoPublish: false,
      parserProfile: 'standard',
      chunkProfile: { strategy: 'SEMANTIC', chunkSize: 800, overlap: 120, titleLevel: null },
      modelBindings: [],
      revision: 0,
      updated: '2026-08-05T09:00:00.000+08:00',
      ownerUserKey: 'user-001',
      canDelete: true,
    })

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('贷款政策资料库')
    await wrapper.get('[data-test="create-knowledge-base"]').trigger('click')
    await nextTick()
    await body().get('[data-test="knowledge-base-name"]').setValue('风险策略研究库')
    await body().get('[data-test="knowledge-base-description"]').setValue('风险研究材料。')
    await body().get('form').trigger('submit')
    await flushPromises()

    expect(createKnowledgeBase).toHaveBeenCalledWith('personal-space', expect.objectContaining({
      name: '风险策略研究库',
      autoParse: true,
      autoPublish: false,
    }))
    expect(wrapper.text()).toContain('风险策略研究库')
  })

  it('keeps management actions hidden for a knowledge user', async () => {
    useWorkspaceStore().setWorkspace({
      key: 'shared-space',
      name: '共享知识空间',
      role: 'KNOWLEDGE_USER',
    })

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.find('[data-test="create-knowledge-base"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('知识用户')
  })

  it('shows a recoverable error when the list request fails', async () => {
    vi.mocked(listKnowledgeBases).mockRejectedValueOnce(new Error('network error'))

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('知识库加载失败，请稍后重试。')
    await wrapper.get('[role="alert"] button').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('贷款政策资料库')
  })

  it('canDelete=true 的卡片操作菜单含重命名和删除', async () => {
    const wrapper = mountView()
    await flushPromises()

    // 打开操作菜单
    await wrapper.get('[data-test="card-menu-trigger-loan-policy"]').trigger('click')
    const menu = wrapper.get('[data-test="card-menu-loan-policy"]')
    expect(menu.text()).toContain('重命名')
    expect(menu.text()).toContain('删除')
  })

  it('canDelete=false 的卡片操作菜单无删除入口', async () => {
    const wrapper = mountView()
    await flushPromises()

    await wrapper.get('[data-test="card-menu-trigger-shared-kb"]').trigger('click')
    const menu = wrapper.get('[data-test="card-menu-shared-kb"]')
    expect(menu.text()).toContain('重命名')
    expect(menu.text()).not.toContain('删除')
  })

  it('重命名弹出弹窗，输入新名称后调用 updateKnowledgeBase 并即时更新列表', async () => {
    vi.mocked(updateKnowledgeBase).mockResolvedValue({
      knowledgeBaseKey: 'loan-policy',
      workspaceKey: 'personal-space',
      name: '贷款政策库（新版）',
      description: '授信、贷后与政策变更材料。',
      autoParse: true,
      autoPublish: true,
      parserProfile: 'standard',
      chunkProfile: { strategy: 'SEMANTIC', chunkSize: 800, overlap: 120, titleLevel: null },
      modelBindings: [],
      revision: 1,
      updated: '2026-08-05T10:00:00.000+08:00',
      ownerUserKey: 'user-001',
      canDelete: true,
    })

    const wrapper = mountView()
    await flushPromises()

    await wrapper.get('[data-test="card-menu-trigger-loan-policy"]').trigger('click')
    await wrapper.get('[data-test="rename-action-loan-policy"]').trigger('click')
    await nextTick()

    // 重命名弹窗出现（AppDialog Teleport 到 body）
    const renameInput = body().get('[data-test="rename-input"]')
    expect((renameInput.element as HTMLInputElement).value).toBe('贷款政策资料库')
    await renameInput.setValue('贷款政策库（新版）')
    await body().get('[data-test="rename-submit"]').trigger('click')
    await flushPromises()

    expect(updateKnowledgeBase).toHaveBeenCalledWith('loan-policy', expect.objectContaining({
      name: '贷款政策库（新版）',
    }))
    expect(wrapper.text()).toContain('贷款政策库（新版）')
    // 旧名称不再显示
    expect(wrapper.text()).not.toContain('贷款政策资料库')
  })

  it('重命名为空时拒绝提交并提示错误', async () => {
    const wrapper = mountView()
    await flushPromises()

    await wrapper.get('[data-test="card-menu-trigger-loan-policy"]').trigger('click')
    await wrapper.get('[data-test="rename-action-loan-policy"]').trigger('click')
    await nextTick()
    await body().get('[data-test="rename-input"]').setValue('   ')
    await body().get('[data-test="rename-submit"]').trigger('click')
    await flushPromises()

    expect(updateKnowledgeBase).not.toHaveBeenCalled()
    expect(body().text()).toContain('请输入知识库名称')
  })

  it('删除弹出确认弹窗，输入名称匹配后调用 deleteKnowledgeBase 并从列表移除', async () => {
    vi.mocked(deleteKnowledgeBase).mockResolvedValue(true)

    const wrapper = mountView()
    await flushPromises()

    await wrapper.get('[data-test="card-menu-trigger-loan-policy"]').trigger('click')
    await wrapper.get('[data-test="delete-action-loan-policy"]').trigger('click')
    await nextTick()

    // 删除确认弹窗出现，需输入名称匹配
    const confirmInput = body().get('[data-test="delete-confirm-input"]')
    await confirmInput.setValue('贷款政策资料库')
    await body().get('[data-test="delete-confirm-submit"]').trigger('click')
    await flushPromises()

    expect(deleteKnowledgeBase).toHaveBeenCalledWith('loan-policy')
    expect(wrapper.text()).not.toContain('贷款政策资料库')
  })

  it('删除确认名称不匹配时拒绝提交并提示错误', async () => {
    const wrapper = mountView()
    await flushPromises()

    await wrapper.get('[data-test="card-menu-trigger-loan-policy"]').trigger('click')
    await wrapper.get('[data-test="delete-action-loan-policy"]').trigger('click')
    await nextTick()
    await body().get('[data-test="delete-confirm-input"]').setValue('错误的名称')
    await body().get('[data-test="delete-confirm-submit"]').trigger('click')
    await flushPromises()

    expect(deleteKnowledgeBase).not.toHaveBeenCalled()
    expect(body().text()).toContain('不匹配')
    // 列表仍包含该知识库
    expect(wrapper.text()).toContain('贷款政策资料库')
  })

  it('删除取消时不执行删除', async () => {
    const wrapper = mountView()
    await flushPromises()

    await wrapper.get('[data-test="card-menu-trigger-loan-policy"]').trigger('click')
    await wrapper.get('[data-test="delete-action-loan-policy"]').trigger('click')
    await nextTick()
    await body().get('[data-test="delete-cancel"]').trigger('click')

    expect(deleteKnowledgeBase).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('贷款政策资料库')
  })

  it('删除失败时显示错误且列表保留', async () => {
    vi.mocked(deleteKnowledgeBase).mockRejectedValue(new Error('server error'))

    const wrapper = mountView()
    await flushPromises()

    await wrapper.get('[data-test="card-menu-trigger-loan-policy"]').trigger('click')
    await wrapper.get('[data-test="delete-action-loan-policy"]').trigger('click')
    await nextTick()
    await body().get('[data-test="delete-confirm-input"]').setValue('贷款政策资料库')
    await body().get('[data-test="delete-confirm-submit"]').trigger('click')
    await flushPromises()

    // 删除失败错误显示在弹窗内（Teleport 到 body）
    expect(body().text()).toContain('删除失败')
    expect(wrapper.text()).toContain('贷款政策资料库')
  })
})
