package com.fons.cloud.ai.rag2okf.common.dto;

import com.fons.cloud.ai.rag2okf.domain.entity.KbOutboxEventEntity;

/**
 * Outbox 事件处理端口，由具体事件处理器实现。
 *
 * <p>实现类负责将事件投递到目标系统（如 ES 投影清理、对象孤儿清理等）。
 * 实现类必须是幂等的，因为 Outbox 保证至少一次投递。
 *
 * @author hongqy
 */
public interface OutboxEventPort {

    /**
     * 判断本端口是否能处理该事件类型。
     *
     * @param eventType 事件类型
     * @return true 如果本端口支持该事件类型
     */
    boolean supports(String eventType);

    /**
     * 处理事件。
     *
     * @param event Outbox 事件实体
     * @return true 表示处理成功，false 表示处理失败需要重试
     */
    boolean handle(KbOutboxEventEntity event);
}
