package com.fons.cloud.ai.rag2okf.application.parsing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fons.cloud.ai.rag2okf.application.model.CurrentUserContext;
import com.fons.cloud.ai.rag2okf.application.model.ModelBusinessKeyGenerator;
import com.fons.cloud.ai.rag2okf.application.task.TaskApplicationService;
import com.fons.cloud.ai.rag2okf.common.constants.WorkspaceRole;
import com.fons.cloud.ai.rag2okf.common.exeception.TaskExecutionException;
import com.fons.cloud.ai.rag2okf.common.response.ChunkPreviewResponse;
import com.fons.cloud.ai.rag2okf.common.response.ParsePreviewResponse;
import com.fons.cloud.ai.rag2okf.common.response.ParseTriggerResponse;
import com.fons.cloud.ai.rag2okf.domain.entity.KbDocumentVersionEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbKnowledgeBaseEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbProcessingTaskEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbSourceDocumentEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbUserEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbWorkspaceEntity;
import com.fons.cloud.ai.rag2okf.domain.task.ProcessingTask;
import com.fons.cloud.ai.rag2okf.domain.task.TaskType;
import com.fons.cloud.ai.rag2okf.infrastructure.identity.WorkspaceAccessPolicy;
import com.fons.cloud.ai.rag2okf.domain.service.KbChunkRevisionDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.KbDocumentVersionDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.KbKnowledgeBaseDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.KbParseRevisionDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.KbSourceDocumentDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.KbWorkspaceDomainService;
import com.fons.cloud.ai.rag2okf.domain.artifact.DocumentArtifactStore;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * ParseApplicationService 测试，覆盖 AC-006、AC-012、AC-013、AC-014。
 *
 * @author hongqy
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("解析应用服务")
class ParseApplicationServiceTest {

    @Mock private CurrentUserContext currentUserContext;
    @Mock private WorkspaceAccessPolicy workspaceAccessPolicy;
    @Mock private KbSourceDocumentDomainService sourceDocumentDomainService;
    @Mock private KbKnowledgeBaseDomainService knowledgeBaseDomainService;
    @Mock private KbDocumentVersionDomainService documentVersionDomainService;
    @Mock private KbParseRevisionDomainService parseRevisionDomainService;
    @Mock private KbChunkRevisionDomainService chunkRevisionDomainService;
    @Mock private KbWorkspaceDomainService workspaceDomainService;
    @Mock private DocumentArtifactStore documentArtifactStore;
    @Mock private TaskApplicationService taskApplicationService;
    @Mock private ModelBusinessKeyGenerator keyGenerator;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks private ParseApplicationService service;

    private KbUserEntity user;
    private KbWorkspaceEntity workspace;
    private KbKnowledgeBaseEntity knowledgeBase;
    private KbSourceDocumentEntity document;
    private KbDocumentVersionEntity version;

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
        knowledgeBase.setAutoParse(true);
        knowledgeBase.setAutoPublish(false);
        knowledgeBase.setParserProfile("NATIVE_TIKA");

        document = new KbSourceDocumentEntity();
        document.setId(3L);
        document.setDocumentKey("01J_DOC");
        document.setKnowledgeBaseId(2L);
        document.setCurrentDocumentVersionId(4L);
        document.setParseStatus("NOT_STARTED");
        document.setPublishStatus("UNPUBLISHED");

