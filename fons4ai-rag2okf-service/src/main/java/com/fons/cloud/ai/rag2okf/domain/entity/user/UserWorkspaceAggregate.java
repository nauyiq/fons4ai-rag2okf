package com.fons.cloud.ai.rag2okf.domain.entity.user;

import com.fons.cloud.ai.rag2okf.common.constants.user.WorkspaceRole;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户工作空间聚合根
 * @param workspace 工作空间
 * @param workspaceMember 工作空间成员
 */
public record UserWorkspaceAggregate(KbWorkspace workspace, KbWorkspaceMember workspaceMember) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 判断当前用户是否具备目标工作空间角色。
     *
     * @param accessRole 需要的角色
     * @return true 表示具备该角色权限
     */
    public boolean hasWorkspaceAccess(WorkspaceRole accessRole) {
        WorkspaceRole localRole = workspaceMember.getLocalRole();
        return localRole.covers(accessRole);
    }




}
