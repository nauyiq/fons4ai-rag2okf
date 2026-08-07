/**
 * 知识库 API 层。
 *
 * 设计依据：技术设计说明书 §3.2。
 * KnowledgeBaseSummary 扩展 ownerUserKey 和 canDelete 字段用于前端删除权限判断。
 * ModelBinding 使用 modelConfigKey 对齐合并后模型配置契约。
 * 新增 deleteKnowledgeBase 接口，服务端校验创建者权限。
 *
 * demo 模式下走本地 mock 数据，real 模式走 http.request。
 */
import { request } from './http'
import { isDemoMode } from '../composables/useDataSource'
import {
  mockListKnowledgeBases,
  mockGetKnowledgeBase,
  mockCreateKnowledgeBase,
  mockUpdateKnowledgeBase,
  mockDeleteKnowledgeBase,
} from './mock/knowledge-bases'

export interface ChunkProfile {
  strategy: string
  chunkSize: number
  overlap: number
  titleLevel: number | null
}

/** 模型绑定，usageType 标识用途（CHAT/EMBEDDING），modelConfigKey 指向合并后的模型配置。 */
export interface ModelBinding {
  bindingKey?: string
  usageType: string
  modelConfigKey: string
}

export interface KnowledgeBaseSummary {
  knowledgeBaseKey: string
  name: string
  description: string
  autoParse: boolean
  autoPublish: boolean
  updated: string
  /** 创建者用户标识，用于前端判断删除权限 */
  ownerUserKey: string
  /** 前端删除入口可见性（服务端根据成员关系与角色计算） */
  canDelete: boolean
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

/** 分页查询知识库列表。 */
export function listKnowledgeBases(workspaceKey: string, page = 0, size = 20): Promise<PageResponse<KnowledgeBaseSummary>> {
  if (isDemoMode()) {
    return Promise.resolve(mockListKnowledgeBases(workspaceKey, page, size))
  }
  return request(`/workspaces/${encodeURIComponent(workspaceKey)}/knowledge-bases?page=${page}&size=${size}`)
}

/** 查询单个知识库详情。 */
export function getKnowledgeBase(knowledgeBaseKey: string): Promise<KnowledgeBase | undefined> {
  if (isDemoMode()) {
    return Promise.resolve(mockGetKnowledgeBase(knowledgeBaseKey))
  }
  return request(`/knowledge-bases/${encodeURIComponent(knowledgeBaseKey)}`)
}

/** 创建知识库。 */
export function createKnowledgeBase(workspaceKey: string, input: SaveKnowledgeBaseInput): Promise<KnowledgeBase> {
  if (isDemoMode()) {
    return Promise.resolve(mockCreateKnowledgeBase(workspaceKey, input))
  }
  return request(`/workspaces/${encodeURIComponent(workspaceKey)}/knowledge-bases`, {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

/** 更新知识库（含重命名，复用 update 仅提交 name）。 */
export function updateKnowledgeBase(knowledgeBaseKey: string, input: SaveKnowledgeBaseInput): Promise<KnowledgeBase | undefined> {
  if (isDemoMode()) {
    return Promise.resolve(mockUpdateKnowledgeBase(knowledgeBaseKey, input))
  }
  return request(`/knowledge-bases/${encodeURIComponent(knowledgeBaseKey)}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

/** 删除知识库（服务端校验创建者；非创建者返回 403）。 */
export function deleteKnowledgeBase(knowledgeBaseKey: string): Promise<boolean> {
  if (isDemoMode()) {
    return Promise.resolve(mockDeleteKnowledgeBase(knowledgeBaseKey))
  }
  return request(`/knowledge-bases/${encodeURIComponent(knowledgeBaseKey)}`, {
    method: 'DELETE',
  })
}
