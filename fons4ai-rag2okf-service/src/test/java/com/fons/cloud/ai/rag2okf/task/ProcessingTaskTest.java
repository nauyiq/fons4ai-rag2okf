package com.fons.cloud.ai.rag2okf.task;

import com.fons.cloud.ai.rag2okf.domain.entity.KbProcessingTaskEntity;
import com.fons.cloud.ai.rag2okf.domain.task.ProcessingTask;
import com.fons.cloud.ai.rag2okf.domain.task.TaskStatus;
import com.fons.cloud.ai.rag2okf.domain.task.TaskType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ProcessingTask 领域模型状态机测试。
 *
 * <p>覆盖 AC-011（任务状态机）和 AC-024（deadline 恢复）。
 *
 * @author hongqy
 */
@DisplayName("ProcessingTask 状态机")
class ProcessingTaskTest {

    private KbProcessingTaskEntity newQueuedTask() {
        KbProcessingTaskEntity entity = new KbProcessingTaskEntity();
        entity.setId(1L);
        entity.setTaskKey("01J_TASK_KEY");
        entity.setTaskType(TaskType.PARSE.name());
        entity.setStatus(TaskStatus.QUEUED.name());
        entity.setAttempt(0);
        entity.setMaxAttempts(ProcessingTask.DEFAULT_MAX_ATTEMPTS);
        entity.setProgress(0);
        entity.setVersion(0);
        return entity;
    }

    // ────────────────────────────── AC-011：QUEUED → RUNNING ──────────────────────────────

    @Test
    @DisplayName("QUEUED 可以标记为 RUNNING，attempt 递增并记录 deadline")
    void markRunning_fromQueuedSetsRunningAndDeadline() {
        KbProcessingTaskEntity entity = newQueuedTask();
        ProcessingTask task = new ProcessingTask(entity);

        Date now = new Date();
        task.markRunning("worker-1", now, 600_000L);

        assertEquals(TaskStatus.RUNNING, task.status());
        assertEquals(1, task.attempt(), "attempt 应递增到 1");
        assertEquals("worker-1", entity.getExecutionOwner());
        assertEquals(now, entity.getHeartbeatAt());
        assertNotNull(entity.getExecutionDeadline(), "deadline 必须设置");
        assertTrue(entity.getExecutionDeadline().after(now), "deadline 必须在未来");
        assertNull(entity.getErrorCode(), "开始执行时清除错误码");
    }

    @Test
    @DisplayName("非 QUEUED 状态不能标记为 RUNNING")
    void markRunning_fromNonQueuedThrows() {
        KbProcessingTaskEntity entity = newQueuedTask();
        entity.setStatus(TaskStatus.RUNNING.name());
        ProcessingTask task = new ProcessingTask(entity);

        assertThrows(IllegalStateException.class, () ->
                task.markRunning("worker-2", new Date(), 600_000L));
    }

    // ────────────────────────────── AC-011：RUNNING → SUCCEEDED ──────────────────────────────

    @Test
    @DisplayName("RUNNING 成功后转为 SUCCEEDED，progress=100")
    void markSucceeded_fromRunningSetsSucceeded() {
        KbProcessingTaskEntity entity = newQueuedTask();
        ProcessingTask task = new ProcessingTask(entity);
        task.markRunning("worker-1", new Date(), 600_000L);

        Date now = new Date();
        task.markSucceeded(now);

        assertEquals(TaskStatus.SUCCEEDED, task.status());
        assertEquals(100, entity.getProgress());
        assertTrue(task.status().isTerminal(), "SUCCEEDED 是终态");
        assertNull(entity.getExecutionOwner(), "完成后清除 executionOwner");
        assertNull(entity.getExecutionDeadline(), "完成后清除 deadline");
    }

    // ────────────────────────────── AC-011：RUNNING → RETRY_WAIT ──────────────────────────────

