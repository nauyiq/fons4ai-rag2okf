package com.fons.cloud.ai.rag2okf.common.exception.document;

import com.fons.cloud.ai.rag2okf.common.constants.Rag2OkfResultCode;
import com.fons.cloud.common.base.exception.BusinessRuntimeException;

/**
 * 文档处理过程中的业务异常。
 *
 * <p>使用 {@link Rag2OkfResultCode} 传递稳定错误码和安全化语义；不向客户端暴露文件正文、
 * 模型响应全文、objectKey、连接 URL 或凭证。需要保留底层原因时通过 cause 参数传递，
 * 且不向接口响应透出。</p>
 *
 * @author hongqy
 */
public class DocumentProcessingException extends BusinessRuntimeException {

    /**
     * 创建文档处理异常。
     *
     * @param resultCode 文档域错误码，提供安全化的用户语义
     */
    public DocumentProcessingException(Rag2OkfResultCode resultCode) {
        super(resultCode);
    }

    /**
     * 创建保留内部原因的文档处理异常。
     *
     * <p>cause 不向接口响应透出，仅用于日志和排查。</p>
     *
     * @param resultCode 文档域错误码，提供安全化的用户语义
     * @param cause       底层原因，不暴露给客户端
     */
    public DocumentProcessingException(Rag2OkfResultCode resultCode, Throwable cause) {
        super(resultCode.getCode(), resultCode.getMessage(), cause);
    }
}
