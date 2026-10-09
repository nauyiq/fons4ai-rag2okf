package com.fons.cloud.ai.rag2okf.application.document;

import com.fons.cloud.ai.rag2okf.common.constants.document.CleanupStatus;
import com.fons.cloud.ai.rag2okf.common.constants.document.DocumentStatus;
import com.fons.cloud.ai.rag2okf.common.constants.document.ParserType;
import com.fons.cloud.ai.rag2okf.common.constants.document.ProcessingMode;
import com.fons.cloud.ai.rag2okf.common.constants.document.ProcessingTaskStatus;
import com.fons.cloud.ai.rag2okf.common.constants.document.ProcessingTaskType;
import com.fons.cloud.ai.rag2okf.common.constants.document.ResultStage;
import com.fons.cloud.ai.rag2okf.common.constants.user.WorkspaceRole;
import com.fons.cloud.ai.rag2okf.common.constants.Rag2OkfResultCode;
import com.fons.cloud.ai.rag2okf.common.model.document.DocumentPrecheckedFile;
import com.fons.cloud.ai.rag2okf.common.model.document.ParseIntent;
import com.fons.cloud.ai.rag2okf.common.request.document.DocumentUploadRequest;
import com.fons.cloud.common.result.PageResult;
import com.fons.cloud.ai.rag2okf.common.response.document.DocumentDetailResponse;
import com.fons.cloud.ai.rag2okf.common.response.document.DocumentSummaryResponse;
import com.fons.cloud.ai.rag2okf.domain.entity.document.KbDocument;
import com.fons.cloud.ai.rag2okf.domain.entity.document.KbDocumentResult;
import com.fons.cloud.ai.rag2okf.domain.entity.document.KbProcessingTask;
import com.fons.cloud.ai.rag2okf.domain.entity.knowledgebase.KbKnowledgeBase;
import com.fons.cloud.ai.rag2okf.domain.entity.user.KbUser;
import com.fons.cloud.ai.rag2okf.domain.entity.user.UserWorkspaceAggregate;
import com.fons.cloud.ai.rag2okf.domain.service.document.KbDocumentDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.document.KbDocumentResultDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.document.KbProcessingTaskDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.knowledgebase.KbKnowledgeBaseDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.knowledgebase.KbModelBindingDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.user.KbModelProfileDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.user.KbWorkspaceDomainService;
import com.fons.cloud.ai.rag2okf.infrastructure.adapter.document.FonsOssDocumentArtifactService;
import com.fons.cloud.ai.rag2okf.common.model.document.DocumentFileContent;
import com.fons.cloud.ai.rag2okf.infrastructure.adapter.user.SaTokenCurrentUserContext;
import com.fons.cloud.ai.rag2okf.infrastructure.document.parser.DocumentParserRegistry;
import com.fons.cloud.ai.rag2okf.infrastructure.document.parser.ParseRecognizer;
import com.fons.cloud.common.result.R;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** T012 文档列表、详情与源文件下载的新三表读链路测试。 */
@ExtendWith(MockitoExtension.class)
class DocumentApplicationServiceReadTest {

    @Mock private SaTokenCurrentUserContext currentUserContext;
    @Mock private UserWorkspaceAggregate userWorkspaceAggregate;
    @Mock private KbKnowledgeBaseDomainService knowledgeBaseDomainService;
    @Mock private KbWorkspaceDomainService workspaceDomainService;
    @Mock private KbDocumentDomainService documentDomainService;
    @Mock private KbDocumentResultDomainService documentResultDomainService;
    @Mock private KbProcessingTaskDomainService processingTaskDomainService;
    @Mock private KbModelBindingDomainService modelBindingDomainService;
    @Mock private KbModelProfileDomainService modelProfileDomainService;
    @Mock private FonsOssDocumentArtifactService documentArtifactService;
    @Mock private DocumentUploadPrecheckPolicy uploadPrecheckPolicy;
    @Mock private DocumentParserRegistry parserRegistry;
    @Mock private ParseRecognizer parseRecognizer;
    @Mock private TransactionTemplate transactionTemplate;

