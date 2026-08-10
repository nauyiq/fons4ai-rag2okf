import { flushPromises, mount, DOMWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'

import DocumentDetailView from '../DocumentDetailView.vue'
import {
  getChunkPreview,
  getDocument,
  getParsePreview,
  getSourceContent,
  rechunkDocument,
  retryTask,
  triggerParse,
  triggerPublish,
} from '../../../api/documents'
import { useWorkspaceStore } from '../../../stores/workspace'

vi.mock('../../../api/documents', () => ({
  getChunkPreview: vi.fn(),
  getDocument: vi.fn(),
  getParsePreview: vi.fn(),
  getSourceContent: vi.fn(),
  rechunkDocument: vi.fn(),
  retryTask: vi.fn(),
  triggerParse: vi.fn(),
  triggerPublish: vi.fn(),
  updateDocumentFile: vi.fn(),
}))
vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { knowledgeBaseKey: 'loan-policy', documentKey: 'doc-1' } }),
  useRouter: () => ({ push: vi.fn() }),
}))

function makeDoc(overrides: Record<string, unknown> = {}) {
  return {
    documentKey: 'doc-1',
    knowledgeBaseKey: 'loan-policy',
    displayName: 'policy.md',
    folderPath: '/',
    currentFile: { filename: 'policy.md', contentType: 'text/markdown', size: 1024 },
    currentFileToken: 'opaque',
    parseStatus: 'SUCCEEDED',
    publishStatus: 'UNPUBLISHED',
    hasActivePublication: false,
    updated: '2026-08-05T08:00:00Z',
    ...overrides,
  }
}

function makeSource() {
  return { blobUrl: 'blob:mock', contentType: 'text/markdown', filename: 'policy.md' }
}

