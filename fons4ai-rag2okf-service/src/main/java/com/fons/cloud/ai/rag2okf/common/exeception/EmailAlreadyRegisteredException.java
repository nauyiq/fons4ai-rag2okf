package com.fons.cloud.ai.rag2okf.common.exeception;

import com.fons.cloud.common.base.exception.BusinessRuntimeException;
import com.fons.cloud.common.result.ResultCode;

/**
 * 注册邮箱已被占用时的异常。
 *
 * <p>不确认邮箱是否属于某个既有用户，仅表示当前邮箱不可用于注册。</p>
 *
 * @author hongqy
 */
public class EmailAlreadyRegisteredException extends BusinessRuntimeException {

    /**
     * 创建不泄露既有用户身份的邮箱冲突异常。
     */
    public EmailAlreadyRegisteredException() {
        super(ResultCode.PARAMS_ERROR);
    }
}
