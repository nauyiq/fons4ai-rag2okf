package com.fons.cloud.ai.rag2okf.task;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fons.cloud.ai.rag2okf.common.dto.ModelBusinessKeyGenerator;
import com.fons.cloud.ai.rag2okf.application.task.TaskApplicationService;
import com.fons.cloud.ai.rag2okf.common.exeception.TaskExecutionException;
import com.fons.cloud.ai.rag2okf.domain.entity.KbProcessingTaskEntity;
import com.fons.cloud.ai.rag2okf.domain.service.KbProcessingTaskDomainService;
import com.fons.cloud.ai.rag2okf.common.dto.ProcessingTask;
import com.fons.cloud.ai.rag2okf.common.dto.TaskStatus;
import com.fons.cloud.ai.rag2okf.common.dto.TaskType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * TaskApplicationService 测试。
 *
 * <p>覆盖 AC-017（重复提交返回原 taskKey）和 AC-023（任务持久化与 GET status）。
 *
 * @author hongqy
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("任务应用服务")
class TaskApplicationServiceTest {

    @Mock private KbProcessingTaskDomainService taskDomainService;
    @Mock private ModelBusinessKeyGenerator keyGenerator;

    @InjectMocks private TaskApplicationService service;

    private final AtomicLong idSeq = new AtomicLong(100L);

    // ────────────────────────────── AC-017：幂等创建 ──────────────────────────────

    @Test
    @DisplayName("相同 idempotencyKey 返回原 taskKey，不创建新任务")
    void createTask_duplicateIdempotencyKeyReturnsOriginal() {
        KbProcessingTaskEntity existing = new KbProcessingTaskEntity();
        existing.setId(50L);
        existing.setTaskKey("01J_ORIGINAL_TASK");
        existing.setTaskType(TaskType.PARSE.name());
        existing.setStatus(TaskStatus.QUEUED.name());
        existing.setSourceDocumentId(1L);
        existing.setIdempotencyKey("client-key-001");
        existing.setVersion(0);

        when(taskDomainService.getOne(any())).thenReturn(existing);

        ProcessingTask result = service.createTask(
                10L, 20L, 1L, TaskType.PARSE, "01J_PARSE_REV", "client-key-001", "{}");

        assertEquals("01J_ORIGINAL_TASK", result.taskKey(), "应返回原 taskKey");
        assertEquals(TaskStatus.QUEUED, result.status());
        verify(keyGenerator, never()).nextKey();
        verify(taskDomainService, never()).save(any());
    }

    @Test
    @DisplayName("不同 idempotencyKey 创建新任务，生成不同 taskKey")
    void createTask_differentIdempotencyKeyCreatesNew() {
        when(taskDomainService.getOne(any())).thenReturn(null);
        when(keyGenerator.nextKey()).thenReturn("01J_NEW_TASK");
        when(taskDomainService.save(any())).thenAnswer(inv -> {
            ((KbProcessingTaskEntity) inv.getArgument(0)).setId(idSeq.incrementAndGet());
            return true;
        });

        ProcessingTask result = service.createTask(
                10L, 20L, 1L, TaskType.PARSE, null, "client-key-002", "{}");

        assertEquals("01J_NEW_TASK", result.taskKey());
        assertEquals(TaskStatus.QUEUED, result.status());
        assertEquals(0, result.attempt());
        assertEquals(ProcessingTask.DEFAULT_MAX_ATTEMPTS, result.maxAttempts());
        verify(taskDomainService).save(any());
    }

    // ────────────────────────────── AC-023：状态查询 ──────────────────────────────

