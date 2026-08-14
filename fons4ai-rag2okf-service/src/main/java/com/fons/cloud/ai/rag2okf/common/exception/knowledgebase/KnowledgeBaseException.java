package com.fons.cloud.ai.rag2okf.common.exception.knowledgebase;

import com.fons.cloud.common.base.exception.BusinessRuntimeException;
import com.fons.cloud.common.result.ResultCode;

/**
 * 知识库参数或配置不合法时抛出的异常。
 *
 * <p>不向客户端暴露具体校验细节，统一映射为参数错误。</p>
 *
 * @author hongqy
 */
public class KnowledgeBaseException extends BusinessRuntimeException {

    /**
     * 创建不暴露具体配置细节的参数异常。
     */
    public KnowledgeBaseException() {
        super(ResultCode.PARAMS_ERROR);
    }
}
