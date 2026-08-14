package com.fons.cloud.ai.rag2okf.application.parsing;

import com.fons.cloud.ai.rag2okf.common.constants.Rag2OkfResultCode;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fons.cloud.ai.rag2okf.infrastructure.adapter.user.SaTokenCurrentUserContext;
import com.fons.cloud.ai.rag2okf.common.dto.ParseTaskPayload;
import com.fons.cloud.ai.rag2okf.application.task.TaskApplicationService;
import com.fons.cloud.ai.rag2okf.common.constants.user.WorkspaceRole;
import com.fons.cloud.ai.rag2okf.common.exeception.DocumentArtifactException;
import com.fons.cloud.ai.rag2okf.common.exception.knowledgebase.KnowledgeBaseConflictException;
import com.fons.cloud.ai.rag2okf.common.exeception.TaskExecutionException;
import com.fons.cloud.ai.rag2okf.common.exception.user.WorkspaceAccessDeniedException;
import com.fons.cloud.ai.rag2okf.common.response.ChunkPreviewResponse;
import com.fons.cloud.ai.rag2okf.common.response.ChunkPreviewResponse.ChunkView;
import com.fons.cloud.ai.rag2okf.common.response.ParsePreviewResponse;
import com.fons.cloud.ai.rag2okf.common.response.ParsePreviewResponse.ParsedBlockView;
import com.fons.cloud.ai.rag2okf.common.response.ParseTriggerResponse;
import com.fons.cloud.ai.rag2okf.common.dto.DocumentArtifactStore;
import com.fons.cloud.ai.rag2okf.common.dto.DocumentArtifactStore.ArtifactContent;
import com.fons.cloud.ai.rag2okf.common.dto.DocumentArtifactStore.ArtifactReference;
import com.fons.cloud.ai.rag2okf.common.dto.DocumentArtifactStore.ArtifactScope;
import com.fons.cloud.ai.rag2okf.common.dto.DocumentArtifactStore.ArtifactType;
import com.fons.cloud.ai.rag2okf.domain.entity.KbChunkRevisionEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.knowledgebase.KbKnowledgeBase;
import com.fons.cloud.ai.rag2okf.domain.entity.KbParseRevisionEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbSourceDocumentEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.user.KbUser;
import com.fons.cloud.ai.rag2okf.domain.entity.user.KbWorkspace;
import com.fons.cloud.ai.rag2okf.common.dto.ChunkManifest;
import com.fons.cloud.ai.rag2okf.common.dto.ParsingChunkProfile;
import com.fons.cloud.ai.rag2okf.common.dto.ParseManifest;
import com.fons.cloud.ai.rag2okf.domain.service.KbChunkRevisionDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.knowledgebase.KbKnowledgeBaseDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.KbParseRevisionDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.KbSourceDocumentDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.user.KbWorkspaceDomainService;
import com.fons.cloud.ai.rag2okf.common.dto.TaskType;
import com.fons.cloud.ai.rag2okf.common.dto.TaskStatus;
import com.fons.cloud.ai.rag2okf.infrastructure.support.user.WorkspaceAccessPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 文档解析应用服务：触发解析、预览解析产物、预览分块和失败重试。
 *
 * <p>遵循 DDD-lite：应用服务负责编排、权限校验和事务边界；
 * 解析和分块执行由 {@code ParseTaskExecutor} 在任务执行器中完成。
 *
 * <p>手动触发与自动触发共用 {@link #triggerParse} 流程（AC-006）。
 * SKIP 模式不创建任务，parseStatus 保持 NOT_STARTED（AC-013 不伪造结果）。
 *
 * @author hongqy
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParseApplicationService {

    private static final String PARSE_STATUS_QUEUED = "QUEUED";
    private static final String PARSE_STATUS_NOT_STARTED = "NOT_STARTED";
    /** 解析执行中：任务开始执行时写入，前端据此继续轮询。 */
    public static final String PARSE_STATUS_RUNNING = "RUNNING";
    /** 解析失败：任务终态失败时写入，前端据此停止轮询并展示重试入口。 */
    public static final String PARSE_STATUS_FAILED = "FAILED";
    private static final String DEFAULT_PARSER_PROFILE = "NATIVE_TIKA";

    private final SaTokenCurrentUserContext currentUserContext;
    private final WorkspaceAccessPolicy workspaceAccessPolicy;
    private final KbSourceDocumentDomainService sourceDocumentDomainService;
    private final KbKnowledgeBaseDomainService knowledgeBaseDomainService;
    private final KbParseRevisionDomainService parseRevisionDomainService;
    private final KbChunkRevisionDomainService chunkRevisionDomainService;
    private final KbWorkspaceDomainService workspaceDomainService;
    private final DocumentArtifactStore documentArtifactStore;
    private final TaskApplicationService taskApplicationService;
    private final ObjectMapper objectMapper;

    // ────────────────────────────── 触发解析 ──────────────────────────────

    /**
     * 触发文档解析。手动与自动触发共用此方法（AC-006）。
     *
     * @param documentKey 文档业务标识
     * @param parseMode   解析模式：DEFAULT、PARSE 或 SKIP
     * @return 解析受理响应
     */
    public ParseTriggerResponse triggerParse(String documentKey, String parseMode) {
        KbUser user = currentUserContext.requireCurrentUser();
        KbSourceDocumentEntity document = requireDocument(documentKey);
        KbKnowledgeBase knowledgeBase = requireKnowledgeBase(document.getKnowledgeBaseId());
        KbWorkspace workspace = requireWorkspace(knowledgeBase.getWorkspaceId());
        workspaceAccessPolicy.checkAccess(
                user.getUserKey(), workspace.getWorkspaceKey(), WorkspaceRole.ADMIN);

        // SKIP 模式不创建任务（AC-013）
        if ("SKIP".equalsIgnoreCase(parseMode)) {
            log.info("Parse skipped by explicit SKIP mode: documentKey={}", documentKey);
            return new ParseTriggerResponse(
                    documentKey, null, document.getParseStatus(),
                    document.getPublishStatus(), document.getUpdated());
        }

        // DEFAULT 模式遵循知识库 autoParse 设置
        boolean shouldParse = "PARSE".equalsIgnoreCase(parseMode)
                || Boolean.TRUE.equals(knowledgeBase.getAutoParse());
        if (!shouldParse) {
            return new ParseTriggerResponse(
                    documentKey, null, document.getParseStatus(),
                    document.getPublishStatus(), document.getUpdated());
        }

        // 构建任务输入快照（文件元数据直接取自源文档）
        ParsingChunkProfile chunkProfile = resolveParsingChunkProfile(knowledgeBase);
        String parserProfile = resolveParserProfile(knowledgeBase);
        boolean autoPublish = Boolean.TRUE.equals(knowledgeBase.getAutoPublish());

        ParseTaskPayload payload = new ParseTaskPayload(
                workspace.getWorkspaceKey(),
                knowledgeBase.getKnowledgeBaseKey(),
                documentKey,
                document.getId(),
                document.getFileToken(),
                document.getOriginalFilename(),
                document.getContentType(),
                parserProfile,
                chunkProfile,
                autoPublish);

        String payloadJson = serializePayload(payload);
        String idempotencyKey = "PARSE:" + document.getFileToken();

        // 幂等创建任务
        var task = taskApplicationService.createTask(
                workspace.getId(), knowledgeBase.getId(), document.getId(),
                TaskType.PARSE, document.getFileToken(),
                idempotencyKey, payloadJson);

        // 更新解析状态为 QUEUED
        document.setParseStatus(PARSE_STATUS_QUEUED);
        boolean updated = sourceDocumentDomainService.updateById(document);
        if (!updated) {
            throw new KnowledgeBaseConflictException();
        }

        log.info("Parse task created: documentKey={}, taskKey={}, parserProfile={}",
                documentKey, task.taskKey(), parserProfile);

        return new ParseTriggerResponse(
                documentKey, task.taskKey(), PARSE_STATUS_QUEUED,
                document.getPublishStatus(), document.getUpdated());
    }

    // ────────────────────────────── 解析预览 ──────────────────────────────

    /**
     * 查询解析预览。需要 USER 权限（AC-012、AC-013）。
     *
     * @param documentKey 文档业务标识
     * @return 解析预览响应，无解析产物时返回空预览
     */
    public ParsePreviewResponse getParsePreview(String documentKey) {
        KbUser user = currentUserContext.requireCurrentUser();
        KbSourceDocumentEntity document = requireDocument(documentKey);
        KbKnowledgeBase knowledgeBase = requireKnowledgeBase(document.getKnowledgeBaseId());
        requireWorkspaceAccess(
                user.getUserKey(), knowledgeBase.getWorkspaceId(), WorkspaceRole.KNOWLEDGE_USER);

        if (document.getCurrentParseRevisionId() == null) {
            // AC-013：无解析产物返回空状态，不伪造
            return new ParsePreviewResponse(false, null, null, 0, null, Collections.emptyList());
        }

        KbParseRevisionEntity parseRevision = parseRevisionDomainService.getById(
                document.getCurrentParseRevisionId());
        if (parseRevision == null) {
            return new ParsePreviewResponse(false, null, null, 0, null, Collections.emptyList());
        }

        ArtifactScope scope = resolveScope(document, knowledgeBase);
        ParseManifest manifest = readManifest(
                scope, ArtifactType.PARSED_MANIFEST, parseRevision.getParseRevisionKey(),
                ParseManifest.class);

        List<ParsedBlockView> blocks = manifest.blocks().stream()
                .map(b -> new ParsedBlockView(b.index(), b.content(), b.sourceAnchor()))
                .collect(Collectors.toList());

        return new ParsePreviewResponse(
                true,
                parseRevision.getParserProfileJson(),
                manifest.parserTrace(),
                manifest.blockCount(),
                manifest.contentHash(),
                blocks);
    }

    // ────────────────────────────── 分块预览 ──────────────────────────────

    /**
     * 查询分块预览。需要 USER 权限（AC-012、AC-013）。
     *
     * @param documentKey 文档业务标识
     * @param page        页码（从 0 开始）
     * @param size        每页大小
     * @return 分块预览响应，无分块产物时返回空预览
     */
    public ChunkPreviewResponse getChunkPreview(String documentKey, int page, int size) {
        KbUser user = currentUserContext.requireCurrentUser();
        KbSourceDocumentEntity document = requireDocument(documentKey);
        KbKnowledgeBase knowledgeBase = requireKnowledgeBase(document.getKnowledgeBaseId());
        requireWorkspaceAccess(
                user.getUserKey(), knowledgeBase.getWorkspaceId(), WorkspaceRole.KNOWLEDGE_USER);

        if (document.getCurrentChunkRevisionId() == null) {
            // AC-013：无分块产物返回空状态，不伪造
            return new ChunkPreviewResponse(false, null, null, 0, 0, null, page, size, 0,
                    Collections.emptyList());
        }

        KbChunkRevisionEntity chunkRevision = chunkRevisionDomainService.getById(
                document.getCurrentChunkRevisionId());
        if (chunkRevision == null) {
            return new ChunkPreviewResponse(false, null, null, 0, 0, null, page, size, 0,
                    Collections.emptyList());
        }

        ArtifactScope scope = resolveScope(document, knowledgeBase);
        ChunkManifest manifest = readManifest(
                scope, ArtifactType.CHUNK_MANIFEST, chunkRevision.getChunkRevisionKey(),
                ChunkManifest.class);

        List<ChunkManifest.Chunk> allChunks = manifest.chunks();
        int total = allChunks.size();
        int fromIndex = Math.min(page * size, total);
        int toIndex = Math.min(fromIndex + size, total);
        List<ChunkView> pageChunks = allChunks.subList(fromIndex, toIndex).stream()
                .map(c -> new ChunkView(c.index(), c.content(), c.parentChunkId(), c.skipEmbedding()))
                .collect(Collectors.toList());

        return new ChunkPreviewResponse(
                true,
                chunkRevision.getChunkRevisionKey(),
                manifest.chunkProfile(),
                manifest.parentCount(),
                manifest.childCount(),
                manifest.contentHash(),
                page, size, total, pageChunks);
    }

    // ────────────────────────────── 重试任务 ──────────────────────────────

    /**
     * 重试任务。根据原输入快照创建下一次尝试（AC-014、AC-024）。
     *
     * @param taskKey 原任务业务标识
     * @return 新任务业务标识
     */
    public String retryTask(String taskKey) {
        KbUser user = currentUserContext.requireCurrentUser();
        var originalTask = taskApplicationService.findByKey(taskKey);
        if (originalTask == null) {
            throw new TaskExecutionException("Task not found: " + taskKey);
        }

        // 仅失败状态的任务可重试，避免误重试正在执行或已成功的任务（AC-008）
        if (originalTask.status() != TaskStatus.FAILED) {
            throw new TaskExecutionException(
                    Rag2OkfResultCode.TASK_NOT_RETRYABLE.getCode()
                            + ": 仅失败状态的任务可重试，当前状态: " + originalTask.status());
        }

        KbSourceDocumentEntity document = sourceDocumentDomainService.getById(
                originalTask.entity().getSourceDocumentId());
        if (document == null) {
            throw new TaskExecutionException("Document not found for task: " + taskKey);
        }
        KbKnowledgeBase knowledgeBase = requireKnowledgeBase(document.getKnowledgeBaseId());
        requireWorkspaceAccess(
                user.getUserKey(), knowledgeBase.getWorkspaceId(), WorkspaceRole.ADMIN);

        // 使用新的幂等键创建重试任务，复用原 payload
        String retryIdempotencyKey = "RETRY:" + taskKey + ":" + System.currentTimeMillis();
        var retryTask = taskApplicationService.createTask(
                originalTask.entity().getWorkspaceId(),
                originalTask.entity().getKnowledgeBaseId(),
                originalTask.entity().getSourceDocumentId(),
                originalTask.taskType(),
                originalTask.entity().getInputRevisionKey(),
                retryIdempotencyKey,
                originalTask.entity().getPayloadJson());

        // 更新解析状态为 QUEUED
        document.setParseStatus(PARSE_STATUS_QUEUED);
        sourceDocumentDomainService.updateById(document);

        log.info("Task retry created: original={}, retry={}", taskKey, retryTask.taskKey());
        return retryTask.taskKey();
    }

    // ────────────────────────────── 辅助方法 ──────────────────────────────

    private ParsingChunkProfile resolveParsingChunkProfile(KbKnowledgeBase knowledgeBase) {
        String json = knowledgeBase.getChunkProfileJson();
        if (json == null || json.isBlank()) {
            return ParsingChunkProfile.DEFAULT_RECURSIVE;
        }
        try {
            return objectMapper.readValue(json, ParsingChunkProfile.class);
        } catch (IOException e) {
            log.warn("Failed to parse chunkProfileJson, using default: {}", json, e);
            return ParsingChunkProfile.DEFAULT_RECURSIVE;
        }
    }

    private String resolveParserProfile(KbKnowledgeBase knowledgeBase) {
        String profile = knowledgeBase.getParserProfile();
        return (profile == null || profile.isBlank()) ? DEFAULT_PARSER_PROFILE : profile;
    }

    private <T> T readManifest(ArtifactScope scope, ArtifactType type, String revisionKey,
                                Class<T> manifestClass) {
        ArtifactContent content = documentArtifactStore.open(
                new ArtifactReference(scope, type, revisionKey));
        try {
            return objectMapper.readValue(content.inputStream(), manifestClass);
        } catch (IOException e) {
            throw new DocumentArtifactException("读取 manifest 失败: " + revisionKey, e);
        } finally {
            try {
                content.inputStream().close();
            } catch (IOException ignored) {
                // 忽略关闭异常
            }
        }
    }

    private ArtifactScope resolveScope(KbSourceDocumentEntity document,
                                       KbKnowledgeBase knowledgeBase) {
        KbWorkspace workspace = workspaceDomainService.getById(knowledgeBase.getWorkspaceId());
        return new ArtifactScope(
                workspace.getWorkspaceKey(),
                knowledgeBase.getKnowledgeBaseKey(),
                document.getDocumentKey());
    }

    private String serializePayload(ParseTaskPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (IOException e) {
            throw new TaskExecutionException("序列化任务输入失败: " + e.getMessage());
        }
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

    private KbKnowledgeBase requireKnowledgeBase(Long knowledgeBaseId) {
        KbKnowledgeBase knowledgeBase = knowledgeBaseDomainService.getById(knowledgeBaseId);
        if (knowledgeBase == null) {
            throw new WorkspaceAccessDeniedException();
        }
        return knowledgeBase;
    }

    private KbWorkspace requireWorkspace(Long workspaceId) {
        KbWorkspace workspace = workspaceDomainService.getById(workspaceId);
        if (workspace == null) {
            throw new WorkspaceAccessDeniedException();
        }
        return workspace;
    }

    private void requireWorkspaceAccess(String userKey, Long workspaceId, WorkspaceRole requiredRole) {
        KbWorkspace workspace = requireWorkspace(workspaceId);
        workspaceAccessPolicy.checkAccess(userKey, workspace.getWorkspaceKey(), requiredRole);
    }
}
