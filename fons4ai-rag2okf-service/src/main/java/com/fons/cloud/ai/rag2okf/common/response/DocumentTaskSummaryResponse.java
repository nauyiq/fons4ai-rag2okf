package com.fons.cloud.ai.rag2okf.common.response;

import java.util.Date;

/**
 * 文档最近处理任务的安全化摘要，用于页面刷新后的轮询与失败重试。
 *
 * @param taskKey 任务业务标识
 * @param taskType 任务类型
 * @param status 任务状态
 * @param stage 当前阶段
 * @param progress 执行进度
 * @param attempt 已尝试次数
 * @param maxAttempts 最大尝试次数
 * @param errorCode 安全化错误码
 * @param errorMessage 安全化错误摘要
 * @param updated 最近更新时间
 * @author hongqy
 */
public record DocumentTaskSummaryResponse(
        String taskKey, String taskType, String status, String stage, Integer progress,
        Integer attempt, Integer maxAttempts, String errorCode, String errorMessage, Date updated) {
}
