package com.fons.cloud.ai.rag2okf.infrastructure.task;

import com.fons.cloud.ai.rag2okf.common.constants.Rag2OkfResultCode;

import com.fons.cloud.ai.rag2okf.application.task.TaskApplicationService;
import com.fons.cloud.ai.rag2okf.common.exeception.TaskExecutionException;
import com.fons.cloud.ai.rag2okf.common.dto.ProcessingTask;
import com.fons.cloud.ai.rag2okf.common.dto.TaskExecutionPort;
import com.fons.cloud.ai.rag2okf.common.dto.TaskExecutionResult;
import com.fons.cloud.ai.rag2okf.common.dto.TaskType;
import com.fons.cloud.lock.annotation.DistributeLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 分布式锁任务执行器。
 *
 * <p>独立 Spring Bean，通过 {@code @DistributeLock} 注解在 taskKey 维度获取分布式锁：
 * <ul>
 *   <li>scene = {@code rag2okf:task-execute}</li>
 *   <li>waitTime = 0：未取得锁立即跳过，不等待</li>
 *   <li>expireTime = -1（默认）：Redisson watchdog 自动续期，避免长解析被固定过期释放</li>
 * </ul>
 *
 * <p>锁内流程（幂等与状态机校验，不是 MySQL 锁）：
 * <ol>
 *   <li>重新读取任务状态</li>
 *   <li>校验仍为 QUEUED 且 nextRunAt 已到期，否则跳过</li>
 *   <li>CAS 标记 RUNNING，记录 executionOwner 和 deadline</li>
 *   <li>路由到对应 TaskExecutionPort 执行</li>
 *   <li>CAS 更新最终状态（SUCCEEDED / RETRY_WAIT / FAILED）</li>
 * </ol>
 *
 * <p>本类不启动 MySQL 事务；每个状态更新由 {@link TaskApplicationService#updateTask} 独立事务保证原子性。
 *
 * @author hongqy
 */
@Slf4j
@Component
public class DistributedLockedTaskExecutor {

    private final TaskApplicationService taskApplicationService;
    private final Map<TaskType, TaskExecutionPort> portRegistry;

    @Value("${rag2okf.task.lease-ms:" + ProcessingTask.DEFAULT_LEASE_MS + "}")
    private long leaseMs;

    @Autowired
    public DistributedLockedTaskExecutor(TaskApplicationService taskApplicationService,
                                         List<TaskExecutionPort> ports) {
        this.taskApplicationService = taskApplicationService;
        this.portRegistry = new EnumMap<>(TaskType.class);
        for (TaskExecutionPort port : ports) {
            TaskExecutionPort previous = portRegistry.put(port.supportedType(), port);
            if (previous != null) {
                throw new IllegalStateException(
                        "Duplicate TaskExecutionPort for type " + port.supportedType());
            }
        }
        log.info("TaskExecutionPort registry initialized: {}", portRegistry.keySet());
    }

    /**
     * 在 taskKey 分布式锁内执行任务。
     *
     * <p>锁获取失败时由 {@code DistributeLockAspect} 抛出 {@code DistributeLockException}，
     * 调用方（{@code TaskCandidateScheduler}）应捕获并跳过。
     *
     * @param taskKey 任务业务标识
     */
    @DistributeLock(scene = "rag2okf:task-execute", keyExpression = "#taskKey", waitTime = 0)
    public void executeLocked(String taskKey) {
        Date now = new Date();

        // 1. 锁内重新读取任务
        ProcessingTask task = taskApplicationService.reloadInLock(taskKey);
        if (task == null) {
            log.warn("Task not found in lock: {}", taskKey);
            return;
        }

        // 2. 校验仍可执行（幂等与状态机校验）
        if (!taskApplicationService.isExecutable(task, now)) {
            log.debug("Task not executable in lock, skipping: taskKey={}, status={}",
                    taskKey, task.status());
            return;
        }

        // 3. CAS 标记 RUNNING
        String executionOwner = resolveExecutionOwner();
        task.markRunning(executionOwner, now, leaseMs);
        taskApplicationService.updateTask(task);
        log.info("Task started: taskKey={}, type={}, attempt={}",
                taskKey, task.taskType(), task.attempt());

        // 4. 路由执行
        TaskExecutionPort port = portRegistry.get(task.taskType());
        if (port == null) {
            log.error("No TaskExecutionPort for type {}, marking task as FAILED", task.taskType());
            task.markFailed(Rag2OkfResultCode.NO_EXECUTOR.getCode(),
                    "No executor registered for " + task.taskType(), new Date());
            taskApplicationService.updateTask(task);
            return;
        }

        TaskExecutionResult result;
        try {
            result = port.execute(task);
        } catch (Exception e) {
            log.error("Task execution threw exception: taskKey={}", taskKey, e);
            result = new TaskExecutionResult.RetryableFailure(
                    Rag2OkfResultCode.TASK_EXECUTION_ERROR.getCode(),
                    "执行异常: " + e.getClass().getSimpleName());
        }

        // 5. CAS 更新最终状态
        Date completedAt = new Date();
        switch (result) {
            case TaskExecutionResult.Succeeded s -> {
                task.markSucceeded(completedAt);
                log.info("Task succeeded: taskKey={}", taskKey);
            }
            case TaskExecutionResult.RetryableFailure r -> {
                boolean willRetry = task.markRetryableFailure(r.errorCode(), r.errorMessage(), completedAt);
                log.warn("Task retryable failure: taskKey={}, willRetry={}", taskKey, willRetry);
                // 终态失败（重试上限）时通知执行器同步业务级状态
                if (!willRetry) {
                    try {
                        port.onTerminalFailure(task, r.errorCode(), r.errorMessage());
                    } catch (Exception e) {
                        log.warn("onTerminalFailure callback failed: taskKey={}", taskKey, e);
                    }
                }
            }
            case TaskExecutionResult.FatalFailure f -> {
                task.markFailed(f.errorCode(), f.errorMessage(), completedAt);
                log.error("Task fatal failure: taskKey={}, errorCode={}", taskKey, f.errorCode());
                // 致命失败时通知执行器同步业务级状态
                try {
                    port.onTerminalFailure(task, f.errorCode(), f.errorMessage());
                } catch (Exception e) {
                    log.warn("onTerminalFailure callback failed: taskKey={}", taskKey, e);
                }
            }
        }
        taskApplicationService.updateTask(task);
    }

    /**
     * 在 taskKey 分布式锁内恢复 stale RUNNING 任务。
     *
     * @param taskKey 任务业务标识
     */
    @DistributeLock(scene = "rag2okf:task-recover", keyExpression = "#taskKey", waitTime = 0)
    public void recoverStale(String taskKey) {
        ProcessingTask task = taskApplicationService.reloadInLock(taskKey);
        if (task == null) {
            log.warn("Task not found for recovery: {}", taskKey);
            return;
        }

        Date now = new Date();
        if (!task.isStale(now)) {
            log.debug("Task no longer stale, skipping recovery: taskKey={}", taskKey);
            return;
        }

        log.warn("Recovering stale task: taskKey={}, deadline={}",
                taskKey, task.entity().getExecutionDeadline());
        task.recoverFromStale(now);
        taskApplicationService.updateTask(task);
    }

    private String resolveExecutionOwner() {
        // 使用 hostname + thread name 作为执行实例标识，不表示数据库锁
        String hostname = System.getProperty("host.name", "unknown");
        String thread = Thread.currentThread().getName();
        return hostname + ":" + thread;
    }
}
