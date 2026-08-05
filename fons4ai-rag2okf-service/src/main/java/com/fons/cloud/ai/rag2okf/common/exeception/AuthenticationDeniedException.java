package com.fons.cloud.ai.rag2okf.common.exeception;

import com.fons.cloud.common.base.exception.BusinessRuntimeException;
import com.fons.cloud.common.result.ResultCode;

/**
 * 不区分账号不存在、密码错误和账号不可用的统一认证失败。
 *
 * @author hongqy
 */
public class AuthenticationDeniedException extends BusinessRuntimeException {

    /**
     * 创建不含敏感原因的统一认证失败异常。
     */
    public AuthenticationDeniedException() {
        super(ResultCode.INVALID_ACCESS_TOKEN);
    }
}
