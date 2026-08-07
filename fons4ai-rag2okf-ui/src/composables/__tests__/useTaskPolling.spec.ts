import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import { mount } from '@vue/test-utils'
import type { TaskSummary } from '../../api/documents'
import { useTaskPolling } from '../useTaskPolling'

/**
 * useTaskPolling 测试。
 * 验证点（对应 T004 Verification）：
 * - 首次轮询在 2s 后执行
 * - 每次轮询间隔 ×1.5，上限 15s
 * - 任务终态（SUCCEEDED/FAILED）时停止
 * - 组件卸载时定时器清理
 * - 重叠请求时复用进行中的 Promise（单飞）
 */
describe('useTaskPolling', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  /** 创建一个 RUNNING 态的 task */
  function runningTask(progress = 30): TaskSummary {
    return {
      taskKey: 'task-001',
      taskType: 'PARSE',
      status: 'RUNNING',
      stage: '解析中',
      progress,
      attempt: 1,
      maxAttempts: 3,
      updated: '2026-08-07T10:00:00Z',
    }
  }

  /** 创建一个 SUCCEEDED 态的 task */
  function succeededTask(): TaskSummary {
    return { ...runningTask(100), status: 'SUCCEEDED', stage: '完成' }
  }

  it('start() 后首次轮询在 2s 后执行', async () => {
    const fetcher = vi.fn().mockResolvedValue(runningTask())
    const isActive = vi.fn().mockReturnValue(true)
    const onUpdate = vi.fn()
    const { start } = useTaskPolling({ fetcher, isActive, onUpdate })

    start()
    // 2s 前不执行
    vi.advanceTimersByTime(1999)
    expect(fetcher).not.toHaveBeenCalled()

    // 2s 时执行
    await vi.advanceTimersByTimeAsync(1)
    expect(fetcher).toHaveBeenCalledTimes(1)
  })

  it('每次轮询间隔 ×1.5', async () => {
    const fetcher = vi.fn().mockResolvedValue(runningTask())
    const isActive = vi.fn().mockReturnValue(true)
    const onUpdate = vi.fn()
    const { start } = useTaskPolling({ fetcher, isActive, onUpdate })

    start()

    // 首次 2s
    await vi.advanceTimersByTimeAsync(2000)
    expect(fetcher).toHaveBeenCalledTimes(1)

    // 第二次 3s (2000 * 1.5)
    await vi.advanceTimersByTimeAsync(2999)
    expect(fetcher).toHaveBeenCalledTimes(1)
    await vi.advanceTimersByTimeAsync(1)
    expect(fetcher).toHaveBeenCalledTimes(2)

    // 第三次 4.5s (3000 * 1.5)
    await vi.advanceTimersByTimeAsync(4499)
    expect(fetcher).toHaveBeenCalledTimes(2)
    await vi.advanceTimersByTimeAsync(1)
    expect(fetcher).toHaveBeenCalledTimes(3)
  })

  it('间隔上限 15s', async () => {
    const fetcher = vi.fn().mockResolvedValue(runningTask())
    const isActive = vi.fn().mockReturnValue(true)
    const onUpdate = vi.fn()
    const { start } = useTaskPolling({ fetcher, isActive, onUpdate })

    start()

    // 轮询序列：2s, 3s, 4.5s, 6.75s, 10.125s, 15s(capped), 15s(capped)
    const intervals = [2000, 3000, 4500, 6750, 10125]
    for (const interval of intervals) {
      await vi.advanceTimersByTimeAsync(interval)
    }
    // 第 6 次：min(10125 * 1.5, 15000) = min(15187.5, 15000) = 15000
    await vi.advanceTimersByTimeAsync(15000)
    expect(fetcher).toHaveBeenCalledTimes(6)

    // 第 7 次：min(15000 * 1.5, 15000) = 15000
    await vi.advanceTimersByTimeAsync(15000)
    expect(fetcher).toHaveBeenCalledTimes(7)
  })

  it('任务状态变为 SUCCEEDED 时轮询停止', async () => {
    let currentStatus = 'RUNNING'
    const fetcher = vi.fn().mockResolvedValue(succeededTask())
    const isActive = vi.fn(() => currentStatus === 'QUEUED' || currentStatus === 'RUNNING')
    const onUpdate = vi.fn((task: TaskSummary) => { currentStatus = task.status })
    const { start } = useTaskPolling({ fetcher, isActive, onUpdate })

    start()
    await vi.advanceTimersByTimeAsync(2000)

    expect(fetcher).toHaveBeenCalledTimes(1)
    expect(onUpdate).toHaveBeenCalledWith(expect.objectContaining({ status: 'SUCCEEDED' }))

    // 推进很长时间，不应该再轮询
    await vi.advanceTimersByTimeAsync(60000)
    expect(fetcher).toHaveBeenCalledTimes(1)
  })

  it('任务状态变为 FAILED 时轮询停止', async () => {
    let currentStatus = 'RUNNING'
    const failedTask: TaskSummary = { ...runningTask(), status: 'FAILED', errorCode: 'PARSE_ERROR' }
    const fetcher = vi.fn().mockResolvedValue(failedTask)
    const isActive = vi.fn(() => currentStatus === 'QUEUED' || currentStatus === 'RUNNING')
    const onUpdate = vi.fn((task: TaskSummary) => { currentStatus = task.status })
    const { start } = useTaskPolling({ fetcher, isActive, onUpdate })

    start()
    await vi.advanceTimersByTimeAsync(2000)

    expect(fetcher).toHaveBeenCalledTimes(1)
    expect(onUpdate).toHaveBeenCalledWith(expect.objectContaining({ status: 'FAILED' }))

    await vi.advanceTimersByTimeAsync(60000)
    expect(fetcher).toHaveBeenCalledTimes(1)
  })

  it('onUpdate 回调接收 fetcher 返回的 task', async () => {
    const task = runningTask(55)
    const fetcher = vi.fn().mockResolvedValue(task)
    const isActive = vi.fn().mockReturnValue(true)
    const onUpdate = vi.fn()
    const { start } = useTaskPolling({ fetcher, isActive, onUpdate })

    start()
    await vi.advanceTimersByTimeAsync(2000)

    expect(onUpdate).toHaveBeenCalledWith(task)
  })

  it('fetcher 返回 undefined 时不调用 onUpdate', async () => {
    const fetcher = vi.fn().mockResolvedValue(undefined)
    const isActive = vi.fn().mockReturnValue(true)
    const onUpdate = vi.fn()
    const { start } = useTaskPolling({ fetcher, isActive, onUpdate })

    start()
    await vi.advanceTimersByTimeAsync(2000)

    expect(onUpdate).not.toHaveBeenCalled()
  })

  it('单飞：重叠请求时复用进行中的 Promise', async () => {
    let resolveFetch!: (value: TaskSummary | undefined) => void
    const fetcher = vi.fn().mockImplementation(() => new Promise<TaskSummary | undefined>(resolve => {
      resolveFetch = resolve
    }))
    const isActive = vi.fn().mockReturnValue(true)
    const onUpdate = vi.fn()
    const { start } = useTaskPolling({ fetcher, isActive, onUpdate })

    start()

    // 首次轮询触发
    await vi.advanceTimersByTimeAsync(2000)
    expect(fetcher).toHaveBeenCalledTimes(1)

    // 推进到下一次轮询时间（3s），但 fetcher 尚未 resolve
    await vi.advanceTimersByTimeAsync(3000)
    // 应该只有 1 次调用（单飞，不重复发起新请求）
    expect(fetcher).toHaveBeenCalledTimes(1)

    // resolve 后
    resolveFetch(runningTask(60))
    await vi.advanceTimersByTimeAsync(4500)
    expect(fetcher).toHaveBeenCalledTimes(2)
  })

  it('stop() 停止轮询', async () => {
    const fetcher = vi.fn().mockResolvedValue(runningTask())
    const isActive = vi.fn().mockReturnValue(true)
    const onUpdate = vi.fn()
    const { start, stop } = useTaskPolling({ fetcher, isActive, onUpdate })

    start()
    await vi.advanceTimersByTimeAsync(2000)
    expect(fetcher).toHaveBeenCalledTimes(1)

    stop()

    await vi.advanceTimersByTimeAsync(60000)
    expect(fetcher).toHaveBeenCalledTimes(1)
  })

  it('start() 多次调用不会重复启动轮询', async () => {
    const fetcher = vi.fn().mockResolvedValue(runningTask())
    const isActive = vi.fn().mockReturnValue(true)
    const onUpdate = vi.fn()
    const { start } = useTaskPolling({ fetcher, isActive, onUpdate })

    start()
    start()
    start()

    await vi.advanceTimersByTimeAsync(2000)
    expect(fetcher).toHaveBeenCalledTimes(1)
  })

  it('组件卸载时清理定时器', async () => {
    const fetcher = vi.fn().mockResolvedValue(runningTask())
    const isActive = vi.fn().mockReturnValue(true)
    const onUpdate = vi.fn()

    let polling: ReturnType<typeof useTaskPolling> | undefined
    const Comp = defineComponent({
      setup() {
        polling = useTaskPolling({ fetcher, isActive, onUpdate })
        return () => h('div')
      },
    })
    const wrapper = mount(Comp)

    polling!.start()
    await vi.advanceTimersByTimeAsync(2000)
    expect(fetcher).toHaveBeenCalledTimes(1)

    wrapper.unmount()

    await vi.advanceTimersByTimeAsync(60000)
    expect(fetcher).toHaveBeenCalledTimes(1)
  })

  it('间隔参数可自定义', async () => {
    const fetcher = vi.fn().mockResolvedValue(runningTask())
    const isActive = vi.fn().mockReturnValue(true)
    const onUpdate = vi.fn()
    const { start } = useTaskPolling({
      fetcher,
      isActive,
      onUpdate,
      intervalMs: 1000,
      backoffFactor: 2,
      maxIntervalMs: 8000,
    })

    start()

    // 首次 1s
    await vi.advanceTimersByTimeAsync(1000)
    expect(fetcher).toHaveBeenCalledTimes(1)

    // 第二次 2s (1000 * 2)
    await vi.advanceTimersByTimeAsync(2000)
    expect(fetcher).toHaveBeenCalledTimes(2)

    // 第三次 4s (2000 * 2)
    await vi.advanceTimersByTimeAsync(4000)
    expect(fetcher).toHaveBeenCalledTimes(3)

    // 第四次 8s (4000 * 2 = 8000, capped)
    await vi.advanceTimersByTimeAsync(8000)
    expect(fetcher).toHaveBeenCalledTimes(4)

    // 第五次 8s (capped)
    await vi.advanceTimersByTimeAsync(8000)
    expect(fetcher).toHaveBeenCalledTimes(5)
  })
})
