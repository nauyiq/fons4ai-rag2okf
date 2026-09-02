package com.fons.cloud.ai.rag2okf.task;

import com.fons.cloud.ai.rag2okf.application.document.DocumentChunkApplicationService;
import com.fons.cloud.ai.rag2okf.domain.entity.document.KbProcessingTask;
import com.fons.cloud.ai.rag2okf.domain.service.document.KbProcessingTaskDomainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 首次 CHUNK 候选任务调度器。
 *
 * <p>执行器只消费独立的 {@code CHUNK} 任务；QUEUED→RUNNING 的 CAS 由应用层
 * 完成，多个实例并发扫描时的输家不会读取解析制品或覆盖结果。</p>
 *
 * @author hongqy
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentChunkTaskExecutor {

    private static final int TASK_BATCH_SIZE = 20;

    /** 任务查询服务。 */
    private final KbProcessingTaskDomainService taskDomainService;
    /** CHUNK 应用编排服务。 */
    private final DocumentChunkApplicationService applicationService;

    /** 定期尝试执行候选 CHUNK 任务，并隔离单项执行失败。 */
    @Scheduled(
            fixedDelayString = "${rag2okf.document.chunk.scan-interval-ms:120000}",
            initialDelayString = "${rag2okf.document.chunk.initial-delay-ms:22000}")
    public void executeQueued() {
        for (KbProcessingTask task : taskDomainService.listQueuedChunkTasks(TASK_BATCH_SIZE)) {
            try {
                applicationService.execute(task.getTaskKey());
            } catch (RuntimeException exception) {
                log.warn("CHUNK 任务执行结束: taskKey={}, result={}",
                        task.getTaskKey(), exception.getClass().getSimpleName());
            }
        }
    }
}
