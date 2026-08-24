package com.fons.cloud.ai.rag2okf.application.document;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fons.cloud.ai.rag2okf.common.constants.Rag2OkfResultCode;
import com.fons.cloud.ai.rag2okf.common.constants.document.ParserType;
import com.fons.cloud.ai.rag2okf.common.constants.document.ProcessingMode;
import com.fons.cloud.ai.rag2okf.common.constants.document.ProcessingTaskType;
import com.fons.cloud.ai.rag2okf.common.constants.knowledgebase.ModelBindingStatus;
import com.fons.cloud.ai.rag2okf.common.constants.user.WorkspaceRole;
import com.fons.cloud.ai.rag2okf.common.dto.DocumentArtifactStore;
import com.fons.cloud.ai.rag2okf.common.dto.DocumentArtifactStore.ArtifactContent;
import com.fons.cloud.ai.rag2okf.common.dto.DocumentArtifactStore.ArtifactReference;
import com.fons.cloud.ai.rag2okf.common.dto.DocumentArtifactStore.ArtifactScope;
import com.fons.cloud.ai.rag2okf.common.dto.DocumentArtifactStore.ArtifactType;
import com.fons.cloud.ai.rag2okf.common.exception.document.DocumentProcessingException;
import com.fons.cloud.ai.rag2okf.common.model.document.ParseTaskSnapshotV1;
import com.fons.cloud.ai.rag2okf.common.request.document.DocumentUploadRequest;
import com.fons.cloud.ai.rag2okf.common.response.PageResponse;
import com.fons.cloud.ai.rag2okf.common.response.document.BatchDocumentUploadItemResponse;
import com.fons.cloud.ai.rag2okf.common.response.document.DocumentUploadResponse;
import com.fons.cloud.ai.rag2okf.common.response.DocumentDetailResponse;
import com.fons.cloud.ai.rag2okf.common.response.DocumentSummaryResponse;
import com.fons.cloud.ai.rag2okf.common.response.DocumentTaskSummaryResponse;
import com.fons.cloud.ai.rag2okf.common.utils.BusinessKeyGenerator;
import com.fons.cloud.ai.rag2okf.application.task.TaskApplicationService;
import com.fons.cloud.common.base.exception.BusinessRuntimeException;
import com.fons.cloud.ai.rag2okf.domain.entity.KbProcessingTaskEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbSourceDocumentEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.document.KbDocument;
import com.fons.cloud.ai.rag2okf.domain.entity.document.KbDocumentResult;
import com.fons.cloud.ai.rag2okf.domain.entity.document.KbProcessingTask;
import com.fons.cloud.ai.rag2okf.domain.entity.knowledgebase.KbKnowledgeBase;
import com.fons.cloud.ai.rag2okf.domain.entity.knowledgebase.KbModelBinding;
import com.fons.cloud.ai.rag2okf.domain.entity.user.KbUser;
import com.fons.cloud.ai.rag2okf.domain.entity.user.KbWorkspace;
import com.fons.cloud.ai.rag2okf.domain.service.KbSourceDocumentDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.document.KbDocumentDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.document.KbDocumentResultDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.document.KbProcessingTaskDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.knowledgebase.KbKnowledgeBaseDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.knowledgebase.KbModelBindingDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.user.KbModelProfileDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.user.KbWorkspaceDomainService;
import com.fons.cloud.ai.rag2okf.infrastructure.adapter.document.FonsOssDocumentArtifactService;
import com.fons.cloud.ai.rag2okf.infrastructure.adapter.user.SaTokenCurrentUserContext;
import com.fons.cloud.ai.rag2okf.infrastructure.support.user.WorkspaceAccessPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 文档上传与批量上传的应用服务（TP-002 T011 新上传契约）。
 *
 * <p>遵循 DDD-lite：应用服务负责权限校验、幂等、事务边界、跨域资源解析、任务创建、
 * MinIO/MySQL 显式补偿与 DTO 转换；文档身份与状态规则归 {@link KbDocument}，
 * 结果生命周期归 {@link KbDocumentResult}，任务事实归 {@link KbProcessingTask}。</p>
 *
 * <h3>编排顺序（设计 §3.2/§4.4）</h3>
 * <ol>
 *   <li>ADMIN 权限校验后解析上传意图：解析器可用性预检（MINERU 在任何 IO 前拒绝）、
 *       chunkPolicy 合法性校验（非法值拒绝无回退）、processingMode 与知识库
 *       autoParse/autoPublish 合成任务创建决策。</li>
 *   <li>幂等重放：相同 Idempotency-Key 已存在同知识库 PARSE 任务时直接返回原结果，
 *       不写 MinIO、不新建文档；不同 key 即使同名同 hash 也创建新文档。</li>
 *   <li>MinIO 先写（事务外流式写入并计算 SHA-256/大小）+ MySQL 事务
 *       （kb_document + kb_document_result + 可选 kb_processing_task）+ 失败补偿删除。</li>
 * </ol>
 *
 * <h3>过渡说明</h3>
 * <p>列表/详情/下载仍走旧读链路方法，由 T012 按新契约重写后删除；旧上传、替换与
 * 触发解析方法已随新契约移除（旧 Controller 仅保留读入口）。</p>
 *
 * @author hongqy
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentApplicationService {

    /** 分块策略缺省值：知识库默认策略暂无新模型字段，按未配置处理（设计 §3.2）。 */
    private static final ParseTaskSnapshotV1.ChunkPolicySnapshot DEFAULT_CHUNK_POLICY =
            new ParseTaskSnapshotV1.ChunkPolicySnapshot("STRUCTURE", "PARENT_CHILD", null);

    /** 分块边界策略白名单：LENGTH、STRUCTURE、SEMANTIC。 */
    private static final Set<String> BOUNDARY_TYPES = Set.of("LENGTH", "STRUCTURE", "SEMANTIC");

    /** 分块层级策略白名单：FLAT、PARENT_CHILD。 */
    private static final Set<String> HIERARCHY_TYPES = Set.of("FLAT", "PARENT_CHILD");

    private final SaTokenCurrentUserContext currentUserContext;
    private final WorkspaceAccessPolicy workspaceAccessPolicy;
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
    private final TransactionTemplate transactionTemplate;

    /** 旧读链路依赖：仅列表/详情/下载过渡使用，T012 重写后移除。 */
    private final KbSourceDocumentDomainService sourceDocumentDomainService;
    private final TaskApplicationService taskApplicationService;
    private final DocumentArtifactStore legacyArtifactStore;

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
     * @param idempotencyKey   调用方幂等键，空值时按无幂等处理
     * @return 上传受理响应，不含数据库 id、objectKey 或凭证
     * @throws DocumentProcessingException 权限、预检、解析器可用性或策略校验失败时抛出
     */
    public DocumentUploadResponse uploadDocument(
            String knowledgeBaseKey, MultipartFile file,
            DocumentUploadRequest request, String idempotencyKey) {
        KbUser user = currentUserContext.requireCurrentUser();
        KbKnowledgeBase knowledgeBase = requireKnowledgeBaseAccess(
                user.getUserKey(), knowledgeBaseKey, WorkspaceRole.ADMIN);
        KbWorkspace workspace = requireWorkspace(knowledgeBase.getWorkspaceId());

        DocumentUploadRequest effectiveRequest = request != null
                ? request : new DocumentUploadRequest(ProcessingMode.DEFAULT, null, null);
        UploadIntent intent = resolveUploadIntent(knowledgeBase, effectiveRequest);

        String effectiveIdempotencyKey = normalizeIdempotencyKey(idempotencyKey);
        if (intent.createParseTask()) {
            DocumentUploadResponse replayed = replayIfIdempotent(
                    knowledgeBase.getId(), effectiveIdempotencyKey, effectiveRequest.processingMode());
            if (replayed != null) {
                return replayed;
            }
        }

        DocumentUploadPrecheckPolicy.PrecheckedFile precheckedFile = precheckFile(file);
        String documentKey = BusinessKeyGenerator.nextKey();
        String fileToken = BusinessKeyGenerator.nextKey();

        // MinIO 先写（事务外流式写入），失败时制品服务内部已补偿，无需 MySQL 回滚
        FonsOssDocumentArtifactService.StoredSourceArtifact storedArtifact =
                documentArtifactService.storeSource(new FonsOssDocumentArtifactService.SourceArtifactCommand(
                        workspace.getWorkspaceKey(), knowledgeBase.getKnowledgeBaseKey(),
                        documentKey, fileToken, precheckedFile.filename(),
                        precheckedFile.contentType(), precheckedFile.inputStream()));

        UploadOutcome outcome;
        try {
            outcome = transactionTemplate.execute(status -> persistUpload(
                    knowledgeBase, workspace, user, precheckedFile, documentKey, fileToken,
                    storedArtifact, intent, effectiveIdempotencyKey));
        } catch (RuntimeException exception) {
            compensateDeleteSource(storedArtifact.objectKey(), exception);
            throw exception;
        }
        return toUploadResponse(outcome, effectiveRequest.processingMode());
    }

    /**
     * 批量上传：整批预检后逐项独立处理，单项失败不影响其他项（AC-002）。
     *
     * <p>批量幂等以 request key + item index 区分（设计 §3.2）；文件数或总量超限时
     * 整批预检拒绝，不进入逐项处理。失败项只携带稳定错误码与安全化摘要。</p>
     *
     * @param knowledgeBaseKey 知识库业务标识
     * @param files            上传文件列表，不可为空
     * @param request          上传请求契约，对批内每项一致生效
     * @param requestKey       批量请求幂等键，空值时按无幂等处理
     * @return 每项独立成功/失败结果，顺序与入参一致
     * @throws DocumentProcessingException 列表为空、文件数或总大小超限时抛出
     */
    public List<BatchDocumentUploadItemResponse> batchUploadDocuments(
            String knowledgeBaseKey, List<MultipartFile> files,
            DocumentUploadRequest request, String requestKey) {
        uploadPrecheckPolicy.precheckBatch(files);
        String baseKey = normalizeIdempotencyKey(requestKey);
        List<BatchDocumentUploadItemResponse> items = new ArrayList<>(files.size());
        for (int index = 0; index < files.size(); index++) {
            MultipartFile file = files.get(index);
            String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown";
            try {
                DocumentUploadResponse response = uploadDocument(
                        knowledgeBaseKey, file, request, baseKey + ":" + index);
                items.add(BatchDocumentUploadItemResponse.success(index, filename, response));
            } catch (DocumentProcessingException exception) {
                items.add(BatchDocumentUploadItemResponse.failure(
                        index, filename, exception.getCode(), exception.getMessage()));
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
        return items;
    }

    /**
     * 解析上传意图：解析器可用性预检、分块策略校验与任务创建决策。
     *
     * <p>解析器预检在任何 IO（任务、MinIO、模型、网络）前执行：MINERU 直接
     * {@code PARSER_NOT_AVAILABLE}，不创建文档与任务、不调用 Built-in（AC-003、AC-007）。
     * chunkPolicy 只要提供就严格校验，非法值拒绝且不做默认回退；未提供时使用
     * 缺省 STRUCTURE+PARENT_CHILD。</p>
     */
    private UploadIntent resolveUploadIntent(KbKnowledgeBase knowledgeBase, DocumentUploadRequest request) {
        ProcessingMode mode = request.processingMode() != null
                ? request.processingMode() : ProcessingMode.DEFAULT;
        ParserType parserType = request.parserType() != null ? request.parserType() : ParserType.BUILT_IN;
        parserRegistry.requireEnabled(parserType);
        ParseTaskSnapshotV1.ChunkPolicySnapshot chunkPolicy = resolveChunkPolicy(request.chunkPolicy());
        boolean createParseTask = switch (mode) {
            case SKIP -> false;
            case PARSE -> true;
            case DEFAULT -> Boolean.TRUE.equals(knowledgeBase.getAutoParse());
        };
        // DEFAULT 冻结知识库默认；PARSE/SKIP 模式下 publishAfterSuccess 同样以知识库 autoPublish 为准（上传契约不含显式字段）
        boolean publishAfterSuccess = Boolean.TRUE.equals(knowledgeBase.getAutoPublish());
        Map<String, ParseTaskSnapshotV1.ModelProfileRef> modelProfileRefs =
                createParseTask ? freezeModelProfileRefs(knowledgeBase.getId()) : Map.of();
        return new UploadIntent(createParseTask, parserType, chunkPolicy, publishAfterSuccess, modelProfileRefs);
    }

    /**
     * 解析并校验分块策略请求。
     *
     * @param requested 请求分块策略，{@code null} 时返回缺省 STRUCTURE+PARENT_CHILD
     * @return 已冻结的分块策略快照
     * @throws DocumentProcessingException 边界或层级策略非法时抛出 {@code CHUNK_POLICY_INVALID}
     */
    private ParseTaskSnapshotV1.ChunkPolicySnapshot resolveChunkPolicy(
            DocumentUploadRequest.ChunkPolicy requested) {
        if (requested == null) {
            return DEFAULT_CHUNK_POLICY;
        }
        String boundaryType = requested.boundaryType();
        String hierarchyType = requested.hierarchyType();
        if (boundaryType == null || !BOUNDARY_TYPES.contains(boundaryType)
                || hierarchyType == null || !HIERARCHY_TYPES.contains(hierarchyType)) {
            throw new DocumentProcessingException(Rag2OkfResultCode.CHUNK_POLICY_INVALID);
        }
        Map<String, Object> parameters = requested.parameters() == null
                ? null : Map.copyOf(requested.parameters());
        return new ParseTaskSnapshotV1.ChunkPolicySnapshot(boundaryType, hierarchyType, parameters);
    }

    /**
     * 冻结知识库启用绑定对应的非秘密模型档案引用。
     *
     * <p>只冻结 profileKey 业务引用，不复制 Base URL、API Key 或连接密文；档案已删除
     * 的绑定跳过（上传不因缺少解析资源被拒绝，AC-004），运行期引用失效由任务执行
     * fail-closed 处理。</p>
     */
    private Map<String, ParseTaskSnapshotV1.ModelProfileRef> freezeModelProfileRefs(Long knowledgeBaseId) {
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
        Map<String, ParseTaskSnapshotV1.ModelProfileRef> refs = new LinkedHashMap<>();
        for (KbModelBinding binding : activeBindings) {
            String profileKey = profileKeys.get(binding.getModelProfileId());
            if (profileKey != null) {
                refs.put(binding.getUsageType().getValue(),
                        new ParseTaskSnapshotV1.ModelProfileRef(profileKey, null));
            }
        }
        return Map.copyOf(refs);
    }

    /**
     * 单文件预检：文件名净化、扩展名白名单、大小与魔数（设计 §4.2 校验顺序）。
     */
    private DocumentUploadPrecheckPolicy.PrecheckedFile precheckFile(MultipartFile file) {
        if (file == null) {
            throw new DocumentProcessingException(Rag2OkfResultCode.PAYLOAD_INVALID);
        }
        try {
            return uploadPrecheckPolicy.precheck(
                    file.getOriginalFilename(), file.getInputStream(), file.getSize());
        } catch (IOException exception) {
            throw new DocumentProcessingException(
                    Rag2OkfResultCode.DOCUMENT_FILE_SECURITY_REJECTED, exception);
        }
    }

    /**
     * 事务内持久化上传结果：文档身份、结果底座与可选 PARSE 任务。
     *
     * <p>创建任务时把同一份 ParseTaskSnapshotV1 JSON 冻结到任务快照列与结果表的
     * 解析器快照列，保证两侧冻结事实一致；任务保持 QUEUED（TP-002 无执行器，
     * 属已知中间态）。文档初始状态 UPLOADED、结果初始阶段 INIT 由实体工厂保证。</p>
     */
    private UploadOutcome persistUpload(
            KbKnowledgeBase knowledgeBase, KbWorkspace workspace, KbUser user,
            DocumentUploadPrecheckPolicy.PrecheckedFile precheckedFile,
            String documentKey, String fileToken,
            FonsOssDocumentArtifactService.StoredSourceArtifact storedArtifact,
            UploadIntent intent, String idempotencyKey) {
        KbDocument document = KbDocument.create(knowledgeBase.getId(), precheckedFile.filename());
        documentDomainService.save(document);

        KbDocumentResult result = KbDocumentResult.create(document.getId(), user.getId(),
                new KbDocumentResult.SourceFilePointer(
                        fileToken, storedArtifact.objectKey(), precheckedFile.filename(),
                        precheckedFile.contentType(), storedArtifact.sizeBytes(), storedArtifact.sha256()));

        KbProcessingTask task = null;
        if (intent.createParseTask()) {
            ParseTaskSnapshotV1 snapshot = ParseTaskSnapshotV1.of(
                    workspace.getWorkspaceKey(), knowledgeBase.getKnowledgeBaseKey(),
                    document.getDocumentKey(), fileToken, intent.parserType(), null,
                    intent.chunkPolicy(), intent.modelProfileRefs(),
                    intent.publishAfterSuccess(), user.getId(), new Date());
            String snapshotJson = JSON.toJSONString(snapshot);
            result.setParserType(intent.parserType());
            result.setParserSnapshotJson(snapshotJson);
            documentResultDomainService.save(result);
            task = processingTaskDomainService.createIfAbsent(KbProcessingTask.create(
                    workspace.getId(), knowledgeBase.getId(), document.getId(),
                    ProcessingTaskType.PARSE, result.getResultKey(), idempotencyKey,
                    ParseTaskSnapshotV1.SCHEMA_VERSION, snapshotJson, null));
        } else {
            documentResultDomainService.save(result);
        }
        return new UploadOutcome(document, result, task);
    }

    /**
     * 幂等重放：同知识库同 Idempotency-Key 已创建 PARSE 任务时返回原受理结果。
     *
     * <p>原文档已删除或结果缺失时重放不可用，按新上传处理；幂等范围限定为
     * 同一知识库的 PARSE 任务，跨知识库同 key 不互相影响。</p>
     *
     * @return 可重放的受理响应；无可重放任务时返回 {@code null}
     */
    private DocumentUploadResponse replayIfIdempotent(
            Long knowledgeBaseId, String idempotencyKey, ProcessingMode processingMode) {
        KbProcessingTask existing = processingTaskDomainService.findByIdempotencyKey(
                knowledgeBaseId, ProcessingTaskType.PARSE, idempotencyKey);
        if (existing == null) {
            return null;
        }
        KbDocument document = documentDomainService.getById(existing.getSourceDocumentId());
        KbDocumentResult result = documentResultDomainService.findCurrentByDocumentId(
                existing.getSourceDocumentId());
        if (document == null || result == null || Boolean.TRUE.equals(document.getDeleted())) {
            return null;
        }
        return new DocumentUploadResponse(
                document.getDocumentKey(), result.getSourceFileToken(),
                document.getDisplayName(), modeValue(processingMode), existing.getTaskKey());
    }

    /** 幂等键规范化：空值生成唯一键，避免不同请求被误判为重复重放。 */
    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return BusinessKeyGenerator.nextKey();
        }
        return idempotencyKey.strip();
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

    private DocumentUploadResponse toUploadResponse(UploadOutcome outcome, ProcessingMode processingMode) {
        return new DocumentUploadResponse(
                outcome.document().getDocumentKey(),
                outcome.result().getSourceFileToken(),
                outcome.document().getDisplayName(),
                modeValue(processingMode),
                outcome.task() != null ? outcome.task().getTaskKey() : null);
    }

    private String modeValue(ProcessingMode processingMode) {
        return processingMode != null ? processingMode.getValue() : ProcessingMode.DEFAULT.getValue();
    }

    /** 上传意图：任务创建决策与冻结进快照的解析输入。 */
    private record UploadIntent(
            boolean createParseTask,
            ParserType parserType,
            ParseTaskSnapshotV1.ChunkPolicySnapshot chunkPolicy,
            boolean publishAfterSuccess,
            Map<String, ParseTaskSnapshotV1.ModelProfileRef> modelProfileRefs) {
    }

    /** 事务内持久化完成的上传事实。 */
    private record UploadOutcome(KbDocument document, KbDocumentResult result, KbProcessingTask task) {
    }

    // ────────────────────────────── 旧读链路（T012 重写后移除） ──────────────────────────────

    /**
     * 分页查询知识库下的当前文档视图（旧读链路过渡实现）。
     *
     * @param knowledgeBaseKey 知识库业务标识
     * @param page 页码（从 0 开始）
     * @param size 每页条数
     * @param folderPath 文件夹路径筛选，null 时不按文件夹过滤
     */
    public PageResponse<DocumentSummaryResponse> listDocuments(
            String knowledgeBaseKey, int page, int size, String folderPath) {
        KbUser user = currentUserContext.requireCurrentUser();
        KbKnowledgeBase knowledgeBase = requireKnowledgeBaseAccess(
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
     * 查询文档详情（旧读链路过渡实现）。需要 USER 权限。不返回版本列表（D-004）。
     *
     * @param documentKey 文档业务标识
     * @return 文档详情响应
     */
    public DocumentDetailResponse getDocumentDetail(String documentKey) {
        KbUser user = currentUserContext.requireCurrentUser();
        KbSourceDocumentEntity document = requireDocument(documentKey);
        KbKnowledgeBase knowledgeBase = requireKnowledgeBase(
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

    /**
     * 打开文档当前原文件的读取流（旧读链路过渡实现）。需要 USER 权限。调用方负责关闭流。
     *
     * @param documentKey 文档业务标识
     * @return 文件内容与元数据
     */
    public DocumentFileContent downloadDocumentFile(String documentKey) {
        KbUser user = currentUserContext.requireCurrentUser();
        KbSourceDocumentEntity document = requireDocument(documentKey);
        KbKnowledgeBase knowledgeBase = requireKnowledgeBase(
                document.getKnowledgeBaseId());
        KbWorkspace workspace = requireWorkspaceAccess(
                user.getUserKey(), knowledgeBase.getWorkspaceId(), WorkspaceRole.KNOWLEDGE_USER);

        ArtifactScope scope = new ArtifactScope(
                workspace.getWorkspaceKey(), knowledgeBase.getKnowledgeBaseKey(), documentKey);
        ArtifactContent content = legacyArtifactStore.open(new ArtifactReference(
                scope, ArtifactType.ORIGINAL, documentKey, document.getOriginalFilename()));

        return new DocumentFileContent(
                document.getOriginalFilename(),
                document.getContentType(),
                document.getSizeBytes(),
                content.inputStream());
    }

    // ────────────────────────────── 通用辅助 ──────────────────────────────

    private KbKnowledgeBase requireKnowledgeBaseAccess(
            String userKey, String knowledgeBaseKey, WorkspaceRole requiredRole) {
        KbKnowledgeBase knowledgeBase = knowledgeBaseDomainService.getOne(
                Wrappers.<KbKnowledgeBase>lambdaQuery()
                        .eq(KbKnowledgeBase::getKnowledgeBaseKey, knowledgeBaseKey));
        if (knowledgeBase == null) {
            throw new DocumentProcessingException(Rag2OkfResultCode.KNOWLEDGE_BASE_NOT_FOUND);
        }
        workspaceAccessPolicy.checkAccess(userKey,
                resolveWorkspaceKey(knowledgeBase.getWorkspaceId()), requiredRole);
        return knowledgeBase;
    }

    private KbKnowledgeBase requireKnowledgeBase(Long knowledgeBaseId) {
        KbKnowledgeBase knowledgeBase = knowledgeBaseDomainService.getById(knowledgeBaseId);
        if (knowledgeBase == null) {
            throw new DocumentProcessingException(Rag2OkfResultCode.KNOWLEDGE_BASE_NOT_FOUND);
        }
        return knowledgeBase;
    }

    private KbSourceDocumentEntity requireDocument(String documentKey) {
        KbSourceDocumentEntity document = sourceDocumentDomainService.getOne(
                Wrappers.<KbSourceDocumentEntity>lambdaQuery()
                        .eq(KbSourceDocumentEntity::getDocumentKey, documentKey));
        if (document == null) {
            throw new DocumentProcessingException(Rag2OkfResultCode.DOCUMENT_DELETED);
        }
        return document;
    }

    private KbWorkspace requireWorkspace(Long workspaceId) {
        KbWorkspace workspace = workspaceDomainService.getById(workspaceId);
        if (workspace == null) {
            throw new DocumentProcessingException(Rag2OkfResultCode.WORKSPACE_NOT_FOUND);
        }
        return workspace;
    }

    private KbWorkspace requireWorkspaceAccess(
            String userKey, Long workspaceId, WorkspaceRole requiredRole) {
        KbWorkspace workspace = requireWorkspace(workspaceId);
        workspaceAccessPolicy.checkAccess(userKey, workspace.getWorkspaceKey(), requiredRole);
        return workspace;
    }

    private String resolveWorkspaceKey(Long workspaceId) {
        KbWorkspace workspace = requireWorkspace(workspaceId);
        return workspace.getWorkspaceKey();
    }

    private DocumentSummaryResponse toSummaryResponse(
            KbSourceDocumentEntity document, KbProcessingTaskEntity latestTask) {
        return new DocumentSummaryResponse(document.getDocumentKey(), document.getDisplayName(),
                document.getFolderPath(),
                new DocumentSummaryResponse.CurrentFileSummary(document.getOriginalFilename(), document.getContentType(), document.getSizeBytes()),
                document.getFileToken(), document.getParseStatus(), document.getPublishStatus(),
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
