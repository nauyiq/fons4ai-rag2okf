package com.fons.cloud.ai.rag2okf.common.constants.user;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

/**
 * 本地用户账号状态，对应 kb_user.status 的既有 VARCHAR 代码值。
 *
 * @author hongqy
 */
@Getter
public enum UserStatus {
    /** 可以建立会话并访问已授权资源。 */
    ACTIVE("ACTIVE"),
    /** 禁止建立新会话，已存在会话在后续访问时会被踢下线。 */
    DISABLED("DISABLED");

    /** 与 kb_user.status 保持兼容的持久化代码。
     * -- GETTER --
     *  获取持久化代码值。
     *
     */
    @EnumValue
    private final String value;

    UserStatus(String value) {
        this.value = value;
    }

}
