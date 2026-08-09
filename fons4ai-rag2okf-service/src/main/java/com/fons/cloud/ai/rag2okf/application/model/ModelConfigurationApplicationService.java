package com.fons.cloud.ai.rag2okf.application.model;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fons.cloud.ai.rag2okf.common.constants.ModelConnectionStatus;
import com.fons.cloud.ai.rag2okf.common.constants.ModelProfileStatus;
import com.fons.cloud.ai.rag2okf.common.constants.ModelProtocolType;
import com.fons.cloud.ai.rag2okf.common.constants.ModelProviderTemplate;
import com.fons.cloud.ai.rag2okf.common.constants.ModelTestStatus;
import com.fons.cloud.ai.rag2okf.common.constants.ModelType;
import com.fons.cloud.ai.rag2okf.common.dto.CredentialCipher;
import com.fons.cloud.ai.rag2okf.common.dto.CurrentUserContext;
import com.fons.cloud.ai.rag2okf.common.dto.EncryptedCredential;
import com.fons.cloud.ai.rag2okf.common.dto.ModelBusinessKeyGenerator;
import com.fons.cloud.ai.rag2okf.common.dto.ModelClientFactory;
import com.fons.cloud.ai.rag2okf.common.dto.ModelParameterCodec;
import com.fons.cloud.ai.rag2okf.common.dto.ResolvedModelDescriptor;
import com.fons.cloud.ai.rag2okf.common.dto.ResolvedUserModel;
import com.fons.cloud.ai.rag2okf.common.dto.UserModelResolver;
import com.fons.cloud.ai.rag2okf.common.exeception.ModelAccessDeniedException;
import com.fons.cloud.ai.rag2okf.common.exeception.ModelConfigurationException;
import com.fons.cloud.ai.rag2okf.common.request.CreateModelConnectionRequest;
import com.fons.cloud.ai.rag2okf.common.request.CreateModelProfileRequest;
import com.fons.cloud.ai.rag2okf.common.request.UpdateModelConnectionRequest;
import com.fons.cloud.ai.rag2okf.common.request.UpdateModelProfileRequest;
import com.fons.cloud.ai.rag2okf.common.response.ModelConnectionResponse;
import com.fons.cloud.ai.rag2okf.common.response.ModelProfileResponse;
import com.fons.cloud.ai.rag2okf.common.response.ModelProviderTemplateResponse;
import com.fons.cloud.ai.rag2okf.common.response.ModelTestResponse;
import com.fons.cloud.ai.rag2okf.domain.entity.KbModelConnectionEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbModelProfileEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbUserEntity;
import com.fons.cloud.ai.rag2okf.domain.service.KbModelConnectionDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.KbModelProfileDomainService;
import com.fons.cloud.ai.rag2okf.infrastructure.model.ModelEndpointPolicy;
import dev.langchain4j.data.embedding.Embedding;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * 用户 Provider 连接、模型档案和最小能力测试的应用服务。
 *
 * <p>该服务只编排当前用户资源；不提供全局模型或 API Key 回退。</p>
 *
 * @author hongqy
 */
@Service
@RequiredArgsConstructor
public class ModelConfigurationApplicationService {

    private static final String TEST_FAILURE_CODE = "MODEL_TEST_FAILED";

    private final CurrentUserContext currentUserContext;
    private final KbModelConnectionDomainService connectionDomainService;
    private final KbModelProfileDomainService profileDomainService;
    private final CredentialCipher credentialCipher;
    private final ModelBusinessKeyGenerator keyGenerator;
    private final ModelEndpointPolicy endpointPolicy;
    private final ModelParameterCodec parameterCodec;
    private final UserModelResolver userModelResolver;
    private final ModelClientFactory modelClientFactory;

    /**
     * 列出 P0 Provider 模板。
     *
     * @return 不含凭证的模板信息
     */
    public List<ModelProviderTemplateResponse> listTemplates() {
        return Arrays.stream(ModelProviderTemplate.values())
                .map(template -> new ModelProviderTemplateResponse(
                        template, template.getProviderName(), template.getDefaultBaseUrl()
                ))
                .toList();
    }

