package com.fons.cloud.ai.rag2okf.application.knowledgebase;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fons.cloud.ai.rag2okf.common.dto.CurrentUserContext;
import com.fons.cloud.ai.rag2okf.common.dto.ModelBusinessKeyGenerator;
import com.fons.cloud.ai.rag2okf.common.constants.ModelProfileStatus;
import com.fons.cloud.ai.rag2okf.common.constants.ModelType;
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
import com.fons.cloud.ai.rag2okf.common.response.KnowledgeBaseResponse;
import com.fons.cloud.ai.rag2okf.common.response.ModelBindingResponse;
import com.fons.cloud.ai.rag2okf.common.response.PageResponse;
import com.fons.cloud.ai.rag2okf.domain.entity.KbKnowledgeBaseEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbModelBindingEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbModelProfileEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbUserEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbWorkspaceEntity;
import com.fons.cloud.ai.rag2okf.domain.service.KbKnowledgeBaseDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.KbModelBindingDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.KbModelProfileDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.KbWorkspaceDomainService;
import com.fons.cloud.ai.rag2okf.common.dto.ModelUsagePolicy;
import com.fons.cloud.ai.rag2okf.infrastructure.identity.WorkspaceAccessPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 知识库应用服务的创建、列表、设置编辑、模型绑定与并发冲突测试。
 *
 * <p>覆盖 AC-003（创建知识库）、AC-004（设置快照不追溯）、AC-034（用途绑定）和 AC-035（fail-closed）。</p>
 *
 * @author hongqy
 */
class KnowledgeBaseApplicationServiceTest {

    private static final String USER_KEY = "01JUSERKEY000000000000001";
    private static final String WORKSPACE_KEY = "01JWORKSPACE00000000000001";
    private static final String KB_KEY = "01JKBKEY00000000000000001A";
    private static final String CHAT_PROFILE_KEY = "01JCHATPROFILE000000000001";
    private static final String EMBED_PROFILE_KEY = "01JEMBEDPROFILE000000000001";
    private static final String BINDING_KEY = "01JBINDINGKEY000000000000001";

    private CurrentUserContext currentUserContext;
    private WorkspaceAccessPolicy workspaceAccessPolicy;
    private KbWorkspaceDomainService workspaceDomainService;
    private KbKnowledgeBaseDomainService knowledgeBaseDomainService;
    private KbModelBindingDomainService modelBindingDomainService;
    private KbModelProfileDomainService modelProfileDomainService;
    private ModelBusinessKeyGenerator keyGenerator;
    private ModelUsagePolicy modelUsagePolicy;
    private ObjectMapper objectMapper;
    private KnowledgeBaseApplicationService service;

    @BeforeEach
    void setUp() {
        currentUserContext = mock(CurrentUserContext.class);
        workspaceAccessPolicy = mock(WorkspaceAccessPolicy.class);
        workspaceDomainService = mock(KbWorkspaceDomainService.class);
        knowledgeBaseDomainService = mock(KbKnowledgeBaseDomainService.class);
        modelBindingDomainService = mock(KbModelBindingDomainService.class);
        modelProfileDomainService = mock(KbModelProfileDomainService.class);
        keyGenerator = mock(ModelBusinessKeyGenerator.class);
        modelUsagePolicy = new ModelUsagePolicy();
        objectMapper = new ObjectMapper();
        service = new KnowledgeBaseApplicationService(
                currentUserContext, workspaceAccessPolicy, workspaceDomainService,
                knowledgeBaseDomainService, modelBindingDomainService, modelProfileDomainService,
                keyGenerator, modelUsagePolicy, objectMapper);
        // 注入 embeddingDims（@Value 字段，单元测试中需手动设置，CR-013 T043）
        setEmbeddingDims(service, 1024);
        when(currentUserContext.requireCurrentUser()).thenReturn(user(1L));
        when(keyGenerator.nextKey()).thenReturn(KB_KEY, BINDING_KEY);
        when(workspaceDomainService.getOne(any())).thenReturn(workspace(1L));
        when(workspaceDomainService.getById(any())).thenReturn(workspace(1L));
    }

