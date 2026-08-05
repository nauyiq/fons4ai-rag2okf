package com.fons.cloud.ai.rag2okf.common.constants;

import com.baomidou.mybatisplus.annotation.EnumValue;

/**
 * 工作空间内的本地角色。
 *
 * @author hongqy
 */
public enum WorkspaceRole {
    /** 可以访问和操作知识内容。 */
    KNOWLEDGE_USER("KNOWLEDGE_USER"),
    /** 可以管理工作空间级配置。 */
    ADMIN("ADMIN");

    /** 与 kb_workspace_member.local_role 保持兼容的持久化代码。 */
    @EnumValue
    private final String value;

    WorkspaceRole(String value) {
        this.value = value;
    }

    /**
     * 获取持久化代码值。
     *
     * @return 与既有 VARCHAR 列一致的角色代码
     */
    public String getValue() {
        return value;
    }

    /**
     * 判断当前角色是否覆盖目标角色。
     *
     * @param requiredRole 目标角色
     * @return 是否具备目标角色能力
     */
    public boolean covers(WorkspaceRole requiredRole) {
        return ordinal() >= requiredRole.ordinal();
    }
}
