import { computed, onMounted, ref } from 'vue'

export type ThemeMode = 'light' | 'dark' | 'system'

const mode = ref<ThemeMode>('system')

function resolveTheme(currentMode: ThemeMode): 'light' | 'dark' {
  if (currentMode !== 'system') {
    return currentMode
  }
  return window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

function applyTheme(): void {
  document.documentElement.dataset.theme = resolveTheme(mode.value)
}

export function useTheme() {
  const activeTheme = computed(() => resolveTheme(mode.value))

  function setTheme(nextMode: ThemeMode): void {
    mode.value = nextMode
    applyTheme()
  }

  onMounted(() => {
    applyTheme()
  })

  return { mode, activeTheme, setTheme }
}
