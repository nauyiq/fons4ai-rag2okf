package com.fons.cloud.ai.rag2okf.application.document;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fons.cloud.ai.rag2okf.application.model.CurrentUserContext;
import com.fons.cloud.ai.rag2okf.application.model.ModelBusinessKeyGenerator;
import com.fons.cloud.ai.rag2okf.application.task.TaskApplicationService;
import com.fons.cloud.ai.rag2okf.common.constants.WorkspaceRole;
import com.fons.cloud.ai.rag2okf.common.exeception.DocumentArtifactException;
import com.fons.cloud.ai.rag2okf.common.exeception.KnowledgeBaseConflictException;
import com.fons.cloud.ai.rag2okf.common.exeception.WorkspaceAccessDeniedException;
import com.fons.cloud.ai.rag2okf.common.response.DocumentUploadResponse;
import com.fons.cloud.ai.rag2okf.common.response.DocumentSummaryResponse;
import com.fons.cloud.ai.rag2okf.domain.artifact.DocumentArtifactStore;
import com.fons.cloud.ai.rag2okf.domain.artifact.DocumentArtifactStore.ArtifactContent;
import com.fons.cloud.ai.rag2okf.domain.artifact.DocumentArtifactStore.StoredArtifact;
import com.fons.cloud.ai.rag2okf.domain.entity.KbDocumentVersionEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbKnowledgeBaseEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbProcessingTaskEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbSourceDocumentEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbUserEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbWorkspaceEntity;
import com.fons.cloud.ai.rag2okf.domain.service.KbDocumentVersionDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.KbKnowledgeBaseDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.KbSourceDocumentDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.KbWorkspaceDomainService;
import com.fons.cloud.ai.rag2okf.infrastructure.identity.WorkspaceAccessPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 文档上传、更新文件与原文件访问的聚焦测试。
 *
 * <p>覆盖 AC-005（下载）、AC-008（同名上传不同 documentKey）、
 * AC-009（更新 CAS 与指针结构）、AC-010（无版本/历史/回退入口）、
 * AC-013（未解析不伪造结果）和 AC-014（安全错误）。</p>
 *
 * @author hongqy
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("文档上传、更新与下载")
class DocumentApplicationServiceTest {

    @Mock private CurrentUserContext currentUserContext;
    @Mock private WorkspaceAccessPolicy workspaceAccessPolicy;
    @Mock private KbKnowledgeBaseDomainService knowledgeBaseDomainService;
    @Mock private KbSourceDocumentDomainService sourceDocumentDomainService;
    @Mock private KbDocumentVersionDomainService documentVersionDomainService;
    @Mock private KbWorkspaceDomainService workspaceDomainService;
    @Mock private DocumentArtifactStore documentArtifactStore;
    @Mock private FileValidationPolicy fileValidationPolicy;
    @Mock private ModelBusinessKeyGenerator keyGenerator;
    @Mock private TaskApplicationService taskApplicationService;

    @InjectMocks private DocumentApplicationService service;

    private final AtomicLong idSequence = new AtomicLong(100L);

    @BeforeEach
    void setUp() {
        // 设置 self 引用以使 @Transactional 内部调用生效
        ReflectionTestUtils.setField(service, "self", service);
        idSequence.set(100L);
    }

    // ────────────────────────────── AC-008：同名上传不同 documentKey ──────────────────────────────

