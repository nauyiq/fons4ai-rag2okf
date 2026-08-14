package com.fons.cloud.ai.rag2okf.common.request.knowledgebase;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 编辑知识库请求。
 *
 * <p>所有字段除 revision 外均可为 null；null 表示不修改对应字段。
 * modelBindings 不为 null 时整体替换现有绑定。</p>
 *
 * @param name 知识库名称，null 不修改
 * @param description 知识库描述，null 不修改
 * @param autoParse 上传成功后是否自动发起解析，null 不修改
 * @param autoPublish 解析成功后是否自动发布，null 不修改；可与 autoParse 独立配置
 * @param parserProfile 解析策略标识，null 不修改
 * @param chunkProfile 分块配置，null 不修改
 * @param modelBindings 模型用途绑定列表，null 不修改；非 null 时整体替换
 * @param revision 乐观锁版本，必填
 * @author hongqy
 */
public record UpdateKnowledgeBaseRequest(
        @Size(max = 128, message = "知识库名称不能超过128个字符")
        String name,
        @Size(max = 1000, message = "知识库描述不能超过1000个字符")
        String description,
        Boolean autoParse,
        Boolean autoPublish,
        @Size(max = 64, message = "解析策略标识不能超过64个字符")
        String parserProfile,
        @Valid
        ChunkProfileRequest chunkProfile,
        @Valid
        List<ModelBindingItem> modelBindings,
        @Min(value = 0, message = "乐观锁版本不能为负数")
        int revision
) {
}
