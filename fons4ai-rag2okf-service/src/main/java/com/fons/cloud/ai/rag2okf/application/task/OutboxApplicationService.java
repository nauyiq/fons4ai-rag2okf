package com.fons.cloud.ai.rag2okf.application.task;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fons.cloud.ai.rag2okf.application.model.ModelBusinessKeyGenerator;
import com.fons.cloud.ai.rag2okf.domain.entity.KbOutboxEventEntity;
import com.fons.cloud.ai.rag2okf.domain.service.KbOutboxEventDomainService;
import com.fons.cloud.ai.rag2okf.domain.task.OutboxEventStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * Outbox 应用服务：事件创建、候选扫描和投递状态更新。
 *
 * <p>事件创建必须与聚合变更在同一 MySQL 事务内，保证至少一次投递。
 * 投递由 {@code OutboxDispatchScheduler} 在分布式锁内执行。
 *
 * @author hongqy
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxApplicationService {

    /** Outbox 最大重试次数。 */
    public static final int MAX_OUTBOX_ATTEMPTS = 5;

    /** Outbox 退避基准毫秒。 */
    public static final long OUTBOX_BACKOFF_BASE_MS = 10_000L;

    /** Outbox 退避上限毫秒（10 分钟）。 */
    public static final long OUTBOX_BACKOFF_CAP_MS = 600_000L;

    private final KbOutboxEventDomainService outboxDomainService;
    private final ModelBusinessKeyGenerator keyGenerator;

    /**
     * 创建 Outbox 事件。
     *
     * <p>调用方必须在本方法外层已有 MySQL 事务，保证事件与聚合变更原子提交。
     *
     * @param aggregateType 聚合类型
     * @param aggregateKey  聚合业务标识
     * @param eventType     事件类型
     * @param payloadJson   事件载荷 JSON 快照
     * @return 事件实体
     */
    public KbOutboxEventEntity createEvent(
            String aggregateType, String aggregateKey, String eventType, String payloadJson) {
        KbOutboxEventEntity entity = new KbOutboxEventEntity();
        entity.setEventKey(keyGenerator.nextKey());
        entity.setAggregateType(aggregateType);
        entity.setAggregateKey(aggregateKey);
        entity.setEventType(eventType);
        entity.setPayloadJson(payloadJson);
        entity.setStatus(OutboxEventStatus.PENDING.name());
        entity.setAttempt(0);
        outboxDomainService.save(entity);
        return entity;
    }

    /**
     * 按 eventKey 查询事件。
     *
     * @param eventKey 事件业务标识
     * @return 事件实体，不存在返回 null
     */
    public KbOutboxEventEntity findByKey(String eventKey) {
        return outboxDomainService.getOne(
                Wrappers.<KbOutboxEventEntity>lambdaQuery()
                        .eq(KbOutboxEventEntity::getEventKey, eventKey)
                        .last("LIMIT 1"));
    }

    /**
     * 扫描待投递事件（status=PENDING 且 nextRunAt 已到期）。
     *
     * @param now      当前时间
     * @param batchSize 批量大小
     * @return 候选事件列表
     */
    public List<KbOutboxEventEntity> scanPendingEvents(Date now, int batchSize) {
        return outboxDomainService.list(
                Wrappers.<KbOutboxEventEntity>lambdaQuery()
                        .eq(KbOutboxEventEntity::getStatus, OutboxEventStatus.PENDING.name())
                        .and(w -> w.isNull(KbOutboxEventEntity::getNextRunAt)
                                .or().le(KbOutboxEventEntity::getNextRunAt, now))
                        .orderByAsc(KbOutboxEventEntity::getCreated)
                        .last("LIMIT " + batchSize));
    }

    /**
     * 标记投递成功。
     *
     * @param eventKey 事件业务标识
     * @param now      当前时间
     */
    @Transactional(rollbackFor = Exception.class)
    public void markPublished(String eventKey, Date now) {
        KbOutboxEventEntity entity = outboxDomainService.getOne(
                Wrappers.<KbOutboxEventEntity>lambdaQuery()
                        .eq(KbOutboxEventEntity::getEventKey, eventKey)
                        .last("LIMIT 1"));
        if (entity == null) {
            log.warn("Outbox event not found: {}", eventKey);
            return;
        }
        entity.setStatus(OutboxEventStatus.PUBLISHED.name());
        entity.setPublishedAt(now);
        entity.setNextRunAt(null);
        outboxDomainService.updateById(entity);
    }

    /**
     * 标记投递失败，安排重试或转为 FAILED。
     *
     * @param eventKey 事件业务标识
     * @param now      当前时间
     */
    @Transactional(rollbackFor = Exception.class)
    public void markFailed(String eventKey, Date now) {
        KbOutboxEventEntity entity = outboxDomainService.getOne(
                Wrappers.<KbOutboxEventEntity>lambdaQuery()
                        .eq(KbOutboxEventEntity::getEventKey, eventKey)
                        .last("LIMIT 1"));
        if (entity == null) {
            log.warn("Outbox event not found: {}", eventKey);
            return;
        }
        entity.setAttempt(entity.getAttempt() + 1);
        if (entity.getAttempt() >= MAX_OUTBOX_ATTEMPTS) {
            entity.setStatus(OutboxEventStatus.FAILED.name());
            entity.setNextRunAt(null);
            log.error("Outbox event exhausted retries: eventKey={}, attempts={}",
                    eventKey, entity.getAttempt());
        } else {
            long backoff = computeOutboxBackoff(entity.getAttempt());
            entity.setNextRunAt(new Date(now.getTime() + backoff));
            log.warn("Outbox event retry scheduled: eventKey={}, attempts={}, nextRunAt in {}ms",
                    eventKey, entity.getAttempt(), backoff);
        }
        outboxDomainService.updateById(entity);
    }

    /**
     * 计算指数退避毫秒数。
     *
     * @param attempt 当前已投递次数
     * @return 退避毫秒数，不超过上限
     */
    public static long computeOutboxBackoff(int attempt) {
        long backoff = OUTBOX_BACKOFF_BASE_MS * (1L << Math.max(0, attempt - 1));
        return Math.min(backoff, OUTBOX_BACKOFF_CAP_MS);
    }
}
