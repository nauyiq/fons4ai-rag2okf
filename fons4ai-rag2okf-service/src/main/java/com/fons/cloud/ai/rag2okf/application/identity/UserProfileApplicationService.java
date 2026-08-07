package com.fons.cloud.ai.rag2okf.application.identity;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fons.cloud.ai.rag2okf.domain.entity.KbUserEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbWorkspaceEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbWorkspaceMemberEntity;
import com.fons.cloud.ai.rag2okf.domain.mapper.KbWorkspaceMapper;
import com.fons.cloud.ai.rag2okf.domain.mapper.KbWorkspaceMemberMapper;
import com.fons.cloud.ai.rag2okf.common.exeception.AuthenticationDeniedException;
import com.fons.cloud.ai.rag2okf.common.exeception.InvalidUserProfileException;
import com.fons.cloud.ai.rag2okf.infrastructure.identity.LocalAccountRepository;
import com.fons.cloud.ai.rag2okf.common.constants.UserStatus;
import com.fons.cloud.ai.rag2okf.common.constants.WorkspaceMemberStatus;
import com.fons.cloud.auth.satoken.api.SaTokenAuthTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 当前已登录用户资料的应用服务。
 *
 * @author hongqy
 */
@Service
@RequiredArgsConstructor
public class UserProfileApplicationService {

    private final LocalAccountRepository accountRepository;
    private final SaTokenAuthTemplate saTokenAuthTemplate;
    private final KbWorkspaceMapper workspaceMapper;
    private final KbWorkspaceMemberMapper workspaceMemberMapper;

    /**
     * 获取当前会话所属用户的安全资料快照。
     *
     * @return 当前活跃本地账号
     */
    public KbUserEntity currentUser() {
        if (!saTokenAuthTemplate.isLogin()) {
            throw new AuthenticationDeniedException();
        }
        String userKey = saTokenAuthTemplate.getCurrentLoginIdAsString();
        KbUserEntity user = accountRepository.findByUserKey(userKey).orElse(null);
        if (user == null || user.getStatus() != UserStatus.ACTIVE) {
            saTokenAuthTemplate.kickout(userKey);
            throw new AuthenticationDeniedException();
        }
        return user;
    }

    /**
     * 获取当前用户的个人工作空间。
     *
     * @param user 当前用户
     * @return 个人工作空间实体，不存在时返回 null
     */
    public KbWorkspaceEntity currentWorkspace(KbUserEntity user) {
        return workspaceMapper.selectOne(
                new LambdaQueryWrapper<KbWorkspaceEntity>()
                        .eq(KbWorkspaceEntity::getOwnerUserId, user.getId()));
    }

    /**
     * 获取当前用户在工作空间中的成员关系。
     *
     * @param user 当前用户
     * @param workspace 工作空间
     * @return 成员关系实体，不存在时返回 null
     */
    public KbWorkspaceMemberEntity currentMembership(KbUserEntity user, KbWorkspaceEntity workspace) {
        if (workspace == null) {
            return null;
        }
        return workspaceMemberMapper.selectOne(
                new LambdaQueryWrapper<KbWorkspaceMemberEntity>()
                        .eq(KbWorkspaceMemberEntity::getWorkspaceId, workspace.getId())
                        .eq(KbWorkspaceMemberEntity::getUserId, user.getId())
                        .eq(KbWorkspaceMemberEntity::getStatus, WorkspaceMemberStatus.ACTIVE));
    }

    /**
     * 更新当前用户允许自行编辑的资料白名单。
     *
     * @param displayName 展示名称
     * @param avatarUrl 头像地址
     * @param preferenceJson 用户偏好快照
     * @return 更新后的当前用户
     */
    public KbUserEntity updateCurrentUser(String displayName, String avatarUrl, String preferenceJson) {
        KbUserEntity user = currentUser();
        user.setDisplayName(normalizeDisplayName(displayName));
        user.setAvatarUrl(normalizeOptional(avatarUrl, 512));
        user.setPreferenceJson(normalizeOptional(preferenceJson, 8_192));
        accountRepository.updateProfile(user);
        return user;
    }

    private String normalizeDisplayName(String displayName) {
        String normalized = displayName == null ? "" : displayName.trim();
        if (normalized.isBlank() || normalized.length() > 64) {
            throw new InvalidUserProfileException();
        }
        return normalized;
    }

    private String normalizeOptional(String value, int maximumLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maximumLength) {
            throw new InvalidUserProfileException();
        }
        return normalized.isEmpty() ? null : normalized;
    }
}
