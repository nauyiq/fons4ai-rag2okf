package com.fons.cloud.ai.rag2okf.common.constants.user;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

/**
 * 工作空间状态，对应 kb_workspace.status 的 VARCHAR 代码值。
 *
 * @author hongqy
 */
@Getter
public enum WorkspaceStatus {

    /** 工作空间可被已授权成员访问。 */
    ACTIVE("ACTIVE"),
    /** 工作空间停用，成员无法继续访问。 */
    DISABLED("DISABLED");

    /** 与 kb_workspace.status 保持兼容的持久化代码。
     * -- GETTER --
     *  获取持久化代码值。
     *
     */
    @EnumValue
    private final String value;

    WorkspaceStatus(String value) {
        this.value = value;
    }

}
