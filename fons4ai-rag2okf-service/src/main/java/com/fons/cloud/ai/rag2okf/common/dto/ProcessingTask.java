package com.fons.cloud.ai.rag2okf.common.dto;

import com.fons.cloud.ai.rag2okf.domain.entity.KbProcessingTaskEntity;

import java.util.Date;

/**
 * 处理任务领域模型，封装状态机流转与恢复规则。
 *
 * <p>状态机规则见技术设计 §7.3：
 * <ul>
 *   <li>QUEUED -> RUNNING：在 taskKey 分布式锁内开始执行</li>
 *   <li>RUNNING -> SUCCEEDED：完成，写结果与状态同事务</li>
 *   <li>RUNNING -> RETRY_WAIT：可重试失败，指数退避</li>
 *   <li>RUNNING -> FAILED：不可重试失败或重试上限</li>
 *   <li>RETRY_WAIT -> QUEUED：退避到期</li>
 *   <li>RUNNING -> QUEUED：deadline 恢复</li>
 * </ul>
 *
 * <p>本类不依赖基础设施，只操作传入的实体字段。
 *
 * @author hongqy
 */
public class ProcessingTask {

    /** 默认最大执行次数。 */
    public static final int DEFAULT_MAX_ATTEMPTS = 3;

    /** 退避基准毫秒，实际等待 = base * 2^(attempt-1)。 */
    public static final long BACKOFF_BASE_MS = 5_000L;

    /** 退避上限毫秒（5 分钟）。 */
    public static final long BACKOFF_CAP_MS = 300_000L;

    /** 默认执行租约时长（10 分钟），用于 deadline 恢复。 */
    public static final long DEFAULT_LEASE_MS = 600_000L;

    private final KbProcessingTaskEntity entity;

    public ProcessingTask(KbProcessingTaskEntity entity) {
        this.entity = entity;
    }

    public KbProcessingTaskEntity entity() {
        return entity;
    }

    public String taskKey() {
        return entity.getTaskKey();
    }

    public TaskStatus status() {
        return TaskStatus.valueOf(entity.getStatus());
    }

    public TaskType taskType() {
        return TaskType.valueOf(entity.getTaskType());
    }

    public int attempt() {
        return entity.getAttempt();
    }

    public int maxAttempts() {
        return entity.getMaxAttempts();
    }

    /**
     * 从 QUEUED 转为 RUNNING：记录执行实例、心跳和截止时间。
     *
     * @param executionOwner 执行实例标识
     * @param now            当前时间
     * @param leaseMs        执行租约时长（毫秒）
     */
    public void markRunning(String executionOwner, Date now, long leaseMs) {
        TaskStatus current = status();
        if (current != TaskStatus.QUEUED) {
            throw new IllegalStateException(
                    "Task " + entity.getTaskKey() + " cannot transition from " + current + " to RUNNING");
        }
        entity.setStatus(TaskStatus.RUNNING.name());
        entity.setAttempt(entity.getAttempt() + 1);
        entity.setExecutionOwner(executionOwner);
        entity.setHeartbeatAt(now);
        entity.setExecutionDeadline(new Date(now.getTime() + leaseMs));
        entity.setStage(null);
        entity.setProgress(0);
        entity.setErrorCode(null);
        entity.setErrorMessage(null);
    }

    /**
     * 从 RUNNING 转为 SUCCEEDED。
     *
     * @param now 当前时间
     */
    public void markSucceeded(Date now) {
        TaskStatus current = status();
        if (current != TaskStatus.RUNNING) {
            throw new IllegalStateException(
                    "Task " + entity.getTaskKey() + " cannot transition from " + current + " to SUCCEEDED");
        }
        entity.setStatus(TaskStatus.SUCCEEDED.name());
        entity.setProgress(100);
        entity.setExecutionOwner(null);
        entity.setHeartbeatAt(now);
        entity.setExecutionDeadline(null);
        entity.setNextRunAt(null);
    }