    @Test
    void shouldCreateKnowledgeBaseWithValidSettings() {
        when(workspaceDomainService.getOne(any())).thenReturn(workspace(1L));
        CreateKnowledgeBaseRequest request = createRequest("金融知识库", true, false,
                "NATIVE_TIKA", "markdown-header", 1000, 100, 3);

        KnowledgeBaseResponse response = service.createKnowledgeBase(WORKSPACE_KEY, request);

        ArgumentCaptor<KbKnowledgeBaseEntity> captor = ArgumentCaptor.forClass(KbKnowledgeBaseEntity.class);
        verify(knowledgeBaseDomainService).save(captor.capture());
        KbKnowledgeBaseEntity saved = captor.getValue();
        assertThat(saved.getKnowledgeBaseKey()).isEqualTo(KB_KEY);
        assertThat(saved.getWorkspaceId()).isEqualTo(1L);
        assertThat(saved.getName()).isEqualTo("金融知识库");
        assertThat(saved.getAutoParse()).isTrue();
        assertThat(saved.getAutoPublish()).isFalse();
        assertThat(saved.getParserProfile()).isEqualTo("NATIVE_TIKA");
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        assertThat(response.knowledgeBaseKey()).isEqualTo(KB_KEY);
        assertThat(response.autoParse()).isTrue();
        assertThat(response.chunkProfile().strategy()).isEqualTo("markdown-header");
        assertThat(response.chunkProfile().chunkSize()).isEqualTo(1000);
        assertThat(response.modelBindings()).isEmpty();
    }

    @Test
    void shouldCreateKnowledgeBaseWithInitialModelBindings() {
        when(workspaceDomainService.getOne(any())).thenReturn(workspace(1L));
        when(modelProfileDomainService.getOne(any())).thenReturn(chatProfile(1L));
        CreateKnowledgeBaseRequest request = new CreateKnowledgeBaseRequest(
                "金融知识库", "描述", true, false, "NATIVE_TIKA",
                new ChunkProfileRequest("markdown-header", 1000, 100, 3),
                List.of(new ModelBindingItem(ModelUsageType.ANSWER_GENERATION, CHAT_PROFILE_KEY)),
                0);

        KnowledgeBaseResponse response = service.createKnowledgeBase(WORKSPACE_KEY, request);

        verify(modelBindingDomainService).save(any(KbModelBindingEntity.class));
        assertThat(response.modelBindings()).hasSize(1);
        assertThat(response.modelBindings().get(0).usageType()).isEqualTo(ModelUsageType.ANSWER_GENERATION);
        assertThat(response.modelBindings().get(0).modelProfileKey()).isEqualTo(CHAT_PROFILE_KEY);
    }

    @Test
    void shouldRejectCreateWithAutoPublishButNotAutoParse() {
        CreateKnowledgeBaseRequest request = createRequest("知识库", false, true,
                "NATIVE_TIKA", "markdown-header", 1000, 100, 3);
        assertThatThrownBy(() -> service.createKnowledgeBase(WORKSPACE_KEY, request))
                .isInstanceOf(KnowledgeBaseException.class);
    }

    @Test
    void shouldRejectCreateWithBlankName() {
        CreateKnowledgeBaseRequest request = createRequest("  ", true, false,
                "NATIVE_TIKA", "markdown-header", 1000, 100, 3);
        assertThatThrownBy(() -> service.createKnowledgeBase(WORKSPACE_KEY, request))
                .isInstanceOf(KnowledgeBaseException.class);
    }

    @Test
    void shouldRejectCreateWithInvalidChunkProfile() {
        CreateKnowledgeBaseRequest request = createRequest("知识库", true, false,
                "NATIVE_TIKA", "markdown-header", 0, 100, 3);
        assertThatThrownBy(() -> service.createKnowledgeBase(WORKSPACE_KEY, request))
                .isInstanceOf(KnowledgeBaseException.class);
    }

    @Test
    void shouldRejectCreateWithOverlapGreaterThanOrEqualToChunkSize() {
        CreateKnowledgeBaseRequest request = createRequest("知识库", true, false,
                "NATIVE_TIKA", "markdown-header", 500, 500, 3);
        assertThatThrownBy(() -> service.createKnowledgeBase(WORKSPACE_KEY, request))
                .isInstanceOf(KnowledgeBaseException.class);
    }

    @Test
    void shouldRejectCreateWhenNotAdmin() {
        doThrow(new WorkspaceAccessDeniedException()).when(workspaceAccessPolicy)
                .checkAccess(USER_KEY, WORKSPACE_KEY, WorkspaceRole.ADMIN);
        CreateKnowledgeBaseRequest request = createRequest("知识库", true, false,
                "NATIVE_TIKA", "markdown-header", 1000, 100, 3);
        assertThatThrownBy(() -> service.createKnowledgeBase(WORKSPACE_KEY, request))
                .isInstanceOf(WorkspaceAccessDeniedException.class);
    }

