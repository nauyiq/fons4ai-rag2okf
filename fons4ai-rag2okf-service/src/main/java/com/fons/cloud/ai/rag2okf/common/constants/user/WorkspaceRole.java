package com.fons.cloud.ai.rag2okf.common.constants.user;

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
     * <p>角色覆盖关系显式定义，不依赖枚举声明顺序，避免调整枚举顺序时静默改变权限。</p>
     *
     * @param requiredRole 目标角色
     * @return 是否具备目标角色能力
     */
    public boolean covers(WorkspaceRole requiredRole) {
        if (requiredRole == null) {
            return false;
        }
        return switch (this) {
            case ADMIN -> true;
            case KNOWLEDGE_USER -> requiredRole == KNOWLEDGE_USER;
        };
    }
}
