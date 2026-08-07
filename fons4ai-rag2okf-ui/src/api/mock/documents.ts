/**
 * 文档演示数据。
 * 忠实模拟真实接口响应结构（DocumentSummary、DocumentDetail、TaskSummary、ChunkPreview、ParsePreview）。
 * 仅在 demo 模式下使用，完全在内存中，不写入 localStorage 真实 key。
 */
import type {
  DocumentSummary,
  DocumentDetail,
  PageResponse,
  OperationAccepted,
  ChunkPreview,
  ParsePreview,
  TaskSummary,
} from '../documents'

const now = new Date().toISOString()
const oneHourAgo = new Date(Date.now() - 3600_000).toISOString()
const twoHoursAgo = new Date(Date.now() - 7200_000).toISOString()

const documents: (DocumentDetail & { knowledgeBaseKey: string })[] = [
  {
    documentKey: 'doc-demo-001',
    displayName: '快速入门指南.md',
    folderPath: '/',
    currentFile: { filename: 'quick-start.md', contentType: 'text/markdown', size: 12_400 },
    currentFileToken: 'mock-token-001',
    parseStatus: 'SUCCEEDED',
    publishStatus: 'PUBLISHED',
    hasActivePublication: true,
    latestTask: {
      taskKey: 'task-demo-001',
      taskType: 'PARSE',
      status: 'SUCCEEDED',
      stage: '完成',
      progress: 100,
      attempt: 1,
      maxAttempts: 3,
      updated: oneHourAgo,
    },
    updated: oneHourAgo,
    knowledgeBaseKey: 'kb-demo-001',
  },
  {
    documentKey: 'doc-demo-002',
    displayName: '风控规则手册.pdf',
    folderPath: '/合规材料',
    currentFile: { filename: 'risk-rules.pdf', contentType: 'application/pdf', size: 1_240_000 },
    currentFileToken: 'mock-token-002',
    parseStatus: 'RUNNING',
    publishStatus: 'UNPUBLISHED',
    hasActivePublication: false,
    latestTask: {
      taskKey: 'task-demo-002',
      taskType: 'PARSE',
      status: 'RUNNING',
      stage: '正在提取文本',
      progress: 45,
      attempt: 1,
      maxAttempts: 3,
      updated: now,
    },
    updated: now,
    knowledgeBaseKey: 'kb-demo-002',
  },
  {
    documentKey: 'doc-demo-003',
    displayName: '系统架构图.png',
    folderPath: '/图片资料',
    currentFile: { filename: 'architecture.png', contentType: 'image/png', size: 560_000 },
    currentFileToken: 'mock-token-003',
    parseStatus: 'FAILED',
    publishStatus: 'UNPUBLISHED',
    hasActivePublication: false,
    latestTask: {
      taskKey: 'task-demo-003',
      taskType: 'PARSE',
      status: 'FAILED',
      stage: '失败',
      progress: 0,
      attempt: 3,
      maxAttempts: 3,
      errorCode: 'PARSE_OCR_UNSUPPORTED',
      errorMessage: '当前解析器暂不支持图片 OCR，请使用支持文本的文件格式。',
      updated: twoHoursAgo,
    },
    updated: twoHoursAgo,
    knowledgeBaseKey: 'kb-demo-001',
  },
  {
    documentKey: 'doc-demo-004',
    displayName: '产品需求文档.docx',
    folderPath: '/合规材料/2024Q4',
    currentFile: { filename: 'prd.docx', contentType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', size: 890_000 },
    currentFileToken: 'mock-token-004',
    parseStatus: 'PENDING',
    publishStatus: 'UNPUBLISHED',
    hasActivePublication: false,
    updated: now,
    knowledgeBaseKey: 'kb-demo-001',
  },
]

/** 模拟分页查询文档列表。 */
export function mockListDocuments(knowledgeBaseKey: string, page = 0, size = 20, folderPath?: string): PageResponse<DocumentSummary> {
  let filtered = documents.filter((doc) => doc.knowledgeBaseKey === knowledgeBaseKey)
  if (folderPath !== undefined) {
    filtered = filtered.filter((doc) => doc.folderPath === folderPath)
  }
  const start = page * size
  const records = filtered.slice(start, start + size).map(({ knowledgeBaseKey: _kb, ...summary }) => summary)
  return { records, total: filtered.length, page, size }
}

/** 模拟查询单个文档详情。 */
export function mockGetDocument(documentKey: string): DocumentDetail | undefined {
  const doc = documents.find((d) => d.documentKey === documentKey)
  if (!doc) return undefined
  const { knowledgeBaseKey: _kb, ...detail } = doc
  return { ...detail, knowledgeBaseKey: doc.knowledgeBaseKey }
}

/** 模拟触发解析。 */
export function mockTriggerParse(documentKey: string): OperationAccepted {
  const doc = documents.find((d) => d.documentKey === documentKey)
  if (doc) {
    doc.parseStatus = 'QUEUED'
    doc.latestTask = {
      taskKey: `task-${Date.now()}`,
      taskType: 'PARSE',
      status: 'QUEUED',
      stage: '排队中',
      progress: 0,
      attempt: 1,
      maxAttempts: 3,
      updated: new Date().toISOString(),
    }
    return { taskKey: doc.latestTask.taskKey, documentKey }
  }
  return { documentKey }
}

/** 模拟触发发布。 */
export function mockTriggerPublish(documentKey: string): OperationAccepted {
  const doc = documents.find((d) => d.documentKey === documentKey)
  if (doc) {
    doc.publishStatus = 'PUBLISHING'
    doc.latestTask = {
      taskKey: `task-${Date.now()}`,
      taskType: 'PUBLISH',
      status: 'QUEUED',
      stage: '排队中',
      progress: 0,
      attempt: 1,
      maxAttempts: 3,
      updated: new Date().toISOString(),
    }
    return { taskKey: doc.latestTask.taskKey, documentKey }
  }
  return { documentKey }
}

/** 模拟查询分块预览。 */
export function mockGetChunkPreview(documentKey: string): ChunkPreview {
  const doc = documents.find((d) => d.documentKey === documentKey)
  const hasChunk = doc?.parseStatus === 'SUCCEEDED'
  return {
    hasChunk,
    currentChunkRevisionKey: hasChunk ? `cr-${documentKey}` : undefined,
    chunkProfile: hasChunk ? { strategy: 'fixed', chunkSize: 512, overlap: 64 } : undefined,
    parentCount: hasChunk ? 8 : 0,
    childCount: hasChunk ? 24 : 0,
    total: hasChunk ? 24 : 0,
  }
}

/** 模拟查询解析预览。 */
export function mockGetParsePreview(documentKey: string): ParsePreview {
  const doc = documents.find((d) => d.documentKey === documentKey)
  const hasParse = doc?.parseStatus === 'SUCCEEDED'
  return {
    hasParse,
    parserProfile: hasParse ? 'DEFAULT' : undefined,
    blockCount: hasParse ? 16 : 0,
  }
}

/** 模拟重试任务。 */
export function mockRetryTask(taskKey: string): string {
  return `task-${taskKey}-retry`
}

/** 模拟删除文档（软删除）。 */
export function mockDeleteDocument(documentKey: string): boolean {
  const idx = documents.findIndex((d) => d.documentKey === documentKey)
  if (idx === -1) return false
  documents.splice(idx, 1)
  return true
}

/** 模拟批量删除文档。 */
export function mockBatchDeleteDocuments(documentKeys: string[]): { deleted: string[]; failed: { key: string; error: string }[] } {
  const deleted: string[] = []
  const failed: { key: string; error: string }[] = []
  for (const key of documentKeys) {
    const idx = documents.findIndex((d) => d.documentKey === key)
    if (idx !== -1) {
      documents.splice(idx, 1)
      deleted.push(key)
    } else {
      failed.push({ key, error: '文档不存在或已被删除' })
    }
  }
  return { deleted, failed }
}

/** 获取全部 mock 文档（供调试或其他 mock 模块引用）。 */
export function mockAllDocuments(): (DocumentDetail & { knowledgeBaseKey: string })[] {
  return documents
}

/** 模拟获取最新任务（供 useTaskPolling 轮询使用）。 */
export function mockGetLatestTask(documentKey: string): TaskSummary | undefined {
  return documents.find((d) => d.documentKey === documentKey)?.latestTask
}
