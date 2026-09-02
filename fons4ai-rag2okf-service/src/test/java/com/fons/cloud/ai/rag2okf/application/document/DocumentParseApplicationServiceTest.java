package com.fons.cloud.ai.rag2okf.application.document;

import com.alibaba.fastjson2.JSON;
import com.fons.cloud.ai.rag2okf.common.constants.document.DocumentStatus;
import com.fons.cloud.ai.rag2okf.common.constants.document.ParserType;
import com.fons.cloud.ai.rag2okf.common.constants.document.ChunkBoundaryType;
import com.fons.cloud.ai.rag2okf.common.constants.document.ChunkHierarchyType;
import com.fons.cloud.ai.rag2okf.common.constants.document.ProcessingTaskStatus;
import com.fons.cloud.ai.rag2okf.common.constants.document.ProcessingTaskType;
import com.fons.cloud.ai.rag2okf.common.constants.document.ResultStage;
import com.fons.cloud.ai.rag2okf.common.constants.user.WorkspaceRole;
import com.fons.cloud.ai.rag2okf.common.constants.Rag2OkfResultCode;
import com.fons.cloud.ai.rag2okf.common.model.document.DocumentParseWorkflowResult;
import com.fons.cloud.ai.rag2okf.common.model.document.ChunkPolicy;
import com.fons.cloud.ai.rag2okf.common.model.document.ParseTaskSnapshot;
import com.fons.cloud.ai.rag2okf.common.model.document.ParsedDocument;
import com.fons.cloud.ai.rag2okf.common.request.document.DocumentParseRequest;
import com.fons.cloud.ai.rag2okf.common.request.document.ChunkPolicyRequest;
import com.fons.cloud.ai.rag2okf.common.response.document.DocumentParseResponse;
import com.fons.cloud.ai.rag2okf.domain.entity.document.KbDocument;
import com.fons.cloud.ai.rag2okf.domain.entity.document.KbDocumentResult;
import com.fons.cloud.ai.rag2okf.domain.entity.document.KbProcessingTask;
import com.fons.cloud.ai.rag2okf.domain.entity.knowledgebase.KbKnowledgeBase;
import com.fons.cloud.ai.rag2okf.domain.entity.user.KbUser;
import com.fons.cloud.ai.rag2okf.domain.entity.user.KbWorkspace;
import com.fons.cloud.ai.rag2okf.domain.entity.user.UserWorkspaceAggregate;
import com.fons.cloud.ai.rag2okf.domain.service.document.KbDocumentDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.document.KbDocumentResultDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.document.KbProcessingTaskDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.knowledgebase.KbKnowledgeBaseDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.knowledgebase.KbModelBindingDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.user.KbModelProfileDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.user.KbWorkspaceDomainService;
import com.fons.cloud.ai.rag2okf.infrastructure.adapter.document.FonsOssDocumentArtifactService;
import com.fons.cloud.ai.rag2okf.infrastructure.adapter.user.SaTokenCurrentUserContext;
import com.fons.cloud.ai.rag2okf.infrastructure.document.parser.DocumentParseWorkflow;
import com.fons.cloud.common.result.R;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** TP-009 手动解析独立结果版本与失败重发契约测试。 */
@ExtendWith(MockitoExtension.class)
class DocumentParseApplicationServiceTest {

    @Mock private KbProcessingTaskDomainService taskService;
    @Mock private KbDocumentDomainService documentService;
    @Mock private KbDocumentResultDomainService resultService;
    @Mock private FonsOssDocumentArtifactService artifactService;
    @Mock private DocumentParseWorkflow parseWorkflow;
    @Mock private DocumentChunkApplicationService chunkApplicationService;
    @Mock private SaTokenCurrentUserContext currentUserContext;
    @Mock private UserWorkspaceAggregate userWorkspaceAggregate;
    @Mock private KbKnowledgeBaseDomainService knowledgeBaseService;
    @Mock private KbWorkspaceDomainService workspaceService;
    @Mock private KbModelBindingDomainService bindingService;
    @Mock private KbModelProfileDomainService profileService;
    @Mock private TransactionTemplate transactionTemplate;

