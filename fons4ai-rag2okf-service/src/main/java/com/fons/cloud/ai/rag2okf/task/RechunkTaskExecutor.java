package com.fons.cloud.ai.rag2okf.task;

import com.fons.cloud.ai.rag2okf.application.document.DocumentChunkApplicationService;
import com.fons.cloud.ai.rag2okf.domain.entity.document.KbProcessingTask;
import com.fons.cloud.ai.rag2okf.domain.service.document.KbProcessingTaskDomainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 新文档域 RECHUNK 候选任务调度器。
 *
 * <p>执行权由 QUEUED→RUNNING CAS 决定，多个实例扫描同一候选任务时只有获胜者进入分块编排。</p>
 *
 * @author hongqy
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RechunkTaskExecutor {

    /** 任务查询服务。 */
    private final KbProcessingTaskDomainService taskDomainService;
    /** RECHUNK 应用编排服务。 */
    private final DocumentChunkApplicationService applicationService;

    /** 定期尝试执行候选 RECHUNK 任务。 */
    @Scheduled(
            fixedDelayString = "${rag2okf.document.rechunk.scan-interval-ms:120000}",
            initialDelayString = "${rag2okf.document.rechunk.initial-delay-ms:25000}")
    public void executeQueued() {
        for (KbProcessingTask task : taskDomainService.listQueuedRechunkTasks(20)) {
            try {
                applicationService.execute(task.getTaskKey());
            } catch (RuntimeException exception) {
                log.warn("RECHUNK 任务执行结束: taskKey={}, result={}",
                        task.getTaskKey(), exception.getClass().getSimpleName());
            }
        }
    }
}
