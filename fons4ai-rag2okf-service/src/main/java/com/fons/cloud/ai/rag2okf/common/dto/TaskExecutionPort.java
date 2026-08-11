package com.fons.cloud.ai.rag2okf.common.dto;

/**
 * 任务执行端口，由具体执行器实现（T012 PARSE、T013 RECHUNK、T015 PUBLISH）。
 *
 * <p>实现类不得自行获取分布式锁；锁由 {@code DistributedLockedTaskExecutor} 统一管理。
 * 实现类应聚焦业务逻辑：解析、分块、发布投影。
 *
 * @author hongqy
 */
public interface TaskExecutionPort {

    /**
     * 本端口支持的任务类型。
     *
     * @return 任务类型
     */
    TaskType supportedType();

    /**
     * 执行任务。
     *
     * @param task 处理任务领域模型，包含输入快照和当前状态
     * @return 执行结果
     */
    TaskExecutionResult execute(ProcessingTask task);

    /**
     * 任务终态失败回调。
     *
     * <p>当任务最终失败（达到最大重试次数或不可重试致命错误）时，
     * 由 {@code DistributedLockedTaskExecutor} 在更新任务状态前调用此方法，
     * 让执行器有机会同步业务级状态（如文档解析状态流转为 FAILED）。
     *
     * <p>默认空实现，执行器按需覆写。回调异常由调用方捕获并记录 WARN 日志，
     * 不影响任务状态持久化。
     *
     * @param task         处理任务领域模型，此时状态已在内存中标记为终态
     * @param errorCode    安全化错误码
     * @param errorMessage 安全化错误摘要
     */
    default void onTerminalFailure(ProcessingTask task, String errorCode, String errorMessage) {
    }
}
