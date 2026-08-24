package com.fons.cloud.ai.rag2okf.domain.service.document;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fons.cloud.ai.rag2okf.common.constants.document.ProcessingTaskStatus;
import com.fons.cloud.ai.rag2okf.common.constants.document.ProcessingTaskType;
import com.fons.cloud.ai.rag2okf.common.exception.document.DocumentProcessingException;
import com.fons.cloud.ai.rag2okf.domain.entity.document.KbProcessingTask;

/**
 * 文档域异步处理任务领域服务。
 *
 * <p>只服务任务事实持久化、幂等创建、按业务键查询与状态 CAS 流转，
 * 不包装分布式锁、调度或执行器；实现风格与
 * {@code KbKnowledgeBaseDomainService} 一致。</p>
 *
 * @author hongqy
 */
public interface KbProcessingTaskDomainService extends IService<KbProcessingTask> {

    /**
     * 根据任务业务标识查询任务记录。
     *
     * @param taskKey 任务业务标识
     * @return 任务记录；不存在或已软删除时返回 {@code null}
     */
    KbProcessingTask findByTaskKey(String taskKey);

    /**
     * 按幂等唯一键查询已存在任务。
     *
     * <p>幂等键为 {@code (source_document_id, task_type, idempotency_key)}，
     * 对应数据库唯一约束，最多命中一条。</p>
     *
     * @param sourceDocumentId 目标文档主键
     * @param taskType         任务类型
     * @param idempotencyKey   调用方幂等键
     * @return 已存在任务；无匹配时返回 {@code null}
     */
    KbProcessingTask findIdempotent(Long sourceDocumentId, ProcessingTaskType taskType, String idempotencyKey);

    /**
     * 按知识库与幂等键查询已存在任务（不含文档维度）。
     *
     * <p>用于上传链路的操作幂等判定：上传创建任务前以
     * {@code (knowledge_base_id, task_type, idempotency_key)} 查重，命中说明
     * 相同操作键的上传已经受理过，应返回原结果而非重复创建文档。
     * 与数据库唯一约束 {@code (source_document_id, task_type, idempotency_key)}
     * 不同维度：本查询不做唯一性保证，仅取最近一条。</p>
     *
     * @param knowledgeBaseId 所属知识库主键
     * @param taskType        任务类型
     * @param idempotencyKey  调用方幂等键
     * @return 最近一条匹配任务；无匹配时返回 {@code null}
     */
    KbProcessingTask findByIdempotencyKey(Long knowledgeBaseId, ProcessingTaskType taskType, String idempotencyKey);

    /**
     * 幂等创建任务。
     *
     * <p>先按 {@code (source_document_id, task_type, idempotency_key)} 查重：
     * 已存在则直接返回已有任务，不重复创建；不存在则保存新任务。并发下
     * 两个请求同时通过查重时由数据库唯一约束兜底：保存冲突方重新查询
     * 返回已存在任务，调用方感知为同一结果。</p>
     *
     * @param task 已按 {@link KbProcessingTask#create} 初始化的任务实体
     * @return 实际生效的任务：新创建或幂等命中的已有任务
     */
    KbProcessingTask createIfAbsent(KbProcessingTask task);

    /**
     * 以当前状态为条件 CAS 流转任务状态。
     *
     * <p>先校验状态流转白名单，再执行条件更新：
     * {@code UPDATE kb_processing_task SET status=to WHERE id=? AND status=from}。
     * 条件未命中（任务已被并发实例更新或状态失配）时抛出
     * {@code TASK_INPUT_SUPERSEDED}，不产生半更新；任何执行器不得绕过
     * 本方法以 {@code updateById} 无条件覆盖任务状态。</p>
     *
     * @param taskId 任务主键
     * @param from   调用方持有的期望当前状态
     * @param to     目标状态
     * @throws DocumentProcessingException 状态流转非法或条件更新未命中时抛出
     */
    void casTransitionStatus(Long taskId, ProcessingTaskStatus from, ProcessingTaskStatus to);
}
