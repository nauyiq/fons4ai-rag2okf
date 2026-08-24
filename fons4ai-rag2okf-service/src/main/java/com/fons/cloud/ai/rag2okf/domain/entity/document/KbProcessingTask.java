package com.fons.cloud.ai.rag2okf.domain.entity.document;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fons.cloud.ai.rag2okf.common.constants.Rag2OkfResultCode;
import com.fons.cloud.ai.rag2okf.common.constants.document.ProcessingTaskStatus;
import com.fons.cloud.ai.rag2okf.common.constants.document.ProcessingTaskType;
import com.fons.cloud.ai.rag2okf.common.exception.document.DocumentProcessingException;
import com.fons.cloud.ai.rag2okf.common.utils.BusinessKeyGenerator;
import com.fons.cloud.db.entity.CommonEntity;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serial;
import java.util.Date;

/**
 * 异步处理任务、幂等、租约恢复与安全失败原因持久化实体。
 *
 * <p>任务事实底座（设计 §4.4/§5.3）：以
 * {@code (source_document_id, task_type, idempotency_key)} 唯一约束实现幂等创建；
 * {@code snapshot_json} 保存版本化最小快照，只冻结非秘密引用；
 * {@code error_code/error_message} 是安全化失败原因的事实源，结果表不复制。
 * 终态失败后不改写旧任务，通过重新发起新任务并以
 * {@code retry_of_task_id} 关联原失败任务追溯。</p>
 *
 * <p>执行所有权、心跳和截止时间只用于崩溃恢复，不表示数据库锁；
 * 多实例互斥由 Fons4Cloud 分布式锁完成。状态流转规则下沉到
 * {@link #transitionStatus}，持久化由领域服务以条件更新保证并发安全。</p>
 *
 * <p>与旧 {@code KbProcessingTaskEntity} 并存：旧链路退出归 TP-008，
 * 本实体映射重建后的 {@code kb_processing_task} 新表结构。</p>
 *
 * @author hongqy
 */
@Getter
@Setter
@ToString(callSuper = true)
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("kb_processing_task")
public class KbProcessingTask extends CommonEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 异步任务业务标识，每次创建生成，全局唯一。 */
    private String taskKey;

    /** 所属工作空间数据库主键。 */
    private Long workspaceId;

    /** 所属知识库数据库主键。 */
    private Long knowledgeBaseId;

    /** 目标文档主键。 */
    private Long sourceDocumentId;

    /** 任务类型，严格枚举：PARSE、RECHUNK、PUBLISH、DELETE_CLEANUP。 */
    private ProcessingTaskType taskType;

    /** 任务输入 {@code kb_document_result.result_key} 引用，执行前校验仍为当前结果。 */
    private String inputResultKey;

    /** 调用方幂等键，一次用户操作提供一个，参与唯一约束。 */
    private String idempotencyKey;

    /** 重新发起或清理重试时关联的原失败任务主键，用于追溯，首次创建为空。 */
    private Long retryOfTaskId;

    /** 任务快照结构版本，与 {@code snapshot_json} 内 schemaVersion 一致，如 1.0。 */
    private String snapshotVersion;

    /** 任务输入版本化最小快照 JSON，只冻结非秘密引用。 */
    private String snapshotJson;

    /** 任务状态，按 {@link ProcessingTaskStatus} 白名单流转。 */
    private ProcessingTaskStatus status;

    /** 当前执行阶段描述，由各类型执行器按需写入，展示用。 */
    private String stage;

    /** 进度百分比，范围 0 到 100。 */
    private Integer progress;

    /** 已执行次数。 */
    private Integer attempt;

    /** 最大执行次数，按任务类型默认值创建。 */
    private Integer maxAttempts;

    /** 下次候选执行时间，RETRY_WAIT 退避到期时间。 */
    private Date nextRunAt;

    /** 最近一次执行实例标识，不表示数据库锁。 */
    private String executionOwner;

    /** 最近执行心跳时间。 */
    private Date heartbeatAt;

    /** 执行租约恢复期限，超期视为实例崩溃可恢复回 QUEUED，不表示数据库锁。 */
    private Date executionDeadline;

    /** 安全化错误码，任务失败原因事实源。 */
    private String errorCode;

    /** 安全化错误摘要，不保存正文或凭证。 */
    private String errorMessage;

    /**
     * 创建任务实体。
     *
     * <p>初始状态为 QUEUED，进度 0，已执行 0 次，最大执行次数取任务类型
     * 默认值（PARSE/RECHUNK/PUBLISH 为 3，DELETE_CLEANUP 为 10）。
     * 幂等由数据库唯一约束与领域服务查重兜底，不在实体内判断。</p>
     *
     * @param workspaceId      所属工作空间主键
     * @param knowledgeBaseId  所属知识库主键
     * @param sourceDocumentId 目标文档主键
     * @param taskType         任务类型
     * @param inputResultKey   任务输入 result_key 引用，可为 {@code null}
     * @param idempotencyKey   调用方幂等键
     * @param snapshotVersion  快照结构版本，与 snapshot_json 的 schemaVersion 一致
     * @param snapshotJson     版本化最小快照 JSON
     * @param retryOfTaskId    关联的原失败任务主键，首次创建传 {@code null}
     * @return 已初始化的任务实体
     */
    public static KbProcessingTask create(
            Long workspaceId,
            Long knowledgeBaseId,
            Long sourceDocumentId,
            ProcessingTaskType taskType,
            String inputResultKey,
            String idempotencyKey,
            String snapshotVersion,
            String snapshotJson,
            Long retryOfTaskId) {
        KbProcessingTask task = new KbProcessingTask();
        task.setTaskKey(BusinessKeyGenerator.nextKey());
        task.setWorkspaceId(workspaceId);
        task.setKnowledgeBaseId(knowledgeBaseId);
        task.setSourceDocumentId(sourceDocumentId);
        task.setTaskType(taskType);
        task.setInputResultKey(inputResultKey);
        task.setIdempotencyKey(idempotencyKey);
        task.setRetryOfTaskId(retryOfTaskId);
        task.setSnapshotVersion(snapshotVersion);
        task.setSnapshotJson(snapshotJson);
        task.setStatus(ProcessingTaskStatus.QUEUED);
        task.setProgress(0);
        task.setAttempt(0);
        task.setMaxAttempts(taskType.defaultMaxAttempts());
        return task;
    }

    /**
     * 按白名单流转任务状态。
     *
     * <p>只允许设计 §4.5 状态机内的转移：终态不允许出边，旧终态任务
     * 不得改回运行态，重新处理必须创建新任务。持久化时由领域服务以
     * 当前状态为条件做 CAS 更新，防止并发实例互相覆盖。</p>
     *
     * @param target 目标状态
     * @throws DocumentProcessingException 非法转移时抛出，语义为任务已不是调用方持有的状态
     */
    public void transitionStatus(ProcessingTaskStatus target) {
        if (!this.status.canTransitionTo(target)) {
            throw new DocumentProcessingException(Rag2OkfResultCode.TASK_INPUT_SUPERSEDED);
        }
        this.status = target;
    }
}
