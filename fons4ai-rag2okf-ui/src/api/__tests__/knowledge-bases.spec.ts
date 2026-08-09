import { describe, it, expect, beforeEach, vi } from 'vitest'

/**
 * 知识库 API 层测试（T006）。
 *
 * 验证点：
 * - KnowledgeBaseSummary 含 ownerUserKey 和 canDelete 字段
 * - ModelBinding 使用 profileKey 字段（两步式对齐）
 * - demo 模式下走 mock 数据，不发起网络请求
 * - real 模式下走 http.request，路径和方法匹配技术设计说明书 §3.2
 * - deleteKnowledgeBase 在 demo 模式下尊重 canDelete 权限
 */

vi.mock('../http', () => ({
  request: vi.fn(),
}))

import { request } from '../http'

const mockedRequest = vi.mocked(request)

describe('knowledge-bases API - 扩展契约导出', () => {
  beforeEach(() => {
    vi.resetModules()
    vi.stubEnv('VITE_RAG2OKF_DATA_SOURCE', 'demo')
  })

  it('导出 deleteKnowledgeBase 函数', async () => {
    const api = await import('../knowledge-bases')
    expect(typeof api.deleteKnowledgeBase).toBe('function')
  })

  it('KnowledgeBaseSummary 类型含 ownerUserKey 和 canDelete 字段', async () => {
    const { listKnowledgeBases } = await import('../knowledge-bases')
    const result = await listKnowledgeBases('ws-demo')
    expect(result.records.length).toBeGreaterThan(0)
    const first = result.records[0]
    expect(first).toHaveProperty('ownerUserKey')
    expect(first).toHaveProperty('canDelete')
  })
})

describe('knowledge-bases API - demo 模式走 mock 数据', () => {
  beforeEach(() => {
    vi.resetModules()
    vi.stubEnv('VITE_RAG2OKF_DATA_SOURCE', 'demo')
    mockedRequest.mockClear()
  })

  it('listKnowledgeBases 返回含 ownerUserKey/canDelete 的列表', async () => {
    const { listKnowledgeBases } = await import('../knowledge-bases')
    const result = await listKnowledgeBases('ws-demo')
    expect(result.records.length).toBe(3)
    const canDeleteKb = result.records.find((kb) => kb.canDelete)
    expect(canDeleteKb?.ownerUserKey).toBeDefined()
    const nonDeletable = result.records.find((kb) => !kb.canDelete)
    expect(nonDeletable?.ownerUserKey).toBe('user-demo-002')
    expect(mockedRequest).not.toHaveBeenCalled()
  })

  it('deleteKnowledgeBase 删除 canDelete=true 的知识库', async () => {
    const { deleteKnowledgeBase, listKnowledgeBases } = await import('../knowledge-bases')
    const before = await listKnowledgeBases('ws-demo')
    const deletable = before.records.find((kb) => kb.canDelete)
    expect(deletable).toBeDefined()
    const ok = await deleteKnowledgeBase(deletable!.knowledgeBaseKey)
    expect(ok).toBe(true)
    const after = await listKnowledgeBases('ws-demo')
    expect(after.records.length).toBe(before.records.length - 1)
    expect(mockedRequest).not.toHaveBeenCalled()
  })

  it('deleteKnowledgeBase 不能删除 canDelete=false 的知识库', async () => {
    const { deleteKnowledgeBase, listKnowledgeBases } = await import('../knowledge-bases')
    const list = await listKnowledgeBases('ws-demo')
    const nonDeletable = list.records.find((kb) => !kb.canDelete)
    expect(nonDeletable).toBeDefined()
    const ok = await deleteKnowledgeBase(nonDeletable!.knowledgeBaseKey)
    expect(ok).toBe(false)
    expect(mockedRequest).not.toHaveBeenCalled()
  })

  it('getKnowledgeBase 返回详情含 profileKey 绑定', async () => {
    const { getKnowledgeBase } = await import('../knowledge-bases')
    const kb = await getKnowledgeBase('kb-demo-001')
    expect(kb).toBeDefined()
    expect(kb!.modelBindings.length).toBeGreaterThan(0)
    expect(kb!.modelBindings[0]).toHaveProperty('profileKey')
    expect(kb!.modelBindings[0]).not.toHaveProperty('modelConfigKey')
    expect(mockedRequest).not.toHaveBeenCalled()
  })

  it('createKnowledgeBase 和 updateKnowledgeBase 在 demo 模式下走 mock', async () => {
    const { createKnowledgeBase, updateKnowledgeBase, listKnowledgeBases } = await import('../knowledge-bases')
    const created = await createKnowledgeBase('ws-demo', {
      name: '新知识库',
      revision: 1,
    })
    expect(created.knowledgeBaseKey).toBeDefined()
    expect(created.canDelete).toBe(true)
    const updated = await updateKnowledgeBase(created.knowledgeBaseKey, {
      name: '重命名后',
      revision: 2,
    })
    expect(updated?.name).toBe('重命名后')
    expect(mockedRequest).not.toHaveBeenCalled()
  })
})

