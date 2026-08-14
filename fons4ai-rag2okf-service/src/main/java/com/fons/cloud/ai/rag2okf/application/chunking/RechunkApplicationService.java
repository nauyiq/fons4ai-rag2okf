package com.fons.cloud.ai.rag2okf.application.chunking;

import com.fons.cloud.ai.rag2okf.common.constants.Rag2OkfResultCode;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fons.cloud.ai.rag2okf.infrastructure.adapter.user.SaTokenCurrentUserContext;
import com.fons.cloud.ai.rag2okf.common.dto.RechunkTaskPayload;
import com.fons.cloud.ai.rag2okf.application.task.TaskApplicationService;
import com.fons.cloud.ai.rag2okf.common.constants.user.WorkspaceRole;
import com.fons.cloud.ai.rag2okf.common.exeception.KnowledgeBaseConflictException;
import com.fons.cloud.ai.rag2okf.common.exeception.TaskExecutionException;
import com.fons.cloud.ai.rag2okf.common.exception.user.WorkspaceAccessDeniedException;
import com.fons.cloud.ai.rag2okf.common.response.RechunkResponse;
import com.fons.cloud.ai.rag2okf.domain.entity.KbChunkRevisionEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbKnowledgeBase;
import com.fons.cloud.ai.rag2okf.domain.entity.KbParseRevisionEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbSourceDocumentEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.user.KbUser;
import com.fons.cloud.ai.rag2okf.domain.entity.user.KbWorkspace;
import com.fons.cloud.ai.rag2okf.common.dto.ParsingChunkProfile;
import com.fons.cloud.ai.rag2okf.domain.service.KbChunkRevisionDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.KbKnowledgeBaseDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.KbParseRevisionDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.KbSourceDocumentDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.user.KbWorkspaceDomainService;
import com.fons.cloud.ai.rag2okf.common.dto.TaskType;
import com.fons.cloud.ai.rag2okf.infrastructure.support.user.WorkspaceAccessPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 重新分块应用服务：确认校验、创建任务（AC-019、AC-020、AC-021）。
 *
 * <p>遵循 DDD-lite RechunkPolicy：
 * <ul>
 *   <li>confirmed 非 true 直接拒绝（AC-019）</li>
 *   <li>只允许基于当前成功的 ParseRevision 执行</li>
 *   <li>expectedChunkRevisionKey 匹配当前指针才允许执行（CAS 前置校验）</li>
 *   <li>activePublicationRevisionId 全程不变（AC-021）</li>
 * </ul>
 *
 * @author hongqy
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RechunkApplicationService {

    private static final String PARSE_STATUS_SUCCEEDED = "SUCCEEDED";

    private final SaTokenCurrentUserContext currentUserContext;
    private final WorkspaceAccessPolicy workspaceAccessPolicy;
    private final KbSourceDocumentDomainService sourceDocumentDomainService;
    private final KbKnowledgeBaseDomainService knowledgeBaseDomainService;
    private final KbParseRevisionDomainService parseRevisionDomainService;
    private final KbChunkRevisionDomainService chunkRevisionDomainService;
    private final KbWorkspaceDomainService workspaceDomainService;
    private final TaskApplicationService taskApplicationService;
    private final ObjectMapper objectMapper;

    /**
     * 触发重新分块。
     *
     * @param documentKey              文档业务标识
     * @param confirmed                必须为 true
     * @param expectedChunkRevisionKey 调用方持有的当前 ChunkRevision key
     * @param chunkProfile             新分块策略
     * @return 重新分块受理响应
     */
    public RechunkResponse triggerRechunk(
            String documentKey, boolean confirmed,
            String expectedChunkRevisionKey, ParsingChunkProfile chunkProfile) {

        // AC-019：confirmed 非 true 直接拒绝
        if (!confirmed) {
            throw new TaskExecutionException(Rag2OkfResultCode.RECHUNK_CONFIRMATION_REQUIRED.getCode());
        }

        KbUser user = currentUserContext.requireCurrentUser();
        KbSourceDocumentEntity document = requireDocument(documentKey);
        KbKnowledgeBase knowledgeBase = requireKnowledgeBase(document.getKnowledgeBaseId());
        KbWorkspace workspace = requireWorkspace(knowledgeBase.getWorkspaceId());
        workspaceAccessPolicy.checkAccess(
                user.getUserKey(), workspace.getWorkspaceKey(), WorkspaceRole.ADMIN);

        // 校验解析已成功
        if (!PARSE_STATUS_SUCCEEDED.equals(document.getParseStatus())
                || document.getCurrentParseRevisionId() == null) {
            throw new TaskExecutionException(Rag2OkfResultCode.PARSE_NOT_SUCCEEDED.getCode());
        }

        // 校验 expectedChunkRevisionKey 匹配当前指针
        if (document.getCurrentChunkRevisionId() == null) {
            throw new TaskExecutionException(Rag2OkfResultCode.PARSE_NOT_SUCCEEDED.getCode());
        }
        KbChunkRevisionEntity currentChunkRevision = chunkRevisionDomainService.getById(
                document.getCurrentChunkRevisionId());
        if (currentChunkRevision == null
                || !currentChunkRevision.getChunkRevisionKey().equals(expectedChunkRevisionKey)) {
            // AC-020：revision 冲突返回 409
            throw new KnowledgeBaseConflictException();
        }

        // 获取 ParseRevision key
        KbParseRevisionEntity parseRevision = parseRevisionDomainService.getById(
                document.getCurrentParseRevisionId());
        if (parseRevision == null) {
            throw new TaskExecutionException(Rag2OkfResultCode.PARSE_NOT_SUCCEEDED.getCode());
        }

        // 构建任务输入快照
        RechunkTaskPayload payload = new RechunkTaskPayload(
                workspace.getWorkspaceKey(),
                knowledgeBase.getKnowledgeBaseKey(),
                documentKey,
                document.getId(),
                parseRevision.getId(),
                parseRevision.getParseRevisionKey(),
                expectedChunkRevisionKey,
                chunkProfile != null ? chunkProfile : ParsingChunkProfile.DEFAULT_RECURSIVE);

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new TaskExecutionException("序列化任务输入失败: " + e.getMessage());
        }

        String idempotencyKey = "RECHUNK:" + parseRevision.getParseRevisionKey()
                + ":" + payload.chunkProfile().strategy()
                + ":" + payload.chunkProfile().chunkSize();

        var task = taskApplicationService.createTask(
                workspace.getId(), knowledgeBase.getId(), document.getId(),
                TaskType.RECHUNK, parseRevision.getParseRevisionKey(),
                idempotencyKey, payloadJson);

        log.info("Rechunk task created: documentKey={}, taskKey={}, expectedChunkRevisionKey={}",
                documentKey, task.taskKey(), expectedChunkRevisionKey);

        return new RechunkResponse(documentKey, task.taskKey());
    }

    // ────────────────────────────── 辅助方法 ──────────────────────────────

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
}