    @Test
    @DisplayName("同名普通上传两次得到不同 documentKey")
    void uploadDocument_sameFilenameGeneratesDifferentDocumentKeys() throws IOException {
        KbUserEntity user = mockUser("01J_USER_KEY");
        KbKnowledgeBaseEntity kb = mockKnowledgeBase("01J_KB_KEY", 1L);
        KbWorkspaceEntity workspace = mockWorkspace("01J_WS_KEY", 1L);
        when(currentUserContext.requireCurrentUser()).thenReturn(user);
        when(knowledgeBaseDomainService.getOne(any())).thenReturn(kb);
        when(workspaceDomainService.getById(1L)).thenReturn(workspace);
        when(keyGenerator.nextKey()).thenReturn("01J_DOC_1", "01J_VER_1", "01J_DOC_2", "01J_VER_2");
        FileValidationPolicy.ValidatedFile vf1 = mockValidatedFile("policy.md");
        when(fileValidationPolicy.validate(anyString(), anyString(), any(), anyLong()))
                .thenReturn(vf1);
        when(documentArtifactStore.storeOriginal(any())).thenReturn(mockStoredArtifact());
        when(sourceDocumentDomainService.save(any())).thenAnswer(inv -> {
            ((KbSourceDocumentEntity) inv.getArgument(0)).setId(idSequence.incrementAndGet());
            return true;
        });
        when(documentVersionDomainService.save(any())).thenAnswer(inv -> {
            ((KbDocumentVersionEntity) inv.getArgument(0)).setId(idSequence.incrementAndGet());
            return true;
        });
        when(sourceDocumentDomainService.updateById(any())).thenReturn(true);

        MultipartFile file = mockMultipartFile("policy.md");
        DocumentUploadResponse response1 = service.uploadDocument("01J_KB_KEY", file, "SKIP");
        DocumentUploadResponse response2 = service.uploadDocument("01J_KB_KEY", file, "SKIP");

        assertNotEquals(response1.documentKey(), response2.documentKey(),
                "同名上传两次必须得到不同 documentKey");
        assertEquals("01J_DOC_1", response1.documentKey());
        assertEquals("01J_DOC_2", response2.documentKey());
    }

    // ────────────────────────────── AC-013：SKIP 不伪造解析结果 ──────────────────────────────

    @Test
    @DisplayName("SKIP 模式上传后 parseStatus 为 NOT_STARTED，不伪造解析结果")
    void uploadDocument_parseModeSkipSetsNotStarted() throws IOException {
        KbUserEntity user = mockUser("01J_USER_KEY");
        KbKnowledgeBaseEntity kb = mockKnowledgeBase("01J_KB_KEY", 1L);
        KbWorkspaceEntity workspace = mockWorkspace("01J_WS_KEY", 1L);
        when(currentUserContext.requireCurrentUser()).thenReturn(user);
        when(knowledgeBaseDomainService.getOne(any())).thenReturn(kb);
        when(workspaceDomainService.getById(1L)).thenReturn(workspace);
        when(keyGenerator.nextKey()).thenReturn("01J_DOC", "01J_VER");
        FileValidationPolicy.ValidatedFile vf2 = mockValidatedFile("guide.md");
        when(fileValidationPolicy.validate(anyString(), anyString(), any(), anyLong()))
                .thenReturn(vf2);
        when(documentArtifactStore.storeOriginal(any())).thenReturn(mockStoredArtifact());
        when(sourceDocumentDomainService.save(any())).thenAnswer(inv -> {
            ((KbSourceDocumentEntity) inv.getArgument(0)).setId(idSequence.incrementAndGet());
            return true;
        });
        when(documentVersionDomainService.save(any())).thenAnswer(inv -> {
            ((KbDocumentVersionEntity) inv.getArgument(0)).setId(idSequence.incrementAndGet());
            return true;
        });
        when(sourceDocumentDomainService.updateById(any())).thenReturn(true);

        DocumentUploadResponse response = service.uploadDocument(
                "01J_KB_KEY", mockMultipartFile("guide.md"), "SKIP");

        assertEquals("NOT_STARTED", response.parseStatus());
        assertEquals("UNPUBLISHED", response.publishStatus());
        assertNull(response.taskKey(), "T009 不创建任务，taskKey 为 null");
    }

    // ────────────────────────────── AC-009：更新成功切换当前文件 ──────────────────────────────

