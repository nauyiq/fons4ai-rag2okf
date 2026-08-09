/**
 * 模型类型定义（CR-001 T025）。
 *
 * 7 种模型类型枚举，不含已废弃的 CHAT。
 * CHAT 作为历史值在读取时由后端别名映射为 LLM，前端不直接使用 CHAT。
 */

/** 7 种合法模型类型（不含已废弃的 CHAT）。 */
export type ModelType = 'LLM' | 'EMBEDDING' | 'RERANK' | 'TTS' | 'ASR' | 'VLM' | 'OCR'

/** 7 种模型类型常量数组，用于下拉选项、标签栏等遍历场景。 */
export const MODEL_TYPES: ModelType[] = ['LLM', 'EMBEDDING', 'RERANK', 'TTS', 'ASR', 'VLM', 'OCR']

/** 模型类型中文标签映射。 */
export const MODEL_TYPE_LABELS: Record<ModelType, string> = {
  LLM: 'LLM 对话',
  EMBEDDING: 'Embedding 向量化',
  RERANK: 'Rerank 重排',
  TTS: 'TTS 语音合成',
  ASR: 'ASR 语音识别',
  VLM: 'VLM 视觉理解',
  OCR: 'OCR 图文识别',
}

/**
 * 将可能的旧值 CHAT 兼容映射为 LLM。
 * 用于读取后端返回数据时的前端兼容处理。
 */
export function normalizeModelType(raw: string): ModelType {
  if (raw === 'CHAT') return 'LLM'
  return raw as ModelType
}

/** 判断字符串是否为合法的 7 种模型类型之一（拒绝 CHAT）。 */
export function isValidModelType(value: string): value is ModelType {
  return MODEL_TYPES.includes(value as ModelType)
}