    /**
     * 查询当前用户的 Provider 连接。
     *
     * @return 当前用户拥有的连接列表
     */
    public List<ModelConnectionResponse> listConnections() {
        KbUserEntity user = currentUserContext.requireCurrentUser();
        return connectionDomainService.list(Wrappers.<KbModelConnectionEntity>lambdaQuery()
                        .eq(KbModelConnectionEntity::getOwnerUserId, user.getId())
                        .orderByDesc(KbModelConnectionEntity::getUpdated))
                .stream()
                .map(this::toConnectionResponse)
                .toList();
    }

    /**
     * 创建当前用户的 Provider 连接并加密保存 API Key。
     *
     * @param request 创建请求
     * @return 不含凭证的连接响应
     */
    @Transactional(rollbackFor = Exception.class)
    public ModelConnectionResponse createConnection(CreateModelConnectionRequest request) {
        KbUserEntity user = currentUserContext.requireCurrentUser();
        // providerCode 为字符串：匹配已知模板时复用模板信息，否则按自定义处理使用传入的 providerName 和 baseUrl
        String providerCode = validateProviderCode(request.providerCode());
        validateConnectionRequest(request.providerName(), request.displayName(), request.baseUrl());
        endpointPolicy.validate(request.baseUrl());
        EncryptedCredential credential = credentialCipher.encrypt(request.apiKey());
        KbModelConnectionEntity connection = new KbModelConnectionEntity();
        connection.setConnectionKey(keyGenerator.nextKey());
        connection.setOwnerUserId(user.getId());
        connection.setProviderCode(providerCode);
        connection.setProviderName(requiredText(request.providerName(), 80));
        connection.setDisplayName(requiredText(request.displayName(), 80));
        connection.setProtocolType(ModelProtocolType.OPENAI_COMPATIBLE);
        connection.setBaseUrl(request.baseUrl().trim());
        connection.setApiKeyCiphertext(credential.ciphertext());
        connection.setApiKeyNonce(credential.nonce());
        connection.setKeyVersion(credential.keyVersion());
        connection.setApiKeyMask(mask(request.apiKey()));
        connection.setStatus(ModelConnectionStatus.ACTIVE);
        connectionDomainService.save(connection);
        return toConnectionResponse(connection);
    }

    /**
     * 更新当前用户的 Provider 连接；只在显式提交时替换 API Key。
     *
     * @param connectionKey 连接业务标识
     * @param request 更新请求
     * @return 不含凭证的连接响应
     */
    @Transactional(rollbackFor = Exception.class)
    public ModelConnectionResponse updateConnection(String connectionKey, UpdateModelConnectionRequest request) {
        KbUserEntity user = currentUserContext.requireCurrentUser();
        KbModelConnectionEntity connection = requireOwnedConnection(connectionKey, user.getId());
        if (request.providerName() != null) {
            connection.setProviderName(requiredText(request.providerName(), 80));
        }
        if (request.displayName() != null) {
            connection.setDisplayName(requiredText(request.displayName(), 80));
        }
        if (request.baseUrl() != null) {
            endpointPolicy.validate(request.baseUrl());
            connection.setBaseUrl(request.baseUrl().trim());
        }
        if (request.status() != null) {
            connection.setStatus(request.status());
        }
        if (request.apiKey() != null && !request.apiKey().isBlank()) {
            EncryptedCredential credential = credentialCipher.encrypt(request.apiKey());
            connection.setApiKeyCiphertext(credential.ciphertext());
            connection.setApiKeyNonce(credential.nonce());
            connection.setKeyVersion(credential.keyVersion());
            connection.setApiKeyMask(mask(request.apiKey()));
        }
        connectionDomainService.updateById(connection);
        return toConnectionResponse(connection);
    }

    /**
     * 独立替换当前用户 Provider 连接的 API Key。
     *
     * <p>复用与创建相同的加密逻辑，更新密文、nonce、keyVersion 和掩码，不触碰其他字段。</p>
     *
     * @param connectionKey 连接业务标识
     * @param apiKey 新的 API Key 明文
     * @return 更新后的连接响应
     */
    @Transactional(rollbackFor = Exception.class)
    public ModelConnectionResponse replaceApiKey(String connectionKey, String apiKey) {
        KbUserEntity user = currentUserContext.requireCurrentUser();
        KbModelConnectionEntity connection = requireOwnedConnection(connectionKey, user.getId());
        if (apiKey == null || apiKey.isBlank()) {
            throw new ModelConfigurationException();
        }
        EncryptedCredential credential = credentialCipher.encrypt(apiKey);
        connection.setApiKeyCiphertext(credential.ciphertext());
        connection.setApiKeyNonce(credential.nonce());
        connection.setKeyVersion(credential.keyVersion());
        connection.setApiKeyMask(mask(apiKey));
        connectionDomainService.updateById(connection);
        return toConnectionResponse(connection);
    }

