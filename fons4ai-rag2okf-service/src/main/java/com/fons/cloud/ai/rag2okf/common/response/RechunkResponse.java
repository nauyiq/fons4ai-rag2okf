package com.fons.cloud.ai.rag2okf.common.response;

/**
 * 重新分块受理响应。
 *
 * @param documentKey 文档业务标识
 * @param taskKey     任务业务标识
 * @author hongqy
 */
public record RechunkResponse(
        String documentKey,
        String taskKey
) {
}
