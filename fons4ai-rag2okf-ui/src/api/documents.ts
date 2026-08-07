import { request } from './http'

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

export interface ChunkPreview {
  hasChunk: boolean
  currentChunkRevisionKey?: string
  chunkProfile?: Record<string, unknown>
  parentCount: number
  childCount: number
  total: number
}

export interface ParsePreview {
  hasParse: boolean
  parserProfile?: string
  blockCount: number
}

export function listDocuments(knowledgeBaseKey: string, page = 0, size = 20, folderPath?: string): Promise<PageResponse<DocumentSummary>> {
  let url = `/knowledge-bases/${encodeURIComponent(knowledgeBaseKey)}/documents?page=${page}&size=${size}`
  if (folderPath) {
    url += `&folderPath=${encodeURIComponent(folderPath)}`
  }
  return request(url)
}

export function getDocument(documentKey: string): Promise<DocumentDetail> {
  return request(`/documents/${encodeURIComponent(documentKey)}`)
}

export function uploadDocument(knowledgeBaseKey: string, file: File, parseMode: 'DEFAULT' | 'PARSE' | 'SKIP', folderPath?: string): Promise<OperationAccepted> {
  const body = new FormData()
  body.append('file', file)
  body.append('parseMode', parseMode)
  if (folderPath) {
    body.append('folderPath', folderPath)
  }
  return request(`/knowledge-bases/${encodeURIComponent(knowledgeBaseKey)}/documents`, { method: 'POST', body })
}

export function batchUploadDocuments(knowledgeBaseKey: string, files: File[], parseMode: 'DEFAULT' | 'PARSE' | 'SKIP', relativePaths?: string[]): Promise<OperationAccepted[]> {
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
  const body = new FormData()
  body.append('file', file)
  body.append('parseMode', parseMode)
  body.append('expectedCurrentFileToken', currentFileToken)
  return request(`/documents/${encodeURIComponent(documentKey)}/files`, { method: 'POST', body })
}

export function triggerParse(documentKey: string): Promise<OperationAccepted> {
  return request(`/documents/${encodeURIComponent(documentKey)}/parse`, { method: 'POST' })
}

export function triggerPublish(documentKey: string): Promise<OperationAccepted> {
  return request(`/documents/${encodeURIComponent(documentKey)}/publish`, { method: 'POST' })
}

export function getChunkPreview(documentKey: string): Promise<ChunkPreview> {
  return request(`/documents/${encodeURIComponent(documentKey)}/chunks?page=0&size=20`)
}

export function getParsePreview(documentKey: string): Promise<ParsePreview> {
  return request(`/documents/${encodeURIComponent(documentKey)}/parse-preview`)
}

export function retryTask(taskKey: string): Promise<string> {
  return request(`/tasks/${encodeURIComponent(taskKey)}/retry`, { method: 'POST' })
}

export function rechunkDocument(documentKey: string, expectedChunkRevisionKey: string, chunkProfile: Record<string, unknown>): Promise<OperationAccepted> {
  return request(`/documents/${encodeURIComponent(documentKey)}/rechunk`, {
    method: 'POST', body: JSON.stringify({ confirmed: true, expectedChunkRevisionKey, chunkProfile }),
  })
}
