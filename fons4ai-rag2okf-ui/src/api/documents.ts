import { request, ApiRequestError } from './http'
import { isDemoMode } from '../composables/useDataSource'
import {
  mockListDocuments,
  mockGetDocument,
  mockUploadDocument,
  mockBatchUploadDocuments,
  mockUpdateDocumentFile,
  mockTriggerParse,
  mockTriggerPublish,
  mockGetChunkPreview,
  mockGetParsePreview,
  mockRetryTask,
  mockGetSourceContent,
  mockDeleteDocument,
  mockBatchDeleteDocuments,
} from './mock/documents'

export interface CurrentFile {
  filename: string
  contentType: string
  size: number
}

export interface TaskSummary {
  taskKey: string
  taskType: string
  status: string
  stage?: string
  progress: number
  attempt: number
  maxAttempts: number
  errorCode?: string
  errorMessage?: string
  updated: string
}

export interface DocumentSummary {
  documentKey: string
  displayName: string
  folderPath: string
  currentFile: CurrentFile
  /** Opaque, in-memory-only CAS token. Never render or persist it. */
  currentFileToken: string
  parseStatus: string
  publishStatus: string
  hasActivePublication: boolean
  latestTask?: TaskSummary
  updated: string
}

export interface DocumentDetail extends DocumentSummary {
  knowledgeBaseKey: string
}

export interface PageResponse<T> {
  records: T[]
  total: number
  page: number
  size: number
}

export interface OperationAccepted {
  taskKey?: string
  documentKey?: string
  folderPath?: string
  currentFileToken?: string
}

export interface ChunkView {
  /** 分块序号，从 0 开始 */
  index: number
  /** 分块文本内容 */
  content: string
  /** 父分块标识；父块自身为 null，子块指向父块 index 字符串 */
  parentChunkId: string | null
  /** 是否跳过向量化（父块为 true，仅存储供检索时召回） */
  skipEmbedding: boolean
}

export interface ChunkPreview {
  hasChunk: boolean
  currentChunkRevisionKey?: string
  chunkProfile?: Record<string, unknown>
  parentCount: number
  childCount: number
  total: number
  /** 当前页码（从 0 开始） */
  page: number
  /** 每页大小 */
  size: number
  /** 当前页分块列表 */
  chunks: ChunkView[]
}

export interface ParsePreview {
  hasParse: boolean
  parserProfile?: string
  blockCount: number
}

export function listDocuments(knowledgeBaseKey: string, page = 0, size = 20, folderPath?: string): Promise<PageResponse<DocumentSummary>> {
  if (isDemoMode()) {
    return Promise.resolve(mockListDocuments(knowledgeBaseKey, page, size, folderPath))
  }
  let url = `/knowledge-bases/${encodeURIComponent(knowledgeBaseKey)}/documents?page=${page}&size=${size}`
  if (folderPath) {
    url += `&folderPath=${encodeURIComponent(folderPath)}`
  }
  return request(url)
}

export function getDocument(documentKey: string): Promise<DocumentDetail> {
  if (isDemoMode()) {
    const doc = mockGetDocument(documentKey)
    if (!doc) return Promise.reject(new ApiRequestError('文档不存在', 404))
    return Promise.resolve(doc)
  }
  return request(`/documents/${encodeURIComponent(documentKey)}`)
}

export function uploadDocument(knowledgeBaseKey: string, file: File, parseMode: 'DEFAULT' | 'PARSE' | 'SKIP', folderPath?: string): Promise<OperationAccepted> {
  if (isDemoMode()) {
    return Promise.resolve(mockUploadDocument(knowledgeBaseKey, file, parseMode, folderPath))
  }
  const body = new FormData()
  body.append('file', file)
  body.append('parseMode', parseMode)
  if (folderPath) {
    body.append('folderPath', folderPath)
  }
  return request(`/knowledge-bases/${encodeURIComponent(knowledgeBaseKey)}/documents`, { method: 'POST', body })
}

export function batchUploadDocuments(knowledgeBaseKey: string, files: File[], parseMode: 'DEFAULT' | 'PARSE' | 'SKIP', relativePaths?: string[]): Promise<OperationAccepted[]> {
  if (isDemoMode()) {
    return Promise.resolve(mockBatchUploadDocuments(knowledgeBaseKey, files, parseMode, relativePaths))
  }
  const body = new FormData()
  for (const file of files) {
    body.append('files', file)
  }
  body.append('parseMode', parseMode)
  if (relativePaths && relativePaths.length > 0) {
    for (const rp of relativePaths) {
      body.append('relativePaths', rp)
    }
  }
  return request(`/knowledge-bases/${encodeURIComponent(knowledgeBaseKey)}/documents/batch`, { method: 'POST', body })
}

