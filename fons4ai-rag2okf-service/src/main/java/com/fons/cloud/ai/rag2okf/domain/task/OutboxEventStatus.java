package com.fons.cloud.ai.rag2okf.domain.task;

/**
 * Outbox 事件投递状态。
 *
 * @author hongqy
 */
public enum OutboxEventStatus {

    /** 待投递。 */
    PENDING,

    /** 已投递成功。 */
    PUBLISHED,

    /** 投递失败且不再重试。 */
    FAILED
}
