package com.fons.cloud.ai.rag2okf.infrastructure.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fons.cloud.ai.rag2okf.application.model.ModelBusinessKeyGenerator;
import com.fons.cloud.ai.rag2okf.application.publication.EmbeddingProjectionService;
import com.fons.cloud.ai.rag2okf.application.publication.PublicationTaskPayload;
import com.fons.cloud.ai.rag2okf.application.task.OutboxApplicationService;
import com.fons.cloud.ai.rag2okf.application.task.TaskApplicationService;
import com.fons.cloud.ai.rag2okf.common.exeception.DocumentArtifactException;
import com.fons.cloud.ai.rag2okf.domain.artifact.DocumentArtifactStore;
import com.fons.cloud.ai.rag2okf.domain.artifact.DocumentArtifactStore.ArtifactContent;
import com.fons.cloud.ai.rag2okf.domain.entity.KbPublicationRevisionEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbSourceDocumentEntity;
import com.fons.cloud.ai.rag2okf.domain.parsing.ChunkManifest;
import com.fons.cloud.ai.rag2okf.domain.parsing.ChunkProfile;
import com.fons.cloud.ai.rag2okf.domain.publication.PublicationProjectionPort;
import com.fons.cloud.ai.rag2okf.domain.publication.PublicationProjectionPort.ProjectionResult;
import com.fons.cloud.ai.rag2okf.domain.service.KbChunkRevisionDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.KbDocumentVersionDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.KbParseRevisionDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.KbPublicationRevisionDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.KbSourceDocumentDomainService;
import com.fons.cloud.ai.rag2okf.domain.task.ProcessingTask;
import com.fons.cloud.ai.rag2okf.domain.task.TaskExecutionResult;
import com.fons.cloud.ai.rag2okf.domain.entity.KbProcessingTaskEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * PublicationTaskExecutor 测试，覆盖 AC-017、AC-018、AC-021、AC-022。
 *
 * @author hongqy
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("发布任务执行器")
class PublicationTaskExecutorTest {

    @Mock private DocumentArtifactStore documentArtifactStore;
    @Mock private PublicationProjectionPort projectionPort;
    @Mock private KbSourceDocumentDomainService sourceDocumentDomainService;
    @Mock private KbParseRevisionDomainService parseRevisionDomainService;
    @Mock private KbChunkRevisionDomainService chunkRevisionDomainService;
    @Mock private KbDocumentVersionDomainService documentVersionDomainService;
    @Mock private KbPublicationRevisionDomainService publicationRevisionDomainService;
    @Mock private TaskApplicationService taskApplicationService;
    @Mock private OutboxApplicationService outboxApplicationService;
    @Mock private ModelBusinessKeyGenerator keyGenerator;
    @Mock private EmbeddingProjectionService embeddingProjectionService;

    private PublicationTaskExecutor executor;
    private PublicationTaskExecutor spyExecutor;
    private ObjectMapper objectMapper;

    private PublicationTaskPayload payload;
    private ChunkManifest chunkManifest;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper();
        executor = new PublicationTaskExecutor(
                documentArtifactStore, projectionPort,
                sourceDocumentDomainService, parseRevisionDomainService,
                chunkRevisionDomainService, documentVersionDomainService,
                publicationRevisionDomainService, taskApplicationService,
                outboxApplicationService, keyGenerator, objectMapper,
                embeddingProjectionService);

        // 默认行为：无 EMBEDDING 绑定，降级 BM25-only（直接返回原列表）
        lenient().when(embeddingProjectionService.embedProjections(any(), anyList()))
                .thenAnswer(invocation -> invocation.getArgument(1));

        // 使用 spy 模拟 @Lazy 自引用，使 persistPublicationResult 直接调用真实方法
        spyExecutor = spy(executor);
        // 通过反射注入 self
        java.lang.reflect.Field selfField = PublicationTaskExecutor.class.getDeclaredField("self");
        selfField.setAccessible(true);
        selfField.set(executor, spyExecutor);

        payload = new PublicationTaskPayload(
                "01J_WS", "01J_KB", "01J_DOC", 3L, 4L, "01J_VER",
                5L, "01J_PARSE", 6L, "01J_CHUNK", "MANUAL");

        chunkManifest = new ChunkManifest(
                "01J_CHUNK", "01J_PARSE", ChunkProfile.DEFAULT_RECURSIVE,
                1, 2,
                List.of(
                        new ChunkManifest.Chunk(0, "parent content", null, true, Map.of()),
                        new ChunkManifest.Chunk(1, "child content", "01J_CHUNK-0", false, Map.of("title", "第1章"))
                ),
                "sha256:abc");

