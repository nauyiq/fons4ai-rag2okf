package com.fons.cloud.ai.rag2okf.domain.service.user.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fons.cloud.ai.rag2okf.domain.entity.user.UserWorkspaceAggregate;
import com.fons.cloud.ai.rag2okf.common.constants.user.WorkspaceMemberStatus;
import com.fons.cloud.ai.rag2okf.common.constants.user.WorkspaceStatus;
import com.fons.cloud.ai.rag2okf.common.exception.user.WorkspaceAccessDeniedException;
import com.fons.cloud.ai.rag2okf.domain.entity.user.KbWorkspace;
import com.fons.cloud.ai.rag2okf.domain.entity.user.KbWorkspaceMember;
import com.fons.cloud.ai.rag2okf.domain.mapper.user.KbWorkspaceMapper;
import com.fons.cloud.ai.rag2okf.domain.mapper.user.KbWorkspaceMemberMapper;
import com.fons.cloud.ai.rag2okf.domain.service.user.KbWorkspaceDomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 知识工作空间领域服务实现。
 *
 * @author hongqy
 */
@Service
@RequiredArgsConstructor
public class KbWorkspaceDomainServiceImpl extends ServiceImpl<KbWorkspaceMapper, KbWorkspace> implements KbWorkspaceDomainService {
    private final KbWorkspaceMemberMapper workspaceMemberMapper;

    @Override
    public UserWorkspaceAggregate findUserWorkspaceAggregate(Long userId, String workspaceKey) {
        KbWorkspace workspace = requireActiveWorkspace(this.findByWorkspaceKey(workspaceKey));
        KbWorkspaceMember workspaceMember = findActiveWorkspaceMember(workspace.getId(), userId);
        return new UserWorkspaceAggregate(workspace, workspaceMember);
    }

    @Override
    public UserWorkspaceAggregate findUserWorkspaceAggregate(Long userId, Long workspaceId) {
        KbWorkspace workspace = requireActiveWorkspace(this.getById(workspaceId));
        KbWorkspaceMember workspaceMember = findActiveWorkspaceMember(workspace.getId(), userId);
        return new UserWorkspaceAggregate(workspace, workspaceMember);
    }

    private KbWorkspace requireActiveWorkspace(KbWorkspace workspace) {
        if (workspace == null || workspace.getStatus() != WorkspaceStatus.ACTIVE) {
            throw new WorkspaceAccessDeniedException();
        }
        return workspace;
    }

    private KbWorkspaceMember findActiveWorkspaceMember(Long workspaceId, Long userId) {
        KbWorkspaceMember workspaceMember = workspaceMemberMapper.selectOne(Wrappers.lambdaQuery(KbWorkspaceMember.class)
                .eq(KbWorkspaceMember::getWorkspaceId, workspaceId)
                .eq(KbWorkspaceMember::getUserId, userId)
                .eq(KbWorkspaceMember::getStatus, WorkspaceMemberStatus.ACTIVE)
                .eq(KbWorkspaceMember::getDeleted, false));
        if (workspaceMember == null || workspaceMember.getStatus() != WorkspaceMemberStatus.ACTIVE) {
            throw new WorkspaceAccessDeniedException();
        }
        return workspaceMember;
    }

    @Override
    public KbWorkspace findByWorkspaceKey(String workspaceKey) {
        return this.getOne(Wrappers.lambdaQuery(KbWorkspace.class)
                .eq(KbWorkspace::getWorkspaceKey, workspaceKey)
                .eq(KbWorkspace::getDeleted, false));
    }
}
