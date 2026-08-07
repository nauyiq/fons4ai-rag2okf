import { describe, it, expect, beforeEach, vi } from 'vitest'

/**
 * useDataSource 演示数据切换机制测试。
 *
 * 验证点：
 * - 环境变量 VITE_RAG2OKF_DATA_SOURCE=demo 时 isDemo 返回 true
 * - 环境变量未设置或为 real 时 isDemo 返回 false
 * - 运行时 setMode 可临时切换，resetMode 回归环境变量默认值
 * - 运行时切换不写入 localStorage（刷新后回归默认值）
 * - isDemoMode() 供 api 层同步检查
 */
describe('useDataSource', () => {
  beforeEach(() => {
    vi.resetModules()
    localStorage.clear()
  })

  it('环境变量为 demo 时 isDemo 返回 true', async () => {
    vi.stubEnv('VITE_RAG2OKF_DATA_SOURCE', 'demo')
    const { useDataSource } = await import('../useDataSource')
    const { isDemo, mode } = useDataSource()
    expect(isDemo.value).toBe(true)
    expect(mode.value).toBe('demo')
  })

  it('环境变量为 real 时 isDemo 返回 false', async () => {
    vi.stubEnv('VITE_RAG2OKF_DATA_SOURCE', 'real')
    const { useDataSource } = await import('../useDataSource')
    const { isDemo, mode } = useDataSource()
    expect(isDemo.value).toBe(false)
    expect(mode.value).toBe('real')
  })

  it('环境变量未设置时默认为 real', async () => {
    vi.stubEnv('VITE_RAG2OKF_DATA_SOURCE', undefined)
    const { useDataSource } = await import('../useDataSource')
    const { isDemo, mode } = useDataSource()
    expect(isDemo.value).toBe(false)
    expect(mode.value).toBe('real')
  })

  it('环境变量为非法值时默认为 real', async () => {
    vi.stubEnv('VITE_RAG2OKF_DATA_SOURCE', 'invalid')
    const { useDataSource } = await import('../useDataSource')
    const { isDemo, mode } = useDataSource()
    expect(isDemo.value).toBe(false)
    expect(mode.value).toBe('real')
  })

  it('运行时 setMode 可临时切换到 demo', async () => {
    vi.stubEnv('VITE_RAG2OKF_DATA_SOURCE', 'real')
    const { useDataSource } = await import('../useDataSource')
    const { isDemo, setMode } = useDataSource()
    expect(isDemo.value).toBe(false)
    setMode('demo')
    expect(isDemo.value).toBe(true)
  })

  it('运行时 setMode 可临时切换到 real', async () => {
    vi.stubEnv('VITE_RAG2OKF_DATA_SOURCE', 'demo')
    const { useDataSource } = await import('../useDataSource')
    const { isDemo, setMode } = useDataSource()
    expect(isDemo.value).toBe(true)
    setMode('real')
    expect(isDemo.value).toBe(false)
  })

  it('resetMode 后回归环境变量默认值', async () => {
    vi.stubEnv('VITE_RAG2OKF_DATA_SOURCE', 'real')
    const { useDataSource } = await import('../useDataSource')
    const { isDemo, setMode, resetMode } = useDataSource()
    setMode('demo')
    expect(isDemo.value).toBe(true)
    resetMode()
    expect(isDemo.value).toBe(false)
  })

  it('运行时切换不写入 localStorage', async () => {
    vi.stubEnv('VITE_RAG2OKF_DATA_SOURCE', 'real')
    const { useDataSource } = await import('../useDataSource')
    const { setMode } = useDataSource()
    setMode('demo')
    // 切换后 localStorage 不应有数据源相关 key
    expect(localStorage.getItem('rag2okf_data_source')).toBeNull()
    expect(localStorage.getItem('VITE_RAG2OKF_DATA_SOURCE')).toBeNull()
  })

  it('isDemoMode 函数供 api 层同步检查', async () => {
    vi.stubEnv('VITE_RAG2OKF_DATA_SOURCE', 'demo')
    const { isDemoMode } = await import('../useDataSource')
    expect(isDemoMode()).toBe(true)
  })

  it('isDemoMode 在运行时切换后同步更新', async () => {
    vi.stubEnv('VITE_RAG2OKF_DATA_SOURCE', 'real')
    const { useDataSource, isDemoMode } = await import('../useDataSource')
    const { setMode, resetMode } = useDataSource()
    expect(isDemoMode()).toBe(false)
    setMode('demo')
    expect(isDemoMode()).toBe(true)
    resetMode()
    expect(isDemoMode()).toBe(false)
  })

  it('getDataSourceMode 返回当前模式字符串', async () => {
    vi.stubEnv('VITE_RAG2OKF_DATA_SOURCE', 'demo')
    const { getDataSourceMode } = await import('../useDataSource')
    expect(getDataSourceMode()).toBe('demo')
  })
})
