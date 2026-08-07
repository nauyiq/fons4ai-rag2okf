import { computed, ref } from 'vue'

/** 数据源模式：demo 使用本地 mock 数据，real 走真实后端接口。 */
export type DataSourceMode = 'demo' | 'real'

/**
 * 环境变量默认值，模块加载时读取一次。
 * VITE_RAG2OKF_DATA_SOURCE 为 'demo' 时启用演示模式，其余值（含未设置）均为 real。
 */
const envMode: DataSourceMode = import.meta.env.VITE_RAG2OKF_DATA_SOURCE === 'demo' ? 'demo' : 'real'

/**
 * 运行时覆盖值，仅存在于内存 ref 中。
 * 刷新页面后 ref 归零，数据源回归 envMode，不污染 localStorage 真实 key。
 */
const runtimeOverride = ref<DataSourceMode | null>(null)

/** 当前数据源模式（运行时覆盖优先，否则取环境变量默认值）。 */
export function getDataSourceMode(): DataSourceMode {
  return runtimeOverride.value ?? envMode
}

/** demo 模式判断，供 api 层同步检查，避免在非组件上下文使用 composable。 */
export function isDemoMode(): boolean {
  return getDataSourceMode() === 'demo'
}

/**
 * 演示数据与真实接口切换机制。
 * demo 模式下 api 层各函数检测到 demo 模式时返回本地 mock 数据，不发起网络请求。
 * 运行时可通过 setMode 临时切换，resetMode 回归环境变量默认值，切换不持久化。
 */
export function useDataSource() {
  const mode = computed(() => getDataSourceMode())
  const isDemo = computed(() => isDemoMode())

  /** 运行时临时切换数据源模式，仅影响当前会话，不写入 localStorage。 */
  function setMode(next: DataSourceMode): void {
    runtimeOverride.value = next
  }

  /** 清除运行时覆盖，回归环境变量默认值。 */
  function resetMode(): void {
    runtimeOverride.value = null
  }

  return { mode, isDemo, setMode, resetMode }
}
