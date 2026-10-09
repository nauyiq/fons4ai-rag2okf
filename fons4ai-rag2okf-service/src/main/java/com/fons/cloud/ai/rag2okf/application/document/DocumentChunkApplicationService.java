package com.fons.cloud.ai.rag2okf.application.document;

import com.alibaba.fastjson2.JSON;
import com.fons.cloud.ai.rag2okf.common.constants.Rag2OkfResultCode;
import com.fons.cloud.ai.rag2okf.common.constants.document.DocumentStatus;
import com.fons.cloud.ai.rag2okf.common.constants.document.ProcessingTaskStatus;
import com.fons.cloud.ai.rag2okf.common.constants.document.ProcessingTaskType;
import com.fons.cloud.ai.rag2okf.common.constants.document.ResultStage;
import com.fons.cloud.ai.rag2okf.common.constants.knowledgebase.ModelBindingStatus;
import com.fons.cloud.ai.rag2okf.common.constants.user.WorkspaceRole;
import com.fons.cloud.ai.rag2okf.common.exception.document.DocumentProcessingException;
import com.fons.cloud.ai.rag2okf.common.model.document.ChunkManifest;
import com.fons.cloud.ai.rag2okf.common.model.document.ChunkPolicy;
import com.fons.cloud.ai.rag2okf.common.model.document.ModelProfileReference;
import com.fons.cloud.ai.rag2okf.common.model.document.ParsedDocument;
import com.fons.cloud.ai.rag2okf.common.model.document.ChunkTaskSnapshot;
import com.fons.cloud.ai.rag2okf.common.model.document.ParseTaskSnapshot;
import com.fons.cloud.ai.rag2okf.common.request.document.ChunkPolicyRequest;
import com.fons.cloud.ai.rag2okf.common.request.document.DocumentRechunkRequest;
import com.fons.cloud.ai.rag2okf.common.response.document.DocumentRechunkResponse;
import com.fons.cloud.ai.rag2okf.domain.entity.document.KbDocument;
import com.fons.cloud.ai.rag2okf.domain.entity.document.KbDocumentResult;
import com.fons.cloud.ai.rag2okf.domain.entity.document.KbProcessingTask;
import com.fons.cloud.ai.rag2okf.domain.entity.knowledgebase.KbKnowledgeBase;
import com.fons.cloud.ai.rag2okf.domain.entity.knowledgebase.KbModelBinding;
import com.fons.cloud.ai.rag2okf.domain.entity.user.KbUser;
import com.fons.cloud.ai.rag2okf.domain.entity.user.KbWorkspace;
import com.fons.cloud.ai.rag2okf.domain.entity.user.UserWorkspaceAggregate;
import com.fons.cloud.ai.rag2okf.domain.service.document.KbDocumentDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.document.KbDocumentResultDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.document.KbProcessingTaskDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.knowledgebase.KbKnowledgeBaseDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.knowledgebase.KbModelBindingDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.user.KbModelProfileDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.user.KbWorkspaceDomainService;
import com.fons.cloud.ai.rag2okf.infrastructure.adapter.document.FonsOssDocumentArtifactService;
import com.fons.cloud.ai.rag2okf.infrastructure.adapter.user.SaTokenCurrentUserContext;
import com.fons.cloud.ai.rag2okf.infrastructure.document.chunking.ChunkManifestCodec;
import com.fons.cloud.ai.rag2okf.infrastructure.document.chunking.ChunkingExecutionContext;
import com.fons.cloud.ai.rag2okf.infrastructure.document.chunking.DocumentChunkProcessor;
import com.fons.cloud.ai.rag2okf.infrastructure.document.parser.ParsedDocumentCodec;
import com.fons.cloud.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 基于当前 ParsedDocument 创建并执行 RECHUNK 任务的应用编排。 */
@Service
@RequiredArgsConstructor
public class DocumentChunkApplicationService {

