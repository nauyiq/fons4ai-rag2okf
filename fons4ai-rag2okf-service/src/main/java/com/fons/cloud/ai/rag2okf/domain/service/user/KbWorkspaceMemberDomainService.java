package com.fons.cloud.ai.rag2okf.domain.service.user;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fons.cloud.ai.rag2okf.domain.entity.user.KbWorkspaceMember;

/**
 * 工作空间成员关系领域服务。
 *
 * @author hongqy
 */
public interface KbWorkspaceMemberDomainService extends IService<KbWorkspaceMember> {

    /**
     * 查询用户在指定工作空间中的成员关系。
     *
     * @param workspaceId 工作空间数据库主键
     * @param userId 用户数据库主键
     * @return 成员关系；不存在时返回 {@code null}
     */
    KbWorkspaceMember findByWorkspaceIdAndUserId(Long workspaceId, Long userId);
}