    @Test
    @DisplayName("RUNNING 可重试失败转为 RETRY_WAIT，设置指数退避")
    void markRetryableFailure_setsRetryWaitWithBackoff() {
        KbProcessingTaskEntity entity = newQueuedTask();
        entity.setMaxAttempts(3);
        ProcessingTask task = new ProcessingTask(entity);
        task.markRunning("worker-1", new Date(), 600_000L);

        Date now = new Date();
        boolean willRetry = task.markRetryableFailure("PARSE_ERROR", "解析失败", now);

        assertTrue(willRetry, "首次失败应继续重试");
        assertEquals(TaskStatus.RETRY_WAIT, task.status());
        assertEquals("PARSE_ERROR", entity.getErrorCode());
        assertNotNull(entity.getNextRunAt(), "退避时间必须设置");
        assertTrue(entity.getNextRunAt().after(now), "退避时间在未来");
        long backoff = entity.getNextRunAt().getTime() - now.getTime();
        assertEquals(ProcessingTask.BACKOFF_BASE_MS, backoff, "首次退避 = base");
    }

    @Test
    @DisplayName("达到 maxAttempts 后可重试失败转为 FAILED 终态")
    void markRetryableFailure_exceedsMaxAttemptsBecomesFailed() {
        KbProcessingTaskEntity entity = newQueuedTask();
        entity.setMaxAttempts(2);
        entity.setAttempt(2);
        entity.setStatus(TaskStatus.RUNNING.name());
        ProcessingTask task = new ProcessingTask(entity);

        Date now = new Date();
        boolean willRetry = task.markRetryableFailure("PARSE_ERROR", "解析失败", now);

        assertFalse(willRetry, "达到上限应转为 FAILED");
        assertEquals(TaskStatus.FAILED, task.status());
        assertTrue(task.status().isTerminal(), "FAILED 是终态");
        assertNull(entity.getNextRunAt(), "FAILED 不设置 nextRunAt");
    }

    // ────────────────────────────── AC-011：RETRY_WAIT → QUEUED ──────────────────────────────

    @Test
    @DisplayName("RETRY_WAIT 退避到期后恢复为 QUEUED")
    void markRequeued_fromRetryWaitSetsQueued() {
        KbProcessingTaskEntity entity = newQueuedTask();
        entity.setStatus(TaskStatus.RETRY_WAIT.name());
        ProcessingTask task = new ProcessingTask(entity);

        task.markRequeued();

        assertEquals(TaskStatus.QUEUED, task.status());
        assertNull(entity.getNextRunAt(), "QUEUED 清除 nextRunAt");
    }

    // ────────────────────────────── AC-011：指数退避计算 ──────────────────────────────

    @Test
    @DisplayName("指数退避按 2^n 递增且不超过上限")
    void computeBackoffMs_exponentialWithCap() {
        assertEquals(ProcessingTask.BACKOFF_BASE_MS, ProcessingTask.computeBackoffMs(1), "attempt=1: base");
        assertEquals(ProcessingTask.BACKOFF_BASE_MS * 2, ProcessingTask.computeBackoffMs(2), "attempt=2: base*2");
        assertEquals(ProcessingTask.BACKOFF_BASE_MS * 4, ProcessingTask.computeBackoffMs(3), "attempt=3: base*4");
        assertTrue(ProcessingTask.computeBackoffMs(20) <= ProcessingTask.BACKOFF_CAP_MS, "不超过上限");
    }

    // ────────────────────────────── AC-024：deadline 恢复 ──────────────────────────────

    @Test
    @DisplayName("deadline 过期的 RUNNING 任务可恢复为 QUEUED")
    void recoverFromStale_setsQueuedAndClearsDeadline() {
        KbProcessingTaskEntity entity = newQueuedTask();
        entity.setStatus(TaskStatus.RUNNING.name());
        entity.setExecutionDeadline(new Date(System.currentTimeMillis() - 1000)); // 已过期
        ProcessingTask task = new ProcessingTask(entity);

        Date now = new Date();
        assertTrue(task.isStale(now), "deadline 已过期应判定为 stale");

        task.recoverFromStale(now);

        assertEquals(TaskStatus.QUEUED, task.status());
        assertNull(entity.getExecutionOwner(), "恢复后清除 executionOwner");
        assertNull(entity.getExecutionDeadline(), "恢复后清除 deadline");
        assertEquals("TASK_DEADLINE_EXCEEDED", entity.getErrorCode(), "记录恢复原因");
    }

