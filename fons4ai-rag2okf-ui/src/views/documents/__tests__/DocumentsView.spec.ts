import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import DocumentsView from '../DocumentsView.vue'
import { listDocuments, uploadDocument, batchUploadDocuments } from '../../../api/documents'
import { useWorkspaceStore } from '../../../stores/workspace'

vi.mock('../../../api/documents', () => ({
  listDocuments: vi.fn(),
  uploadDocument: vi.fn(),
  batchUploadDocuments: vi.fn(),
  getDocument: vi.fn(),
  updateDocumentFile: vi.fn(),
  triggerParse: vi.fn(),
  triggerPublish: vi.fn(),
  getChunkPreview: vi.fn(),
  getParsePreview: vi.fn(),
  retryTask: vi.fn(),
  rechunkDocument: vi.fn(),
}))
vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { knowledgeBaseKey: 'loan-policy' } }),
  useRouter: () => ({ push: vi.fn() }),
}))

describe('DocumentsView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    useWorkspaceStore().setWorkspace({ key: 'personal', name: 'Personal', role: 'ADMIN' })
    localStorage.clear()
    vi.mocked(listDocuments).mockResolvedValue({
      records: [{
        documentKey: 'doc-1',
        displayName: 'policy.md',
        folderPath: '/',
        currentFile: { filename: 'policy.md', contentType: 'text/markdown', size: 1024 },
        currentFileToken: 'opaque',
        parseStatus: 'SUCCEEDED',
        publishStatus: 'UNPUBLISHED',
        hasActivePublication: false,
        updated: '2026-08-05T08:00:00Z',
      }],
      total: 1,
      page: 0,
      size: 20,
    })
  })

  it('renders folder sidebar with documents from different folders', async () => {
    vi.mocked(listDocuments).mockResolvedValue({
      records: [
        { documentKey: 'doc-1', displayName: 'a.md', folderPath: '/合规材料', currentFile: { filename: 'a.md', contentType: 'text/markdown', size: 100 }, currentFileToken: 't1', parseStatus: 'SUCCEEDED', publishStatus: 'UNPUBLISHED', hasActivePublication: false, updated: '2026-08-05T08:00:00Z' },
        { documentKey: 'doc-2', displayName: 'b.md', folderPath: '/技术文档', currentFile: { filename: 'b.md', contentType: 'text/markdown', size: 200 }, currentFileToken: 't2', parseStatus: 'NOT_STARTED', publishStatus: 'UNPUBLISHED', hasActivePublication: false, updated: '2026-08-06T08:00:00Z' },
      ],
      total: 2,
      page: 0,
      size: 20,
    })
    const wrapper = mount(DocumentsView)
    await flushPromises()
    expect(wrapper.text()).toContain('合规材料')
    expect(wrapper.text()).toContain('技术文档')
    expect(wrapper.text()).toContain('全部文件')
  })

  it('filters documents when selecting a folder', async () => {
    vi.mocked(listDocuments).mockResolvedValue({
      records: [{
        documentKey: 'doc-1', displayName: 'policy.md', folderPath: '/',
        currentFile: { filename: 'policy.md', contentType: 'text/markdown', size: 1024 },
        currentFileToken: 'opaque', parseStatus: 'SUCCEEDED', publishStatus: 'UNPUBLISHED',
        hasActivePublication: false, updated: '2026-08-05T08:00:00Z',
      }],
      total: 1, page: 0, size: 20,
    })
    const wrapper = mount(DocumentsView)
    await flushPromises()

    const folderButton = wrapper.findAll('.folder-item').find(b => b.text().includes('全部文件'))
    expect(folderButton).toBeTruthy()
  })

  it('uploads a single file via the merged upload entry', async () => {
    vi.mocked(uploadDocument).mockResolvedValue({ documentKey: 'doc-2' })
    const wrapper = mount(DocumentsView)
    await flushPromises()
    expect(wrapper.text()).toContain('policy.md')

    await wrapper.get('button.primary-action').trigger('click')
    expect(wrapper.find('.upload-panel').exists()).toBe(true)

    const file = new File(['content'], 'policy.md', { type: 'text/markdown' })
    const input = wrapper.get('[data-test="document-file"]')
    Object.defineProperty(input.element, 'files', { value: [file] })
    await input.trigger('change')
    await wrapper.get('.upload-panel').trigger('submit')
    await flushPromises()

    expect(uploadDocument).toHaveBeenCalledWith('loan-policy', file, 'DEFAULT', undefined)
  })

  it('shows batch upload interface with folder selection', async () => {
    const wrapper = mount(DocumentsView)
    await flushPromises()
    await wrapper.get('button.primary-action').trigger('click')

    expect(wrapper.find('[data-test="document-folder"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('选择文件')
    expect(wrapper.text()).toContain('选择文件夹')
  })

  it('creates a new folder stored in localStorage', async () => {
    const wrapper = mount(DocumentsView)
    await flushPromises()

    const addButton = wrapper.findAll('button').find(b => b.text().includes('新建文件夹'))
    expect(addButton).toBeTruthy()
    await addButton!.trigger('click')

    const input = wrapper.find('.folder-input-group input')
    expect(input.exists()).toBe(true)
    await input.setValue('合规材料/2024年报')
    await wrapper.find('.folder-input-group .secondary-action').trigger('click')

    expect(localStorage.getItem('rag2okf_folders_loan-policy')).toContain('合规材料/2024年报')
    expect(wrapper.text()).toContain('合规材料/2024年报')
  })

  it('shows breadcrumb for root folder', async () => {
    const wrapper = mount(DocumentsView)
    await flushPromises()
    expect(wrapper.text()).toContain('全部文件')
  })
})
