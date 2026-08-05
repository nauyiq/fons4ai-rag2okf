package com.fons.cloud.ai.rag2okf.common.exeception;

import com.fons.cloud.common.base.exception.BusinessRuntimeException;
import com.fons.cloud.common.result.ResultCode;

/**
 * 登录频控窗口已触发的异常。
 *
 * @author hongqy
 */
public class AuthenticationRateLimitedException extends BusinessRuntimeException {

    /**
     * 创建不泄露内部频控阈值的异常。
     */
    public AuthenticationRateLimitedException() {
        super(ResultCode.TOO_MANY_REQUEST);
    }

    /**
     * 创建保留底层原因的登录频控异常。
     *
     * @param cause 底层加密或基础设施异常
     */
    public AuthenticationRateLimitedException(Throwable cause) {
        super(ResultCode.TOO_MANY_REQUEST.code, cause);
    }
}
