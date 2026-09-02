package com.fons.cloud.ai.rag2okf.common.response.document;

import com.fons.cloud.ai.rag2okf.common.constants.document.ParserType;
import com.fons.cloud.ai.rag2okf.common.constants.document.ProcessingTaskStatus;
import com.fons.cloud.ai.rag2okf.common.constants.document.ResultStage;

/**
 * 手动解析受理响应，不暴露数据库主键、内部对象键或任务快照。
 *
 * @param taskKey 任务业务标识
 * @param resultStage 新结果当前阶段
 * @param parserType 已冻结解析器类型
 * @param status 任务当前状态
 */
public record DocumentParseResponse(
        String taskKey,
        ResultStage resultStage,
        ParserType parserType,
        ProcessingTaskStatus status) {
}
