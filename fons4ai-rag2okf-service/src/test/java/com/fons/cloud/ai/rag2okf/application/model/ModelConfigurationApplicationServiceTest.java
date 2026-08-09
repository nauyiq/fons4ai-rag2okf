package com.fons.cloud.ai.rag2okf.application.model;

import com.fons.cloud.ai.rag2okf.common.constants.ModelConnectionStatus;
import com.fons.cloud.ai.rag2okf.common.constants.ModelProfileStatus;
import com.fons.cloud.ai.rag2okf.common.constants.ModelTestStatus;
import com.fons.cloud.ai.rag2okf.common.constants.ModelType;
import com.fons.cloud.ai.rag2okf.common.dto.CurrentUserContext;
import com.fons.cloud.ai.rag2okf.common.dto.ModelBusinessKeyGenerator;
import com.fons.cloud.ai.rag2okf.common.dto.ModelClientFactory;
import com.fons.cloud.ai.rag2okf.common.dto.ModelParameterCodec;
import com.fons.cloud.ai.rag2okf.common.dto.UserModelResolver;
import com.fons.cloud.ai.rag2okf.common.exeception.ModelAccessDeniedException;
import com.fons.cloud.ai.rag2okf.common.request.CreateModelConnectionRequest;
import com.fons.cloud.ai.rag2okf.common.request.CreateModelProfileRequest;
import com.fons.cloud.ai.rag2okf.domain.entity.KbModelConnectionEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbModelProfileEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbUserEntity;
import com.fons.cloud.ai.rag2okf.domain.service.KbModelConnectionDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.KbModelProfileDomainService;
import com.fons.cloud.ai.rag2okf.infrastructure.model.AesGcmCredentialCipher;
import com.fons.cloud.ai.rag2okf.infrastructure.model.ModelEndpointPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户模型配置应用服务的所有权、凭证与动态调用边界测试。
 *
 * @author hongqy
 */
class ModelConfigurationApplicationServiceTest {

    @Test
    void shouldEncryptApiKeyAndReturnOnlyMaskedConnectionData() {
        CurrentUserContext currentUserContext = mock(CurrentUserContext.class);
        KbUserEntity user = user(1L);
        when(currentUserContext.requireCurrentUser()).thenReturn(user);
        KbModelConnectionDomainService connectionService = mock(KbModelConnectionDomainService.class);
        ModelConfigurationApplicationService service = service(currentUserContext, connectionService,
                mock(KbModelProfileDomainService.class), mock(ModelClientFactory.class));

        var response = service.createConnection(new CreateModelConnectionRequest(
                "CUSTOM", "Test Provider", "My connection", "https://8.8.8.8/v1", "secret-key-value"
        ));

        ArgumentCaptor<KbModelConnectionEntity> saved = ArgumentCaptor.forClass(KbModelConnectionEntity.class);
        verify(connectionService).save(saved.capture());
        assertThat(saved.getValue().getOwnerUserId()).isEqualTo(1L);
        assertThat(saved.getValue().getApiKeyCiphertext()).isNotEqualTo("secret-key-value".getBytes());
        assertThat(response.apiKeyMask()).doesNotContain("secret-key-value");
        assertThat(response.connectionKey()).hasSize(26);
    }

    @Test
    void shouldRejectCreatingAProfileOnAnotherUsersConnection() {
        CurrentUserContext currentUserContext = mock(CurrentUserContext.class);
        when(currentUserContext.requireCurrentUser()).thenReturn(user(1L));
        KbModelConnectionDomainService connectionService = mock(KbModelConnectionDomainService.class);
        KbModelConnectionEntity foreignConnection = connection(2L);
        when(connectionService.getOne(any())).thenReturn(foreignConnection);
        ModelConfigurationApplicationService service = service(currentUserContext, connectionService,
                mock(KbModelProfileDomainService.class), mock(ModelClientFactory.class));

        assertThatThrownBy(() -> service.createProfile(new CreateModelProfileRequest(
                foreignConnection.getConnectionKey(), ModelType.CHAT, "chat-model", null, null, 30, 0.2D
        ))).isInstanceOf(ModelAccessDeniedException.class);
    }

