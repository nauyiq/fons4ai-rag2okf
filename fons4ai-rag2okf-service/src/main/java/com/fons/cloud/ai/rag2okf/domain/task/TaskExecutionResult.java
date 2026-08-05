package com.fons.cloud.ai.rag2okf.domain.task;

/**
 * 任务执行结果。
 *
 * @author hongqy
 */
public sealed interface TaskExecutionResult permits TaskExecutionResult.Succeeded,
        TaskExecutionResult.RetryableFailure, TaskExecutionResult.FatalFailure {

    /**
     * 成功。
     *
     * @param resultKey 成功产物 Revision key（可为 null）
     */
    record Succeeded(String resultKey) implements TaskExecutionResult {}

    /**
     * 可重试失败。
     *
     * @param errorCode    安全化错误码
     * @param errorMessage 安全化错误摘要
     */
    record RetryableFailure(String errorCode, String errorMessage) implements TaskExecutionResult {}

    /**
     * 不可重试失败。
     *
     * @param errorCode    安全化错误码
     * @param errorMessage 安全化错误摘要
     */
    record FatalFailure(String errorCode, String errorMessage) implements TaskExecutionResult {}
}