    /**
     * 软删除当前用户的 Provider 连接及其下所有档案。
     *
     * <p>校验连接存在且属于当前用户；删除仅置 deleted=1，先删档案再删连接。</p>
     *
     * @param connectionKey 连接业务标识
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteConnection(String connectionKey) {
        KbUserEntity user = currentUserContext.requireCurrentUser();
        KbModelConnectionEntity connection = requireOwnedConnection(connectionKey, user.getId());
        // 先软删除该连接下的所有档案
        profileDomainService.remove(Wrappers.<KbModelProfileEntity>lambdaQuery()
                .eq(KbModelProfileEntity::getConnectionId, connection.getId()));
        // 再软删除连接
        connectionDomainService.removeById(connection.getId());
    }

    /**
     * 查询当前用户的模型档案。
     *
     * @param connectionKey 可选的连接业务标识，非空时只返回该连接下的档案
     * @return 当前用户拥有的档案列表
     */
    public List<ModelProfileResponse> listProfiles(String connectionKey) {
        KbUserEntity user = currentUserContext.requireCurrentUser();
        List<KbModelProfileEntity> profiles;
        if (connectionKey != null && !connectionKey.isBlank()) {
            // connectionKey 非空时按所属连接过滤
            KbModelConnectionEntity connection = requireOwnedConnection(connectionKey, user.getId());
            profiles = profileDomainService.list(Wrappers.<KbModelProfileEntity>lambdaQuery()
                    .eq(KbModelProfileEntity::getOwnerUserId, user.getId())
                    .eq(KbModelProfileEntity::getConnectionId, connection.getId())
                    .orderByDesc(KbModelProfileEntity::getUpdated));
        } else {
            profiles = profileDomainService.list(Wrappers.<KbModelProfileEntity>lambdaQuery()
                    .eq(KbModelProfileEntity::getOwnerUserId, user.getId())
                    .orderByDesc(KbModelProfileEntity::getUpdated));
        }
        return profiles.stream()
                .map(profile -> toProfileResponse(profile, requireOwnedConnectionById(profile.getConnectionId(), user.getId())))
                .toList();
    }

    /**
     * 创建当前用户的模型档案。
     *
     * @param request 创建请求
     * @return 不含凭证的档案响应
     */
    @Transactional(rollbackFor = Exception.class)
    public ModelProfileResponse createProfile(CreateModelProfileRequest request) {
        KbUserEntity user = currentUserContext.requireCurrentUser();
        KbModelConnectionEntity connection = requireOwnedConnection(request.connectionKey(), user.getId());
        if (connection.getStatus() != ModelConnectionStatus.ACTIVE || request.modelType() == null) {
            throw new ModelConfigurationException();
        }
        validateProfileInput(request.modelType(), request.modelName(), request.dimensions(),
                request.contextWindowLength(), request.timeoutSeconds(), request.temperature());
        KbModelProfileEntity profile = new KbModelProfileEntity();
        profile.setProfileKey(keyGenerator.nextKey());
        profile.setOwnerUserId(user.getId());
        profile.setConnectionId(connection.getId());
        profile.setModelType(request.modelType());
        profile.setModelName(requiredText(request.modelName(), 160));
        profile.setDimensions(request.dimensions());
        profile.setContextWindowLength(request.contextWindowLength());
        profile.setParametersJson(parameterCodec.encode(request.timeoutSeconds(), request.temperature(), request.contextWindowLength()));
        profile.setStatus(ModelProfileStatus.ACTIVE);
        profileDomainService.save(profile);
        return toProfileResponse(profile, connection);
    }

