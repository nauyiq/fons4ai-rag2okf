package com.fons.cloud.ai.rag2okf.common.request;

import java.util.List;

/**
 * 创建知识库请求。
 *
 * @param name 知识库名称
 * @param description 知识库描述
 * @param autoParse 是否自动解析
 * @param autoPublish 是否自动发布；为 true 时 autoParse 必须为 true
 * @param parserProfile 解析策略标识
 * @param chunkProfile 分块配置
 * @param modelBindings 初始模型用途绑定列表，可为 null 或空
 * @param revision 乐观锁版本，创建时传 0
 * @author hongqy
 */
public record CreateKnowledgeBaseRequest(
        String name,
        String description,
        Boolean autoParse,
        Boolean autoPublish,
        String parserProfile,
        ChunkProfileRequest chunkProfile,
        List<ModelBindingItem> modelBindings,
        int revision
) {
}