    @Test
    @DisplayName("deadline 未过期的 RUNNING 任务不判定为 stale")
    void isStale_notExpiredReturnsFalse() {
        KbProcessingTaskEntity entity = newQueuedTask();
        entity.setStatus(TaskStatus.RUNNING.name());
        entity.setExecutionDeadline(new Date(System.currentTimeMillis() + 600_000)); // 未过期
        ProcessingTask task = new ProcessingTask(entity);

        assertFalse(task.isStale(new Date()), "deadline 未过期不判定为 stale");
    }

    @Test
    @DisplayName("非 RUNNING 状态不能恢复")
    void recoverFromStale_fromNonRunningThrows() {
        KbProcessingTaskEntity entity = newQueuedTask();
        entity.setStatus(TaskStatus.SUCCEEDED.name());
        ProcessingTask task = new ProcessingTask(entity);

        assertThrows(IllegalStateException.class, () -> task.recoverFromStale(new Date()));
    }

    // ────────────────────────────── AC-011：候选判定 ──────────────────────────────

    @Test
    @DisplayName("QUEUED 且 nextRunAt 为 null 是候选任务")
    void isCandidate_queuedWithNullNextRunAt() {
        KbProcessingTaskEntity entity = newQueuedTask();
        ProcessingTask task = new ProcessingTask(entity);

        assertTrue(task.isCandidate(new Date()));
    }

    @Test
    @DisplayName("QUEUED 且 nextRunAt 已到期是候选任务")
    void isCandidate_queuedWithExpiredNextRunAt() {
        KbProcessingTaskEntity entity = newQueuedTask();
        entity.setNextRunAt(new Date(System.currentTimeMillis() - 1000));
        ProcessingTask task = new ProcessingTask(entity);

        assertTrue(task.isCandidate(new Date()));
    }

    @Test
    @DisplayName("QUEUED 但 nextRunAt 未到期不是候选任务")
    void isCandidate_queuedWithFutureNextRunAtReturnsFalse() {
        KbProcessingTaskEntity entity = newQueuedTask();
        entity.setNextRunAt(new Date(System.currentTimeMillis() + 60_000));
        ProcessingTask task = new ProcessingTask(entity);

        assertFalse(task.isCandidate(new Date()));
    }

    @Test
    @DisplayName("RUNNING 状态不是候选任务")
    void isCandidate_runningReturnsFalse() {
        KbProcessingTaskEntity entity = newQueuedTask();
        entity.setStatus(TaskStatus.RUNNING.name());
        ProcessingTask task = new ProcessingTask(entity);

        assertFalse(task.isCandidate(new Date()));
    }

    // ────────────────────────────── AC-011：心跳 ──────────────────────────────

    @Test
    @DisplayName("RUNNING 状态可以更新心跳和进度")
    void heartbeat_updatesProgressAndStage() {
        KbProcessingTaskEntity entity = newQueuedTask();
        ProcessingTask task = new ProcessingTask(entity);
        task.markRunning("worker-1", new Date(), 600_000L);

        Date now = new Date();
        task.heartbeat(50, "PARSING", now);

        assertEquals(50, entity.getProgress());
        assertEquals("PARSING", entity.getStage());
        assertEquals(now, entity.getHeartbeatAt());
    }

    @Test
    @DisplayName("非 RUNNING 状态不能心跳")
    void heartbeat_fromNonRunningThrows() {
        KbProcessingTaskEntity entity = newQueuedTask();
        ProcessingTask task = new ProcessingTask(entity);

        assertThrows(IllegalStateException.class, () -> task.heartbeat(50, "PARSING", new Date()));
    }

    // ────────────────────────────── AC-011：不可重试失败 ──────────────────────────────

    @Test
    @DisplayName("RUNNING 不可重试失败直接转为 FAILED")
    void markFailed_fromRunningSetsFailed() {
        KbProcessingTaskEntity entity = newQueuedTask();
        ProcessingTask task = new ProcessingTask(entity);
        task.markRunning("worker-1", new Date(), 600_000L);

        Date now = new Date();
        task.markFailed("MODEL_AUTH_FAILED", "模型认证失败", now);

        assertEquals(TaskStatus.FAILED, task.status());
        assertEquals("MODEL_AUTH_FAILED", entity.getErrorCode());
        assertTrue(task.status().isTerminal());
    }
}
