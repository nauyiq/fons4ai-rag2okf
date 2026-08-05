package com.fons.cloud.ai.rag2okf.common.request;

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
        String strategy,
        int chunkSize,
        int overlap,
        Integer titleLevel
) {
}
