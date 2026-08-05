package com.fons.cloud.ai.rag2okf.common.response;

import java.util.Date;

/**
 * 任务状态查询响应。
 *
 * <p>只暴露安全化字段，不包含 payload、执行实例标识等内部信息。
 *
 * @author hongqy
 */
public record TaskStatusResponse(
        String taskKey,
        String taskType,
        String status,
        String stage,
        Integer progress,
        Integer attempt,
        Integer maxAttempts,
        String errorCode,
        String errorMessage,
        Date created,
        Date updated
) {
}
