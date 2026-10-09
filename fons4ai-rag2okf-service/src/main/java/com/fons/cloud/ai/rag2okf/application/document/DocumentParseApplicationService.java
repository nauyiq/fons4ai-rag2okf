package com.fons.cloud.ai.rag2okf.application.document;

import com.alibaba.fastjson2.JSON;
import com.fons.cloud.ai.rag2okf.common.constants.Rag2OkfResultCode;
import com.fons.cloud.ai.rag2okf.common.constants.document.ProcessingTaskStatus;
import com.fons.cloud.ai.rag2okf.common.constants.document.ProcessingTaskType;
import com.fons.cloud.ai.rag2okf.common.constants.document.ParserType;
import com.fons.cloud.ai.rag2okf.common.constants.document.ResultStage;
import com.fons.cloud.ai.rag2okf.common.constants.document.DocumentStatus;
import com.fons.cloud.ai.rag2okf.common.constants.document.ChunkBoundaryType;
import com.fons.cloud.ai.rag2okf.common.constants.document.ChunkHierarchyType;
import com.fons.cloud.ai.rag2okf.common.constants.knowledgebase.ModelBindingStatus;
import com.fons.cloud.ai.rag2okf.common.constants.user.WorkspaceRole;
import com.fons.cloud.ai.rag2okf.common.exception.document.DocumentProcessingException;
import com.fons.cloud.ai.rag2okf.common.model.document.ParseExecutionContext;
import com.fons.cloud.ai.rag2okf.common.model.document.ParseIntent;
import com.fons.cloud.ai.rag2okf.common.model.document.ParseTaskSnapshot;
import com.fons.cloud.ai.rag2okf.common.model.document.ChunkPolicy;
import com.fons.cloud.ai.rag2okf.common.model.document.DocumentParseWorkflowResult;
import com.fons.cloud.ai.rag2okf.common.model.document.ModelProfileReference;
import com.fons.cloud.ai.rag2okf.common.request.document.DocumentParseRequest;
import com.fons.cloud.ai.rag2okf.common.request.document.ChunkPolicyRequest;
import com.fons.cloud.ai.rag2okf.common.response.document.DocumentParseResponse;
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
import com.fons.cloud.ai.rag2okf.infrastructure.document.parser.DocumentParseWorkflow;
import com.fons.cloud.ai.rag2okf.infrastructure.document.parser.ParseRecognizer;
import com.fons.cloud.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 新三表 PARSE 任务应用编排。
 *
 * <p>任务状态、快照/当前输入校验、解析、规范制品写入与 CAS 登记按顺序执行。
 * 模型或 MinIO 长调用不持有数据库事务。</p>
 *
 * @author hongqy
 */
@Service
@RequiredArgsConstructor
public class DocumentParseApplicationService {

    private static final ChunkPolicy DEFAULT_CHUNK_POLICY =
            new ChunkPolicy(ChunkBoundaryType.RECURSIVE, ChunkHierarchyType.PARENT_CHILD, Map.of());

    private final KbProcessingTaskDomainService taskDomainService;
    private final KbDocumentDomainService documentDomainService;
    private final KbDocumentResultDomainService resultDomainService;
    private final FonsOssDocumentArtifactService artifactService;
    private final DocumentParseWorkflow parseWorkflow;
    private final ParseRecognizer parseRecognizer;
    private final DocumentChunkApplicationService chunkApplicationService;
    private final SaTokenCurrentUserContext currentUserContext;
    private final KbKnowledgeBaseDomainService knowledgeBaseDomainService;
    private final KbWorkspaceDomainService workspaceDomainService;
    private final KbModelBindingDomainService modelBindingDomainService;
    private final KbModelProfileDomainService modelProfileDomainService;
    private final TransactionTemplate transactionTemplate;