export function updateDocumentFile(documentKey: string, file: File, parseMode: 'DEFAULT' | 'PARSE' | 'SKIP', currentFileToken: string): Promise<OperationAccepted> {
  if (isDemoMode()) {
    return Promise.resolve(mockUpdateDocumentFile(documentKey, file, parseMode, currentFileToken))
  }
  const body = new FormData()
  body.append('file', file)
  body.append('parseMode', parseMode)
  body.append('expectedCurrentFileToken', currentFileToken)
  return request(`/documents/${encodeURIComponent(documentKey)}/files`, { method: 'POST', body })
}

export function triggerParse(documentKey: string): Promise<OperationAccepted> {
  if (isDemoMode()) {
    return Promise.resolve(mockTriggerParse(documentKey))
  }
  return request(`/documents/${encodeURIComponent(documentKey)}/parse?parseMode=PARSE`, { method: 'POST' })
}

export function triggerPublish(documentKey: string): Promise<OperationAccepted> {
  if (isDemoMode()) {
    return Promise.resolve(mockTriggerPublish(documentKey))
  }
  return request(`/documents/${encodeURIComponent(documentKey)}/publish`, { method: 'POST' })
}

export function getChunkPreview(documentKey: string, page = 0, size = 20): Promise<ChunkPreview> {
  if (isDemoMode()) {
    return Promise.resolve(mockGetChunkPreview(documentKey, page, size))
  }
  return request(`/documents/${encodeURIComponent(documentKey)}/chunks?page=${page}&size=${size}`)
}

export function getParsePreview(documentKey: string): Promise<ParsePreview> {
  if (isDemoMode()) {
    return Promise.resolve(mockGetParsePreview(documentKey))
  }
  return request(`/documents/${encodeURIComponent(documentKey)}/parse-preview`)
}

export function retryTask(taskKey: string): Promise<string> {
  if (isDemoMode()) {
    return Promise.resolve(mockRetryTask(taskKey))
  }
  return request(`/tasks/${encodeURIComponent(taskKey)}/retry`, { method: 'POST' })
}

export function rechunkDocument(documentKey: string, expectedChunkRevisionKey: string, chunkProfile: Record<string, unknown>): Promise<OperationAccepted> {
  if (isDemoMode()) {
    return Promise.resolve({ documentKey })
  }
  return request(`/documents/${encodeURIComponent(documentKey)}/rechunk`, {
    method: 'POST', body: JSON.stringify({ confirmed: true, expectedChunkRevisionKey, chunkProfile }),
  })
}

/** 源文件内容（blobUrl 供 SourceFilePreview 组件渲染，filename 供下载使用）。 */
export interface SourceContent {
  blobUrl: string
  contentType: string
  filename: string
}

/**
 * 获取文档源文件内容，返回 blobUrl 供 SourceFilePreview 组件按格式渲染。
 * demo 模式下走 mock 数据，real 模式直接 fetch 二进制响应（绕过 JSON-only 的 request 函数）。
 */
export async function getSourceContent(documentKey: string): Promise<SourceContent> {
  if (isDemoMode()) {
    return mockGetSourceContent(documentKey)
  }
  const apiBaseUrl = (import.meta.env.VITE_RAG2OKF_API_BASE_URL ?? '/knowledge/api/v1').replace(/\/$/, '')
  const token = localStorage.getItem('rag2okf_auth_token') ?? undefined
  const response = await fetch(`${apiBaseUrl}/documents/${encodeURIComponent(documentKey)}/file`, {
    headers: { Authentication: token ? `Bearer ${token}` : '' },
  })
  if (!response.ok) {
    throw new ApiRequestError('源文件获取失败，请稍后重试。', response.status)
  }
  const blob = await response.blob()
  const disposition = response.headers.get('Content-Disposition') ?? ''
  const filenameMatch = disposition.match(/filename\*?=(?:UTF-8'')?"?(.+?)"?$/i)
  return {
    blobUrl: URL.createObjectURL(blob),
    contentType: blob.type || 'application/octet-stream',
    filename: filenameMatch ? decodeURIComponent(filenameMatch[1]) : 'unknown',
  }
}

/** 批量删除结果：成功删除的 documentKey 列表与失败项明细。 */
export interface BatchDeleteResult {
  deleted: string[]
  failed: { key: string; error: string }[]
}

/**
 * 删除单个文档（软删除，后端负责 ES 索引清理）。
 * demo 模式下走 mock 数据，real 模式走 DELETE /documents/{documentKey}。
 */
export async function deleteDocument(documentKey: string): Promise<void> {
  if (isDemoMode()) {
    mockDeleteDocument(documentKey)
    return
  }
  await request<void>(`/documents/${encodeURIComponent(documentKey)}`, { method: 'DELETE' })
}

/**
 * 批量删除文档，返回成功和失败列表便于部分成功处理。
 * demo 模式下走 mock 数据，real 模式走 DELETE /documents:batch。
 */
export async function batchDeleteDocuments(documentKeys: string[]): Promise<BatchDeleteResult> {
  if (isDemoMode()) {
    return mockBatchDeleteDocuments(documentKeys)
  }
  return await request<BatchDeleteResult>('/documents:batch', {
    method: 'DELETE',
    body: JSON.stringify({ documentKeys }),
  })
}