    @Test
    @DisplayName("更新成功切换当前文件指针")
    void updateDocumentFile_successSwitchesCurrentFile() throws IOException {
        KbUserEntity user = mockUser("01J_USER_KEY");
        KbKnowledgeBaseEntity kb = mockKnowledgeBase("01J_KB_KEY", 1L);
        KbWorkspaceEntity workspace = mockWorkspace("01J_WS_KEY", 1L);
        KbSourceDocumentEntity document = new KbSourceDocumentEntity();
        document.setId(50L);
        document.setDocumentKey("01J_DOC");
        document.setKnowledgeBaseId(1L);
        document.setDisplayName("old.md");
        document.setCurrentDocumentVersionId(60L);
        document.setParseStatus("SUCCEEDED");
        document.setPublishStatus("PUBLISHED");
        document.setVersion(0);

        KbDocumentVersionEntity oldVersion = new KbDocumentVersionEntity();
        oldVersion.setId(60L);
        oldVersion.setVersionKey("01J_OLD_VER");

        when(currentUserContext.requireCurrentUser()).thenReturn(user);
        when(sourceDocumentDomainService.getOne(any())).thenReturn(document);
        when(knowledgeBaseDomainService.getById(1L)).thenReturn(kb);
        when(workspaceDomainService.getById(1L)).thenReturn(workspace);
        when(documentVersionDomainService.getById(60L)).thenReturn(oldVersion);
        when(keyGenerator.nextKey()).thenReturn("01J_NEW_VER");
        // 必须先创建 ValidatedFile mock，再放入 when().thenReturn()，避免嵌套 stubbing
        FileValidationPolicy.ValidatedFile vfUpdate = mockValidatedFile("new.md");
        when(fileValidationPolicy.validate(anyString(), anyString(), any(), anyLong()))
                .thenReturn(vfUpdate);
        when(documentArtifactStore.storeOriginal(any())).thenReturn(mockStoredArtifact());
        when(documentVersionDomainService.save(any())).thenAnswer(inv -> {
            ((KbDocumentVersionEntity) inv.getArgument(0)).setId(idSequence.incrementAndGet());
            return true;
        });
        when(sourceDocumentDomainService.updateById(any())).thenReturn(true);

        DocumentUploadResponse response = service.updateDocumentFile(
                "01J_DOC", mockMultipartFile("new.md"), "SKIP", "01J_OLD_VER");

        assertEquals("01J_DOC", response.documentKey());
        assertEquals("new.md", response.displayName());
        assertEquals("NOT_STARTED", response.parseStatus(), "更新后解析状态重置");
        assertEquals("UNPUBLISHED", response.publishStatus(), "更新后发布状态重置");
        // activePublicationRevisionId 不修改（旧发布继续可用），但响应不暴露此字段
    }

    // ────────────────────────────── AC-009：并发冲突保留旧指针 ──────────────────────────────

    @Test
    @DisplayName("更新时 expectedCurrentVersionKey 不匹配抛出冲突")
    void updateDocumentFile_invalidExpectedVersionKeyThrowsConflict() {
        KbUserEntity user = mockUser("01J_USER_KEY");
        KbKnowledgeBaseEntity kb = mockKnowledgeBase("01J_KB_KEY", 1L);
        KbWorkspaceEntity workspace = mockWorkspace("01J_WS_KEY", 1L);
        KbSourceDocumentEntity document = new KbSourceDocumentEntity();
        document.setId(50L);
        document.setDocumentKey("01J_DOC");
        document.setKnowledgeBaseId(1L);
        document.setCurrentDocumentVersionId(60L);

        KbDocumentVersionEntity oldVersion = new KbDocumentVersionEntity();
        oldVersion.setId(60L);
        oldVersion.setVersionKey("01J_OLD_VER");

        when(currentUserContext.requireCurrentUser()).thenReturn(user);
        when(sourceDocumentDomainService.getOne(any())).thenReturn(document);
        when(knowledgeBaseDomainService.getById(1L)).thenReturn(kb);
        when(workspaceDomainService.getById(1L)).thenReturn(workspace);
        when(documentVersionDomainService.getById(60L)).thenReturn(oldVersion);

        // 版本 key 校验阶段即抛出异常，文件从未被访问，使用裸 mock 避免 UnnecessaryStubbing
        assertThrows(KnowledgeBaseConflictException.class, () ->
                service.updateDocumentFile(
                        "01J_DOC", mock(MultipartFile.class), "SKIP", "WRONG_VERSION_KEY"));
    }

