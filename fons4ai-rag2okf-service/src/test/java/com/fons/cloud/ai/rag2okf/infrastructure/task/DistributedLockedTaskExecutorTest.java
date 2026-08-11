package com.fons.cloud.ai.rag2okf.infrastructure.task;

import com.fons.cloud.ai.rag2okf.application.task.TaskApplicationService;
import com.fons.cloud.ai.rag2okf.common.dto.ProcessingTask;
import com.fons.cloud.ai.rag2okf.common.dto.TaskExecutionPort;
import com.fons.cloud.ai.rag2okf.common.dto.TaskExecutionResult;
import com.fons.cloud.ai.rag2okf.common.dto.TaskStatus;
import com.fons.cloud.ai.rag2okf.common.dto.TaskType;
import com.fons.cloud.ai.rag2okf.domain.entity.KbProcessingTaskEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * DistributedLockedTaskExecutor 测试，覆盖 T003 的四个场景：
 * <ol>
 *   <li>willRetry=false（重试上限）时调用 onTerminalFailure</li>
 *   <li>FatalFailure 时调用 onTerminalFailure</li>
 *   <li>willRetry=true 时不调用 onTerminalFailure</li>
 *   <li>回调异常不影响 updateTask</li>
 * </ol>
 *
 * @author hongqy
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("分布式锁任务执行器")
class DistributedLockedTaskExecutorTest {

    @Mock private TaskApplicationService taskApplicationService;
    @Mock private TaskExecutionPort port;

    private DistributedLockedTaskExecutor executor;

    @BeforeEach
    void setUp() {
        when(port.supportedType()).thenReturn(TaskType.PARSE);
        executor = new DistributedLockedTaskExecutor(taskApplicationService, List.of(port));
        ReflectionTestUtils.setField(executor, "leaseMs", 600_000L);
    }

    /** 创建 QUEUED 状态的任务，nextRunAt 为 null 表示立即可执行 */
    private ProcessingTask createQueuedTask(int attempt, int maxAttempts) {
        KbProcessingTaskEntity entity = new KbProcessingTaskEntity();
        entity.setId(1L);
        entity.setTaskKey("01J_TASK");
        entity.setTaskType(TaskType.PARSE.name());
        entity.setStatus(TaskStatus.QUEUED.name());
        entity.setAttempt(attempt);
        entity.setMaxAttempts(maxAttempts);
        entity.setVersion(0);
        entity.setSourceDocumentId(42L);
        return new ProcessingTask(entity);
    }

    @Test
    @DisplayName("RetryableFailure 且达到重试上限时调用 onTerminalFailure")
    void executeLocked_retryableFailureMaxAttempts_callsOnTerminalFailure() {
        // attempt=2, maxAttempts=3 → markRunning 后 attempt=3 → markRetryableFailure 时 3>=3 返回 false
        ProcessingTask task = createQueuedTask(2, 3);
        when(taskApplicationService.reloadInLock("01J_TASK")).thenReturn(task);
        when(taskApplicationService.isExecutable(eq(task), any(Date.class))).thenReturn(true);
        when(port.execute(task)).thenReturn(
                new TaskExecutionResult.RetryableFailure("PARSE_ERROR", "解析失败"));

        executor.executeLocked("01J_TASK");

        verify(port).onTerminalFailure(task, "PARSE_ERROR", "解析失败");
        // updateTask 被调用两次：markRunning 后和最终状态更新后
        verify(taskApplicationService, times(2)).updateTask(task);
    }

    @Test
    @DisplayName("FatalFailure 时调用 onTerminalFailure")
    void executeLocked_fatalFailure_callsOnTerminalFailure() {
        ProcessingTask task = createQueuedTask(0, 3);
        when(taskApplicationService.reloadInLock("01J_TASK")).thenReturn(task);
        when(taskApplicationService.isExecutable(eq(task), any(Date.class))).thenReturn(true);
        when(port.execute(task)).thenReturn(
                new TaskExecutionResult.FatalFailure("FATAL_ERROR", "不可恢复错误"));

        executor.executeLocked("01J_TASK");

        verify(port).onTerminalFailure(task, "FATAL_ERROR", "不可恢复错误");
        verify(taskApplicationService, times(2)).updateTask(task);
    }

    @Test
    @DisplayName("RetryableFailure 且仍可重试时不调用 onTerminalFailure")
    void executeLocked_retryableFailureCanRetry_doesNotCallOnTerminalFailure() {
        // attempt=0, maxAttempts=3 → markRunning 后 attempt=1 → markRetryableFailure 时 1<3 返回 true
        ProcessingTask task = createQueuedTask(0, 3);
        when(taskApplicationService.reloadInLock("01J_TASK")).thenReturn(task);
        when(taskApplicationService.isExecutable(eq(task), any(Date.class))).thenReturn(true);
        when(port.execute(task)).thenReturn(
                new TaskExecutionResult.RetryableFailure("TRANSIENT_ERROR", "临时错误"));

        executor.executeLocked("01J_TASK");

        verify(port, never()).onTerminalFailure(any(), any(), any());
        verify(taskApplicationService, times(2)).updateTask(task);
    }

    @Test
    @DisplayName("onTerminalFailure 抛异常时 updateTask 仍被调用")
    void executeLocked_callbackThrows_updateTaskStillCalled() {
        ProcessingTask task = createQueuedTask(2, 3);
        when(taskApplicationService.reloadInLock("01J_TASK")).thenReturn(task);
        when(taskApplicationService.isExecutable(eq(task), any(Date.class))).thenReturn(true);
        when(port.execute(task)).thenReturn(
                new TaskExecutionResult.RetryableFailure("PARSE_ERROR", "解析失败"));
        // 回调抛异常
        doThrow(new RuntimeException("callback DB error"))
                .when(port).onTerminalFailure(any(), any(), any());

        // 不应抛异常
        assertDoesNotThrow(() -> executor.executeLocked("01J_TASK"));

        // 第二次 updateTask（最终状态更新）仍被调用
        verify(taskApplicationService, times(2)).updateTask(task);
    }

    @Test
    @DisplayName("Succeeded 时不调用 onTerminalFailure")
    void executeLocked_succeeded_doesNotCallOnTerminalFailure() {
        ProcessingTask task = createQueuedTask(0, 3);
        when(taskApplicationService.reloadInLock("01J_TASK")).thenReturn(task);
        when(taskApplicationService.isExecutable(eq(task), any(Date.class))).thenReturn(true);
        when(port.execute(task)).thenReturn(
                new TaskExecutionResult.Succeeded("01J_PARSE_REV"));

        executor.executeLocked("01J_TASK");

        verify(port, never()).onTerminalFailure(any(), any(), any());
        verify(taskApplicationService, times(2)).updateTask(task);
    }
}
