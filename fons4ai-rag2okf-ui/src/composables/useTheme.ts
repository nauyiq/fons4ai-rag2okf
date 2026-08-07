import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

export type ThemeMode = 'light' | 'dark' | 'system'

const mode = ref<ThemeMode>('system')
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

export function useTheme() {
  const activeTheme = computed(() => resolveTheme(mode.value))
  const followSystemTheme = (event: MediaQueryListEvent): void => {
    systemTheme.value = event.matches ? 'dark' : 'light'
    if (mode.value === 'system') {
      applyTheme()
    }
  }

  function setTheme(nextMode: ThemeMode): void {
    mode.value = nextMode
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
