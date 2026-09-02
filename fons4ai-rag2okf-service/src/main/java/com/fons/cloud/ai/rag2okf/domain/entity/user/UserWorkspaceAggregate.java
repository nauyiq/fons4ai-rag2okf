package com.fons.cloud.ai.rag2okf.domain.entity.user;

import com.fons.cloud.ai.rag2okf.common.constants.user.WorkspaceRole;
import com.fons.cloud.ai.rag2okf.common.exception.user.WorkspaceAccessDeniedException;
import lombok.Getter;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户在工作空间中的授权聚合。
 *
 * <p>领域服务在创建本聚合前保证工作空间与成员关系均处于有效状态；
 * 本类只表达工作空间访问与角色覆盖规则，不处理登录态、踢出会话等认证基础设施行为。</p>
 * @author hongqy
 */
@Getter
public class UserWorkspaceAggregate implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 已验证处于有效状态的工作空间。 */
    private final KbWorkspace workspace;

    /** 已验证处于有效状态的工作空间成员关系。 */
    private final KbWorkspaceMember workspaceMember;

    /**
     * 创建用户工作空间授权聚合。
     *
     * @param workspace 已验证的工作空间
     * @param workspaceMember 已验证的成员关系
     */
    public UserWorkspaceAggregate(KbWorkspace workspace, KbWorkspaceMember workspaceMember) {
        this.workspace = workspace;
        this.workspaceMember = workspaceMember;
    }

    /**
     * 判断当前用户是否具备目标工作空间角色。
     *
     * @param accessRole 需要的角色
     * @return true 表示具备该角色权限
     */
    public boolean hasWorkspaceAccess(WorkspaceRole accessRole) {
        WorkspaceRole localRole = workspaceMember.getLocalRole();
        return localRole != null && localRole.covers(accessRole);
    }

    /**
     * 校验当前聚合具备目标角色，并返回实际角色以便服务端裁剪响应内容。
     *
     * @param accessRole 所需最小角色
     * @return 当前成员的实际角色
     * @throws WorkspaceAccessDeniedException 角色不足或成员角色缺失时抛出
     */
    public WorkspaceRole requireAccess(WorkspaceRole accessRole) {
        if (!hasWorkspaceAccess(accessRole)) {
            throw new WorkspaceAccessDeniedException();
        }
        return workspaceMember.getLocalRole();
    }
}
