package com.fons.cloud.ai.rag2okf.common.response.document;

import com.fons.cloud.ai.rag2okf.common.constants.document.ProcessingTaskStatus;
import com.fons.cloud.ai.rag2okf.common.constants.document.ResultStage;

/** 重新分块任务的安全受理响应。 */
public class DocumentRechunkResponse {

    /** 新创建的任务业务键。 */ private final String taskKey;
    /** 创建任务时的结果阶段。 */ private final ResultStage resultStage;
    /** 任务初始状态。 */ private final ProcessingTaskStatus status;

    /**
     * 创建受理响应。
     *
     * @param taskKey 任务业务键
     * @param resultStage 创建任务时的结果阶段
     * @param status 任务初始状态
     */
    public DocumentRechunkResponse(String taskKey, ResultStage resultStage, ProcessingTaskStatus status) {
        this.taskKey = taskKey;
        this.resultStage = resultStage;
        this.status = status;
    }

    /** @return 任务业务键。 */
    public String getTaskKey() { return taskKey; }
    /** @return 创建任务时的结果阶段。 */
    public ResultStage getResultStage() { return resultStage; }
    /** @return 任务初始状态。 */
    public ProcessingTaskStatus getStatus() { return status; }
}
