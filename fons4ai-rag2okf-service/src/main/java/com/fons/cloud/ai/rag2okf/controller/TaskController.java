package com.fons.cloud.ai.rag2okf.controller;

import com.fons.cloud.ai.rag2okf.application.parsing.ParseApplicationService;
import com.fons.cloud.ai.rag2okf.application.task.TaskApplicationService;
import com.fons.cloud.ai.rag2okf.common.exeception.TaskExecutionException;
import com.fons.cloud.ai.rag2okf.common.response.TaskStatusResponse;
import com.fons.cloud.ai.rag2okf.domain.entity.KbProcessingTaskEntity;
import com.fons.cloud.ai.rag2okf.common.dto.ProcessingTask;
import com.fons.cloud.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 任务状态查询与重试接口（AC-011、AC-014、AC-023、AC-024）。
 *
 * @author hongqy
 */
@RestController
@RequestMapping("/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskApplicationService taskApplicationService;
    private final ParseApplicationService parseApplicationService;

    /**
     * 查询任务状态。
     *
     * @param taskKey 任务业务标识
     * @return 任务状态响应
     */
    @GetMapping("/{taskKey}")
    public R<TaskStatusResponse> getTaskStatus(@PathVariable String taskKey) {
        ProcessingTask task = taskApplicationService.findByKey(taskKey);
        if (task == null) {
            throw new TaskExecutionException("Task not found: " + taskKey);
        }
        KbProcessingTaskEntity entity = task.entity();
        return R.ok(new TaskStatusResponse(
                entity.getTaskKey(),
                entity.getTaskType(),
                entity.getStatus(),
                entity.getStage(),
                entity.getProgress(),
                entity.getAttempt(),
                entity.getMaxAttempts(),
                entity.getErrorCode(),
                entity.getErrorMessage(),
                entity.getCreated(),
                entity.getUpdated()
        ));
    }

    /**
     * 重试任务。根据原输入快照创建下一次尝试（AC-014、AC-024）。
     *
     * @param taskKey 原任务业务标识
     * @return 新任务业务标识
     */
    @PostMapping("/{taskKey}/retry")
    public R<String> retryTask(@PathVariable String taskKey) {
        return R.ok(parseApplicationService.retryTask(taskKey));
    }
}
