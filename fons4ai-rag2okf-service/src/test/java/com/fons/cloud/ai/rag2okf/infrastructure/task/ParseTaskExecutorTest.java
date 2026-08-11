package com.fons.cloud.ai.rag2okf.infrastructure.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fons.cloud.ai.rag2okf.application.parsing.ParseApplicationService;
import com.fons.cloud.ai.rag2okf.application.task.TaskApplicationService;
import com.fons.cloud.ai.rag2okf.common.dto.DocumentArtifactStore.ArtifactScope;
import com.fons.cloud.ai.rag2okf.common.dto.DocumentChunkerPort;
import com.fons.cloud.ai.rag2okf.common.dto.DocumentParserPort;
import com.fons.cloud.ai.rag2okf.common.dto.ModelBusinessKeyGenerator;
import com.fons.cloud.ai.rag2okf.common.dto.ParseTaskPayload;
import com.fons.cloud.ai.rag2okf.common.dto.ParsingChunkProfile;
import com.fons.cloud.ai.rag2okf.common.dto.ProcessingTask;
import com.fons.cloud.ai.rag2okf.common.dto.TaskExecutionResult;
import com.fons.cloud.ai.rag2okf.common.dto.TaskStatus;
import com.fons.cloud.ai.rag2okf.common.dto.TaskType;
import com.fons.cloud.ai.rag2okf.common.exeception.DocumentArtifactException;
import com.fons.cloud.ai.rag2okf.domain.entity.KbProcessingTaskEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbSourceDocumentEntity;
import com.fons.cloud.ai.rag2okf.domain.service.KbChunkRevisionDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.KbParseRevisionDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.KbSourceDocumentDomainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * ParseTaskExecutor 测试，覆盖 T002 的三个场景：
 * <ol>
 *   <li>RUNNING 写入：markParseRunning 将文档 parseStatus 设为 RUNNING</li>
 *   <li>FAILED 写入：onTerminalFailure 将文档 parseStatus 设为 FAILED</li>
 *   <li>异常不中断：markParseRunning 抛异常时解析流程继续执行</li>
 * </ol>
 *
 * @author hongqy
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("解析任务执行器")
class ParseTaskExecutorTest {

    @Mock private DocumentParserPort documentParserPort;
    @Mock private DocumentChunkerPort documentChunkerPort;
    @Mock private KbParseRevisionDomainService parseRevisionDomainService;
    @Mock private KbChunkRevisionDomainService chunkRevisionDomainService;
    @Mock private KbSourceDocumentDomainService sourceDocumentDomainService;
    @Mock private TaskApplicationService taskApplicationService;
    @Mock private ModelBusinessKeyGenerator keyGenerator;
    @Mock private ObjectMapper objectMapper;

