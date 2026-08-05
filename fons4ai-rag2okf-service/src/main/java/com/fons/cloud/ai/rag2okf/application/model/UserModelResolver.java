package com.fons.cloud.ai.rag2okf.application.model;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fons.cloud.ai.rag2okf.common.constants.ModelConnectionStatus;
import com.fons.cloud.ai.rag2okf.common.constants.ModelProfileStatus;
import com.fons.cloud.ai.rag2okf.common.exeception.ModelAccessDeniedException;
import com.fons.cloud.ai.rag2okf.common.exeception.ModelConfigurationException;
import com.fons.cloud.ai.rag2okf.domain.entity.KbModelConnectionEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbModelProfileEntity;
import com.fons.cloud.ai.rag2okf.domain.service.KbModelConnectionDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.KbModelProfileDomainService;
import com.fons.cloud.ai.rag2okf.infrastructure.model.ModelEndpointPolicy;
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
    private final ModelEndpointPolicy endpointPolicy;
    private final ModelParameterCodec parameterCodec;

    /**
     * 解析指定用户拥有且已启用的模型档案；不返回明文凭证。
     *
     * @param profileKey 模型档案业务标识
     * @param ownerUserId 当前用户主键
     * @return 经所有权、状态和端点校验后的调用描述
     */
    public ResolvedUserModel resolveOwnedActiveProfile(String profileKey, Long ownerUserId) {
        KbModelProfileEntity profile = profileDomainService.getOne(Wrappers.<KbModelProfileEntity>lambdaQuery()
                .eq(KbModelProfileEntity::getProfileKey, profileKey));
        if (profile == null || !ownerUserId.equals(profile.getOwnerUserId())) {
            throw new ModelAccessDeniedException();
        }
        KbModelConnectionEntity connection = connectionDomainService.getById(profile.getConnectionId());
        if (connection == null || !ownerUserId.equals(connection.getOwnerUserId())) {
            throw new ModelAccessDeniedException();
        }
        if (profile.getStatus() != ModelProfileStatus.ACTIVE || connection.getStatus() != ModelConnectionStatus.ACTIVE) {
            throw new ModelConfigurationException();
        }
        endpointPolicy.validate(connection.getBaseUrl());
        ModelParameterCodec.ModelParameters parameters = parameterCodec.decode(profile.getParametersJson());
        ResolvedModelDescriptor descriptor = new ResolvedModelDescriptor(profile.getProfileKey(), profile.getModelType(),
                connection.getBaseUrl(), profile.getModelName(), profile.getDimensions(), parameters.timeoutSeconds(),
                parameters.temperature());
        return new ResolvedUserModel(profile, connection, descriptor);
    }
}
