/**
 * 共享响应类型，避免在多个 API 模块中重复定义。
 */

/** 分页响应，使用后端返回的 total 作为真实总数（CR-015 AC-043）。 */
export interface PageResponse<T> {
  records: T[]
  total: number
  page: number
  size: number
}

/** 操作受理响应。 */
export interface OperationAccepted {
  taskKey?: string
  documentKey?: string
  folderPath?: string
  currentFileToken?: string
}
