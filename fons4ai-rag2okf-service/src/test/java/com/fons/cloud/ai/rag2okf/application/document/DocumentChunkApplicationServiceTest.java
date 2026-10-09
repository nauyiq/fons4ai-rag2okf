package com.fons.cloud.ai.rag2okf.application.document;

import com.alibaba.fastjson2.JSON;
import com.fons.cloud.ai.rag2okf.common.constants.Rag2OkfResultCode;
import com.fons.cloud.ai.rag2okf.common.constants.document.ChunkBoundaryType;
import com.fons.cloud.ai.rag2okf.common.constants.document.ChunkHierarchyType;
import com.fons.cloud.ai.rag2okf.common.constants.document.DocumentStatus;
import com.fons.cloud.ai.rag2okf.common.constants.document.ProcessingTaskStatus;
import com.fons.cloud.ai.rag2okf.common.constants.document.ProcessingTaskType;
import com.fons.cloud.ai.rag2okf.common.constants.document.ResultStage;
import com.fons.cloud.ai.rag2okf.common.exception.document.DocumentProcessingException;
import com.fons.cloud.ai.rag2okf.common.model.document.ChunkManifest;
import com.fons.cloud.ai.rag2okf.common.model.document.ChunkPolicy;
import com.fons.cloud.ai.rag2okf.common.model.document.ChunkTaskSnapshot;
import com.fons.cloud.ai.rag2okf.common.model.document.ParsedDocument;
import com.fons.cloud.ai.rag2okf.domain.entity.document.KbDocument;
import com.fons.cloud.ai.rag2okf.domain.entity.document.KbDocumentResult;
import com.fons.cloud.ai.rag2okf.domain.entity.document.KbProcessingTask;
import com.fons.cloud.ai.rag2okf.domain.service.document.KbDocumentDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.document.KbDocumentResultDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.document.KbProcessingTaskDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.knowledgebase.KbKnowledgeBaseDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.knowledgebase.KbModelBindingDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.user.KbModelProfileDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.user.KbWorkspaceDomainService;
import com.fons.cloud.ai.rag2okf.infrastructure.adapter.document.FonsOssDocumentArtifactService;
import com.fons.cloud.ai.rag2okf.infrastructure.adapter.user.SaTokenCurrentUserContext;
import com.fons.cloud.ai.rag2okf.infrastructure.document.chunking.ChunkManifestCodec;
import com.fons.cloud.ai.rag2okf.infrastructure.document.chunking.DocumentChunkProcessor;
import com.fons.cloud.ai.rag2okf.infrastructure.document.parser.ParsedDocumentCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.util.Date;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CHUNK 与 RECHUNK 的制品隔离、失败补偿和结果 CAS 契约测试。
 *
 * @author hongqy
 */
@ExtendWith(MockitoExtension.class)
class DocumentChunkApplicationServiceTest {

    private static final Long DOCUMENT_ID = 11L;
    private static final Long KNOWLEDGE_BASE_ID = 21L;
    private static final Long WORKSPACE_ID = 31L;
    private static final int RESULT_VERSION = 4;
    private static final String TASK_KEY = "chunk-task";
    private static final String RESULT_KEY = "result-key";
    private static final String PARSED_OBJECT_KEY = "parsed-document";

    @Mock private KbProcessingTaskDomainService taskDomainService;
    @Mock private KbDocumentDomainService documentDomainService;
    @Mock private KbDocumentResultDomainService resultDomainService;
    @Mock private KbKnowledgeBaseDomainService knowledgeBaseDomainService;
    @Mock private KbWorkspaceDomainService workspaceDomainService;
    @Mock private KbModelBindingDomainService modelBindingDomainService;
    @Mock private KbModelProfileDomainService modelProfileDomainService;
    @Mock private FonsOssDocumentArtifactService artifactService;
    @Mock private ParsedDocumentCodec parsedDocumentCodec;
    @Mock private ChunkManifestCodec chunkManifestCodec;
    @Mock private DocumentChunkProcessor chunkProcessor;
    @Mock private SaTokenCurrentUserContext currentUserContext;
    @Mock private TransactionTemplate transactionTemplate;

