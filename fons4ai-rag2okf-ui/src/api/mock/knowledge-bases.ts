/**
 * 知识库演示数据。
 * 忠实模拟真实接口响应结构，含 ownerUserKey/canDelete 字段（T006 已正式扩展 KnowledgeBaseSummary）。
 * 仅在 demo 模式下使用，完全在内存中，不写入 localStorage 真实 key。
 */
import type { KnowledgeBaseSummary, KnowledgeBase, PageResponse, SaveKnowledgeBaseInput } from '../knowledge-bases'

const now = new Date().toISOString()
const oneHourAgo = new Date(Date.now() - 3600_000).toISOString()
const oneDayAgo = new Date(Date.now() - 86400_000).toISOString()

const knowledgeBases: KnowledgeBase[] = [
  {
    knowledgeBaseKey: 'kb-demo-001',
    name: '产品帮助文档',
    description: 'Rag2OKF 平台的产品使用手册和常见问题解答。',
    autoParse: true,
    autoPublish: false,
    updated: oneHourAgo,
    ownerUserKey: 'user-demo-001',
    canDelete: true,
    workspaceKey: 'ws-demo',
    parserProfile: 'DEFAULT',
    chunkProfile: { strategy: 'fixed', chunkSize: 512, overlap: 64, titleLevel: null },
    modelBindings: [
      { usageType: 'CHAT', modelConfigKey: 'mc-demo-001' },
      { usageType: 'EMBEDDING', modelConfigKey: 'mc-demo-002' },
    ],
    revision: 3,
  },
  {
    knowledgeBaseKey: 'kb-demo-002',
    name: '金融风控知识库',
    description: '风控决策规则、反洗钱法规和案例库。',
    autoParse: true,
    autoPublish: true,
    updated: oneDayAgo,
    ownerUserKey: 'user-demo-001',
    canDelete: true,
    workspaceKey: 'ws-demo',
    parserProfile: 'DEFAULT',
    chunkProfile: { strategy: 'fixed', chunkSize: 768, overlap: 128, titleLevel: 2 },
    modelBindings: [
      { usageType: 'CHAT', modelConfigKey: 'mc-demo-001' },
      { usageType: 'EMBEDDING', modelConfigKey: 'mc-demo-002' },
    ],
    revision: 5,
  },
  {
    knowledgeBaseKey: 'kb-demo-003',
    name: '团队协作空间（只读示例）',
    description: '由其他用户创建的知识库，当前用户无删除权限。',
    autoParse: false,
    autoPublish: false,
    updated: now,
    ownerUserKey: 'user-demo-002',
    canDelete: false,
    workspaceKey: 'ws-demo',
    parserProfile: 'DEFAULT',
    chunkProfile: { strategy: 'fixed', chunkSize: 512, overlap: 64, titleLevel: null },
    modelBindings: [],
    revision: 1,
  },
]

/** 模拟分页查询知识库列表。 */
export function mockListKnowledgeBases(workspaceKey: string, page = 0, size = 20): PageResponse<KnowledgeBaseSummary> {
  const filtered = knowledgeBases.filter((kb) => kb.workspaceKey === workspaceKey)
  const start = page * size
  const records = filtered.slice(start, start + size).map(toSummary)
  return { records, total: filtered.length, page, size }
}

/** 模拟查询单个知识库详情。 */
export function mockGetKnowledgeBase(knowledgeBaseKey: string): KnowledgeBase | undefined {
  return knowledgeBases.find((kb) => kb.knowledgeBaseKey === knowledgeBaseKey)
}

/** 模拟创建知识库。 */
export function mockCreateKnowledgeBase(workspaceKey: string, input: SaveKnowledgeBaseInput): KnowledgeBase {
  const created: KnowledgeBase = {
    knowledgeBaseKey: `kb-demo-${Date.now()}`,
    name: input.name ?? '',
    description: input.description ?? '',
    autoParse: input.autoParse ?? false,
    autoPublish: input.autoPublish ?? false,
    updated: new Date().toISOString(),
    ownerUserKey: 'user-demo-001',
    canDelete: true,
    workspaceKey,
    parserProfile: input.parserProfile ?? 'DEFAULT',
    chunkProfile: input.chunkProfile ?? { strategy: 'fixed', chunkSize: 512, overlap: 64, titleLevel: null },
    modelBindings: input.modelBindings ?? [],
    revision: 1,
  }
  knowledgeBases.push(created)
  return created
}

/** 模拟更新知识库（含重命名）。 */
export function mockUpdateKnowledgeBase(knowledgeBaseKey: string, input: SaveKnowledgeBaseInput): KnowledgeBase | undefined {
  const idx = knowledgeBases.findIndex((kb) => kb.knowledgeBaseKey === knowledgeBaseKey)
  if (idx === -1) return undefined
  knowledgeBases[idx] = {
    ...knowledgeBases[idx],
    ...input,
    updated: new Date().toISOString(),
  }
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
export function mockAllKnowledgeBases(): KnowledgeBase[] {
  return knowledgeBases
}

/** 将知识库详情裁剪为列表摘要（剥离设置字段）。 */
function toSummary(kb: KnowledgeBase): KnowledgeBaseSummary {
  const { workspaceKey: _ws, parserProfile: _pp, chunkProfile: _cp, modelBindings: _mb, revision: _rev, ...summary } = kb
  return summary
}
