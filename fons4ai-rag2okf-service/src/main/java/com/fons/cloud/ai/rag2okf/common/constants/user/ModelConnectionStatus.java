package com.fons.cloud.ai.rag2okf.common.constants.user;

import com.baomidou.mybatisplus.annotation.EnumValue;

/**
 * Provider 连接的启用状态。
 *
 * @author hongqy
 */
public enum ModelConnectionStatus {
    /** 连接可用于创建档案和模型调用。 */
    ACTIVE("ACTIVE"),
    /** 连接停用，禁止用于后续调用。 */
    DISABLED("DISABLED");

    @EnumValue
    private final String value;

    ModelConnectionStatus(String value) {
        this.value = value;
    }

    /** @return 数据库存储代码 */
    public String getValue() {
        return value;
    }
}
