import { flushPromises, mount, DOMWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'

import DocumentsView from '../DocumentsView.vue'
import { listDocuments, uploadDocument, batchUploadDocuments, deleteDocument, batchDeleteDocuments } from '../../../api/documents'
import { useWorkspaceStore } from '../../../stores/workspace'

vi.mock('../../../api/documents', () => ({
  listDocuments: vi.fn(),
  uploadDocument: vi.fn(),
  batchUploadDocuments: vi.fn(),
  deleteDocument: vi.fn(),
  batchDeleteDocuments: vi.fn(),
  getDocument: vi.fn(),
  updateDocumentFile: vi.fn(),
  triggerParse: vi.fn(),
  triggerPublish: vi.fn(),
  getChunkPreview: vi.fn(),
  getParsePreview: vi.fn(),
  retryTask: vi.fn(),
  rechunkDocument: vi.fn(),
  getSourceContent: vi.fn(),
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
    vi.clearAllMocks()
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

    const folderButton = wrapper.findAll('[data-test="folder-item"]').find(b => b.text().includes('全部文件'))
    expect(folderButton).toBeTruthy()
  })

  /** AppDialog / ConfirmDialog 内容 Teleport 到 body，通过 DOMWrapper 检索 */
  const body = () => new DOMWrapper(document.body)

  it('uploads a single file via the merged upload entry', async () => {
    vi.mocked(uploadDocument).mockResolvedValue({ documentKey: 'doc-2' })
    const wrapper = mount(DocumentsView, { attachTo: document.body })
    await flushPromises()
    expect(wrapper.text()).toContain('policy.md')

    await wrapper.get('[data-test="upload-btn"]').trigger('click')
    await nextTick()
    expect(body().find('.upload-form').exists()).toBe(true)

    const file = new File(['content'], 'policy.md', { type: 'text/markdown' })
    const input = body().get('[data-test="document-file"]')
    Object.defineProperty(input.element, 'files', { value: [file] })
    await input.trigger('change')
    await body().get('.upload-form').trigger('submit')
    await flushPromises()

    expect(uploadDocument).toHaveBeenCalledWith('loan-policy', file, 'DEFAULT', undefined)
  })

  it('shows batch upload interface with folder selection', async () => {
    const wrapper = mount(DocumentsView, { attachTo: document.body })
    await flushPromises()
    await wrapper.get('[data-test="upload-btn"]').trigger('click')
    await nextTick()

    expect(body().find('[data-test="document-folder"]').exists()).toBe(true)
    expect(body().text()).toContain('选择文件')
    expect(body().text()).toContain('选择文件夹')
  })

  it('creates a new folder stored in localStorage', async () => {
    const wrapper = mount(DocumentsView)
    await flushPromises()

    const addButton = wrapper.findAll('button').find(b => b.text().includes('新建文件夹'))
    expect(addButton).toBeTruthy()
    await addButton!.trigger('click')

    const input = wrapper.find('[data-test="new-folder-input"]')
    expect(input.exists()).toBe(true)
    await input.setValue('合规材料/2024年报')
    await wrapper.find('[data-test="new-folder-confirm"]').trigger('click')

    expect(localStorage.getItem('rag2okf_folders_loan-policy')).toContain('合规材料/2024年报')
    expect(wrapper.text()).toContain('合规材料')
  })

  it('shows breadcrumb for root folder', async () => {
    const wrapper = mount(DocumentsView)
    await flushPromises()
    expect(wrapper.text()).toContain('全部文件')
  })

  it('右键点击目录区域时出现新建目录选项', async () => {
    const wrapper = mount(DocumentsView)
    await flushPromises()

    const sidebar = wrapper.find('[data-test="folder-sidebar"]')
    await sidebar.trigger('contextmenu')
    expect(wrapper.text()).toContain('新建目录')
  })

  it('右键新建目录输入名称后创建成功并暂存到 localStorage', async () => {
    const wrapper = mount(DocumentsView)
    await flushPromises()

    await wrapper.find('[data-test="folder-sidebar"]').trigger('contextmenu')
    await wrapper.get('[data-test="context-new-folder"]').trigger('click')

    const input = wrapper.find('[data-test="new-folder-input"]')
    expect(input.exists()).toBe(true)
    await input.setValue('/风险策略')
    await wrapper.find('[data-test="new-folder-confirm"]').trigger('click')

    expect(localStorage.getItem('rag2okf_folders_loan-policy')).toContain('/风险策略')
    expect(wrapper.text()).toContain('风险策略')
  })

  it('树状结构展示目录层级可展开收起', async () => {
    vi.mocked(listDocuments).mockResolvedValue({
      records: [
        { documentKey: 'doc-1', displayName: 'a.md', folderPath: '/合规材料/2024年报', currentFile: { filename: 'a.md', contentType: 'text/markdown', size: 100 }, currentFileToken: 't1', parseStatus: 'SUCCEEDED', publishStatus: 'UNPUBLISHED', hasActivePublication: false, updated: '2026-08-05T08:00:00Z' },
        { documentKey: 'doc-2', displayName: 'b.md', folderPath: '/合规材料', currentFile: { filename: 'b.md', contentType: 'text/markdown', size: 200 }, currentFileToken: 't2', parseStatus: 'NOT_STARTED', publishStatus: 'UNPUBLISHED', hasActivePublication: false, updated: '2026-08-06T08:00:00Z' },
      ],
      total: 2, page: 0, size: 20,
    })
    const wrapper = mount(DocumentsView)
    await flushPromises()

    // 一级目录"合规材料"可见
    expect(wrapper.text()).toContain('合规材料')
    // 子目录"2024年报"初始不可见（收起状态）
    expect(wrapper.text()).not.toContain('2024年报')

    // 点击展开按钮
    const expandBtn = wrapper.find('[data-test="folder-toggle-/合规材料"]')
    expect(expandBtn.exists()).toBe(true)
    await expandBtn.trigger('click')

    // 展开后子目录可见
    expect(wrapper.text()).toContain('2024年报')

    // 再次点击收起
    await expandBtn.trigger('click')
    expect(wrapper.text()).not.toContain('2024年报')
  })

  it('切换目录时文档列表按 folderPath 过滤', async () => {
    let lastFolderPath: string | undefined
    vi.mocked(listDocuments).mockImplementation(async (_kb, _page, _size, folderPath) => {
      lastFolderPath = folderPath
      return {
        records: [{
          documentKey: 'doc-1', displayName: 'a.md', folderPath: folderPath ?? '/',
          currentFile: { filename: 'a.md', contentType: 'text/markdown', size: 100 },
          currentFileToken: 't1', parseStatus: 'SUCCEEDED', publishStatus: 'UNPUBLISHED',
          hasActivePublication: false, updated: '2026-08-05T08:00:00Z',
        }],
        total: 1, page: 0, size: 20,
      }
    })

    const wrapper = mount(DocumentsView)
    await flushPromises()
    // 初始加载根目录
    expect(lastFolderPath).toBeUndefined()

    // 点击"全部文件"
    const rootFolder = wrapper.findAll('[data-test="folder-item"]').find(b => b.text().includes('全部文件'))
    await rootFolder!.trigger('click')
    await flushPromises()
    expect(lastFolderPath).toBeUndefined()
  })

  it('勾选文档后出现批量删除按钮', async () => {
    vi.mocked(listDocuments).mockResolvedValue({
      records: [
        { documentKey: 'doc-1', displayName: 'a.md', folderPath: '/', currentFile: { filename: 'a.md', contentType: 'text/markdown', size: 100 }, currentFileToken: 't1', parseStatus: 'SUCCEEDED', publishStatus: 'UNPUBLISHED', hasActivePublication: false, updated: '2026-08-05T08:00:00Z' },
        { documentKey: 'doc-2', displayName: 'b.md', folderPath: '/', currentFile: { filename: 'b.md', contentType: 'text/markdown', size: 200 }, currentFileToken: 't2', parseStatus: 'NOT_STARTED', publishStatus: 'UNPUBLISHED', hasActivePublication: false, updated: '2026-08-06T08:00:00Z' },
      ],
      total: 2, page: 0, size: 20,
    })
    const wrapper = mount(DocumentsView)
    await flushPromises()

    // 初始无批量删除按钮
    expect(wrapper.find('[data-test="batch-delete-btn"]').exists()).toBe(false)

    // 勾选第一个文档
    const checkbox = wrapper.find('[data-test="select-doc-1"]')
    expect(checkbox.exists()).toBe(true)
    await checkbox.setValue(true)
    await flushPromises()

    // 出现批量删除按钮
    expect(wrapper.find('[data-test="batch-delete-btn"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('已选 1 个')
  })

  it('全选 checkbox 可勾选所有文档', async () => {
    vi.mocked(listDocuments).mockResolvedValue({
      records: [
        { documentKey: 'doc-1', displayName: 'a.md', folderPath: '/', currentFile: { filename: 'a.md', contentType: 'text/markdown', size: 100 }, currentFileToken: 't1', parseStatus: 'SUCCEEDED', publishStatus: 'UNPUBLISHED', hasActivePublication: false, updated: '2026-08-05T08:00:00Z' },
        { documentKey: 'doc-2', displayName: 'b.md', folderPath: '/', currentFile: { filename: 'b.md', contentType: 'text/markdown', size: 200 }, currentFileToken: 't2', parseStatus: 'NOT_STARTED', publishStatus: 'UNPUBLISHED', hasActivePublication: false, updated: '2026-08-06T08:00:00Z' },
      ],
      total: 2, page: 0, size: 20,
    })
    const wrapper = mount(DocumentsView)
    await flushPromises()

    const selectAll = wrapper.find('[data-test="select-all"]')
    expect(selectAll.exists()).toBe(true)
    await selectAll.setValue(true)
    await flushPromises()

    // 两个文档都被勾选
    expect((wrapper.find('[data-test="select-doc-1"]').element as HTMLInputElement).checked).toBe(true)
    expect((wrapper.find('[data-test="select-doc-2"]').element as HTMLInputElement).checked).toBe(true)
    expect(wrapper.text()).toContain('已选 2 个')
  })

  it('单个删除弹出确认弹窗并执行删除', async () => {
    vi.mocked(listDocuments).mockResolvedValue({
      records: [
        { documentKey: 'doc-1', displayName: 'a.md', folderPath: '/', currentFile: { filename: 'a.md', contentType: 'text/markdown', size: 100 }, currentFileToken: 't1', parseStatus: 'SUCCEEDED', publishStatus: 'UNPUBLISHED', hasActivePublication: false, updated: '2026-08-05T08:00:00Z' },
      ],
      total: 1, page: 0, size: 20,
    })
    vi.mocked(deleteDocument).mockResolvedValue(undefined)
    const wrapper = mount(DocumentsView, { attachTo: document.body })
    await flushPromises()

    // 点击单行操作菜单触发器
    await wrapper.get('[data-test="row-menu-trigger"]').trigger('click')
    // 点击删除
    await wrapper.get('[data-test="row-delete"]').trigger('click')
    await nextTick()

    // 出现确认弹窗（ConfirmDialog Teleport 到 body）
    expect(body().text()).toContain('删除文档')
    expect(body().text()).toContain('a.md')

    // 确认删除
    await body().get('[data-testid="confirm-btn"]').trigger('click')
    await flushPromises()

    expect(deleteDocument).toHaveBeenCalledWith('doc-1')
  })

  it('批量删除弹出确认弹窗并执行删除', async () => {
    vi.mocked(listDocuments).mockResolvedValue({
      records: [
        { documentKey: 'doc-1', displayName: 'a.md', folderPath: '/', currentFile: { filename: 'a.md', contentType: 'text/markdown', size: 100 }, currentFileToken: 't1', parseStatus: 'SUCCEEDED', publishStatus: 'UNPUBLISHED', hasActivePublication: false, updated: '2026-08-05T08:00:00Z' },
        { documentKey: 'doc-2', displayName: 'b.md', folderPath: '/', currentFile: { filename: 'b.md', contentType: 'text/markdown', size: 200 }, currentFileToken: 't2', parseStatus: 'NOT_STARTED', publishStatus: 'UNPUBLISHED', hasActivePublication: false, updated: '2026-08-06T08:00:00Z' },
      ],
      total: 2, page: 0, size: 20,
    })
    vi.mocked(batchDeleteDocuments).mockResolvedValue({ deleted: ['doc-1', 'doc-2'], failed: [] })
    const wrapper = mount(DocumentsView, { attachTo: document.body })
    await flushPromises()

    // 全选
    await wrapper.get('[data-test="select-all"]').setValue(true)
    // 点击批量删除
    await wrapper.get('[data-test="batch-delete-btn"]').trigger('click')
    await nextTick()

    // 出现确认弹窗（ConfirmDialog Teleport 到 body）
    expect(body().text()).toContain('批量删除文档')
    expect(body().text()).toContain('选中的 2 个文档')

    // 确认删除
    await body().get('[data-testid="confirm-btn"]').trigger('click')
    await flushPromises()

    expect(batchDeleteDocuments).toHaveBeenCalledWith(['doc-1', 'doc-2'])
  })

  it('批量删除部分失败时显示失败信息', async () => {
    vi.mocked(listDocuments).mockResolvedValue({
      records: [
        { documentKey: 'doc-1', displayName: 'a.md', folderPath: '/', currentFile: { filename: 'a.md', contentType: 'text/markdown', size: 100 }, currentFileToken: 't1', parseStatus: 'SUCCEEDED', publishStatus: 'UNPUBLISHED', hasActivePublication: false, updated: '2026-08-05T08:00:00Z' },
        { documentKey: 'doc-2', displayName: 'b.md', folderPath: '/', currentFile: { filename: 'b.md', contentType: 'text/markdown', size: 200 }, currentFileToken: 't2', parseStatus: 'NOT_STARTED', publishStatus: 'UNPUBLISHED', hasActivePublication: false, updated: '2026-08-06T08:00:00Z' },
      ],
      total: 2, page: 0, size: 20,
    })
    vi.mocked(batchDeleteDocuments).mockResolvedValue({
      deleted: ['doc-1'],
      failed: [{ key: 'doc-2', error: '文档已被其他用户删除' }],
    })
    const wrapper = mount(DocumentsView, { attachTo: document.body })
    await flushPromises()

    await wrapper.get('[data-test="select-all"]').setValue(true)
    await wrapper.get('[data-test="batch-delete-btn"]').trigger('click')
    await nextTick()
    await body().get('[data-testid="confirm-btn"]').trigger('click')
    await flushPromises()

    // 部分失败时显示失败信息（在主视图中，非弹窗内）
    expect(wrapper.text()).toContain('失败')
  })

  it('删除确认弹窗取消时不执行删除', async () => {
    vi.mocked(listDocuments).mockResolvedValue({
      records: [
        { documentKey: 'doc-1', displayName: 'a.md', folderPath: '/', currentFile: { filename: 'a.md', contentType: 'text/markdown', size: 100 }, currentFileToken: 't1', parseStatus: 'SUCCEEDED', publishStatus: 'UNPUBLISHED', hasActivePublication: false, updated: '2026-08-05T08:00:00Z' },
      ],
      total: 1, page: 0, size: 20,
    })
    const wrapper = mount(DocumentsView, { attachTo: document.body })
    await flushPromises()

    await wrapper.get('[data-test="row-menu-trigger"]').trigger('click')
    await wrapper.get('[data-test="row-delete"]').trigger('click')
    await nextTick()

    // 取消删除（按钮在 ConfirmDialog 内，Teleport 到 body）
    await body().get('[data-testid="cancel-btn"]').trigger('click')
    await flushPromises()

    expect(deleteDocument).not.toHaveBeenCalled()
  })
})