    /**
     * 发起手动解析并原子创建新的结果版本和 PARSE 任务。
     *
     * <p>权限、解析器与策略预检先于持久化；每次调用创建独立任务。
     * 新任务只通过 retryOfTaskId 追溯最近失败任务，不修改旧终态事实。
     * 预期业务校验失败直接以 {@link R#failed} 返回稳定错误码，不抛异常。</p>
     *
     * @param documentKey 文档业务标识
     * @param request 手动解析请求，可空时使用 Built-in 默认策略
     * @return 安全的任务受理响应
     */
    public R<DocumentParseResponse> startParse(String documentKey, DocumentParseRequest request) {
        // 步骤 1：解析文档与知识库，建立手动解析用例的业务边界。
        KbUser user = currentUserContext.requireCurrentUser();
        KbDocument document = documentDomainService.findByDocumentKey(documentKey);
        if (document == null || Boolean.TRUE.equals(document.getDeleted())) {
            return R.failed(Rag2OkfResultCode.DOCUMENT_DELETED);
        }
        KbKnowledgeBase knowledgeBase = knowledgeBaseDomainService.getById(document.getKnowledgeBaseId());
        if (knowledgeBase == null) {
            return R.failed(Rag2OkfResultCode.KNOWLEDGE_BASE_NOT_FOUND);
        }
        // 步骤 2：通过用户—工作空间聚合完成管理员权限校验，并取得所属工作空间事实。
        UserWorkspaceAggregate access = workspaceDomainService.findUserWorkspaceAggregate(user.getId(), knowledgeBase.getWorkspaceId());
        access.requireAccess(WorkspaceRole.ADMIN);
        KbWorkspace workspace = access.getWorkspace();

        // 步骤 3：冻结分块策略；解析器由已保存的源文件事实识别，不信任请求值。
        DocumentParseRequest effective = request == null
                ? new DocumentParseRequest(ParserType.BUILT_IN, null, false) : request;
        ChunkPolicy chunkPolicy = resolveChunkPolicy(effective.getChunkPolicy());
        if (chunkPolicy == null) {
            return R.failed(Rag2OkfResultCode.CHUNK_POLICY_INVALID);
        }
        // 步骤 4：确认源文件结果存在，并冻结跨域的非秘密模型档案引用。
        KbDocumentResult source = resultDomainService.findCurrentByDocumentId(document.getId());
        if (source == null) {
            return R.failed(Rag2OkfResultCode.DOCUMENT_SOURCE_ARTIFACT_ERROR);
        }
        ParseIntent parseIntent = parseRecognizer.recognize(
                source.getSourceOriginalFilename(), source.getSourceContentType());
        ParserType parserType = parseIntent.parserType();
        if ((effective.getParserType() != null && effective.getParserType() != parserType)
                || !parseWorkflow.isEnabled(parserType)) {
            return R.failed(Rag2OkfResultCode.PARSER_NOT_AVAILABLE);
        }
        Map<String, ModelProfileReference> profileRefs =
                freezeModelProfileRefs(knowledgeBase.getId());
        // 步骤 5：在短事务中创建新的结果版本与 PARSE 任务，不修改历史任务事实。
        KbProcessingTask actual = transactionTemplate.execute(status -> persistManualParse(
                user, workspace, knowledgeBase, document, source, parserType, chunkPolicy,
                profileRefs, effective.isPublishAfterSuccess()));
        // 步骤 6：新结果初始阶段和解析器已由本次冻结输入确定，无需额外查询结果表。
        return R.ok(new DocumentParseResponse(
                actual.getTaskKey(), ResultStage.INIT, parserType, actual.getStatus()));
    }

    private KbProcessingTask persistManualParse(
            KbUser user, KbWorkspace workspace, KbKnowledgeBase knowledgeBase,
            KbDocument document, KbDocumentResult source, ParserType parserType,
            ChunkPolicy chunkPolicy,
            Map<String, ModelProfileReference> profileRefs,
            boolean publishAfterSuccess) {
        // 步骤 1：复制不可变源文件指针，创建独立的结果版本。
        KbDocumentResult result = KbDocumentResult.create(document.getId(), source.getSourceUploadActorId(),
                new KbDocumentResult.SourceFilePointer(
                        source.getSourceFileToken(), source.getSourceObjectKey(),
                        source.getSourceOriginalFilename(), source.getSourceContentType(),
                        source.getSourceSizeBytes(), source.getSourceSha256()));
        // 步骤 2：冻结任务输入；快照只携带模型引用，绝不携带连接密文。
        ParseTaskSnapshot snapshot = ParseTaskSnapshot.of(
                workspace.getWorkspaceKey(), knowledgeBase.getKnowledgeBaseKey(), document.getDocumentKey(),
                source.getSourceFileToken(), parserType, null, chunkPolicy, profileRefs,
                publishAfterSuccess, user.getId(), new Date());
        String snapshotJson = JSON.toJSONString(snapshot);
        result.configureParse(parserType, snapshotJson, chunkPolicy.boundaryType(),
                chunkPolicy.hierarchyType(), JSON.toJSONString(chunkPolicy));
        resultDomainService.save(result);

        // 步骤 3：只读取最近失败任务作为追溯关系，再创建新的独立任务事实。
        KbProcessingTask failed = taskDomainService.findLatestFailed(
                document.getId(), ProcessingTaskType.PARSE);
        KbProcessingTask created = KbProcessingTask.create(
                workspace.getId(), knowledgeBase.getId(), document.getId(), ProcessingTaskType.PARSE,
                result.getResultKey(), snapshotJson, failed != null ? failed.getId() : null);
        return taskDomainService.create(created);
    }

