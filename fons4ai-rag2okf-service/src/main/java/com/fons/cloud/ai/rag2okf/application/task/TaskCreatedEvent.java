package com.fons.cloud.ai.rag2okf.application.task;

/**
 * 任务创建事件。
 *
 * <p>在任务新建（非幂等命中）后由 {@link TaskApplicationService#createTask} 发布，
 * 由 {@code TaskCreatedEventListener} 在事务提交后异步接收，触发任务的立即执行，
 * 避免等待兜底扫描。
 *
 * @param taskKey 任务业务标识
 * @author hongqy
 */
public record TaskCreatedEvent(String taskKey) {
}