    /** 任务事实服务。 */
    private final KbProcessingTaskDomainService taskDomainService;
    /** 文档身份服务。 */
    private final KbDocumentDomainService documentDomainService;
    /** 文档结果服务。 */
    private final KbDocumentResultDomainService resultDomainService;
    /** 知识库服务。 */
    private final KbKnowledgeBaseDomainService knowledgeBaseDomainService;
    /** 工作空间服务。 */
    private final KbWorkspaceDomainService workspaceDomainService;
    /** 模型绑定服务。 */
    private final KbModelBindingDomainService modelBindingDomainService;
    /** 模型档案服务。 */
    private final KbModelProfileDomainService modelProfileDomainService;
    /** MinIO 制品服务。 */
    private final FonsOssDocumentArtifactService artifactService;
    /** ParsedDocument 编解码器。 */
    private final ParsedDocumentCodec parsedDocumentCodec;
    /** ChunkManifest 编解码器。 */
    private final ChunkManifestCodec chunkManifestCodec;
    /** 分块策略处理器。 */
    private final DocumentChunkProcessor chunkProcessor;
    /** 当前用户上下文。 */
    private final SaTokenCurrentUserContext currentUserContext;
    /** 数据库事务模板。 */
    private final TransactionTemplate transactionTemplate;

    /**
     * 基于当前 PARSE 阶段结果创建首次分块任务。
     *
     * <p>该方法仅供 PARSE 成功登记制品后的短事务调用。它不读取源文件、不调用解析器，
     * 且任务类型固定为 {@link ProcessingTaskType#CHUNK}，不会把首次分块伪装成 RECHUNK。</p>
     *
     * @param parseSnapshot 已冻结的 PARSE 任务快照
     * @param parseTask     已成功提交解析制品的 PARSE 任务
     * @param document      当前文档事实
     * @param result        已推进至 PARSE 阶段的当前结果
     */
    public void createInitialChunkTask(
            ParseTaskSnapshot parseSnapshot, KbProcessingTask parseTask,
            KbDocument document, KbDocumentResult result) {
        if (parseSnapshot == null || parseTask == null || document == null || result == null
                || result.getStage() != ResultStage.PARSE) {
            throw new DocumentProcessingException(Rag2OkfResultCode.TASK_INPUT_SUPERSEDED);
        }
        createTask(
                ProcessingTaskType.CHUNK,
                parseSnapshot.workspaceKey(),
                parseSnapshot.knowledgeBaseKey(),
                parseSnapshot.documentKey(),
                parseSnapshot.chunkPolicy(),
                parseSnapshot.modelProfileRefs(),
                parseSnapshot.requestedBy(),
                parseTask.getWorkspaceId(),
                parseTask.getKnowledgeBaseId(),
                document,
                result);
    }

