package com.fons.cloud.ai.rag2okf.application.publication;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fons.cloud.ai.rag2okf.common.dto.CredentialCipher;
import com.fons.cloud.ai.rag2okf.common.dto.EncryptedCredential;
import com.fons.cloud.ai.rag2okf.common.dto.ModelClientFactory;
import com.fons.cloud.ai.rag2okf.common.dto.ResolvedModelDescriptor;
import com.fons.cloud.ai.rag2okf.common.dto.ResolvedUserModel;
import com.fons.cloud.ai.rag2okf.common.dto.UserModelResolver;
import com.fons.cloud.ai.rag2okf.common.constants.ModelProfileStatus;
import com.fons.cloud.ai.rag2okf.common.constants.ModelUsageType;
import com.fons.cloud.ai.rag2okf.domain.entity.KbKnowledgeBaseEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbModelBindingEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbModelConnectionEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbModelProfileEntity;
import com.fons.cloud.ai.rag2okf.common.dto.PublicationProjectionPort.ChunkProjection;
import com.fons.cloud.ai.rag2okf.domain.service.KbKnowledgeBaseDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.KbModelBindingDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.KbModelProfileDomainService;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * EmbeddingProjectionService 单元测试，覆盖 CR-013 向量化场景（T045）。
 *
 * <p>使用 Mock EmbeddingModel，不真实调用 LLM。
 *
 * @author hongqy
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("发布时同步向量化")
class EmbeddingProjectionServiceTest {

    private static final String KB_KEY = "01J_KB";
    private static final Long KB_ID = 10L;
    private static final Long PROFILE_ID = 20L;
    private static final String PROFILE_KEY = "01J_PROFILE";
    private static final Long OWNER_USER_ID = 1L;
    private static final int DIMS = 1024;

    @Mock private KbKnowledgeBaseDomainService knowledgeBaseDomainService;
    @Mock private KbModelBindingDomainService modelBindingDomainService;
    @Mock private KbModelProfileDomainService modelProfileDomainService;
    @Mock private UserModelResolver userModelResolver;
    @Mock private CredentialCipher credentialCipher;
    @Mock private ModelClientFactory modelClientFactory;
    @Mock private EmbeddingModel embeddingModel;

    private EmbeddingProjectionService service;

    @BeforeEach
    void setUp() {
        service = new EmbeddingProjectionService(
                knowledgeBaseDomainService, modelBindingDomainService,
                modelProfileDomainService, userModelResolver,
                credentialCipher, modelClientFactory);
        // 通过反射注入 embeddingDims（@Value 字段）
        setField(service, "embeddingDims", DIMS);

        // 默认 stub：知识库存在
        KbKnowledgeBaseEntity kb = new KbKnowledgeBaseEntity();
        kb.setId(KB_ID);
        lenient().when(knowledgeBaseDomainService.getOne(any())).thenReturn(kb);
    }