    private ChunkPolicy resolveChunkPolicy(ChunkPolicyRequest requested) {
        if (requested == null) {
            return DEFAULT_CHUNK_POLICY;
        }
        if (requested.getBoundaryType() == null || requested.getHierarchyType() == null) {
            return null;
        }
        return new ChunkPolicy(
                requested.getBoundaryType(), requested.getHierarchyType(), requested.getParameters());
    }

    private Map<String, ModelProfileReference> freezeModelProfileRefs(Long knowledgeBaseId) {
        List<KbModelBinding> bindings = modelBindingDomainService.listByKnowledgeBaseId(knowledgeBaseId);
        List<KbModelBinding> active = bindings == null ? List.of() : bindings.stream()
                .filter(binding -> binding.getStatus() == ModelBindingStatus.ACTIVE
                        && binding.getModelProfileId() != null)
                .toList();
        if (active.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> profileKeys = modelProfileDomainService.findProfileKeysByIds(
                active.stream().map(KbModelBinding::getModelProfileId).collect(Collectors.toSet()));
        Map<String, ModelProfileReference> refs = new LinkedHashMap<>();
        for (KbModelBinding binding : active) {
            String profileKey = profileKeys.get(binding.getModelProfileId());
            if (profileKey != null) {
                refs.put(binding.getUsageType().getValue(),
                        new ModelProfileReference(profileKey, null));
            }
        }
        return Map.copyOf(refs);
    }

    public void execute(String taskKey) {
        // 步骤 1：校验待执行任务并通过 CAS 抢占 QUEUED→RUNNING。
        KbProcessingTask task = requireTask(taskKey);
        taskDomainService.casTransitionStatus(
                task.getId(), ProcessingTaskStatus.QUEUED, ProcessingTaskStatus.RUNNING);
        try {
            // 步骤 2：执行解析、对象存储写入和结果阶段提交。
            executeRunning(task);
            // 步骤 3：所有制品与结果均提交后，CAS 标记任务成功。
            taskDomainService.casTransitionStatus(
                    task.getId(), ProcessingTaskStatus.RUNNING, ProcessingTaskStatus.SUCCEEDED);
        } catch (DocumentProcessingException exception) {
            // 步骤 4：预期业务失败按稳定错误码登记，必要时同步当前文档失败状态。
            markDocumentFailed(task, exception);
            fail(task, exception.getCode(), exception.getMessage());
            throw exception;
        } catch (RuntimeException exception) {
            // 步骤 5：未预期异常统一转换为安全错误码，避免暴露底层异常信息。
            markDocumentFailed(task, exception);
            fail(task, Rag2OkfResultCode.PARSE_UNEXPECTED_ERROR.getCode(),
                    Rag2OkfResultCode.PARSE_UNEXPECTED_ERROR.getMessage());
            throw new DocumentProcessingException(Rag2OkfResultCode.PARSE_UNEXPECTED_ERROR, exception);
        }
    }

    private void executeRunning(KbProcessingTask task) {
        // 步骤 1：恢复冻结快照，并验证任务、文档、结果仍指向同一份当前输入。
        ParseTaskSnapshot snapshot = readSnapshot(task);
        KbDocument document = documentDomainService.getById(task.getSourceDocumentId());
        KbDocumentResult result = resultDomainService.findByResultKey(task.getInputResultKey());
        requireCurrentInput(task, snapshot, document, result);

        FonsOssDocumentArtifactService.StoredParsedArtifacts storedParsed = null;
        boolean parsedCommitted = false;
        try {
            // 步骤 2：构造只含冻结事实和可重复打开源流的技术上下文。
            ParseExecutionContext context = new ParseExecutionContext(
                    snapshot.workspaceKey(), snapshot.knowledgeBaseKey(),
                    snapshot.documentKey(), result.getResultKey(),
                    result.getVersion(), snapshot.requestedBy(),
                    result.getSourceFileToken(), result.getSourceObjectKey(),
                    result.getSourceOriginalFilename(), result.getSourceContentType(),
                    result.getSourceSha256(), snapshot.parserType(),
                    snapshot.modelProfileRefs(),
                    () -> artifactService.openSource(result.getSourceObjectKey()).inputStream());
            // 步骤 3：基础设施流水线只完成解析、规范化、校验和稳定编码。
            DocumentParseWorkflowResult workflowResult = parseWorkflow.process(context);
            storedParsed = artifactService.storeParsed(
                    new FonsOssDocumentArtifactService.ParsedArtifactCommand(
                            snapshot.workspaceKey(), snapshot.knowledgeBaseKey(),
                            snapshot.documentKey(), result.getResultKey(),
                            workflowResult.getParsedDocumentJson(), workflowResult.getParsedMarkdown()));
            // 步骤 4：短事务内 CAS 登记解析制品并创建独立 CHUNK 任务。
            // 任何数据库提交失败都会回滚，随后补偿本次尚未登记成功的 MinIO 制品。
            FonsOssDocumentArtifactService.StoredParsedArtifacts parsedArtifacts = storedParsed;
            transactionTemplate.executeWithoutResult(status -> {
                resultDomainService.commitParsedArtifacts(
                        result.getResultKey(),
                        result.getDocumentId(), result.getVersion(),
                        parsedArtifacts.jsonObjectKey(), parsedArtifacts.markdownObjectKey(),
                        workflowResult.getParsedDocument().blocks().size(),
                        workflowResult.getParsedDocument().warnings().size());
                result.setStage(ResultStage.PARSE);
                result.setVersion(result.getVersion() + 1);
                result.setParsedDocumentObjectKey(parsedArtifacts.jsonObjectKey());
                result.setParsedMarkdownObjectKey(parsedArtifacts.markdownObjectKey());
                chunkApplicationService.createInitialChunkTask(snapshot, task, document, result);
            });
            parsedCommitted = true;
        } catch (RuntimeException exception) {
            // 步骤 5：解析、源流打开或解析制品提交失败采用同一补偿规则。
            if (!parsedCommitted) {
                compensateParsed(storedParsed, exception);
            }
            throw exception;
        }
    }

    private KbProcessingTask requireTask(String taskKey) {
        KbProcessingTask task = taskDomainService.findByTaskKey(taskKey);
        if (task == null || task.getTaskType() != ProcessingTaskType.PARSE
                || task.getStatus() != ProcessingTaskStatus.QUEUED) {
            throw new DocumentProcessingException(Rag2OkfResultCode.TASK_INPUT_SUPERSEDED);
        }
        return task;
    }

    private ParseTaskSnapshot readSnapshot(KbProcessingTask task) {
        try {
            ParseTaskSnapshot snapshot = JSON.parseObject(task.getSnapshotJson(), ParseTaskSnapshot.class);
            if (snapshot == null) {
                throw new DocumentProcessingException(Rag2OkfResultCode.TASK_INPUT_SUPERSEDED);
            }
            return snapshot;
        } catch (DocumentProcessingException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new DocumentProcessingException(Rag2OkfResultCode.TASK_INPUT_SUPERSEDED, exception);
        }
    }

    private void requireCurrentInput(
            KbProcessingTask task,
            ParseTaskSnapshot snapshot,
            KbDocument document,
            KbDocumentResult result) {
        if (document == null || Boolean.TRUE.equals(document.getDeleted())
                || result == null || result.getStage() != ResultStage.INIT
                || !snapshot.documentKey().equals(document.getDocumentKey())
                || !snapshot.sourceFileToken().equals(result.getSourceFileToken())
                || !task.getInputResultKey().equals(result.getResultKey())) {
            throw new DocumentProcessingException(Rag2OkfResultCode.TASK_INPUT_SUPERSEDED);
        }
        KbDocumentResult current = resultDomainService.findCurrentByDocumentId(document.getId());
        if (current == null || !current.getResultKey().equals(result.getResultKey())) {
            throw new DocumentProcessingException(Rag2OkfResultCode.TASK_INPUT_SUPERSEDED);
        }
    }

    private void compensateParsed(
            FonsOssDocumentArtifactService.StoredParsedArtifacts stored,
            Exception original) {
        if (stored == null) {
            return;
        }
        try {
            artifactService.deleteParsed(stored);
        } catch (RuntimeException cleanupFailure) {
            original.addSuppressed(cleanupFailure);
        }
    }

    private void markDocumentFailed(KbProcessingTask task, RuntimeException exception) {
        if (exception instanceof DocumentProcessingException documentException
                && Rag2OkfResultCode.TASK_INPUT_SUPERSEDED.getCode().equals(documentException.getCode())) {
            return;
        }
        KbDocument document = documentDomainService.getById(task.getSourceDocumentId());
        KbDocumentResult current = document == null ? null : resultDomainService.findCurrentByDocumentId(document.getId());
        if (document == null || current == null || !task.getInputResultKey().equals(current.getResultKey())
                || document.getStatus() == DocumentStatus.FAILED || document.getStatus() == DocumentStatus.PUBLISHED
                || document.getStatus() == DocumentStatus.DELETED) {
            return;
        }
        documentDomainService.casTransitionStatus(document.getId(), document.getStatus(), DocumentStatus.FAILED);
    }

    private void fail(KbProcessingTask task, String code, String message) {
        String safeMessage = message == null || message.isBlank()
                ? Rag2OkfResultCode.PARSE_UNEXPECTED_ERROR.getMessage()
                : message;
        taskDomainService.failRunningTask(task.getId(), code, safeMessage);
    }
}