    /**
     * 基于当前已解析结果重新创建首次 CHUNK 任务。
     *
     * <p>当前不暴露 HTTP 路由，供后续任务入口或失败重试编排调用。它只复用结果中
     * 已冻结的策略和 ParsedDocument，不会重新解析源文件。</p>
     *
     * @param documentKey           文档业务键
     * @param expectedResultVersion 调用方读取当前结果时持有的版本
     * @return 任务受理响应或稳定业务失败码
     */
    public R<DocumentRechunkResponse> startChunk(String documentKey, Integer expectedResultVersion) {
        if (expectedResultVersion == null) {
            return R.failed(Rag2OkfResultCode.TASK_INPUT_SUPERSEDED);
        }
        KbUser user = currentUserContext.requireCurrentUser();
        KbDocument document = documentDomainService.findByDocumentKey(documentKey);
        if (document == null || Boolean.TRUE.equals(document.getDeleted())) {
            return R.failed(Rag2OkfResultCode.DOCUMENT_DELETED);
        }
        KbKnowledgeBase knowledgeBase = knowledgeBaseDomainService.getById(document.getKnowledgeBaseId());
        KbWorkspace workspace = knowledgeBase == null ? null : workspaceDomainService.getById(knowledgeBase.getWorkspaceId());
        if (knowledgeBase == null) {
            return R.failed(Rag2OkfResultCode.KNOWLEDGE_BASE_NOT_FOUND);
        }
        if (workspace == null) {
            return R.failed(Rag2OkfResultCode.WORKSPACE_NOT_FOUND);
        }
        UserWorkspaceAggregate access = workspaceDomainService
                .findUserWorkspaceAggregate(user.getId(), workspace.getId());
        access.requireAccess(WorkspaceRole.ADMIN);
        KbDocumentResult result = resultDomainService.findCurrentByDocumentId(document.getId());
        ChunkPolicy policy = readFrozenChunkPolicy(result);
        if (!isChunkable(result) || !expectedResultVersion.equals(result.getVersion()) || policy == null) {
            return R.failed(Rag2OkfResultCode.TASK_INPUT_SUPERSEDED);
        }
        KbProcessingTask task = transactionTemplate.execute(status -> createTask(
                ProcessingTaskType.CHUNK,
                workspace.getWorkspaceKey(),
                knowledgeBase.getKnowledgeBaseKey(),
                document.getDocumentKey(),
                policy,
                freezeModelProfileRefs(knowledgeBase.getId()),
                user.getId(),
                workspace.getId(),
                knowledgeBase.getId(),
                document,
                result));
        return R.ok(new DocumentRechunkResponse(task.getTaskKey(), result.getStage(), task.getStatus()));
    }

    /**
     * 发起重新分块；每次合法请求创建独立 RECHUNK 任务。
     *
     * @param documentKey 文档业务键
     * @param request 重分块请求
     * @return 任务受理响应或稳定业务失败码
     */
    public R<DocumentRechunkResponse> startRechunk(String documentKey, DocumentRechunkRequest request) {
        if (request == null || request.getExpectedResultVersion() == null) {
            return R.failed(Rag2OkfResultCode.CHUNK_POLICY_INVALID);
        }
        ChunkPolicy policy = resolvePolicy(request.getChunkPolicy());
        if (policy == null) {
            return R.failed(Rag2OkfResultCode.CHUNK_POLICY_INVALID);
        }
        KbUser user = currentUserContext.requireCurrentUser();
        KbDocument document = documentDomainService.findByDocumentKey(documentKey);
        if (document == null || Boolean.TRUE.equals(document.getDeleted())) {
            return R.failed(Rag2OkfResultCode.DOCUMENT_DELETED);
        }
        KbKnowledgeBase knowledgeBase = knowledgeBaseDomainService.getById(document.getKnowledgeBaseId());
        KbWorkspace workspace = knowledgeBase == null ? null : workspaceDomainService.getById(knowledgeBase.getWorkspaceId());
        if (knowledgeBase == null) {
            return R.failed(Rag2OkfResultCode.KNOWLEDGE_BASE_NOT_FOUND);
        }
        if (workspace == null) {
            return R.failed(Rag2OkfResultCode.WORKSPACE_NOT_FOUND);
        }
        UserWorkspaceAggregate access = workspaceDomainService
                .findUserWorkspaceAggregate(user.getId(), workspace.getId());
        access.requireAccess(WorkspaceRole.ADMIN);
        KbDocumentResult result = resultDomainService.findCurrentByDocumentId(document.getId());
        if (!isRechunkable(result) || !request.getExpectedResultVersion().equals(result.getVersion())) {
            return R.failed(Rag2OkfResultCode.TASK_INPUT_SUPERSEDED);
        }
        KbProcessingTask task = transactionTemplate.execute(status -> createTask(
                ProcessingTaskType.RECHUNK,
                workspace.getWorkspaceKey(),
                knowledgeBase.getKnowledgeBaseKey(),
                document.getDocumentKey(),
                policy,
                freezeModelProfileRefs(knowledgeBase.getId()),
                user.getId(),
                workspace.getId(),
                knowledgeBase.getId(),
                document,
                result));
        return R.ok(new DocumentRechunkResponse(task.getTaskKey(), result.getStage(), task.getStatus()));
    }

