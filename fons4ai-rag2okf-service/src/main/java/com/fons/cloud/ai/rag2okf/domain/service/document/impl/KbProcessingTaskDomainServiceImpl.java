package com.fons.cloud.ai.rag2okf.domain.service.document.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fons.cloud.ai.rag2okf.common.constants.Rag2OkfResultCode;
import com.fons.cloud.ai.rag2okf.common.constants.document.ProcessingTaskStatus;
import com.fons.cloud.ai.rag2okf.common.constants.document.ProcessingTaskType;
import com.fons.cloud.ai.rag2okf.common.exception.document.DocumentProcessingException;
import com.fons.cloud.ai.rag2okf.domain.entity.document.KbProcessingTask;
import com.fons.cloud.ai.rag2okf.domain.mapper.document.KbProcessingTaskMapper;
import com.fons.cloud.ai.rag2okf.domain.service.document.KbProcessingTaskDomainService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 文档域异步处理任务领域服务实现。
 *
 * @author hongqy
 */
@Service
public class KbProcessingTaskDomainServiceImpl
        extends ServiceImpl<KbProcessingTaskMapper, KbProcessingTask>
        implements KbProcessingTaskDomainService {

    @Override
    public Map<Long, List<KbProcessingTask>> findLatestBySourceDocumentIds(List<Long> sourceDocumentIds) {
        if (sourceDocumentIds.isEmpty()) {
            return Map.of();
        }
        List<KbProcessingTask> tasks = this.list(Wrappers.<KbProcessingTask>lambdaQuery()
                .in(KbProcessingTask::getSourceDocumentId, sourceDocumentIds)
                .eq(KbProcessingTask::getDeleted, false)
                .orderByDesc(KbProcessingTask::getUpdated)
                .orderByDesc(KbProcessingTask::getId));
        Map<Long, Map<ProcessingTaskType, KbProcessingTask>> latestByDocument = new LinkedHashMap<>();
        for (KbProcessingTask task : tasks) {
            latestByDocument.computeIfAbsent(task.getSourceDocumentId(), ignored -> new LinkedHashMap<>())
                    .putIfAbsent(task.getTaskType(), task);
        }
        Map<Long, List<KbProcessingTask>> latestTasks = new LinkedHashMap<>();
        latestByDocument.forEach((documentId, tasksByType) -> latestTasks.put(documentId,
                tasksByType.values().stream()
                        .sorted(Comparator.comparing(task -> task.getTaskType().ordinal()))
                        .toList()));
        return Map.copyOf(latestTasks);
    }

    @Override
    public KbProcessingTask findByTaskKey(String taskKey) {
        return this.getOne(Wrappers.<KbProcessingTask>lambdaQuery()
                .eq(KbProcessingTask::getDeleted, false)
                .eq(KbProcessingTask::getTaskKey, taskKey));
    }

    @Override
    public KbProcessingTask create(KbProcessingTask task) {
        this.save(task);
        return task;
    }

    @Override
    public void casTransitionStatus(Long taskId, ProcessingTaskStatus from, ProcessingTaskStatus to) {
        if (!from.canTransitionTo(to)) {
            throw new DocumentProcessingException(Rag2OkfResultCode.TASK_INPUT_SUPERSEDED);
        }
        boolean updated = this.update(Wrappers.<KbProcessingTask>lambdaUpdate()
                .set(KbProcessingTask::getStatus, to)
                .eq(KbProcessingTask::getId, taskId)
                .eq(KbProcessingTask::getDeleted, false)
                .eq(KbProcessingTask::getStatus, from));
        if (!updated) {
            throw new DocumentProcessingException(Rag2OkfResultCode.TASK_INPUT_SUPERSEDED);
        }
    }

    @Override
    public KbProcessingTask findLatestFailed(Long sourceDocumentId, ProcessingTaskType taskType) {
        return this.getOne(Wrappers.<KbProcessingTask>lambdaQuery()
                .eq(KbProcessingTask::getDeleted, false)
                .eq(KbProcessingTask::getSourceDocumentId, sourceDocumentId)
                .eq(KbProcessingTask::getTaskType, taskType)
                .eq(KbProcessingTask::getStatus, ProcessingTaskStatus.FAILED)
                .orderByDesc(KbProcessingTask::getId)
                .last("LIMIT 1"));
    }

    @Override
    public List<KbProcessingTask> listQueuedParseTasks(int limit) {
        return listQueuedTasks(ProcessingTaskType.PARSE, limit);
    }

    @Override
    public List<KbProcessingTask> listQueuedChunkTasks(int limit) {
        return listQueuedTasks(ProcessingTaskType.CHUNK, limit);
    }

    @Override
    public List<KbProcessingTask> listQueuedRechunkTasks(int limit) {
        return listQueuedTasks(ProcessingTaskType.RECHUNK, limit);
    }

    private List<KbProcessingTask> listQueuedTasks(ProcessingTaskType type, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return this.list(Wrappers.<KbProcessingTask>lambdaQuery()
                .eq(KbProcessingTask::getDeleted, false)
                .eq(KbProcessingTask::getTaskType, type)
                .eq(KbProcessingTask::getStatus, ProcessingTaskStatus.QUEUED)
                .orderByAsc(KbProcessingTask::getId)
                .last("LIMIT " + safeLimit));
    }

    @Override
    public void failRunningTask(Long taskId, String errorCode, String safeMessage) {
        boolean updated = this.update(Wrappers.<KbProcessingTask>lambdaUpdate()
                .set(KbProcessingTask::getStatus, ProcessingTaskStatus.FAILED)
                .set(KbProcessingTask::getErrorCode, errorCode)
                .set(KbProcessingTask::getErrorMessage, safeMessage)
                .set(KbProcessingTask::getProgress, 100)
                .eq(KbProcessingTask::getId, taskId)
                .eq(KbProcessingTask::getDeleted, false)
                .eq(KbProcessingTask::getStatus, ProcessingTaskStatus.RUNNING));
        if (!updated) {
            throw new DocumentProcessingException(Rag2OkfResultCode.TASK_INPUT_SUPERSEDED);
        }
    }
}
