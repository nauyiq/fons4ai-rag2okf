/**
 * 共享格式化工具：时间、文件大小和状态枚举的统一格式化。
 *
 * <p>所有页面应使用这些工具而非各自实现，避免格式化逻辑漂移（CR-015）。
 */

/**
 * 格式化时间为中文短格式（如 "8月7日 14:30"）。
 * 非法时间返回"未知时间"。
 */
export function formatTime(value: string | Date | null | undefined): string {
  if (!value) return '未知时间'
  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.valueOf())) return '未知时间'
  return date.toLocaleString('zh-CN', {
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

/**
 * 格式化文件大小为人类可读格式（B/KB/MB/GB）。
 * 非法值返回"未知大小"。
 */
export function formatBytes(bytes: number | null | undefined): string {
  if (bytes == null || bytes < 0 || Number.isNaN(bytes)) return '未知大小'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${Math.ceil(bytes / 1024)} KB`
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`
  return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`
}

/** 解析状态到中文的映射。 */
const PARSE_STATUS_LABELS: Record<string, string> = {
  NOT_STARTED: '尚未解析',
  QUEUED: '等待处理',
  RUNNING: '处理中',
  SUCCEEDED: '已完成',
  FAILED: '处理失败',
}

/** 发布状态到中文的映射。 */
const PUBLISH_STATUS_LABELS: Record<string, string> = {
  UNPUBLISHED: '未发布',
  PUBLISHED: '已发布',
  PUBLISHING: '发布中',
  PUBLISH_FAILED: '发布失败',
}

/** 任务状态到中文的映射。 */
const TASK_STATUS_LABELS: Record<string, string> = {
  QUEUED: '排队中',
  RUNNING: '执行中',
  RETRY_WAIT: '重试中',
  SUCCEEDED: '已完成',
  FAILED: '已失败',
  CANCELLED: '已取消',
}

/** 模型测试结果到中文的映射。状态值与后端 ModelTestStatus 枚举一致（SUCCEEDED/FAILED）。 */
const MODEL_TEST_LABELS: Record<string, string> = {
  SUCCEEDED: '成功',
  AUTH_FAILED: '认证失败，请检查 API Key',
  UNREACHABLE: '地址不可用，请检查 Base URL',
  MODEL_NOT_FOUND: '模型不存在，请检查模型名称',
  CAPABILITY_MISMATCH: '能力不匹配，该模型不支持当前操作',
  TIMEOUT: '测试超时，请稍后重试',
  SSRF_BLOCKED: '该地址不符合服务端出站安全策略',
}

/**
 * 将解析状态枚举转换为中文标签。
 * 未知状态返回原值。
 */
export function parseStatusLabel(status: string): string {
  return PARSE_STATUS_LABELS[status] ?? status
}

/**
 * 将发布状态枚举转换为中文标签。
 * 未知状态返回原值。
 */
export function publishStatusLabel(status: string): string {
  return PUBLISH_STATUS_LABELS[status] ?? status
}

/**
 * 将任务状态枚举转换为中文标签。
 * 未知状态返回原值。
 */
export function taskStatusLabel(status: string): string {
  return TASK_STATUS_LABELS[status] ?? status
}

/**
 * 将模型测试结果枚举转换为中文标签。
 * 优先使用 status，其次尝试 errorCode。
 */
export function modelTestLabel(status: string | null | undefined, errorCode?: string | null): string {
  if (status && MODEL_TEST_LABELS[status]) return MODEL_TEST_LABELS[status]
  if (errorCode && MODEL_TEST_LABELS[errorCode]) return MODEL_TEST_LABELS[errorCode]
  return '测试未通过，请稍后重试。'
}