describe('DocumentDetailView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
    useWorkspaceStore().setWorkspace({ key: 'personal', name: 'Personal', role: 'ADMIN' })
    vi.mocked(getDocument).mockResolvedValue(makeDoc())
    vi.mocked(getChunkPreview).mockResolvedValue({ hasChunk: true, currentChunkRevisionKey: 'chunk-token', chunkProfile: {}, parentCount: 1, childCount: 2, total: 2 })
    vi.mocked(getParsePreview).mockResolvedValue({ hasParse: true, parserProfile: 'native', blockCount: 3 })
    vi.mocked(getSourceContent).mockResolvedValue(makeSource())
  })

  it('renders source file preview on the left and chunk details on the right', async () => {
    const wrapper = mount(DocumentDetailView)
    await flushPromises()
    expect(wrapper.find('[data-test="source-panel"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="source-file-preview"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="chunk-card"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="parse-card"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="publish-card"]').exists()).toBe(true)
  })

  it('does not submit destructive rechunk when the user cancels', async () => {
    const wrapper = mount(DocumentDetailView)
    await flushPromises()
    const rechunkBtn = wrapper.findAll('button').find(b => b.text().includes('重新分块'))
    expect(rechunkBtn).toBeTruthy()
    await rechunkBtn!.trigger('click')
    await nextTick()
    const body = new DOMWrapper(document.body)
    await body.get('[data-test="cancel-rechunk"]').trigger('click')
    expect(rechunkDocument).not.toHaveBeenCalled()
  })

  it('disables parse button when parseStatus is RUNNING', async () => {
    vi.mocked(getDocument).mockResolvedValue(makeDoc({ parseStatus: 'RUNNING', latestTask: { taskKey: 't1', taskType: 'PARSE', status: 'RUNNING', stage: '提取文本', progress: 45, attempt: 1, maxAttempts: 3, updated: '2026-08-05T08:00:00Z' } }))
    const wrapper = mount(DocumentDetailView)
    await flushPromises()
    const parseBtn = wrapper.find('[data-test="parse-button"]')
    expect(parseBtn.attributes('disabled')).toBeDefined()
    expect(wrapper.find('[data-test="parse-progress"]').exists()).toBe(true)
  })

  it('shows parse failure and allows retry', async () => {
    vi.mocked(getDocument).mockResolvedValue(makeDoc({ parseStatus: 'FAILED', latestTask: { taskKey: 't1', taskType: 'PARSE', status: 'FAILED', progress: 0, attempt: 1, maxAttempts: 3, errorCode: 'PARSE_ERROR', errorMessage: '文件格式不支持', updated: '2026-08-05T08:00:00Z' } }))
    const wrapper = mount(DocumentDetailView)
    await flushPromises()
    expect(wrapper.find('[data-test="parse-failed"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('文件格式不支持')
    // 解析按钮仍然可用（非 disabled）
    const parseBtn = wrapper.find('[data-test="parse-button"]')
    expect(parseBtn.attributes('disabled')).toBeUndefined()
  })

  it('triggers parse and shows success feedback', async () => {
    vi.mocked(triggerParse).mockResolvedValue({ taskKey: 't2' })
    const wrapper = mount(DocumentDetailView)
    await flushPromises()
    const parseBtn = wrapper.find('[data-test="parse-button"]')
    await parseBtn.trigger('click')
    await flushPromises()
    expect(triggerParse).toHaveBeenCalledWith('doc-1')
    expect(wrapper.find('[data-test="parse-success"]').exists()).toBe(true)
  })

  it('triggers publish independently without blocking parse', async () => {
    vi.mocked(triggerPublish).mockResolvedValue({ taskKey: 't3' })
    const wrapper = mount(DocumentDetailView)
    await flushPromises()
    const publishBtn = wrapper.find('[data-test="publish-button"]')
    expect(publishBtn.exists()).toBe(true)
    await publishBtn.trigger('click')
    await flushPromises()
    expect(triggerPublish).toHaveBeenCalledWith('doc-1')
    // 解析按钮不受发布操作影响
    const parseBtn = wrapper.find('[data-test="parse-button"]')
    expect(parseBtn.attributes('disabled')).toBeUndefined()
  })

  it('未生成成功分块时禁止发布，避免未解析文档进入检索', async () => {
    vi.mocked(getDocument).mockResolvedValue(makeDoc({ parseStatus: 'NOT_STARTED' }))
    vi.mocked(getChunkPreview).mockResolvedValue({ hasChunk: false, parentCount: 0, childCount: 0, total: 0 })

    const wrapper = mount(DocumentDetailView)
    await flushPromises()

    expect(wrapper.get('[data-test="publish-button"]').attributes('disabled')).toBeDefined()
    expect(triggerPublish).not.toHaveBeenCalled()
  })

  it('最新发布失败时保留旧发布内容的可用提示', async () => {
    vi.mocked(getDocument).mockResolvedValue(makeDoc({
      publishStatus: 'PUBLISH_FAILED',
      hasActivePublication: true,
    }))

    const wrapper = mount(DocumentDetailView)
    await flushPromises()

    expect(wrapper.text()).toContain('此前已发布内容继续对检索可用')
  })

  it('确认重新分块时携带当前分块版本，取消路径不改变分块', async () => {
    vi.mocked(rechunkDocument).mockResolvedValue({ taskKey: 'rechunk-1' })
    const wrapper = mount(DocumentDetailView, { attachTo: document.body })
    await flushPromises()

    await wrapper.get('[data-test="rechunk-button"]').trigger('click')
    await nextTick()
    const body = new DOMWrapper(document.body)
    await body.get('[data-test="confirm-rechunk"]').trigger('click')
    await flushPromises()

    expect(rechunkDocument).toHaveBeenCalledWith('doc-1', 'chunk-token', {})
  })

  it('解析失败的重试只提交最新失败任务，不触发发布', async () => {
    vi.mocked(getDocument).mockResolvedValue(makeDoc({
      parseStatus: 'FAILED',
      latestTask: {
        taskKey: 'task-failed-1',
        taskType: 'PARSE',
        status: 'FAILED',
        progress: 0,
        attempt: 1,
        maxAttempts: 3,
        errorCode: 'PARSE_ERROR',
        errorMessage: '解析失败',
        updated: '2026-08-05T08:00:00Z',
      },
    }))
    vi.mocked(retryTask).mockResolvedValue('task-retry-1')

    const wrapper = mount(DocumentDetailView)
    await flushPromises()
    await wrapper.get('[data-test="retry-task"]').trigger('click')
    await flushPromises()

    expect(retryTask).toHaveBeenCalledWith('task-failed-1')
    expect(triggerPublish).not.toHaveBeenCalled()
  })
})