    @Test
    void shouldPaginateKnowledgeBases() {
        when(workspaceDomainService.getOne(any())).thenReturn(workspace(1L));
        Page<KbKnowledgeBaseEntity> pageResult = new Page<>(1, 20);
        pageResult.setTotal(2);
        pageResult.setRecords(List.of(kbEntity(1L, "KB1"), kbEntity(2L, "KB2")));
        when(knowledgeBaseDomainService.page(any(Page.class), any())).thenReturn(pageResult);

        PageResponse<com.fons.cloud.ai.rag2okf.common.response.KnowledgeBaseSummaryResponse> result =
                service.listKnowledgeBases(WORKSPACE_KEY, 0, 20);

        assertThat(result.records()).hasSize(2);
        assertThat(result.total()).isEqualTo(2);
        assertThat(result.page()).isEqualTo(0);
        assertThat(result.size()).isEqualTo(20);
        assertThat(result.records().get(0).name()).isEqualTo("KB1");
    }

    @Test
    void shouldGetKnowledgeBaseDetail() {
        when(workspaceDomainService.getById(1L)).thenReturn(workspace(1L));
        KbKnowledgeBaseEntity entity = kbEntity(1L, "金融知识库");
        when(knowledgeBaseDomainService.getOne(any())).thenReturn(entity);
        when(modelBindingDomainService.list(any(Wrapper.class))).thenReturn(List.of());
        KnowledgeBaseResponse response = service.getKnowledgeBase(KB_KEY);
        assertThat(response.name()).isEqualTo("金融知识库");
        assertThat(response.chunkProfile().strategy()).isEqualTo("markdown-header");
    }

    @Test
    void shouldUpdateKnowledgeBaseSettings() {
        when(workspaceDomainService.getById(1L)).thenReturn(workspace(1L));
        KbKnowledgeBaseEntity entity = kbEntity(1L, "旧名称");
        when(knowledgeBaseDomainService.getOne(any())).thenReturn(entity);
        when(knowledgeBaseDomainService.updateById(any())).thenReturn(true);
        when(modelBindingDomainService.list(any(Wrapper.class))).thenReturn(List.of());

        UpdateKnowledgeBaseRequest request = new UpdateKnowledgeBaseRequest(
                "新名称", "新描述", true, false, "NATIVE_TIKA",
                new ChunkProfileRequest("recursive", 800, 80, null),
                null, 0);

        KnowledgeBaseResponse response = service.updateKnowledgeBase(KB_KEY, request);

        assertThat(response.name()).isEqualTo("新名称");
        assertThat(response.chunkProfile().strategy()).isEqualTo("recursive");
        assertThat(response.chunkProfile().chunkSize()).isEqualTo(800);
        verify(knowledgeBaseDomainService).updateById(any());
    }

    @Test
    void shouldRejectUpdateWithStaleRevision() {
        when(workspaceDomainService.getById(1L)).thenReturn(workspace(1L));
        KbKnowledgeBaseEntity entity = kbEntity(1L, "知识库");
        entity.setVersion(3);
        when(knowledgeBaseDomainService.getOne(any())).thenReturn(entity);

        UpdateKnowledgeBaseRequest request = new UpdateKnowledgeBaseRequest(
                "新名称", null, null, null, null, null, null, 1);

        assertThatThrownBy(() -> service.updateKnowledgeBase(KB_KEY, request))
                .isInstanceOf(KnowledgeBaseConflictException.class);
        verify(knowledgeBaseDomainService, never()).updateById(any());
    }

    @Test
    void shouldRejectUpdateWhenDatabaseVersionChanged() {
        when(workspaceDomainService.getById(1L)).thenReturn(workspace(1L));
        KbKnowledgeBaseEntity entity = kbEntity(1L, "知识库");
        entity.setVersion(0);
        when(knowledgeBaseDomainService.getOne(any())).thenReturn(entity);
        when(knowledgeBaseDomainService.updateById(any())).thenReturn(false);

        UpdateKnowledgeBaseRequest request = new UpdateKnowledgeBaseRequest(
                "新名称", null, null, null, null, null, null, 0);

        assertThatThrownBy(() -> service.updateKnowledgeBase(KB_KEY, request))
                .isInstanceOf(KnowledgeBaseConflictException.class);
    }

