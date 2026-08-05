package com.fons.cloud.ai.rag2okf.common.exeception;

import com.fons.cloud.common.base.exception.BusinessRuntimeException;
import com.fons.cloud.common.result.ResultCode;

/**
 * 任务状态机非法流转或分布式锁不可用时抛出。
 *
 * @author hongqy
 */
public class TaskExecutionException extends BusinessRuntimeException {

    public TaskExecutionException(String message) {
        super(ResultCode.FAILED.getCode(), message);
    }

    public TaskExecutionException() {
        super(ResultCode.FAILED);
    }
}