    @Test
    @DisplayName("按 taskKey 查询返回任务状态")
    void findByKey_returnsTaskStatus() {
        KbProcessingTaskEntity entity = new KbProcessingTaskEntity();
        entity.setId(50L);
        entity.setTaskKey("01J_TASK");
        entity.setTaskType(TaskType.PUBLISH.name());
        entity.setStatus(TaskStatus.RUNNING.name());
        entity.setProgress(42);
        entity.setAttempt(1);
        entity.setMaxAttempts(3);
        entity.setStage("INDEXING");
        entity.setVersion(1);

        when(taskDomainService.getOne(any())).thenReturn(entity);

        ProcessingTask result = service.findByKey("01J_TASK");

        assertNotNull(result);
        assertEquals("01J_TASK", result.taskKey());
        assertEquals(TaskType.PUBLISH, result.taskType());
        assertEquals(TaskStatus.RUNNING, result.status());
        assertEquals(42, result.entity().getProgress());
        assertEquals("INDEXING", result.entity().getStage());
    }

    @Test
    @DisplayName("查询不存在的 taskKey 返回 null")
    void findByKey_notFoundReturnsNull() {
        when(taskDomainService.getOne(any())).thenReturn(null);

        ProcessingTask result = service.findByKey("01J_NONEXISTENT");

        assertNull(result);
    }

    // ────────────────────────────── AC-023：CAS 更新 ──────────────────────────────

    @Test
    @DisplayName("CAS 更新成功（updateById 返回 true）")
    void updateTask_casSuccess() {
        KbProcessingTaskEntity entity = new KbProcessingTaskEntity();
        entity.setId(50L);
        entity.setTaskKey("01J_TASK");
        entity.setVersion(0);
        entity.setStatus(TaskStatus.QUEUED.name());
        entity.setAttempt(0);
        entity.setMaxAttempts(ProcessingTask.DEFAULT_MAX_ATTEMPTS);
        ProcessingTask task = new ProcessingTask(entity);
        task.markRunning("worker-1", new Date(), 600_000L);

        when(taskDomainService.updateById(any())).thenReturn(true);

        assertDoesNotThrow(() -> service.updateTask(task));
    }

    @Test
    @DisplayName("CAS 更新失败（乐观锁冲突）抛出异常")
    void updateTask_casFailureThrows() {
        KbProcessingTaskEntity entity = new KbProcessingTaskEntity();
        entity.setId(50L);
        entity.setTaskKey("01J_TASK");
        entity.setVersion(0);
        entity.setStatus(TaskStatus.QUEUED.name());
        entity.setAttempt(0);
        entity.setMaxAttempts(ProcessingTask.DEFAULT_MAX_ATTEMPTS);
        ProcessingTask task = new ProcessingTask(entity);
        task.markRunning("worker-1", new Date(), 600_000L);

        when(taskDomainService.updateById(any())).thenReturn(false);

        assertThrows(TaskExecutionException.class, () -> service.updateTask(task));
    }

    // ────────────────────────────── AC-023：候选扫描 ──────────────────────────────

    @Test
    @DisplayName("扫描候选任务返回 QUEUED 且 nextRunAt 已到期的任务")
    void scanCandidates_returnsQueuedTasks() {
        KbProcessingTaskEntity candidate = new KbProcessingTaskEntity();
        candidate.setId(50L);
        candidate.setTaskKey("01J_TASK");
        candidate.setStatus(TaskStatus.QUEUED.name());

        when(taskDomainService.list(any(Wrapper.class))).thenReturn(List.of(candidate));

        List<ProcessingTask> results = service.scanCandidates(new Date(), 10);

        assertEquals(1, results.size());
        assertEquals("01J_TASK", results.get(0).taskKey());
        assertTrue(results.get(0).isCandidate(new Date()));
    }

    @Test
    @DisplayName("扫描 stale 任务返回 deadline 已过期的 RUNNING 任务")
    void scanStaleTasks_returnsExpiredRunningTasks() {
        KbProcessingTaskEntity stale = new KbProcessingTaskEntity();
        stale.setId(50L);
        stale.setTaskKey("01J_STALE_TASK");
        stale.setStatus(TaskStatus.RUNNING.name());
        stale.setExecutionDeadline(new Date(System.currentTimeMillis() - 5000));

        when(taskDomainService.list(any(Wrapper.class))).thenReturn(List.of(stale));

        List<ProcessingTask> results = service.scanStaleTasks(new Date(), 10);

        assertEquals(1, results.size());
        assertTrue(results.get(0).isStale(new Date()));
    }
}
