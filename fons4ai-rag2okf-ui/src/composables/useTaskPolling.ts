import { onBeforeUnmount, getCurrentInstance } from 'vue'
import type { TaskSummary } from '../api/documents'

/** useTaskPolling 配置选项 */
export interface UseTaskPollingOptions {
  /** 获取最新任务状态的函数 */
  fetcher: () => Promise<TaskSummary | undefined>
  /** 判断任务是否仍需轮询（返回 false 时停止）。通常检查 status 是否为 QUEUED/RUNNING */
  isActive: () => boolean
  /** 任务更新回调，接收最新 task 数据 */
  onUpdate: (task: TaskSummary) => void
  /** 轮询间隔（ms），默认 5000 */
  intervalMs?: number
}

/**
 * 异步任务轮询组合式函数。
 *
 * <p>封装固定间隔轮询策略，用于文档解析、发布、重新分块等异步任务的进度刷新。
 *
 * <ul>
 *   <li>固定间隔：每次轮询后按 intervalMs 间隔执行下一次</li>
 *   <li>终态停止：isActive 返回 false 时自动停止</li>
 *   <li>单飞：重叠请求时复用进行中的 Promise，不重复发起</li>
 *   <li>卸载清理：onBeforeUnmount 清理定时器，防止内存泄漏</li>
 *   <li>局部刷新：onUpdate 回调只更新传入数据，不遮蔽整页</li>
 * </ul>
 */
export function useTaskPolling(options: UseTaskPollingOptions): {
  start: () => void
  stop: () => void
} {
  const {
    fetcher,
    isActive,
    onUpdate,
    intervalMs = 5000,
  } = options

  /** 当前定时器 ID，null 表示无待执行定时器 */
  let timerId: ReturnType<typeof setTimeout> | null = null
  /** 进行中的 fetcher Promise，null 表示无进行中请求 */
  let inFlight: Promise<TaskSummary | undefined> | null = null
  /** 轮询是否处于运行状态 */
  let running = false

  /** 清除待执行定时器 */
  function clearTimer(): void {
    if (timerId !== null) {
      clearTimeout(timerId)
      timerId = null
    }
  }

  /** 安排下一次轮询。如果 isActive 返回 false 则停止 */
  function scheduleNext(): void {
    if (!running) return
    if (!isActive()) {
      stop()
      return
    }
    timerId = setTimeout(poll, intervalMs)
  }

  /** 执行一次轮询：调用 fetcher 获取最新任务状态 */
  async function poll(): Promise<void> {
    timerId = null
    if (!running) return

    // 单飞：如果有进行中的请求，跳过本次轮询，等 inFlight 完成后会自行安排下一次
    if (inFlight !== null) return

    // 再次检查 isActive，防止在定时器等待期间任务已终态
    if (!isActive()) {
      stop()
      return
    }

    inFlight = fetcher()
    try {
      const task = await inFlight
      if (task) {
        onUpdate(task)
      }
    } finally {
      inFlight = null
    }

    // fetcher 完成后检查是否继续轮询
    if (!running || !isActive()) {
      stop()
      return
    }

    scheduleNext()
  }

  /** 启动轮询。如果已在运行则忽略（防重复启动） */
  function start(): void {
    if (running) return
    running = true
    scheduleNext()
  }

  /** 停止轮询并清理定时器 */
  function stop(): void {
    running = false
    clearTimer()
  }

  // 仅在组件上下文中注册卸载钩子，避免非组件环境调用警告
  if (getCurrentInstance()) {
    onBeforeUnmount(() => {
      stop()
    })
  }

  return { start, stop }
}
