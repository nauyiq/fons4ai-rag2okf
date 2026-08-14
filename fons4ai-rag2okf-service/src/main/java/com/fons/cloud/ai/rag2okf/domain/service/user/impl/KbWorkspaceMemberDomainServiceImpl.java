package com.fons.cloud.ai.rag2okf.domain.service.user.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fons.cloud.ai.rag2okf.domain.entity.user.KbWorkspaceMember;
import com.fons.cloud.ai.rag2okf.domain.mapper.user.KbWorkspaceMemberMapper;
import com.fons.cloud.ai.rag2okf.domain.service.user.KbWorkspaceMemberDomainService;
import org.springframework.stereotype.Service;

/**
 * 工作空间成员关系领域服务实现。
 *
 * @author hongqy
 */
@Service
public class KbWorkspaceMemberDomainServiceImpl
        extends ServiceImpl<KbWorkspaceMemberMapper, KbWorkspaceMember>
        implements KbWorkspaceMemberDomainService {

    @Override
    public KbWorkspaceMember findByWorkspaceIdAndUserId(Long workspaceId, Long userId) {
        return getOne(Wrappers.lambdaQuery(KbWorkspaceMember.class)
                .eq(KbWorkspaceMember::getWorkspaceId, workspaceId)
                .eq(KbWorkspaceMember::getUserId, userId)
                .eq(KbWorkspaceMember::getDeleted, false));
    }
}
