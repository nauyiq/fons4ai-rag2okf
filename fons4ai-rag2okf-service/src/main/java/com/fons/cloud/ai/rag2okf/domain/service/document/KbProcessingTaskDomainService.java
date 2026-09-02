package com.fons.cloud.ai.rag2okf.domain.service.document;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fons.cloud.ai.rag2okf.common.constants.document.ProcessingTaskStatus;
import com.fons.cloud.ai.rag2okf.common.constants.document.ProcessingTaskType;
import com.fons.cloud.ai.rag2okf.common.exception.document.DocumentProcessingException;
import com.fons.cloud.ai.rag2okf.domain.entity.document.KbProcessingTask;

import java.util.List;
import java.util.Map;

/**
 * 文档域异步处理任务领域服务。
 *
 * <p>只服务任务事实持久化、按业务键查询与状态 CAS 流转，
 * 不包装分布式锁、调度或执行器；实现风格与
 * {@code KbKnowledgeBaseDomainService} 一致。</p>
 *
 * @author hongqy
 */
public interface KbProcessingTaskDomainService extends IService<KbProcessingTask> {

    /**
     * 批量查询每个文档、每种任务类型的最近未删除任务。
     *
     * <p>返回任务按任务类型枚举声明顺序排列，并以文档主键分组。</p>
     *
     * @param sourceDocumentIds 源文档主键集合
     * @return 文档主键到每种类型最近任务的索引；无匹配任务时为空 Map
     */
    Map<Long, List<KbProcessingTask>> findLatestBySourceDocumentIds(List<Long> sourceDocumentIds);

    /**
     * 根据任务业务标识查询任务记录。
     *
     * @param taskKey 任务业务标识
     * @return 任务记录；不存在或已软删除时返回 {@code null}
     */
    KbProcessingTask findByTaskKey(String taskKey);

    /**
     * 查询文档指定类型最近一条终态失败任务，供重新发起建立只读追溯关系。
     *
     * @param sourceDocumentId 文档主键
     * @param taskType 任务类型
     * @return 最近失败任务；不存在时返回 {@code null}
     */
    KbProcessingTask findLatestFailed(Long sourceDocumentId, ProcessingTaskType taskType);

    /** 创建独立任务事实。 */
    KbProcessingTask create(KbProcessingTask task);

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

    /** 查询待执行 PARSE 任务，按创建顺序限制数量。 */
    List<KbProcessingTask> listQueuedParseTasks(int limit);

    /** 查询待执行 CHUNK 任务，按创建顺序限制数量。 */
    List<KbProcessingTask> listQueuedChunkTasks(int limit);

    /** 查询待执行 RECHUNK 任务，按创建顺序限制数量。 */
    List<KbProcessingTask> listQueuedRechunkTasks(int limit);

    /** 从 RUNNING 原子失败并登记安全错误。 */
    void failRunningTask(Long taskId, String errorCode, String safeMessage);
}