    /** 执行一条 QUEUED RECHUNK 任务；调度器负责捕获运行时异常。 */
    public void execute(String taskKey) {
        KbProcessingTask task = requireQueuedTask(taskKey);
        taskDomainService.casTransitionStatus(task.getId(), ProcessingTaskStatus.QUEUED, ProcessingTaskStatus.RUNNING);
        try {
            executeRunning(task);
            taskDomainService.casTransitionStatus(task.getId(), ProcessingTaskStatus.RUNNING, ProcessingTaskStatus.SUCCEEDED);
        } catch (DocumentProcessingException exception) {
            markInitialChunkFailed(task, exception);
            taskDomainService.failRunningTask(task.getId(), exception.getCode(), exception.getMessage());
            throw exception;
        } catch (RuntimeException exception) {
            markInitialChunkFailed(task, exception);
            Rag2OkfResultCode errorCode = unexpectedErrorCode(task);
            taskDomainService.failRunningTask(task.getId(), errorCode.getCode(), errorCode.getMessage());
            throw new DocumentProcessingException(errorCode, exception);
        }
    }

    private KbProcessingTask createTask(
            ProcessingTaskType taskType,
            String workspaceKey,
            String knowledgeBaseKey,
            String documentKey,
            ChunkPolicy policy,
            Map<String, ModelProfileReference> modelProfileRefs,
            Long requestedBy,
            Long workspaceId,
            Long knowledgeBaseId,
            KbDocument document,
            KbDocumentResult result) {
        ChunkTaskSnapshot snapshot = new ChunkTaskSnapshot(
                workspaceKey, knowledgeBaseKey, documentKey,
                result.getResultKey(), result.getVersion(), policy, modelProfileRefs,
                requestedBy, new Date());
        KbProcessingTask failed = taskDomainService.findLatestFailed(document.getId(), taskType);
        return taskDomainService.create(KbProcessingTask.create(
                workspaceId, knowledgeBaseId, document.getId(), taskType,
                result.getResultKey(), JSON.toJSONString(snapshot), failed == null ? null : failed.getId()));
    }

    private void executeRunning(KbProcessingTask task) {
        ChunkTaskSnapshot snapshot = readSnapshot(task);
        KbDocument document = documentDomainService.getById(task.getSourceDocumentId());
        KbDocumentResult result = resultDomainService.findByResultKey(task.getInputResultKey());
        requireCurrentInput(task, snapshot, document, result);
        FonsOssDocumentArtifactService.StoredChunkArtifact stored = null;
        try (FonsOssDocumentArtifactService.SourceArtifactContent parsedContent =
                     artifactService.openParsed(result.getParsedDocumentObjectKey())) {
            ParsedDocument parsed = parsedDocumentCodec.readJson(parsedContent.inputStream().readAllBytes());
            ChunkManifest manifest = chunkProcessor.process(parsed, result.getResultKey(), snapshot.chunkPolicy(),
                    new ChunkingExecutionContext(snapshot.requestedBy(), snapshot.modelProfileRefs()));
            stored = artifactService.storeChunk(new FonsOssDocumentArtifactService.ChunkArtifactCommand(
                    snapshot.workspaceKey(), snapshot.knowledgeBaseKey(), snapshot.documentKey(), result.getResultKey(),
                    chunkManifestCodec.writeJson(manifest)));
            FonsOssDocumentArtifactService.StoredChunkArtifact artifact = stored;
            transactionTemplate.executeWithoutResult(status -> {
                resultDomainService.commitChunkManifest(result.getResultKey(), result.getDocumentId(), result.getVersion(),
                        result.getStage(), artifact.getObjectKey(), manifest.getParentCount(), manifest.getChildCount(),
                        manifest.getTotalCount(), manifest.getContentHash());
                transitionDocumentToParsed(document);
            });
        } catch (IOException exception) {
            compensate(stored, exception);
            throw new DocumentProcessingException(artifactErrorCode(task), exception);
        } catch (RuntimeException exception) {
            compensate(stored, exception);
            throw exception;
        }
    }