    @Test
    void shouldSaveModelBindingsWithValidChatAndEmbeddingProfiles() {
        when(workspaceDomainService.getById(1L)).thenReturn(workspace(1L));
        KbKnowledgeBaseEntity entity = kbEntity(1L, "知识库");
        when(knowledgeBaseDomainService.getOne(any())).thenReturn(entity);
        when(modelProfileDomainService.getOne(any())).thenReturn(
                chatProfile(1L), embedProfile(1L));

        SaveModelBindingsRequest request = new SaveModelBindingsRequest(List.of(
                new ModelBindingItem(ModelUsageType.ANSWER_GENERATION, CHAT_PROFILE_KEY),
                new ModelBindingItem(ModelUsageType.EMBEDDING, EMBED_PROFILE_KEY)));

        List<ModelBindingResponse> result = service.saveModelBindings(KB_KEY, request);

        verify(modelBindingDomainService).remove(any());
        verify(modelBindingDomainService, times(2)).save(any(KbModelBindingEntity.class));
        assertThat(result).hasSize(2);
    }

    @Test
    void shouldRejectBindingWithProfileNotOwnedByWorkspaceOwner() {
        when(workspaceDomainService.getById(1L)).thenReturn(workspace(1L));
        KbKnowledgeBaseEntity entity = kbEntity(1L, "知识库");
        when(knowledgeBaseDomainService.getOne(any())).thenReturn(entity);
        when(modelProfileDomainService.getOne(any())).thenReturn(chatProfile(2L));

        SaveModelBindingsRequest request = new SaveModelBindingsRequest(List.of(
                new ModelBindingItem(ModelUsageType.ANSWER_GENERATION, CHAT_PROFILE_KEY)));

        assertThatThrownBy(() -> service.saveModelBindings(KB_KEY, request))
                .isInstanceOf(KnowledgeBaseException.class);
    }

    @Test
    void shouldRejectBindingWithInactiveProfile() {
        when(workspaceDomainService.getById(1L)).thenReturn(workspace(1L));
        KbKnowledgeBaseEntity entity = kbEntity(1L, "知识库");
        when(knowledgeBaseDomainService.getOne(any())).thenReturn(entity);
        KbModelProfileEntity inactiveProfile = chatProfile(1L);
        inactiveProfile.setStatus(ModelProfileStatus.DISABLED);
        when(modelProfileDomainService.getOne(any())).thenReturn(inactiveProfile);

        SaveModelBindingsRequest request = new SaveModelBindingsRequest(List.of(
                new ModelBindingItem(ModelUsageType.ANSWER_GENERATION, CHAT_PROFILE_KEY)));

        assertThatThrownBy(() -> service.saveModelBindings(KB_KEY, request))
                .isInstanceOf(KnowledgeBaseException.class);
    }

    @Test
    void shouldRejectBindingWithIncompatibleModelType() {
        when(workspaceDomainService.getById(1L)).thenReturn(workspace(1L));
        KbKnowledgeBaseEntity entity = kbEntity(1L, "知识库");
        when(knowledgeBaseDomainService.getOne(any())).thenReturn(entity);
        when(modelProfileDomainService.getOne(any())).thenReturn(embedProfile(1L));

        SaveModelBindingsRequest request = new SaveModelBindingsRequest(List.of(
                new ModelBindingItem(ModelUsageType.ANSWER_GENERATION, EMBED_PROFILE_KEY)));

        assertThatThrownBy(() -> service.saveModelBindings(KB_KEY, request))
                .isInstanceOf(KnowledgeBaseException.class);
    }

    @Test
    void shouldRejectDuplicateUsageTypeInBindings() {
        when(workspaceDomainService.getById(1L)).thenReturn(workspace(1L));
        KbKnowledgeBaseEntity entity = kbEntity(1L, "知识库");
        when(knowledgeBaseDomainService.getOne(any())).thenReturn(entity);

        SaveModelBindingsRequest request = new SaveModelBindingsRequest(List.of(
                new ModelBindingItem(ModelUsageType.ANSWER_GENERATION, CHAT_PROFILE_KEY),
                new ModelBindingItem(ModelUsageType.ANSWER_GENERATION, "01JOTHERPROFILE0000000001")));

        assertThatThrownBy(() -> service.saveModelBindings(KB_KEY, request))
                .isInstanceOf(KnowledgeBaseException.class);
    }

    @Test
    void shouldReplaceExistingBindingsWhenSaving() {
        when(workspaceDomainService.getById(1L)).thenReturn(workspace(1L));
        KbKnowledgeBaseEntity entity = kbEntity(1L, "知识库");
        when(knowledgeBaseDomainService.getOne(any())).thenReturn(entity);
        when(modelProfileDomainService.getOne(any())).thenReturn(chatProfile(1L));

        SaveModelBindingsRequest request = new SaveModelBindingsRequest(List.of(
                new ModelBindingItem(ModelUsageType.ANSWER_GENERATION, CHAT_PROFILE_KEY)));

        service.saveModelBindings(KB_KEY, request);

        verify(modelBindingDomainService).remove(any());
        verify(modelBindingDomainService).save(any(KbModelBindingEntity.class));
    }

