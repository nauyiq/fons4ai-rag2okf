package com.fons.cloud.ai.rag2okf.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fons.cloud.db.entity.CommonEntity;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serial;
import java.util.Date;

/**
 * 异步处理、幂等和崩溃恢复事实持久化实体。
 *
 * <p>执行所有权、心跳和截止时间只用于任务恢复，不表示数据库锁；
 * 多实例互斥必须由 Fons4Cloud 分布式锁完成。</p>
 *
 * @author hongqy
 */
@Getter
@Setter
@ToString(callSuper = true)
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("kb_processing_task")
public class KbProcessingTaskEntity extends CommonEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 异步任务业务标识。 */
    private String taskKey;

    /** 所属工作空间数据库主键。 */
    private Long workspaceId;

    /** 所属知识库数据库主键。 */
    private Long knowledgeBaseId;

    /** 目标源文档数据库主键。 */
    private Long sourceDocumentId;

    /** 任务类型：PARSE、RECHUNK 或 PUBLISH。 */
    private String taskType;

    /** 任务输入 Revision 业务标识。 */
    private String inputRevisionKey;

    /** 调用方幂等键。 */
    private String idempotencyKey;

    /** 任务状态。 */
    private String status;

    /** 当前执行阶段。 */
    private String stage;

    /** 进度百分比，范围 0 到 100。 */
    private Integer progress;

    /** 已执行次数。 */
    private Integer attempt;

    /** 最大执行次数。 */
    private Integer maxAttempts;

    /** 下次候选执行时间。 */
    private Date nextRunAt;

    /** 最近一次执行实例标识，不表示数据库锁。 */
    private String executionOwner;

    /** 最近执行心跳时间。 */
    private Date heartbeatAt;

    /** 执行租约恢复期限，不表示数据库锁。 */
    private Date executionDeadline;

    /** 安全化错误码。 */
    private String errorCode;

    /** 安全化错误摘要，不保存正文或凭证。 */
    private String errorMessage;

    /** 任务输入 JSON 快照。 */
    private String payloadJson;
}