    private DocumentChunkApplicationService service;
    private KbDocument document;
    private KbDocumentResult result;
    private KbProcessingTask task;

    @BeforeEach
    void setUp() {
        service = new DocumentChunkApplicationService(
                taskDomainService, documentDomainService, resultDomainService,
                knowledgeBaseDomainService, workspaceDomainService, modelBindingDomainService,
                modelProfileDomainService, artifactService, parsedDocumentCodec, chunkManifestCodec,
                chunkProcessor, currentUserContext, transactionTemplate);
        document = new KbDocument();
        document.setId(DOCUMENT_ID);
        document.setDocumentKey("document-key");
        document.setKnowledgeBaseId(KNOWLEDGE_BASE_ID);
        document.setStatus(DocumentStatus.UPLOADED);
        document.setDeleted(false);
        result = KbDocumentResult.create(DOCUMENT_ID, 7L,
                new KbDocumentResult.SourceFilePointer(
                        "source-token", "source-object", "report.pdf", "application/pdf", 1L, "sha256"));
        result.setResultKey(RESULT_KEY);
        result.setStage(ResultStage.PARSE);
        result.setVersion(RESULT_VERSION);
        result.setParsedDocumentObjectKey(PARSED_OBJECT_KEY);
        task = KbProcessingTask.create(
                WORKSPACE_ID, KNOWLEDGE_BASE_ID, DOCUMENT_ID, ProcessingTaskType.CHUNK, RESULT_KEY,
                snapshotJson(), null);
        task.setId(51L);
        task.setTaskKey(TASK_KEY);
        task.setStatus(ProcessingTaskStatus.QUEUED);
        when(taskDomainService.findByTaskKey(TASK_KEY)).thenReturn(task);
        when(documentDomainService.getById(DOCUMENT_ID)).thenReturn(document);
        when(resultDomainService.findByResultKey(RESULT_KEY)).thenReturn(result);
        when(resultDomainService.findCurrentByDocumentId(DOCUMENT_ID)).thenReturn(result);
    }

    @Test
    void shouldConsumeParsedDocumentAndCommitChunkManifest() {
        executeTransactionsInline();
        ParsedDocument parsedDocument = new ParsedDocument();
        ChunkManifest manifest = new ChunkManifest();
        manifest.setParentCount(1);
        manifest.setChildCount(2);
        manifest.setTotalCount(3);
        manifest.setContentHash("manifest-hash");
        when(artifactService.openParsed(PARSED_OBJECT_KEY)).thenReturn(
                new FonsOssDocumentArtifactService.SourceArtifactContent(
                        PARSED_OBJECT_KEY, new ByteArrayInputStream(new byte[]{1})));
        when(parsedDocumentCodec.readJson(any())).thenReturn(parsedDocument);
        when(chunkProcessor.process(any(), any(), any(), any())).thenReturn(manifest);
        when(chunkManifestCodec.writeJson(manifest)).thenReturn(new byte[]{2});
        when(artifactService.storeChunk(any())).thenReturn(
                new FonsOssDocumentArtifactService.StoredChunkArtifact("chunk-object"));

        service.execute(TASK_KEY);

        verify(resultDomainService).commitChunkManifest(
                RESULT_KEY, DOCUMENT_ID, RESULT_VERSION, ResultStage.PARSE,
                "chunk-object", 1, 2, 3, "manifest-hash");
        verify(documentDomainService).casTransitionStatus(
                DOCUMENT_ID, DocumentStatus.UPLOADED, DocumentStatus.PARSED);
        verify(artifactService, never()).deleteParsed(any());
    }

