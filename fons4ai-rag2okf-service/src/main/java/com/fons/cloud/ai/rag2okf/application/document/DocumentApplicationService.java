package com.fons.cloud.ai.rag2okf.application.document;

import com.alibaba.fastjson2.JSON;
import com.fons.cloud.ai.rag2okf.common.constants.Rag2OkfResultCode;
import com.fons.cloud.ai.rag2okf.common.constants.document.DocumentStatus;
import com.fons.cloud.ai.rag2okf.common.constants.document.ParserType;
import com.fons.cloud.ai.rag2okf.common.constants.document.ChunkBoundaryType;
import com.fons.cloud.ai.rag2okf.common.constants.document.ChunkHierarchyType;
import com.fons.cloud.ai.rag2okf.common.constants.document.ProcessingMode;
import com.fons.cloud.ai.rag2okf.common.constants.document.ProcessingTaskType;
import com.fons.cloud.ai.rag2okf.common.constants.knowledgebase.ModelBindingStatus;
import com.fons.cloud.ai.rag2okf.common.constants.user.WorkspaceRole;
import com.fons.cloud.ai.rag2okf.common.exception.document.DocumentProcessingException;
import com.fons.cloud.ai.rag2okf.common.model.document.ParseTaskSnapshot;
import com.fons.cloud.ai.rag2okf.common.model.document.ChunkPolicy;
import com.fons.cloud.ai.rag2okf.common.model.document.ModelProfileReference;
import com.fons.cloud.ai.rag2okf.common.model.document.DocumentFileContent;
import com.fons.cloud.ai.rag2okf.common.model.document.DocumentUploadIntent;
import com.fons.cloud.ai.rag2okf.common.model.document.DocumentUploadOutcome;
import com.fons.cloud.ai.rag2okf.common.model.document.DocumentPrecheckedFile;
import com.fons.cloud.ai.rag2okf.common.model.document.DocumentReadBatch;
import com.fons.cloud.ai.rag2okf.common.model.document.DocumentUploadAccessContext;
import com.fons.cloud.ai.rag2okf.common.model.document.ParseIntent;
import com.fons.cloud.ai.rag2okf.common.request.document.DocumentUploadRequest;
import com.fons.cloud.ai.rag2okf.common.request.document.ChunkPolicyRequest;
import com.fons.cloud.common.result.PageResult;
import com.fons.cloud.ai.rag2okf.common.response.document.BatchDocumentUploadItemResponse;
import com.fons.cloud.ai.rag2okf.common.response.document.DocumentDetailResponse;
import com.fons.cloud.ai.rag2okf.common.response.document.DocumentSummaryResponse;
import com.fons.cloud.ai.rag2okf.common.response.document.DocumentTaskSummaryResponse;
import com.fons.cloud.ai.rag2okf.common.response.document.DocumentUploadResponse;
import com.fons.cloud.ai.rag2okf.common.utils.BusinessKeyGenerator;
import com.fons.cloud.ai.rag2okf.infrastructure.document.parser.DocumentParserRegistry;
import com.fons.cloud.ai.rag2okf.infrastructure.document.parser.ParseRecognizer;
import com.fons.cloud.common.base.exception.BusinessRuntimeException;
import com.fons.cloud.common.result.R;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 文档上传、批量上传、列表、详情与源文件下载的应用服务（TP-002 T011～T012）。
 *
 * <p>遵循 DDD-lite：应用服务负责权限校验、事务边界、跨域资源解析、任务创建、
 * MinIO/MySQL 显式补偿与 DTO 转换；文档身份与状态规则归 {@link KbDocument}，
 * 结果生命周期归 {@link KbDocumentResult}，任务事实归 {@link KbProcessingTask}。</p>
 *
 * <h3>编排顺序（设计 §3.2/§4.4）</h3>
 * <ol>
 *   <li>ADMIN 权限校验后解析上传意图：解析器可用性预检（MINERU 在任何 IO 前拒绝）、
 *       chunkPolicy 合法性校验（非法值拒绝无回退）、processingMode 与知识库
 *       autoParse/autoPublish 合成任务创建决策。</li>
 *   <li>每次上传请求都创建独立文档；即使同名同 hash 也不合并。</li>
 *   <li>MinIO 先写（事务外流式写入并计算 SHA-256/大小）+ MySQL 事务
 *       （kb_document + kb_document_result + 可选 kb_processing_task）+ 失败补偿删除。</li>
 * </ol>
 *
 * <h3>当前边界</h3>
 * <p>上传与读取均使用新三表模型；旧上传、替换与旧读链路已退出有效 Controller。
 * 解析、重新分块、发布和删除入口由后续 Task Pack 交付。</p>
 *
 * @author hongqy
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentApplicationService {

    /** 分块策略缺省值：知识库默认策略暂无新模型字段，按未配置处理（设计 §3.2）。 */
    private static final ChunkPolicy DEFAULT_CHUNK_POLICY =
            new ChunkPolicy(ChunkBoundaryType.RECURSIVE, ChunkHierarchyType.PARENT_CHILD, Map.of());

    private final SaTokenCurrentUserContext currentUserContext;
    private final KbKnowledgeBaseDomainService knowledgeBaseDomainService;
    private final KbWorkspaceDomainService workspaceDomainService;
    private final KbDocumentDomainService documentDomainService;
    private final KbDocumentResultDomainService documentResultDomainService;
    private final KbProcessingTaskDomainService processingTaskDomainService;
    private final KbModelBindingDomainService modelBindingDomainService;
    private final KbModelProfileDomainService modelProfileDomainService;
    private final FonsOssDocumentArtifactService documentArtifactService;
    private final DocumentUploadPrecheckPolicy uploadPrecheckPolicy;
    private final DocumentParserRegistry parserRegistry;
    private final ParseRecognizer parseRecognizer;
    private final TransactionTemplate transactionTemplate;

    // ────────────────────────────── 新上传契约（T011） ──────────────────────────────

    /**
     * 上传新文档：无条件创建独立文档，同名不合并（AC-001）。
     *
     * <p>{@code processingMode} 决定是否创建 PARSE 任务：SKIP 仅保存；DEFAULT 读取
     * 知识库 autoParse/autoPublish 冻结进快照；PARSE 无条件创建任务并冻结
     * parserType、chunkPolicy、非秘密 modelProfileRefs 与 publishAfterSuccess。
     * 上传成功不因知识库未绑定模型而拒绝（资源校验延后解析期，AC-004）。</p>
     *
     * @param knowledgeBaseKey 知识库业务标识
     * @param file             上传文件
     * @param request          上传请求契约，可为 {@code null}（按 DEFAULT 处理）
     * @return 上传受理响应，不含数据库 id、objectKey 或凭证；预期校验失败直接返回失败结果
     */
    public R<DocumentUploadResponse> uploadDocument(
            String knowledgeBaseKey, MultipartFile file,
            DocumentUploadRequest request) {
        R<DocumentUploadAccessContext> accessResult = resolveUploadAccess(knowledgeBaseKey);
        if (!accessResult.isSuccess()) {
            return R.failed(accessResult);
        }
        return uploadDocument(accessResult.getData(), file, request);
    }

    private R<DocumentUploadResponse> uploadDocument(
            DocumentUploadAccessContext accessContext, MultipartFile file, DocumentUploadRequest request) {
        KbUser user = accessContext.getUser();
        KbKnowledgeBase knowledgeBase = accessContext.getKnowledgeBase();
        KbWorkspace workspace = accessContext.getWorkspace();

        DocumentUploadRequest effectiveRequest = request != null
                ? request : new DocumentUploadRequest(ProcessingMode.DEFAULT, null, null);
        R<DocumentPrecheckedFile> precheckResult = precheckFile(file);
        if (!precheckResult.isSuccess()) {
            return R.failed(precheckResult);
        }
        DocumentPrecheckedFile precheckedFile = precheckResult.getData();
        R<DocumentUploadIntent> intentResult = resolveUploadIntent(
                knowledgeBase, effectiveRequest, precheckedFile);
        if (!intentResult.isSuccess()) {
            return R.failed(intentResult);
        }
        DocumentUploadIntent intent = intentResult.getData();
        String documentKey = BusinessKeyGenerator.nextKey();
        String fileToken = BusinessKeyGenerator.nextKey();

        // MinIO 先写（事务外流式写入），失败时制品服务内部已补偿，无需 MySQL 回滚
        FonsOssDocumentArtifactService.StoredSourceArtifact storedArtifact =
                documentArtifactService.storeSource(new FonsOssDocumentArtifactService.SourceArtifactCommand(
                        workspace.getWorkspaceKey(), knowledgeBase.getKnowledgeBaseKey(),
                        documentKey, fileToken, precheckedFile.getFilename(),
                        precheckedFile.getContentType(), precheckedFile.getInputStream()));

        DocumentUploadOutcome outcome;
        try {
            outcome = transactionTemplate.execute(status -> persistUpload(
                    knowledgeBase, workspace, user, precheckedFile, fileToken, storedArtifact, intent));
        } catch (RuntimeException exception) {
            compensateDeleteSource(storedArtifact.objectKey(), exception);
            throw exception;
        }
        return R.ok(toUploadResponse(outcome, effectiveRequest.getProcessingMode()));
    }

    /**
     * 批量上传：整批预检后逐项独立处理，单项失败不影响其他项（AC-002）。
     *
     * <p>文件数或总量超限时整批预检拒绝，不进入逐项处理。
     * 失败项只携带稳定错误码与安全化摘要。</p>
     *
     * @param knowledgeBaseKey 知识库业务标识
     * @param files            上传文件列表，不可为空
     * @param request          上传请求契约，对批内每项一致生效
     * @return 每项独立成功/失败结果，顺序与入参一致；整批预检失败直接返回失败结果
     */
    public R<List<BatchDocumentUploadItemResponse>> batchUploadDocuments(
            String knowledgeBaseKey, List<MultipartFile> files,
            DocumentUploadRequest request) {
        R<Void> batchPrecheck = uploadPrecheckPolicy.precheckBatch(files);
        if (!batchPrecheck.isSuccess()) {
            return R.failed(batchPrecheck);
        }
        R<DocumentUploadAccessContext> accessResult = resolveUploadAccess(knowledgeBaseKey);
        if (!accessResult.isSuccess()) {
            return R.failed(accessResult);
        }
        DocumentUploadAccessContext accessContext = accessResult.getData();
        List<BatchDocumentUploadItemResponse> items = new ArrayList<>(files.size());
        for (int index = 0; index < files.size(); index++) {
            MultipartFile file = files.get(index);
            String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown";
            try {
                R<DocumentUploadResponse> upload = uploadDocument(accessContext, file, request);
                if (upload.isSuccess()) {
                    items.add(BatchDocumentUploadItemResponse.success(
                            index, filename, upload.getData()));
                } else {
                    items.add(BatchDocumentUploadItemResponse.failure(
                            index, filename, upload.getCode(), upload.getMessage()));
                }
            } catch (BusinessRuntimeException exception) {
                items.add(BatchDocumentUploadItemResponse.failure(
                        index, filename, exception.getCode(), exception.getMessage()));
            } catch (RuntimeException exception) {
                log.warn("批量上传单项处理失败: filename={}", filename, exception);
                items.add(BatchDocumentUploadItemResponse.failure(
                        index, filename,
                        Rag2OkfResultCode.TASK_EXECUTION_ERROR.getCode(),
                        Rag2OkfResultCode.TASK_EXECUTION_ERROR.getMessage()));
            }
        }
        return R.ok(items);
    }

    /**
     * 解析上传意图：解析器可用性预检、分块策略校验与任务创建决策。
     *
     * <p>解析器类型以通过安全预检的文件事实识别为准；显式请求与识别结果不一致时直接
     * {@code PARSER_NOT_AVAILABLE}，不创建文档与任务、不调用其他 Parser（AC-003、AC-007）。
     * chunkPolicy 只要提供就严格校验，非法值拒绝且不做默认回退；未提供时使用
     * 缺省 RECURSIVE+PARENT_CHILD。</p>
     */
    private R<DocumentUploadIntent> resolveUploadIntent(
            KbKnowledgeBase knowledgeBase, DocumentUploadRequest request,
            DocumentPrecheckedFile precheckedFile) {
        ProcessingMode mode = request.getProcessingMode() != null
                ? request.getProcessingMode() : ProcessingMode.DEFAULT;
        ParseIntent parseIntent = parseRecognizer.recognize(precheckedFile);
        ParserType parserType = parseIntent.parserType();
        if (request.getParserType() != null && request.getParserType() != parserType) {
            return R.failed(Rag2OkfResultCode.PARSER_NOT_AVAILABLE);
        }
        if (!parserRegistry.isEnabled(parserType)) {
            return R.failed(Rag2OkfResultCode.PARSER_NOT_AVAILABLE);
        }
        ChunkPolicy chunkPolicy = resolveChunkPolicy(request.getChunkPolicy());
        if (chunkPolicy == null) {
            return R.failed(Rag2OkfResultCode.CHUNK_POLICY_INVALID);
        }
        boolean createParseTask = switch (mode) {
            case SKIP -> false;
            case PARSE -> true;
            case DEFAULT -> Boolean.TRUE.equals(knowledgeBase.getAutoParse());
        };
        // DEFAULT 冻结知识库默认；PARSE/SKIP 模式下 publishAfterSuccess 同样以知识库 autoPublish 为准（上传契约不含显式字段）
        boolean publishAfterSuccess = Boolean.TRUE.equals(knowledgeBase.getAutoPublish());
        Map<String, ModelProfileReference> modelProfileRefs =
                createParseTask ? freezeModelProfileRefs(knowledgeBase.getId()) : Map.of();
        return R.ok(new DocumentUploadIntent(createParseTask, parserType, chunkPolicy, publishAfterSuccess, modelProfileRefs));
    }

    /**
     * 解析并校验分块策略请求。
     *
     * @param requested 请求分块策略，{@code null} 时返回缺省 RECURSIVE+PARENT_CHILD
     * @return 已冻结的分块策略快照；边界或层级策略非法时返回 {@code null}
     */
    private ChunkPolicy resolveChunkPolicy(ChunkPolicyRequest requested) {
        if (requested == null) {
            return DEFAULT_CHUNK_POLICY;
        }
        ChunkBoundaryType boundaryType = requested.getBoundaryType();
        ChunkHierarchyType hierarchyType = requested.getHierarchyType();
        if (boundaryType == null || hierarchyType == null) {
            return null;
        }
        Map<String, Object> parameters = requested.getParameters() == null
                ? null : Map.copyOf(requested.getParameters());
        return new ChunkPolicy(boundaryType, hierarchyType, parameters);
    }

    /**
     * 冻结知识库启用绑定对应的非秘密模型档案引用。
     *
     * <p>只冻结 profileKey 业务引用，不复制 Base URL、API Key 或连接密文；档案已删除
     * 的绑定跳过（上传不因缺少解析资源被拒绝，AC-004），运行期引用失效由任务执行
     * fail-closed 处理。</p>
     */
    private Map<String, ModelProfileReference> freezeModelProfileRefs(Long knowledgeBaseId) {
        List<KbModelBinding> bindings = modelBindingDomainService.listByKnowledgeBaseId(knowledgeBaseId);
        List<KbModelBinding> activeBindings = bindings == null ? List.of() : bindings.stream()
                .filter(binding -> binding.getStatus() == ModelBindingStatus.ACTIVE
                        && binding.getModelProfileId() != null)
                .toList();
        if (activeBindings.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> profileKeys = modelProfileDomainService.findProfileKeysByIds(
                activeBindings.stream().map(KbModelBinding::getModelProfileId).collect(Collectors.toSet()));
        Map<String, ModelProfileReference> refs = new LinkedHashMap<>();
        for (KbModelBinding binding : activeBindings) {
            String profileKey = profileKeys.get(binding.getModelProfileId());
            if (profileKey != null) {
                refs.put(binding.getUsageType().getValue(),
                        new ModelProfileReference(profileKey, null));
            }
        }
        return Map.copyOf(refs);
    }

    /**
     * 单文件预检：文件名净化、扩展名白名单、大小与魔数（设计 §4.2 校验顺序）。
     */
    private R<DocumentPrecheckedFile> precheckFile(MultipartFile file) {
        if (file == null) {
            return R.failed(Rag2OkfResultCode.PAYLOAD_INVALID);
        }
        try {
            return uploadPrecheckPolicy.precheck(
                    file.getOriginalFilename(), file.getInputStream(), file.getSize());
        } catch (IOException exception) {
            return R.failed(Rag2OkfResultCode.DOCUMENT_FILE_SECURITY_REJECTED);
        }
    }

    /**
     * 事务内持久化上传结果：文档身份、结果底座与可选 PARSE 任务。
     *
     * <p>创建任务时把同一份 ParseTaskSnapshot JSON 冻结到任务快照列与结果表的
     * 解析器快照列，保证两侧冻结事实一致；任务保持 QUEUED（TP-002 无执行器，
     * 属已知中间态）。文档初始状态 UPLOADED、结果初始阶段 INIT 由实体工厂保证。</p>
     */
    private DocumentUploadOutcome persistUpload(
            KbKnowledgeBase knowledgeBase, KbWorkspace workspace, KbUser user,
            DocumentPrecheckedFile precheckedFile, String fileToken,
            FonsOssDocumentArtifactService.StoredSourceArtifact storedArtifact,
            DocumentUploadIntent intent) {
        KbDocument document = KbDocument.create(knowledgeBase.getId(), precheckedFile.getFilename());
        documentDomainService.save(document);

        KbDocumentResult result = KbDocumentResult.create(document.getId(), user.getId(),
                new KbDocumentResult.SourceFilePointer(
                        fileToken, storedArtifact.objectKey(), precheckedFile.getFilename(),
                        precheckedFile.getContentType(), storedArtifact.sizeBytes(), storedArtifact.sha256()));

        KbProcessingTask task = null;
        if (intent.isCreateParseTask()) {
            ParseTaskSnapshot snapshot = ParseTaskSnapshot.of(
                    workspace.getWorkspaceKey(), knowledgeBase.getKnowledgeBaseKey(),
                    document.getDocumentKey(), fileToken, intent.getParserType(), null,
                    intent.getChunkPolicy(), intent.getModelProfileRefs(),
                    intent.isPublishAfterSuccess(), user.getId(), new Date());
            String snapshotJson = JSON.toJSONString(snapshot);
            result.configureParse(
                    intent.getParserType(), snapshotJson,
                    intent.getChunkPolicy().boundaryType(), intent.getChunkPolicy().hierarchyType(),
                    JSON.toJSONString(intent.getChunkPolicy()));
            documentResultDomainService.save(result);
            task = processingTaskDomainService.create(KbProcessingTask.create(
                    workspace.getId(), knowledgeBase.getId(), document.getId(),
                    ProcessingTaskType.PARSE, result.getResultKey(),
                    snapshotJson, null));
        } else {
            documentResultDomainService.save(result);
        }
        return new DocumentUploadOutcome(document, result, task);
    }

    /** MySQL 事务失败后补偿删除已写入的 MinIO 源对象；补偿失败不掩盖原始异常。 */
    private void compensateDeleteSource(String objectKey, Exception originalFailure) {
        try {
            documentArtifactService.deleteSource(objectKey);
        } catch (RuntimeException cleanupFailure) {
            log.warn("上传事务失败后的源制品补偿删除失败", cleanupFailure);
            originalFailure.addSuppressed(cleanupFailure);
        }
    }

    private DocumentUploadResponse toUploadResponse(DocumentUploadOutcome outcome, ProcessingMode processingMode) {
        return new DocumentUploadResponse(
                outcome.getDocument().getDocumentKey(),
                outcome.getResult().getSourceFileToken(),
                outcome.getDocument().getDisplayName(),
                modeValue(processingMode),
                outcome.getTask() != null ? outcome.getTask().getTaskKey() : null);
    }

    private String modeValue(ProcessingMode processingMode) {
        return processingMode != null ? processingMode.getValue() : ProcessingMode.DEFAULT.getValue();
    }


    // ────────────────────────────── 新读链路（T012） ──────────────────────────────

    /**
     * 分页查询知识库下的活跃文档视图。
     *
     * @param knowledgeBaseKey 知识库业务标识
     * @param page 页码（从 1 开始）
     * @param size 每页条数
     */
    public R<PageResult<DocumentSummaryResponse>> listDocuments(String knowledgeBaseKey, int page, int size) {
        KbUser user = currentUserContext.requireCurrentUser();
        KbKnowledgeBase knowledgeBase = findKnowledgeBaseByKey(knowledgeBaseKey);
        if (knowledgeBase == null) {
            return R.failed(Rag2OkfResultCode.KNOWLEDGE_BASE_NOT_FOUND);
        }
        workspaceDomainService.findUserWorkspaceAggregate(user.getId(), knowledgeBase.getWorkspaceId())
                .requireAccess(WorkspaceRole.KNOWLEDGE_USER);
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        PageResult<KbDocument> result = documentDomainService.pageActiveByKnowledgeBaseId(
                knowledgeBase.getId(), safePage, safeSize);
        List<KbDocument> documents = result.getResultList();
        DocumentReadBatch readBatch = loadReadBatch(documents);
        PageResult<DocumentSummaryResponse> response = new PageResult<>(safePage, safeSize, result.getTotal(), documents.stream()
                .map(document -> toSummaryResponse(document,
                        readBatch.getCurrentResult(document.getId()),
                        readBatch.getTaskSummaries(document.getId())))
                .toList());
        response.setPages(result.getPages());
        return R.ok(response);
    }

    /**
     * 查询文档详情。需要 USER 权限，不返回对象键、数据库主键或任务快照。
     *
     * @param documentKey 文档业务标识
     * @return 文档详情响应
     */
    public R<DocumentDetailResponse> getDocumentDetail(String documentKey) {
        KbUser user = currentUserContext.requireCurrentUser();
        KbDocument document = documentDomainService.findByDocumentKey(documentKey);
        if (document == null || Boolean.TRUE.equals(document.getDeleted())) {
            return R.failed(Rag2OkfResultCode.DOCUMENT_DELETED);
        }
        KbKnowledgeBase knowledgeBase = knowledgeBaseDomainService.getById(document.getKnowledgeBaseId());
        if (knowledgeBase == null) {
            return R.failed(Rag2OkfResultCode.KNOWLEDGE_BASE_NOT_FOUND);
        }
        WorkspaceRole actualRole = workspaceDomainService
                .findUserWorkspaceAggregate(user.getId(), knowledgeBase.getWorkspaceId())
                .requireAccess(WorkspaceRole.KNOWLEDGE_USER);
        KbDocumentResult result = documentResultDomainService.findCurrentByDocumentId(document.getId());
        if (result == null) {
            return R.failed(Rag2OkfResultCode.DOCUMENT_SOURCE_ARTIFACT_ERROR);
        }
        List<DocumentTaskSummaryResponse> tasks = latestTasksByType(List.of(document.getId()))
                .getOrDefault(document.getId(), List.of());
        return R.ok(toDetailResponse(document, knowledgeBase, result, tasks, actualRole));
    }

    /**
     * 打开文档当前源文件读取流。需要 USER 权限，调用方负责关闭流。
     *
     * <p>返回二进制流，无法使用 {@link R} 表达失败，因此校验失败以
     * {@link DocumentProcessingException} 抛出，由全局异常端点统一转为 {@code R.failed}。</p>
     *
     * @param documentKey 文档业务标识
     * @return 文件内容与元数据
     * @throws DocumentProcessingException 文档不存在、知识库/工作空间缺失或当前结果缺失时抛出
     */
    public DocumentFileContent downloadDocumentFile(String documentKey) {
        KbUser user = currentUserContext.requireCurrentUser();
        KbDocument document = documentDomainService.findByDocumentKey(documentKey);
        if (document == null || Boolean.TRUE.equals(document.getDeleted())) {
            throw new DocumentProcessingException(Rag2OkfResultCode.DOCUMENT_DELETED);
        }
        KbKnowledgeBase knowledgeBase = knowledgeBaseDomainService.getById(document.getKnowledgeBaseId());
        if (knowledgeBase == null) {
            throw new DocumentProcessingException(Rag2OkfResultCode.KNOWLEDGE_BASE_NOT_FOUND);
        }
        workspaceDomainService.findUserWorkspaceAggregate(user.getId(), knowledgeBase.getWorkspaceId())
                .requireAccess(WorkspaceRole.KNOWLEDGE_USER);
        KbDocumentResult result = documentResultDomainService.findCurrentByDocumentId(document.getId());
        if (result == null) {
            throw new DocumentProcessingException(Rag2OkfResultCode.DOCUMENT_SOURCE_ARTIFACT_ERROR);
        }
        FonsOssDocumentArtifactService.SourceArtifactContent content =
                documentArtifactService.openSource(result.getSourceObjectKey());
        return new DocumentFileContent(
                result.getSourceOriginalFilename(),
                result.getSourceContentType(),
                result.getSourceSizeBytes(),
                content.inputStream());
    }

    // ────────────────────────────── 通用辅助 ──────────────────────────────

    private KbKnowledgeBase findKnowledgeBaseByKey(String knowledgeBaseKey) {
        return knowledgeBaseDomainService.findByKnowledgeBaseKey(knowledgeBaseKey);
    }

    /** 解析单次上传用例共享的用户、知识库和工作空间授权事实。 */
    private R<DocumentUploadAccessContext> resolveUploadAccess(String knowledgeBaseKey) {
        KbUser user = currentUserContext.requireCurrentUser();
        KbKnowledgeBase knowledgeBase = findKnowledgeBaseByKey(knowledgeBaseKey);
        if (knowledgeBase == null) {
            return R.failed(Rag2OkfResultCode.KNOWLEDGE_BASE_NOT_FOUND);
        }
        UserWorkspaceAggregate userWorkspace = workspaceDomainService
                .findUserWorkspaceAggregate(user.getId(), knowledgeBase.getWorkspaceId());
        userWorkspace.requireAccess(WorkspaceRole.ADMIN);
        return R.ok(new DocumentUploadAccessContext(user, knowledgeBase, userWorkspace));
    }

    private DocumentSummaryResponse toSummaryResponse(
            KbDocument document, KbDocumentResult result, List<DocumentTaskSummaryResponse> tasks) {
        return new DocumentSummaryResponse(document.getDocumentKey(), document.getDisplayName(),
                toCurrentFile(result), document.getStatus(), result != null ? result.getStage() : null,
                tasks, document.getUpdated());
    }

    private DocumentDetailResponse toDetailResponse(
            KbDocument document, KbKnowledgeBase knowledgeBase, KbDocumentResult result,
            List<DocumentTaskSummaryResponse> tasks, WorkspaceRole actualRole) {
        return new DocumentDetailResponse(
                document.getDocumentKey(), knowledgeBase.getKnowledgeBaseKey(), document.getDisplayName(),
                toCurrentFile(result), document.getStatus(), result.getStage(),
                new DocumentDetailResponse.ParseSummary(
                        result.getParserType(), result.getBlockCount(), result.getWarningCount()),
                new DocumentDetailResponse.ChunkSummary(
                        result.getBoundaryType() == null ? null : result.getBoundaryType().getValue(),
                        result.getHierarchyType() == null ? null : result.getHierarchyType().getValue(),
                        result.getParentCount(),
                        result.getChildCount(), result.getTotalCount()),
                new DocumentDetailResponse.PublicationSummary(
                        result.getProjectionCount(), result.getPublishedAt()),
                actualRole == WorkspaceRole.ADMIN ? document.getCleanupStatus() : null,
                tasks, availableActions(document), document.getUpdated());
    }

    private DocumentSummaryResponse.CurrentFileSummary toCurrentFile(KbDocumentResult result) {
        if (result == null) {
            return null;
        }
        return new DocumentSummaryResponse.CurrentFileSummary(
                result.getSourceFileToken(), result.getSourceOriginalFilename(),
                result.getSourceContentType(), result.getSourceSizeBytes(), result.getSourceSha256());
    }

    /** 当前 T012 已开放动作；后续任务包交付解析、分块、发布和删除入口时扩展。 */
    private List<String> availableActions(KbDocument document) {
        return document.getStatus() == DocumentStatus.DELETED
                ? List.of() : List.of("DOWNLOAD");
    }

    /**
     * 一页文档固定使用两次批量查询装配当前结果和每种类型最近任务，避免 N+1。
     */
    private DocumentReadBatch loadReadBatch(List<KbDocument> documents) {
        if (documents.isEmpty()) {
            return DocumentReadBatch.empty();
        }
        List<Long> documentIds = documents.stream().map(KbDocument::getId).toList();
        return new DocumentReadBatch(
                documentResultDomainService.findCurrentByDocumentIds(documentIds),
                latestTasksByType(documentIds));
    }

    private Map<Long, List<DocumentTaskSummaryResponse>> latestTasksByType(List<Long> documentIds) {
        if (documentIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<KbProcessingTask>> latest =
                processingTaskDomainService.findLatestBySourceDocumentIds(documentIds);
        Map<Long, List<DocumentTaskSummaryResponse>> summaries = new LinkedHashMap<>();
        latest.forEach((documentId, tasks) -> summaries.put(documentId, tasks.stream()
                .map(this::toTaskSummary)
                .toList()));
        return summaries;
    }

    private DocumentTaskSummaryResponse toTaskSummary(KbProcessingTask task) {
        return new DocumentTaskSummaryResponse(
                task.getTaskKey(), task.getTaskType(), task.getStatus(), task.getStage(),
                task.getProgress(), task.getErrorCode(), task.getErrorMessage(), task.getUpdated());
    }

}
