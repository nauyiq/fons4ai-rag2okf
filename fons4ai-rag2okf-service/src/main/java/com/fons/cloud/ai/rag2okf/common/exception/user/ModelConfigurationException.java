package com.fons.cloud.ai.rag2okf.common.exception.user;

import com.fons.cloud.common.base.exception.BusinessRuntimeException;
import com.fons.cloud.common.result.ResultCode;

/**
 * 用户模型配置、凭证或端点不符合安全约束时抛出的异常。
 *
 * @author hongqy
 */
public class ModelConfigurationException extends BusinessRuntimeException {

    /**
     * 创建不暴露具体配置细节的参数异常。
     */
    public ModelConfigurationException() {
        super(ResultCode.PARAMS_ERROR);
    }

    /**
     * 创建保留内部原因的安全化配置异常。
     *
     * @param cause 底层异常原因，不向接口响应透出
     */
    public ModelConfigurationException(Throwable cause) {
        super(ResultCode.PARAMS_ERROR.code, cause);
    }
}