    @Test
    void shouldCompensateOnlyNewChunkArtifactWhenCasLoses() {
        executeTransactionsInline();
        ParsedDocument parsedDocument = new ParsedDocument();
        ChunkManifest manifest = new ChunkManifest();
        when(artifactService.openParsed(PARSED_OBJECT_KEY)).thenReturn(
                new FonsOssDocumentArtifactService.SourceArtifactContent(
                        PARSED_OBJECT_KEY, new ByteArrayInputStream(new byte[]{1})));
        when(parsedDocumentCodec.readJson(any())).thenReturn(parsedDocument);
        when(chunkProcessor.process(any(), any(), any(), any())).thenReturn(manifest);
        when(chunkManifestCodec.writeJson(manifest)).thenReturn(new byte[]{2});
        FonsOssDocumentArtifactService.StoredChunkArtifact stored =
                new FonsOssDocumentArtifactService.StoredChunkArtifact("orphan-chunk-object");
        when(artifactService.storeChunk(any())).thenReturn(stored);
        doThrow(new DocumentProcessingException(Rag2OkfResultCode.TASK_INPUT_SUPERSEDED))
                .when(resultDomainService).commitChunkManifest(
                        any(), any(), any(), any(), any(), anyInt(), anyInt(), anyInt(), any());

        DocumentProcessingException exception = assertThrows(
                DocumentProcessingException.class, () -> service.execute(TASK_KEY));

        assertEquals(Rag2OkfResultCode.TASK_INPUT_SUPERSEDED.getCode(), exception.getCode());
        verify(artifactService).deleteChunk(stored);
        verify(artifactService, never()).deleteParsed(any());
        verify(resultDomainService, never()).commitParsedArtifacts(
                any(), any(), any(), any(), any(), anyInt(), anyInt());
    }

    /** 首次 CHUNK 失败时保留 ParsedDocument，但文档状态必须对用户可见地标记为失败。 */
    @Test
    void shouldMarkUploadedDocumentFailedWhenInitialChunkFails() {
        when(artifactService.openParsed(PARSED_OBJECT_KEY)).thenReturn(
                new FonsOssDocumentArtifactService.SourceArtifactContent(
                        PARSED_OBJECT_KEY, new ByteArrayInputStream(new byte[]{1})));
        when(parsedDocumentCodec.readJson(any())).thenReturn(new ParsedDocument());
        DocumentProcessingException failure =
                new DocumentProcessingException(Rag2OkfResultCode.CHUNK_POLICY_INVALID);
        when(chunkProcessor.process(any(), any(), any(), any())).thenThrow(failure);

        assertThrows(DocumentProcessingException.class, () -> service.execute(TASK_KEY));

        verify(taskDomainService).failRunningTask(
                task.getId(), Rag2OkfResultCode.CHUNK_POLICY_INVALID.getCode(), failure.getMessage());
        verify(documentDomainService).casTransitionStatus(
                DOCUMENT_ID, DocumentStatus.UPLOADED, DocumentStatus.FAILED);
        verify(artifactService, never()).deleteParsed(any());
    }

    @Test
    void shouldRecordGenericTaskErrorWhenInitialChunkFailsUnexpectedly() {
        when(artifactService.openParsed(PARSED_OBJECT_KEY)).thenThrow(new IllegalStateException("storage unavailable"));

        DocumentProcessingException exception = assertThrows(
                DocumentProcessingException.class, () -> service.execute(TASK_KEY));

        assertEquals(Rag2OkfResultCode.TASK_EXECUTION_ERROR.getCode(), exception.getCode());
        verify(taskDomainService).failRunningTask(
                task.getId(), Rag2OkfResultCode.TASK_EXECUTION_ERROR.getCode(),
                Rag2OkfResultCode.TASK_EXECUTION_ERROR.getMessage());
    }

    private String snapshotJson() {
        return JSON.toJSONString(new ChunkTaskSnapshot(
                "workspace-key", "knowledge-base-key", "document-key", RESULT_KEY, RESULT_VERSION,
                new ChunkPolicy(ChunkBoundaryType.RECURSIVE, ChunkHierarchyType.PARENT_CHILD, Map.of()),
                Map.of(), 7L, new Date()));
    }

    private void executeTransactionsInline() {
        doAnswer(invocation -> {
            Consumer<?> callback = invocation.getArgument(0);
            callback.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }
}
