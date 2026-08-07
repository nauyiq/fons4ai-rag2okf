package com.fons.cloud.ai.rag2okf.common.response;

import com.fons.cloud.ai.rag2okf.common.constants.WorkspaceRole;
import com.fons.cloud.ai.rag2okf.domain.entity.KbUserEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbWorkspaceEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbWorkspaceMemberEntity;

/**
 * 面向客户端的用户资料安全响应，不包含密码摘要和会话令牌。
 *
 * @param userKey 用户业务标识
 * @param email 当前本人可见的邮箱
 * @param displayName 展示名称
 * @param avatarUrl 头像地址
 * @param preferenceJson 用户偏好 JSON
 * @param workspaceKey 当前用户个人工作空间业务标识
 * @param workspaceName 当前用户个人工作空间名称
 * @param workspaceRole 当前用户在工作空间中的角色
 * @author hongqy
 */
public record UserProfileResponse(
        String userKey,
        String email,
        String displayName,
        String avatarUrl,
        String preferenceJson,
        String workspaceKey,
        String workspaceName,
        String workspaceRole
) {

    /**
     * 从本地用户实体创建不包含敏感字段的响应。
     *
     * @param user 本地用户实体
     * @param workspace 用户个人工作空间实体（可为 null）
     * @param membership 工作空间成员关系实体（可为 null）
     * @return 安全的用户资料响应
     */
    public static UserProfileResponse from(KbUserEntity user, KbWorkspaceEntity workspace,
                                           KbWorkspaceMemberEntity membership) {
        return new UserProfileResponse(
                user.getUserKey(), user.getEmail(), user.getDisplayName(), user.getAvatarUrl(),
                user.getPreferenceJson(),
                workspace != null ? workspace.getWorkspaceKey() : null,
                workspace != null ? workspace.getName() : null,
                membership != null && membership.getLocalRole() != null
                        ? membership.getLocalRole().getValue() : null
        );
    }
}
