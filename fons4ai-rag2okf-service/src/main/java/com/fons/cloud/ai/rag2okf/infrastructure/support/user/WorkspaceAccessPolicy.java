package com.fons.cloud.ai.rag2okf.infrastructure.support.user;

import com.fons.cloud.ai.rag2okf.common.constants.user.UserStatus;
import com.fons.cloud.ai.rag2okf.common.constants.user.WorkspaceMemberStatus;
import com.fons.cloud.ai.rag2okf.common.constants.user.WorkspaceRole;
import com.fons.cloud.ai.rag2okf.common.constants.user.WorkspaceStatus;
import com.fons.cloud.ai.rag2okf.common.exception.user.WorkspaceAccessDeniedException;
import com.fons.cloud.ai.rag2okf.domain.entity.user.KbUser;
import com.fons.cloud.ai.rag2okf.domain.entity.user.KbWorkspace;
import com.fons.cloud.ai.rag2okf.domain.entity.user.KbWorkspaceMember;
import com.fons.cloud.ai.rag2okf.domain.service.user.KbUserDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.user.KbWorkspaceDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.user.KbWorkspaceMemberDomainService;
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
        KbUser user = userDomainService.findByUserKey(userKey);
        if (user == null || user.getStatus() != UserStatus.ACTIVE) {
            saTokenAuthTemplate.kickout(userKey);
            throw new WorkspaceAccessDeniedException();
        }
        KbWorkspace workspace = workspaceDomainService.findByWorkspaceKey(workspaceKey);
        if (workspace == null || workspace.getStatus() != WorkspaceStatus.ACTIVE) {
            throw new WorkspaceAccessDeniedException();
        }
        KbWorkspaceMember membership = workspaceMemberDomainService
                .findByWorkspaceIdAndUserId(workspace.getId(), user.getId());
        if (membership == null || membership.getStatus() != WorkspaceMemberStatus.ACTIVE) {
            throw new WorkspaceAccessDeniedException();
        }
        WorkspaceRole actualRole = membership.getLocalRole();
        if (actualRole == null) {
            throw new WorkspaceAccessDeniedException();
        }
        if (!actualRole.covers(requiredRole)) {
            throw new WorkspaceAccessDeniedException();
        }
    }
}
