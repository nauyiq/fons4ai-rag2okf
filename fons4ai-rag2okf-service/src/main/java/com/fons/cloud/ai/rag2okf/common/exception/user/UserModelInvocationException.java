package com.fons.cloud.ai.rag2okf.common.exception.user;

import com.fons.cloud.ai.rag2okf.common.constants.Rag2OkfResultCode;
import com.fons.cloud.common.base.exception.BusinessRuntimeException;

/**
 * 用户域模型能力调用异常。
 *
 * <p>用于 UserModelInvocationService 在调用过程中遇到协议不支持、调用失败、
 * 凭证错误或安全约束违规时抛出。携带 {@link Rag2OkfResultCode} 统一错误码，
 * 不向客户端暴露 API Key、连接 URL 或模型响应全文。</p>
 *
 * <p>与 {@code ModelConfigurationException} 的区别：
 * ModelConfigurationException 用于档案配置无效，使用通用 PARAMS_ERROR 码；
 * 本异常用于调用过程中的能力/协议/网络错误，使用具体的 RF300016~RF500019 码。</p>
 *
 * @author hongqy
 */
public class UserModelInvocationException extends BusinessRuntimeException {

    /**
     * 创建模型调用异常。
     *
     * @param resultCode 用户域错误码，提供安全化的用户语义
     */
    public UserModelInvocationException(Rag2OkfResultCode resultCode) {
        super(resultCode);
    }

    /**
     * 创建保留内部原因的模型调用异常。
     *
     * <p>cause 不向接口响应透出，仅用于日志和排查。</p>
     *
     * @param resultCode 用户域错误码
     * @param cause       底层原因，不暴露给客户端
     */
    public UserModelInvocationException(Rag2OkfResultCode resultCode, Throwable cause) {
        super(resultCode.getCode(), resultCode.getMessage(), cause);
    }
}