    private DocumentParseApplicationService service;
    private KbDocument document;
    private KbDocumentResult source;
    private KbWorkspace workspace;

    @BeforeEach
    void setUp() {
        service = new DocumentParseApplicationService(
                taskService, documentService, resultService, artifactService, parseWorkflow,
                chunkApplicationService,
                currentUserContext, knowledgeBaseService, workspaceService, bindingService, profileService,
                transactionTemplate);
        KbUser user = new KbUser();
        user.setId(7L);
        user.setUserKey("user-key");
        document = new KbDocument();
        document.setId(11L);
        document.setDocumentKey("doc-key");
        document.setKnowledgeBaseId(21L);
        document.setDeleted(false);
        KbKnowledgeBase knowledgeBase = new KbKnowledgeBase();
        knowledgeBase.setId(21L);
        knowledgeBase.setKnowledgeBaseKey("kb-key");
        knowledgeBase.setWorkspaceId(31L);
        workspace = new KbWorkspace();
        workspace.setId(31L);
        workspace.setWorkspaceKey("workspace-key");
        source = KbDocumentResult.create(11L, 7L,
                new KbDocumentResult.SourceFilePointer(
                        "file-token", "internal/source", "report.pdf",
                        "application/pdf", 12L, "sha256"));
        source.setId(41L);
        lenient().when(currentUserContext.requireCurrentUser()).thenReturn(user);
        lenient().when(documentService.findByDocumentKey("doc-key")).thenReturn(document);
        lenient().when(knowledgeBaseService.getById(21L)).thenReturn(knowledgeBase);
        lenient().when(workspaceService.findUserWorkspaceAggregate(anyLong(), anyLong()))
                .thenReturn(userWorkspaceAggregate);
        lenient().when(userWorkspaceAggregate.requireAccess(any())).thenReturn(WorkspaceRole.ADMIN);
        lenient().when(bindingService.listByKnowledgeBaseId(21L)).thenReturn(List.of());
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
    }

    @Test
    void shouldCreateIndependentResultAndTaskForEachRequestWithRetryTrace() {
        AtomicReference<KbDocumentResult> savedResult = new AtomicReference<>();
        KbProcessingTask failed = KbProcessingTask.create(
                31L, 21L, 11L, ProcessingTaskType.PARSE, source.getResultKey(),
                "{}", null);
        failed.setId(51L);
        failed.setStatus(ProcessingTaskStatus.FAILED);
        when(userWorkspaceAggregate.getWorkspace()).thenReturn(workspace);
        when(parseWorkflow.isEnabled(ParserType.BUILT_IN)).thenReturn(true);
        when(resultService.findCurrentByDocumentId(11L)).thenReturn(source);
        when(resultService.save(any())).thenAnswer(invocation -> {
            KbDocumentResult result = invocation.getArgument(0);
            result.setId(42L);
            savedResult.set(result);
            return true;
        });
        when(taskService.findLatestFailed(11L, ProcessingTaskType.PARSE)).thenReturn(failed);
        when(taskService.create(any())).thenAnswer(invocation -> invocation.getArgument(0));

        R<DocumentParseResponse> result = service.startParse(
                "doc-key", new DocumentParseRequest(ParserType.BUILT_IN,
                        new ChunkPolicyRequest(ChunkBoundaryType.RECURSIVE, ChunkHierarchyType.PARENT_CHILD, null), false));
        R<DocumentParseResponse> repeated = service.startParse(
                "doc-key", new DocumentParseRequest(ParserType.BUILT_IN,
                        new ChunkPolicyRequest(ChunkBoundaryType.RECURSIVE, ChunkHierarchyType.PARENT_CHILD, null), false));
        assertTrue(result.isSuccess());
        assertTrue(repeated.isSuccess());
        DocumentParseResponse response = result.getData();

        ArgumentCaptor<KbProcessingTask> taskCaptor = ArgumentCaptor.forClass(KbProcessingTask.class);
        verify(taskService, times(2)).create(taskCaptor.capture());
        KbProcessingTask createdTask = taskCaptor.getAllValues().get(0);
        KbProcessingTask repeatedTask = taskCaptor.getAllValues().get(1);
        assertNotEquals(source.getResultKey(), savedResult.get().getResultKey());
        assertNotEquals(createdTask.getTaskKey(), repeatedTask.getTaskKey());
        assertNotEquals(createdTask.getInputResultKey(), repeatedTask.getInputResultKey());
        assertEquals("file-token", savedResult.get().getSourceFileToken());
        assertEquals(savedResult.get().getResultKey(), repeatedTask.getInputResultKey());
        assertEquals(51L, createdTask.getRetryOfTaskId());
        assertEquals(ResultStage.INIT, response.resultStage());
        assertEquals(ProcessingTaskStatus.QUEUED, response.status());
        assertTrue(createdTask.getSnapshotJson().contains("workspace-key"));
        assertTrue(!createdTask.getSnapshotJson().contains("internal/source"));
    }