    /**
     * 更新当前用户的模型档案。
     *
     * @param profileKey 档案业务标识
     * @param request 更新请求
     * @return 不含凭证的档案响应
     */
    @Transactional(rollbackFor = Exception.class)
    public ModelProfileResponse updateProfile(String profileKey, UpdateModelProfileRequest request) {
        KbUserEntity user = currentUserContext.requireCurrentUser();
        KbModelProfileEntity profile = requireOwnedProfile(profileKey, user.getId());
        KbModelConnectionEntity connection = requireOwnedConnectionById(profile.getConnectionId(), user.getId());
        Integer dimensions = request.dimensions() == null ? profile.getDimensions() : request.dimensions();
        ModelParameterCodec.ModelParameters currentParameters = parameterCodec.decode(profile.getParametersJson());
        Integer contextWindowLength = request.contextWindowLength() == null ? profile.getContextWindowLength() : request.contextWindowLength();
        Integer timeout = request.timeoutSeconds() == null ? currentParameters.timeoutSeconds() : request.timeoutSeconds();
        Double temperature = request.temperature() == null ? currentParameters.temperature() : request.temperature();
        validateProfileInput(profile.getModelType(), request.modelName() == null ? profile.getModelName() : request.modelName(),
                dimensions, contextWindowLength, timeout, temperature);
        if (request.modelName() != null) {
            profile.setModelName(requiredText(request.modelName(), 160));
        }
        profile.setDimensions(dimensions);
        profile.setContextWindowLength(contextWindowLength);
        profile.setParametersJson(parameterCodec.encode(timeout, temperature, contextWindowLength));
        if (request.status() != null) {
            profile.setStatus(request.status());
        }
        profileDomainService.updateById(profile);
        return toProfileResponse(profile, connection);
    }

    /**
     * 软删除当前用户的模型档案。
     *
     * <p>校验档案存在且属于当前用户；删除仅置 deleted=1。</p>
     *
     * @param profileKey 档案业务标识
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteProfile(String profileKey) {
        KbUserEntity user = currentUserContext.requireCurrentUser();
        KbModelProfileEntity profile = requireOwnedProfile(profileKey, user.getId());
        profileDomainService.removeById(profile.getId());
    }

    /**
     * 使用当前用户的档案执行最小模型调用，并只保存安全化结论。
     *
     * @param profileKey 档案业务标识
     * @return 安全化测试结果
     */
    public ModelTestResponse testProfile(String profileKey) {
        KbUserEntity user = currentUserContext.requireCurrentUser();
        ResolvedUserModel resolvedModel = userModelResolver.resolveOwnedActiveProfile(profileKey, user.getId());
        KbModelProfileEntity profile = resolvedModel.profile();
        KbModelConnectionEntity connection = resolvedModel.connection();
        try {
            String apiKey = credentialCipher.decrypt(new EncryptedCredential(
                    connection.getApiKeyCiphertext(), connection.getApiKeyNonce(), connection.getKeyVersion()
            ));
            Integer dimensions = invokeMinimalProbe(resolvedModel.descriptor(), apiKey);
            updateTestResult(profile, connection, ModelTestStatus.SUCCEEDED, null);
            return new ModelTestResponse(ModelTestStatus.SUCCEEDED, null, dimensions);
        } catch (RuntimeException exception) {
            updateTestResult(profile, connection, ModelTestStatus.FAILED, TEST_FAILURE_CODE);
            return new ModelTestResponse(ModelTestStatus.FAILED, TEST_FAILURE_CODE, null);
        }
    }

    private Integer invokeMinimalProbe(ResolvedModelDescriptor descriptor, String apiKey) {
        // 读取时兼容旧值 CHAT：归一为 LLM 走对话探活。
        String type = ModelType.normalize(descriptor.modelType().getValue());
        if ("LLM".equals(type)) {
            modelClientFactory.createChatModel(descriptor, apiKey).chat("health check");
            return null;
        }
        Embedding embedding = modelClientFactory.createEmbeddingModel(descriptor, apiKey).embed("health check").content();
        int actualDimensions = embedding.vector().length;
        if (descriptor.dimensions() != null && descriptor.dimensions() != actualDimensions) {
            throw new ModelConfigurationException();
        }
        return actualDimensions;
    }

    private void updateTestResult(KbModelProfileEntity profile, KbModelConnectionEntity connection,
                                  ModelTestStatus status, String errorCode) {
        Date now = new Date();
        profile.setLastTestStatus(status);
        profile.setLastTestAt(now);
        profile.setLastTestErrorCode(errorCode);
        connection.setLastTestStatus(status);
        connection.setLastTestAt(now);
        connection.setLastTestErrorCode(errorCode);
        profileDomainService.updateById(profile);
        connectionDomainService.updateById(connection);
    }

