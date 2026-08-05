package com.fons.cloud.ai.rag2okf.domain.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fons.cloud.ai.rag2okf.domain.entity.KbKnowledgeBaseEntity;
import com.fons.cloud.ai.rag2okf.domain.mapper.KbKnowledgeBaseMapper;
import com.fons.cloud.ai.rag2okf.domain.service.KbKnowledgeBaseDomainService;
import org.springframework.stereotype.Service;

/**
 * 知识库领域服务实现。
 *
 * @author hongqy
 */
@Service
public class KbKnowledgeBaseDomainServiceImpl
        extends ServiceImpl<KbKnowledgeBaseMapper, KbKnowledgeBaseEntity>
        implements KbKnowledgeBaseDomainService {
}
