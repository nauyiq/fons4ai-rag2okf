/**
 * 默认模型偏好 composable（CR-001 T028）。
 *
 * <p>加载 /users/me 中的 preferenceJson.defaultModels，保存时局部合并
 * （不覆盖 preferenceJson 中其他偏好键），并处理 CHAT→LLM 历史值迁移。
 *
 * <p>设计依据：技术设计说明书 §3.1。
 * - CHAT 为已废弃类型，读取时若存在 defaults.CHAT 则迁移到 defaults.LLM 并清理
 * - 保存仅写入 defaultModels 子键，保留后端 preferenceJson 其他键
 */
import { ref } from 'vue'

import {
  getDefaultModels,
  saveDefaultModels,
  type DefaultModelSettings,
} from '../api/models'
import type { ModelType } from '../types/model'

/** 运行时宽松类型：兼容历史 CHAT 键（ModelType 不含 CHAT）。 */
type LooseDefaults = Partial<Record<string, string | null>>

/**
 * 默认模型偏好 composable。
 * 提供 defaults 状态、加载、保存与 CHAT→LLM 迁移能力。
 */
export function useDefaultModels() {
  const defaults = ref<DefaultModelSettings>({ defaults: {} })
  const loading = ref(false)

  /** 加载默认模型偏好并执行 CHAT→LLM 迁移。 */
  async function load(): Promise<void> {
    loading.value = true
    try {
      const settings = await getDefaultModels()
      defaults.value = migrateChatKey(settings)
    } finally {
      loading.value = false
    }
  }

  /**
   * CHAT→LLM 迁移：若 defaults.CHAT 存在，迁移到 defaults.LLM（LLM 未设置时）并清理 CHAT 键。
   * 返回迁移后的新对象，不修改入参。
   */
  function migrateChatKey(settings: DefaultModelSettings): DefaultModelSettings {
    const loose = settings.defaults as LooseDefaults
    const chatValue = loose.CHAT
    if (chatValue == null) {
      // 无 CHAT 键或值为 null，无需迁移
      return settings
    }
    const next: LooseDefaults = { ...loose }
    // LLM 未设置时用 CHAT 值填充
    if (next.LLM == null) {
      next.LLM = chatValue
    }
    delete next.CHAT
    return { defaults: next as Partial<Record<ModelType, string | null>> }
  }

  /**
   * 保存默认模型偏好。
   * 仅写入 preferenceJson.defaultModels 子键，由后端局部合并，不覆盖其他偏好键。
   */
  async function save(): Promise<void> {
    await saveDefaultModels(defaults.value)
  }

  return {
    defaults,
    loading,
    load,
    save,
    migrateChatKey,
  }
}
