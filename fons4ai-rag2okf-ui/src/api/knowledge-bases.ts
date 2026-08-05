import { request } from './http'

export interface ChunkProfile {
  strategy: string
  chunkSize: number
  overlap: number
  titleLevel: number | null
}

export interface ModelBinding {
  bindingKey?: string
  usageType: string
  modelProfileKey: string
}

export interface KnowledgeBaseSummary {
  knowledgeBaseKey: string
  name: string
  description: string
  autoParse: boolean
  autoPublish: boolean
  updated: string
}

export interface KnowledgeBase extends KnowledgeBaseSummary {
  workspaceKey: string
  parserProfile: string
  chunkProfile: ChunkProfile
  modelBindings: ModelBinding[]
  revision: number
}

export interface PageResponse<T> {
  records: T[]
  total: number
  page: number
  size: number
}

export interface SaveKnowledgeBaseInput {
  name?: string
  description?: string
  autoParse?: boolean
  autoPublish?: boolean
  parserProfile?: string
  chunkProfile?: ChunkProfile
  modelBindings?: ModelBinding[]
  revision: number
}

export function listKnowledgeBases(workspaceKey: string, page = 0, size = 20): Promise<PageResponse<KnowledgeBaseSummary>> {
  return request(`/workspaces/${encodeURIComponent(workspaceKey)}/knowledge-bases?page=${page}&size=${size}`)
}

export function createKnowledgeBase(workspaceKey: string, input: SaveKnowledgeBaseInput): Promise<KnowledgeBase> {
  return request(`/workspaces/${encodeURIComponent(workspaceKey)}/knowledge-bases`, {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function getKnowledgeBase(knowledgeBaseKey: string): Promise<KnowledgeBase> {
  return request(`/knowledge-bases/${encodeURIComponent(knowledgeBaseKey)}`)
}

export function updateKnowledgeBase(knowledgeBaseKey: string, input: SaveKnowledgeBaseInput): Promise<KnowledgeBase> {
  return request(`/knowledge-bases/${encodeURIComponent(knowledgeBaseKey)}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}
