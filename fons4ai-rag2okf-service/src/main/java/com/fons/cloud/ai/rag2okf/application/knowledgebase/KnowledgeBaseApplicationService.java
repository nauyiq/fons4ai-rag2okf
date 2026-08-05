package com.fons.cloud.ai.rag2okf.application.knowledgebase;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fons.cloud.ai.rag2okf.application.model.CurrentUserContext;
import com.fons.cloud.ai.rag2okf.application.model.ModelBusinessKeyGenerator;
import com.fons.cloud.ai.rag2okf.common.constants.ModelProfileStatus;
import com.fons.cloud.ai.rag2okf.common.constants.ModelUsageType;
import com.fons.cloud.ai.rag2okf.common.constants.WorkspaceRole;
import com.fons.cloud.ai.rag2okf.common.exeception.KnowledgeBaseConflictException;
import com.fons.cloud.ai.rag2okf.common.exeception.KnowledgeBaseException;
import com.fons.cloud.ai.rag2okf.common.exeception.WorkspaceAccessDeniedException;
import com.fons.cloud.ai.rag2okf.common.request.ChunkProfileRequest;
import com.fons.cloud.ai.rag2okf.common.request.CreateKnowledgeBaseRequest;
import com.fons.cloud.ai.rag2okf.common.request.ModelBindingItem;
import com.fons.cloud.ai.rag2okf.common.request.SaveModelBindingsRequest;
import com.fons.cloud.ai.rag2okf.common.request.UpdateKnowledgeBaseRequest;
import com.fons.cloud.ai.rag2okf.common.response.ChunkProfileResponse;
import com.fons.cloud.ai.rag2okf.common.response.KnowledgeBaseResponse;
import com.fons.cloud.ai.rag2okf.common.response.KnowledgeBaseSummaryResponse;
import com.fons.cloud.ai.rag2okf.common.response.ModelBindingResponse;
import com.fons.cloud.ai.rag2okf.common.response.PageResponse;
import com.fons.cloud.ai.rag2okf.domain.entity.KbKnowledgeBaseEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbModelBindingEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbModelProfileEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbUserEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbWorkspaceEntity;
import com.fons.cloud.ai.rag2okf.domain.knowledgebase.ChunkProfile;
import com.fons.cloud.ai.rag2okf.domain.service.KbKnowledgeBaseDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.KbModelBindingDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.KbModelProfileDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.KbWorkspaceDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.ModelUsagePolicy;
import com.fons.cloud.ai.rag2okf.infrastructure.identity.WorkspaceAccessPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 知识库创建、列表、详情、设置编辑与模型用途绑定的应用服务。
 *
 * <p>遵循 DDD-lite：领域规则（ChunkProfile 校验、autoPublish 依赖 autoParse）下沉到值对象，
 * 应用服务负责编排、事务、权限校验和持久化协调。Controller 不承载业务规则。</p>
 *
 * <p>设置只形成后续操作默认快照，修改设置不会批量重处理已有文档或任务。</p>
 *
 * @author hongqy
 */
@Service
@RequiredArgsConstructor
public class KnowledgeBaseApplicationService {

    private static final int MAX_NAME_LENGTH = 128;
    private static final int MAX_DESCRIPTION_LENGTH = 1000;
    private static final int MAX_PARSER_PROFILE_LENGTH = 64;
    private static final String KB_STATUS_ACTIVE = "ACTIVE";
    private static final String BINDING_STATUS_ACTIVE = "ACTIVE";

    private final CurrentUserContext currentUserContext;
    private final WorkspaceAccessPolicy workspaceAccessPolicy;
    private final KbWorkspaceDomainService workspaceDomainService;
    private final KbKnowledgeBaseDomainService knowledgeBaseDomainService;
    private final KbModelBindingDomainService modelBindingDomainService;
    private final KbModelProfileDomainService modelProfileDomainService;
    private final ModelBusinessKeyGenerator keyGenerator;
    private final ModelUsagePolicy modelUsagePolicy;
    private final ObjectMapper objectMapper;