    /**
     * 从 RUNNING 转为 RETRY_WAIT 或 FAILED。
     *
     * <p>当 attempt >= maxAttempts 时转为 FAILED；否则转为 RETRY_WAIT 并设置指数退避。
     *
     * @param errorCode    安全化错误码
     * @param errorMessage 安全化错误摘要
     * @param now          当前时间
     * @return true 如果转为 RETRY_WAIT，false 如果转为 FAILED
     */
    public boolean markRetryableFailure(String errorCode, String errorMessage, Date now) {
        TaskStatus current = status();
        if (current != TaskStatus.RUNNING) {
            throw new IllegalStateException(
                    "Task " + entity.getTaskKey() + " cannot transition from " + current + " to RETRY_WAIT/FAILED");
        }
        entity.setErrorCode(errorCode);
        entity.setErrorMessage(errorMessage);
        entity.setExecutionOwner(null);
        entity.setExecutionDeadline(null);
        entity.setHeartbeatAt(now);

        if (entity.getAttempt() >= entity.getMaxAttempts()) {
            entity.setStatus(TaskStatus.FAILED.name());
            entity.setNextRunAt(null);
            return false;
        }
        entity.setStatus(TaskStatus.RETRY_WAIT.name());
        entity.setNextRunAt(new Date(now.getTime() + computeBackoffMs(entity.getAttempt())));
        return true;
    }

    /**
     * 从 RUNNING 转为 FAILED（不可重试失败）。
     *
     * @param errorCode    安全化错误码
     * @param errorMessage 安全化错误摘要
     * @param now          当前时间
     */
    public void markFailed(String errorCode, String errorMessage, Date now) {
        TaskStatus current = status();
        if (current != TaskStatus.RUNNING) {
            throw new IllegalStateException(
                    "Task " + entity.getTaskKey() + " cannot transition from " + current + " to FAILED");
        }
        entity.setStatus(TaskStatus.FAILED.name());
        entity.setErrorCode(errorCode);
        entity.setErrorMessage(errorMessage);
        entity.setExecutionOwner(null);
        entity.setExecutionDeadline(null);
        entity.setHeartbeatAt(now);
        entity.setNextRunAt(null);
    }

    /**
     * 从 RETRY_WAIT 转为 QUEUED（退避到期恢复）。
     */
    public void markRequeued() {
        TaskStatus current = status();
        if (current != TaskStatus.RETRY_WAIT) {
            throw new IllegalStateException(
                    "Task " + entity.getTaskKey() + " cannot transition from " + current + " to QUEUED");
        }
        entity.setStatus(TaskStatus.QUEUED.name());
        entity.setNextRunAt(null);
    }

    /**
     * 从 RUNNING 恢复为 QUEUED（deadline 超时恢复）。
     *
     * <p>恢复时不清除 attempt，保留错误信息供观察。
     *
     * @param now 当前时间
     */
    public void recoverFromStale(Date now) {
        TaskStatus current = status();
        if (current != TaskStatus.RUNNING) {
            throw new IllegalStateException(
                    "Task " + entity.getTaskKey() + " cannot recover from " + current);
        }
        entity.setStatus(TaskStatus.QUEUED.name());
        entity.setExecutionOwner(null);
        entity.setExecutionDeadline(null);
        entity.setHeartbeatAt(now);
        entity.setNextRunAt(null);
        entity.setErrorCode("TASK_DEADLINE_EXCEEDED");
        entity.setErrorMessage("执行租约超时，已恢复到队列");
    }

    /**
     * 更新心跳和执行进度。
     *
     * @param progress 进度百分比 0-100
     * @param stage    当前执行阶段
     * @param now      当前时间
     */
    public void heartbeat(int progress, String stage, Date now) {
        if (status() != TaskStatus.RUNNING) {
            throw new IllegalStateException(
                    "Task " + entity.getTaskKey() + " cannot heartbeat from " + status());
        }
        entity.setProgress(progress);
        entity.setStage(stage);
        entity.setHeartbeatAt(now);
    }

    /**
     * 判断是否为候选执行任务。
     *
     * @param now 当前时间
     * @return true 如果状态为 QUEUED 且 nextRunAt 为 null 或已到期
     */
    public boolean isCandidate(Date now) {
        if (status() != TaskStatus.QUEUED) {
            return false;
        }
        return entity.getNextRunAt() == null || !entity.getNextRunAt().after(now);
    }

    /**
     * 判断是否为 stale RUNNING 任务（deadline 已过期）。
     *
     * @param now 当前时间
     * @return true 如果状态为 RUNNING 且 executionDeadline 早于 now
     */
    public boolean isStale(Date now) {
        if (status() != TaskStatus.RUNNING) {
            return false;
        }
        return entity.getExecutionDeadline() != null && entity.getExecutionDeadline().before(now);
    }

    /**
     * 计算指数退避毫秒数。
     *
     * @param attempt 当前已执行次数
     * @return 退避毫秒数，不超过上限
     */
    public static long computeBackoffMs(int attempt) {
        long backoff = BACKOFF_BASE_MS * (1L << Math.max(0, attempt - 1));
        return Math.min(backoff, BACKOFF_CAP_MS);
    }
}
