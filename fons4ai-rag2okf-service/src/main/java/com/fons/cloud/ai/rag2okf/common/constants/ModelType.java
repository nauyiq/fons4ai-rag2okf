package com.fons.cloud.ai.rag2okf.common.constants;

import com.baomidou.mybatisplus.annotation.EnumValue;

/**
 * 模型档案提供的调用能力类型。
 *
 * @author hongqy
 */
public enum ModelType {
    /** 对话生成能力。 */
    CHAT("CHAT"),
    /** 文本向量化能力。 */
    EMBEDDING("EMBEDDING");

    @EnumValue
    private final String value;

    ModelType(String value) {
        this.value = value;
    }

    /**
     * 获取数据库持久化代码。
     *
     * @return 模型类型代码
     */
    public String getValue() {
        return value;
    }
}
