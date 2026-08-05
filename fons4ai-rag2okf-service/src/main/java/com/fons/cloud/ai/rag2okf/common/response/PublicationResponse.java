package com.fons.cloud.ai.rag2okf.common.response;

/**
 * 发布受理响应。
 *
 * @param documentKey       文档业务标识
 * @param taskKey           任务业务标识
 * @param publishStatus     当前发布状态：UNPUBLISHED、PUBLISHING、PUBLISHED、PUBLISH_FAILED
 * @param latestAttemptStatus 最近一次发布尝试状态，用于区分"旧内容仍可用"与"整体未发布"
 * @author hongqy
 */
public record PublicationResponse(
        String documentKey,
        String taskKey,
        String publishStatus,
        String latestAttemptStatus
) {
}
