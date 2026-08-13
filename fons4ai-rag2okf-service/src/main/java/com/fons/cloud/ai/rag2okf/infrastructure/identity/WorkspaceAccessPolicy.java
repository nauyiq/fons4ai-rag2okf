package com.fons.cloud.ai.rag2okf.infrastructure.identity;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fons.cloud.ai.rag2okf.common.constants.UserStatus;
import com.fons.cloud.ai.rag2okf.common.constants.WorkspaceMemberStatus;
import com.fons.cloud.ai.rag2okf.common.constants.WorkspaceRole;
import com.fons.cloud.ai.rag2okf.common.constants.WorkspaceStatus;
import com.fons.cloud.ai.rag2okf.common.exeception.WorkspaceAccessDeniedException;
import com.fons.cloud.ai.rag2okf.domain.entity.KbUser;
import com.fons.cloud.ai.rag2okf.domain.entity.KbWorkspace;
import com.fons.cloud.ai.rag2okf.domain.entity.KbWorkspaceMember;
import com.fons.cloud.ai.rag2okf.domain.service.KbUserDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.KbWorkspaceDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.KbWorkspaceMemberDomainService;
import com.fons.cloud.auth.satoken.api.SaTokenAuthTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 基于本地账号、工作空间和成员关系的授权规则。
 *
 * @author hongqy
 */
@Component
@RequiredArgsConstructor
public class WorkspaceAccessPolicy {

    private final KbUserDomainService userDomainService;
    private final KbWorkspaceDomainService workspaceDomainService;
    private final KbWorkspaceMemberDomainService workspaceMemberDomainService;
    private final SaTokenAuthTemplate saTokenAuthTemplate;

    /**
     * 校验当前 userKey 在目标工作空间中是否具备目标角色。
     *
     * @param userKey Sa-Token loginId，即本地用户业务标识
     * @param workspaceKey 工作空间业务标识
     * @param requiredRole 目标操作所需的最小本地角色
     */
    public void checkAccess(String userKey, String workspaceKey, WorkspaceRole requiredRole) {
        KbUser user = userDomainService.getOne(Wrappers.<KbUser>lambdaQuery()
                .eq(KbUser::getUserKey, userKey));
        if (user == null || user.getStatus() != UserStatus.ACTIVE) {
            saTokenAuthTemplate.kickout(userKey);
            throw new WorkspaceAccessDeniedException();
        }
        KbWorkspace workspace = workspaceDomainService.getOne(Wrappers.<KbWorkspace>lambdaQuery()
                .eq(KbWorkspace::getWorkspaceKey, workspaceKey));
        if (workspace == null || workspace.getStatus() != WorkspaceStatus.ACTIVE) {
            throw new WorkspaceAccessDeniedException();
        }
        KbWorkspaceMember membership = workspaceMemberDomainService.getOne(
                Wrappers.<KbWorkspaceMember>lambdaQuery()
                        .eq(KbWorkspaceMember::getWorkspaceId, workspace.getId())
                        .eq(KbWorkspaceMember::getUserId, user.getId())
        );
        if (membership == null || membership.getStatus() != WorkspaceMemberStatus.ACTIVE) {
            throw new WorkspaceAccessDeniedException();
        }
        WorkspaceRole actualRole = membership.getLocalRole();
        if (actualRole == null) {
            throw new WorkspaceAccessDeniedException();
        }
        if (requiredRole == null || !actualRole.covers(requiredRole)) {
            throw new WorkspaceAccessDeniedException();
        }
    }
}
