package com.fons.cloud.ai.rag2okf.common.response;

/**
 * 分块配置响应 DTO。
 *
 * @param strategy 分块策略标识
 * @param chunkSize 块大小
 * @param overlap 重叠量
 * @param titleLevel 标题层级，可空
 * @author hongqy
 */
public record ChunkProfileResponse(
        String strategy,
        int chunkSize,
        int overlap,
        Integer titleLevel
) {
}