    @Value("${sys.embedding.dims:1024}")
    private int embeddingDims;

    /**
     * 创建知识库。
     *
     * <p>需要 ADMIN 权限。创建后可选择性地同时保存初始模型用途绑定。</p>
     *
     * @param workspaceKey 工作空间业务标识
     * @param request 创建请求
     * @return 知识库详情响应
     */
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeBaseResponse createKnowledgeBase(String workspaceKey, CreateKnowledgeBaseRequest request) {
        KbUserEntity user = currentUserContext.requireCurrentUser();
        KbWorkspaceEntity workspace = requireWorkspaceAccess(user.getUserKey(), workspaceKey, WorkspaceRole.ADMIN);
        ChunkProfile chunkProfile = toChunkProfile(request.chunkProfile());
        validateSettings(request.autoParse(), request.autoPublish(), request.parserProfile());
        String name = requiredText(request.name(), MAX_NAME_LENGTH);
        String description = optionalText(request.description(), MAX_DESCRIPTION_LENGTH);
        String parserProfile = requiredText(request.parserProfile(), MAX_PARSER_PROFILE_LENGTH);

        KbKnowledgeBaseEntity entity = new KbKnowledgeBaseEntity();
        entity.setKnowledgeBaseKey(keyGenerator.nextKey());
        entity.setWorkspaceId(workspace.getId());
        entity.setName(name);
        entity.setDescription(description);
        entity.setAutoParse(request.autoParse());
        entity.setAutoPublish(request.autoPublish());
        entity.setParserProfile(parserProfile);
        entity.setChunkProfileJson(serializeChunkProfile(chunkProfile));
        entity.setStatus(KB_STATUS_ACTIVE);
        knowledgeBaseDomainService.save(entity);

        List<ModelBindingResponse> bindingResponses = List.of();
        if (request.modelBindings() != null && !request.modelBindings().isEmpty()) {
            bindingResponses = saveBindings(entity.getId(), workspace.getOwnerUserId(), request.modelBindings());
        }
        return toResponse(entity, workspace.getWorkspaceKey(), bindingResponses);
    }

    /**
     * 分页查询知识库列表。
     *
     * <p>需要 USER 权限。只返回当前工作空间下的知识库摘要。</p>
     *
     * @param workspaceKey 工作空间业务标识
     * @param page 页码（0 基）
     * @param size 每页大小
     * @return 分页知识库摘要
     */
    public PageResponse<KnowledgeBaseSummaryResponse> listKnowledgeBases(
            String workspaceKey, int page, int size) {
        KbUserEntity user = currentUserContext.requireCurrentUser();
        KbWorkspaceEntity workspace = requireWorkspaceAccess(
                user.getUserKey(), workspaceKey, WorkspaceRole.KNOWLEDGE_USER);
        Page<KbKnowledgeBaseEntity> pageParam = new Page<>(page + 1, size);
        Page<KbKnowledgeBaseEntity> result = knowledgeBaseDomainService.page(pageParam,
                Wrappers.<KbKnowledgeBaseEntity>lambdaQuery()
                        .eq(KbKnowledgeBaseEntity::getWorkspaceId, workspace.getId())
                        .orderByDesc(KbKnowledgeBaseEntity::getUpdated));
        List<KnowledgeBaseSummaryResponse> records = result.getRecords().stream()
                .map(this::toSummaryResponse)
                .toList();
        return new PageResponse<>(records, result.getTotal(), page, size);
    }

