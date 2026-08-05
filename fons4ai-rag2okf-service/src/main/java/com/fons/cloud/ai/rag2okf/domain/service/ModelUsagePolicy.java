package com.fons.cloud.ai.rag2okf.domain.service;

import com.fons.cloud.ai.rag2okf.common.constants.ModelType;
import com.fons.cloud.ai.rag2okf.common.constants.ModelUsageType;
import org.springframework.stereotype.Component;

/**
 * 知识库用途与模型能力的兼容规则。
 *
 * @author hongqy
 */
@Component
public class ModelUsagePolicy {

    /**
     * 判断模型能力能否提供指定知识库用途。
     *
     * @param usageType 知识库用途
     * @param modelType 模型能力
     * @return 是否兼容
     */
    public boolean isCompatible(ModelUsageType usageType, ModelType modelType) {
        return usageType != null && usageType.getRequiredModelType() == modelType;
    }
}
