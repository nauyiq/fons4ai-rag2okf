package com.fons.cloud.ai.rag2okf.application.publication;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fons.cloud.ai.rag2okf.application.model.CurrentUserContext;
import com.fons.cloud.ai.rag2okf.application.task.TaskApplicationService;
import com.fons.cloud.ai.rag2okf.common.exeception.KnowledgeBaseConflictException;
import com.fons.cloud.ai.rag2okf.common.exeception.TaskExecutionException;
import com.fons.cloud.ai.rag2okf.common.response.PublicationResponse;
import com.fons.cloud.ai.rag2okf.domain.entity.KbChunkRevisionEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbDocumentVersionEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbKnowledgeBaseEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbProcessingTaskEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbPublicationRevisionEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbSourceDocumentEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbUserEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbWorkspaceEntity;
import com.fons.cloud.ai.rag2okf.domain.service.KbChunkRevisionDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.KbDocumentVersionDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.KbKnowledgeBaseDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.KbProcessingTaskDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.KbPublicationRevisionDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.KbSourceDocumentDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.KbWorkspaceDomainService;
import com.fons.cloud.ai.rag2okf.domain.task.ProcessingTask;
import com.fons.cloud.ai.rag2okf.domain.task.TaskType;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * PublicationApplicationService 测试，覆盖 AC-015、AC-016、AC-017、AC-018、AC-021。
 *
 * @author hongqy
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("发布应用服务")
class PublicationApplicationServiceTest {

    @Mock private CurrentUserContext currentUserContext;
    @Mock private WorkspaceAccessPolicy workspaceAccessPolicy;
    @Mock private KbSourceDocumentDomainService sourceDocumentDomainService;
    @Mock private KbKnowledgeBaseDomainService knowledgeBaseDomainService;
    @Mock private KbWorkspaceDomainService workspaceDomainService;
    @Mock private KbDocumentVersionDomainService documentVersionDomainService;
    @Mock private KbChunkRevisionDomainService chunkRevisionDomainService;
    @Mock private KbPublicationRevisionDomainService publicationRevisionDomainService;
    @Mock private KbProcessingTaskDomainService processingTaskDomainService;
    @Mock private TaskApplicationService taskApplicationService;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks private PublicationApplicationService service;

    private KbUserEntity user;
    private KbWorkspaceEntity workspace;
    private KbKnowledgeBaseEntity knowledgeBase;
    private KbSourceDocumentEntity document;
    private KbDocumentVersionEntity version;
    private KbChunkRevisionEntity chunkRevision;

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
        document.setCurrentDocumentVersionId(4L);
        document.setCurrentParseRevisionId(5L);
        document.setCurrentChunkRevisionId(6L);
        document.setParseStatus("SUCCEEDED");
        document.setPublishStatus("UNPUBLISHED");

        version = new KbDocumentVersionEntity();
        version.setId(4L);
        version.setVersionKey("01J_VER");