    private KbModelConnectionEntity requireOwnedConnection(String connectionKey, Long userId) {
        KbModelConnectionEntity connection = connectionDomainService.getOne(Wrappers.<KbModelConnectionEntity>lambdaQuery()
                .eq(KbModelConnectionEntity::getConnectionKey, connectionKey));
        if (connection == null || !userId.equals(connection.getOwnerUserId())) {
            throw new ModelAccessDeniedException();
        }
        return connection;
    }

    private KbModelConnectionEntity requireOwnedConnectionById(Long connectionId, Long userId) {
        KbModelConnectionEntity connection = connectionDomainService.getById(connectionId);
        if (connection == null || !userId.equals(connection.getOwnerUserId())) {
            throw new ModelAccessDeniedException();
        }
        return connection;
    }

    private KbModelProfileEntity requireOwnedProfile(String profileKey, Long userId) {
        KbModelProfileEntity profile = profileDomainService.getOne(Wrappers.<KbModelProfileEntity>lambdaQuery()
                .eq(KbModelProfileEntity::getProfileKey, profileKey));
        if (profile == null || !userId.equals(profile.getOwnerUserId())) {
            throw new ModelAccessDeniedException();
        }
        return profile;
    }

    private void validateConnectionRequest(String providerName, String displayName, String baseUrl) {
        requiredText(providerName, 80);
        requiredText(displayName, 80);
        requiredText(baseUrl, 512);
    }

    /**
     * 校验 providerCode：非空且长度不超过数据库列长度，匹配已知模板或自定义代码。
     *
     * @param providerCode 前端传入的厂商代码
     * @return 规整后的 providerCode
     */
    private String validateProviderCode(String providerCode) {
        return requiredText(providerCode, 40);
    }

    private void validateProfileInput(ModelType modelType, String modelName, Integer dimensions,
                                      Integer contextWindowLength, Integer timeoutSeconds, Double temperature) {
        requiredText(modelName, 160);
        parameterCodec.encode(timeoutSeconds, temperature, contextWindowLength);
        // 仅允许 7 白名单内的模型类型写入，旧值 CHAT 已废弃。
        if (modelType == null || !ModelType.isValid(modelType.getValue())) {
            throw new ModelConfigurationException();
        }
        String type = ModelType.normalize(modelType.getValue());
        if ("LLM".equals(type) && dimensions != null
                || "EMBEDDING".equals(type) && temperature != null
                || "EMBEDDING".equals(type) && dimensions != null && dimensions < 1) {
            throw new ModelConfigurationException();
        }
    }

    private String requiredText(String value, int maxLength) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw new ModelConfigurationException();
        }
        return value.trim();
    }

    private String mask(String apiKey) {
        if (apiKey == null || apiKey.length() < 8) {
            return "********";
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }

    private ModelConnectionResponse toConnectionResponse(KbModelConnectionEntity connection) {
        boolean apiKeyConfigured = connection.getApiKeyMask() != null && !connection.getApiKeyMask().isBlank();
        return new ModelConnectionResponse(connection.getConnectionKey(), connection.getProviderCode(),
                connection.getProviderName(), connection.getDisplayName(), connection.getProtocolType(),
                connection.getBaseUrl(), connection.getApiKeyMask(), connection.getStatus(),
                connection.getLastTestStatus(), connection.getLastTestAt(), apiKeyConfigured, connection.getUpdated());
    }

    private ModelProfileResponse toProfileResponse(KbModelProfileEntity profile, KbModelConnectionEntity connection) {
        ModelParameterCodec.ModelParameters parameters = parameterCodec.decode(profile.getParametersJson());
        // 读取别名兼容：旧数据 model_type=CHAT 读取时归一为 LLM 返回。
        ModelType responseType = normalizeResponseType(profile.getModelType());
        return new ModelProfileResponse(profile.getProfileKey(), connection.getConnectionKey(), responseType,
                profile.getModelName(), profile.getDimensions(), parameters.timeoutSeconds(), parameters.temperature(),
                profile.getStatus(), profile.getLastTestStatus(), profile.getLastTestAt(),
                profile.getContextWindowLength(), profile.getUpdated());
    }

    private ModelType normalizeResponseType(ModelType modelType) {
        if (modelType == null) {
            return null;
        }
        String normalized = ModelType.normalize(modelType.getValue());
        return "LLM".equals(normalized) ? ModelType.LLM : modelType;
    }
}
