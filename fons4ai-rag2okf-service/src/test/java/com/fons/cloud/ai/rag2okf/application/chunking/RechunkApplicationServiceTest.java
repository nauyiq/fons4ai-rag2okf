package com.fons.cloud.ai.rag2okf.application.chunking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fons.cloud.ai.rag2okf.common.dto.CurrentUserContext;
import com.fons.cloud.ai.rag2okf.common.dto.ModelBusinessKeyGenerator;
import com.fons.cloud.ai.rag2okf.application.task.TaskApplicationService;
import com.fons.cloud.ai.rag2okf.common.exeception.KnowledgeBaseConflictException;
import com.fons.cloud.ai.rag2okf.common.exeception.TaskExecutionException;
import com.fons.cloud.ai.rag2okf.common.response.RechunkResponse;
import com.fons.cloud.ai.rag2okf.domain.entity.KbChunkRevisionEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbKnowledgeBaseEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbParseRevisionEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbProcessingTaskEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbSourceDocumentEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbUserEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbWorkspaceEntity;
import com.fons.cloud.ai.rag2okf.common.dto.ParsingChunkProfile;
import com.fons.cloud.ai.rag2okf.domain.service.KbChunkRevisionDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.KbKnowledgeBaseDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.KbParseRevisionDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.KbSourceDocumentDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.KbWorkspaceDomainService;
import com.fons.cloud.ai.rag2okf.common.dto.ProcessingTask;
import com.fons.cloud.ai.rag2okf.common.dto.TaskType;
import com.fons.cloud.ai.rag2okf.infrastructure.identity.WorkspaceAccessPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * RechunkApplicationService 测试，覆盖 AC-019、AC-020、AC-021。
 *
 * @author hongqy
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("重新分块应用服务")
class RechunkApplicationServiceTest {

    @Mock private CurrentUserContext currentUserContext;
    @Mock private WorkspaceAccessPolicy workspaceAccessPolicy;
    @Mock private KbSourceDocumentDomainService sourceDocumentDomainService;
    @Mock private KbKnowledgeBaseDomainService knowledgeBaseDomainService;
    @Mock private KbParseRevisionDomainService parseRevisionDomainService;
    @Mock private KbChunkRevisionDomainService chunkRevisionDomainService;
    @Mock private KbWorkspaceDomainService workspaceDomainService;
    @Mock private TaskApplicationService taskApplicationService;
    @Mock private ModelBusinessKeyGenerator keyGenerator;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks private RechunkApplicationService service;

    private KbUserEntity user;
    private KbWorkspaceEntity workspace;
    private KbKnowledgeBaseEntity knowledgeBase;
    private KbSourceDocumentEntity document;
    private KbParseRevisionEntity parseRevision;
    private KbChunkRevisionEntity currentChunkRevision;

    @BeforeEach
    void setUp() {
        user = new KbUserEntity();
        user.setUserKey("01J_USER");

        workspace = new KbWorkspaceEntity();
        workspace.setId(1L);
        workspace.setWorkspaceKey("01J_WS");

        knowledgeBase = new KbKnowledgeBaseEntity();
        knowledgeBase.setId(2L);
        knowledgeBase.setKnowledgeBaseKey("01J_KB");
        knowledgeBase.setWorkspaceId(1L);

        document = new KbSourceDocumentEntity();
        document.setId(3L);
        document.setDocumentKey("01J_DOC");
        document.setKnowledgeBaseId(2L);
        document.setCurrentParseRevisionId(5L);
        document.setCurrentChunkRevisionId(6L);
        document.setParseStatus("SUCCEEDED");

        parseRevision = new KbParseRevisionEntity();
        parseRevision.setId(5L);
        parseRevision.setParseRevisionKey("01J_PARSE");

        currentChunkRevision = new KbChunkRevisionEntity();
        currentChunkRevision.setId(6L);
        currentChunkRevision.setChunkRevisionKey("01J_CHUNK_OLD");
    }

    @Test
    @DisplayName("confirmed=false 直接拒绝（AC-019）")
    void triggerRechunk_notConfirmed_throwsException() {
        TaskExecutionException ex = assertThrows(TaskExecutionException.class,
                () -> service.triggerRechunk("01J_DOC", false, "01J_CHUNK_OLD",
                        ParsingChunkProfile.DEFAULT_RECURSIVE));
        assertEquals("RECHUNK_CONFIRMATION_REQUIRED", ex.getMessage());
        verifyNoInteractions(taskApplicationService);
    }

    @Test
    @DisplayName("解析未成功时拒绝（PARSE_NOT_SUCCEEDED）")
    void triggerRechunk_parseNotSucceeded_throwsException() {
        document.setParseStatus("FAILED");
        setupCommonMocks();

        TaskExecutionException ex = assertThrows(TaskExecutionException.class,
                () -> service.triggerRechunk("01J_DOC", true, "01J_CHUNK_OLD",
                        ParsingChunkProfile.DEFAULT_RECURSIVE));
        assertEquals("PARSE_NOT_SUCCEEDED", ex.getMessage());
    }

    @Test
    @DisplayName("expectedChunkRevisionKey 不匹配返回 409（AC-020）")
    void triggerRechunk_revisionMismatch_throwsConflict() {
        setupCommonMocks();
        when(chunkRevisionDomainService.getById(6L)).thenReturn(currentChunkRevision);

        // 传入错误的 expectedChunkRevisionKey
        assertThrows(KnowledgeBaseConflictException.class,
                () -> service.triggerRechunk("01J_DOC", true, "01J_WRONG_KEY",
                        ParsingChunkProfile.DEFAULT_RECURSIVE));
    }

    @Test
    @DisplayName("成功创建重新分块任务（AC-020）")
    void triggerRechunk_success_createsTask() {
        setupCommonMocks();
        when(chunkRevisionDomainService.getById(6L)).thenReturn(currentChunkRevision);
        when(parseRevisionDomainService.getById(5L)).thenReturn(parseRevision);

        KbProcessingTaskEntity taskEntity = new KbProcessingTaskEntity();
        taskEntity.setTaskKey("01J_RECHUNK_TASK");
        when(taskApplicationService.createTask(any(), any(), any(), eq(TaskType.RECHUNK),
                anyString(), anyString(), anyString()))
                .thenReturn(new ProcessingTask(taskEntity));

        RechunkResponse response = service.triggerRechunk(
                "01J_DOC", true, "01J_CHUNK_OLD", ParsingChunkProfile.DEFAULT_RECURSIVE);

        assertEquals("01J_DOC", response.documentKey());
        assertEquals("01J_RECHUNK_TASK", response.taskKey());
        verify(taskApplicationService).createTask(eq(1L), eq(2L), eq(3L), eq(TaskType.RECHUNK),
                eq("01J_PARSE"), anyString(), anyString());
    }

    private void setupCommonMocks() {
        when(currentUserContext.requireCurrentUser()).thenReturn(user);
        when(sourceDocumentDomainService.getOne(any())).thenReturn(document);
        when(knowledgeBaseDomainService.getById(2L)).thenReturn(knowledgeBase);
        when(workspaceDomainService.getById(1L)).thenReturn(workspace);
    }
}