        lenient().when(keyGenerator.nextKey()).thenReturn("01J_PUB");
    }

    @Test
    @DisplayName("成功发布：写入 ES + CAS 切换 + Outbox 清理（AC-017、AC-021）")
    void execute_success_persistsAndSwitchesPointer() throws Exception {
        ProcessingTask task = buildTask(payload);
        setupArtifactRead(chunkManifest);
        ProjectionResult projectionResult = new ProjectionResult("kb-chunk-v1", 2, "sha256:abc");
        when(projectionPort.projectChunks(any())).thenReturn(projectionResult);

        KbSourceDocumentEntity document = new KbSourceDocumentEntity();
        document.setId(3L);
        document.setActivePublicationRevisionId(7L);
        when(sourceDocumentDomainService.getById(3L)).thenReturn(document);
        when(sourceDocumentDomainService.updateById(any())).thenReturn(true);

        KbPublicationRevisionEntity oldPublication = new KbPublicationRevisionEntity();
        oldPublication.setPublicationRevisionKey("01J_OLD_PUB");
        when(publicationRevisionDomainService.getById(7L)).thenReturn(oldPublication);
        when(publicationRevisionDomainService.save(any())).thenAnswer(inv -> {
            ((KbPublicationRevisionEntity) inv.getArgument(0)).setId(8L);
            return true;
        });

        TaskExecutionResult result = executor.execute(task);

        assertThat(result).isInstanceOf(TaskExecutionResult.Succeeded.class);
        assertThat(((TaskExecutionResult.Succeeded) result).resultKey()).isEqualTo("01J_PUB");

        verify(projectionPort).projectChunks(any());
        verify(publicationRevisionDomainService).save(any());
        verify(sourceDocumentDomainService).updateById(any());
        // 验证 Outbox 清理事件已创建（旧发布存在）
        verify(outboxApplicationService).createEvent(
                eq("PUBLICATION_PROJECTION"), eq("01J_OLD_PUB"),
                eq("CLEANUP_OLD_PROJECTION"), eq("01J_OLD_PUB"));
    }

    @Test
    @DisplayName("ES 写入失败时保留旧发布（AC-018）")
    void execute_projectionFails_keepsOldPublication() throws Exception {
        ProcessingTask task = buildTask(payload);
        setupArtifactRead(chunkManifest);
        when(projectionPort.projectChunks(any()))
                .thenThrow(new PublicationProjectionPort.ProjectionException(
                        "PROJECTION_WRITE_ERROR", "bulk failure"));

        TaskExecutionResult result = executor.execute(task);

        assertThat(result).isInstanceOf(TaskExecutionResult.RetryableFailure.class);
        TaskExecutionResult.RetryableFailure retry = (TaskExecutionResult.RetryableFailure) result;
        assertThat(retry.errorCode()).isEqualTo("PUBLISH_PROJECTION_ERROR");

        // 不应切换指针或写 Outbox
        verify(publicationRevisionDomainService, never()).save(any());
        verify(sourceDocumentDomainService, never()).updateById(any());
    }

    @Test
    @DisplayName("空分块产物拒绝发布（AC-018 拒绝空产物）")
    void execute_emptyChunks_rejected() throws Exception {
        ProcessingTask task = buildTask(payload);
        ChunkManifest emptyManifest = new ChunkManifest(
                "01J_CHUNK", "01J_PARSE", ChunkProfile.DEFAULT_RECURSIVE,
                0, 0, List.of(), "sha256:empty");
        setupArtifactRead(emptyManifest);

        TaskExecutionResult result = executor.execute(task);

        assertThat(result).isInstanceOf(TaskExecutionResult.FatalFailure.class);
        TaskExecutionResult.FatalFailure fatal = (TaskExecutionResult.FatalFailure) result;
        assertThat(fatal.errorCode()).isEqualTo("PUBLISH_EMPTY_CHUNKS");
        verify(projectionPort, never()).projectChunks(any());
    }

    @Test
    @DisplayName("投影数量不匹配时返回可重试失败")
    void execute_projectionCountMismatch_returnsRetryableFailure() throws Exception {
        ProcessingTask task = buildTask(payload);
        setupArtifactRead(chunkManifest);
        // 期望 2 个，实际返回 1 个
        ProjectionResult mismatchResult = new ProjectionResult("kb-chunk-v1", 1, "sha256:abc");
        when(projectionPort.projectChunks(any())).thenReturn(mismatchResult);

        TaskExecutionResult result = executor.execute(task);

        assertThat(result).isInstanceOf(TaskExecutionResult.RetryableFailure.class);
        assertThat(((TaskExecutionResult.RetryableFailure) result).errorCode())
                .isEqualTo("PROJECTION_COUNT_MISMATCH");
    }

    @Test
    @DisplayName("CAS 切换失败时安排清理并返回可重试失败（AC-022 失败保旧）")
    void execute_casConflict_schedulesCleanupAndReturnsRetryable() throws Exception {
        ProcessingTask task = buildTask(payload);
        setupArtifactRead(chunkManifest);
        ProjectionResult projectionResult = new ProjectionResult("kb-chunk-v1", 2, "sha256:abc");
        when(projectionPort.projectChunks(any())).thenReturn(projectionResult);

        KbSourceDocumentEntity document = new KbSourceDocumentEntity();
        document.setId(3L);
        when(sourceDocumentDomainService.getById(3L)).thenReturn(document);
        when(sourceDocumentDomainService.updateById(any())).thenReturn(false); // CAS 失败

        TaskExecutionResult result = executor.execute(task);

        assertThat(result).isInstanceOf(TaskExecutionResult.RetryableFailure.class);
        assertThat(((TaskExecutionResult.RetryableFailure) result).errorCode())
                .isEqualTo("PUBLISH_CAS_CONFLICT");
        // 应安排 STAGED 投影清理
        verify(outboxApplicationService).createEvent(
                eq("PUBLICATION_PROJECTION"), eq("01J_PUB"),
                eq("CLEANUP_OLD_PROJECTION"), eq("01J_PUB"));
    }

    @Test
    @DisplayName("首次发布无旧发布时不写 Outbox 清理事件")
    void execute_firstPublish_noOutboxCleanup() throws Exception {
        ProcessingTask task = buildTask(payload);
        setupArtifactRead(chunkManifest);
        ProjectionResult projectionResult = new ProjectionResult("kb-chunk-v1", 2, "sha256:abc");
        when(projectionPort.projectChunks(any())).thenReturn(projectionResult);

        KbSourceDocumentEntity document = new KbSourceDocumentEntity();
        document.setId(3L);
        document.setActivePublicationRevisionId(null); // 首次发布
        when(sourceDocumentDomainService.getById(3L)).thenReturn(document);
        when(sourceDocumentDomainService.updateById(any())).thenReturn(true);
        when(publicationRevisionDomainService.save(any())).thenAnswer(inv -> {
            ((KbPublicationRevisionEntity) inv.getArgument(0)).setId(8L);
            return true;
        });

        TaskExecutionResult result = executor.execute(task);

        assertThat(result).isInstanceOf(TaskExecutionResult.Succeeded.class);
        // 首次发布无旧发布，不应写清理事件
        verify(outboxApplicationService, never()).createEvent(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("payload 反序列化失败返回 FatalFailure")
    void execute_invalidPayload_returnsFatalFailure() {
        KbProcessingTaskEntity entity = new KbProcessingTaskEntity();
        entity.setTaskKey("01J_TASK");
        entity.setPayloadJson("{invalid json}");
        ProcessingTask task = new ProcessingTask(entity);

        TaskExecutionResult result = executor.execute(task);

        assertThat(result).isInstanceOf(TaskExecutionResult.FatalFailure.class);
        assertThat(((TaskExecutionResult.FatalFailure) result).errorCode()).isEqualTo("PAYLOAD_INVALID");
    }

    @Test
    @DisplayName("向量化维度不匹配返回 FatalFailure，不切指针（CR-013，T045）")
    void execute_embeddingDimsMismatch_returnsFatalFailure() throws Exception {
        setupArtifactRead(chunkManifest);
        when(embeddingProjectionService.embedProjections(anyString(), anyList()))
                .thenThrow(new EmbeddingProjectionService.EmbeddingException(
                        EmbeddingProjectionService.ERR_DIMS_MISMATCH,
                        "向量维度不匹配: expected=1024, actual=768", true));

        TaskExecutionResult result = executor.execute(buildTask(payload));

        assertThat(result).isInstanceOf(TaskExecutionResult.FatalFailure.class);
        assertThat(((TaskExecutionResult.FatalFailure) result).errorCode())
                .isEqualTo(EmbeddingProjectionService.ERR_DIMS_MISMATCH);
        // 不切指针，不写 ES
        verify(projectionPort, never()).projectChunks(any());
        verify(publicationRevisionDomainService, never()).save(any());
    }

    @Test
    @DisplayName("向量化模型调用失败返回 RetryableFailure，不切指针（CR-013，T045）")
    void execute_embeddingModelError_returnsRetryableFailure() throws Exception {
        setupArtifactRead(chunkManifest);
        when(embeddingProjectionService.embedProjections(anyString(), anyList()))
                .thenThrow(new EmbeddingProjectionService.EmbeddingException(
                        EmbeddingProjectionService.ERR_MODEL_INVOCATION,
                        "向量化模型调用失败: RuntimeException", false));

        TaskExecutionResult result = executor.execute(buildTask(payload));

        assertThat(result).isInstanceOf(TaskExecutionResult.RetryableFailure.class);
        assertThat(((TaskExecutionResult.RetryableFailure) result).errorCode())
                .isEqualTo(EmbeddingProjectionService.ERR_MODEL_INVOCATION);
        // 不切指针，不写 ES
        verify(projectionPort, never()).projectChunks(any());
        verify(publicationRevisionDomainService, never()).save(any());
    }

    private ProcessingTask buildTask(PublicationTaskPayload payload) throws Exception {
        KbProcessingTaskEntity entity = new KbProcessingTaskEntity();
        entity.setTaskKey("01J_TASK");
        entity.setPayloadJson(objectMapper.writeValueAsString(payload));
        return new ProcessingTask(entity);
    }

    private void setupArtifactRead(ChunkManifest manifest) throws Exception {
        byte[] json = objectMapper.writeValueAsBytes(manifest);
        ArtifactContent content = mock(ArtifactContent.class);
        java.io.ByteArrayInputStream bis = new java.io.ByteArrayInputStream(json);
        when(content.inputStream()).thenReturn(bis);
        when(documentArtifactStore.open(any())).thenReturn(content);
    }
}