    private void transitionDocumentToParsed(KbDocument document) {
        if (document.getStatus() != DocumentStatus.PARSED) {
            documentDomainService.casTransitionStatus(document.getId(), document.getStatus(), DocumentStatus.PARSED);
        }
    }

    /**
     * 首次分块失败时将仍指向该任务输入的上传文档标记为失败。
     *
     * @param task 当前分块任务
     * @param exception 当前执行异常
     */
    private void markInitialChunkFailed(KbProcessingTask task, RuntimeException exception) {
        if (task.getTaskType() != ProcessingTaskType.CHUNK || isTaskInputSuperseded(exception)) {
            return;
        }
        KbDocument document = documentDomainService.getById(task.getSourceDocumentId());
        KbDocumentResult current = document == null ? null : resultDomainService.findCurrentByDocumentId(document.getId());
        if (document == null || Boolean.TRUE.equals(document.getDeleted()) || current == null
                || !task.getInputResultKey().equals(current.getResultKey())
                || document.getStatus() != DocumentStatus.UPLOADED) {
            return;
        }
        documentDomainService.casTransitionStatus(document.getId(), DocumentStatus.UPLOADED, DocumentStatus.FAILED);
    }

    /**
     * 判断异常是否表示任务输入已被新版本结果替代。
     *
     * @param exception 当前执行异常
     * @return 输入已被替代时返回 {@code true}
     */
    private boolean isTaskInputSuperseded(RuntimeException exception) {
        return exception instanceof DocumentProcessingException documentException
                && Rag2OkfResultCode.TASK_INPUT_SUPERSEDED.getCode().equals(documentException.getCode());
    }

    /**
     * 解析制品读取失败时按首次分块或重新分块选择对应的安全错误码。
     *
     * @param task 当前分块任务
     * @return 可对外登记的安全错误码
     */
    private Rag2OkfResultCode artifactErrorCode(KbProcessingTask task) {
        return task.getTaskType() == ProcessingTaskType.CHUNK
                ? Rag2OkfResultCode.TASK_EXECUTION_ERROR
                : Rag2OkfResultCode.RECHUNK_ARTIFACT_ERROR;
    }

    /**
     * 未预期异常时按首次分块或重新分块选择对应的安全错误码。
     *
     * @param task 当前分块任务
     * @return 可对外登记的安全错误码
     */
    private Rag2OkfResultCode unexpectedErrorCode(KbProcessingTask task) {
        return task.getTaskType() == ProcessingTaskType.CHUNK
                ? Rag2OkfResultCode.TASK_EXECUTION_ERROR
                : Rag2OkfResultCode.RECHUNK_UNEXPECTED_ERROR;
    }

    private KbProcessingTask requireQueuedTask(String taskKey) {
        KbProcessingTask task = taskDomainService.findByTaskKey(taskKey);
        if (task == null || !isChunkTask(task.getTaskType())
                || task.getStatus() != ProcessingTaskStatus.QUEUED) {
            throw new DocumentProcessingException(Rag2OkfResultCode.TASK_INPUT_SUPERSEDED);
        }
        return task;
    }

    private ChunkTaskSnapshot readSnapshot(KbProcessingTask task) {
        try {
            ChunkTaskSnapshot snapshot = JSON.parseObject(task.getSnapshotJson(), ChunkTaskSnapshot.class);
            if (snapshot == null) {
                throw new DocumentProcessingException(Rag2OkfResultCode.PAYLOAD_INVALID);
            }
            return snapshot;
        } catch (DocumentProcessingException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new DocumentProcessingException(Rag2OkfResultCode.PAYLOAD_INVALID, exception);
        }
    }