    /**
     * 查询知识库详情。
     *
     * <p>需要 USER 权限。返回基础信息、处理设置和模型用途绑定。</p>
     *
     * @param knowledgeBaseKey 知识库业务标识
     * @return 知识库详情响应
     */
    public KnowledgeBaseResponse getKnowledgeBase(String knowledgeBaseKey) {
        KbUserEntity user = currentUserContext.requireCurrentUser();
        KbKnowledgeBaseEntity entity = requireKnowledgeBase(knowledgeBaseKey);
        KbWorkspaceEntity workspace = requireWorkspaceAccess(
                user.getUserKey(), resolveWorkspaceKey(entity), WorkspaceRole.KNOWLEDGE_USER);
        List<ModelBindingResponse> bindings = loadBindings(entity.getId());
        return toResponse(entity, workspace.getWorkspaceKey(), bindings);
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
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeBaseResponse updateKnowledgeBase(String knowledgeBaseKey, UpdateKnowledgeBaseRequest request) {
        KbUserEntity user = currentUserContext.requireCurrentUser();
        KbKnowledgeBaseEntity entity = requireKnowledgeBase(knowledgeBaseKey);
        KbWorkspaceEntity workspace = requireWorkspaceAccess(
                user.getUserKey(), resolveWorkspaceKey(entity), WorkspaceRole.ADMIN);
        if (entity.getVersion() != request.revision()) {
            throw new KnowledgeBaseConflictException();
        }

        boolean autoParse = entity.getAutoParse();
        boolean autoPublish = entity.getAutoPublish();
        String parserProfile = entity.getParserProfile();
        ChunkProfile chunkProfile = deserializeChunkProfile(entity.getChunkProfileJson());

        if (request.name() != null) {
            entity.setName(requiredText(request.name(), MAX_NAME_LENGTH));
        }
        if (request.description() != null) {
            entity.setDescription(optionalText(request.description(), MAX_DESCRIPTION_LENGTH));
        }
        if (request.autoParse() != null) {
            autoParse = request.autoParse();
        }
        if (request.autoPublish() != null) {
            autoPublish = request.autoPublish();
        }
        if (request.parserProfile() != null) {
            parserProfile = requiredText(request.parserProfile(), MAX_PARSER_PROFILE_LENGTH);
        }
        if (request.chunkProfile() != null) {
            chunkProfile = toChunkProfile(request.chunkProfile());
        }
        validateSettings(autoParse, autoPublish, parserProfile);
        entity.setAutoParse(autoParse);
        entity.setAutoPublish(autoPublish);
        entity.setParserProfile(parserProfile);
        entity.setChunkProfileJson(serializeChunkProfile(chunkProfile));

        boolean updated = knowledgeBaseDomainService.updateById(entity);
        if (!updated) {
            throw new KnowledgeBaseConflictException();
        }

        List<ModelBindingResponse> bindingResponses = loadBindings(entity.getId());
        if (request.modelBindings() != null) {
            bindingResponses = saveBindings(entity.getId(), workspace.getOwnerUserId(), request.modelBindings());
        }
        return toResponse(entity, workspace.getWorkspaceKey(), bindingResponses);
    }

    /**
     * 查询知识库的模型用途绑定。
     *
     * <p>需要 USER 权限。不返回凭证。</p>
     *
     * @param knowledgeBaseKey 知识库业务标识
     * @return 模型用途绑定列表
     */
    public List<ModelBindingResponse> getModelBindings(String knowledgeBaseKey) {
        KbUserEntity user = currentUserContext.requireCurrentUser();
        KbKnowledgeBaseEntity entity = requireKnowledgeBase(knowledgeBaseKey);
        requireWorkspaceAccess(user.getUserKey(), resolveWorkspaceKey(entity), WorkspaceRole.KNOWLEDGE_USER);
        return loadBindings(entity.getId());
    }

    /**
     * 整体保存知识库模型用途绑定。
     *
     * <p>需要 ADMIN 权限。同一用途最多一个有效绑定；档案必须属于工作空间所有者且为 ACTIVE，
     * 且模型类型与用途兼容。保存前先删除现有绑定。</p>
     *
     * @param knowledgeBaseKey 知识库业务标识
     * @param request 保存请求
     * @return 保存后的模型用途绑定列表
     */
    @Transactional(rollbackFor = Exception.class)
    public List<ModelBindingResponse> saveModelBindings(String knowledgeBaseKey, SaveModelBindingsRequest request) {
        KbUserEntity user = currentUserContext.requireCurrentUser();
        KbKnowledgeBaseEntity entity = requireKnowledgeBase(knowledgeBaseKey);
        KbWorkspaceEntity workspace = requireWorkspaceAccess(
                user.getUserKey(), resolveWorkspaceKey(entity), WorkspaceRole.ADMIN);
        List<ModelBindingResponse> bindings = saveBindings(entity.getId(), workspace.getOwnerUserId(),
                request.modelBindings() != null ? request.modelBindings() : List.of());
        return bindings;
    }

    private List<ModelBindingResponse> saveBindings(
            Long knowledgeBaseId, Long workspaceOwnerUserId, List<ModelBindingItem> items) {
        validateBindingItems(items);
        modelBindingDomainService.remove(Wrappers.<KbModelBindingEntity>lambdaQuery()
                .eq(KbModelBindingEntity::getKnowledgeBaseId, knowledgeBaseId));
        List<ModelBindingResponse> responses = new ArrayList<>();
        for (ModelBindingItem item : items) {
            KbModelProfileEntity profile = requireOwnedActiveCompatibleProfile(
                    item.modelProfileKey(), workspaceOwnerUserId, item.usageType());
            KbModelBindingEntity binding = new KbModelBindingEntity();
            binding.setBindingKey(keyGenerator.nextKey());
            binding.setKnowledgeBaseId(knowledgeBaseId);
            binding.setUsageType(item.usageType());
            binding.setModelProfileId(profile.getId());
            binding.setConfigJson("{}");
            binding.setStatus(BINDING_STATUS_ACTIVE);
            modelBindingDomainService.save(binding);
            responses.add(new ModelBindingResponse(
                    binding.getBindingKey(), item.usageType(), profile.getProfileKey()));
        }
        return responses;
    }

    private void validateBindingItems(List<ModelBindingItem> items) {
        Set<ModelUsageType> seen = new HashSet<>();
        for (ModelBindingItem item : items) {
            if (item.usageType() == null) {
                throw new KnowledgeBaseException();
            }
            if (item.modelProfileKey() == null || item.modelProfileKey().isBlank()) {
                throw new KnowledgeBaseException();
            }
            if (!seen.add(item.usageType())) {
                throw new KnowledgeBaseException();
            }
        }
    }

    private KbModelProfileEntity requireOwnedActiveCompatibleProfile(
            String profileKey, Long workspaceOwnerUserId, ModelUsageType usageType) {
        KbModelProfileEntity profile = modelProfileDomainService.getOne(
                Wrappers.<KbModelProfileEntity>lambdaQuery()
                        .eq(KbModelProfileEntity::getProfileKey, profileKey));
        if (profile == null
                || !workspaceOwnerUserId.equals(profile.getOwnerUserId())
                || profile.getStatus() != ModelProfileStatus.ACTIVE
                || !modelUsagePolicy.isCompatible(usageType, profile.getModelType())) {
            throw new KnowledgeBaseException();
        }
        // CR-013：EMBEDDING 绑定维度必须与系统配置 sys.embedding.dims 一致，
        // 保证发布时同步向量化写入 ES 的 dense_vector 维度匹配（D-007）。
        if (usageType == ModelUsageType.EMBEDDING
                && (profile.getDimensions() == null || profile.getDimensions() != embeddingDims)) {
            throw new KnowledgeBaseException();
        }
        return profile;
    }

    private KbKnowledgeBaseEntity requireKnowledgeBase(String knowledgeBaseKey) {
        KbKnowledgeBaseEntity entity = knowledgeBaseDomainService.getOne(
                Wrappers.<KbKnowledgeBaseEntity>lambdaQuery()
                        .eq(KbKnowledgeBaseEntity::getKnowledgeBaseKey, knowledgeBaseKey));
        if (entity == null) {
            throw new WorkspaceAccessDeniedException();
        }
        return entity;
    }

    private KbWorkspaceEntity requireWorkspaceAccess(
            String userKey, String workspaceKey, WorkspaceRole requiredRole) {
        workspaceAccessPolicy.checkAccess(userKey, workspaceKey, requiredRole);
        KbWorkspaceEntity workspace = workspaceDomainService.getOne(
                Wrappers.<KbWorkspaceEntity>lambdaQuery()
                        .eq(KbWorkspaceEntity::getWorkspaceKey, workspaceKey));
        if (workspace == null) {
            throw new WorkspaceAccessDeniedException();
        }
        return workspace;
    }

    private String resolveWorkspaceKey(KbKnowledgeBaseEntity entity) {
        KbWorkspaceEntity workspace = workspaceDomainService.getById(entity.getWorkspaceId());
        if (workspace == null) {
            throw new WorkspaceAccessDeniedException();
        }
        return workspace.getWorkspaceKey();
    }

    private List<ModelBindingResponse> loadBindings(Long knowledgeBaseId) {
        return modelBindingDomainService.list(
                        Wrappers.<KbModelBindingEntity>lambdaQuery()
                                .eq(KbModelBindingEntity::getKnowledgeBaseId, knowledgeBaseId))
                .stream()
                .map(this::toBindingResponse)
                .toList();
    }

    private ModelBindingResponse toBindingResponse(KbModelBindingEntity binding) {
        KbModelProfileEntity profile = modelProfileDomainService.getById(binding.getModelProfileId());
        String profileKey = profile != null ? profile.getProfileKey() : null;
        return new ModelBindingResponse(binding.getBindingKey(), binding.getUsageType(), profileKey);
    }

    private void validateSettings(boolean autoParse, boolean autoPublish, String parserProfile) {
        requiredText(parserProfile, MAX_PARSER_PROFILE_LENGTH);
        if (autoPublish && !autoParse) {
            throw new KnowledgeBaseException();
        }
    }

    private ChunkProfile toChunkProfile(ChunkProfileRequest request) {
        if (request == null) {
            throw new KnowledgeBaseException();
        }
        return new ChunkProfile(request.strategy(), request.chunkSize(), request.overlap(), request.titleLevel());
    }

    private ChunkProfile deserializeChunkProfile(String json) {
        try {
            return objectMapper.readValue(json, ChunkProfile.class);
        } catch (JsonProcessingException exception) {
            throw new KnowledgeBaseException();
        }
    }

    private String serializeChunkProfile(ChunkProfile chunkProfile) {
        try {
            return objectMapper.writeValueAsString(chunkProfile);
        } catch (JsonProcessingException exception) {
            throw new KnowledgeBaseException();
        }
    }

    private String requiredText(String value, int maxLength) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw new KnowledgeBaseException();
        }
        return value.trim();
    }

    private String optionalText(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.trim().length() > maxLength) {
            throw new KnowledgeBaseException();
        }
        return value.trim();
    }

    private KnowledgeBaseResponse toResponse(
            KbKnowledgeBaseEntity entity, String workspaceKey, List<ModelBindingResponse> bindings) {
        ChunkProfile chunkProfile = deserializeChunkProfile(entity.getChunkProfileJson());
        ChunkProfileResponse chunkResponse = new ChunkProfileResponse(
                chunkProfile.strategy(), chunkProfile.chunkSize(),
                chunkProfile.overlap(), chunkProfile.titleLevel());
        return new KnowledgeBaseResponse(
                entity.getKnowledgeBaseKey(), workspaceKey, entity.getName(), entity.getDescription(),
                Boolean.TRUE.equals(entity.getAutoParse()), Boolean.TRUE.equals(entity.getAutoPublish()),
                entity.getParserProfile(), chunkResponse, bindings,
                entity.getVersion() != null ? entity.getVersion() : 0, entity.getUpdated());
    }

    private KnowledgeBaseSummaryResponse toSummaryResponse(KbKnowledgeBaseEntity entity) {
        return new KnowledgeBaseSummaryResponse(
                entity.getKnowledgeBaseKey(), entity.getName(), entity.getDescription(),
                Boolean.TRUE.equals(entity.getAutoParse()), Boolean.TRUE.equals(entity.getAutoPublish()),
                entity.getUpdated());
    }
}
