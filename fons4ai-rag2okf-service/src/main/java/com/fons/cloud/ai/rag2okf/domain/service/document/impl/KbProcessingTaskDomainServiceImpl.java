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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

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
    public KbProcessingTask findByTaskKey(String taskKey) {
        return this.getOne(Wrappers.<KbProcessingTask>lambdaQuery()
                .eq(KbProcessingTask::getDeleted, false)
                .eq(KbProcessingTask::getTaskKey, taskKey));
    }

    @Override
    public KbProcessingTask findIdempotent(Long sourceDocumentId, ProcessingTaskType taskType, String idempotencyKey) {
        return this.getOne(Wrappers.<KbProcessingTask>lambdaQuery()
                .eq(KbProcessingTask::getDeleted, false)
                .eq(KbProcessingTask::getSourceDocumentId, sourceDocumentId)
                .eq(KbProcessingTask::getTaskType, taskType)
                .eq(KbProcessingTask::getIdempotencyKey, idempotencyKey));
    }

    @Override
    public KbProcessingTask findByIdempotencyKey(Long knowledgeBaseId, ProcessingTaskType taskType, String idempotencyKey) {
        return this.getOne(Wrappers.<KbProcessingTask>lambdaQuery()
                .eq(KbProcessingTask::getDeleted, false)
                .eq(KbProcessingTask::getKnowledgeBaseId, knowledgeBaseId)
                .eq(KbProcessingTask::getTaskType, taskType)
                .eq(KbProcessingTask::getIdempotencyKey, idempotencyKey)
                .orderByDesc(KbProcessingTask::getId)
                .last("LIMIT 1"));
    }

    @Override
    public KbProcessingTask createIfAbsent(KbProcessingTask task) {
        KbProcessingTask existing = this.findIdempotent(
                task.getSourceDocumentId(), task.getTaskType(), task.getIdempotencyKey());
        if (existing != null) {
            return existing;
        }
        try {
            this.save(task);
            return task;
        } catch (DuplicateKeyException duplicateKey) {
            // 并发下另一请求已按相同幂等键抢先创建：唯一约束兜底，返回已存在任务
            return this.findIdempotent(
                    task.getSourceDocumentId(), task.getTaskType(), task.getIdempotencyKey());
        }
    }

    @Override
    public void casTransitionStatus(Long taskId, ProcessingTaskStatus from, ProcessingTaskStatus to) {
        if (!from.canTransitionTo(to)) {
            throw new DocumentProcessingException(Rag2OkfResultCode.TASK_INPUT_SUPERSEDED);
        }
        boolean updated = this.update(Wrappers.<KbProcessingTask>lambdaUpdate()
                .set(KbProcessingTask::getStatus, to)
                .eq(KbProcessingTask::getId, taskId)
                .eq(KbProcessingTask::getStatus, from));
        if (!updated) {
            throw new DocumentProcessingException(Rag2OkfResultCode.TASK_INPUT_SUPERSEDED);
        }
    }
}
