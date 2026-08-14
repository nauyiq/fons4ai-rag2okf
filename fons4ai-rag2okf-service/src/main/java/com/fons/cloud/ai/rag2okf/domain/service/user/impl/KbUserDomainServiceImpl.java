package com.fons.cloud.ai.rag2okf.domain.service.user.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fons.cloud.ai.rag2okf.domain.entity.user.UserProfileAggregate;
import com.fons.cloud.ai.rag2okf.common.constants.user.WorkspaceMemberStatus;
import com.fons.cloud.ai.rag2okf.common.constants.user.WorkspaceType;
import com.fons.cloud.ai.rag2okf.domain.entity.user.KbUser;
import com.fons.cloud.ai.rag2okf.domain.entity.user.KbWorkspace;
import com.fons.cloud.ai.rag2okf.domain.entity.user.KbWorkspaceMember;
import com.fons.cloud.ai.rag2okf.domain.mapper.user.KbUserMapper;
import com.fons.cloud.ai.rag2okf.domain.mapper.user.KbWorkspaceMapper;
import com.fons.cloud.ai.rag2okf.domain.mapper.user.KbWorkspaceMemberMapper;
import com.fons.cloud.ai.rag2okf.domain.service.user.KbUserDomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * 本地用户领域服务实现，使用用户域 Mapper 恢复用户资料聚合并持久化资料白名单字段。
 *
 * @author hongqy
 */
@Service
@RequiredArgsConstructor
public class KbUserDomainServiceImpl extends ServiceImpl<KbUserMapper, KbUser> implements KbUserDomainService {

    private final KbWorkspaceMapper workspaceMapper;
    private final KbWorkspaceMemberMapper workspaceMemberMapper;

    @Override
    public KbUser findByUserKey(String userKey) {
        return getOne(Wrappers.lambdaQuery(KbUser.class)
                .eq(KbUser::getUserKey, userKey)
                .eq(KbUser::getDeleted, false));
    }

    @Override
    public UserProfileAggregate findUserProfileAggregate(String userKey) {
        KbUser user = findByUserKey(userKey);
        if (user == null) {
            return null;
        }
        KbWorkspace workspace = workspaceMapper.selectOne(Wrappers.lambdaQuery(KbWorkspace.class)
                .eq(KbWorkspace::getOwnerUserId, user.getId())
                .eq(KbWorkspace::getWorkspaceType, WorkspaceType.PERSONAL)
                .eq(KbWorkspace::getDeleted, false));
        if (workspace == null) {
            return new UserProfileAggregate(user, null, null);
        }
        KbWorkspaceMember membership = workspaceMemberMapper.selectOne(
                Wrappers.lambdaQuery(KbWorkspaceMember.class)
                        .eq(KbWorkspaceMember::getWorkspaceId, workspace.getId())
                        .eq(KbWorkspaceMember::getUserId, user.getId())
                        .eq(KbWorkspaceMember::getStatus, WorkspaceMemberStatus.ACTIVE)
                        .eq(KbWorkspaceMember::getDeleted, false));
        return new UserProfileAggregate(user, workspace, membership);
    }

    @Override
    public KbUser findByEmail(String email) {
        return getOne(Wrappers.lambdaQuery(KbUser.class).eq(KbUser::getEmail, email).eq(KbUser::getDeleted, false));
    }

    @Override
    public void updateProfile(KbUser user) {
        update(Wrappers.<KbUser>lambdaUpdate()
                .eq(KbUser::getId, user.getId())
                .eq(KbUser::getDeleted, false)
                .set(KbUser::getDisplayName, user.getDisplayName())
                .set(KbUser::getAvatarUrl, user.getAvatarUrl())
                .set(KbUser::getPreferenceJson, user.getPreferenceJson()));
    }

    @Override
    public void updateLastLoginAt(Long id) {
        update(Wrappers.<KbUser>lambdaUpdate()
                .eq(KbUser::getId, id)
                .set(KbUser::getLastLoginAt, new Date()));
    }
}
