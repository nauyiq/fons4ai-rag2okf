package com.fons.cloud.ai.rag2okf.common.constants.document;

import com.baomidou.mybatisplus.annotation.EnumValue;

/**
 * 文档域异步处理任务状态。
 *
 * <p>状态机（设计 §4.5）：QUEUED→RUNNING 由 worker 取得分布式锁后 CAS 抢占；
 * RUNNING 可流转 SUCCEEDED/RETRY_WAIT/FAILED，也可在执行租约过期后回退 QUEUED
 * 由其他实例恢复；RETRY_WAIT 在退避到期后回到 QUEUED。SUCCEEDED 与 FAILED 为
 * 终态：终态任务不得改回运行态，重新处理必须创建新任务并以
 * {@code retry_of_task_id} 关联原失败任务。</p>
 *
 * <p>多实例互斥由 Fons4Cloud 分布式锁保证；本状态与 {@code execution_owner}、
 * {@code execution_deadline} 等租约字段只用于崩溃恢复判断，不表示数据库锁。</p>
 *
 * @author hongqy
 */
public enum ProcessingTaskStatus {
    /** 已入队，等待 worker 取得分布式锁后执行。 */
    QUEUED("QUEUED"),
    /** 执行中，由持有租约的实例运行。 */
    RUNNING("RUNNING"),
    /** 执行成功，终态。 */
    SUCCEEDED("SUCCEEDED"),
    /** 可重试失败等待退避，到期后回到 QUEUED。 */
    RETRY_WAIT("RETRY_WAIT"),
    /** 永久失败或达到最大执行次数，终态；重新处理需创建新任务。 */
    FAILED("FAILED");

    /** 与数据库列保持兼容的持久化代码。 */
    @EnumValue
    private final String value;

    ProcessingTaskStatus(String value) {
        this.value = value;
    }

    /** 获取持久化代码值。 */
    public String getValue() {
        return value;
    }

    /**
     * 判断是否允许流转到目标状态。
     *
     * <p>白名单即设计 §4.5 状态图的全部合法边：终态不允许出边，
     * QUEUED 只允许被 worker 抢占为 RUNNING，RETRY_WAIT 只允许退避到期回 QUEUED。</p>
     *
     * @param target 目标状态
     * @return 允许流转返回 {@code true}
     */
    public boolean canTransitionTo(ProcessingTaskStatus target) {
        return switch (this) {
            case QUEUED -> target == RUNNING;
            case RUNNING -> target == SUCCEEDED || target == RETRY_WAIT || target == FAILED || target == QUEUED;
            case RETRY_WAIT -> target == QUEUED;
            case SUCCEEDED, FAILED -> false;
        };
    }

    /**
     * 判断是否终态。
     *
     * @return SUCCEEDED 或 FAILED 返回 {@code true}
     */
    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED;
    }
}
