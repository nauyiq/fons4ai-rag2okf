package com.fons.cloud.ai.rag2okf.domain.entity.user;

import com.fons.cloud.ai.rag2okf.common.constants.user.ModelConnectionStatus;
import com.fons.cloud.ai.rag2okf.common.constants.user.ModelProfileStatus;
import com.fons.cloud.ai.rag2okf.common.exception.user.ModelConfigurationException;
import com.fons.cloud.ai.rag2okf.common.model.user.ResolvedUserModel;
import com.fons.cloud.ai.rag2okf.common.model.user.ResolvedModelDescriptor;
import com.fons.cloud.ai.rag2okf.common.utils.ModelParameterCodec;
import com.fons.cloud.ai.rag2okf.common.utils.ModelEndpointValidator;
import lombok.*;

/**
 * 用户可调用模型聚合。
 *
 * <p>聚合由领域服务按档案和连接共同所有者查询构造；它负责校验启用状态和端点，
 * 并生成不含明文凭证的调用描述。</p>
 * @author hongqy
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserModelAggregate {

    /** 当前用户拥有的模型连接。 */
    private KbModelConnection modelConnection;
    /** 当前用户拥有的模型档案。 */
    private KbModelProfile modelProfile;

    /**
     * 获取非敏感模型调用描述。
     *
     * @return 模型调用描述
     */
    public ResolvedModelDescriptor getModelDescriptor() {
        ModelParameterCodec.ModelParameters parameters = ModelParameterCodec.decode(modelProfile.getParametersJson());
        return new ResolvedModelDescriptor(modelProfile.getProfileKey(), modelProfile.getModelType(),
                modelConnection.getBaseUrl(), modelProfile.getModelName(), modelProfile.getDimensions(), parameters.timeoutSeconds(),
                parameters.temperature());
    }

    /**
     * 校验当前聚合可调用并返回档案、连接与调用描述。
     *
     * @return 已校验的用户模型
     * @throws ModelConfigurationException 模型档案、连接或端点不可用时抛出
     */
    public ResolvedUserModel requireCallable() {
        if (modelProfile == null || modelConnection == null
                || modelProfile.getStatus() != ModelProfileStatus.ACTIVE
                || modelConnection.getStatus() != ModelConnectionStatus.ACTIVE) {
            throw new ModelConfigurationException();
        }
        ModelEndpointValidator.validate(modelConnection.getBaseUrl());
        return new ResolvedUserModel(modelProfile, modelConnection, getModelDescriptor());
    }

}
