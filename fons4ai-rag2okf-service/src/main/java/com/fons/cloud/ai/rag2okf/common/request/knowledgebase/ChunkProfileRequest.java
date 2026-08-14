package com.fons.cloud.ai.rag2okf.common.request.knowledgebase;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * 分块配置请求 DTO，用于在知识库创建和编辑接口中接收前端提交的分块参数。
 *
 * @param strategy 分块策略标识
 * @param chunkSize 块大小
 * @param overlap 重叠量
 * @param titleLevel 标题层级，可空
 * @author hongqy
 */
public record ChunkProfileRequest(
        @NotBlank(message = "分块策略不能为空")
        String strategy,
        @Min(value = 1, message = "块大小必须大于0")
        int chunkSize,
        @Min(value = 0, message = "重叠量不能为负数")
        int overlap,
        Integer titleLevel) {


}