    private void requireCurrentInput(KbProcessingTask task, ChunkTaskSnapshot snapshot,
                                     KbDocument document, KbDocumentResult result) {
        KbDocumentResult current = document == null ? null : resultDomainService.findCurrentByDocumentId(document.getId());
        if (document == null || Boolean.TRUE.equals(document.getDeleted()) || result == null
                || current == null || !result.getResultKey().equals(current.getResultKey())
                || !task.getInputResultKey().equals(snapshot.resultKey())
                || !task.getInputResultKey().equals(result.getResultKey())
                || !snapshot.expectedResultVersion().equals(result.getVersion())
                || !isExpectedResultStage(task.getTaskType(), result)
                || result.getParsedDocumentObjectKey() == null || result.getParsedDocumentObjectKey().isBlank()) {
            throw new DocumentProcessingException(Rag2OkfResultCode.TASK_INPUT_SUPERSEDED);
        }
    }

    private boolean isRechunkable(KbDocumentResult result) {
        return result != null && (result.getStage() == ResultStage.CHUNK || result.getStage() == ResultStage.PUBLISH);
    }

    private boolean isChunkable(KbDocumentResult result) {
        return result != null && result.getStage() == ResultStage.PARSE
                && result.getParsedDocumentObjectKey() != null && !result.getParsedDocumentObjectKey().isBlank();
    }

    private boolean isChunkTask(ProcessingTaskType taskType) {
        return taskType == ProcessingTaskType.CHUNK || taskType == ProcessingTaskType.RECHUNK;
    }

    private boolean isExpectedResultStage(ProcessingTaskType taskType, KbDocumentResult result) {
        return taskType == ProcessingTaskType.CHUNK ? isChunkable(result) : isRechunkable(result);
    }

    private ChunkPolicy readFrozenChunkPolicy(KbDocumentResult result) {
        if (result == null || result.getPolicySnapshotJson() == null || result.getPolicySnapshotJson().isBlank()) {
            return null;
        }
        try {
            ChunkPolicy policy = JSON.parseObject(result.getPolicySnapshotJson(), ChunkPolicy.class);
            return policy == null || policy.boundaryType() == null || policy.hierarchyType() == null
                    ? null : policy;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private ChunkPolicy resolvePolicy(ChunkPolicyRequest requested) {
        if (requested == null || requested.getBoundaryType() == null || requested.getHierarchyType() == null) {
            return null;
        }
        return new ChunkPolicy(requested.getBoundaryType(), requested.getHierarchyType(),
                requested.getParameters() == null ? Map.of() : Map.copyOf(requested.getParameters()));
    }

    private Map<String, ModelProfileReference> freezeModelProfileRefs(Long knowledgeBaseId) {
        List<KbModelBinding> bindings = modelBindingDomainService.listByKnowledgeBaseId(knowledgeBaseId);
        List<KbModelBinding> active = bindings == null ? List.of() : bindings.stream()
                .filter(binding -> binding.getStatus() == ModelBindingStatus.ACTIVE
                        && binding.getModelProfileId() != null)
                .toList();
        Map<Long, String> profileKeys = modelProfileDomainService.findProfileKeysByIds(
                active.stream().map(KbModelBinding::getModelProfileId).collect(Collectors.toSet()));
        Map<String, ModelProfileReference> refs = new LinkedHashMap<>();
        for (KbModelBinding binding : active) {
            String profileKey = profileKeys.get(binding.getModelProfileId());
            if (profileKey != null) {
                refs.put(binding.getUsageType().getValue(), new ModelProfileReference(profileKey, null));
            }
        }
        return Map.copyOf(refs);
    }

    private void compensate(FonsOssDocumentArtifactService.StoredChunkArtifact stored, Exception original) {
        if (stored == null) {
            return;
        }
        try {
            artifactService.deleteChunk(stored);
        } catch (RuntimeException cleanupFailure) {
            original.addSuppressed(cleanupFailure);
        }
    }
}
