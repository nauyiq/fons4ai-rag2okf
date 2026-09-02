package com.fons.cloud.ai.rag2okf.task;

import com.fons.cloud.ai.rag2okf.application.document.DocumentParseApplicationService;
import com.fons.cloud.ai.rag2okf.domain.entity.document.KbProcessingTask;
import com.fons.cloud.ai.rag2okf.domain.service.document.KbProcessingTaskDomainService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PARSE 调度器仅消费新三表候选任务并隔离单项失败。
 *
 * @author hongqy
 */
class DocumentParseTaskExecutorTest {

    @Test
    void shouldContinueAfterOneCandidateFails() {
        KbProcessingTaskDomainService taskService = mock(KbProcessingTaskDomainService.class);
        DocumentParseApplicationService applicationService = mock(DocumentParseApplicationService.class);
        KbProcessingTask first = new KbProcessingTask();
        first.setTaskKey("first");
        KbProcessingTask second = new KbProcessingTask();
        second.setTaskKey("second");
        when(taskService.listQueuedParseTasks(20)).thenReturn(List.of(first, second));
        doThrow(new IllegalStateException("safe-test-failure"))
                .when(applicationService).execute("first");

        new DocumentParseTaskExecutor(taskService, applicationService).executeQueued();

        verify(applicationService).execute("first");
        verify(applicationService).execute("second");
    }
}