    @Test
    @DisplayName("无 EMBEDDING 绑定时降级 BM25-only，vector 全为 null")
    void shouldDegradeToBm25OnlyWhenNoBinding() {
        when(modelBindingDomainService.getOne(any())).thenReturn(null);

        List<ChunkProjection> input = List.of(
                buildChunk("c0", "text0", false),
                buildChunk("c1", "text1", true));

        List<ChunkProjection> result = service.embedProjections(KB_KEY, input);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).vector()).isNull();
        assertThat(result.get(1).vector()).isNull();
        verify(embeddingModel, never()).embedAll(anyList());
    }

    @Test
    @DisplayName("向量化成功：有 EMBEDDING 绑定时 chunk 携带 vector")
    void shouldEmbedChunksWhenBindingExists() {
        setupEmbeddingBinding();
        float[] vector0 = buildVector(DIMS, 0.1f);
        float[] vector1 = buildVector(DIMS, 0.2f);
        when(embeddingModel.embedAll(anyList())).thenReturn(
                Response.from(List.of(new Embedding(vector0), new Embedding(vector1))));

        List<ChunkProjection> input = List.of(
                buildChunk("c0", "text0", false),
                buildChunk("c1", "text1", false));

        List<ChunkProjection> result = service.embedProjections(KB_KEY, input);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).vector()).isEqualTo(vector0);
        assertThat(result.get(1).vector()).isEqualTo(vector1);

        // 验证 embedAll 收到 2 个 segment
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TextSegment>> captor = ArgumentCaptor.forClass(List.class);
        verify(embeddingModel).embedAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
    }

    @Test
    @DisplayName("skipEmbedding chunk 不被向量化，vector 为 null")
    void shouldSkipEmbeddingChunks() {
        setupEmbeddingBinding();
        // 只有 c0 需要 embed，c1 是 skipEmbedding（embeddingText=null）
        float[] vector0 = buildVector(DIMS, 0.1f);
        when(embeddingModel.embedAll(anyList())).thenReturn(
                Response.from(List.of(new Embedding(vector0))));

        List<ChunkProjection> input = List.of(
                buildChunk("c0", "text0", false),
                buildChunk("c1", null, true));

        List<ChunkProjection> result = service.embedProjections(KB_KEY, input);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).vector()).isEqualTo(vector0);
        assertThat(result.get(1).vector()).isNull();

        // embedAll 只收到 1 个 segment（skipEmbedding 的被过滤）
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TextSegment>> captor = ArgumentCaptor.forClass(List.class);
        verify(embeddingModel).embedAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
    }

    @Test
    @DisplayName("维度不匹配时抛出 fatal=true 的 EmbeddingException")
    void shouldFailFatallyOnDimsMismatch() {
        setupEmbeddingBinding();
        // 返回 768 维向量，与配置 1024 不匹配
        float[] wrongVector = buildVector(768, 0.1f);
        when(embeddingModel.embedAll(anyList())).thenReturn(
                Response.from(List.of(new Embedding(wrongVector))));

        List<ChunkProjection> input = List.of(buildChunk("c0", "text0", false));

        assertThatThrownBy(() -> service.embedProjections(KB_KEY, input))
                .isInstanceOf(EmbeddingProjectionService.EmbeddingException.class)
                .satisfies(ex -> {
                    EmbeddingProjectionService.EmbeddingException ee =
                            (EmbeddingProjectionService.EmbeddingException) ex;
                    assertThat(ee.errorCode())
                            .isEqualTo(EmbeddingProjectionService.ERR_DIMS_MISMATCH);
                    assertThat(ee.fatal()).isTrue();
                });
    }

    @Test
    @DisplayName("模型调用失败时抛出 fatal=false 的 EmbeddingException")
    void shouldRetryOnModelInvocationError() {
        setupEmbeddingBinding();
        when(embeddingModel.embedAll(anyList()))
                .thenThrow(new RuntimeException("connection timeout"));

        List<ChunkProjection> input = List.of(buildChunk("c0", "text0", false));

        assertThatThrownBy(() -> service.embedProjections(KB_KEY, input))
                .isInstanceOf(EmbeddingProjectionService.EmbeddingException.class)
                .satisfies(ex -> {
                    EmbeddingProjectionService.EmbeddingException ee =
                            (EmbeddingProjectionService.EmbeddingException) ex;
                    assertThat(ee.errorCode())
                            .isEqualTo(EmbeddingProjectionService.ERR_MODEL_INVOCATION);
                    assertThat(ee.fatal()).isFalse();
                });
    }

    @Test
    @DisplayName("模型档案不可用时抛出 fatal=true 的 EmbeddingException")
    void shouldFailFatallyOnProfileUnavailable() {
        KbModelBindingEntity binding = new KbModelBindingEntity();
        binding.setModelProfileId(PROFILE_ID);
        binding.setUsageType(ModelUsageType.EMBEDDING);
        when(modelBindingDomainService.getOne(any())).thenReturn(binding);

        // 档案存在但非 ACTIVE
        KbModelProfileEntity profile = new KbModelProfileEntity();
        profile.setStatus(ModelProfileStatus.DISABLED);
        when(modelProfileDomainService.getById(PROFILE_ID)).thenReturn(profile);

        List<ChunkProjection> input = List.of(buildChunk("c0", "text0", false));

        assertThatThrownBy(() -> service.embedProjections(KB_KEY, input))
                .isInstanceOf(EmbeddingProjectionService.EmbeddingException.class)
                .satisfies(ex -> {
                    EmbeddingProjectionService.EmbeddingException ee =
                            (EmbeddingProjectionService.EmbeddingException) ex;
                    assertThat(ee.errorCode())
                            .isEqualTo(EmbeddingProjectionService.ERR_PROFILE_UNAVAILABLE);
                    assertThat(ee.fatal()).isTrue();
                });
    }

    // ────────────────────────────── 辅助 ──────────────────────────────

    /**
     * 配置完整的 EMBEDDING 绑定链路：binding -> profile -> resolvedModel -> client。
     */
    private void setupEmbeddingBinding() {
        KbModelBindingEntity binding = new KbModelBindingEntity();
        binding.setModelProfileId(PROFILE_ID);
        binding.setUsageType(ModelUsageType.EMBEDDING);
        when(modelBindingDomainService.getOne(any())).thenReturn(binding);

        KbModelProfileEntity profile = new KbModelProfileEntity();
        profile.setId(PROFILE_ID);
        profile.setProfileKey(PROFILE_KEY);
        profile.setOwnerUserId(OWNER_USER_ID);
        profile.setStatus(ModelProfileStatus.ACTIVE);
        when(modelProfileDomainService.getById(PROFILE_ID)).thenReturn(profile);

        KbModelConnectionEntity connection = new KbModelConnectionEntity();
        connection.setApiKeyCiphertext(new byte[]{1, 2});
        connection.setApiKeyNonce(new byte[]{3, 4});
        connection.setKeyVersion("v1");
        ResolvedModelDescriptor descriptor = mock(ResolvedModelDescriptor.class);
        ResolvedUserModel resolvedModel = new ResolvedUserModel(profile, connection, descriptor);
        when(userModelResolver.resolveOwnedActiveProfile(PROFILE_KEY, OWNER_USER_ID))
                .thenReturn(resolvedModel);

        when(credentialCipher.decrypt(any(EncryptedCredential.class))).thenReturn("decrypted-key");
        when(modelClientFactory.createEmbeddingModel(any(), anyString())).thenReturn(embeddingModel);
    }

    private ChunkProjection buildChunk(String chunkKey, String embeddingText, boolean skipEmbedding) {
        return new ChunkProjection(
                chunkKey,
                null,
                ChunkProjection.LEVEL_PARENT,
                0,
                "raw-" + chunkKey,
                "display-" + chunkKey,
                embeddingText,
                null,
                "NONE",
                null,
                null,
                null,
                null);
    }

    private float[] buildVector(int dims, float base) {
        float[] v = new float[dims];
        for (int i = 0; i < dims; i++) {
            v[i] = base + i * 0.001f;
        }
        return v;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
