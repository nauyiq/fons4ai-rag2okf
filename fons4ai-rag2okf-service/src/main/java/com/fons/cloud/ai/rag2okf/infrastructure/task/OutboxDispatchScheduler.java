package com.fons.cloud.ai.rag2okf.infrastructure.task;

import com.fons.cloud.ai.rag2okf.application.task.OutboxApplicationService;
import com.fons.cloud.ai.rag2okf.domain.entity.KbOutboxEventEntity;
import com.fons.cloud.lock.common.DistributeLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/**
 * Outbox 事件分发调度器。
 *
 * <p>定时扫描 PENDING 事件，委托 {@link OutboxDispatchExecutor} 在 eventKey 分布式锁内分发。
 * 扫描查询不加数据库锁；互斥完全由分布式锁保证。
 *
 * @author hongqy
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxDispatchScheduler {

    private final OutboxApplicationService outboxApplicationService;
    private final OutboxDispatchExecutor outboxDispatchExecutor;

    @Value("${rag2okf.outbox.scan-batch-size:20}")
    private int scanBatchSize;

    /**
     * 定时扫描待投递事件并尝试分发。
     *
     * <p>固定延迟 5 秒，初始延迟 15 秒。worker 不占用 HTTP 线程。
     */
    @Scheduled(fixedDelayString = "${rag2okf.outbox.scan-interval-ms:60000}", initialDelayString = "15000")
    public void scanAndDispatch() {
        Date now = new Date();
        List<KbOutboxEventEntity> candidates = outboxApplicationService.scanPendingEvents(now, scanBatchSize);
        if (candidates.isEmpty()) {
            return;
        }

        log.debug("Found {} pending outbox events", candidates.size());
        for (KbOutboxEventEntity event : candidates) {
            String eventKey = event.getEventKey();
            try {
                outboxDispatchExecutor.dispatchLocked(eventKey);
            } catch (DistributeLockException e) {
                log.debug("Outbox lock held by another instance, skipping: eventKey={}", eventKey);
            } catch (Exception e) {
                log.error("Outbox dispatch failed: eventKey={}", eventKey, e);
            }
        }
    }
}
