package com.fons.cloud.ai.rag2okf.infrastructure.task;

import com.fons.cloud.ai.rag2okf.common.constants.Rag2OkfResultCode;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fons.cloud.ai.rag2okf.common.dto.PublicationTaskPayload;
import com.fons.cloud.ai.rag2okf.application.task.OutboxApplicationService;
import com.fons.cloud.ai.rag2okf.application.task.TaskApplicationService;
import com.fons.cloud.ai.rag2okf.common.exeception.DocumentArtifactException;
import com.fons.cloud.ai.rag2okf.common.dto.DocumentArtifactStore;
import com.fons.cloud.ai.rag2okf.common.dto.DocumentArtifactStore.ArtifactContent;
import com.fons.cloud.ai.rag2okf.common.dto.DocumentArtifactStore.ArtifactReference;
import com.fons.cloud.ai.rag2okf.common.dto.DocumentArtifactStore.ArtifactScope;
import com.fons.cloud.ai.rag2okf.common.dto.DocumentArtifactStore.ArtifactType;
import com.fons.cloud.ai.rag2okf.common.utils.BusinessKeyGenerator;
import com.fons.cloud.ai.rag2okf.domain.entity.KbPublicationRevisionEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbSourceDocumentEntity;
import com.fons.cloud.ai.rag2okf.common.dto.ChunkManifest;
import com.fons.cloud.ai.rag2okf.common.dto.PublicationProjectionPort;
import com.fons.cloud.ai.rag2okf.common.dto.PublicationProjectionPort.ChunkProjection;
import com.fons.cloud.ai.rag2okf.common.dto.PublicationProjectionPort.ProjectionRequest;
import com.fons.cloud.ai.rag2okf.common.dto.PublicationProjectionPort.ProjectionResult;
import com.fons.cloud.ai.rag2okf.domain.service.KbChunkRevisionDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.KbParseRevisionDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.KbPublicationRevisionDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.KbSourceDocumentDomainService;
import com.fons.cloud.ai.rag2okf.common.dto.ProcessingTask;
import com.fons.cloud.ai.rag2okf.common.dto.TaskExecutionPort;
import com.fons.cloud.ai.rag2okf.common.dto.TaskExecutionResult;
import com.fons.cloud.ai.rag2okf.common.dto.TaskType;
import com.fons.cloud.ai.rag2okf.application.publication.EmbeddingProjectionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 发布任务执行器，实现 {@link TaskExecutionPort}。
 *
 * <p>执行流程（技术设计 §5.6 发布 Saga）：
 * <ol>
 *   <li>反序列化 payloadJson 获取输入快照</li>
 *   <li>从 MinIO 读取 ChunkManifest</li>
 *   <li>构建 PublicationManifest 和 ChunkProjection 列表</li>
 *   <li>调用 {@link PublicationProjectionPort#projectChunks} 批量写入 ES</li>
 *   <li>校验写入数量与 contentHash；任何部分失败标记 FAILED（§5.6 第 4 步）</li>
 *   <li>事务内创建 PublicationRevision，CAS 切换 active_publication_revision_id（§5.6 第 5 步）</li>
 *   <li>同事务写 Outbox 事件，异步清理旧发布投影（§5.6 第 6 步）</li>
 *   <li>失败时不切指针，旧发布保持可用（§5.6 第 8 步、AC-018）</li>
 * </ol>
 *
 * <p>MySQL 是唯一当前发布事实来源（D-002）；ES 只是投影，清理延迟不影响 active 切换。
 * 任何补偿不得撤回旧发布（AC-021）。
 *
 * @author hongqy
 */
@Slf4j
@Component
public class PublicationTaskExecutor implements TaskExecutionPort {

    /** Outbox 聚合类型：发布投影清理。 */
    public static final String OUTBOX_AGGREGATE_TYPE = "PUBLICATION_PROJECTION";
    /** Outbox 事件类型：清理旧发布投影。 */
    public static final String OUTBOX_EVENT_TYPE_CLEANUP = "CLEANUP_OLD_PROJECTION";

    private final DocumentArtifactStore documentArtifactStore;
    private final PublicationProjectionPort projectionPort;
    private final KbSourceDocumentDomainService sourceDocumentDomainService;
    private final KbParseRevisionDomainService parseRevisionDomainService;
    private final KbChunkRevisionDomainService chunkRevisionDomainService;
    private final KbPublicationRevisionDomainService publicationRevisionDomainService;
    private final TaskApplicationService taskApplicationService;
    private final OutboxApplicationService outboxApplicationService;
    private final ObjectMapper objectMapper;
    private final EmbeddingProjectionService embeddingProjectionService;

    @Lazy
    @Autowired
    private PublicationTaskExecutor self;

    public PublicationTaskExecutor(DocumentArtifactStore documentArtifactStore,
                                   PublicationProjectionPort projectionPort,
                                   KbSourceDocumentDomainService sourceDocumentDomainService,
                                   KbParseRevisionDomainService parseRevisionDomainService,
                                   KbChunkRevisionDomainService chunkRevisionDomainService,
                                   KbPublicationRevisionDomainService publicationRevisionDomainService,
                                   TaskApplicationService taskApplicationService,
                                   OutboxApplicationService outboxApplicationService,
                                   ObjectMapper objectMapper,
                                   EmbeddingProjectionService embeddingProjectionService) {
        this.documentArtifactStore = documentArtifactStore;
        this.projectionPort = projectionPort;
        this.sourceDocumentDomainService = sourceDocumentDomainService;
        this.parseRevisionDomainService = parseRevisionDomainService;
        this.chunkRevisionDomainService = chunkRevisionDomainService;
        this.publicationRevisionDomainService = publicationRevisionDomainService;
        this.taskApplicationService = taskApplicationService;
        this.outboxApplicationService = outboxApplicationService;
        this.objectMapper = objectMapper;
        this.embeddingProjectionService = embeddingProjectionService;
    }

    @Override
    public TaskType supportedType() {
        return TaskType.PUBLISH;
    }

    @Override
    public TaskExecutionResult execute(ProcessingTask task) {
        PublicationTaskPayload payload;
        try {
            payload = objectMapper.readValue(task.entity().getPayloadJson(), PublicationTaskPayload.class);
        } catch (Exception e) {
            log.error("Failed to deserialize publish payload: taskKey={}", task.taskKey(), e);
            return new TaskExecutionResult.FatalFailure(
                    Rag2OkfResultCode.PAYLOAD_INVALID.getCode(), "任务输入快照解析失败");
        }

        ArtifactScope scope = new ArtifactScope(
                payload.workspaceKey(), payload.knowledgeBaseKey(), payload.documentKey());
        String publicationRevisionKey = BusinessKeyGenerator.nextKey();

        try {
            // 1. 读取 ChunkManifest
            log.info("Publish started: taskKey={}, chunkRevisionKey={}",
                    task.taskKey(), payload.chunkRevisionKey());

            ChunkManifest chunkManifest = readChunkManifest(scope, payload.chunkRevisionKey());

            // AC-018 / 拒绝空产物：childCount=0 不发布
            if (chunkManifest.childCount() == 0 || chunkManifest.chunks().isEmpty()) {
                return new TaskExecutionResult.FatalFailure(
                        Rag2OkfResultCode.PUBLISH_EMPTY_CHUNKS.getCode(), "分块产物为空，拒绝发布");
            }

            // 2. 构建 PublicationManifest 与 ChunkProjection 列表
            List<ChunkProjection> projections = buildProjections(
                    payload.chunkRevisionKey(), chunkManifest);

            // 2.1 发布时同步向量化（CR-013，D-007）
            // 无 EMBEDDING 绑定时降级 BM25-only；向量化失败阻塞发布
            projections = embeddingProjectionService.embedProjections(
                    payload.knowledgeBaseKey(), projections);

            String contentHash = chunkManifest.contentHash();
            ProjectionRequest projectionRequest = new ProjectionRequest(
                    publicationRevisionKey,
                    payload.workspaceKey(),
                    payload.knowledgeBaseKey(),
                    payload.documentKey(),
                    payload.parseRevisionKey(),
                    payload.chunkRevisionKey(),
                    contentHash,
                    projections);

            // 3. 写入 ES（§5.6 第 3-4 步）
            ProjectionResult projectionResult = projectionPort.projectChunks(projectionRequest);

            // 校验写入数量
            if (projectionResult.projectionCount() != projections.size()) {
                log.error("Projection count mismatch: expected={}, actual={}, publicationRevisionKey={}",
                        projections.size(), projectionResult.projectionCount(), publicationRevisionKey);
                return new TaskExecutionResult.RetryableFailure(
                        Rag2OkfResultCode.PROJECTION_COUNT_MISMATCH.getCode(), "投影写入数量不一致");
            }

            // 4. 事务内创建 PublicationRevision，CAS 切换指针，写 Outbox（§5.6 第 5-6 步）
            self.persistPublicationResult(payload, publicationRevisionKey, projectionResult);

            log.info("Publish succeeded: taskKey={}, publicationRevisionKey={}, projectionCount={}",
                    task.taskKey(), publicationRevisionKey, projectionResult.projectionCount());

            return new TaskExecutionResult.Succeeded(publicationRevisionKey);

        } catch (EmbeddingProjectionService.EmbeddingException e) {
            // 向量化失败：不切指针，旧发布保持（AC-018）
            // fatal=true（维度不匹配/档案不可用）-> FatalFailure，重试不会成功
            // fatal=false（模型调用临时故障）-> RetryableFailure
            log.warn("Publish failed (embedding): taskKey={}, errorCode={}, fatal={}, message={}",
                    task.taskKey(), e.errorCode(), e.fatal(), e.getMessage());
            if (e.fatal()) {
                return new TaskExecutionResult.FatalFailure(
                        e.errorCode(), safeMessage(e.getMessage()));
            }
            return new TaskExecutionResult.RetryableFailure(
                    e.errorCode(), safeMessage(e.getMessage()));
        } catch (ProjectionPersistenceException e) {
            // 事务内 CAS 失败：ES 已写入 STAGED 投影，需要清理，但旧发布保持
            log.warn("Publish persistence failed (CAS conflict): taskKey={}, publicationRevisionKey={}",
                    task.taskKey(), publicationRevisionKey, e);
            scheduleStaleProjectionCleanup(publicationRevisionKey);
            return new TaskExecutionResult.RetryableFailure(
                    Rag2OkfResultCode.PUBLISH_CAS_CONFLICT.getCode(), "发布指针切换冲突，已安排清理");
        } catch (PublicationProjectionPort.ProjectionException e) {
            // ES 写入或查询失败：不切指针，旧发布保持（AC-018）
            log.warn("Publish failed (projection): taskKey={}, errorCode={}, message={}",
                    task.taskKey(), e.errorCode(), e.getMessage());
            return new TaskExecutionResult.RetryableFailure(
                    Rag2OkfResultCode.PUBLISH_PROJECTION_ERROR.getCode(), safeMessage(e.getMessage()));
        } catch (DocumentArtifactException e) {
            log.warn("Publish failed (artifact): taskKey={}, message={}",
                    task.taskKey(), e.getMessage());
            return new TaskExecutionResult.RetryableFailure(
                    Rag2OkfResultCode.PUBLISH_ARTIFACT_ERROR.getCode(), safeMessage(e.getMessage()));
        } catch (Exception e) {
            log.error("Publish failed (unexpected): taskKey={}", task.taskKey(), e);
            return new TaskExecutionResult.RetryableFailure(
                    Rag2OkfResultCode.PUBLISH_UNEXPECTED_ERROR.getCode(),
                    "发布执行异常: " + e.getClass().getSimpleName());
        }
    }

    /**
     * 事务内创建 PublicationRevision，CAS 切换 active_publication_revision_id，写 Outbox 事件。
     *
     * <p>通过 @Lazy 自引用触发 Spring AOP 代理，使 @Transactional 生效。
     * 失败时抛出 {@link ProjectionPersistenceException}，调用方安排 STAGED 投影清理。
     *
     * @throws ProjectionPersistenceException CAS 切换失败
     */
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public void persistPublicationResult(PublicationTaskPayload payload,
                                         String publicationRevisionKey,
                                         ProjectionResult projectionResult) {
        // 创建 PublicationRevision
        KbPublicationRevisionEntity publicationRevision = new KbPublicationRevisionEntity();
        publicationRevision.setPublicationRevisionKey(publicationRevisionKey);
        publicationRevision.setSourceDocumentId(payload.sourceDocumentId());
        publicationRevision.setParseRevisionId(payload.parseRevisionId());
        publicationRevision.setChunkRevisionId(payload.chunkRevisionId());
        publicationRevision.setProjectionIndex(projectionResult.projectionIndex());
        publicationRevision.setProjectionCount(projectionResult.projectionCount());
        publicationRevision.setStatus("PUBLISHED");
        publicationRevision.setTriggerType(payload.triggerType());
        publicationRevision.setPublishedAt(new Date());
        publicationRevisionDomainService.save(publicationRevision);

        // CAS 切换文档指针
        KbSourceDocumentEntity document = sourceDocumentDomainService.getById(payload.sourceDocumentId());
        if (document == null) {
            throw new ProjectionPersistenceException("文档不存在: " + payload.documentKey());
        }

        // 记录旧 active 发布用于后续清理（§5.6 第 6 步）
        Long oldPublicationRevisionId = document.getActivePublicationRevisionId();

        document.setActivePublicationRevisionId(publicationRevision.getId());
        document.setPublishStatus("PUBLISHED");
        boolean updated = sourceDocumentDomainService.updateById(document);
        if (!updated) {
            throw new ProjectionPersistenceException(
                    "发布指针切换失败（CAS 冲突）: " + payload.documentKey());
        }

        // 写 Outbox 事件：异步清理旧发布投影（§5.6 第 6 步）
        if (oldPublicationRevisionId != null) {
            KbPublicationRevisionEntity oldPublication = publicationRevisionDomainService.getById(
                    oldPublicationRevisionId);
            if (oldPublication != null) {
                String outboxPayload = oldPublication.getPublicationRevisionKey();
                outboxApplicationService.createEvent(
                        OUTBOX_AGGREGATE_TYPE,
                        oldPublication.getPublicationRevisionKey(),
                        OUTBOX_EVENT_TYPE_CLEANUP,
                        outboxPayload);
                log.info("Outbox cleanup scheduled: oldPublicationRevisionKey={}",
                        oldPublication.getPublicationRevisionKey());
            }
        }
    }

    // ────────────────────────────── 内部辅助 ──────────────────────────────

    private ChunkManifest readChunkManifest(ArtifactScope scope, String chunkRevisionKey) {
        ArtifactContent content = documentArtifactStore.open(
                new ArtifactReference(scope, ArtifactType.CHUNK_MANIFEST, chunkRevisionKey));
        try {
            return objectMapper.readValue(content.inputStream(), ChunkManifest.class);
        } catch (IOException e) {
            throw new DocumentArtifactException("读取 ChunkManifest 失败: " + chunkRevisionKey, e);
        } finally {
            try {
                content.inputStream().close();
            } catch (IOException ignored) {
                // 忽略关闭异常
            }
        }
    }

    /**
     * 将 ChunkManifest 转换为 ChunkProjection 列表。
     *
     * <p>V1 投影策略（D-007）：
     * <ul>
     *   <li>chunkKey = chunkRevisionKey + "-" + index，可从 manifest 重建</li>
     *   <li>displayText = content，参与 BM25</li>
     *   <li>embeddingText = skipEmbedding ? null : content（V1 不索引向量，但保留供后续重建）</li>
     *   <li>sourceLocatorType = NONE（不伪造页码，AC-014）</li>
     *   <li>pageNumber = null（NONE 时必须为 null）</li>
     *   <li>titlePath 从 metadata.title/subtitle 组合，可空</li>
     * </ul>
     */
    private List<ChunkProjection> buildProjections(String chunkRevisionKey, ChunkManifest manifest) {
        List<ChunkProjection> projections = new ArrayList<>(manifest.chunks().size());
        for (ChunkManifest.Chunk chunk : manifest.chunks()) {
            Map<String, Object> metadata = chunk.metadata();
            String parentChunkKey = metadata != null ? (String) metadata.get("parentChunkId") : null;
            boolean skipEmbedding = chunk.skipEmbedding();
            String titlePath = buildTitlePath(metadata);

            String chunkKey = chunkRevisionKey + "-" + chunk.index();
            String chunkLevel = parentChunkKey == null
                    ? ChunkProjection.LEVEL_PARENT
                    : ChunkProjection.LEVEL_CHILD;

            projections.add(new ChunkProjection(
                    chunkKey,
                    parentChunkKey,
                    chunkLevel,
                    chunk.index(),
                    chunk.content(),
                    chunk.content(),
                    skipEmbedding ? null : chunk.content(),
                    titlePath,
                    "NONE",
                    null,
                    null,
                    null,
                    null));
        }
        return projections;
    }

    private String buildTitlePath(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        String title = getString(metadata, "title");
        String subtitle = getString(metadata, "subtitle");
        if (title == null && subtitle == null) {
            return null;
        }
        if (title == null) {
            return subtitle;
        }
        if (subtitle == null) {
            return title;
        }
        return title + "/" + subtitle;
    }

    private String getString(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return text.isBlank() ? null : text;
    }

    /**
     * 安排清理 ES 中已写入但未生效的 STAGED 投影。
     *
     * <p>CAS 失败时调用：publicationRevisionKey 对应的投影未成为 active，需要异步删除。
     * 失败只记录日志，不影响主流程重试。
     */
    private void scheduleStaleProjectionCleanup(String publicationRevisionKey) {
        try {
            outboxApplicationService.createEvent(
                    OUTBOX_AGGREGATE_TYPE,
                    publicationRevisionKey,
                    OUTBOX_EVENT_TYPE_CLEANUP,
                    publicationRevisionKey);
            log.info("Stale projection cleanup scheduled: publicationRevisionKey={}",
                    publicationRevisionKey);
        } catch (Exception e) {
            log.warn("Failed to schedule stale projection cleanup (orphaned but harmless): publicationRevisionKey={}",
                    publicationRevisionKey, e);
        }
    }

    private String safeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "发布产物处理失败";
        }
        return message;
    }

    /**
     * 发布持久化异常，用于区分 CAS 失败与其他失败。
     */
    static class ProjectionPersistenceException extends RuntimeException {
        public ProjectionPersistenceException(String message) {
            super(message);
        }
    }
}
