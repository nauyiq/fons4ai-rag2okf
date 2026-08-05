package com.fons.cloud.ai.rag2okf.common.exeception;

import com.fons.cloud.common.base.exception.BusinessRuntimeException;
import com.fons.cloud.common.result.ResultCode;

/**
 * 知识库乐观锁版本冲突时抛出的异常。
 *
 * <p>当客户端提交的 revision 与服务端当前版本不一致时抛出，映射为 HTTP 409，
 * 提示客户端刷新后重试，不泄露服务端内部状态。</p>
 *
 * @author hongqy
 */
public class KnowledgeBaseConflictException extends BusinessRuntimeException {

    /**
     * 创建不泄露服务端实际版本的并发冲突异常。
     */
    public KnowledgeBaseConflictException() {
        super(ResultCode.FAILED);
    }
}
