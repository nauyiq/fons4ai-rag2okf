import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import KnowledgeBaseListView from '../KnowledgeBaseListView.vue'
import { createKnowledgeBase, listKnowledgeBases } from '../../../api/knowledge-bases'
import { useWorkspaceStore } from '../../../stores/workspace'

vi.mock('../../../api/knowledge-bases', () => ({
  createKnowledgeBase: vi.fn(),
  listKnowledgeBases: vi.fn(),
}))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))

const mountView = () => mount(KnowledgeBaseListView)

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
        },
      ],
      total: 1,
      page: 0,
      size: 20,
    })
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
    })

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('贷款政策资料库')
    await wrapper.get('[data-test="create-knowledge-base"]').trigger('click')
    await wrapper.get('[data-test="knowledge-base-name"]').setValue('风险策略研究库')
    await wrapper.get('[data-test="knowledge-base-description"]').setValue('风险研究材料。')
    await wrapper.get('form').trigger('submit')
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
})