    private DocumentApplicationService applicationService;

    @BeforeEach
    void setUp() {
        applicationService = new DocumentApplicationService(
                currentUserContext, knowledgeBaseDomainService,
                workspaceDomainService, documentDomainService, documentResultDomainService,
                processingTaskDomainService, modelBindingDomainService, modelProfileDomainService,
                documentArtifactService, uploadPrecheckPolicy, parserRegistry, parseRecognizer,
                transactionTemplate);
        KbUser user = new KbUser();
        user.setId(7L);
        user.setUserKey("user-key");
        when(currentUserContext.requireCurrentUser()).thenReturn(user);
        when(workspaceDomainService.findUserWorkspaceAggregate(anyLong(), anyLong()))
                .thenReturn(userWorkspaceAggregate);
        when(userWorkspaceAggregate.requireAccess(any(WorkspaceRole.class))).thenReturn(WorkspaceRole.ADMIN);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldLoadPageResultsAndLatestTasksWithFixedBatchQueries() {
        KbKnowledgeBase knowledgeBase = knowledgeBase();
        KbDocument first = document(101L, "doc-1", "同名.pdf");
        KbDocument second = document(102L, "doc-2", "同名.pdf");
        KbDocumentResult firstResult = result(201L, 101L, "token-1", "internal/source-1");
        KbDocumentResult secondResult = result(202L, 102L, "token-2", "internal/source-2");
        KbProcessingTask latest = task(302L, 101L, "latest", ProcessingTaskType.PARSE);

        when(knowledgeBaseDomainService.findByKnowledgeBaseKey("kb-key")).thenReturn(knowledgeBase);
        when(documentDomainService.pageActiveByKnowledgeBaseId(anyLong(), anyInt(), anyInt()))
                .thenAnswer(invocation -> {
            int pageNumber = invocation.getArgument(1, Integer.class);
            int pageSize = invocation.getArgument(2, Integer.class);
            PageResult<KbDocument> page = new PageResult<>(
                    pageNumber, pageSize, 2L, List.of(first, second));
            page.setPages(1);
            return page;
        });
        when(documentResultDomainService.findCurrentByDocumentIds(anyList()))
                .thenReturn(Map.of(first.getId(), firstResult, second.getId(), secondResult));
        when(processingTaskDomainService.findLatestBySourceDocumentIds(anyList()))
                .thenReturn(Map.of(first.getId(), List.of(latest)));

        R<PageResult<DocumentSummaryResponse>> listResult =
                applicationService.listDocuments("kb-key", 1, 20);
        assertTrue(listResult.isSuccess());
        PageResult<DocumentSummaryResponse> response = listResult.getData();

        assertEquals(2, response.getTotal());
        assertEquals("doc-1", response.getResultList().getFirst().documentKey());
        assertEquals("token-1", response.getResultList().getFirst().currentFile().fileToken());
        assertEquals("latest", response.getResultList().getFirst().latestTasks().getFirst().taskKey());
        assertFalse(response.getResultList().getFirst().toString().contains("internal/source-1"));
        verify(documentResultDomainService).findCurrentByDocumentIds(anyList());
        verify(processingTaskDomainService).findLatestBySourceDocumentIds(anyList());
    }

    @Test
    void shouldReturnSafeDetailAndStreamCurrentSource() {
        KbKnowledgeBase knowledgeBase = knowledgeBase();
        KbDocument document = document(101L, "doc-1", "报告.pdf");
        KbDocumentResult result = result(201L, 101L, "token-1", "internal/source-1");
        when(documentDomainService.findByDocumentKey("doc-1")).thenReturn(document);
        when(knowledgeBaseDomainService.getById(knowledgeBase.getId())).thenReturn(knowledgeBase);
        when(documentResultDomainService.findCurrentByDocumentId(document.getId())).thenReturn(result);
        when(processingTaskDomainService.findLatestBySourceDocumentIds(anyList())).thenReturn(Map.of());
        when(documentArtifactService.openSource("internal/source-1")).thenReturn(
                new FonsOssDocumentArtifactService.SourceArtifactContent(
                        "internal/source-1", new ByteArrayInputStream(new byte[]{1, 2, 3})));

        R<DocumentDetailResponse> detailResult = applicationService.getDocumentDetail("doc-1");
        DocumentFileContent file = applicationService.downloadDocumentFile("doc-1");
        assertTrue(detailResult.isSuccess());
        DocumentDetailResponse detail = detailResult.getData();

        assertEquals("token-1", detail.currentFile().fileToken());
        assertEquals(ResultStage.INIT, detail.stage());
        assertEquals(List.of("DOWNLOAD"), detail.availableActions());
        assertNull(detail.parse().parserType());
        assertFalse(detail.toString().contains("internal/source-1"));
        assertEquals("报告.pdf", file.getFilename());
        assertEquals(3, file.getSize());
        verify(documentArtifactService).openSource("internal/source-1");
    }

    @Test
    void shouldRejectUploadParserRequestThatDiffersFromRecognizedIntent() throws Exception {
        KbKnowledgeBase knowledgeBase = knowledgeBase();
        MultipartFile file = mock(MultipartFile.class);
        DocumentPrecheckedFile precheckedFile = new DocumentPrecheckedFile(
                "report.pdf", "application/pdf", new ByteArrayInputStream(new byte[]{1, 2, 3}));
        when(knowledgeBaseDomainService.findByKnowledgeBaseKey("kb-key")).thenReturn(knowledgeBase);
        when(file.getOriginalFilename()).thenReturn("report.pdf");
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3}));
        when(file.getSize()).thenReturn(3L);
        when(uploadPrecheckPolicy.precheck(eq("report.pdf"), any(), eq(3L))).thenReturn(R.ok(precheckedFile));
        when(parseRecognizer.recognize(precheckedFile)).thenReturn(
                new ParseIntent("PDF", ParserType.BUILT_IN, List.of(), List.of(), List.of()));

        R<?> result = applicationService.uploadDocument("kb-key", file,
                new DocumentUploadRequest(ProcessingMode.PARSE, ParserType.MINERU, null));

        assertFalse(result.isSuccess());
        assertEquals(Rag2OkfResultCode.PARSER_NOT_AVAILABLE.getCode(), result.getCode());
        verify(documentArtifactService, never()).storeSource(any());
        verify(transactionTemplate, never()).execute(any());
    }

    private KbKnowledgeBase knowledgeBase() {
        KbKnowledgeBase knowledgeBase = new KbKnowledgeBase();
        knowledgeBase.setId(11L);
        knowledgeBase.setKnowledgeBaseKey("kb-key");
        knowledgeBase.setWorkspaceId(21L);
        return knowledgeBase;
    }

    private KbDocument document(Long id, String key, String name) {
        KbDocument document = new KbDocument();
        document.setId(id);
        document.setKnowledgeBaseId(11L);
        document.setDocumentKey(key);
        document.setDisplayName(name);
        document.setStatus(DocumentStatus.UPLOADED);
        document.setCleanupStatus(CleanupStatus.NOT_REQUIRED);
        document.setDeleted(false);
        return document;
    }

    private KbDocumentResult result(Long id, Long documentId, String token, String objectKey) {
        KbDocumentResult result = new KbDocumentResult();
        result.setId(id);
        result.setDocumentId(documentId);
        result.setSourceFileToken(token);
        result.setSourceObjectKey(objectKey);
        result.setSourceOriginalFilename("报告.pdf");
        result.setSourceContentType("application/pdf");
        result.setSourceSizeBytes(3L);
        result.setSourceSha256("abc");
        result.setStage(ResultStage.INIT);
        result.setDeleted(false);
        return result;
    }

    private KbProcessingTask task(Long id, Long documentId, String key, ProcessingTaskType type) {
        KbProcessingTask task = new KbProcessingTask();
        task.setId(id);
        task.setSourceDocumentId(documentId);
        task.setTaskKey(key);
        task.setTaskType(type);
        task.setStatus(ProcessingTaskStatus.QUEUED);
        task.setProgress(0);
        task.setDeleted(false);
        return task;
    }
}
