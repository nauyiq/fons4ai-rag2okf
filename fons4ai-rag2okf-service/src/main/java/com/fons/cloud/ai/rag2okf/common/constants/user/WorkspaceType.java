package com.fons.cloud.ai.rag2okf.common.constants.user;

import com.baomidou.mybatisplus.annotation.EnumValue;

/**
 * 工作空间归属类型，对应 kb_workspace.workspace_type 的 VARCHAR 代码值。
 *
 * @author hongqy
 */
public enum WorkspaceType {
    /** 个人用户的默认空间。 */
    PERSONAL("PERSONAL"),
    /** 企业空间，为后续企业租户能力预留。 */
    ENTERPRISE("ENTERPRISE");

    /** 与 kb_workspace.workspace_type 保持兼容的持久化代码。 */
    @EnumValue
    private final String value;

    WorkspaceType(String value) {
        this.value = value;
    }

    /**
     * 获取持久化代码值。
     *
     * @return 与既有 VARCHAR 列一致的类型代码
     */
    public String getValue() {
        return value;
    }
}
