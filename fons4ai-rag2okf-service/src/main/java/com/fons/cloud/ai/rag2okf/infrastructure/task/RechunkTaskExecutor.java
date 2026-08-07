package com.fons.cloud.ai.rag2okf.infrastructure.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fons.cloud.ai.rag2okf.common.dto.RechunkTaskPayload;
import com.fons.cloud.ai.rag2okf.common.dto.ModelBusinessKeyGenerator;
import com.fons.cloud.ai.rag2okf.common.exeception.DocumentArtifactException;
import com.fons.cloud.ai.rag2okf.common.dto.DocumentArtifactStore;
import com.fons.cloud.ai.rag2okf.common.dto.DocumentArtifactStore.ArtifactReference;
import com.fons.cloud.ai.rag2okf.common.dto.DocumentArtifactStore.ArtifactScope;
import com.fons.cloud.ai.rag2okf.common.dto.DocumentArtifactStore.ArtifactType;
import com.fons.cloud.ai.rag2okf.domain.entity.KbChunkRevisionEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbSourceDocumentEntity;
import com.fons.cloud.ai.rag2okf.common.dto.DocumentChunkerPort;
import com.fons.cloud.ai.rag2okf.domain.service.KbChunkRevisionDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.KbSourceDocumentDomainService;
import com.fons.cloud.ai.rag2okf.common.dto.ProcessingTask;
import com.fons.cloud.ai.rag2okf.common.dto.TaskExecutionPort;
import com.fons.cloud.ai.rag2okf.common.dto.TaskExecutionResult;
import com.fons.cloud.ai.rag2okf.common.dto.TaskType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * 重新分块任务执行器，实现 {@link TaskExecutionPort}。
 *
 * <p>执行流程（技术设计 §5.5）：
 * <ol>
 *   <li>反序列化 payloadJson 获取输入快照</li>
 *   <li>基于已有 ParseRevision 调用 {@link DocumentChunkerPort} 生成新 ChunkManifest</li>
 *   <li>事务内创建新 ChunkRevision，CAS 切换 currentChunkRevisionId</li>
 *   <li>切换成功后删除旧解析侧 ChunkManifest（技术补偿）</li>
 * </ol>
 *
 * <p>activePublicationRevisionId 全程不变（AC-021）；
 * 失败时旧解析侧分块保持当前，不造成数据丢失（§5.5 第 7 步）。
 *
 * @author hongqy
 */
@Slf4j
@Component
public class RechunkTaskExecutor implements TaskExecutionPort {

    private final DocumentChunkerPort documentChunkerPort;
    private final KbChunkRevisionDomainService chunkRevisionDomainService;
    private final KbSourceDocumentDomainService sourceDocumentDomainService;
    private final DocumentArtifactStore documentArtifactStore;
    private final ModelBusinessKeyGenerator keyGenerator;
    private final ObjectMapper objectMapper;

    @Lazy
    @Autowired
    private RechunkTaskExecutor self;

    public RechunkTaskExecutor(DocumentChunkerPort documentChunkerPort,
                               KbChunkRevisionDomainService chunkRevisionDomainService,
                               KbSourceDocumentDomainService sourceDocumentDomainService,
                               DocumentArtifactStore documentArtifactStore,
                               ModelBusinessKeyGenerator keyGenerator,
                               ObjectMapper objectMapper) {
        this.documentChunkerPort = documentChunkerPort;
        this.chunkRevisionDomainService = chunkRevisionDomainService;
        this.sourceDocumentDomainService = sourceDocumentDomainService;
        this.documentArtifactStore = documentArtifactStore;
        this.keyGenerator = keyGenerator;
        this.objectMapper = objectMapper;
    }

    @Override
    public TaskType supportedType() {
        return TaskType.RECHUNK;
    }