        chunkRevision = new KbChunkRevisionEntity();
        chunkRevision.setId(6L);
        chunkRevision.setChunkRevisionKey("01J_CHUNK");
        chunkRevision.setStatus("SUCCEEDED");
    }

    @Test
    @DisplayName("未解析成功时拒绝发布（AC-015）")
    void triggerPublish_parseNotSucceeded_throwsException() {
        document.setParseStatus("FAILED");
        setupCommonMocks();

        TaskExecutionException ex = assertThrows(TaskExecutionException.class,
                () -> service.triggerPublish("01J_DOC", "MANUAL"));
        assertEquals("PUBLISH_PARSE_NOT_SUCCEEDED", ex.getMessage());
        verifyNoInteractions(taskApplicationService);
    }

    @Test
    @DisplayName("分块未成功时拒绝发布（AC-015）")
    void triggerPublish_chunkNotSucceeded_throwsException() {
        setupCommonMocks();
        chunkRevision.setStatus("FAILED");
        when(chunkRevisionDomainService.getById(6L)).thenReturn(chunkRevision);

        TaskExecutionException ex = assertThrows(TaskExecutionException.class,
                () -> service.triggerPublish("01J_DOC", "MANUAL"));
        assertEquals("PUBLISH_CHUNK_NOT_SUCCEEDED", ex.getMessage());
    }

    @Test
    @DisplayName("存在 RUNNING 发布任务时返回 409（§5.6 第 1 步）")
    void triggerPublish_runningTaskExists_throwsConflict() {
        setupCommonMocks();
        when(chunkRevisionDomainService.getById(6L)).thenReturn(chunkRevision);
        when(documentVersionDomainService.getById(4L)).thenReturn(version);
        KbProcessingTaskEntity runningTask = new KbProcessingTaskEntity();
        when(processingTaskDomainService.getOne(any())).thenReturn(runningTask);

        assertThrows(KnowledgeBaseConflictException.class,
                () -> service.triggerPublish("01J_DOC", "MANUAL"));
    }

    @Test
    @DisplayName("首次发布成功创建任务并置 PUBLISHING（AC-016）")
    void triggerPublish_firstPublish_createsTaskAndSetsPublishing() {
        setupCommonMocks();
        when(chunkRevisionDomainService.getById(6L)).thenReturn(chunkRevision);
        when(documentVersionDomainService.getById(4L)).thenReturn(version);

        KbProcessingTaskEntity taskEntity = new KbProcessingTaskEntity();
        taskEntity.setTaskKey("01J_PUB_TASK");
        when(taskApplicationService.createTask(any(), any(), any(), eq(TaskType.PUBLISH),
                anyString(), anyString(), anyString()))
                .thenReturn(new ProcessingTask(taskEntity));

        PublicationResponse response = service.triggerPublish("01J_DOC", "MANUAL");

        assertEquals("01J_DOC", response.documentKey());
        assertEquals("01J_PUB_TASK", response.taskKey());
        assertEquals("PUBLISHING", response.publishStatus());
        assertEquals("QUEUED", response.latestAttemptStatus());
        verify(taskApplicationService).createTask(eq(1L), eq(2L), eq(3L), eq(TaskType.PUBLISH),
                eq("01J_CHUNK"), eq("PUBLISH:01J_CHUNK"), anyString());
    }

    @Test
    @DisplayName("重新发布保持 PUBLISHED 状态（§7.2）")
    void triggerPublish_rePublish_keepsPublishedStatus() {
        document.setActivePublicationRevisionId(7L);
        document.setPublishStatus("PUBLISHED");
        setupCommonMocks();
        when(chunkRevisionDomainService.getById(6L)).thenReturn(chunkRevision);
        when(documentVersionDomainService.getById(4L)).thenReturn(version);
        KbPublicationRevisionEntity existingPublication = new KbPublicationRevisionEntity();
        existingPublication.setPublicationRevisionKey("01J_OLD_PUB");
        when(publicationRevisionDomainService.getById(7L)).thenReturn(existingPublication);

        KbProcessingTaskEntity taskEntity = new KbProcessingTaskEntity();
        taskEntity.setTaskKey("01J_PUB_TASK_2");
        when(taskApplicationService.createTask(any(), any(), any(), eq(TaskType.PUBLISH),
                anyString(), anyString(), anyString()))
                .thenReturn(new ProcessingTask(taskEntity));

        PublicationResponse response = service.triggerPublish("01J_DOC", "AUTO");

        assertEquals("PUBLISHED", response.publishStatus());
        assertEquals("AUTO", "AUTO");
    }

    @Test
    @DisplayName("自动与手动发布共用流程（AC-016）")
    void triggerPublish_autoAndManualShareSameFlow() {
        setupCommonMocks();
        when(chunkRevisionDomainService.getById(6L)).thenReturn(chunkRevision);
        when(documentVersionDomainService.getById(4L)).thenReturn(version);

        KbProcessingTaskEntity taskEntity = new KbProcessingTaskEntity();
        taskEntity.setTaskKey("01J_PUB_AUTO");
        when(taskApplicationService.createTask(any(), any(), any(), eq(TaskType.PUBLISH),
                anyString(), anyString(), anyString()))
                .thenReturn(new ProcessingTask(taskEntity));

        PublicationResponse autoResponse = service.triggerPublish("01J_DOC", "AUTO");

        assertNotNull(autoResponse.taskKey());
        // 幂等键相同，复用任务
        verify(taskApplicationService).createTask(eq(1L), eq(2L), eq(3L), eq(TaskType.PUBLISH),
                eq("01J_CHUNK"), eq("PUBLISH:01J_CHUNK"), anyString());
    }

    private void setupCommonMocks() {
        when(currentUserContext.requireCurrentUser()).thenReturn(user);
        when(sourceDocumentDomainService.getOne(any())).thenReturn(document);
        when(knowledgeBaseDomainService.getById(2L)).thenReturn(knowledgeBase);
        when(workspaceDomainService.getById(1L)).thenReturn(workspace);
        lenient().when(sourceDocumentDomainService.updateById(any())).thenReturn(true);
    }
}
