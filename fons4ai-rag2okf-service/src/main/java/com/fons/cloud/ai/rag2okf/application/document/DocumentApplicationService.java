package com.fons.cloud.ai.rag2okf.application.document;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fons.cloud.ai.rag2okf.common.dto.CurrentUserContext;
import com.fons.cloud.ai.rag2okf.common.dto.ModelBusinessKeyGenerator;
import com.fons.cloud.ai.rag2okf.common.dto.FileValidationPolicy;
import com.fons.cloud.ai.rag2okf.application.task.TaskApplicationService;
import com.fons.cloud.ai.rag2okf.application.parsing.ParseApplicationService;
import com.fons.cloud.ai.rag2okf.common.constants.WorkspaceRole;
import com.fons.cloud.ai.rag2okf.common.exeception.DocumentArtifactException;
import com.fons.cloud.ai.rag2okf.common.exeception.KnowledgeBaseConflictException;
import com.fons.cloud.ai.rag2okf.common.exeception.WorkspaceAccessDeniedException;
import com.fons.cloud.ai.rag2okf.common.response.DocumentDetailResponse;
import com.fons.cloud.ai.rag2okf.common.response.DocumentSummaryResponse;
import com.fons.cloud.ai.rag2okf.common.response.DocumentTaskSummaryResponse;
import com.fons.cloud.ai.rag2okf.common.response.DocumentUploadResponse;
import com.fons.cloud.ai.rag2okf.common.response.PageResponse;
import com.fons.cloud.ai.rag2okf.common.response.ParseTriggerResponse;
import com.fons.cloud.ai.rag2okf.common.dto.DocumentArtifactStore;
import com.fons.cloud.ai.rag2okf.common.dto.DocumentArtifactStore.ArtifactContent;
import com.fons.cloud.ai.rag2okf.common.dto.DocumentArtifactStore.ArtifactReference;
import com.fons.cloud.ai.rag2okf.common.dto.DocumentArtifactStore.ArtifactScope;
import com.fons.cloud.ai.rag2okf.common.dto.DocumentArtifactStore.ArtifactType;
import com.fons.cloud.ai.rag2okf.common.dto.DocumentArtifactStore.OriginalArtifactRequest;
import com.fons.cloud.ai.rag2okf.common.dto.DocumentArtifactStore.StoredArtifact;
import com.fons.cloud.ai.rag2okf.domain.entity.KbKnowledgeBaseEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbSourceDocumentEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbProcessingTaskEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbUserEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbWorkspaceEntity;
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
    private final KbWorkspaceDomainService workspaceDomainService;
    private final DocumentArtifactStore documentArtifactStore;
    private final FileValidationPolicy fileValidationPolicy;
    private final ModelBusinessKeyGenerator keyGenerator;
    private final TaskApplicationService taskApplicationService;
    private final ParseApplicationService parseApplicationService;

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
     * @param folderPath 文件夹路径，null 或空时默认为根级 /
     * @return 上传受理响应
     */
    public DocumentUploadResponse uploadDocument(String knowledgeBaseKey, MultipartFile file, String parseMode, String folderPath) {
        KbUserEntity user = currentUserContext.requireCurrentUser();
        KbKnowledgeBaseEntity knowledgeBase = requireKnowledgeBaseAccess(
                user.getUserKey(), knowledgeBaseKey, WorkspaceRole.ADMIN);
        KbWorkspaceEntity workspace = requireWorkspace(knowledgeBase.getWorkspaceId());

        FileValidationPolicy.ValidatedFile validatedFile = validateFile(file);
        String documentKey = keyGenerator.nextKey();
        String fileToken = keyGenerator.nextKey();
        String fileExtension = extractExtension(validatedFile.safeFilename());
        String safeFolderPath = sanitizeFolderPath(folderPath);

        // 上传到 MinIO（事务外，避免长事务占用数据库连接）
        ArtifactScope scope = new ArtifactScope(
                workspace.getWorkspaceKey(), knowledgeBaseKey, documentKey);
        StoredArtifact storedArtifact = documentArtifactStore.storeOriginal(new OriginalArtifactRequest(
                scope, validatedFile.safeFilename(),
                validatedFile.contentType(), validatedFile.inputStream()));

        // MySQL 事务创建 SourceDocument 并写入文件元数据
        DocumentUploadResponse response;
        try {
            response = self.persistNewDocument(
                    knowledgeBase, documentKey, fileToken, workspace.getId(), user.getId(),
                    validatedFile, fileExtension, storedArtifact, scope, safeFolderPath);
        } catch (RuntimeException e) {
            compensateDelete(scope, ArtifactType.ORIGINAL, documentKey, validatedFile.safeFilename());
            throw e;
        }
        return triggerParseAfterFileCommit(response, parseMode);
    }

    /**
     * 事务内创建 SourceDocument 并写入文件元数据。
     */
    @Transactional(rollbackFor = Exception.class)
    public DocumentUploadResponse persistNewDocument(
            KbKnowledgeBaseEntity knowledgeBase, String documentKey, String fileToken,
            Long workspaceId, Long uploadActorId,
            FileValidationPolicy.ValidatedFile validatedFile, String fileExtension,
            StoredArtifact storedArtifact, ArtifactScope scope, String folderPath) {

        KbSourceDocumentEntity document = new KbSourceDocumentEntity();
        document.setDocumentKey(documentKey);
        document.setKnowledgeBaseId(knowledgeBase.getId());
        document.setDisplayName(validatedFile.safeFilename());
        document.setFolderPath(folderPath);
        document.setObjectKey(storedArtifact.objectKey());
        document.setOriginalFilename(validatedFile.safeFilename());
        document.setContentType(validatedFile.contentType());
        document.setFileExtension(fileExtension);
        document.setSizeBytes(storedArtifact.sizeBytes());
        document.setSha256(storedArtifact.sha256());
        document.setFileToken(fileToken);
        document.setUploadActorId(uploadActorId);
        document.setParseStatus(PARSE_STATUS_NOT_STARTED);
        document.setPublishStatus(PUBLISH_STATUS_UNPUBLISHED);
        sourceDocumentDomainService.save(document);

        return toUploadResponse(document);
    }

    // ────────────────────────────── 更新文件 ──────────────────────────────

    /**
     * 更新文档的当前文件。使用 CAS 切换指针，失败时保留旧文件（技术设计 §5.3）。
     *
     * @param documentKey 文档业务标识
     * @param file 新文件
     * @param parseMode 解析模式
     * @param expectedCurrentFileToken 调用方持有的当前文件 CAS 令牌，用于乐观控制
     * @return 更新受理响应
     */
    public DocumentUploadResponse updateDocumentFile(
            String documentKey, MultipartFile file, String parseMode, String expectedCurrentFileToken) {
        KbUserEntity user = currentUserContext.requireCurrentUser();
        KbSourceDocumentEntity document = requireDocument(documentKey);
        KbKnowledgeBaseEntity knowledgeBase = requireKnowledgeBaseAccess(
                user.getUserKey(), document.getKnowledgeBaseId(), WorkspaceRole.ADMIN, documentKey);
        KbWorkspaceEntity workspace = requireWorkspace(knowledgeBase.getWorkspaceId());

        // CAS 前置校验：比较 expectedCurrentFileToken 与 document.fileToken
        if (document.getFileToken() == null || !document.getFileToken().equals(expectedCurrentFileToken)) {
            throw new KnowledgeBaseConflictException();
        }

        FileValidationPolicy.ValidatedFile validatedFile = validateFile(file);
        String newFileToken = keyGenerator.nextKey();
        String fileExtension = extractExtension(validatedFile.safeFilename());

        // 上传新文件到 MinIO（事务外）
        ArtifactScope scope = new ArtifactScope(
                workspace.getWorkspaceKey(), knowledgeBase.getKnowledgeBaseKey(), documentKey);
        StoredArtifact storedArtifact = documentArtifactStore.storeOriginal(new OriginalArtifactRequest(
                scope, validatedFile.safeFilename(),
                validatedFile.contentType(), validatedFile.inputStream()));

        // 记录旧 objectKey 和旧文件名，事务成功后删除旧对象
        String oldObjectKey = document.getObjectKey();
        String oldOriginalFilename = document.getOriginalFilename();

        // MySQL 事务内 CAS 更新文件元数据
        DocumentUploadResponse response;
        try {
            response = self.persistUpdatedDocument(
                    document, newFileToken, user.getId(),
                    validatedFile, fileExtension, storedArtifact);
            // 事务成功后删除旧 MinIO 对象（新旧 objectKey 不同时才删除）
            if (oldObjectKey != null && !oldObjectKey.equals(storedArtifact.objectKey())) {
                compensateDelete(scope, ArtifactType.ORIGINAL, documentKey, oldOriginalFilename);
            }
        } catch (RuntimeException e) {
            // 事务失败，补偿删除刚上传的新对象（新旧 objectKey 不同时才删除）
            if (oldObjectKey == null || !oldObjectKey.equals(storedArtifact.objectKey())) {
                compensateDelete(scope, ArtifactType.ORIGINAL, documentKey, validatedFile.safeFilename());
            }
            throw e;
        }
        return triggerParseAfterFileCommit(response, parseMode);
    }

    /**
     * 事务内直接覆盖 document 上的文件元数据并 CAS 更新。
     * 不修改 activePublicationRevisionId，旧发布内容继续可用（技术设计 §5.3 第 4 步）。
     */
    @Transactional(rollbackFor = Exception.class)
    public DocumentUploadResponse persistUpdatedDocument(
            KbSourceDocumentEntity document, String newFileToken, Long uploadActorId,
            FileValidationPolicy.ValidatedFile validatedFile, String fileExtension,
            StoredArtifact storedArtifact) {

        // 直接覆盖文件字段
        document.setObjectKey(storedArtifact.objectKey());
        document.setOriginalFilename(validatedFile.safeFilename());
        document.setContentType(validatedFile.contentType());
        document.setFileExtension(fileExtension);
        document.setSizeBytes(storedArtifact.sizeBytes());
        document.setSha256(storedArtifact.sha256());
        document.setFileToken(newFileToken);
        document.setUploadActorId(uploadActorId);
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

        return toUploadResponse(document);
    }

    // ────────────────────────────── 文档列表与详情 ──────────────────────────────

    /**
     * 分页查询知识库下的当前文档视图，不返回历史版本。
     *
     * @param knowledgeBaseKey 知识库业务标识
     * @param page 页码（从 0 开始）
     * @param size 每页条数
     * @param folderPath 文件夹路径筛选，null 时不按文件夹过滤
     */
    public PageResponse<DocumentSummaryResponse> listDocuments(
            String knowledgeBaseKey, int page, int size, String folderPath) {
        KbUserEntity user = currentUserContext.requireCurrentUser();
        KbKnowledgeBaseEntity knowledgeBase = requireKnowledgeBaseAccess(
                user.getUserKey(), knowledgeBaseKey, WorkspaceRole.KNOWLEDGE_USER);
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        Page<KbSourceDocumentEntity> result = new Page<>(safePage + 1L, safeSize);
        var query = Wrappers.<KbSourceDocumentEntity>lambdaQuery()
                .eq(KbSourceDocumentEntity::getKnowledgeBaseId, knowledgeBase.getId());
        if (folderPath != null && !folderPath.isBlank()) {
            query.eq(KbSourceDocumentEntity::getFolderPath, folderPath);
        }
        query.orderByDesc(KbSourceDocumentEntity::getUpdated);
        sourceDocumentDomainService.page(result, query);
        List<KbSourceDocumentEntity> documents = result.getRecords();
        Map<Long, KbProcessingTaskEntity> latestTasks = taskApplicationService.findLatestByDocumentIds(
                documents.stream().map(KbSourceDocumentEntity::getId).toList());
        return new PageResponse<>(documents.stream()
                .map(document -> toSummaryResponse(document, latestTasks.get(document.getId())))
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

        KbProcessingTaskEntity latestTask = taskApplicationService.findLatestByDocumentIds(List.of(document.getId())).get(document.getId());

        return new DocumentDetailResponse(
                document.getDocumentKey(),
                knowledgeBase.getKnowledgeBaseKey(),
                document.getDisplayName(),
                document.getFolderPath(),
                new DocumentDetailResponse.CurrentFileSummary(
                        document.getOriginalFilename(),
                        document.getContentType(),
                        document.getSizeBytes()),
                document.getFileToken(),
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

        ArtifactScope scope = new ArtifactScope(
                workspace.getWorkspaceKey(), knowledgeBase.getKnowledgeBaseKey(), documentKey);
        ArtifactContent content = documentArtifactStore.open(new ArtifactReference(
                scope, ArtifactType.ORIGINAL, documentKey, document.getOriginalFilename()));

        return new DocumentFileContent(
                document.getOriginalFilename(),
                document.getContentType(),
                document.getSizeBytes(),
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

    private void compensateDelete(ArtifactScope scope, ArtifactType type, String revisionKey, String detail) {
        try {
            documentArtifactStore.delete(new ArtifactReference(scope, type, revisionKey, detail));
        } catch (Exception e) {
            // 补偿删除失败时记录日志，不抛出异常以避免掩盖原始错误
            log.warn("补偿删除对象存储失败 scope={} type={} revisionKey={}", scope, type, revisionKey, e);
        }
    }

    /**
     * 规范化文件夹路径。null/空/blank 返回根级 "/"。
     * 不允许包含 ".." 和 null 字符，防止路径穿越（CR-014 Quality）。
     */
    private String sanitizeFolderPath(String folderPath) {
        if (folderPath == null || folderPath.isBlank()) {
            return "/";
        }
        String trimmed = folderPath.trim();
        if (trimmed.contains("..") || trimmed.indexOf('\0') >= 0) {
            throw new DocumentArtifactException("非法文件夹路径");
        }
        if (!trimmed.startsWith("/")) {
            trimmed = "/" + trimmed;
        }
        if (trimmed.length() > 512) {
            throw new DocumentArtifactException("文件夹路径过长");
        }
        return trimmed;
    }

    // ────────────────────────────── 批量上传 ──────────────────────────────

    /**
     * 批量上传文档。每个文件独立处理，部分失败时已成功文件不回滚（CR-014）。
     *
     * @param knowledgeBaseKey 知识库业务标识
     * @param files 上传文件列表
     * @param relativePaths 每个文件的相对路径（用于推导 folderPath），可为 null
     * @param parseMode 解析模式
     * @return 每个文件的上传结果
     */
    public List<DocumentUploadResponse> batchUploadDocuments(
            String knowledgeBaseKey, List<MultipartFile> files,
            List<String> relativePaths, String parseMode) {
        if (files == null || files.isEmpty()) {
            throw new DocumentArtifactException("批量上传文件列表为空");
        }
        if (files.size() > 50) {
            throw new DocumentArtifactException("批量上传单次最多 50 个文件");
        }
        long totalSize = 0;
        for (MultipartFile file : files) {
            totalSize += file.getSize();
        }
        if (totalSize > 200L * 1024 * 1024) {
            throw new DocumentArtifactException("批量上传总大小超过 200MB 限制");
        }

        List<DocumentUploadResponse> results = new java.util.ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            String folderPath = "/";
            if (relativePaths != null && i < relativePaths.size() && relativePaths.get(i) != null) {
                folderPath = deriveFolderPath(relativePaths.get(i));
            }
            try {
                results.add(uploadDocument(knowledgeBaseKey, file, parseMode, folderPath));
            } catch (RuntimeException e) {
                log.warn("批量上传文件失败: filename={}, error={}", file.getOriginalFilename(), e.getMessage());
                throw e;
            }
        }
        return results;
    }

    /**
     * 从相对路径推导文件夹路径。例如 "subdir/file.pdf" -> "/subdir"，
     * "file.pdf" -> "/"，"a/b/c/file.pdf" -> "/a/b/c"。
     */
    private String deriveFolderPath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return "/";
        }
        String normalized = relativePath.replace('\\', '/');
        int lastSlash = normalized.lastIndexOf('/');
        if (lastSlash <= 0) {
            return "/";
        }
        String dir = normalized.substring(0, lastSlash);
        return sanitizeFolderPath(dir);
    }

    private String extractExtension(String filename) {
        int separator = filename.lastIndexOf('.');
        if (separator <= 0 || separator == filename.length() - 1) {
            throw new DocumentArtifactException();
        }
        return filename.substring(separator + 1).toLowerCase(Locale.ROOT);
    }

    private DocumentUploadResponse toUploadResponse(KbSourceDocumentEntity document) {
        return new DocumentUploadResponse(
                document.getDocumentKey(),
                document.getDisplayName(),
                document.getFolderPath(),
                new DocumentUploadResponse.CurrentFileSummary(
                        document.getOriginalFilename(),
                        document.getContentType(),
                        document.getSizeBytes()),
                document.getFileToken(),
                null, // T009 不创建任务，taskKey 为 null
                document.getParseStatus(),
                document.getPublishStatus());
    }

    private DocumentSummaryResponse toSummaryResponse(
            KbSourceDocumentEntity document, KbProcessingTaskEntity latestTask) {
        return new DocumentSummaryResponse(document.getDocumentKey(), document.getDisplayName(),
                document.getFolderPath(),
                new DocumentSummaryResponse.CurrentFileSummary(document.getOriginalFilename(), document.getContentType(), document.getSizeBytes()),
                document.getFileToken(), document.getParseStatus(), document.getPublishStatus(),
                document.getActivePublicationRevisionId() != null, toTaskSummary(latestTask), document.getUpdated());
    }

    /**
     * 文件元数据事务提交后再编排解析，避免解析失败触发对象存储补偿并破坏已提交文档。
     */
    private DocumentUploadResponse triggerParseAfterFileCommit(
            DocumentUploadResponse response, String parseMode) {
        if ("SKIP".equalsIgnoreCase(parseMode)) {
            return response;
        }
        ParseTriggerResponse parse = parseApplicationService.triggerParse(
                response.documentKey(), parseMode != null ? parseMode : "DEFAULT");
        return new DocumentUploadResponse(
                response.documentKey(), response.displayName(), response.folderPath(),
                response.currentFile(), response.currentFileToken(), parse.taskKey(),
                parse.parseStatus(), parse.publishStatus());
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
