import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import KnowledgeBaseSettingsView from '../KnowledgeBaseSettingsView.vue'
import { getKnowledgeBase, updateKnowledgeBase } from '../../../api/knowledge-bases'
import { useWorkspaceStore } from '../../../stores/workspace'
import { listModelProfiles } from '../../../api/models'

vi.mock('../../../api/knowledge-bases', () => ({
  getKnowledgeBase: vi.fn(),
  updateKnowledgeBase: vi.fn(),
}))
vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { knowledgeBaseKey: 'loan-policy' } }),
  useRouter: () => ({ push: vi.fn() }),
}))
vi.mock('../../../api/models', () => ({ listModelProfiles: vi.fn() }))

const mountView = () => mount(KnowledgeBaseSettingsView)

describe('KnowledgeBaseSettingsView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    useWorkspaceStore().setWorkspace({ key: 'personal-space', name: '个人工作空间', role: 'ADMIN' })
    vi.mocked(getKnowledgeBase).mockResolvedValue({
      knowledgeBaseKey: 'loan-policy', workspaceKey: 'personal-space', name: '贷款政策资料库', description: '政策材料。',
      autoParse: true, autoPublish: false, parserProfile: 'standard',
      chunkProfile: { strategy: 'SEMANTIC', chunkSize: 800, overlap: 120, titleLevel: null },
      modelBindings: [], revision: 3, updated: '2026-08-05T09:00:00.000+08:00',
    })
    vi.mocked(listModelProfiles).mockResolvedValue([])
  })

  it('explains that settings only apply to future work and saves the edited defaults', async () => {
    vi.mocked(updateKnowledgeBase).mockImplementation(async (_key, input) => ({
      knowledgeBaseKey: 'loan-policy', workspaceKey: 'personal-space', name: input.name ?? '', description: input.description ?? '',
      autoParse: input.autoParse ?? false, autoPublish: input.autoPublish ?? false, parserProfile: input.parserProfile ?? 'standard',
      chunkProfile: input.chunkProfile!, modelBindings: [], revision: 4, updated: '2026-08-05T09:05:00.000+08:00',
    }))

    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('只影响后续操作')
    await wrapper.get('input[type="number"]').setValue('1000')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(updateKnowledgeBase).toHaveBeenCalledWith('loan-policy', expect.objectContaining({ revision: 3 }))
    expect(wrapper.text()).toContain('设置已保存，仅应用于之后发起的上传和处理操作。')
  })
})
