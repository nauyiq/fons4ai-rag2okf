package com.fons.cloud.ai.rag2okf.infrastructure.task;

import com.fons.cloud.ai.rag2okf.application.task.TaskCreatedEvent;
import com.fons.cloud.lock.common.DistributeLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 任务创建事件监听器。
 *
 * <p>在创建任务的事务提交后（AFTER_COMMIT）异步调用
 * {@link DistributedLockedTaskExecutor#executeLocked}，实现任务创建后立即触发执行。
 * 锁冲突或执行异常时静默跳过，由兜底扫描补偿。
 *
 * @author hongqy
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskCreatedEventListener {

    private final DistributedLockedTaskExecutor taskExecutor;

    /**
     * 事务提交后异步触发任务执行。
     *
     * <p>锁冲突或任务不可执行时静默跳过，不抛异常，由兜底扫描补偿。
     *
     * @param event 任务创建事件
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTaskCreated(TaskCreatedEvent event) {
        String taskKey = event.taskKey();
        try {
            taskExecutor.executeLocked(taskKey);
            log.debug("Task immediately triggered after creation: taskKey={}", taskKey);
        } catch (DistributeLockException e) {
            log.debug("Task lock held by another instance, skipping immediate trigger: taskKey={}", taskKey);
        } catch (Exception e) {
            log.warn("Immediate task trigger failed, will be picked up by scan: taskKey={}", taskKey, e);
        }
    }
}
