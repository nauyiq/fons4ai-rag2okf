/**
 * 模型目录 composable（CR-001 T028）。
 *
 * <p>缓存 /model-catalog 结果，提供关键词搜索与类型标签过滤能力，
 * 供 ModelSettingsTab 右侧模型市场使用。
 *
 * <p>设计依据：技术设计说明书 §3.1（Ragflow 风格左右双栏）。
 * - 实例级缓存：首次 fetchCatalog 后复用，refresh 强制刷新
 * - searchKeyword 按提供商名称或旗下模型名称模糊匹配
 * - activeTypeFilter 为选中的 ModelType 标签，空字符串表示"全部"
 */
import { computed, ref } from 'vue'

import { getModelCatalog, type CatalogProvider, type ModelCatalog } from '../api/models'
import type { ModelType } from '../types/model'

/**
 * 模型目录 composable。
 * 首次调用 fetchCatalog 后缓存，后续复用，refresh 强制刷新。
 */
export function useModelCatalog() {
  const cachedCatalog = ref<ModelCatalog | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)
  let loaded = false

  /** 关键词搜索（匹配提供商名称或旗下模型名称）。 */
  const searchKeyword = ref('')
  /** 当前选中的类型标签，空字符串表示"全部"。 */
  const activeTypeFilter = ref<ModelType | ''>('')

  /** 全部提供商列表。 */
  const catalog = computed<CatalogProvider[]>(() => cachedCatalog.value?.providers ?? [])

  /** 当前类型标签下该提供商的模型列表（用于卡片内渲染）。 */
  function providerModels(provider: CatalogProvider): CatalogProvider['models'] {
    if (!activeTypeFilter.value) return provider.models
    return provider.models.filter((m) => m.modelType === activeTypeFilter.value)
  }

  /**
   * 过滤后的提供商列表：综合关键词与类型标签。
   * - 关键词匹配提供商名称或旗下模型名称
   * - 类型标签要求该提供商至少有一个该类型模型
   */
  const filteredProviders = computed<CatalogProvider[]>(() => {
    const keyword = searchKeyword.value.trim().toLowerCase()
    const typeFilter = activeTypeFilter.value
    return catalog.value.filter((provider) => {
      if (typeFilter && !provider.models.some((m) => m.modelType === typeFilter)) {
        return false
      }
      if (!keyword) return true
      if (provider.providerName.toLowerCase().includes(keyword)) return true
      return provider.models.some((m) => m.modelName.toLowerCase().includes(keyword))
    })
  })

  /** 拉取目录数据（首次调用后缓存，force=true 强制刷新）。 */
  async function fetchCatalog(force = false): Promise<void> {
    if (loaded && !force) return
    loading.value = true
    error.value = null
    try {
      cachedCatalog.value = await getModelCatalog()
      loaded = true
    } catch (e) {
      error.value = e instanceof Error ? e.message : '无法加载模型目录。'
    } finally {
      loading.value = false
    }
  }

  /** 强制刷新目录。 */
  async function refresh(): Promise<void> {
    await fetchCatalog(true)
  }

  return {
    catalog,
    loading,
    error,
    refresh,
    filteredProviders,
    searchKeyword,
    activeTypeFilter,
    providerModels,
    fetchCatalog,
  }
}
