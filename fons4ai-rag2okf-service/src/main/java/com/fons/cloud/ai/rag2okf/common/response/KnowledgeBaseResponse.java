package com.fons.cloud.ai.rag2okf.common.response;

import java.util.Date;
import java.util.List;

/**
 * 知识库详情响应。
 *
 * <p>返回知识库基础信息、处理设置和模型用途绑定，不包含凭证或内部数据库标识。</p>
 *
 * @param knowledgeBaseKey 知识库业务标识
 * @param workspaceKey 工作空间业务标识
 * @param name 知识库名称
 * @param description 知识库描述
 * @param autoParse 是否自动解析
 * @param autoPublish 是否自动发布
 * @param parserProfile 解析策略标识
 * @param chunkProfile 分块配置
 * @param modelBindings 模型用途绑定列表
 * @param revision 当前乐观锁版本
 * @param updated 最近更新时间
 * @author hongqy
 */
public record KnowledgeBaseResponse(
        String knowledgeBaseKey,
        String workspaceKey,
        String name,
        String description,
        boolean autoParse,
        boolean autoPublish,
        String parserProfile,
        ChunkProfileResponse chunkProfile,
        List<ModelBindingResponse> modelBindings,
        int revision,
        Date updated
) {
}