    @Test
    @DisplayName("更新时 CAS 失败（乐观锁冲突）抛出异常")
    void updateDocumentFile_casFailureThrowsConflict() throws IOException {
        KbUserEntity user = mockUser("01J_USER_KEY");
        KbKnowledgeBaseEntity kb = mockKnowledgeBase("01J_KB_KEY", 1L);
        KbWorkspaceEntity workspace = mockWorkspace("01J_WS_KEY", 1L);
        KbSourceDocumentEntity document = new KbSourceDocumentEntity();
        document.setId(50L);
        document.setDocumentKey("01J_DOC");
        document.setKnowledgeBaseId(1L);
        document.setCurrentDocumentVersionId(60L);
        document.setVersion(0);

        KbDocumentVersionEntity oldVersion = new KbDocumentVersionEntity();
        oldVersion.setId(60L);
        oldVersion.setVersionKey("01J_OLD_VER");

        when(currentUserContext.requireCurrentUser()).thenReturn(user);
        when(sourceDocumentDomainService.getOne(any())).thenReturn(document);
        when(knowledgeBaseDomainService.getById(1L)).thenReturn(kb);
        when(workspaceDomainService.getById(1L)).thenReturn(workspace);
        when(documentVersionDomainService.getById(60L)).thenReturn(oldVersion);
        when(keyGenerator.nextKey()).thenReturn("01J_NEW_VER");
        // 必须先创建 ValidatedFile mock，再放入 when().thenReturn()，避免嵌套 stubbing
        FileValidationPolicy.ValidatedFile vfCas = mockValidatedFile("new.md");
        when(fileValidationPolicy.validate(anyString(), anyString(), any(), anyLong()))
                .thenReturn(vfCas);
        when(documentArtifactStore.storeOriginal(any())).thenReturn(mockStoredArtifact());
        when(documentVersionDomainService.save(any())).thenAnswer(inv -> {
            ((KbDocumentVersionEntity) inv.getArgument(0)).setId(idSequence.incrementAndGet());
            return true;
        });
        // 模拟乐观锁冲突：updateById 返回 false
        when(sourceDocumentDomainService.updateById(any())).thenReturn(false);

        assertThrows(KnowledgeBaseConflictException.class, () ->
                service.updateDocumentFile("01J_DOC", mockMultipartFile("new.md"), "SKIP", "01J_OLD_VER"));

        // 验证补偿删除被调用
        verify(documentArtifactStore).delete(any());
    }

    // ────────────────────────────── AC-010：响应无版本列表/回退 ──────────────────────────────

    @Test
    @DisplayName("上传响应不包含版本列表或回退操作")
    void uploadResponse_hasNoVersionList() throws IOException {
        KbUserEntity user = mockUser("01J_USER_KEY");
        KbKnowledgeBaseEntity kb = mockKnowledgeBase("01J_KB_KEY", 1L);
        KbWorkspaceEntity workspace = mockWorkspace("01J_WS_KEY", 1L);
        when(currentUserContext.requireCurrentUser()).thenReturn(user);
        when(knowledgeBaseDomainService.getOne(any())).thenReturn(kb);
        when(workspaceDomainService.getById(1L)).thenReturn(workspace);
        when(keyGenerator.nextKey()).thenReturn("01J_DOC", "01J_VER");
        FileValidationPolicy.ValidatedFile vf5 = mockValidatedFile("report.md");
        when(fileValidationPolicy.validate(anyString(), anyString(), any(), anyLong()))
                .thenReturn(vf5);
        when(documentArtifactStore.storeOriginal(any())).thenReturn(mockStoredArtifact());
        when(sourceDocumentDomainService.save(any())).thenAnswer(inv -> {
            ((KbSourceDocumentEntity) inv.getArgument(0)).setId(idSequence.incrementAndGet());
            return true;
        });
        when(documentVersionDomainService.save(any())).thenAnswer(inv -> {
            ((KbDocumentVersionEntity) inv.getArgument(0)).setId(idSequence.incrementAndGet());
            return true;
        });
        when(sourceDocumentDomainService.updateById(any())).thenReturn(true);

        DocumentUploadResponse response = service.uploadDocument(
                "01J_KB_KEY", mockMultipartFile("report.md"), "SKIP");

        // 响应只有 documentKey、displayName、currentFile、taskKey、parseStatus、publishStatus
        // 不包含版本列表、版本序号或回退操作
        assertEquals("01J_DOC", response.documentKey());
        assertEquals("report.md", response.displayName());
        assertNotNull(response.currentFile());
        assertEquals("report.md", response.currentFile().filename());
        assertEquals("text/markdown", response.currentFile().contentType());
        assertEquals(1024L, response.currentFile().size());
    }

