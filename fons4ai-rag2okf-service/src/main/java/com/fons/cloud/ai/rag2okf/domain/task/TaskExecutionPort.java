package com.fons.cloud.ai.rag2okf.domain.task;

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
}
