import { describe, it, expect, beforeEach, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import { mount } from '@vue/test-utils'

/**
 * useTheme 测试。
 * 验证点（对应 T022 Verification 与 Quality）：
 * - loadTheme 无缓存默认 'system'
 * - loadTheme 非法值默认 'system'
 * - loadTheme 合法值从 localStorage 读取
 * - setTheme 同步写入 localStorage 键 `rag2okf.theme`
 * - setTheme 更新 mode 并应用 theme 到 document
 * - setTheme('system') 时 dataset.theme 跟随系统主题
 * - 三主题 light/dark/system 循环切换
 */

/** mock window.matchMedia，matches 控制系统明暗；返回监听器列表便于派发事件。 */
function mockMatchMedia(matches = false): ((e: MediaQueryListEvent) => void)[] {
  const listeners: ((e: MediaQueryListEvent) => void)[] = []
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    configurable: true,
    value: vi.fn().mockReturnValue({
      matches,
      addEventListener: (_: string, cb: (e: MediaQueryListEvent) => void) => listeners.push(cb),
      removeEventListener: (_: string, cb: (e: MediaQueryListEvent) => void) => {
        const i = listeners.indexOf(cb)
        if (i >= 0) listeners.splice(i, 1)
      },
    }),
  })
  return listeners
}

/** 在组件 setup 中调用 useTheme，挂载后返回 composable 结果。 */
function mountWithTheme(useThemeFn: () => ReturnType<typeof import('../useTheme').useTheme>) {
  let captured: ReturnType<typeof useThemeFn>
  const Comp = defineComponent({
    setup() {
      captured = useThemeFn()
      return () => h('div')
    },
  })
  mount(Comp)
  return captured!
}

describe('useTheme', () => {
  beforeEach(() => {
    localStorage.clear()
    document.documentElement.removeAttribute('data-theme')
    mockMatchMedia(false)
  })

  describe('loadTheme 初始化', () => {
    it('无 localStorage 缓存时默认 system', async () => {
      vi.resetModules()
      const { useTheme } = await import('../useTheme')
      const { mode } = mountWithTheme(useTheme)
      expect(mode.value).toBe('system')
    })

    it('localStorage 缓存 dark 时初始化为 dark', async () => {
      localStorage.setItem('rag2okf.theme', 'dark')
      vi.resetModules()
      const { useTheme } = await import('../useTheme')
      const { mode } = mountWithTheme(useTheme)
      expect(mode.value).toBe('dark')
    })

    it('localStorage 缓存 light 时初始化为 light', async () => {
      localStorage.setItem('rag2okf.theme', 'light')
      vi.resetModules()
      const { useTheme } = await import('../useTheme')
      const { mode } = mountWithTheme(useTheme)
      expect(mode.value).toBe('light')
    })

    it('localStorage 非法值时默认 system', async () => {
      localStorage.setItem('rag2okf.theme', 'pink')
      vi.resetModules()
      const { useTheme } = await import('../useTheme')
      const { mode } = mountWithTheme(useTheme)
      expect(mode.value).toBe('system')
    })
  })

  describe('setTheme 持久化', () => {
    it('setTheme 同步写入 localStorage 键 rag2okf.theme', async () => {
      vi.resetModules()
      const { useTheme } = await import('../useTheme')
      const { setTheme } = mountWithTheme(useTheme)
      setTheme('dark')
      expect(localStorage.getItem('rag2okf.theme')).toBe('dark')
    })

    it('setTheme 更新 mode', async () => {
      vi.resetModules()
      const { useTheme } = await import('../useTheme')
      const { mode, setTheme } = mountWithTheme(useTheme)
      setTheme('light')
      expect(mode.value).toBe('light')
    })

    it('setTheme 应用 theme 到 document', async () => {
      vi.resetModules()
      const { useTheme } = await import('../useTheme')
      const { setTheme } = mountWithTheme(useTheme)
      setTheme('dark')
      expect(document.documentElement.dataset.theme).toBe('dark')
    })

    it('setTheme("system") 时 dataset.theme 跟随系统暗色', async () => {
      mockMatchMedia(true)
      vi.resetModules()
      const { useTheme } = await import('../useTheme')
      const { setTheme } = mountWithTheme(useTheme)
      setTheme('system')
      expect(document.documentElement.dataset.theme).toBe('dark')
    })

    it('setTheme("system") 时 dataset.theme 跟随系统亮色', async () => {
      mockMatchMedia(false)
      vi.resetModules()
      const { useTheme } = await import('../useTheme')
      const { setTheme } = mountWithTheme(useTheme)
      setTheme('system')
      expect(document.documentElement.dataset.theme).toBe('light')
    })
  })

  describe('主题循环切换', () => {
    it('light → dark → system → light 循环且每次写入 localStorage', async () => {
      vi.resetModules()
      const { useTheme } = await import('../useTheme')
      const { mode, setTheme } = mountWithTheme(useTheme)

      setTheme('light')
      expect(mode.value).toBe('light')
      expect(localStorage.getItem('rag2okf.theme')).toBe('light')

      setTheme('dark')
      expect(mode.value).toBe('dark')
      expect(localStorage.getItem('rag2okf.theme')).toBe('dark')

      setTheme('system')
      expect(mode.value).toBe('system')
      expect(localStorage.getItem('rag2okf.theme')).toBe('system')

      setTheme('light')
      expect(mode.value).toBe('light')
      expect(localStorage.getItem('rag2okf.theme')).toBe('light')
    })
  })

  describe('系统主题变化', () => {
    it('system 模式下系统主题变化时更新 dataset.theme', async () => {
      const listeners = mockMatchMedia(false)
      vi.resetModules()
      const { useTheme } = await import('../useTheme')
      mountWithTheme(useTheme)

      // 派发系统主题变化为暗色
      const event = { matches: true } as MediaQueryListEvent
      listeners.forEach((cb) => cb(event))
      expect(document.documentElement.dataset.theme).toBe('dark')
    })
  })
})