        version = new KbDocumentVersionEntity();
        version.setId(4L);
        version.setVersionKey("01J_VER");
        version.setOriginalFilename("test.md");
        version.setContentType("text/markdown");
    }

    @Test
    @DisplayName("SKIP 模式不创建任务，返回 NOT_STARTED（AC-013）")
    void triggerParse_skipMode_returnsNullTaskKey() {
        setupCommonMocks();

        ParseTriggerResponse response = service.triggerParse("01J_DOC", "SKIP");

        assertNull(response.taskKey());
        assertEquals("NOT_STARTED", response.parseStatus());
        verify(taskApplicationService, never()).createTask(any(), any(), any(),
                any(), any(), any(), any());
    }

    @Test
    @DisplayName("PARSE 模式创建任务并设置 QUEUED（AC-006）")
    void triggerParse_parseMode_createsTaskAndQueues() {
        setupCommonMocks();
        KbProcessingTaskEntity taskEntity = new KbProcessingTaskEntity();
        taskEntity.setTaskKey("01J_TASK");
        when(taskApplicationService.createTask(any(), any(), any(), eq(TaskType.PARSE),
                anyString(), anyString(), anyString()))
                .thenReturn(new ProcessingTask(taskEntity));
        when(sourceDocumentDomainService.updateById(any())).thenReturn(true);

        ParseTriggerResponse response = service.triggerParse("01J_DOC", "PARSE");

        assertEquals("01J_TASK", response.taskKey());
        assertEquals("QUEUED", response.parseStatus());
        verify(taskApplicationService).createTask(any(), any(), any(), eq(TaskType.PARSE),
                anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("DEFAULT 模式且 autoParse=false 不创建任务（AC-006）")
    void triggerParse_defaultMode_autoParseFalse_returnsNullTaskKey() {
        knowledgeBase.setAutoParse(false);
        setupCommonMocks();

        ParseTriggerResponse response = service.triggerParse("01J_DOC", "DEFAULT");

        assertNull(response.taskKey());
        verify(taskApplicationService, never()).createTask(any(), any(), any(),
                any(), any(), any(), any());
    }

    @Test
    @DisplayName("无解析产物时返回空预览（AC-013 不伪造）")
    void getParsePreview_noParse_returnsEmptyPreview() {
        when(currentUserContext.requireCurrentUser()).thenReturn(user);
        when(sourceDocumentDomainService.getOne(any())).thenReturn(document);
        when(knowledgeBaseDomainService.getById(2L)).thenReturn(knowledgeBase);
        when(workspaceDomainService.getById(1L)).thenReturn(workspace);
        document.setCurrentParseRevisionId(null);

        ParsePreviewResponse response = service.getParsePreview("01J_DOC");

        assertFalse(response.hasParse());
        assertTrue(response.blocks().isEmpty());
    }

    @Test
    @DisplayName("无分块产物时返回空预览（AC-013 不伪造）")
    void getChunkPreview_noChunk_returnsEmptyPreview() {
        when(currentUserContext.requireCurrentUser()).thenReturn(user);
        when(sourceDocumentDomainService.getOne(any())).thenReturn(document);
        when(knowledgeBaseDomainService.getById(2L)).thenReturn(knowledgeBase);
        when(workspaceDomainService.getById(1L)).thenReturn(workspace);
        document.setCurrentChunkRevisionId(null);

        ChunkPreviewResponse response = service.getChunkPreview("01J_DOC", 0, 20);

        assertFalse(response.hasChunk());
        assertTrue(response.chunks().isEmpty());
    }

    @Test
    @DisplayName("重试任务创建新任务（AC-014）")
    void retryTask_createsNewTask() {
        KbProcessingTaskEntity originalEntity = new KbProcessingTaskEntity();
        originalEntity.setTaskKey("01J_ORIG_TASK");
        originalEntity.setWorkspaceId(1L);
        originalEntity.setKnowledgeBaseId(2L);
        originalEntity.setSourceDocumentId(3L);
        originalEntity.setTaskType("PARSE");
        originalEntity.setInputRevisionKey("01J_VER");
        originalEntity.setPayloadJson("{\"documentKey\":\"01J_DOC\"}");
        ProcessingTask originalTask = new ProcessingTask(originalEntity);

        when(currentUserContext.requireCurrentUser()).thenReturn(user);
        when(taskApplicationService.findByKey("01J_ORIG_TASK")).thenReturn(originalTask);
        when(sourceDocumentDomainService.getById(3L)).thenReturn(document);
        when(knowledgeBaseDomainService.getById(2L)).thenReturn(knowledgeBase);
        when(workspaceDomainService.getById(1L)).thenReturn(workspace);

        KbProcessingTaskEntity retryEntity = new KbProcessingTaskEntity();
        retryEntity.setTaskKey("01J_RETRY_TASK");
        when(taskApplicationService.createTask(any(), any(), any(), any(),
                any(), any(), any())).thenReturn(new ProcessingTask(retryEntity));
        when(sourceDocumentDomainService.updateById(any())).thenReturn(true);

        String retryTaskKey = service.retryTask("01J_ORIG_TASK");

        assertEquals("01J_RETRY_TASK", retryTaskKey);
        verify(taskApplicationService).createTask(eq(1L), eq(2L), eq(3L), any(),
                anyString(), anyString(), anyString());
    }

    private void setupCommonMocks() {
        when(currentUserContext.requireCurrentUser()).thenReturn(user);
        when(sourceDocumentDomainService.getOne(any())).thenReturn(document);
        when(knowledgeBaseDomainService.getById(2L)).thenReturn(knowledgeBase);
        when(workspaceDomainService.getById(1L)).thenReturn(workspace);
        when(documentVersionDomainService.getById(4L)).thenReturn(version);
    }
}
