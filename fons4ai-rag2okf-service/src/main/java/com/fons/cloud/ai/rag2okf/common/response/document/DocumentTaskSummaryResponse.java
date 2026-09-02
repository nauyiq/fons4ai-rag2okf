package com.fons.cloud.ai.rag2okf.common.response.document;

import com.fons.cloud.ai.rag2okf.common.constants.document.ProcessingTaskStatus;
import com.fons.cloud.ai.rag2okf.common.constants.document.ProcessingTaskType;

import java.util.Date;

/**
 * 文档最近处理任务的安全摘要。
 *
 * <p>只暴露任务标识、类型、状态、进度与安全化失败信息，不返回任务数据库主键、
 * 快照 JSON、执行实例、租约或任何模型凭证。</p>
 *
 * @param taskKey     任务业务标识
 * @param taskType    任务类型
 * @param status      任务状态
 * @param stage       当前展示阶段
 * @param progress    进度百分比
 * @param errorCode   安全化错误码
 * @param errorMessage 安全化错误摘要
 * @param updatedAt   最近更新时间
 */
public record DocumentTaskSummaryResponse(
        String taskKey,
        ProcessingTaskType taskType,
        ProcessingTaskStatus status,
        String stage,
        Integer progress,
        String errorCode,
        String errorMessage,
        Date updatedAt) {
}
