package com.fons.cloud.ai.rag2okf.application.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fons.cloud.ai.rag2okf.common.dto.ModelBusinessKeyGenerator;
import com.fons.cloud.ai.rag2okf.common.exeception.TaskExecutionException;
import com.fons.cloud.ai.rag2okf.domain.entity.KbProcessingTaskEntity;
import com.fons.cloud.ai.rag2okf.domain.service.KbProcessingTaskDomainService;
import com.fons.cloud.ai.rag2okf.common.dto.ProcessingTask;
import com.fons.cloud.ai.rag2okf.common.dto.TaskStatus;
import com.fons.cloud.ai.rag2okf.common.dto.TaskType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * 任务应用服务：幂等创建、状态查询、状态机更新和候选扫描。
 *
 * <p>本服务不获取分布式锁；锁由 {@code DistributedLockedTaskExecutor} 统一管理。
 * MySQL 只保存状态、幂等和恢复事实，不使用行锁互斥。
 *
 * @author hongqy
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskApplicationService {

    private final KbProcessingTaskDomainService taskDomainService;
    private final ModelBusinessKeyGenerator keyGenerator;

    /**
     * 幂等创建任务。
     *
     * <p>相同 (sourceDocumentId, taskType, idempotencyKey) 返回原任务；
     * 依赖数据库唯一约束 uk_kb_processing_task_idempotency 保证并发安全。
     *
     * @param workspaceId      工作空间主键
     * @param knowledgeBaseId  知识库主键
     * @param sourceDocumentId 源文档主键
     * @param taskType         任务类型
     * @param inputRevisionKey 输入 Revision key（可为 null）
     * @param idempotencyKey   幂等键
     * @param payloadJson      任务输入 JSON 快照
     * @return 任务领域模型
     */
    @Transactional(rollbackFor = Exception.class)
    public ProcessingTask createTask(
            Long workspaceId, Long knowledgeBaseId, Long sourceDocumentId,
            TaskType taskType, String inputRevisionKey,
            String idempotencyKey, String payloadJson) {

        // 幂等查询：相同文档、类型和幂等键
        KbProcessingTaskEntity existing = taskDomainService.getOne(
                Wrappers.<KbProcessingTaskEntity>lambdaQuery()
                        .eq(KbProcessingTaskEntity::getSourceDocumentId, sourceDocumentId)
                        .eq(KbProcessingTaskEntity::getTaskType, taskType.name())
                        .eq(KbProcessingTaskEntity::getIdempotencyKey, idempotencyKey)
                        .last("LIMIT 1"));
        if (existing != null) {
            log.debug("Idempotent task hit: taskKey={}, type={}, idempotencyKey={}",
                    existing.getTaskKey(), taskType, idempotencyKey);
            return new ProcessingTask(existing);
        }

        KbProcessingTaskEntity entity = new KbProcessingTaskEntity();
        entity.setTaskKey(keyGenerator.nextKey());
        entity.setWorkspaceId(workspaceId);
        entity.setKnowledgeBaseId(knowledgeBaseId);
        entity.setSourceDocumentId(sourceDocumentId);
        entity.setTaskType(taskType.name());
        entity.setInputRevisionKey(inputRevisionKey);
        entity.setIdempotencyKey(idempotencyKey);
        entity.setStatus(TaskStatus.QUEUED.name());
        entity.setProgress(0);
        entity.setAttempt(0);
        entity.setMaxAttempts(ProcessingTask.DEFAULT_MAX_ATTEMPTS);
        entity.setPayloadJson(payloadJson);
        taskDomainService.save(entity);
        return new ProcessingTask(entity);
    }

    /**
     * 按 taskKey 查询任务。
     *
     * @param taskKey 任务业务标识
     * @return 任务领域模型，不存在返回 null
     */
    public ProcessingTask findByKey(String taskKey) {
        KbProcessingTaskEntity entity = taskDomainService.getOne(
                Wrappers.<KbProcessingTaskEntity>lambdaQuery()
                        .eq(KbProcessingTaskEntity::getTaskKey, taskKey)
                        .last("LIMIT 1"));
        return entity != null ? new ProcessingTask(entity) : null;
    }

    /**
     * 批量读取每个文档最近更新的任务，用于文档页恢复状态观察。
     * 任务输入、执行实例与租约不在此边界返回。
     *
     * @param sourceDocumentIds 文档数据库主键集合
     * @return 以文档主键为 key 的最近任务
     */
    public java.util.Map<Long, KbProcessingTaskEntity> findLatestByDocumentIds(java.util.Collection<Long> sourceDocumentIds) {
        if (sourceDocumentIds == null || sourceDocumentIds.isEmpty()) {
            return java.util.Map.of();
        }
        java.util.Map<Long, KbProcessingTaskEntity> latest = new java.util.LinkedHashMap<>();
        taskDomainService.list(Wrappers.<KbProcessingTaskEntity>lambdaQuery()
                        .in(KbProcessingTaskEntity::getSourceDocumentId, sourceDocumentIds)
                        .orderByDesc(KbProcessingTaskEntity::getUpdated))
                .forEach(task -> latest.putIfAbsent(task.getSourceDocumentId(), task));
        return latest;
    }

    /**
     * CAS 更新任务状态。
     *
     * <p>使用 version 字段做乐观锁，失败时抛出 TaskExecutionException。
     *
     * @param task 任务领域模型
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateTask(ProcessingTask task) {
        KbProcessingTaskEntity entity = task.entity();
        boolean updated = taskDomainService.updateById(entity);
        if (!updated) {
            throw new TaskExecutionException(
                    "CAS failed for task " + entity.getTaskKey() + ", version=" + entity.getVersion());
        }
    }

    /**
     * 扫描候选执行任务（status=QUEUED 且 nextRunAt 已到期）。
     *
     * @param now     当前时间
     * @param batchSize 批量大小
     * @return 候选任务列表
     */
    public List<ProcessingTask> scanCandidates(Date now, int batchSize) {
        List<KbProcessingTaskEntity> entities = taskDomainService.list(
                Wrappers.<KbProcessingTaskEntity>lambdaQuery()
                        .eq(KbProcessingTaskEntity::getStatus, TaskStatus.QUEUED.name())
                        .and(w -> w.isNull(KbProcessingTaskEntity::getNextRunAt)
                                .or().le(KbProcessingTaskEntity::getNextRunAt, now))
                        .orderByAsc(KbProcessingTaskEntity::getCreated)
                        .last("LIMIT " + batchSize));
        return entities.stream().map(ProcessingTask::new).toList();
    }

    /**
     * 扫描 stale RUNNING 任务（executionDeadline 已过期）。
     *
     * @param now      当前时间
     * @param batchSize 批量大小
     * @return stale 任务列表
     */
    public List<ProcessingTask> scanStaleTasks(Date now, int batchSize) {
        List<KbProcessingTaskEntity> entities = taskDomainService.list(
                Wrappers.<KbProcessingTaskEntity>lambdaQuery()
                        .eq(KbProcessingTaskEntity::getStatus, TaskStatus.RUNNING.name())
                        .lt(KbProcessingTaskEntity::getExecutionDeadline, now)
                        .orderByAsc(KbProcessingTaskEntity::getExecutionDeadline)
                        .last("LIMIT " + batchSize));
        return entities.stream().map(ProcessingTask::new).toList();
    }

    /**
     * 在 taskKey 分布式锁内重新读取任务并执行条件状态更新。
     *
     * <p>这是幂等与状态机校验，不是 MySQL 锁。
     *
     * @param taskKey 任务业务标识
     * @return 重新读取的任务，如果状态已变更则返回最新状态
     */
    public ProcessingTask reloadInLock(String taskKey) {
        return findByKey(taskKey);
    }

    /**
     * 判断任务是否可执行（QUEUED 且 nextRunAt 已到期）。
     *
     * @param task 任务领域模型
     * @param now  当前时间
     * @return true 如果可执行
     */
    public boolean isExecutable(ProcessingTask task, Date now) {
        return task.isCandidate(now);
    }
}
