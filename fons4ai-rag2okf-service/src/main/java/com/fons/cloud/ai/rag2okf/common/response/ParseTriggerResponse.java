package com.fons.cloud.ai.rag2okf.common.response;

import java.util.Date;

/**
 * 解析受理响应。
 *
 * @param documentKey  文档业务标识
 * @param taskKey      任务业务标识，SKIP 模式为 null
 * @param parseStatus  当前解析状态
 * @param publishStatus 当前发布状态
 * @author hongqy
 */
public record ParseTriggerResponse(
        String documentKey,
        String taskKey,
        String parseStatus,
        String publishStatus,
        Date updated
) {
}
