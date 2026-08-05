package com.fons.cloud.ai.rag2okf.domain.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fons.cloud.ai.rag2okf.domain.entity.KbWorkspaceEntity;
import com.fons.cloud.ai.rag2okf.domain.mapper.KbWorkspaceMapper;
import com.fons.cloud.ai.rag2okf.domain.service.KbWorkspaceDomainService;
import org.springframework.stereotype.Service;

/**
 * 知识工作空间领域服务实现。
 *
 * @author hongqy
 */
@Service
public class KbWorkspaceDomainServiceImpl
        extends ServiceImpl<KbWorkspaceMapper, KbWorkspaceEntity>
        implements KbWorkspaceDomainService {
}
