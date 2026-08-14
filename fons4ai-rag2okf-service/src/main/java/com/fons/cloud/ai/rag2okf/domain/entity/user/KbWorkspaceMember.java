package com.fons.cloud.ai.rag2okf.domain.entity.user;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fons.cloud.ai.rag2okf.common.constants.user.WorkspaceMemberStatus;
import com.fons.cloud.ai.rag2okf.common.constants.user.WorkspaceRole;
import com.fons.cloud.db.entity.CommonEntity;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serial;

/**
 * 本地用户与工作空间角色关系持久化实体。
 *
 * @author hongqy
 */
@Getter
@Setter
@ToString(callSuper = true)
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("kb_workspace_member")
public class KbWorkspaceMember extends CommonEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 工作空间数据库主键。 */
    private Long workspaceId;

    /** 本地用户数据库主键。 */
    private Long userId;

    /** 本地角色：ADMIN 或 KNOWLEDGE_USER。 */
    private WorkspaceRole localRole;

    /** 成员关系状态。 */
    private WorkspaceMemberStatus status;

    public static KbWorkspaceMember createAdmin(Long userId, Long workspaceId) {
        KbWorkspaceMember member = new KbWorkspaceMember();
        member.setUserId(userId);
        member.setWorkspaceId(workspaceId);
        member.setLocalRole(WorkspaceRole.ADMIN);
        member.setStatus(WorkspaceMemberStatus.ACTIVE);
        return member;
    }
}