    @Test
    void shouldNotModifyTasksWhenUpdatingSettings() {
        when(workspaceDomainService.getById(1L)).thenReturn(workspace(1L));
        KbKnowledgeBaseEntity entity = kbEntity(1L, "知识库");
        when(knowledgeBaseDomainService.getOne(any())).thenReturn(entity);
        when(knowledgeBaseDomainService.updateById(any())).thenReturn(true);
        when(modelBindingDomainService.list(any(Wrapper.class))).thenReturn(List.of());

        UpdateKnowledgeBaseRequest request = new UpdateKnowledgeBaseRequest(
                "新名称", null, null, null, null, null, null, 0);

        service.updateKnowledgeBase(KB_KEY, request);

        verify(knowledgeBaseDomainService).updateById(any());
        verify(modelBindingDomainService, never()).save(any());
        verify(modelBindingDomainService, never()).remove(any());
    }

    @Test
    void shouldRejectAccessWhenUserIsNotWorkspaceMember() {
        doThrow(new WorkspaceAccessDeniedException()).when(workspaceAccessPolicy)
                .checkAccess(USER_KEY, WORKSPACE_KEY, WorkspaceRole.KNOWLEDGE_USER);
        assertThatThrownBy(() -> service.listKnowledgeBases(WORKSPACE_KEY, 0, 20))
                .isInstanceOf(WorkspaceAccessDeniedException.class);
    }

    private KbUserEntity user(Long id) {
        KbUserEntity user = new KbUserEntity();
        user.setId(id);
        user.setUserKey(USER_KEY);
        return user;
    }

    private KbWorkspaceEntity workspace(Long ownerUserId) {
        KbWorkspaceEntity workspace = new KbWorkspaceEntity();
        workspace.setId(1L);
        workspace.setWorkspaceKey(WORKSPACE_KEY);
        workspace.setOwnerUserId(ownerUserId);
        return workspace;
    }

    private KbKnowledgeBaseEntity kbEntity(Long id, String name) {
        KbKnowledgeBaseEntity entity = new KbKnowledgeBaseEntity();
        entity.setId(id);
        entity.setKnowledgeBaseKey(KB_KEY);
        entity.setWorkspaceId(1L);
        entity.setName(name);
        entity.setAutoParse(true);
        entity.setAutoPublish(false);
        entity.setParserProfile("NATIVE_TIKA");
        entity.setChunkProfileJson("{\"strategy\":\"markdown-header\",\"chunkSize\":1000,\"overlap\":100,\"titleLevel\":3}");
        entity.setStatus("ACTIVE");
        entity.setVersion(0);
        return entity;
    }

    private KbModelProfileEntity chatProfile(Long ownerUserId) {
        KbModelProfileEntity profile = new KbModelProfileEntity();
        profile.setId(10L);
        profile.setProfileKey(CHAT_PROFILE_KEY);
        profile.setOwnerUserId(ownerUserId);
        profile.setModelType(ModelType.CHAT);
        profile.setStatus(ModelProfileStatus.ACTIVE);
        return profile;
    }

    private KbModelProfileEntity embedProfile(Long ownerUserId) {
        KbModelProfileEntity profile = new KbModelProfileEntity();
        profile.setId(11L);
        profile.setProfileKey(EMBED_PROFILE_KEY);
        profile.setOwnerUserId(ownerUserId);
        profile.setModelType(ModelType.EMBEDDING);
        profile.setStatus(ModelProfileStatus.ACTIVE);
        // CR-013 T043：EMBEDDING 档案维度必须与系统配置一致
        profile.setDimensions(1024);
        return profile;
    }

    private CreateKnowledgeBaseRequest createRequest(
            String name, boolean autoParse, boolean autoPublish,
            String parserProfile, String strategy, int chunkSize, int overlap, Integer titleLevel) {
        return new CreateKnowledgeBaseRequest(
                name, "描述", autoParse, autoPublish, parserProfile,
                new ChunkProfileRequest(strategy, chunkSize, overlap, titleLevel),
                null, 0);
    }

    /**
     * 通过反射注入 {@code embeddingDims}（@Value 字段，CR-013 T043）。
     */
    private void setEmbeddingDims(KnowledgeBaseApplicationService target, int dims) {
        try {
            java.lang.reflect.Field field = KnowledgeBaseApplicationService.class
                    .getDeclaredField("embeddingDims");
            field.setAccessible(true);
            field.set(target, dims);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
