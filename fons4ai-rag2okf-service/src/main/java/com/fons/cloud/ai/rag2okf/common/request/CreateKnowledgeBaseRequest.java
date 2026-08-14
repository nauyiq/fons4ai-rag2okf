package com.fons.cloud.ai.rag2okf.common.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

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
        @NotBlank(message = "知识库名称不能为空")
        @Size(max = 128, message = "知识库名称不能超过128个字符")
        String name,
        @Size(max = 1000, message = "知识库描述不能超过1000个字符")
        String description,
        Boolean autoParse,
        Boolean autoPublish,
        @NotBlank(message = "解析策略标识不能为空")
        @Size(max = 64, message = "解析策略标识不能超过64个字符")
        String parserProfile,
        @NotNull(message = "分块配置不能为空")
        @Valid
        ChunkProfileRequest chunkProfile,
        @Valid
        List<ModelBindingItem> modelBindings,
        int revision
) {
}