    private ParseTaskExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new ParseTaskExecutor(
                documentParserPort, documentChunkerPort,
                parseRevisionDomainService, chunkRevisionDomainService,
                sourceDocumentDomainService, taskApplicationService,
                keyGenerator, objectMapper);
        // 注入 self 代理（@Lazy @Autowired 字段），用 executor 自身作为 spy
        ReflectionTestUtils.setField(executor, "self", executor);
    }

    // ────────────────────── RUNNING 写入 ──────────────────────

    @Test
    @DisplayName("markParseRunning 将文档 parseStatus 设为 RUNNING")
    void markParseRunning_setsRunningStatus() {
        Long sourceDocumentId = 42L;
        KbSourceDocumentEntity document = new KbSourceDocumentEntity();
        document.setId(sourceDocumentId);
        document.setParseStatus("QUEUED");

        when(sourceDocumentDomainService.getById(sourceDocumentId)).thenReturn(document);
        when(sourceDocumentDomainService.updateById(document)).thenReturn(true);

        executor.markParseRunning(sourceDocumentId);

        assertEquals(ParseApplicationService.PARSE_STATUS_RUNNING, document.getParseStatus());
        verify(sourceDocumentDomainService).updateById(document);
    }

    @Test
    @DisplayName("markParseRunning 文档不存在时安全返回，不抛异常")
    void markParseRunning_documentNotFound_safeReturn() {
        when(sourceDocumentDomainService.getById(99L)).thenReturn(null);

        assertDoesNotThrow(() -> executor.markParseRunning(99L));
        verify(sourceDocumentDomainService, never()).updateById(any());
    }

    // ────────────────────── FAILED 写入 ──────────────────────

    @Test
    @DisplayName("onTerminalFailure 将文档 parseStatus 设为 FAILED")
    void onTerminalFailure_setsFailedStatus() {
        Long sourceDocumentId = 42L;
        KbProcessingTaskEntity taskEntity = new KbProcessingTaskEntity();
        taskEntity.setId(1L);
        taskEntity.setTaskKey("01J_TASK");
        taskEntity.setSourceDocumentId(sourceDocumentId);
        taskEntity.setStatus(TaskStatus.RUNNING.name());
        taskEntity.setTaskType(TaskType.PARSE.name());
        ProcessingTask task = new ProcessingTask(taskEntity);

        KbSourceDocumentEntity document = new KbSourceDocumentEntity();
        document.setId(sourceDocumentId);
        document.setParseStatus(ParseApplicationService.PARSE_STATUS_RUNNING);

        when(sourceDocumentDomainService.getById(sourceDocumentId)).thenReturn(document);
        when(sourceDocumentDomainService.updateById(document)).thenReturn(true);

        executor.onTerminalFailure(task, "PARSE_ARTIFACT_ERROR", "解析产物错误");

        assertEquals(ParseApplicationService.PARSE_STATUS_FAILED, document.getParseStatus());
        verify(sourceDocumentDomainService).updateById(document);
    }

    @Test
    @DisplayName("onTerminalFailure sourceDocumentId 为 null 时安全返回")
    void onTerminalFailure_nullSourceDocumentId_safeReturn() {
        KbProcessingTaskEntity taskEntity = new KbProcessingTaskEntity();
        taskEntity.setSourceDocumentId(null);
        ProcessingTask task = new ProcessingTask(taskEntity);

        assertDoesNotThrow(() -> executor.onTerminalFailure(task, "ERR", "msg"));
        verify(sourceDocumentDomainService, never()).getById(any());
    }

    // ────────────────────── 异常不中断 ──────────────────────

    @Test
    @DisplayName("markParseRunning 抛异常时解析流程继续执行，不中断")
    void execute_markParseRunningThrows_continuesExecution() throws Exception {
        Long sourceDocumentId = 42L;
        ParseTaskPayload payload = new ParseTaskPayload(
                "ws-1", "kb-1", "doc-1", sourceDocumentId,
                "file-token-1", "test.md", "text/markdown",
                "NATIVE_TIKA", new ParsingChunkProfile("recursive", 512, 64), false);

        KbProcessingTaskEntity taskEntity = new KbProcessingTaskEntity();
        taskEntity.setId(1L);
        taskEntity.setTaskKey("01J_TASK");
        taskEntity.setSourceDocumentId(sourceDocumentId);
        taskEntity.setStatus(TaskStatus.RUNNING.name());
        taskEntity.setTaskType(TaskType.PARSE.name());
        taskEntity.setAttempt(1);
        taskEntity.setMaxAttempts(3);
        taskEntity.setPayloadJson("{\"documentKey\":\"doc-1\"}");
        ProcessingTask task = new ProcessingTask(taskEntity);

        when(objectMapper.readValue(anyString(), eq(ParseTaskPayload.class))).thenReturn(payload);
        // markParseRunning 抛异常（sourceDocumentDomainService.getById 抛异常）
        when(sourceDocumentDomainService.getById(sourceDocumentId))
                .thenThrow(new RuntimeException("DB connection lost"));
        // 解析器抛 DocumentArtifactException，execute 应返回 RetryableFailure
        when(documentParserPort.parse(any())).thenThrow(
                new DocumentArtifactException("parse failed"));

        // 使用不拦截 markParseRunning 的 self（真实对象，markParseRunning 直接执行抛异常被 catch）
        TaskExecutionResult result = executor.execute(task);

        // 验证：execute 返回 RetryableFailure 而非抛异常，证明 markParseRunning 异常被捕获
        assertInstanceOf(TaskExecutionResult.RetryableFailure.class, result);
        // 验证：documentParserPort.parse 被调用，证明解析流程继续执行
        verify(documentParserPort).parse(any());
    }
}
