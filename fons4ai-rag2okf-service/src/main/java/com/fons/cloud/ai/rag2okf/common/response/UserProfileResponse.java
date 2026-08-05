package com.fons.cloud.ai.rag2okf.common.response;

import com.fons.cloud.ai.rag2okf.domain.entity.KbUserEntity;

/**
 * 面向客户端的用户资料安全响应，不包含密码摘要和会话令牌。
 *
 * @param userKey 用户业务标识
 * @param email 当前本人可见的邮箱
 * @param displayName 展示名称
 * @param avatarUrl 头像地址
 * @param preferenceJson 用户偏好 JSON
 * @author hongqy
 */
public record UserProfileResponse(
        String userKey,
        String email,
        String displayName,
        String avatarUrl,
        String preferenceJson
) {

    /**
     * 从本地用户实体创建不包含敏感字段的响应。
     *
     * @param user 本地用户实体
     * @return 安全的用户资料响应
     */
    public static UserProfileResponse from(KbUserEntity user) {
        return new UserProfileResponse(
                user.getUserKey(), user.getEmail(), user.getDisplayName(), user.getAvatarUrl(),
                user.getPreferenceJson()
        );
    }
}
