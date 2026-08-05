package com.fons.cloud.ai.rag2okf.common.request;

import java.util.List;

/**
 * 编辑知识库请求。
 *
 * <p>所有字段除 revision 外均可为 null；null 表示不修改对应字段。
 * modelBindings 不为 null 时整体替换现有绑定。</p>
 *
 * @param name 知识库名称，null 不修改
 * @param description 知识库描述，null 不修改
 * @param autoParse 是否自动解析，null 不修改
 * @param autoPublish 是否自动发布，null 不修改
 * @param parserProfile 解析策略标识，null 不修改
 * @param chunkProfile 分块配置，null 不修改
 * @param modelBindings 模型用途绑定列表，null 不修改；非 null 时整体替换
 * @param revision 乐观锁版本，必填
 * @author hongqy
 */
public record UpdateKnowledgeBaseRequest(
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
