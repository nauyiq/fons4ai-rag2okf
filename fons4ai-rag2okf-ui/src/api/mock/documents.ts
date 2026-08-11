/**
 * 文档演示数据。
 * 忠实模拟真实接口响应结构（DocumentSummary、DocumentDetail、TaskSummary、ChunkPreview、ParsePreview）。
 * 仅在 demo 模式下使用，完全在内存中，不写入 localStorage 真实 key。
 *
 * 文档全部归属于 kb-demo-001（产品帮助文档），覆盖多级目录与多种文件类型，
 * 便于展示 T009 树状目录、T023 源文件预览、T024 删除与批量删除。
 */
import type {
  DocumentSummary,
  DocumentDetail,
  PageResponse,
  OperationAccepted,
  ChunkPreview,
  ChunkView,
  ParsePreview,
  TaskSummary,
} from '../documents'

const now = new Date().toISOString()
const oneHourAgo = new Date(Date.now() - 3600_000).toISOString()
const twoHoursAgo = new Date(Date.now() - 7200_000).toISOString()
const oneDayAgo = new Date(Date.now() - 86400_000).toISOString()

/** 内部文档存储（含 knowledgeBaseKey），全部归属 kb-demo-001。 */
const documents: (DocumentDetail & { knowledgeBaseKey: string })[] = [
  // === 根目录 ===
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
    documentKey: 'doc-demo-010',
    displayName: 'API参考文档.txt',
    folderPath: '/',
    currentFile: { filename: 'api-ref.txt', contentType: 'text/plain', size: 8_800 },
    currentFileToken: 'mock-token-010',
    parseStatus: 'SUCCEEDED',
    publishStatus: 'UNPUBLISHED',
    hasActivePublication: false,
    updated: twoHoursAgo,
    knowledgeBaseKey: 'kb-demo-001',
  },
  // === /合规材料 ===
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
    knowledgeBaseKey: 'kb-demo-001',
  },
  {
    documentKey: 'doc-demo-011',
    displayName: '反洗钱法规汇编.docx',
    folderPath: '/合规材料',
    currentFile: { filename: 'aml-regs.docx', contentType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', size: 560_000 },
    currentFileToken: 'mock-token-011',
    parseStatus: 'SUCCEEDED',
    publishStatus: 'PUBLISHED',
    hasActivePublication: true,
    updated: oneDayAgo,
    knowledgeBaseKey: 'kb-demo-001',
  },
  // === /合规材料/2024Q4 ===
  {
    documentKey: 'doc-demo-004',
    displayName: '2024Q4季度合规报告.docx',
    folderPath: '/合规材料/2024Q4',
    currentFile: { filename: '2024q4-report.docx', contentType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', size: 890_000 },
    currentFileToken: 'mock-token-004',
    parseStatus: 'NOT_STARTED',
    publishStatus: 'UNPUBLISHED',
    hasActivePublication: false,
    updated: now,
    knowledgeBaseKey: 'kb-demo-001',
  },
  // === /合规材料/2025Q1 ===
  {
    documentKey: 'doc-demo-012',
    displayName: '2025Q1季度合规报告.docx',
    folderPath: '/合规材料/2025Q1',
    currentFile: { filename: '2025q1-report.docx', contentType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', size: 920_000 },
    currentFileToken: 'mock-token-012',
    parseStatus: 'SUCCEEDED',
    publishStatus: 'UNPUBLISHED',
    hasActivePublication: false,
    updated: oneDayAgo,
    knowledgeBaseKey: 'kb-demo-001',
  },
  // === /技术文档 ===
  {
    documentKey: 'doc-demo-005',
    displayName: '系统架构设计.md',
    folderPath: '/技术文档',
    currentFile: { filename: 'architecture.md', contentType: 'text/markdown', size: 24_000 },
    currentFileToken: 'mock-token-005',
    parseStatus: 'SUCCEEDED',
    publishStatus: 'PUBLISHED',
    hasActivePublication: true,
    updated: oneHourAgo,
    knowledgeBaseKey: 'kb-demo-001',
  },
  {
    documentKey: 'doc-demo-006',
    displayName: '部署运维指南.md',
    folderPath: '/技术文档',
    currentFile: { filename: 'deploy-guide.md', contentType: 'text/markdown', size: 18_500 },
    currentFileToken: 'mock-token-006',
    parseStatus: 'FAILED',
    publishStatus: 'UNPUBLISHED',
    hasActivePublication: false,
    latestTask: {
      taskKey: 'task-demo-006',
      taskType: 'PARSE',
      status: 'FAILED',
      stage: '失败',
      progress: 0,
      attempt: 3,
      maxAttempts: 3,
      errorCode: 'PARSE_ENCODING_UNSUPPORTED',
      errorMessage: '文件编码无法识别，请转换为 UTF-8 后重试。',
      updated: twoHoursAgo,
    },
    updated: twoHoursAgo,
    knowledgeBaseKey: 'kb-demo-001',
  },
  // === /技术文档/前端 ===
  {
    documentKey: 'doc-demo-007',
    displayName: '前端组件规范.md',
    folderPath: '/技术文档/前端',
    currentFile: { filename: 'frontend-spec.md', contentType: 'text/markdown', size: 9_600 },
    currentFileToken: 'mock-token-007',
    parseStatus: 'SUCCEEDED',
    publishStatus: 'PUBLISHED',
    hasActivePublication: true,
    updated: oneDayAgo,
    knowledgeBaseKey: 'kb-demo-001',
  },
  // === /图片资料 ===
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
    documentKey: 'doc-demo-008',
    displayName: '业务流程图.png',
    folderPath: '/图片资料',
    currentFile: { filename: 'flowchart.png', contentType: 'image/png', size: 320_000 },
    currentFileToken: 'mock-token-008',
    parseStatus: 'NOT_STARTED',
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

/** 模拟上传单个文档（demo 模式下生成新记录）。 */
export function mockUploadDocument(knowledgeBaseKey: string, file: File, parseMode: 'DEFAULT' | 'PARSE' | 'SKIP', folderPath?: string): OperationAccepted {
  const documentKey = `doc-demo-${Date.now()}`
  const path = folderPath || '/'
  const doc: DocumentDetail & { knowledgeBaseKey: string } = {
    documentKey,
    displayName: file.name,
    folderPath: path,
    currentFile: { filename: file.name, contentType: file.type || 'application/octet-stream', size: file.size },
    currentFileToken: `mock-token-${Date.now()}`,
    parseStatus: 'NOT_STARTED',
    publishStatus: 'UNPUBLISHED',
    hasActivePublication: false,
    updated: new Date().toISOString(),
    knowledgeBaseKey,
  }
  documents.push(doc)
  return { documentKey }
}

/** 模拟批量上传文档。 */
export function mockBatchUploadDocuments(knowledgeBaseKey: string, files: File[], parseMode: 'DEFAULT' | 'PARSE' | 'SKIP', relativePaths?: string[]): OperationAccepted[] {
  const results: OperationAccepted[] = []
  files.forEach((file, i) => {
    const documentKey = `doc-demo-${Date.now()}-${i}`
    const relPath = relativePaths?.[i]
    // 从 relativePath 提取目录（如 "合规材料/sub/a.md" -> "/合规材料/sub"）
    let folderPath = '/'
    if (relPath && relPath.includes('/')) {
      const lastSlash = relPath.lastIndexOf('/')
      const dirPart = relPath.substring(0, lastSlash)
      folderPath = dirPart.startsWith('/') ? dirPart : '/' + dirPart
    }
    const doc: DocumentDetail & { knowledgeBaseKey: string } = {
      documentKey,
      displayName: file.name,
      folderPath,
      currentFile: { filename: file.name, contentType: file.type || 'application/octet-stream', size: file.size },
      currentFileToken: `mock-token-${Date.now()}-${i}`,
      parseStatus: 'NOT_STARTED',
      publishStatus: 'UNPUBLISHED',
      hasActivePublication: false,
      updated: new Date().toISOString(),
      knowledgeBaseKey,
    }
    documents.push(doc)
    results.push({ documentKey })
  })
  return results
}

/** 模拟更新文档文件（替换当前文件）。 */
export function mockUpdateDocumentFile(documentKey: string, file: File, parseMode: 'DEFAULT' | 'PARSE' | 'SKIP', _currentFileToken: string): OperationAccepted {
  const doc = documents.find((d) => d.documentKey === documentKey)
  if (doc) {
    doc.currentFile = { filename: file.name, contentType: file.type || 'application/octet-stream', size: file.size }
    doc.currentFileToken = `mock-token-${Date.now()}`
    doc.parseStatus = 'NOT_STARTED'
    doc.updated = new Date().toISOString()
  }
  return { documentKey }
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

/**
 * 生成模拟父子分块数据。
 *
 * <p>父块数量 = parentCount，每个父块挂 3 个子块，总块数 = parentCount * 4。
 * 父块 skipEmbedding=true，子块 skipEmbedding=false，子块 parentChunkId 指向父块 index。
 */
function buildMockChunks(parentCount: number): ChunkView[] {
  const chunks: ChunkView[] = []
  let index = 0
  for (let p = 0; p < parentCount; p++) {
    const parentIndex = index
    chunks.push({
      index: parentIndex,
      content: `父块 #${parentIndex}：本块为分块层级中的父节点，包含下文摘要或标题，仅存储供检索召回使用，不参与向量化。`,
      parentChunkId: null,
      skipEmbedding: true,
    })
    index++
    for (let c = 0; c < 3; c++) {
      chunks.push({
        index,
        content: `子块 #${parentIndex}-${c}：本块为父块 #${parentIndex} 的第 ${c + 1} 个子块，包含具体正文内容片段，参与向量化检索。`.padEnd(120, '示例正文内容。'),
        parentChunkId: String(parentIndex),
        skipEmbedding: false,
      })
      index++
    }
  }
  return chunks
}

/** 模拟查询分块预览（支持分页）。 */
export function mockGetChunkPreview(documentKey: string, page = 0, size = 20): ChunkPreview {
  const doc = documents.find((d) => d.documentKey === documentKey)
  const hasChunk = doc?.parseStatus === 'SUCCEEDED'
  if (!hasChunk) {
    return {
      hasChunk: false,
      currentChunkRevisionKey: undefined,
      chunkProfile: undefined,
      parentCount: 0,
      childCount: 0,
      total: 0,
      page,
      size,
      chunks: [],
    }
  }
  const parentCount = 8
  const allChunks = buildMockChunks(parentCount)
  const total = allChunks.length
  const start = page * size
  const chunks = allChunks.slice(start, start + size)
  return {
    hasChunk: true,
    currentChunkRevisionKey: `cr-${documentKey}`,
    chunkProfile: { strategy: 'fixed', chunkSize: 512, overlap: 64 },
    parentCount,
    childCount: total - parentCount,
    total,
    page,
    size,
    chunks,
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

/**
 * 模拟获取源文件内容（返回 blobUrl 供 SourceFilePreview 组件渲染）。
 * 按 contentType 返回对应演示内容，覆盖 Markdown/TXT/PDF/图片/DOCX 五种格式。
 */
export function mockGetSourceContent(documentKey: string): { blobUrl: string; contentType: string; filename: string } {
  const doc = documents.find((d) => d.documentKey === documentKey)
  if (!doc) {
    return { blobUrl: '', contentType: 'application/octet-stream', filename: 'unknown' }
  }
  const { contentType, filename } = doc.currentFile
  let content = ''
  if (contentType === 'text/markdown') {
    content = `# ${filename}\n\n这是演示用的 Markdown 内容。\n\n## 概述\n\nRag2OKF 是可追溯、可观察的企业知识库与知识工程平台。\n\n## 核心能力\n\n- 文档工程\n- RAG 混合检索\n- OKF 知识工程\n\n## 快速开始\n\n1. 创建知识库\n2. 上传源文件\n3. 选择是否解析\n4. 发布 RAG 知识\n`
  } else if (contentType === 'text/plain') {
    content = '这是演示用的纯文本内容。\n\nRag2OKF API 参考文档\n\nGET /knowledge-bases\nPOST /knowledge-bases/{key}/documents\nGET /documents/{key}/source-content\n'
  } else if (contentType.startsWith('image/')) {
    // 生成 1x1 透明像素的 PNG，便于 demo 模式下图片预览不报错
    content = ''
    const byteString = atob('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==')
    const bytes = new Uint8Array(byteString.length)
    for (let i = 0; i < byteString.length; i++) bytes[i] = byteString.charCodeAt(i)
    const blob = new Blob([bytes], { type: contentType })
    return { blobUrl: URL.createObjectURL(blob), contentType, filename }
  } else if (contentType === 'application/pdf') {
    // PDF 文件头 + 简单内容
    const pdfContent = '%PDF-1.4\n1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n2 0 obj\n<< /Type /Pages /Kids [] /Count 0 >>\nendobj\nxref\n0 3\n0000000000 65535 f \ntrailer\n<< /Size 3 /Root 1 0 R >>\nstartxref\n0\n%%EOF'
    const blob = new Blob([pdfContent], { type: contentType })
    return { blobUrl: URL.createObjectURL(blob), contentType, filename }
  } else if (contentType === 'application/vnd.openxmlformats-officedocument.wordprocessingml.document') {
    // DOCX 无法简单构造有效二进制，返回 HTML 文本以供 mammoth 转换
    const htmlContent = `<html><body><h1>${filename}</h1><p>这是演示用的 DOCX 文档内容。Rag2OKF 平台支持文档工程、RAG 混合检索和 OKF 知识工程。</p></body></html>`
    const blob = new Blob([htmlContent], { type: 'text/html' })
    return { blobUrl: URL.createObjectURL(blob), contentType: 'text/html', filename }
  } else {
    content = '演示文件内容'
    const blob = new Blob([content], { type: contentType })
    return { blobUrl: URL.createObjectURL(blob), contentType, filename }
  }
  const blob = new Blob([content], { type: contentType })
  return { blobUrl: URL.createObjectURL(blob), contentType, filename }
}

/** 模拟获取最新任务（供 useTaskPolling 轮询使用）。 */
export function mockGetLatestTask(documentKey: string): TaskSummary | undefined {
  return documents.find((d) => d.documentKey === documentKey)?.latestTask
}
