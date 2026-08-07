package com.fons.cloud.ai.rag2okf.task;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fons.cloud.ai.rag2okf.common.dto.ModelBusinessKeyGenerator;
import com.fons.cloud.ai.rag2okf.application.task.OutboxApplicationService;
import com.fons.cloud.ai.rag2okf.domain.entity.KbOutboxEventEntity;
import com.fons.cloud.ai.rag2okf.domain.service.KbOutboxEventDomainService;
import com.fons.cloud.ai.rag2okf.common.dto.OutboxEventStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * OutboxApplicationService 测试。
 *
 * <p>覆盖 Outbox 事件创建、投递状态更新和至少一次投递退避机制。
 *
 * @author hongqy
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Outbox 应用服务")
class OutboxApplicationServiceTest {

    @Mock private KbOutboxEventDomainService outboxDomainService;
    @Mock private ModelBusinessKeyGenerator keyGenerator;

    @InjectMocks private OutboxApplicationService service;

    // ────────────────────────────── 事件创建 ──────────────────────────────

    @Test
    @DisplayName("创建事件初始状态为 PENDING，attempt=0")
    void createEvent_setsPendingStatusAndZeroAttempt() {
        when(keyGenerator.nextKey()).thenReturn("01J_EVENT_KEY");
        when(outboxDomainService.save(any())).thenAnswer(inv -> {
            ((KbOutboxEventEntity) inv.getArgument(0)).setId(1L);
            return true;
        });

        KbOutboxEventEntity event = service.createEvent(
                "SourceDocument", "01J_DOC_KEY", "DOCUMENT_VERSION_SWITCHED", "{}");

        assertEquals("01J_EVENT_KEY", event.getEventKey());
        assertEquals(OutboxEventStatus.PENDING.name(), event.getStatus());
        assertEquals(0, event.getAttempt());
        assertEquals("DOCUMENT_VERSION_SWITCHED", event.getEventType());
    }

    // ────────────────────────────── 投递成功 ──────────────────────────────

    @Test
    @DisplayName("投递成功标记为 PUBLISHED")
    void markPublished_setsPublishedStatus() {
        KbOutboxEventEntity event = new KbOutboxEventEntity();
        event.setId(1L);
        event.setEventKey("01J_EVENT_KEY");
        event.setStatus(OutboxEventStatus.PENDING.name());
        event.setAttempt(0);
        event.setVersion(0);

        when(outboxDomainService.getOne(any())).thenReturn(event);
        when(outboxDomainService.updateById(any())).thenReturn(true);

        Date now = new Date();
        service.markPublished("01J_EVENT_KEY", now);

        assertEquals(OutboxEventStatus.PUBLISHED.name(), event.getStatus());
        assertEquals(now, event.getPublishedAt());
        assertNull(event.getNextRunAt());
    }

    // ────────────────────────────── 投递失败与重试 ──────────────────────────────

    @Test
    @DisplayName("投递失败在重试上限内设置退避并保持 PENDING")
    void markFailed_withinMaxAttemptsSchedulesRetry() {
        KbOutboxEventEntity event = new KbOutboxEventEntity();
        event.setId(1L);
        event.setEventKey("01J_EVENT_KEY");
        event.setStatus(OutboxEventStatus.PENDING.name());
        event.setAttempt(0);
        event.setVersion(0);

        when(outboxDomainService.getOne(any())).thenReturn(event);
        when(outboxDomainService.updateById(any())).thenReturn(true);

        Date now = new Date();
        service.markFailed("01J_EVENT_KEY", now);

        assertEquals(1, event.getAttempt(), "attempt 应递增");
        assertEquals(OutboxEventStatus.PENDING.name(), event.getStatus(), "仍为 PENDING 等待重试");
        assertNotNull(event.getNextRunAt(), "退避时间必须设置");
        assertTrue(event.getNextRunAt().after(now), "退避时间在未来");
    }

    @Test
    @DisplayName("投递失败超过重试上限转为 FAILED 终态")
    void markFailed_exceedsMaxAttemptsBecomesFailed() {
        KbOutboxEventEntity event = new KbOutboxEventEntity();
        event.setId(1L);
        event.setEventKey("01J_EVENT_KEY");
        event.setStatus(OutboxEventStatus.PENDING.name());
        event.setAttempt(OutboxApplicationService.MAX_OUTBOX_ATTEMPTS - 1);
        event.setVersion(0);

        when(outboxDomainService.getOne(any())).thenReturn(event);
        when(outboxDomainService.updateById(any())).thenReturn(true);

        service.markFailed("01J_EVENT_KEY", new Date());

        assertEquals(OutboxApplicationService.MAX_OUTBOX_ATTEMPTS, event.getAttempt());
        assertEquals(OutboxEventStatus.FAILED.name(), event.getStatus(), "超过上限转为 FAILED");
        assertNull(event.getNextRunAt(), "FAILED 不设置 nextRunAt");
    }

    // ────────────────────────────── 退避计算 ──────────────────────────────

    @Test
    @DisplayName("Outbox 退避按 2^n 递增且不超过上限")
    void computeOutboxBackoff_exponentialWithCap() {
        assertEquals(OutboxApplicationService.OUTBOX_BACKOFF_BASE_MS,
                OutboxApplicationService.computeOutboxBackoff(1), "attempt=1: base");
        assertEquals(OutboxApplicationService.OUTBOX_BACKOFF_BASE_MS * 2,
                OutboxApplicationService.computeOutboxBackoff(2), "attempt=2: base*2");
        assertTrue(OutboxApplicationService.computeOutboxBackoff(20) <=
                OutboxApplicationService.OUTBOX_BACKOFF_CAP_MS, "不超过上限");
    }

    // ────────────────────────────── 候选扫描 ──────────────────────────────

    @Test
    @DisplayName("扫描 PENDING 事件返回候选列表")
    void scanPendingEvents_returnsPendingEvents() {
        KbOutboxEventEntity event = new KbOutboxEventEntity();
        event.setId(1L);
        event.setEventKey("01J_EVENT_KEY");
        event.setStatus(OutboxEventStatus.PENDING.name());

        when(outboxDomainService.list(any(Wrapper.class))).thenReturn(List.of(event));

        List<KbOutboxEventEntity> results = service.scanPendingEvents(new Date(), 10);

        assertEquals(1, results.size());
        assertEquals("01J_EVENT_KEY", results.get(0).getEventKey());
    }

    @Test
    @DisplayName("按 eventKey 查询事件")
    void findByKey_returnsEvent() {
        KbOutboxEventEntity event = new KbOutboxEventEntity();
        event.setId(1L);
        event.setEventKey("01J_EVENT_KEY");
        event.setStatus(OutboxEventStatus.PENDING.name());

        when(outboxDomainService.getOne(any())).thenReturn(event);

        KbOutboxEventEntity result = service.findByKey("01J_EVENT_KEY");

        assertNotNull(result);
        assertEquals("01J_EVENT_KEY", result.getEventKey());
    }
}
