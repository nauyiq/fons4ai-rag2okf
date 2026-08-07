package com.fons.cloud.ai.rag2okf.infrastructure.task;

import com.fons.cloud.ai.rag2okf.application.task.TaskApplicationService;
import com.fons.cloud.ai.rag2okf.common.dto.ProcessingTask;
import com.fons.cloud.lock.common.DistributeLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/**
 * 任务候选扫描器。
 *
 * <p>定时扫描 QUEUED 任务，委托 {@link DistributedLockedTaskExecutor} 在 taskKey 分布式锁内执行。
 * 扫描查询不加数据库锁，不使用 FOR UPDATE / SKIP LOCKED；互斥完全由分布式锁保证。
 *
 * <p>锁获取失败（DistributeLockException）时跳过当前候选，不阻塞后续任务。
 *
 * @author hongqy
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskCandidateScheduler {

    private final TaskApplicationService taskApplicationService;
    private final DistributedLockedTaskExecutor taskExecutor;

    @Value("${rag2okf.task.scan-batch-size:20}")
    private int scanBatchSize;

    /**
     * 定时扫描候选任务并尝试执行。
     *
     * <p>固定延迟 5 秒，初始延迟 10 秒。worker 不占用 HTTP 线程。
     */
    @Scheduled(fixedDelayString = "${rag2okf.task.scan-interval-ms:60000}", initialDelayString = "10000")
    public void scanAndExecute() {
        Date now = new Date();
        List<ProcessingTask> candidates = taskApplicationService.scanCandidates(now, scanBatchSize);
        if (candidates.isEmpty()) {
            return;
        }

        log.debug("Found {} candidate tasks", candidates.size());
        for (ProcessingTask candidate : candidates) {
            String taskKey = candidate.taskKey();
            try {
                taskExecutor.executeLocked(taskKey);
            } catch (DistributeLockException e) {
                // 锁被其他实例持有，跳过本轮候选
                log.debug("Task lock held by another instance, skipping: taskKey={}", taskKey);
            } catch (Exception e) {
                // 单个任务执行异常不阻塞后续任务
                log.error("Task execution failed: taskKey={}", taskKey, e);
            }
        }
    }

    /**
     * 定时扫描 stale RUNNING 任务并尝试恢复。
     *
     * <p>固定延迟 30 秒，初始延迟 30 秒。
     */
    @Scheduled(fixedDelayString = "${rag2okf.task.recovery-interval-ms:30000}", initialDelayString = "30000")
    public void scanAndRecover() {
        Date now = new Date();
        List<ProcessingTask> staleTasks = taskApplicationService.scanStaleTasks(now, scanBatchSize);
        if (staleTasks.isEmpty()) {
            return;
        }

        log.warn("Found {} stale RUNNING tasks", staleTasks.size());
        for (ProcessingTask stale : staleTasks) {
            String taskKey = stale.taskKey();
            try {
                taskExecutor.recoverStale(taskKey);
            } catch (DistributeLockException e) {
                log.debug("Recovery lock held by another instance, skipping: taskKey={}", taskKey);
            } catch (Exception e) {
                log.error("Task recovery failed: taskKey={}", taskKey, e);
            }
        }
    }
}
