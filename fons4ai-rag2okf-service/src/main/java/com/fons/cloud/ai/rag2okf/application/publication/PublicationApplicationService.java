package com.fons.cloud.ai.rag2okf.application.publication;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fons.cloud.ai.rag2okf.common.dto.CurrentUserContext;
import com.fons.cloud.ai.rag2okf.common.dto.PublicationTaskPayload;
import com.fons.cloud.ai.rag2okf.application.task.TaskApplicationService;
import com.fons.cloud.ai.rag2okf.common.constants.WorkspaceRole;
import com.fons.cloud.ai.rag2okf.common.exeception.KnowledgeBaseConflictException;
import com.fons.cloud.ai.rag2okf.common.exeception.TaskExecutionException;
import com.fons.cloud.ai.rag2okf.common.exeception.WorkspaceAccessDeniedException;
import com.fons.cloud.ai.rag2okf.common.response.PublicationResponse;
import com.fons.cloud.ai.rag2okf.domain.entity.KbChunkRevisionEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbKnowledgeBaseEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbProcessingTaskEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbPublicationRevisionEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbSourceDocumentEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbUser;
import com.fons.cloud.ai.rag2okf.domain.entity.KbWorkspace;
import com.fons.cloud.ai.rag2okf.common.dto.PublicationManifest;
import com.fons.cloud.ai.rag2okf.domain.service.KbChunkRevisionDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.KbKnowledgeBaseDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.KbProcessingTaskDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.KbPublicationRevisionDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.KbSourceDocumentDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.KbWorkspaceDomainService;
import com.fons.cloud.ai.rag2okf.common.dto.TaskStatus;
import com.fons.cloud.ai.rag2okf.common.dto.TaskType;
import com.fons.cloud.ai.rag2okf.infrastructure.identity.WorkspaceAccessPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 发布应用服务：触发发布、校验当前分块、幂等创建发布任务（技术设计 §5.6、§7.2）。
 *
 * <p>遵循 DDD-lite PublicationPolicy：
 * <ul>
 *   <li>未解析成功不可发布（AC-015）</li>
 *   <li>同一文档存在 RUNNING 发布任务时拒绝重复触发（§5.6 第 1 步）</li>
 *   <li>自动/人工发布共用流程，triggerType 仅用于审计（AC-016）</li>
 *   <li>幂等键基于 chunkRevisionKey，同一分块重复发布复用任务（§5.7）</li>
 *   <li>首次发布置 publishStatus=PUBLISHING；重新发布保持 PUBLISHED，由任务结果决定最终状态（§7.2）</li>
 * </ul>
 *
 * <p>本服务不直接执行 ES 投影或 CAS 指针切换；这些由 {@code PublicationTaskExecutor} 完成。
 *
 * @author hongqy
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PublicationApplicationService {

    /** 发布状态：未发布。 */
    public static final String PUBLISH_STATUS_UNPUBLISHED = "UNPUBLISHED";
    /** 发布状态：发布中。 */
    public static final String PUBLISH_STATUS_PUBLISHING = "PUBLISHING";
    /** 发布状态：已发布。 */
    public static final String PUBLISH_STATUS_PUBLISHED = "PUBLISHED";
    /** 发布状态：发布失败。 */
    public static final String PUBLISH_STATUS_PUBLISH_FAILED = "PUBLISH_FAILED";

    /** 解析状态：已成功。 */
    private static final String PARSE_STATUS_SUCCEEDED = "SUCCEEDED";

    private final CurrentUserContext currentUserContext;
    private final WorkspaceAccessPolicy workspaceAccessPolicy;
    private final KbSourceDocumentDomainService sourceDocumentDomainService;
    private final KbKnowledgeBaseDomainService knowledgeBaseDomainService;
    private final KbWorkspaceDomainService workspaceDomainService;
    private final KbChunkRevisionDomainService chunkRevisionDomainService;
    private final KbPublicationRevisionDomainService publicationRevisionDomainService;
    private final KbProcessingTaskDomainService processingTaskDomainService;
    private final TaskApplicationService taskApplicationService;
    private final ObjectMapper objectMapper;

    /**
     * 触发发布。手动与自动触发共用此方法（AC-015、AC-016）。
     *
     * @param documentKey 文档业务标识
     * @param triggerType 触发方式：MANUAL 或 AUTO
     * @return 发布受理响应
     */
    @Transactional(rollbackFor = Exception.class)
    public PublicationResponse triggerPublish(String documentKey, String triggerType) {
        KbUser user = currentUserContext.requireCurrentUser();
        KbSourceDocumentEntity document = requireDocument(documentKey);
        KbKnowledgeBaseEntity knowledgeBase = requireKnowledgeBase(document.getKnowledgeBaseId());
        KbWorkspace workspace = requireWorkspace(knowledgeBase.getWorkspaceId());
        workspaceAccessPolicy.checkAccess(
                user.getUserKey(), workspace.getWorkspaceKey(), WorkspaceRole.ADMIN);

        // AC-015：未解析成功不可发布
        if (!PARSE_STATUS_SUCCEEDED.equals(document.getParseStatus())
                || document.getCurrentParseRevisionId() == null
                || document.getCurrentChunkRevisionId() == null) {
            throw new TaskExecutionException("PUBLISH_PARSE_NOT_SUCCEEDED");
        }

        KbChunkRevisionEntity chunkRevision = chunkRevisionDomainService.getById(
                document.getCurrentChunkRevisionId());
        if (chunkRevision == null || !"SUCCEEDED".equals(chunkRevision.getStatus())) {
            throw new TaskExecutionException("PUBLISH_CHUNK_NOT_SUCCEEDED");
        }

        // §5.6 第 1 步：同一文档存在 RUNNING 发布任务时拒绝重复触发
        if (hasRunningPublishTask(document.getId())) {
            throw new KnowledgeBaseConflictException();
        }

        // 构建任务输入快照
        PublicationTaskPayload payload = new PublicationTaskPayload(
                workspace.getWorkspaceKey(),
                knowledgeBase.getKnowledgeBaseKey(),
                documentKey,
                document.getId(),
                document.getFileToken(),
                document.getCurrentParseRevisionId(),
                null,
                chunkRevision.getId(),
                chunkRevision.getChunkRevisionKey(),
                triggerType != null ? triggerType : PublicationManifest.TRIGGER_MANUAL);

        // 解析 parseRevisionKey（payload 中 parseRevisionKey 字段保留 null，执行器从 id 读取）
        KbPublicationRevisionEntity existingActive = null;
        if (document.getActivePublicationRevisionId() != null) {
            existingActive = publicationRevisionDomainService.getById(
                    document.getActivePublicationRevisionId());
        }
        boolean isFirstPublish = existingActive == null;

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new TaskExecutionException("序列化任务输入失败: " + e.getMessage());
        }

        // 幂等键基于 chunkRevisionKey，同一分块重复发布复用任务（§5.7）
        String idempotencyKey = "PUBLISH:" + chunkRevision.getChunkRevisionKey();

        var task = taskApplicationService.createTask(
                workspace.getId(), knowledgeBase.getId(), document.getId(),
                TaskType.PUBLISH, chunkRevision.getChunkRevisionKey(),
                idempotencyKey, payloadJson);

        // §7.2：首次发布置 PUBLISHING；重新发布保持 PUBLISHED，由任务结果决定最终状态
        String nextPublishStatus;
        if (isFirstPublish) {
            nextPublishStatus = PUBLISH_STATUS_PUBLISHING;
        } else {
            // 已有发布的新任务：保持 PUBLISHED，避免把"旧内容仍可用"误显示为整体未发布
            nextPublishStatus = PUBLISH_STATUS_PUBLISHED;
        }
        document.setPublishStatus(nextPublishStatus);
        boolean updated = sourceDocumentDomainService.updateById(document);
        if (!updated) {
            throw new KnowledgeBaseConflictException();
        }

        log.info("Publish task created: documentKey={}, taskKey={}, triggerType={}, isFirstPublish={}",
                documentKey, task.taskKey(), triggerType, isFirstPublish);

        return new PublicationResponse(
                documentKey, task.taskKey(), nextPublishStatus, TaskStatus.QUEUED.name());
    }

    /**
     * 查询文档当前发布摘要（用于 Controller 详情接口）。
     *
     * @param documentKey 文档业务标识
     * @return 当前发布 Revision 实体，无发布时返回 null
     */
    public KbPublicationRevisionEntity getCurrentPublication(String documentKey) {
        KbSourceDocumentEntity document = sourceDocumentDomainService.getOne(
                Wrappers.<KbSourceDocumentEntity>lambdaQuery()
                        .eq(KbSourceDocumentEntity::getDocumentKey, documentKey));
        if (document == null || document.getActivePublicationRevisionId() == null) {
            return null;
        }
        return publicationRevisionDomainService.getById(document.getActivePublicationRevisionId());
    }

    // ────────────────────────────── 辅助方法 ──────────────────────────────

    /**
     * 检查同一文档是否存在 RUNNING 的发布任务（§5.6 第 1 步）。
     */
    private boolean hasRunningPublishTask(Long sourceDocumentId) {
        KbProcessingTaskEntity running = processingTaskDomainService.getOne(
                Wrappers.<KbProcessingTaskEntity>lambdaQuery()
                        .eq(KbProcessingTaskEntity::getSourceDocumentId, sourceDocumentId)
                        .eq(KbProcessingTaskEntity::getTaskType, TaskType.PUBLISH.name())
                        .eq(KbProcessingTaskEntity::getStatus, TaskStatus.RUNNING.name())
                        .last("LIMIT 1"));
        return running != null;
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

    private KbKnowledgeBaseEntity requireKnowledgeBase(Long knowledgeBaseId) {
        KbKnowledgeBaseEntity knowledgeBase = knowledgeBaseDomainService.getById(knowledgeBaseId);
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
