package com.fons.cloud.ai.rag2okf.task;

import com.fons.cloud.ai.rag2okf.application.document.DocumentParseApplicationService;
import com.fons.cloud.ai.rag2okf.domain.entity.document.KbProcessingTask;
import com.fons.cloud.ai.rag2okf.domain.service.document.KbProcessingTaskDomainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 新文档域 PARSE 候选任务调度器。
 *
 * <p>多实例可能同时看到候选项，真正执行权由 QUEUED→RUNNING CAS 决定；
 * 并发输家不会调用解析器或覆盖结果。</p>
 *
 * @author hongqy
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentParseTaskExecutor {

    private final KbProcessingTaskDomainService taskDomainService;
    private final DocumentParseApplicationService applicationService;

    @Scheduled(
            fixedDelayString = "${rag2okf.document.parse.scan-interval-ms:120000}",
            initialDelayString = "${rag2okf.document.parse.initial-delay-ms:20000}")
    public void executeQueued() {
        for (KbProcessingTask task : taskDomainService.listQueuedParseTasks(20)) {
            try {
                applicationService.execute(task.getTaskKey());
            } catch (RuntimeException exception) {
                log.warn("PARSE 任务执行结束: taskKey={}, result={}",
                        task.getTaskKey(), exception.getClass().getSimpleName());
            }
        }
    }
}
