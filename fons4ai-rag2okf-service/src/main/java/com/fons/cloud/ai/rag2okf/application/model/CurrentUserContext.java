package com.fons.cloud.ai.rag2okf.application.model;

import com.fons.cloud.ai.rag2okf.domain.entity.KbUserEntity;

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
    KbUserEntity requireCurrentUser();
}
