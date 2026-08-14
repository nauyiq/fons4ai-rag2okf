package com.fons.cloud.ai.rag2okf.application.user;

import com.fons.cloud.ai.rag2okf.common.constants.user.ModelConnectionStatus;
import com.fons.cloud.ai.rag2okf.common.constants.user.ModelProfileStatus;
import com.fons.cloud.ai.rag2okf.common.exception.user.ModelAccessDeniedException;
import com.fons.cloud.ai.rag2okf.common.exception.user.ModelConfigurationException;
import com.fons.cloud.ai.rag2okf.common.model.user.ResolvedModelDescriptor;
import com.fons.cloud.ai.rag2okf.common.model.user.ResolvedUserModel;
import com.fons.cloud.ai.rag2okf.common.utils.ModelEndpointValidator;
import com.fons.cloud.ai.rag2okf.domain.entity.user.KbModelConnection;
import com.fons.cloud.ai.rag2okf.domain.entity.user.KbModelProfile;
import com.fons.cloud.ai.rag2okf.domain.service.user.KbModelConnectionDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.user.KbModelProfileDomainService;
import com.fons.cloud.ai.rag2okf.infrastructure.support.user.ModelParameterCodec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 按用户所有权和启用状态解析可调用模型，集中收敛模型使用前的安全边界。
 *
 * @author hongqy
 */
@Component
@RequiredArgsConstructor
public class UserModelResolver {

    private final KbModelConnectionDomainService connectionDomainService;
    private final KbModelProfileDomainService profileDomainService;
    private final ModelParameterCodec parameterCodec;

    /**
     * 解析指定用户拥有且已启用的模型档案；不返回明文凭证。
     *
     * @param profileKey 模型档案业务标识
     * @param ownerUserId 当前用户主键
     * @return 经所有权、状态和端点校验后的调用描述
     */
    public ResolvedUserModel resolveOwnedActiveProfile(String profileKey, Long ownerUserId) {
        KbModelProfile profile = profileDomainService
                .findByProfileKeyAndOwnerUserId(profileKey, ownerUserId);
        if (profile == null) {
            throw new ModelAccessDeniedException();
        }
        KbModelConnection connection = connectionDomainService
                .findByIdAndOwnerUserId(profile.getConnectionId(), ownerUserId);
        if (connection == null) {
            throw new ModelAccessDeniedException();
        }
        if (profile.getStatus() != ModelProfileStatus.ACTIVE || connection.getStatus() != ModelConnectionStatus.ACTIVE) {
            throw new ModelConfigurationException();
        }
        ModelEndpointValidator.validate(connection.getBaseUrl());
        ModelParameterCodec.ModelParameters parameters = parameterCodec.decode(profile.getParametersJson());
        ResolvedModelDescriptor descriptor = new ResolvedModelDescriptor(profile.getProfileKey(), profile.getModelType(),
                connection.getBaseUrl(), profile.getModelName(), profile.getDimensions(), parameters.timeoutSeconds(),
                parameters.temperature());
        return new ResolvedUserModel(profile, connection, descriptor);
    }
}
