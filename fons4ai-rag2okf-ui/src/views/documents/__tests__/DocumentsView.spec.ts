import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import DocumentsView from '../DocumentsView.vue'
import { listDocuments, uploadDocument } from '../../../api/documents'
import { useWorkspaceStore } from '../../../stores/workspace'

vi.mock('../../../api/documents', () => ({ listDocuments: vi.fn(), uploadDocument: vi.fn() }))
vi.mock('vue-router', () => ({ useRoute: () => ({ params: { knowledgeBaseKey: 'loan-policy' } }), useRouter: () => ({ push: vi.fn() }) }))

describe('DocumentsView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    useWorkspaceStore().setWorkspace({ key: 'personal', name: 'Personal', role: 'ADMIN' })
    vi.mocked(listDocuments).mockResolvedValue({ records: [{ documentKey: 'doc-1', displayName: 'policy.md', currentFile: { filename: 'policy.md', contentType: 'text/markdown', size: 1024 }, currentFileToken: 'opaque', parseStatus: 'SUCCEEDED', publishStatus: 'UNPUBLISHED', hasActivePublication: false, updated: '2026-08-05T08:00:00Z' }], total: 1, page: 0, size: 20 })
  })

  it('loads document rows and uploads a new same-name document without client-side merging', async () => {
    vi.mocked(uploadDocument).mockResolvedValue({ documentKey: 'doc-2' })
    const wrapper = mount(DocumentsView)
    await flushPromises()
    expect(wrapper.text()).toContain('policy.md')
    await wrapper.get('button.primary-action').trigger('click')
    const file = new File(['content'], 'policy.md', { type: 'text/markdown' })
    const input = wrapper.get('[data-test="document-file"]')
    Object.defineProperty(input.element, 'files', { value: [file] })
    await input.trigger('change')
    await wrapper.get('.upload-panel').trigger('submit')
    await flushPromises()
    expect(uploadDocument).toHaveBeenCalledWith('loan-policy', file, 'DEFAULT')
  })
})
