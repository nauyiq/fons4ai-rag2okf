package com.fons.cloud.ai.rag2okf.common.exception.user;

import com.fons.cloud.common.base.exception.BusinessRuntimeException;
import com.fons.cloud.common.result.ResultCode;

/**
 * 当前用户资料白名单不符合约束。
 *
 * @author hongqy
 */
public class InvalidUserProfileException extends BusinessRuntimeException {

    /**
     * 创建不暴露具体字段约束的参数异常。
     */
    public InvalidUserProfileException() {
        super(ResultCode.PARAMS_ERROR);
    }
}
