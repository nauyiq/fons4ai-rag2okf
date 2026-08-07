import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

export type ThemeMode = 'light' | 'dark' | 'system'

/** 主题持久化存储键，light/dark/system 三态缓存于 localStorage。 */
const STORAGE_KEY = 'rag2okf.theme'
const VALID_MODES: readonly ThemeMode[] = ['light', 'dark', 'system']

/**
 * 从 localStorage 读取主题缓存。
 * 无缓存或非法值时回退为 'system'，保证容错。
 */
function loadTheme(): ThemeMode {
  const stored = typeof localStorage !== 'undefined' ? localStorage.getItem(STORAGE_KEY) : null
  return VALID_MODES.includes(stored as ThemeMode) ? (stored as ThemeMode) : 'system'
}

const mode = ref<ThemeMode>(loadTheme())
const systemTheme = ref<'light' | 'dark'>('light')

function resolveTheme(currentMode: ThemeMode): 'light' | 'dark' {
  if (currentMode !== 'system') {
    return currentMode
  }
  return systemTheme.value
}

function refreshSystemTheme(): void {
  systemTheme.value = window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

function applyTheme(): void {
  document.documentElement.dataset.theme = resolveTheme(mode.value)
}

// 模块加载时立即初始化系统主题并应用到 document，使主题在所有页面（含未挂载 AppShell 的 public 页面）即时生效。
if (typeof window !== 'undefined') {
  refreshSystemTheme()
}
if (typeof document !== 'undefined') {
  applyTheme()
}

export function useTheme() {
  const activeTheme = computed(() => resolveTheme(mode.value))
  const followSystemTheme = (event: MediaQueryListEvent): void => {
    systemTheme.value = event.matches ? 'dark' : 'light'
    if (mode.value === 'system') {
      applyTheme()
    }
  }

  /** 切换主题并同步写入 localStorage，刷新后沿用缓存值。 */
  function setTheme(nextMode: ThemeMode): void {
    mode.value = nextMode
    localStorage.setItem(STORAGE_KEY, nextMode)
    if (nextMode === 'system') {
      refreshSystemTheme()
    }
    applyTheme()
  }

  onMounted(() => {
    refreshSystemTheme()
    applyTheme()
    window.matchMedia?.('(prefers-color-scheme: dark)').addEventListener('change', followSystemTheme)
  })

  onBeforeUnmount(() => {
    window.matchMedia?.('(prefers-color-scheme: dark)').removeEventListener('change', followSystemTheme)
  })

  return { mode, activeTheme, setTheme }
}
