package com.fons.cloud.ai.rag2okf.application.knowledgebase;

import cn.hutool.core.lang.Assert;
import com.alibaba.fastjson2.JSON;
import com.fons.cloud.ai.rag2okf.common.constants.knowledgebase.ModelUsageType;
import com.fons.cloud.ai.rag2okf.common.constants.Rag2OkfResultCode;
import com.fons.cloud.ai.rag2okf.common.constants.user.ModelProfileStatus;
import com.fons.cloud.ai.rag2okf.common.constants.user.ModelType;
import com.fons.cloud.ai.rag2okf.common.constants.user.WorkspaceRole;
import com.fons.cloud.ai.rag2okf.common.model.knowledgebase.ChunkProfile;
import com.fons.cloud.ai.rag2okf.infrastructure.adapter.user.SaTokenCurrentUserContext;
import com.fons.cloud.ai.rag2okf.common.request.knowledgebase.CreateKnowledgeBaseRequest;
import com.fons.cloud.ai.rag2okf.common.request.knowledgebase.ModelBindingItem;
import com.fons.cloud.ai.rag2okf.common.request.knowledgebase.SaveModelBindingsRequest;
import com.fons.cloud.ai.rag2okf.common.request.knowledgebase.UpdateKnowledgeBaseRequest;
import com.fons.cloud.ai.rag2okf.common.response.knowledgebase.ChunkProfileResponse;
import com.fons.cloud.ai.rag2okf.common.response.knowledgebase.KnowledgeBaseResponse;
import com.fons.cloud.ai.rag2okf.common.response.knowledgebase.KnowledgeBaseSummaryResponse;
import com.fons.cloud.ai.rag2okf.common.response.knowledgebase.ModelBindingResponse;
import com.fons.cloud.ai.rag2okf.domain.entity.knowledgebase.KbKnowledgeBase;
import com.fons.cloud.ai.rag2okf.domain.entity.knowledgebase.KbModelBinding;
import com.fons.cloud.ai.rag2okf.domain.entity.user.KbModelProfile;
import com.fons.cloud.ai.rag2okf.domain.entity.user.UserWorkspaceAggregate;
import com.fons.cloud.ai.rag2okf.domain.service.knowledgebase.KbKnowledgeBaseDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.knowledgebase.KbModelBindingDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.user.KbModelProfileDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.user.KbUserDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.user.KbWorkspaceDomainService;
import com.fons.cloud.common.base.exception.BusinessRuntimeException;
import com.fons.cloud.common.result.PageResult;
import com.fons.cloud.common.result.R;
import com.fons.cloud.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 知识库创建、列表、详情、设置编辑与模型用途绑定的应用服务。
 *
 * <p>遵循 DDD-lite：领域规则（ChunkProfile 校验、自动化策略组合）下沉到实体，
 * 应用服务负责编排、事务、权限校验和持久化协调。Controller 不承载业务规则。</p>
 *
 * <p>设置只形成后续操作默认快照，修改设置不会批量重处理已有文档或任务。</p>
 *
 * @author hongqy
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseApplicationService {

    private final TransactionTemplate transactionTemplate;

    private final SaTokenCurrentUserContext currentUserContext;
    private final KbWorkspaceDomainService workspaceDomainService;
    private final KbKnowledgeBaseDomainService knowledgeBaseDomainService;
    private final KbModelBindingDomainService modelBindingDomainService;
    private final KbModelProfileDomainService modelProfileDomainService;
    private final KbUserDomainService userDomainService;
    @Value("${sys.embedding.dims:1024}")
    private int embeddingDims;


    /**
     * 分页查询知识库列表。
     * <p>需要 USER 权限。只返回当前工作空间下的知识库摘要。</p>
     * @param workspaceKey 工作空间业务标识
     * @param page 页码（1 基）
     * @param size 每页大小
     * @return 分页知识库摘要
     */
    public R<PageResult<KnowledgeBaseSummaryResponse>> pageKnowledgeBases(String workspaceKey, int page, int size) {
        Long currentUserId = currentUserContext.requireCurrentUserId();
        UserWorkspaceAggregate workspace = workspaceDomainService.findUserWorkspaceAggregate(
                currentUserId, workspaceKey);
        if (workspace == null) {
            return R.failed(Rag2OkfResultCode.WORKSPACE_NOT_FOUND);
        }
        if (!workspace.hasWorkspaceAccess(WorkspaceRole.KNOWLEDGE_USER)) {
            return R.failed(Rag2OkfResultCode.WORKSPACE_NOT_FOUND);
        }

        PageResult<KbKnowledgeBase> result = knowledgeBaseDomainService.pageByWorkspaceId(
                workspace.workspace().getId(), page, size);

        // 批量查询知识库创建者，避免逐条 N+1 查 kb_user
        Map<Long, String> ownerMap = loadOwnerUserMap(result.getResultList());
        List<KnowledgeBaseSummaryResponse> records = result.getResultList().stream()
                .map(entity -> toSummaryResponse(entity, currentUserId, ownerMap))
                .toList();
        PageResult<KnowledgeBaseSummaryResponse> response = new PageResult<>(
                result.getCurrentPage(), result.getPageSize(), result.getTotal(), records);
        response.setPages(result.getPages());
        return R.ok(response);
    }


    /**
     * 创建知识库。
     * <p>需要 ADMIN 权限。创建后可选择性地同时保存初始模型用途绑定。</p>
     * @param workspaceKey 工作空间业务标识
     * @param request 创建请求
     * @return 知识库详情响应
     */
    public R<KnowledgeBaseResponse> createKnowledgeBase(String workspaceKey, CreateKnowledgeBaseRequest request) {
        Long currentUserId = currentUserContext.requireCurrentUserId();
        UserWorkspaceAggregate workspace = workspaceDomainService.findUserWorkspaceAggregate(
                currentUserId, workspaceKey);
        if (workspace == null) {
            return R.failed(Rag2OkfResultCode.WORKSPACE_NOT_FOUND);
        }
        if (!workspace.hasWorkspaceAccess(WorkspaceRole.ADMIN)) {
            return R.failed(Rag2OkfResultCode.NOT_PERMISSION_CREATE_DATABASES);
        }

        // 判断知识库名称是否已存在
        if (knowledgeBaseDomainService.existsByWorkspaceIdAndName(
                workspace.workspace().getId(), request.name())) {
            return R.failed(Rag2OkfResultCode.KNOWLEDGE_BASE_NAME_DUPLICATED);
        }

        // 自动解析与自动发布可独立组合；ChunkProfile 校验由实体工厂方法负责
        KbKnowledgeBase kbKnowledgeBase = KbKnowledgeBase.create(
                currentUserId, workspace.workspace().getId(), request);

        // 事务外校验绑定项，校验失败直接返回具体错误码
        R<Map<String, KbModelProfile>> validation = validateAllBindings(request.modelBindings(), workspace.workspace().getOwnerUserId());
        if (!validation.isSuccess()) {
            return R.failed(validation);
        }
        Map<String, KbModelProfile> profileMap = validation.getData();

        List<ModelBindingResponse> bindingResponses = new ArrayList<>();
        Boolean execute = transactionTemplate.execute(status -> {
            try {
                Assert.isTrue(knowledgeBaseDomainService.save(kbKnowledgeBase), () -> BusinessRuntimeException.of(ResultCode.INSERT_FAILED));
                if (CollectionUtils.isNotEmpty(request.modelBindings())) {
                    bindingResponses.addAll(replaceBindings(
                            kbKnowledgeBase.getId(), request.modelBindings(), profileMap));
                }
                return true;
            } catch (Exception e) {
                log.error(e.getMessage(), e);
                status.setRollbackOnly();
                return false;
            }
        });

        if (Boolean.FALSE.equals(execute)) {
            return R.failed(ResultCode.SYSTEM_BUSY);
        }
        return R.ok(toResponse(
                kbKnowledgeBase, workspace.workspace().getWorkspaceKey(), bindingResponses));
    }


    /**
     * 查询知识库详情。
     *
     * <p>需要 USER 权限。返回基础信息、处理设置和模型用途绑定。</p>
     *
     * @param knowledgeBaseKey 知识库业务标识
     * @return 知识库详情响应
     */
    public R<KnowledgeBaseResponse> getKnowledgeBase(String knowledgeBaseKey) {
        Long currentUserId = currentUserContext.requireCurrentUserId();
        KbKnowledgeBase entity = knowledgeBaseDomainService.findByKnowledgeBaseKey(knowledgeBaseKey);
        if (entity == null) {
            return R.failed(Rag2OkfResultCode.KNOWLEDGE_BASE_NOT_FOUND);
        }
        UserWorkspaceAggregate workspace = workspaceDomainService.findUserWorkspaceAggregate(
                currentUserId, entity.getWorkspaceId());
        if (workspace == null || !workspace.hasWorkspaceAccess(WorkspaceRole.KNOWLEDGE_USER)) {
            return R.failed(Rag2OkfResultCode.WORKSPACE_NOT_FOUND);
        }
        List<ModelBindingResponse> bindings = loadBindings(entity.getId());
        return R.ok(toResponse(entity, workspace.workspace().getWorkspaceKey(), bindings));
    }

    /**
     * 编辑知识库信息与默认处理设置。
     *
     * <p>需要 ADMIN 权限。使用乐观锁版本控制并发修改。
     * modelBindings 不为 null 时整体替换现有绑定。
     * 修改设置只影响后续操作，不批量修改已有文档或任务输入。</p>
     *
     * @param knowledgeBaseKey 知识库业务标识
     * @param request 更新请求
     * @return 更新后的知识库详情响应
     */
    public R<KnowledgeBaseResponse> updateKnowledgeBase(String knowledgeBaseKey, UpdateKnowledgeBaseRequest request) {
        Long currentUserId = currentUserContext.requireCurrentUserId();
        KbKnowledgeBase entity = knowledgeBaseDomainService.findByKnowledgeBaseKey(knowledgeBaseKey);
        if (entity == null) {
            return R.failed(Rag2OkfResultCode.KNOWLEDGE_BASE_NOT_FOUND);
        }
        UserWorkspaceAggregate workspace = workspaceDomainService.findUserWorkspaceAggregate(
                currentUserId, entity.getWorkspaceId());
        if (workspace == null || !workspace.hasWorkspaceAccess(WorkspaceRole.ADMIN)) {
            return R.failed(Rag2OkfResultCode.WORKSPACE_NOT_FOUND);
        }
        if (entity.getVersion() != request.revision()) {
            return R.failed(Rag2OkfResultCode.KB_VERSION_CONFLICT);
        }

        // 自动化策略独立更新；ChunkProfile 校验由实体负责
        entity.applyUpdate(request);

        // 事务外校验绑定项，校验失败直接透传错误码
        R<Map<String, KbModelProfile>> validation = validateAllBindings(
                request.modelBindings(), workspace.workspace().getOwnerUserId());
        if (!validation.isSuccess()) {
            return R.failed(validation);
        }
        Map<String, KbModelProfile> profileMap = validation.getData();

        List<ModelBindingResponse> bindingResponses = new ArrayList<>();
        Boolean execute = transactionTemplate.execute(status -> {
            try {
                Assert.isTrue(knowledgeBaseDomainService.updateById(entity),
                        () -> BusinessRuntimeException.of(ResultCode.UPDATE_FAILED));
                if (request.modelBindings() != null) {
                    bindingResponses.addAll(replaceBindings(
                            entity.getId(), request.modelBindings(), profileMap));
                } else {
                    bindingResponses.addAll(loadBindings(entity.getId()));
                }
                return true;
            } catch (Exception e) {
                log.error(e.getMessage(), e);
                status.setRollbackOnly();
                return false;
            }
        });

        if (Boolean.FALSE.equals(execute)) {
            return R.failed(ResultCode.SYSTEM_BUSY);
        }

        return R.ok(toResponse(entity, workspace.workspace().getWorkspaceKey(), bindingResponses));
    }

    /**
     * 查询知识库的模型用途绑定。
     *
     * <p>需要 USER 权限。不返回凭证。</p>
     *
     * @param knowledgeBaseKey 知识库业务标识
     * @return 模型用途绑定列表
     */
    public R<List<ModelBindingResponse>> getModelBindings(String knowledgeBaseKey) {
        Long currentUserId = currentUserContext.requireCurrentUserId();
        KbKnowledgeBase entity = knowledgeBaseDomainService.findByKnowledgeBaseKey(knowledgeBaseKey);
        if (entity == null) {
            return R.failed(Rag2OkfResultCode.WORKSPACE_NOT_FOUND);
        }
        UserWorkspaceAggregate workspace = workspaceDomainService.findUserWorkspaceAggregate(
                currentUserId, entity.getWorkspaceId());
        if (workspace == null || !workspace.hasWorkspaceAccess(WorkspaceRole.KNOWLEDGE_USER)) {
            return R.failed(Rag2OkfResultCode.WORKSPACE_NOT_FOUND);
        }
        return R.ok(loadBindings(entity.getId()));
    }

    /**
     * 整体保存知识库模型用途绑定。
     *
     * <p>需要 ADMIN 权限。同一用途最多一个有效绑定；档案必须属于工作空间所有者且为 ACTIVE，
     * 且模型类型与用途兼容。保存时按用途整体同步，已有用途原位更新。</p>
     *
     * @param knowledgeBaseKey 知识库业务标识
     * @param request 保存请求
     * @return 保存后的模型用途绑定列表
     */
    public R<List<ModelBindingResponse>> saveModelBindings(String knowledgeBaseKey, SaveModelBindingsRequest request) {
        Long currentUserId = currentUserContext.requireCurrentUserId();
        KbKnowledgeBase entity = knowledgeBaseDomainService.findByKnowledgeBaseKey(knowledgeBaseKey);
        if (entity == null) {
            return R.failed(Rag2OkfResultCode.WORKSPACE_NOT_FOUND);
        }
        UserWorkspaceAggregate workspace = workspaceDomainService.findUserWorkspaceAggregate(
                currentUserId, entity.getWorkspaceId());
        if (workspace == null || !workspace.hasWorkspaceAccess(WorkspaceRole.ADMIN)) {
            return R.failed(Rag2OkfResultCode.WORKSPACE_NOT_FOUND);
        }

        List<ModelBindingItem> items = request.modelBindings() != null ? request.modelBindings() : List.of();

        // 事务外校验绑定项，校验失败直接返回具体错误码
        R<Map<String, KbModelProfile>> validation = validateAllBindings(
                items, workspace.workspace().getOwnerUserId());
        if (!validation.isSuccess()) {
            return R.failed(validation);
        }
        Map<String, KbModelProfile> profileMap = validation.getData();

        List<ModelBindingResponse> bindingResponses = new ArrayList<>();
        Boolean execute = transactionTemplate.execute(status -> {
            try {
                bindingResponses.addAll(replaceBindings(entity.getId(), items, profileMap));
                return true;
            } catch (Exception e) {
                log.error(e.getMessage(), e);
                status.setRollbackOnly();
                return false;
            }
        });

        if (Boolean.FALSE.equals(execute)) {
            return R.failed(ResultCode.SYSTEM_BUSY);
        }

        return R.ok(bindingResponses);
    }

    /**
     * 删除知识库（软删除）。
     *
     * <p>仅创建者（owner_user_id 等于当前会话用户主键）可删除。命中已删除或不存在的知识库时
     * 视为幂等成功直接返回，不抛异常。删除仅置 deleted=1，不物理删除关联文档或 ES 投影。</p>
     *
     * @param knowledgeBaseKey 知识库业务标识
     * @return 统一响应包装
     */
    public R<Void> deleteKnowledgeBase(String knowledgeBaseKey) {
        Long currentUserId = currentUserContext.requireCurrentUserId();
        KbKnowledgeBase entity = knowledgeBaseDomainService.findByKnowledgeBaseKey(knowledgeBaseKey);
        // 不存在或已被软删除：幂等返回成功，避免泄露存在性与删除状态
        if (entity == null) {
            return R.ok(null);
        }
        // 仅创建者可删除，不匹配统一返回无权限
        if (entity.getOwnerUserId() == null || !entity.getOwnerUserId().equals(currentUserId)) {
            return R.failed(Rag2OkfResultCode.NOT_PERMISSION_DELETE_DATABASES);
        }
        knowledgeBaseDomainService.removeById(entity.getId());
        return R.ok(null);
    }




    /**
     * 事务外校验绑定项并批量加载模型档案。
     *
     * <p>校验同一用途不重复、档案归属/状态/类型兼容/EMBEDDING 维度一致性。
     *
     * @param items 绑定项列表，可为 null 或空
     * @param workspaceOwnerUserId 工作空间所有者主键
     * @return 校验结果，成功时 data 为 profileKey 到模型档案的映射
     */
    private R<Map<String, KbModelProfile>> validateAllBindings(
            List<ModelBindingItem> items, Long workspaceOwnerUserId) {
        if (CollectionUtils.isEmpty(items)) {
            return R.ok(Map.of());
        }
        // 校验同一用途不重复（字段非空已由 @Valid 覆盖）
        Set<ModelUsageType> seen = new HashSet<>();
        for (ModelBindingItem item : items) {
            if (!seen.add(item.usageType())) {
                return R.failed(Rag2OkfResultCode.BINDING_USAGE_TYPE_DUPLICATE);
            }
        }
        // 批量查询模型档案，避免循环 N+1
        Set<String> profileKeys = items.stream()
                .map(ModelBindingItem::modelProfileKey)
                .collect(Collectors.toSet());
        Map<String, KbModelProfile> profileMap = modelProfileDomainService
                .listByProfileKeysAndOwnerUserId(profileKeys, workspaceOwnerUserId)
                .stream()
                .collect(Collectors.toMap(KbModelProfile::getProfileKey, Function.identity()));
        // 逐项校验档案归属/状态/兼容/维度
        for (ModelBindingItem item : items) {
            KbModelProfile profile = profileMap.get(item.modelProfileKey());
            Rag2OkfResultCode code = validateProfile(profile, workspaceOwnerUserId, item.usageType());
            if (code != null) {
                return R.failed(code);
            }
        }
        return R.ok(profileMap);
    }

    /**
     * 校验单个模型档案的归属、状态、类型兼容性和 EMBEDDING 维度。
     *
     * @param profile 模型档案
     * @param workspaceOwnerUserId 工作空间所有者主键
     * @param usageType 模型用途
     * @return 校验失败时返回错误码，通过时返回 null
     */
    private Rag2OkfResultCode validateProfile(
            KbModelProfile profile, Long workspaceOwnerUserId, ModelUsageType usageType) {
        if (profile == null
                || !workspaceOwnerUserId.equals(profile.getOwnerUserId())
                || profile.getStatus() != ModelProfileStatus.ACTIVE
                || !isModelUsageCompatible(usageType, profile.getModelType())) {
            return Rag2OkfResultCode.MODEL_PROFILE_INVALID;
        }
        if (!isEmbeddingDimensionsValid(usageType, profile.getDimensions())) {
            return Rag2OkfResultCode.EMBEDDING_DIMS_MISMATCH;
        }
        return null;
    }

    private boolean isModelUsageCompatible(ModelUsageType usageType, ModelType modelType) {
        return usageType != null && usageType.getRequiredModelType().matches(modelType);
    }

    private boolean isEmbeddingDimensionsValid(ModelUsageType usageType, Integer profileDimensions) {
        if (usageType != ModelUsageType.EMBEDDING) {
            return true;
        }
        return profileDimensions != null && profileDimensions == embeddingDims;
    }

    /**
     * 事务内整体替换模型绑定。
     *
     * <p>校验已在事务外完成；领域服务按用途原位更新已有记录、停用缺失用途，
     * 仅为首次出现的用途插入记录。</p>
     *
     * @param knowledgeBaseId 知识库主键
     * @param items 绑定项列表
     * @param profileMap 模型档案映射（由 {@link #validateAllBindings} 产出）
     * @return 保存后的绑定响应列表
     */
    private List<ModelBindingResponse> replaceBindings(
            Long knowledgeBaseId,
            List<ModelBindingItem> items,
            Map<String, KbModelProfile> profileMap) {
        List<KbModelBinding> requestedBindings = new ArrayList<>();
        for (ModelBindingItem item : items) {
            KbModelProfile profile = profileMap.get(item.modelProfileKey());
            requestedBindings.add(KbModelBinding.create(
                    knowledgeBaseId, item.usageType(), profile.getId()));
        }
        List<KbModelBinding> persistedBindings = modelBindingDomainService.replaceByKnowledgeBaseId(
                knowledgeBaseId, requestedBindings);

        List<ModelBindingResponse> responses = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            ModelBindingItem item = items.get(index);
            KbModelBinding binding = persistedBindings.get(index);
            responses.add(new ModelBindingResponse(
                    binding.getBindingKey(), item.usageType(), item.modelProfileKey()));
        }
        return responses;
    }

    /**
     * 批量加载知识库的模型绑定，避免逐条 N+1 查询模型档案。
     *
     * @param knowledgeBaseId 知识库主键
     * @return 模型绑定响应列表
     */
    private List<ModelBindingResponse> loadBindings(Long knowledgeBaseId) {
        List<KbModelBinding> bindings = modelBindingDomainService.listByKnowledgeBaseId(knowledgeBaseId);
        if (bindings.isEmpty()) {
            return List.of();
        }
        // 批量查询模型档案
        Set<Long> profileIds = bindings.stream()
                .map(KbModelBinding::getModelProfileId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> profileMap = profileIds.isEmpty()
                ? Map.of()
                : modelProfileDomainService.findProfileKeysByIds(profileIds);

        return bindings.stream()
                .map(binding -> toBindingResponse(binding, profileMap))
                .toList();
    }

    private ModelBindingResponse toBindingResponse(KbModelBinding binding, Map<Long, String> profileMap) {
        String profileKey = binding.getModelProfileId() != null
                ? profileMap.get(binding.getModelProfileId())
                : null;
        return new ModelBindingResponse(binding.getBindingKey(), binding.getUsageType(), profileKey);
    }

    private KnowledgeBaseResponse toResponse(
            KbKnowledgeBase entity, String workspaceKey, List<ModelBindingResponse> bindings) {
        ChunkProfile chunkProfile = JSON.parseObject(entity.getChunkProfileJson(), ChunkProfile.class);
        ChunkProfileResponse chunkResponse = new ChunkProfileResponse(
                chunkProfile.strategy(), chunkProfile.chunkSize(),
                chunkProfile.overlap(), chunkProfile.titleLevel());
        return new KnowledgeBaseResponse(
                entity.getKnowledgeBaseKey(), workspaceKey, entity.getName(), entity.getDescription(),
                Boolean.TRUE.equals(entity.getAutoParse()), Boolean.TRUE.equals(entity.getAutoPublish()),
                entity.getParserProfile(), chunkResponse, bindings,
                entity.getVersion() != null ? entity.getVersion() : 0, entity.getUpdated());
    }

    private KnowledgeBaseSummaryResponse toSummaryResponse(
            KbKnowledgeBase entity, Long currentUserId, Map<Long, String> ownerMap) {
        String ownerUserKey = entity.getOwnerUserId() != null
                ? ownerMap.get(entity.getOwnerUserId())
                : null;
        // canDelete 仅创建者可删除：owner_user_id 等于当前会话用户主键
        boolean canDelete = entity.getOwnerUserId() != null
                && entity.getOwnerUserId().equals(currentUserId);
        return new KnowledgeBaseSummaryResponse(
                entity.getKnowledgeBaseKey(), entity.getName(), entity.getDescription(),
                Boolean.TRUE.equals(entity.getAutoParse()), Boolean.TRUE.equals(entity.getAutoPublish()),
                ownerUserKey, canDelete, entity.getUpdated());
    }

    /**
     * 批量加载知识库创建者业务标识，避免逐条 N+1 查询。
     *
     * @param knowledgeBases 当前页知识库实体
     * @return ownerUserId 到 userKey 的映射，无创建者时返回空映射
     */
    private Map<Long, String> loadOwnerUserMap(List<KbKnowledgeBase> knowledgeBases) {
        Set<Long> ownerUserIds = knowledgeBases.stream()
                .map(KbKnowledgeBase::getOwnerUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ownerUserIds.isEmpty()) {
            return Map.of();
        }
        return userDomainService.findUserKeysByIds(ownerUserIds);
    }
}
