package com.fons.cloud.ai.rag2okf.infrastructure.task;

import com.fons.cloud.ai.rag2okf.application.task.OutboxApplicationService;
import com.fons.cloud.ai.rag2okf.domain.entity.KbOutboxEventEntity;
import com.fons.cloud.ai.rag2okf.domain.task.OutboxEventPort;
import com.fons.cloud.lock.annotation.DistributeLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/**
 * Outbox 事件分布式锁分发执行器。
 *
 * <p>独立 Spring Bean，通过 {@code @DistributeLock} 在 eventKey 维度获取分布式锁：
 * <ul>
 *   <li>scene = {@code rag2okf:outbox-dispatch}</li>
 *   <li>waitTime = 0：未取得锁立即跳过</li>
 *   <li>expireTime = -1（默认）：watchdog 自动续期</li>
 * </ul>
 *
 * <p>保证至少一次投递：事件可能被分发多次，消费者必须幂等。
 *
 * @author hongqy
 */
@Slf4j
@Component
public class OutboxDispatchExecutor {

    private final OutboxApplicationService outboxApplicationService;
    private final List<OutboxEventPort> ports;

    @Autowired
    public OutboxDispatchExecutor(OutboxApplicationService outboxApplicationService,
                                  List<OutboxEventPort> ports) {
        this.outboxApplicationService = outboxApplicationService;
        this.ports = ports;
        log.info("OutboxEventPort registry initialized: {} ports", ports.size());
    }

    /**
     * 在 eventKey 分布式锁内分发事件。
     *
     * @param eventKey 事件业务标识
     */
    @DistributeLock(scene = "rag2okf:outbox-dispatch", keyExpression = "#eventKey", waitTime = 0)
    public void dispatchLocked(String eventKey) {
        // 锁内重新查询事件
        KbOutboxEventEntity event = outboxApplicationService.findByKey(eventKey);
        if (event == null) {
            log.debug("Outbox event not found: {}", eventKey);
            return;
        }

        // 校验仍为 PENDING（幂等校验，可能已被其他实例处理）
        if (!"PENDING".equals(event.getStatus())) {
            log.debug("Outbox event already dispatched: eventKey={}, status={}", eventKey, event.getStatus());
            return;
        }

        // 查找支持的端口
        OutboxEventPort matchedPort = ports.stream()
                .filter(p -> p.supports(event.getEventType()))
                .findFirst()
                .orElse(null);

        Date now = new Date();
        if (matchedPort == null) {
            log.error("No OutboxEventPort for event type {}, marking as FAILED", event.getEventType());
            outboxApplicationService.markFailed(eventKey, now);
            return;
        }

        try {
            boolean success = matchedPort.handle(event);
            if (success) {
                outboxApplicationService.markPublished(eventKey, now);
                log.info("Outbox event dispatched: eventKey={}, type={}", eventKey, event.getEventType());
            } else {
                outboxApplicationService.markFailed(eventKey, now);
                log.warn("Outbox event dispatch returned false: eventKey={}", eventKey);
            }
        } catch (Exception e) {
            log.error("Outbox event dispatch threw exception: eventKey={}", eventKey, e);
            outboxApplicationService.markFailed(eventKey, new Date());
        }
    }
}
