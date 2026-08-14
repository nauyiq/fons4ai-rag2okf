package com.fons.cloud.ai.rag2okf.common.response.knowledgebase;

import java.util.Date;

/**
 * 知识库列表项摘要响应。
 *
 * @param knowledgeBaseKey 知识库业务标识
 * @param name 知识库名称
 * @param description 知识库描述
 * @param autoParse 是否自动解析
 * @param autoPublish 是否自动发布
 * @param ownerUserKey 创建者用户业务标识，由 owner_user_id 关联 kb_user 得到
 * @param canDelete 当前会话用户是否可删除该知识库（owner_user_id 等于当前用户主键时为 true）
 * @param updated 最近更新时间
 * @author hongqy
 */
public record KnowledgeBaseSummaryResponse(
        String knowledgeBaseKey,
        String name,
        String description,
        boolean autoParse,
        boolean autoPublish,
        String ownerUserKey,
        boolean canDelete,
        Date updated
) {
}
