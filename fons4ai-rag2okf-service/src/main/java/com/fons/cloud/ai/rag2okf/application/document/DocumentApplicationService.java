package com.fons.cloud.ai.rag2okf.application.document;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fons.cloud.ai.rag2okf.application.model.CurrentUserContext;
import com.fons.cloud.ai.rag2okf.application.model.ModelBusinessKeyGenerator;
import com.fons.cloud.ai.rag2okf.application.task.TaskApplicationService;
import com.fons.cloud.ai.rag2okf.common.constants.WorkspaceRole;
import com.fons.cloud.ai.rag2okf.common.exeception.DocumentArtifactException;
import com.fons.cloud.ai.rag2okf.common.exeception.KnowledgeBaseConflictException;
import com.fons.cloud.ai.rag2okf.common.exeception.WorkspaceAccessDeniedException;
import com.fons.cloud.ai.rag2okf.common.response.DocumentDetailResponse;
import com.fons.cloud.ai.rag2okf.common.response.DocumentSummaryResponse;
import com.fons.cloud.ai.rag2okf.common.response.DocumentTaskSummaryResponse;
import com.fons.cloud.ai.rag2okf.common.response.DocumentUploadResponse;
import com.fons.cloud.ai.rag2okf.common.response.PageResponse;
import com.fons.cloud.ai.rag2okf.domain.artifact.DocumentArtifactStore;
import com.fons.cloud.ai.rag2okf.domain.artifact.DocumentArtifactStore.ArtifactContent;
import com.fons.cloud.ai.rag2okf.domain.artifact.DocumentArtifactStore.ArtifactReference;
import com.fons.cloud.ai.rag2okf.domain.artifact.DocumentArtifactStore.ArtifactScope;
import com.fons.cloud.ai.rag2okf.domain.artifact.DocumentArtifactStore.ArtifactType;
import com.fons.cloud.ai.rag2okf.domain.artifact.DocumentArtifactStore.OriginalArtifactRequest;
import com.fons.cloud.ai.rag2okf.domain.artifact.DocumentArtifactStore.StoredArtifact;
import com.fons.cloud.ai.rag2okf.domain.entity.KbDocumentVersionEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbKnowledgeBaseEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbSourceDocumentEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbProcessingTaskEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbUserEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbWorkspaceEntity;
import com.fons.cloud.ai.rag2okf.domain.service.KbDocumentVersionDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.KbKnowledgeBaseDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.KbSourceDocumentDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.KbWorkspaceDomainService;
import com.fons.cloud.ai.rag2okf.infrastructure.identity.WorkspaceAccessPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