    @Override
    public TaskExecutionResult execute(ProcessingTask task) {
        RechunkTaskPayload payload;
        try {
            payload = objectMapper.readValue(task.entity().getPayloadJson(), RechunkTaskPayload.class);
        } catch (Exception e) {
            log.error("Failed to deserialize rechunk payload: taskKey={}", task.taskKey(), e);
            return new TaskExecutionResult.FatalFailure(
                    "PAYLOAD_INVALID", "任务输入快照解析失败");
        }

        ArtifactScope scope = new ArtifactScope(
                payload.workspaceKey(), payload.knowledgeBaseKey(), payload.documentKey());
        String newChunkRevisionKey = keyGenerator.nextKey();

        try {
            // 1. 基于已有 ParseRevision 重新分块
            log.info("Rechunk started: taskKey={}, parseRevisionKey={}, expectedChunkRevisionKey={}",
                    task.taskKey(), payload.parseRevisionKey(), payload.expectedChunkRevisionKey());

            DocumentChunkerPort.ChunkRequest chunkRequest = new DocumentChunkerPort.ChunkRequest(
                    scope, payload.parseRevisionKey(), newChunkRevisionKey, payload.chunkProfile());
            DocumentChunkerPort.ChunkResult chunkResult = documentChunkerPort.chunk(chunkRequest);

            // 2. 事务内创建新 ChunkRevision，CAS 切换指针
            KbChunkRevisionEntity oldChunkRevision = self.persistRechunkResult(
                    payload, newChunkRevisionKey, chunkResult);

            // 3. 切换成功后删除旧解析侧 ChunkManifest（技术补偿，§5.5 第 5 步）
            if (oldChunkRevision != null) {
                deleteOldChunkManifest(scope, oldChunkRevision.getChunkRevisionKey());
            }

            log.info("Rechunk succeeded: taskKey={}, newChunkRevisionKey={}, chunks={}",
                    task.taskKey(), newChunkRevisionKey, chunkResult.manifest().childCount());

            return new TaskExecutionResult.Succeeded(newChunkRevisionKey);

        } catch (DocumentArtifactException e) {
            log.warn("Rechunk failed (artifact): taskKey={}, message={}",
                    task.taskKey(), e.getMessage());
            return new TaskExecutionResult.RetryableFailure(
                    "RECHUNK_ARTIFACT_ERROR", safeMessage(e.getMessage()));
        } catch (Exception e) {
            log.error("Rechunk failed (unexpected): taskKey={}", task.taskKey(), e);
            return new TaskExecutionResult.RetryableFailure(
                    "RECHUNK_UNEXPECTED_ERROR", "重新分块执行异常: " + e.getClass().getSimpleName());
        }
    }

    /**
     * 事务内创建新 ChunkRevision，CAS 切换 currentChunkRevisionId。
     *
     * @return 被替换的旧 ChunkRevision 实体，用于后续清理
     */
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public KbChunkRevisionEntity persistRechunkResult(
            RechunkTaskPayload payload, String newChunkRevisionKey,
            DocumentChunkerPort.ChunkResult chunkResult) {

        // 创建新 ChunkRevision
        KbChunkRevisionEntity newChunkRevision = new KbChunkRevisionEntity();
        newChunkRevision.setChunkRevisionKey(newChunkRevisionKey);
        newChunkRevision.setSourceDocumentId(payload.sourceDocumentId());
        newChunkRevision.setParseRevisionId(payload.parseRevisionId());
        try {
            newChunkRevision.setChunkProfileJson(
                    objectMapper.writeValueAsString(payload.chunkProfile()));
        } catch (Exception e) {
            newChunkRevision.setChunkProfileJson(null);
        }
        newChunkRevision.setManifestObjectKey(chunkResult.manifestArtifact().objectKey());
        newChunkRevision.setParentCount(chunkResult.manifest().parentCount());
        newChunkRevision.setChildCount(chunkResult.manifest().childCount());
        newChunkRevision.setContentHash(chunkResult.manifest().contentHash());
        newChunkRevision.setStatus("SUCCEEDED");
        chunkRevisionDomainService.save(newChunkRevision);

        // CAS 切换文档指针
        KbSourceDocumentEntity document = sourceDocumentDomainService.getById(payload.sourceDocumentId());
        if (document == null) {
            throw new DocumentArtifactException("文档不存在: " + payload.documentKey());
        }

        // 记录旧 ChunkRevision 用于后续清理
        KbChunkRevisionEntity oldChunkRevision = null;
        if (document.getCurrentChunkRevisionId() != null) {
            oldChunkRevision = chunkRevisionDomainService.getById(
                    document.getCurrentChunkRevisionId());
        }

        document.setCurrentChunkRevisionId(newChunkRevision.getId());
        boolean updated = sourceDocumentDomainService.updateById(document);
        if (!updated) {
            throw new DocumentArtifactException("分块指针切换失败（CAS 冲突）: " + payload.documentKey());
        }

        return oldChunkRevision;
    }

    /**
     * 删除旧解析侧 ChunkManifest。失败时只记录日志，不影响主流程（§5.5 第 5 步）。
     */
    private void deleteOldChunkManifest(ArtifactScope scope, String oldChunkRevisionKey) {
        try {
            documentArtifactStore.delete(new ArtifactReference(
                    scope, ArtifactType.CHUNK_MANIFEST, oldChunkRevisionKey));
            log.info("Old chunk manifest deleted: chunkRevisionKey={}", oldChunkRevisionKey);
        } catch (Exception e) {
            log.warn("Failed to delete old chunk manifest (orphaned but harmless): chunkRevisionKey={}",
                    oldChunkRevisionKey, e);
        }
    }

    private String safeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "重新分块产物处理失败";
        }
        return message;
    }
}
