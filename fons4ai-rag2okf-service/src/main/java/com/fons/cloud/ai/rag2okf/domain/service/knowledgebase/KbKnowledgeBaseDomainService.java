package com.fons.cloud.ai.rag2okf.domain.service.knowledgebase;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fons.cloud.common.result.PageResult;
import com.fons.cloud.ai.rag2okf.domain.entity.knowledgebase.KbKnowledgeBase;

/**
 * 知识库领域服务。
 *
 * @author hongqy
 */
public interface KbKnowledgeBaseDomainService extends IService<KbKnowledgeBase> {

    /**
     * 分页查询知识库。
     * @param workspaceId 工作空间主键
     * @param page 页码，从 1 开始
     * @param size 每页大小
     * @return 知识库分页查询结果。
     */
    PageResult<KbKnowledgeBase> pageByWorkspaceId(Long workspaceId, int page, int size);

    /**
     * 判断工作空间内是否已经存在同名知识库。
     *
     * @param workspaceId 工作空间主键
     * @param name 知识库名称
     * @return 存在时返回 {@code true}
     */
    boolean existsByWorkspaceIdAndName(Long workspaceId, String name);

    /**
     * 根据知识库key查询知识库。
     * @param knowledgeBaseKey 知识库key。
     * @return 知识库。
     */
    KbKnowledgeBase findByKnowledgeBaseKey(String knowledgeBaseKey);

}