/**
 * 文档上传、更新文件与原文件访问的应用服务。
 *
 * <p>遵循 DDD-lite：应用服务负责编排、权限校验、事务边界和 MinIO/MySQL 显式补偿。
 * 文件验证委托给 {@link FileValidationPolicy}，对象存储委托给 {@link DocumentArtifactStore}。
 * 不得把对象存储当业务事实来源（技术设计 §5.2 Quality）。</p>
 *
 * <p>P0 不创建解析任务（由 T012 负责），上传后 parseStatus 统一为 NOT_STARTED。
 * 响应不返回版本序号、版本列表或回退操作（D-004）。</p>
 *
 * @author hongqy
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentApplicationService {

    private static final String PARSE_STATUS_NOT_STARTED = "NOT_STARTED";
    private static final String PUBLISH_STATUS_UNPUBLISHED = "UNPUBLISHED";

    private final CurrentUserContext currentUserContext;
    private final WorkspaceAccessPolicy workspaceAccessPolicy;
    private final KbKnowledgeBaseDomainService knowledgeBaseDomainService;
    private final KbSourceDocumentDomainService sourceDocumentDomainService;
    private final KbDocumentVersionDomainService documentVersionDomainService;
    private final KbWorkspaceDomainService workspaceDomainService;
    private final DocumentArtifactStore documentArtifactStore;
    private final FileValidationPolicy fileValidationPolicy;
    private final ModelBusinessKeyGenerator keyGenerator;
    private final TaskApplicationService taskApplicationService;

    /** 通过 @Lazy 自引用触发 Spring AOP 代理，使 @Transactional 在同类内部调用时生效。 */
    @Lazy
    @org.springframework.beans.factory.annotation.Autowired
    private DocumentApplicationService self;

    // ────────────────────────────── 上传新文档 ──────────────────────────────

    /**
     * 上传新文档。同名文件不查询、不合并，天然生成新的 documentKey（技术设计 §5.2 第 8 步）。
     *
     * @param knowledgeBaseKey 知识库业务标识
     * @param file 上传文件
     * @param parseMode 解析模式：DEFAULT、PARSE 或 SKIP
     * @return 上传受理响应
     */
    public DocumentUploadResponse uploadDocument(String knowledgeBaseKey, MultipartFile file, String parseMode) {
        KbUserEntity user = currentUserContext.requireCurrentUser();
        KbKnowledgeBaseEntity knowledgeBase = requireKnowledgeBaseAccess(
                user.getUserKey(), knowledgeBaseKey, WorkspaceRole.ADMIN);
        KbWorkspaceEntity workspace = requireWorkspace(knowledgeBase.getWorkspaceId());

        FileValidationPolicy.ValidatedFile validatedFile = validateFile(file);
        String documentKey = keyGenerator.nextKey();
        String versionKey = keyGenerator.nextKey();
        String fileExtension = extractExtension(validatedFile.safeFilename());

        // 上传到 MinIO（事务外，避免长事务占用数据库连接）
        ArtifactScope scope = new ArtifactScope(
                workspace.getWorkspaceKey(), knowledgeBaseKey, documentKey);
        StoredArtifact storedArtifact = documentArtifactStore.storeOriginal(new OriginalArtifactRequest(
                scope, versionKey, validatedFile.safeFilename(),
                validatedFile.contentType(), validatedFile.inputStream()));

        // MySQL 事务创建 SourceDocument、DocumentVersion 并设置指针
        try {
            return self.persistNewDocument(
                    knowledgeBase, documentKey, versionKey, workspace.getId(), user.getId(),
                    validatedFile, fileExtension, storedArtifact, scope);
        } catch (RuntimeException e) {
            compensateDelete(scope, ArtifactType.ORIGINAL, versionKey);
            throw e;
        }
    }

    /**
     * 事务内创建 SourceDocument 和 DocumentVersion，并设置当前文件指针。
     */
    @Transactional(rollbackFor = Exception.class)
    public DocumentUploadResponse persistNewDocument(
            KbKnowledgeBaseEntity knowledgeBase, String documentKey, String versionKey,
            Long workspaceId, Long uploadActorId,
            FileValidationPolicy.ValidatedFile validatedFile, String fileExtension,
            StoredArtifact storedArtifact, ArtifactScope scope) {

        KbSourceDocumentEntity document = new KbSourceDocumentEntity();
        document.setDocumentKey(documentKey);
        document.setKnowledgeBaseId(knowledgeBase.getId());
        document.setDisplayName(validatedFile.safeFilename());
        document.setParseStatus(PARSE_STATUS_NOT_STARTED);
        document.setPublishStatus(PUBLISH_STATUS_UNPUBLISHED);
        sourceDocumentDomainService.save(document);

        KbDocumentVersionEntity version = new KbDocumentVersionEntity();
        version.setVersionKey(versionKey);
        version.setSourceDocumentId(document.getId());
        version.setObjectKey(storedArtifact.objectKey());
        version.setOriginalFilename(validatedFile.safeFilename());
        version.setContentType(validatedFile.contentType());
        version.setFileExtension(fileExtension);
        version.setSizeBytes(storedArtifact.sizeBytes());
        version.setSha256(storedArtifact.sha256());
        version.setUploadActorId(uploadActorId);
        documentVersionDomainService.save(version);

        // 设置当前文件指针
        document.setCurrentDocumentVersionId(version.getId());
        boolean updated = sourceDocumentDomainService.updateById(document);
        if (!updated) {
            throw new KnowledgeBaseConflictException();
        }

        return toUploadResponse(document, version);
    }

    // ────────────────────────────── 更新文件 ──────────────────────────────

    /**
     * 更新文档的当前文件。使用 CAS 切换指针，失败时保留旧文件（技术设计 §5.3）。
     *
     * @param documentKey 文档业务标识
     * @param file 新文件
     * @param parseMode 解析模式
     * @param expectedCurrentVersionKey 调用方持有的当前版本 key，用于乐观控制
     * @return 更新受理响应
     */
    public DocumentUploadResponse updateDocumentFile(
            String documentKey, MultipartFile file, String parseMode, String expectedCurrentFileToken) {
        KbUserEntity user = currentUserContext.requireCurrentUser();
        KbSourceDocumentEntity document = requireDocument(documentKey);
        KbKnowledgeBaseEntity knowledgeBase = requireKnowledgeBaseAccess(
                user.getUserKey(), document.getKnowledgeBaseId(), WorkspaceRole.ADMIN, documentKey);
        KbWorkspaceEntity workspace = requireWorkspace(knowledgeBase.getWorkspaceId());

        // 校验 expectedCurrentVersionKey 与当前指针（CAS 前置校验）
        KbDocumentVersionEntity currentVersion = documentVersionDomainService.getById(
                document.getCurrentDocumentVersionId());
        if (currentVersion == null || !currentVersion.getVersionKey().equals(expectedCurrentFileToken)) {
            throw new KnowledgeBaseConflictException();
        }

        FileValidationPolicy.ValidatedFile validatedFile = validateFile(file);
        String newVersionKey = keyGenerator.nextKey();
        String fileExtension = extractExtension(validatedFile.safeFilename());

        // 上传新版本到 MinIO（事务外）
        ArtifactScope scope = new ArtifactScope(
                workspace.getWorkspaceKey(), knowledgeBase.getKnowledgeBaseKey(), documentKey);
        StoredArtifact storedArtifact = documentArtifactStore.storeOriginal(new OriginalArtifactRequest(
                scope, newVersionKey, validatedFile.safeFilename(),
                validatedFile.contentType(), validatedFile.inputStream()));

        // MySQL 事务内 CAS 切换指针
        try {
            return self.persistUpdatedDocument(
                    document, newVersionKey, user.getId(),
                    validatedFile, fileExtension, storedArtifact);
        } catch (RuntimeException e) {
            compensateDelete(scope, ArtifactType.ORIGINAL, newVersionKey);
            throw e;
        }
    }

    /**
     * 事务内创建新 DocumentVersion 并 CAS 切换当前文件指针。
     * 不修改 activePublicationRevisionId，旧发布内容继续可用（技术设计 §5.3 第 4 步）。
     */
    @Transactional(rollbackFor = Exception.class)
    public DocumentUploadResponse persistUpdatedDocument(
            KbSourceDocumentEntity document, String newVersionKey, Long uploadActorId,
            FileValidationPolicy.ValidatedFile validatedFile, String fileExtension,
            StoredArtifact storedArtifact) {

        KbDocumentVersionEntity newVersion = new KbDocumentVersionEntity();
        newVersion.setVersionKey(newVersionKey);
        newVersion.setSourceDocumentId(document.getId());
        newVersion.setObjectKey(storedArtifact.objectKey());
        newVersion.setOriginalFilename(validatedFile.safeFilename());
        newVersion.setContentType(validatedFile.contentType());
        newVersion.setFileExtension(fileExtension);
        newVersion.setSizeBytes(storedArtifact.sizeBytes());
        newVersion.setSha256(storedArtifact.sha256());
        newVersion.setUploadActorId(uploadActorId);
        documentVersionDomainService.save(newVersion);

        // CAS 切换：使用乐观锁 version
        document.setCurrentDocumentVersionId(newVersion.getId());
        document.setDisplayName(validatedFile.safeFilename());
        // 更新文件时清空解析和分块指针，设置新处理状态
        document.setCurrentParseRevisionId(null);
        document.setCurrentChunkRevisionId(null);
        document.setParseStatus(PARSE_STATUS_NOT_STARTED);
        document.setPublishStatus(PUBLISH_STATUS_UNPUBLISHED);
        // activePublicationRevisionId 不修改，旧发布继续可用
        boolean updated = sourceDocumentDomainService.updateById(document);
        if (!updated) {
            throw new KnowledgeBaseConflictException();
        }

        return toUploadResponse(document, newVersion);
    }

    // ────────────────────────────── 文档列表与详情 ──────────────────────────────

    /** 分页查询知识库下的当前文档视图，不返回历史版本。 */
    public PageResponse<DocumentSummaryResponse> listDocuments(String knowledgeBaseKey, int page, int size) {
        KbUserEntity user = currentUserContext.requireCurrentUser();
        KbKnowledgeBaseEntity knowledgeBase = requireKnowledgeBaseAccess(
                user.getUserKey(), knowledgeBaseKey, WorkspaceRole.KNOWLEDGE_USER);
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        Page<KbSourceDocumentEntity> result = new Page<>(safePage + 1L, safeSize);
        sourceDocumentDomainService.page(result,
                Wrappers.<KbSourceDocumentEntity>lambdaQuery()
                        .eq(KbSourceDocumentEntity::getKnowledgeBaseId, knowledgeBase.getId())
                        .orderByDesc(KbSourceDocumentEntity::getUpdated));
        List<KbSourceDocumentEntity> documents = result.getRecords();
        Map<Long, KbDocumentVersionEntity> versions = currentVersions(documents);
        Map<Long, KbProcessingTaskEntity> latestTasks = taskApplicationService.findLatestByDocumentIds(
                documents.stream().map(KbSourceDocumentEntity::getId).toList());
        return new PageResponse<>(documents.stream()
                .map(document -> toSummaryResponse(document, versions.get(document.getCurrentDocumentVersionId()), latestTasks.get(document.getId())))
                .toList(), result.getTotal(), safePage, safeSize);
    }

    /**
     * 查询文档详情。需要 USER 权限。不返回版本列表（D-004）。
     *
     * @param documentKey 文档业务标识
     * @return 文档详情响应
     */
    public DocumentDetailResponse getDocumentDetail(String documentKey) {
        KbUserEntity user = currentUserContext.requireCurrentUser();
        KbSourceDocumentEntity document = requireDocument(documentKey);
        KbKnowledgeBaseEntity knowledgeBase = requireKnowledgeBase(
                document.getKnowledgeBaseId());
        requireWorkspaceAccess(
                user.getUserKey(), knowledgeBase.getWorkspaceId(), WorkspaceRole.KNOWLEDGE_USER);

        KbDocumentVersionEntity currentVersion = documentVersionDomainService.getById(
                document.getCurrentDocumentVersionId());
        KbProcessingTaskEntity latestTask = taskApplicationService.findLatestByDocumentIds(List.of(document.getId())).get(document.getId());

        return new DocumentDetailResponse(
                document.getDocumentKey(),
                knowledgeBase.getKnowledgeBaseKey(),
                document.getDisplayName(),
                new DocumentDetailResponse.CurrentFileSummary(
                        currentVersion.getOriginalFilename(),
                        currentVersion.getContentType(),
                        currentVersion.getSizeBytes()),
                currentVersion.getVersionKey(),
                document.getParseStatus(),
                document.getPublishStatus(),
                document.getActivePublicationRevisionId() != null,
                toTaskSummary(latestTask),
                document.getUpdated()
        );
    }

    // ────────────────────────────── 原文件下载 ──────────────────────────────

    /**
     * 打开文档当前原文件的读取流。需要 USER 权限。调用方负责关闭流。
     *
     * @param documentKey 文档业务标识
     * @return 文件内容与元数据
     */
    public DocumentFileContent downloadDocumentFile(String documentKey) {
        KbUserEntity user = currentUserContext.requireCurrentUser();
        KbSourceDocumentEntity document = requireDocument(documentKey);
        KbKnowledgeBaseEntity knowledgeBase = requireKnowledgeBase(
                document.getKnowledgeBaseId());
        KbWorkspaceEntity workspace = requireWorkspaceAccess(
                user.getUserKey(), knowledgeBase.getWorkspaceId(), WorkspaceRole.KNOWLEDGE_USER);
        KbDocumentVersionEntity currentVersion = documentVersionDomainService.getById(
                document.getCurrentDocumentVersionId());
        if (currentVersion == null) {
            throw new DocumentArtifactException();
        }

        ArtifactScope scope = new ArtifactScope(
                workspace.getWorkspaceKey(), knowledgeBase.getKnowledgeBaseKey(), documentKey);
        ArtifactContent content = documentArtifactStore.open(new ArtifactReference(
                scope, ArtifactType.ORIGINAL, currentVersion.getVersionKey()));

        return new DocumentFileContent(
                currentVersion.getOriginalFilename(),
                currentVersion.getContentType(),
                currentVersion.getSizeBytes(),
                content.inputStream());
    }

    // ────────────────────────────── 辅助方法 ──────────────────────────────

    private FileValidationPolicy.ValidatedFile validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new DocumentArtifactException();
        }
        try {
            return fileValidationPolicy.validate(
                    file.getOriginalFilename(),
                    file.getContentType(),
                    file.getInputStream(),
                    file.getSize());
        } catch (IOException e) {
            throw new DocumentArtifactException(e);
        }
    }

    private KbKnowledgeBaseEntity requireKnowledgeBaseAccess(
            String userKey, String knowledgeBaseKey, WorkspaceRole requiredRole) {
        KbKnowledgeBaseEntity knowledgeBase = knowledgeBaseDomainService.getOne(
                Wrappers.<KbKnowledgeBaseEntity>lambdaQuery()
                        .eq(KbKnowledgeBaseEntity::getKnowledgeBaseKey, knowledgeBaseKey));
        if (knowledgeBase == null) {
            throw new WorkspaceAccessDeniedException();
        }
        workspaceAccessPolicy.checkAccess(userKey,
                resolveWorkspaceKey(knowledgeBase.getWorkspaceId()), requiredRole);
        return knowledgeBase;
    }

    /** 通过知识库主键校验访问权限的重载，用于文档更新/下载场景。 */
    private KbKnowledgeBaseEntity requireKnowledgeBaseAccess(
            String userKey, Long knowledgeBaseId, WorkspaceRole requiredRole, String documentKey) {
        KbKnowledgeBaseEntity knowledgeBase = requireKnowledgeBase(knowledgeBaseId);
        workspaceAccessPolicy.checkAccess(userKey,
                resolveWorkspaceKey(knowledgeBase.getWorkspaceId()), requiredRole);
        return knowledgeBase;
    }

    private KbKnowledgeBaseEntity requireKnowledgeBase(Long knowledgeBaseId) {
        KbKnowledgeBaseEntity knowledgeBase = knowledgeBaseDomainService.getById(knowledgeBaseId);
        if (knowledgeBase == null) {
            throw new WorkspaceAccessDeniedException();
        }
        return knowledgeBase;
    }

    private KbSourceDocumentEntity requireDocument(String documentKey) {
        KbSourceDocumentEntity document = sourceDocumentDomainService.getOne(
                Wrappers.<KbSourceDocumentEntity>lambdaQuery()
                        .eq(KbSourceDocumentEntity::getDocumentKey, documentKey));
        if (document == null) {
            throw new WorkspaceAccessDeniedException();
        }
        return document;
    }

    private KbWorkspaceEntity requireWorkspace(Long workspaceId) {
        KbWorkspaceEntity workspace = workspaceDomainService.getById(workspaceId);
        if (workspace == null) {
            throw new WorkspaceAccessDeniedException();
        }
        return workspace;
    }

    private KbWorkspaceEntity requireWorkspaceAccess(
            String userKey, Long workspaceId, WorkspaceRole requiredRole) {
        KbWorkspaceEntity workspace = requireWorkspace(workspaceId);
        workspaceAccessPolicy.checkAccess(userKey, workspace.getWorkspaceKey(), requiredRole);
        return workspace;
    }

    private String resolveWorkspaceKey(Long workspaceId) {
        KbWorkspaceEntity workspace = requireWorkspace(workspaceId);
        return workspace.getWorkspaceKey();
    }

    private void compensateDelete(ArtifactScope scope, ArtifactType type, String revisionKey) {
        try {
            documentArtifactStore.delete(new ArtifactReference(scope, type, revisionKey));
        } catch (Exception e) {
            // 补偿删除失败时记录日志，不抛出异常以避免掩盖原始错误
            log.warn("补偿删除对象存储失败 scope={} type={} revisionKey={}", scope, type, revisionKey, e);
        }
    }

    private String extractExtension(String filename) {
        int separator = filename.lastIndexOf('.');
        if (separator <= 0 || separator == filename.length() - 1) {
            throw new DocumentArtifactException();
        }
        return filename.substring(separator + 1).toLowerCase(Locale.ROOT);
    }

    private DocumentUploadResponse toUploadResponse(
            KbSourceDocumentEntity document, KbDocumentVersionEntity version) {
        return new DocumentUploadResponse(
                document.getDocumentKey(),
                document.getDisplayName(),
                new DocumentUploadResponse.CurrentFileSummary(
                        version.getOriginalFilename(),
                        version.getContentType(),
                        version.getSizeBytes()),
                version.getVersionKey(),
                null, // T009 不创建任务，taskKey 为 null
                document.getParseStatus(),
                document.getPublishStatus());
    }

    private Map<Long, KbDocumentVersionEntity> currentVersions(Collection<KbSourceDocumentEntity> documents) {
        List<Long> versionIds = documents.stream().map(KbSourceDocumentEntity::getCurrentDocumentVersionId)
                .filter(java.util.Objects::nonNull).toList();
        if (versionIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, KbDocumentVersionEntity> versions = new HashMap<>();
        documentVersionDomainService.listByIds(versionIds).forEach(version -> versions.put(version.getId(), version));
        return versions;
    }

    private DocumentSummaryResponse toSummaryResponse(
            KbSourceDocumentEntity document, KbDocumentVersionEntity version, KbProcessingTaskEntity latestTask) {
        if (version == null) {
            throw new DocumentArtifactException();
        }
        return new DocumentSummaryResponse(document.getDocumentKey(), document.getDisplayName(),
                new DocumentSummaryResponse.CurrentFileSummary(version.getOriginalFilename(), version.getContentType(), version.getSizeBytes()),
                version.getVersionKey(), document.getParseStatus(), document.getPublishStatus(),
                document.getActivePublicationRevisionId() != null, toTaskSummary(latestTask), document.getUpdated());
    }

    private DocumentTaskSummaryResponse toTaskSummary(KbProcessingTaskEntity task) {
        if (task == null) {
            return null;
        }
        return new DocumentTaskSummaryResponse(task.getTaskKey(), task.getTaskType(), task.getStatus(), task.getStage(),
                task.getProgress(), task.getAttempt(), task.getMaxAttempts(), task.getErrorCode(), task.getErrorMessage(), task.getUpdated());
    }

    /**
     * 文件下载内容，调用方必须关闭 inputStream。
     *
     * @param filename 文件名
     * @param contentType MIME 类型
     * @param size 文件字节数
     * @param inputStream 文件读取流
     */
    public record DocumentFileContent(
            String filename,
            String contentType,
            long size,
            InputStream inputStream) {
    }
}