    // ────────────────────────────── AC-005：授权下载可用 ──────────────────────────────

    @Test
    @DisplayName("授权下载返回文件内容流")
    void downloadDocumentFile_returnsFileContent() {
        KbUserEntity user = mockUser("01J_USER_KEY");
        KbKnowledgeBaseEntity kb = mockKnowledgeBase("01J_KB_KEY", 1L);
        KbWorkspaceEntity workspace = mockWorkspace("01J_WS_KEY", 1L);
        KbSourceDocumentEntity document = new KbSourceDocumentEntity();
        document.setId(50L);
        document.setDocumentKey("01J_DOC");
        document.setKnowledgeBaseId(1L);
        document.setCurrentDocumentVersionId(60L);

        KbDocumentVersionEntity version = new KbDocumentVersionEntity();
        version.setId(60L);
        version.setVersionKey("01J_VER");
        version.setOriginalFilename("download.md");
        version.setContentType("text/markdown");
        version.setSizeBytes(100L);

        when(currentUserContext.requireCurrentUser()).thenReturn(user);
        when(sourceDocumentDomainService.getOne(any())).thenReturn(document);
        when(knowledgeBaseDomainService.getById(1L)).thenReturn(kb);
        when(workspaceDomainService.getById(1L)).thenReturn(workspace);
        when(documentVersionDomainService.getById(60L)).thenReturn(version);
        when(documentArtifactStore.open(any())).thenReturn(new ArtifactContent(
                new ByteArrayInputStream("content".getBytes())));

        var content = service.downloadDocumentFile("01J_DOC");

        assertEquals("download.md", content.filename());
        assertEquals("text/markdown", content.contentType());
        assertEquals(100L, content.size());
        assertNotNull(content.inputStream());
    }

    @Test
    @DisplayName("无权限用户下载被拒绝")
    void downloadDocumentFile_unauthorizedThrowsAccessDenied() {
        KbUserEntity user = mockUser("01J_USER_KEY");
        KbSourceDocumentEntity document = new KbSourceDocumentEntity();
        document.setId(50L);
        document.setDocumentKey("01J_DOC");
        document.setKnowledgeBaseId(1L);
        document.setCurrentDocumentVersionId(60L);

        when(currentUserContext.requireCurrentUser()).thenReturn(user);
        when(sourceDocumentDomainService.getOne(any())).thenReturn(document);
        when(knowledgeBaseDomainService.getById(1L)).thenReturn(null);

        assertThrows(WorkspaceAccessDeniedException.class, () ->
                service.downloadDocumentFile("01J_DOC"));
    }

    // ────────────────────────────── AC-014：安全错误 ──────────────────────────────

    @Test
    @DisplayName("空文件上传抛出安全异常")
    void uploadDocument_emptyFileThrowsArtifactException() {
        KbUserEntity user = mockUser("01J_USER_KEY");
        KbKnowledgeBaseEntity kb = mockKnowledgeBase("01J_KB_KEY", 1L);
        KbWorkspaceEntity workspace = mockWorkspace("01J_WS_KEY", 1L);
        when(currentUserContext.requireCurrentUser()).thenReturn(user);
        when(knowledgeBaseDomainService.getOne(any())).thenReturn(kb);
        when(workspaceDomainService.getById(1L)).thenReturn(workspace);

        MultipartFile emptyFile = mock(MultipartFile.class);
        when(emptyFile.isEmpty()).thenReturn(true);

        assertThrows(DocumentArtifactException.class, () ->
                service.uploadDocument("01J_KB_KEY", emptyFile, "SKIP"));
    }