    @Test
    void shouldTestAnOwnedActiveChatProfileWithoutPersistingTheApiKeyInTheResponse() {
        CurrentUserContext currentUserContext = mock(CurrentUserContext.class);
        when(currentUserContext.requireCurrentUser()).thenReturn(user(1L));
        KbModelConnectionDomainService connectionService = mock(KbModelConnectionDomainService.class);
        KbModelProfileDomainService profileService = mock(KbModelProfileDomainService.class);
        KbModelConnectionEntity connection = connection(1L);
        KbModelProfileEntity profile = profile(connection.getId());
        when(profileService.getOne(any())).thenReturn(profile);
        when(connectionService.getById(connection.getId())).thenReturn(connection);
        ModelClientFactory factory = mock(ModelClientFactory.class);
        ChatModel chatModel = mock(ChatModel.class);
        when(factory.createChatModel(any(), any())).thenReturn(chatModel);
        when(chatModel.chat(any(String.class))).thenReturn("ok");
        ModelConfigurationApplicationService service = service(currentUserContext, connectionService, profileService, factory);

        var response = service.testProfile(profile.getProfileKey());

        assertThat(response.status()).isEqualTo(ModelTestStatus.SUCCEEDED);
        assertThat(response.errorCode()).isNull();
        verify(profileService).updateById(profile);
        verify(connectionService).updateById(connection);
    }

    @Test
    void shouldReturnASanitizedFailureWhenEmbeddingDimensionsDoNotMatchTheProfile() {
        CurrentUserContext currentUserContext = mock(CurrentUserContext.class);
        when(currentUserContext.requireCurrentUser()).thenReturn(user(1L));
        KbModelConnectionDomainService connectionService = mock(KbModelConnectionDomainService.class);
        KbModelProfileDomainService profileService = mock(KbModelProfileDomainService.class);
        KbModelConnectionEntity connection = connection(1L);
        KbModelProfileEntity profile = profile(connection.getId());
        profile.setModelType(ModelType.EMBEDDING);
        profile.setDimensions(3);
        profile.setParametersJson("{\"timeoutSeconds\":30,\"temperature\":null}");
        when(profileService.getOne(any())).thenReturn(profile);
        when(connectionService.getById(connection.getId())).thenReturn(connection);
        ModelClientFactory factory = mock(ModelClientFactory.class);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        when(factory.createEmbeddingModel(any(), any())).thenReturn(embeddingModel);
        when(embeddingModel.embed(any(String.class))).thenReturn(Response.from(Embedding.from(new float[]{1F, 2F})));
        ModelConfigurationApplicationService service = service(currentUserContext, connectionService, profileService, factory);

        var response = service.testProfile(profile.getProfileKey());

        assertThat(response.status()).isEqualTo(ModelTestStatus.FAILED);
        assertThat(response.errorCode()).isEqualTo("MODEL_TEST_FAILED");
    }

    private ModelConfigurationApplicationService service(CurrentUserContext context,
                                                         KbModelConnectionDomainService connectionService,
                                                         KbModelProfileDomainService profileService,
                                                         ModelClientFactory factory) {
        String key = Base64.getEncoder().encodeToString(new byte[32]);
        ModelBusinessKeyGenerator keyGenerator = () -> "01JMODELKEY000000000000000";
        ModelParameterCodec parameterCodec = new ModelParameterCodec(new ObjectMapper());
        UserModelResolver userModelResolver = new UserModelResolver(connectionService, profileService,
                new ModelEndpointPolicy(), parameterCodec);
        return new ModelConfigurationApplicationService(context, connectionService, profileService,
                new AesGcmCredentialCipher(key, "v1", "rag2okf"), keyGenerator, new ModelEndpointPolicy(),
                parameterCodec, userModelResolver, factory);
    }

    private KbUserEntity user(Long id) {
        KbUserEntity user = new KbUserEntity();
        user.setId(id);
        return user;
    }

    private KbModelConnectionEntity connection(Long ownerUserId) {
        String key = Base64.getEncoder().encodeToString(new byte[32]);
        var credential = new AesGcmCredentialCipher(key, "v1", "rag2okf").encrypt("secret-key-value");
        KbModelConnectionEntity connection = new KbModelConnectionEntity();
        connection.setId(9L);
        connection.setConnectionKey("01JCONNECTION00000000000000");
        connection.setOwnerUserId(ownerUserId);
        connection.setBaseUrl("https://8.8.8.8/v1");
        connection.setStatus(ModelConnectionStatus.ACTIVE);
        connection.setApiKeyCiphertext(credential.ciphertext());
        connection.setApiKeyNonce(credential.nonce());
        connection.setKeyVersion(credential.keyVersion());
        return connection;
    }

    private KbModelProfileEntity profile(Long connectionId) {
        KbModelProfileEntity profile = new KbModelProfileEntity();
        profile.setProfileKey("01JPROFILEKEY000000000000000");
        profile.setOwnerUserId(1L);
        profile.setConnectionId(connectionId);
        profile.setModelType(ModelType.CHAT);
        profile.setModelName("chat-model");
        profile.setParametersJson("{\"timeoutSeconds\":30,\"temperature\":0.2}");
        profile.setStatus(ModelProfileStatus.ACTIVE);
        return profile;
    }
}
