package com.fons.cloud.ai.rag2okf.common.exeception;

import com.fons.cloud.common.base.exception.BusinessRuntimeException;
import com.fons.cloud.common.result.ResultCode;

/**
 * 文档产物的安全校验或私有对象存储失败时抛出的统一异常。
 *
 * @author hongqy
 */
public class DocumentArtifactException extends BusinessRuntimeException {

    /**
     * 创建不向调用方暴露内部对象地址或存储细节的参数异常。
     */
    public DocumentArtifactException() {
        super(ResultCode.PARAMS_ERROR);
    }

    /**
     * 创建带安全化消息的存储异常。
     *
     * @param message 安全化错误消息
     */
    public DocumentArtifactException(String message) {
        super(ResultCode.FAILED.code, message);
    }

    /**
     * 创建保留底层原因但不透传敏感细节的存储异常。
     *
     * @param cause 底层失败原因
     */
    public DocumentArtifactException(Throwable cause) {
        super(ResultCode.FAILED.code, cause);
    }

    /**
     * 创建带安全化消息和底层原因的存储异常。
     *
     * @param message 安全化错误消息
     * @param cause   底层失败原因
     */
    public DocumentArtifactException(String message, Throwable cause) {
        super(ResultCode.FAILED.code, message, cause);
    }
}
