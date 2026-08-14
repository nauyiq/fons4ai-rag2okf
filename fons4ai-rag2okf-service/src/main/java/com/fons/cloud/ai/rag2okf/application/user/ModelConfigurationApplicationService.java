package com.fons.cloud.ai.rag2okf.application.user;

import com.fons.cloud.ai.rag2okf.common.constants.user.ModelConnectionStatus;
import com.fons.cloud.ai.rag2okf.common.constants.user.ModelProtocolType;
import com.fons.cloud.ai.rag2okf.common.constants.user.ModelProviderTemplate;
import com.fons.cloud.ai.rag2okf.common.constants.Rag2OkfResultCode;
import com.fons.cloud.ai.rag2okf.common.constants.user.ModelTestStatus;
import com.fons.cloud.ai.rag2okf.common.constants.user.ModelType;
import com.fons.cloud.ai.rag2okf.common.model.user.EncryptedCredential;
import com.fons.cloud.ai.rag2okf.common.model.user.ResolvedModelDescriptor;
import com.fons.cloud.ai.rag2okf.common.model.user.ResolvedUserModel;
import com.fons.cloud.ai.rag2okf.common.exception.user.ModelAccessDeniedException;
import com.fons.cloud.ai.rag2okf.common.exception.user.ModelConfigurationException;
import com.fons.cloud.ai.rag2okf.common.request.user.CreateModelConnectionRequest;
import com.fons.cloud.ai.rag2okf.common.request.user.CreateModelProfileRequest;
import com.fons.cloud.ai.rag2okf.common.request.user.UpdateModelConnectionRequest;
import com.fons.cloud.ai.rag2okf.common.request.user.UpdateModelProfileRequest;
import com.fons.cloud.ai.rag2okf.common.response.user.ModelConnectionResponse;
import com.fons.cloud.ai.rag2okf.common.response.user.ModelProfileResponse;
import com.fons.cloud.ai.rag2okf.common.response.user.ModelProviderTemplateResponse;
import com.fons.cloud.ai.rag2okf.common.response.user.ModelTestResponse;
import com.fons.cloud.ai.rag2okf.common.utils.BusinessKeyGenerator;
import com.fons.cloud.ai.rag2okf.common.utils.ModelEndpointValidator;
import com.fons.cloud.ai.rag2okf.domain.entity.user.KbModelConnection;
import com.fons.cloud.ai.rag2okf.domain.entity.user.KbModelProfile;
import com.fons.cloud.ai.rag2okf.domain.entity.user.KbUser;
import com.fons.cloud.ai.rag2okf.domain.service.user.KbModelConnectionDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.user.KbModelProfileDomainService;
import com.fons.cloud.ai.rag2okf.infrastructure.adapter.user.AesGcmCredentialCipher;
import com.fons.cloud.ai.rag2okf.infrastructure.adapter.user.SaTokenCurrentUserContext;
import com.fons.cloud.ai.rag2okf.infrastructure.client.user.LangChain4jModelClientFactory;
import com.fons.cloud.ai.rag2okf.infrastructure.support.user.ModelParameterCodec;
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

    private final SaTokenCurrentUserContext currentUserContext;
    private final KbModelConnectionDomainService connectionDomainService;
    private final KbModelProfileDomainService profileDomainService;
    private final AesGcmCredentialCipher credentialCipher;
    private final ModelParameterCodec parameterCodec;
    private final UserModelResolver userModelResolver;
    private final LangChain4jModelClientFactory modelClientFactory;

    /**
     * 列出 P0 Provider 模板。
     *
     * @return 不含凭证的模板信息
     */
    public List<ModelProviderTemplateResponse> listTemplates() {
        return Arrays.stream(ModelProviderTemplate.values())
                .map(template -> new ModelProviderTemplateResponse(
                        template, template.getProviderName(), template.getDefaultBaseUrl(), template.getOfficialUrl()
                ))
                .toList();
    }

    /**
     * 查询当前用户的 Provider 连接。
     *
     * @return 当前用户拥有的连接列表
     */
    public List<ModelConnectionResponse> listConnections() {
        KbUser user = currentUserContext.requireCurrentUser();
        return connectionDomainService.listByOwnerUserId(user.getId())
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
        KbUser user = currentUserContext.requireCurrentUser();
        // providerCode 匹配已知模板时复用模板信息，否则按自定义代码保存。
        String providerCode = request.providerCode().trim();
        ModelEndpointValidator.validate(request.baseUrl());
        EncryptedCredential credential = credentialCipher.encrypt(request.apiKey());
        KbModelConnection connection = KbModelConnection.create(
                BusinessKeyGenerator.nextKey(), user.getId(), providerCode,
                request.providerName().trim(), request.displayName().trim(),
                ModelProtocolType.OPENAI_COMPATIBLE, request.baseUrl().trim(), credential, mask(request.apiKey()));
        connectionDomainService.save(connection);
        return toConnectionResponse(connection);
    }

    /**
     * 更新当前用户的 Provider 连接；API Key 必须通过独立子路径替换。
     *
     * @param connectionKey 连接业务标识
     * @param request 更新请求
     * @return 不含凭证的连接响应
     */
    @Transactional(rollbackFor = Exception.class)
    public ModelConnectionResponse updateConnection(String connectionKey, UpdateModelConnectionRequest request) {
        KbUser user = currentUserContext.requireCurrentUser();
        KbModelConnection connection = requireOwnedConnection(connectionKey, user.getId());
        String providerName = request.providerName() == null ? null : request.providerName().trim();
        String displayName = request.displayName() == null ? null : request.displayName().trim();
        String baseUrl = null;
        if (request.baseUrl() != null) {
            ModelEndpointValidator.validate(request.baseUrl());
            baseUrl = request.baseUrl().trim();
        }
        connection.updateConfiguration(providerName, displayName, baseUrl, request.status());
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
        KbUser user = currentUserContext.requireCurrentUser();
        KbModelConnection connection = requireOwnedConnection(connectionKey, user.getId());
        EncryptedCredential credential = credentialCipher.encrypt(apiKey);
        connection.replaceCredential(credential, mask(apiKey));
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
        KbUser user = currentUserContext.requireCurrentUser();
        KbModelConnection connection = requireOwnedConnection(connectionKey, user.getId());
        // 先软删除该连接下的所有档案
        profileDomainService.removeByConnectionIdAndOwnerUserId(connection.getId(), user.getId());
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
        KbUser user = currentUserContext.requireCurrentUser();
        List<KbModelProfile> profiles;
        if (connectionKey != null && !connectionKey.isBlank()) {
            // connectionKey 非空时按所属连接过滤
            KbModelConnection connection = requireOwnedConnection(connectionKey, user.getId());
            profiles = profileDomainService.listByOwnerUserIdAndConnectionId(user.getId(), connection.getId());
        } else {
            profiles = profileDomainService.listByOwnerUserId(user.getId());
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
        KbUser user = currentUserContext.requireCurrentUser();
        KbModelConnection connection = requireOwnedConnection(request.connectionKey(), user.getId());
        if (connection.getStatus() != ModelConnectionStatus.ACTIVE) {
            throw new ModelConfigurationException();
        }
        validateProfileSemantics(request.modelType(), request.dimensions(), request.temperature());
        KbModelProfile profile = KbModelProfile.create(
                BusinessKeyGenerator.nextKey(), user.getId(), connection.getId(), request.modelType(),
                request.modelName().trim(), request.dimensions(), request.contextWindowLength(),
                parameterCodec.encode(request.timeoutSeconds(), request.temperature(), request.contextWindowLength()));
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
        KbUser user = currentUserContext.requireCurrentUser();
        KbModelProfile profile = requireOwnedProfile(profileKey, user.getId());
        KbModelConnection connection = requireOwnedConnectionById(profile.getConnectionId(), user.getId());
        Integer dimensions = request.dimensions() == null ? profile.getDimensions() : request.dimensions();
        ModelParameterCodec.ModelParameters currentParameters = parameterCodec.decode(profile.getParametersJson());
        Integer contextWindowLength = request.contextWindowLength() == null ? profile.getContextWindowLength() : request.contextWindowLength();
        Integer timeout = request.timeoutSeconds() == null ? currentParameters.timeoutSeconds() : request.timeoutSeconds();
        Double temperature = request.temperature() == null ? currentParameters.temperature() : request.temperature();
        validateProfileSemantics(profile.getModelType(), dimensions, temperature);
        String modelName = request.modelName() == null ? null : request.modelName().trim();
        profile.updateConfiguration(modelName, dimensions, contextWindowLength,
                parameterCodec.encode(timeout, temperature, contextWindowLength), request.status());
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
        KbUser user = currentUserContext.requireCurrentUser();
        KbModelProfile profile = requireOwnedProfile(profileKey, user.getId());
        profileDomainService.removeById(profile.getId());
    }

    /**
     * 使用当前用户的档案执行最小模型调用，并只保存安全化结论。
     *
     * @param profileKey 档案业务标识
     * @return 安全化测试结果
     */
    public ModelTestResponse testProfile(String profileKey) {
        KbUser user = currentUserContext.requireCurrentUser();
        ResolvedUserModel resolvedModel = userModelResolver.resolveOwnedActiveProfile(profileKey, user.getId());
        KbModelProfile profile = resolvedModel.profile();
        KbModelConnection connection = resolvedModel.connection();
        try {
            String apiKey = credentialCipher.decrypt(new EncryptedCredential(
                    connection.getApiKeyCiphertext(), connection.getApiKeyNonce(), connection.getKeyVersion()
            ));
            Integer dimensions = invokeMinimalProbe(resolvedModel.descriptor(), apiKey);
            updateTestResult(profile, connection, ModelTestStatus.SUCCEEDED, null);
            return new ModelTestResponse(ModelTestStatus.SUCCEEDED, null, dimensions);
        } catch (RuntimeException exception) {
            String errorCode = Rag2OkfResultCode.MODEL_TEST_FAILED.getCode();
            updateTestResult(profile, connection, ModelTestStatus.FAILED, errorCode);
            return new ModelTestResponse(ModelTestStatus.FAILED, errorCode, null);
        }
    }

    private Integer invokeMinimalProbe(ResolvedModelDescriptor descriptor, String apiKey) {
        // 读取时兼容旧值 CHAT：归一为 LLM 走对话探活。
        if (descriptor.modelType().matches(ModelType.LLM)) {
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

    private void updateTestResult(KbModelProfile profile, KbModelConnection connection,
                                  ModelTestStatus status, String errorCode) {
        Date now = new Date();
        profile.recordTestResult(status, now, errorCode);
        connection.recordTestResult(status, now, errorCode);
        profileDomainService.updateById(profile);
        connectionDomainService.updateById(connection);
    }

    private KbModelConnection requireOwnedConnection(String connectionKey, Long userId) {
        KbModelConnection connection = connectionDomainService
                .findByConnectionKeyAndOwnerUserId(connectionKey, userId);
        if (connection == null) {
            throw new ModelAccessDeniedException();
        }
        return connection;
    }

    private KbModelConnection requireOwnedConnectionById(Long connectionId, Long userId) {
        KbModelConnection connection = connectionDomainService.findByIdAndOwnerUserId(connectionId, userId);
        if (connection == null) {
            throw new ModelAccessDeniedException();
        }
        return connection;
    }

    private KbModelProfile requireOwnedProfile(String profileKey, Long userId) {
        KbModelProfile profile = profileDomainService.findByProfileKeyAndOwnerUserId(profileKey, userId);
        if (profile == null) {
            throw new ModelAccessDeniedException();
        }
        return profile;
    }

    /**
     * 校验模型类型与类型专属参数之间的业务语义。
     *
     * @param modelType 模型能力类型
     * @param dimensions 向量维度，仅 EMBEDDING 使用
     * @param temperature 对话温度，仅 LLM 使用
     */
    private void validateProfileSemantics(ModelType modelType, Integer dimensions, Double temperature) {
        // 仅允许 7 白名单内的模型类型写入，旧值 CHAT 已废弃。
        if (!modelType.isWritable()) {
            throw new ModelConfigurationException();
        }
        if (modelType.matches(ModelType.LLM) && dimensions != null
                || modelType.matches(ModelType.EMBEDDING) && temperature != null) {
            throw new ModelConfigurationException();
        }
    }

    private String mask(String apiKey) {
        if (apiKey == null || apiKey.length() < 8) {
            return "********";
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }

    private ModelConnectionResponse toConnectionResponse(KbModelConnection connection) {
        boolean apiKeyConfigured = connection.getApiKeyMask() != null && !connection.getApiKeyMask().isBlank();
        return new ModelConnectionResponse(connection.getConnectionKey(), connection.getProviderCode(),
                connection.getProviderName(), connection.getDisplayName(), connection.getProtocolType(),
                connection.getBaseUrl(), connection.getApiKeyMask(), connection.getStatus(),
                connection.getLastTestStatus(), connection.getLastTestAt(), apiKeyConfigured, connection.getUpdated());
    }

    private ModelProfileResponse toProfileResponse(KbModelProfile profile, KbModelConnection connection) {
        ModelParameterCodec.ModelParameters parameters = parameterCodec.decode(profile.getParametersJson());
        // 读取别名兼容：旧数据 model_type=CHAT 读取时归一为 LLM 返回。
        ModelType responseType = profile.getModelType() == null ? null : profile.getModelType().canonical();
        return new ModelProfileResponse(profile.getProfileKey(), connection.getConnectionKey(), responseType,
                profile.getModelName(), profile.getDimensions(), parameters.timeoutSeconds(), parameters.temperature(),
                profile.getStatus(), profile.getLastTestStatus(), profile.getLastTestAt(),
                profile.getContextWindowLength(), profile.getUpdated());
    }

}
