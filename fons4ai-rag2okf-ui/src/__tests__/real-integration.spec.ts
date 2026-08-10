import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../api/http', () => ({
  request: vi.fn(),
  ApiRequestError: class extends Error {
    constructor(
      message: string,
      readonly status: number,
      readonly code?: string,
    ) {
      super(message)
      this.name = 'ApiRequestError'
    }
  },
}))

import { request } from '../api/http'

const mockedRequest = vi.mocked(request)

/**
 * T017 real 模式契约回归。
 *
 * 这里不伪造业务状态机，只验证 real 模式会把完整生命周期动作映射到后端契约；
 * 真实服务的启动、健康和路由证据由实施报告中的服务级 Evidence Matrix 记录。
 */
describe('real 模式前后端契约（T017）', () => {
  beforeEach(() => {
    vi.resetModules()
    vi.stubEnv('VITE_RAG2OKF_DATA_SOURCE', 'real')
    vi.stubEnv('VITE_RAG2OKF_API_BASE_URL', 'http://127.0.0.1:8080')
    mockedRequest.mockReset()
  })

  it('创建和删除知识库均走真实接口且保留服务端删除鉴权', async () => {
    mockedRequest
      .mockResolvedValueOnce({
        knowledgeBaseKey: 'kb-real-001',
        workspaceKey: 'ws-real',
        name: '联调知识库',
        description: '',
        autoParse: false,
        autoPublish: false,
        parserProfile: 'DEFAULT',
        chunkProfile: { strategy: 'PARENT_CHILD', chunkSize: 800, overlap: 80, titleLevel: null },
        modelBindings: [],
        revision: 1,
        ownerUserKey: 'user-real',
        canDelete: true,
        updated: '2026-08-10T00:00:00Z',
      } as never)
      .mockResolvedValueOnce(true as never)

    const { createKnowledgeBase, deleteKnowledgeBase } = await import('../api/knowledge-bases')
    await createKnowledgeBase('ws-real', {
      name: '联调知识库',
      description: '',
      autoParse: false,
      autoPublish: false,
      parserProfile: 'DEFAULT',
      chunkProfile: { strategy: 'PARENT_CHILD', chunkSize: 800, overlap: 80, titleLevel: null },
      modelBindings: [],
      revision: 0,
    })
    await deleteKnowledgeBase('kb-real-001')

    expect(mockedRequest.mock.calls[0][0]).toBe('/workspaces/ws-real/knowledge-bases')
    expect(mockedRequest.mock.calls[0][1]?.method).toBe('POST')
    expect(mockedRequest.mock.calls[1]).toEqual([
      '/knowledge-bases/kb-real-001',
      { method: 'DELETE' },
    ])
  })

  it.each([
    ['PARSE', 'PARSE'],
    ['SKIP', 'SKIP'],
  ] as const)('上传选择 %s 时把 parseMode 原样传给后端', async (_label, parseMode) => {
    mockedRequest.mockResolvedValueOnce({ documentKey: 'doc-real-001' } as never)
    const { uploadDocument } = await import('../api/documents')
    const file = new File(['hello'], 'policy.txt', { type: 'text/plain' })

    await uploadDocument('kb-real-001', file, parseMode, '/联调')

    const [path, init] = mockedRequest.mock.calls[0]
    expect(path).toBe('/knowledge-bases/kb-real-001/documents')
    expect(init?.method).toBe('POST')
    expect(init?.body).toBeInstanceOf(FormData)
    expect((init?.body as FormData).get('parseMode')).toBe(parseMode)
    expect((init?.body as FormData).get('folderPath')).toBe('/联调')
  })

  it('解析、发布和重新分块映射到彼此独立的真实接口', async () => {
    mockedRequest
      .mockResolvedValueOnce({ taskKey: 'task-parse' } as never)
      .mockResolvedValueOnce({ taskKey: 'task-publish' } as never)
      .mockResolvedValueOnce({ taskKey: 'task-rechunk' } as never)

    const { triggerParse, triggerPublish, rechunkDocument } = await import('../api/documents')
    await triggerParse('doc-real-001')
    await triggerPublish('doc-real-001')
    await rechunkDocument('doc-real-001', 'chunk-rev-001', { strategy: 'PARENT_CHILD' })

    expect(mockedRequest.mock.calls[0]).toEqual([
      '/documents/doc-real-001/parse?parseMode=PARSE',
      { method: 'POST' },
    ])
    expect(mockedRequest.mock.calls[1]).toEqual([
      '/documents/doc-real-001/publish',
      { method: 'POST' },
    ])
    const [rechunkPath, rechunkInit] = mockedRequest.mock.calls[2]
    expect(rechunkPath).toBe('/documents/doc-real-001/rechunk')
    expect(rechunkInit?.method).toBe('POST')
    expect(JSON.parse(rechunkInit?.body as string)).toEqual({
      confirmed: true,
      expectedChunkRevisionKey: 'chunk-rev-001',
      chunkProfile: { strategy: 'PARENT_CHILD' },
    })
  })

  it('源文件预览使用后端正式的 /file 下载契约', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      blob: vi.fn().mockResolvedValue(new Blob(['policy'], { type: 'text/plain' })),
      headers: { get: vi.fn().mockReturnValue('attachment; filename="policy.txt"') },
    })
    vi.stubGlobal('fetch', fetchMock)

    const { getSourceContent } = await import('../api/documents')
    await getSourceContent('doc real/001')

    expect(fetchMock).toHaveBeenCalledWith(
      'http://127.0.0.1:8080/documents/doc%20real%2F001/file',
      expect.objectContaining({ headers: expect.any(Object) }),
    )
  })

  it('demo 与 real 的运行时切换不写入 localStorage，real 恢复真实请求', async () => {
    const { useDataSource } = await import('../composables/useDataSource')
    const source = useDataSource()
    source.setMode('demo')
    expect(source.mode.value).toBe('demo')
    expect(localStorage.getItem('VITE_RAG2OKF_DATA_SOURCE')).toBeNull()

    source.setMode('real')
    mockedRequest.mockResolvedValueOnce({ records: [], total: 0, page: 0, size: 20 } as never)
    const { listKnowledgeBases } = await import('../api/knowledge-bases')
    await listKnowledgeBases('ws-real')

    expect(mockedRequest).toHaveBeenCalledWith('/workspaces/ws-real/knowledge-bases?page=0&size=20')
    expect(localStorage.getItem('VITE_RAG2OKF_DATA_SOURCE')).toBeNull()
  })
})
