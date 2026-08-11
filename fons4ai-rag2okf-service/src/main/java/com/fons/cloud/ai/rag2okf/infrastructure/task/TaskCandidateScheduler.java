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
     * <p>固定延迟 2 分钟，初始延迟 10 秒。正常任务创建后已立即触发，兜底扫描只需补偿漏网情况。
     *
     * <p>扫描顺序：
     * <ol>
     *   <li>先将退避到期的 RETRY_WAIT 任务转回 QUEUED，使其可被本轮扫描执行</li>
     *   <li>再扫描所有 QUEUED 任务并执行</li>
     * </ol>
     */
    @Scheduled(fixedDelayString = "${rag2okf.task.scan-interval-ms:120000}", initialDelayString = "10000")
    public void scanAndExecute() {
        Date now = new Date();

        // 1. 恢复退避到期的 RETRY_WAIT 任务到 QUEUED
        List<ProcessingTask> retryWaitTasks = taskApplicationService.scanRetryWaitTasks(now, scanBatchSize);
        if (!retryWaitTasks.isEmpty()) {
            log.info("Found {} RETRY_WAIT tasks due for requeue", retryWaitTasks.size());
            for (ProcessingTask task : retryWaitTasks) {
                String taskKey = task.taskKey();
                try {
                    boolean requeued = taskApplicationService.requeueFromRetryWait(taskKey);
                    if (requeued) {
                        log.info("Requeued from RETRY_WAIT: taskKey={}", taskKey);
                    }
                } catch (Exception e) {
                    log.warn("Failed to requeue from RETRY_WAIT: taskKey={}", taskKey, e);
                }
            }
        }

        // 2. 扫描 QUEUED 任务并执行（包括刚从 RETRY_WAIT 转回的）
        List<ProcessingTask> candidates = taskApplicationService.scanCandidates(now, scanBatchSize);
        if (candidates.isEmpty()) {
            return;
        }

        log.info("Found {} candidate tasks", candidates.size());
        for (ProcessingTask candidate : candidates) {
            String taskKey = candidate.taskKey();
            try {
                taskExecutor.executeLocked(taskKey);
            } catch (DistributeLockException e) {
                // 锁被其他实例持有，跳过本轮候选
                log.info("Task lock held by another instance, skipping: taskKey={}", taskKey);
            } catch (Exception e) {
                // 单个任务执行异常不阻塞后续任务
                log.error("Task execution failed: taskKey={}", taskKey, e);
            }
        }
    }

    /**
     * 定时扫描 stale RUNNING 任务并尝试恢复。
     *
     * <p>固定延迟 2 分钟，初始延迟 30 秒。
     */
    @Scheduled(fixedDelayString = "${rag2okf.task.recovery-interval-ms:120000}", initialDelayString = "30000")
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
