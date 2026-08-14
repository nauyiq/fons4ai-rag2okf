package com.fons.cloud.ai.rag2okf.common.exception.user;

import com.fons.cloud.common.base.exception.BusinessRuntimeException;
import com.fons.cloud.common.result.ResultCode;

/**
 * 当前用户不拥有目标模型连接或档案时抛出的异常。
 *
 * @author hongqy
 */
public class ModelAccessDeniedException extends BusinessRuntimeException {

    /**
     * 创建不泄露资源存在性的授权异常。
     */
    public ModelAccessDeniedException() {
        super(ResultCode.NOT_PERMISSION);
    }
}
