package com.fons.cloud.ai.rag2okf.common.constants.user;

import com.baomidou.mybatisplus.annotation.EnumValue;

/**
 * 工作空间成员关系状态，对应 kb_workspace_member.status 的 VARCHAR 代码值。
 *
 * @author hongqy
 */
public enum WorkspaceMemberStatus {
    /** 成员关系生效。 */
    ACTIVE("ACTIVE"),
    /** 成员关系停用，不能作为授权依据。 */
    DISABLED("DISABLED");

    /** 与 kb_workspace_member.status 保持兼容的持久化代码。 */
    @EnumValue
    private final String value;

    WorkspaceMemberStatus(String value) {
        this.value = value;
    }

    /**
     * 获取持久化代码值。
     *
     * @return 与既有 VARCHAR 列一致的状态代码
     */
    public String getValue() {
        return value;
    }
}
