package com.fons.cloud.ai.rag2okf.common.dto;

import com.fons.cloud.ai.rag2okf.domain.entity.KbUser;

/**
 * 当前认证用户解析端口。
 *
 * @author hongqy
 */
public interface CurrentUserContext {

    /**
     * 取得已认证且仍可用的本地用户。
     *
     * @return 当前本地用户
     */
    KbUser requireCurrentUser();
}