    @Test
    void shouldRejectMinerUBeforeTransactionOrPersistence() {
        // isEnabled 未被 stub 时 mock 默认返回 false，等价于 MINERU 未启用
        R<DocumentParseResponse> result = service.startParse(
                "doc-key", new DocumentParseRequest(ParserType.MINERU, null, false));

        assertFalse(result.isSuccess());
        assertEquals(Rag2OkfResultCode.PARSER_NOT_AVAILABLE.getCode(), result.getCode());
        verify(transactionTemplate, never()).execute(any());
        verify(resultService, never()).save(any());
        verify(taskService, never()).create(any());
    }

    @Test
    void shouldCommitOnlyParsedArtifactsAndCreateIndependentChunkTask() {
        KbProcessingTask task = KbProcessingTask.create(
                31L, 21L, 11L, ProcessingTaskType.PARSE, source.getResultKey(),
                JSON.toJSONString(ParseTaskSnapshot.of(
                        "workspace-key", "kb-key", "doc-key", "file-token", ParserType.BUILT_IN,
                        null, new ChunkPolicy(ChunkBoundaryType.RECURSIVE, ChunkHierarchyType.PARENT_CHILD, Map.of()),
                        Map.of(), false, 7L, new Date())), null);
        task.setId(51L);
        source.setVersion(4);
        document.setStatus(DocumentStatus.UPLOADED);
        ParsedDocument parsedDocument = mock(ParsedDocument.class);
        when(parsedDocument.blocks()).thenReturn(List.of());
        when(parsedDocument.warnings()).thenReturn(List.of());
        DocumentParseWorkflowResult workflowResult = mock(DocumentParseWorkflowResult.class);
        when(workflowResult.getParsedDocument()).thenReturn(parsedDocument);
        when(workflowResult.getParsedDocumentJson()).thenReturn(new byte[]{1});
        when(workflowResult.getParsedMarkdown()).thenReturn(new byte[]{2});
        when(taskService.findByTaskKey(task.getTaskKey())).thenReturn(task);
        when(documentService.getById(11L)).thenReturn(document);
        when(resultService.findByResultKey(source.getResultKey())).thenReturn(source);
        when(resultService.findCurrentByDocumentId(11L)).thenReturn(source);
        when(parseWorkflow.process(any())).thenReturn(workflowResult);
        when(artifactService.storeParsed(any())).thenReturn(
                new FonsOssDocumentArtifactService.StoredParsedArtifacts("parsed-json", "parsed-markdown"));
        doAnswer(invocation -> {
            Consumer<?> callback = invocation.getArgument(0);
            callback.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        service.execute(task.getTaskKey());

        verify(resultService).commitParsedArtifacts(
                source.getResultKey(), source.getDocumentId(), 4,
                "parsed-json", "parsed-markdown", 0, 0);
        verify(chunkApplicationService).createInitialChunkTask(any(), any(), any(), any());
        verify(artifactService, never()).storeChunk(any());
        verify(resultService, never()).commitChunkManifest(
                any(), any(), any(), any(), any(), anyInt(), anyInt(), anyInt(), any());
        assertEquals(ResultStage.PARSE, source.getStage());
        assertEquals(5, source.getVersion());
    }
}
