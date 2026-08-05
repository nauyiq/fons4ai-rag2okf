package com.fons.cloud.ai.rag2okf.domain.task;

/**
 * 异步处理任务状态。
 *
 * <p>状态流转见技术设计 §7.3：
 * <pre>
 *   QUEUED → RUNNING → SUCCEEDED（终态）
 *                    → FAILED（终态）
 *                    → RETRY_WAIT → QUEUED（退避到期）
 *   RUNNING → QUEUED（deadline 恢复）
 * </pre>
 * P0 不提供 CANCELLED、WITHDRAWN 或 ROLLED_BACK 业务状态。
 *
 * @author hongqy
 */
public enum TaskStatus {

    /** 已排队，等待 worker 领取。 */
    QUEUED,

    /** 执行中，由 taskKey 分布式锁持有。 */
    RUNNING,

    /** 重试等待中，退避到期后回到 QUEUED。 */
    RETRY_WAIT,

    /** 成功，终态。 */
    SUCCEEDED,

    /** 失败，终态。 */
    FAILED;

    /**
     * 判断是否为终态。
     *
     * @return true 表示任务已结束，不会再变更
     */
    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED;
    }
}
