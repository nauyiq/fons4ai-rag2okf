package com.fons.cloud.ai.rag2okf.domain.service.knowledgebase.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fons.cloud.ai.rag2okf.domain.entity.knowledgebase.KbKnowledgeBase;
import com.fons.cloud.ai.rag2okf.domain.mapper.knowledgebase.KbKnowledgeBaseMapper;
import com.fons.cloud.ai.rag2okf.domain.service.knowledgebase.KbKnowledgeBaseDomainService;
import com.fons.cloud.common.result.PageResult;
import org.springframework.stereotype.Service;

/**
 * 知识库领域服务实现。
 *
 * @author hongqy
 */
@Service
public class KbKnowledgeBaseDomainServiceImpl extends ServiceImpl<KbKnowledgeBaseMapper, KbKnowledgeBase> implements KbKnowledgeBaseDomainService {

    @Override
    public PageResult<KbKnowledgeBase> pageByWorkspaceId(Long workspaceId, int page, int size) {
        Page<KbKnowledgeBase> pageParam = new Page<>(page, size);
        Page<KbKnowledgeBase> result = this.page(pageParam,
                Wrappers.<KbKnowledgeBase>lambdaQuery()
                        .eq(KbKnowledgeBase::getWorkspaceId, workspaceId)
                        .orderByDesc(KbKnowledgeBase::getUpdated));
        PageResult<KbKnowledgeBase> pageResult = new PageResult<>(
                (int) result.getCurrent(), (int) result.getSize(), result.getTotal(), result.getRecords());
        pageResult.setPages((int) result.getPages());
        return pageResult;
    }

    @Override
    public boolean existsByWorkspaceIdAndName(Long workspaceId, String name) {
        return count(Wrappers.<KbKnowledgeBase>lambdaQuery()
                .eq(KbKnowledgeBase::getWorkspaceId, workspaceId)
                .eq(KbKnowledgeBase::getName, name)) > 0;
    }

    @Override
    public KbKnowledgeBase findByKnowledgeBaseKey(String knowledgeBaseKey) {
        return this.getOne(Wrappers.<KbKnowledgeBase>lambdaQuery()
                .eq(KbKnowledgeBase::getDeleted, false)
                .eq(KbKnowledgeBase::getKnowledgeBaseKey, knowledgeBaseKey));
    }
}
