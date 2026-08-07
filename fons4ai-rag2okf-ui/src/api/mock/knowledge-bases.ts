/**
 * 知识库演示数据。
 * 忠实模拟真实接口响应结构，含 ownerUserKey/canDelete 字段（T006 扩展类型后对齐）。
 * 仅在 demo 模式下使用，完全在内存中，不写入 localStorage 真实 key。
 */
import type { KnowledgeBaseSummary, KnowledgeBase, PageResponse } from '../knowledge-bases'

/** 扩展 KnowledgeBaseSummary，加入创建者标识和删除权限标识（T006 会正式扩展类型）。 */
export interface MockKnowledgeBaseSummary extends KnowledgeBaseSummary {
  ownerUserKey: string
  canDelete: boolean
}

export interface MockKnowledgeBase extends KnowledgeBase {
  ownerUserKey: string
  canDelete: boolean
}

const now = new Date().toISOString()
const oneHourAgo = new Date(Date.now() - 3600_000).toISOString()
const oneDayAgo = new Date(Date.now() - 86400_000).toISOString()

const knowledgeBases: MockKnowledgeBase[] = [
  {
    knowledgeBaseKey: 'kb-demo-001',
    name: '产品帮助文档',
    description: 'Rag2OKF 平台的产品使用手册和常见问题解答。',
    autoParse: true,
    autoPublish: false,
    updated: oneHourAgo,
    workspaceKey: 'ws-demo',
    parserProfile: 'DEFAULT',
    chunkProfile: { strategy: 'fixed', chunkSize: 512, overlap: 64, titleLevel: null },
    modelBindings: [
      { usageType: 'CHAT', modelProfileKey: 'mc-demo-001' },
      { usageType: 'EMBEDDING', modelProfileKey: 'mc-demo-002' },
    ],
    revision: 3,
    ownerUserKey: 'user-demo-001',
    canDelete: true,
  },
  {
    knowledgeBaseKey: 'kb-demo-002',
    name: '金融风控知识库',
    description: '风控决策规则、反洗钱法规和案例库。',
    autoParse: true,
    autoPublish: true,
    updated: oneDayAgo,
    workspaceKey: 'ws-demo',
    parserProfile: 'DEFAULT',
    chunkProfile: { strategy: 'fixed', chunkSize: 768, overlap: 128, titleLevel: 2 },
    modelBindings: [
      { usageType: 'CHAT', modelProfileKey: 'mc-demo-001' },
      { usageType: 'EMBEDDING', modelProfileKey: 'mc-demo-002' },
    ],
    revision: 5,
    ownerUserKey: 'user-demo-001',
    canDelete: true,
  },
  {
    knowledgeBaseKey: 'kb-demo-003',
    name: '团队协作空间（只读示例）',
    description: '由其他用户创建的知识库，当前用户无删除权限。',
    autoParse: false,
    autoPublish: false,
    updated: now,
    workspaceKey: 'ws-demo',
    parserProfile: 'DEFAULT',
    chunkProfile: { strategy: 'fixed', chunkSize: 512, overlap: 64, titleLevel: null },
    modelBindings: [],
    revision: 1,
    ownerUserKey: 'user-demo-002',
    canDelete: false,
  },
]

/** 模拟分页查询知识库列表。 */
export function mockListKnowledgeBases(workspaceKey: string, page = 0, size = 20): PageResponse<MockKnowledgeBaseSummary> {
  const filtered = knowledgeBases.filter((kb) => kb.workspaceKey === workspaceKey)
  const start = page * size
  const records = filtered.slice(start, start + size).map(({ workspaceKey: _ws, parserProfile: _pp, chunkProfile: _cp, modelBindings: _mb, revision: _rev, ...summary }) => summary)
  return { records, total: filtered.length, page, size }
}

/** 模拟查询单个知识库详情。 */
export function mockGetKnowledgeBase(knowledgeBaseKey: string): MockKnowledgeBase | undefined {
  return knowledgeBases.find((kb) => kb.knowledgeBaseKey === knowledgeBaseKey)
}

/** 模拟创建知识库。 */
export function mockCreateKnowledgeBase(input: Partial<MockKnowledgeBase> & { name: string }): MockKnowledgeBase {
  const created: MockKnowledgeBase = {
    knowledgeBaseKey: `kb-demo-${Date.now()}`,
    name: input.name,
    description: input.description ?? '',
    autoParse: input.autoParse ?? false,
    autoPublish: input.autoPublish ?? false,
    updated: new Date().toISOString(),
    workspaceKey: input.workspaceKey ?? 'ws-demo',
    parserProfile: input.parserProfile ?? 'DEFAULT',
    chunkProfile: input.chunkProfile ?? { strategy: 'fixed', chunkSize: 512, overlap: 64, titleLevel: null },
    modelBindings: input.modelBindings ?? [],
    revision: 1,
    ownerUserKey: input.ownerUserKey ?? 'user-demo-001',
    canDelete: true,
  }
  knowledgeBases.push(created)
  return created
}

/** 模拟更新知识库（含重命名）。 */
export function mockUpdateKnowledgeBase(knowledgeBaseKey: string, input: Partial<MockKnowledgeBase>): MockKnowledgeBase | undefined {
  const idx = knowledgeBases.findIndex((kb) => kb.knowledgeBaseKey === knowledgeBaseKey)
  if (idx === -1) return undefined
  knowledgeBases[idx] = { ...knowledgeBases[idx], ...input, updated: new Date().toISOString() }
  return knowledgeBases[idx]
}

/** 模拟删除知识库（仅 canDelete 为 true 时可删）。 */
export function mockDeleteKnowledgeBase(knowledgeBaseKey: string): boolean {
  const idx = knowledgeBases.findIndex((kb) => kb.knowledgeBaseKey === knowledgeBaseKey)
  if (idx === -1 || !knowledgeBases[idx].canDelete) return false
  knowledgeBases.splice(idx, 1)
  return true
}

/** 获取全部 mock 知识库（供调试或其他 mock 模块引用）。 */
export function mockAllKnowledgeBases(): MockKnowledgeBase[] {
  return knowledgeBases
}
