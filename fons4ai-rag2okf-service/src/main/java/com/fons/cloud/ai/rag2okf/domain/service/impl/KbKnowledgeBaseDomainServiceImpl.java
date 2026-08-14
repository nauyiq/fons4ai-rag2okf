package com.fons.cloud.ai.rag2okf.domain.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fons.cloud.ai.rag2okf.common.request.PageKnowledgeBaseRequest;
import com.fons.cloud.ai.rag2okf.domain.entity.KbKnowledgeBase;
import com.fons.cloud.ai.rag2okf.domain.mapper.KbKnowledgeBaseMapper;
import com.fons.cloud.ai.rag2okf.domain.service.KbKnowledgeBaseDomainService;
import org.springframework.stereotype.Service;

/**
 * 知识库领域服务实现。
 *
 * @author hongqy
 */
@Service
public class KbKnowledgeBaseDomainServiceImpl extends ServiceImpl<KbKnowledgeBaseMapper, KbKnowledgeBase> implements KbKnowledgeBaseDomainService {

    @Override
    public Page<KbKnowledgeBase> pageQueryKnowledgeBases(PageKnowledgeBaseRequest request) {
        Page<KbKnowledgeBase> pageParam = new Page<>(request.getPage(), request.getSize());
        return this.page(pageParam,
                Wrappers.<KbKnowledgeBase>lambdaQuery()
                        .eq(KbKnowledgeBase::getWorkspaceId, request.getWorkspaceId())
                        .orderByDesc(KbKnowledgeBase::getUpdated));
    }

    @Override
    public KbKnowledgeBase findByKnowledgeBaseKey(String knowledgeBaseKey) {
        return this.getOne(Wrappers.<KbKnowledgeBase>lambdaQuery()
                .eq(KbKnowledgeBase::getDeleted, false)
                .eq(KbKnowledgeBase::getKnowledgeBaseKey, knowledgeBaseKey));
    }
}