describe('knowledge-bases API - real 模式走 http.request', () => {
  beforeEach(() => {
    vi.resetModules()
    vi.stubEnv('VITE_RAG2OKF_DATA_SOURCE', 'real')
    mockedRequest.mockClear()
  })

  it('listKnowledgeBases 调用 GET /workspaces/{key}/knowledge-bases', async () => {
    mockedRequest.mockResolvedValueOnce({ records: [], total: 0, page: 0, size: 20 } as never)
    const { listKnowledgeBases } = await import('../knowledge-bases')
    await listKnowledgeBases('ws-001')
    expect(mockedRequest).toHaveBeenCalledTimes(1)
    const [path] = mockedRequest.mock.calls[0]
    expect(path).toContain('/workspaces/ws-001/knowledge-bases')
  })

  it('getKnowledgeBase 调用 GET /knowledge-bases/{key}', async () => {
    mockedRequest.mockResolvedValueOnce({} as never)
    const { getKnowledgeBase } = await import('../knowledge-bases')
    await getKnowledgeBase('kb-001')
    expect(mockedRequest).toHaveBeenCalledWith('/knowledge-bases/kb-001')
  })

  it('createKnowledgeBase 调用 POST /workspaces/{key}/knowledge-bases', async () => {
    mockedRequest.mockResolvedValueOnce({} as never)
    const { createKnowledgeBase } = await import('../knowledge-bases')
    await createKnowledgeBase('ws-001', { name: '测试', revision: 1 })
    const [path, init] = mockedRequest.mock.calls[0]
    expect(path).toBe('/workspaces/ws-001/knowledge-bases')
    expect(init?.method).toBe('POST')
  })

  it('updateKnowledgeBase 调用 PATCH /knowledge-bases/{key}', async () => {
    mockedRequest.mockResolvedValueOnce({} as never)
    const { updateKnowledgeBase } = await import('../knowledge-bases')
    await updateKnowledgeBase('kb-001', { name: '更新', revision: 2 })
    const [path, init] = mockedRequest.mock.calls[0]
    expect(path).toBe('/knowledge-bases/kb-001')
    expect(init?.method).toBe('PATCH')
  })

  it('deleteKnowledgeBase 调用 DELETE /knowledge-bases/{key}', async () => {
    mockedRequest.mockResolvedValueOnce(true as never)
    const { deleteKnowledgeBase } = await import('../knowledge-bases')
    await deleteKnowledgeBase('kb-001')
    expect(mockedRequest).toHaveBeenCalledTimes(1)
    const [path, init] = mockedRequest.mock.calls[0]
    expect(path).toBe('/knowledge-bases/kb-001')
    expect(init?.method).toBe('DELETE')
  })
})