    @Test
    @DisplayName("文档列表仅返回当前文件和安全任务摘要")
    void listDocuments_returnsCurrentFileTokenAndLatestTaskSummary() {
        KbUserEntity user = mockUser("01J_USER_KEY");
        KbKnowledgeBaseEntity kb = mockKnowledgeBase("01J_KB_KEY", 1L);
        KbWorkspaceEntity workspace = mockWorkspace("01J_WS_KEY", 1L);
        KbSourceDocumentEntity document = new KbSourceDocumentEntity();
        document.setId(50L);
        document.setDocumentKey("01J_DOC");
        document.setDisplayName("policy.md");
        document.setKnowledgeBaseId(1L);
        document.setCurrentDocumentVersionId(60L);
        document.setParseStatus("SUCCEEDED");
        document.setPublishStatus("PUBLISH_FAILED");
        document.setActivePublicationRevisionId(70L);
        KbDocumentVersionEntity version = new KbDocumentVersionEntity();
        version.setId(60L);
        version.setVersionKey("01J_FILE_TOKEN");
        version.setOriginalFilename("policy.md");
        version.setContentType("text/markdown");
        version.setSizeBytes(1024L);
        KbProcessingTaskEntity task = new KbProcessingTaskEntity();
        task.setTaskKey("01J_TASK");
        task.setTaskType("PUBLISH");
        task.setStatus("FAILED");
        task.setStage("INDEX");
        task.setProgress(80);
        task.setAttempt(1);
        task.setMaxAttempts(3);
        task.setErrorCode("ES_UNAVAILABLE");
        task.setErrorMessage("index unavailable");

        when(currentUserContext.requireCurrentUser()).thenReturn(user);
        when(knowledgeBaseDomainService.getOne(any())).thenReturn(kb);
        when(workspaceDomainService.getById(1L)).thenReturn(workspace);
        when(sourceDocumentDomainService.page(any(Page.class), any())).thenAnswer(invocation -> {
            Page<KbSourceDocumentEntity> result = invocation.getArgument(0);
            result.setRecords(List.of(document));
            result.setTotal(1L);
            return result;
        });
        when(documentVersionDomainService.listByIds(List.of(60L))).thenReturn(List.of(version));
        when(taskApplicationService.findLatestByDocumentIds(List.of(50L))).thenReturn(Map.of(50L, task));

        var response = service.listDocuments("01J_KB_KEY", 0, 20);

        assertEquals(1L, response.total());
        DocumentSummaryResponse item = response.records().getFirst();
        assertEquals("01J_FILE_TOKEN", item.currentFileToken());
        assertTrue(item.hasActivePublication());
        assertEquals("01J_TASK", item.latestTask().taskKey());
        assertEquals("FAILED", item.latestTask().status());
    }

    // ────────────────────────────── 辅助方法 ──────────────────────────────

    private KbUserEntity mockUser(String userKey) {
        KbUserEntity user = new KbUserEntity();
        user.setId(1L);
        user.setUserKey(userKey);
        return user;
    }

    private KbKnowledgeBaseEntity mockKnowledgeBase(String kbKey, Long workspaceId) {
        KbKnowledgeBaseEntity kb = new KbKnowledgeBaseEntity();
        kb.setId(1L);
        kb.setKnowledgeBaseKey(kbKey);
        kb.setWorkspaceId(workspaceId);
        kb.setStatus("ACTIVE");
        return kb;
    }

    private KbWorkspaceEntity mockWorkspace(String wsKey, Long id) {
        KbWorkspaceEntity ws = new KbWorkspaceEntity();
        ws.setId(id);
        ws.setWorkspaceKey(wsKey);
        return ws;
    }

    private MultipartFile mockMultipartFile(String filename) {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn(filename);
        when(file.getContentType()).thenReturn("text/markdown");
        when(file.getSize()).thenReturn(1024L);
        try {
            when(file.getInputStream()).thenReturn(new ByteArrayInputStream("test content".getBytes()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return file;
    }

    private FileValidationPolicy.ValidatedFile mockValidatedFile(String filename) {
        // 仅 stub 服务实际调用的方法：safeFilename、contentType、inputStream。
        // size/sha256 由 StoredArtifact 提供，ValidatedFile 不直接使用，避免 UnnecessaryStubbing。
        FileValidationPolicy.ValidatedFile mock = mock(FileValidationPolicy.ValidatedFile.class);
        when(mock.safeFilename()).thenReturn(filename);
        when(mock.contentType()).thenReturn("text/markdown");
        when(mock.inputStream()).thenReturn(new ByteArrayInputStream("test content".getBytes()));
        return mock;
    }

    private StoredArtifact mockStoredArtifact() {
        return new StoredArtifact(
                "workspace/kb/doc/version/original",
                "abc123def456",
                1024L,
                Map.of());
    }
}
