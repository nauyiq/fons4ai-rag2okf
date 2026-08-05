package com.fons.cloud.ai.rag2okf.domain.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fons.cloud.ai.rag2okf.domain.entity.KbWorkspaceMemberEntity;
import com.fons.cloud.ai.rag2okf.domain.mapper.KbWorkspaceMemberMapper;
import com.fons.cloud.ai.rag2okf.domain.service.KbWorkspaceMemberDomainService;
import org.springframework.stereotype.Service;

/**
 * 工作空间成员关系领域服务实现。
 *
 * @author hongqy
 */
@Service
public class KbWorkspaceMemberDomainServiceImpl
        extends ServiceImpl<KbWorkspaceMemberMapper, KbWorkspaceMemberEntity>
        implements KbWorkspaceMemberDomainService {
}
