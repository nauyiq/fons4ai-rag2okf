package com.fons.cloud.ai.rag2okf.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fons.cloud.ai.rag2okf.common.constants.WorkspaceStatus;
import com.fons.cloud.ai.rag2okf.common.constants.WorkspaceType;
import com.fons.cloud.db.entity.CommonEntity;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serial;

/**
 * 本地知识工作空间持久化实体。
 *
 * @author hongqy
 */
@Getter
@Setter
@ToString(callSuper = true)
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("kb_workspace")
public class KbWorkspace extends CommonEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 工作空间业务标识。 */
    private String workspaceKey;

    /** 工作空间名称。 */
    private String name;

    /** 空间类型：PERSONAL 个人，ENTERPRISE 企业预留。 */
    private WorkspaceType workspaceType;

    /** 空间所有者的本地用户主键。 */
    private Long ownerUserId;

    /** 工作空间状态。 */
    private WorkspaceStatus status;

    public static KbWorkspace create(String workspaceKey, Long ownerUserId, String userDisplayName) {
        KbWorkspace kbWorkspace = new KbWorkspace();
        kbWorkspace.setWorkspaceKey(workspaceKey);
        kbWorkspace.setName(userDisplayName + " 的知识空间");
        kbWorkspace.setOwnerUserId(ownerUserId);
        kbWorkspace.setWorkspaceType(WorkspaceType.PERSONAL);
        kbWorkspace.setStatus(WorkspaceStatus.ACTIVE);
        return kbWorkspace;
    }
}
