package com.fons.cloud.ai.rag2okf.common.dto;

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
        if (usageType == null || modelType == null) {
            return false;
        }
        // 读取别名兼容：旧值 CHAT 归一为 LLM 后再比较。
        String required = ModelType.normalize(usageType.getRequiredModelType().getValue());
        String actual = ModelType.normalize(modelType.getValue());
        return required.equals(actual);
    }
}
