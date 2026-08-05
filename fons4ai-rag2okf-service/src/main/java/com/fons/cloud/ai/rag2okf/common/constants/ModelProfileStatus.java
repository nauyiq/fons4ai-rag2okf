package com.fons.cloud.ai.rag2okf.common.constants;

import com.baomidou.mybatisplus.annotation.EnumValue;

/**
 * 模型档案的启用状态。
 *
 * @author hongqy
 */
public enum ModelProfileStatus {
    /** 档案可被绑定和调用。 */
    ACTIVE("ACTIVE"),
    /** 档案停用，禁止用于后续调用。 */
    DISABLED("DISABLED");

    @EnumValue
    private final String value;

    ModelProfileStatus(String value) {
        this.value = value;
    }

    /** @return 数据库存储代码 */
    public String getValue() {
        return value;
    }
}
