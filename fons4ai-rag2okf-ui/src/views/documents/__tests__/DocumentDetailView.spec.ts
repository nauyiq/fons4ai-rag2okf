import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import DocumentDetailView from '../DocumentDetailView.vue'
import { getChunkPreview, getDocument, getParsePreview, rechunkDocument } from '../../../api/documents'
import { useWorkspaceStore } from '../../../stores/workspace'

vi.mock('../../../api/documents', () => ({ getChunkPreview: vi.fn(), getDocument: vi.fn(), getParsePreview: vi.fn(), rechunkDocument: vi.fn(), retryTask: vi.fn(), triggerParse: vi.fn(), triggerPublish: vi.fn(), updateDocumentFile: vi.fn() }))
vi.mock('vue-router', () => ({ useRoute: () => ({ params: { knowledgeBaseKey: 'loan-policy', documentKey: 'doc-1' } }), useRouter: () => ({ push: vi.fn() }) }))

describe('DocumentDetailView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    useWorkspaceStore().setWorkspace({ key: 'personal', name: 'Personal', role: 'ADMIN' })
    vi.mocked(getDocument).mockResolvedValue({ documentKey: 'doc-1', knowledgeBaseKey: 'loan-policy', displayName: 'policy.md', currentFile: { filename: 'policy.md', contentType: 'text/markdown', size: 1024 }, currentFileToken: 'opaque', parseStatus: 'SUCCEEDED', publishStatus: 'UNPUBLISHED', hasActivePublication: false, updated: '2026-08-05T08:00:00Z' })
    vi.mocked(getChunkPreview).mockResolvedValue({ hasChunk: true, currentChunkRevisionKey: 'chunk-token', chunkProfile: {}, parentCount: 1, childCount: 2, total: 2 })
    vi.mocked(getParsePreview).mockResolvedValue({ hasParse: true, parserProfile: 'native', blockCount: 3 })
  })
  it('does not submit destructive rechunk when the user cancels', async () => {
    const wrapper = mount(DocumentDetailView)
    await flushPromises()
    await wrapper.get('.danger-link').trigger('click')
    await wrapper.get('[data-test="cancel-rechunk"]').trigger('click')
    expect(rechunkDocument).not.toHaveBeenCalled()
  })
})
