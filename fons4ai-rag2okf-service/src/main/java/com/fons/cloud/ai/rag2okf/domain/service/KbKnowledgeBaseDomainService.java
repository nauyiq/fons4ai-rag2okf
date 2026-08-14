package com.fons.cloud.ai.rag2okf.domain.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.fons.cloud.ai.rag2okf.common.request.PageKnowledgeBaseRequest;
import com.fons.cloud.ai.rag2okf.domain.entity.KbKnowledgeBase;

/**
 * 知识库领域服务。
 *
 * @author hongqy
 */
public interface KbKnowledgeBaseDomainService extends IService<KbKnowledgeBase> {

    /**
     * 分页查询知识库。
     * @param request 分页查询知识库请求。
     * @return 知识库分页查询结果。
     */
    Page<KbKnowledgeBase> pageQueryKnowledgeBases(PageKnowledgeBaseRequest request);

    /**
     * 根据知识库key查询知识库。
     * @param knowledgeBaseKey 知识库key。
     * @return 知识库。
     */
    KbKnowledgeBase findByKnowledgeBaseKey(String knowledgeBaseKey);

}
