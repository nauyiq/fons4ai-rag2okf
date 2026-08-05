package com.fons.cloud.ai.rag2okf.common.constants;

import com.baomidou.mybatisplus.annotation.EnumValue;

/**
 * 知识库模型用途。
 *
 * @author hongqy
 */
public enum ModelUsageType {
    /** 知识库回答生成。 */
    ANSWER_GENERATION("ANSWER_GENERATION", ModelType.CHAT),
    /** 知识库内容向量化。 */
    EMBEDDING("EMBEDDING", ModelType.EMBEDDING);

    @EnumValue
    private final String value;
    private final ModelType requiredModelType;

    ModelUsageType(String value, ModelType requiredModelType) {
        this.value = value;
        this.requiredModelType = requiredModelType;
    }

    /**
     * 获取数据库持久化代码。
     *
     * @return 用途代码
     */
    public String getValue() {
        return value;
    }

    /**
     * 获取该用途要求的模型能力。
     *
     * @return 必须匹配的模型类型
     */
    public ModelType getRequiredModelType() {
        return requiredModelType;
    }
}
