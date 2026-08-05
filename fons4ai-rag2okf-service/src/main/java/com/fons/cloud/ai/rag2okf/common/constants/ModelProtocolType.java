package com.fons.cloud.ai.rag2okf.common.constants;

import com.baomidou.mybatisplus.annotation.EnumValue;

/**
 * 用户模型连接使用的受支持协议。
 *
 * @author hongqy
 */
public enum ModelProtocolType {
    /** OpenAI-compatible HTTP API。 */
    OPENAI_COMPATIBLE("OPENAI_COMPATIBLE");

    @EnumValue
    private final String value;

    ModelProtocolType(String value) {
        this.value = value;
    }

    /** @return 数据库存储代码 */
    public String getValue() {
        return value;
    }
}
