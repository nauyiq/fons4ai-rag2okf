package com.fons.cloud.ai.rag2okf.common.constants.knowledgebase;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fons.cloud.ai.rag2okf.common.constants.user.ModelType;
import lombok.Getter;

/**
 * 知识库模型用途。
 *
 * @author hongqy
 */
@Getter
public enum ModelUsageType {
    /** 知识库回答生成。 */
    ANSWER_GENERATION("ANSWER_GENERATION", ModelType.LLM),
    /** 知识库内容向量化。 */
    EMBEDDING("EMBEDDING", ModelType.EMBEDDING);

    /**
     * -- GETTER --
     *  获取数据库持久化代码。
     *
     */
    @EnumValue
    private final String value;
    /**
     * -- GETTER --
     *  获取该用途要求的模型能力。
     *
     * @return 必须匹配的模型类型
     */
    private final ModelType requiredModelType;

    ModelUsageType(String value, ModelType requiredModelType) {
        this.value = value;
        this.requiredModelType = requiredModelType;
    }

}
