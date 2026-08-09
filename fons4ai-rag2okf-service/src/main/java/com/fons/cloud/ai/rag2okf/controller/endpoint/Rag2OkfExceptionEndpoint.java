package com.fons.cloud.ai.rag2okf.controller.endpoint;

import cn.dev33.satoken.exception.NotLoginException;
import com.fons.cloud.ai.rag2okf.common.exeception.AuthenticationDeniedException;
import com.fons.cloud.ai.rag2okf.common.exeception.AuthenticationRateLimitedException;
import com.fons.cloud.ai.rag2okf.common.exeception.InvalidUserProfileException;
import com.fons.cloud.ai.rag2okf.common.exeception.KnowledgeBaseConflictException;
import com.fons.cloud.ai.rag2okf.common.exeception.KnowledgeBaseException;
import com.fons.cloud.ai.rag2okf.common.exeception.ModelAccessDeniedException;
import com.fons.cloud.ai.rag2okf.common.exeception.ModelConfigurationException;
import com.fons.cloud.ai.rag2okf.common.exeception.WorkspaceAccessDeniedException;
import com.fons.cloud.common.result.R;
import com.fons.cloud.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Rag2OKF HTTP 接口的统一异常响应端点。
 *
 * <p>只输出 Fons4Cloud 标准 {@link R}，不向客户端暴露账号、频控、令牌或内部异常细节。</p>
 *
 * @author hongqy
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class Rag2OkfExceptionEndpoint {

    /**
     * 收敛业务认证失败。
     *
     * @param exception 认证失败
     * @return 统一未认证响应
     */
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    @ExceptionHandler(AuthenticationDeniedException.class)
    public R<Void> handleAuthenticationDenied(AuthenticationDeniedException exception) {
        return R.failed(ResultCode.INVALID_ACCESS_TOKEN);
    }

    /**
     * 收敛 Sa-Token 缺失或失效的会话。
     *
     * @param exception Sa-Token 登录异常
     * @return 统一未认证响应
     */
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    @ExceptionHandler(NotLoginException.class)
    public R<Void> handleNotLogin(NotLoginException exception) {
        return R.failed(ResultCode.INVALID_ACCESS_TOKEN);
    }

    /**
     * 收敛工作空间授权失败。
     *
     * @param exception 授权失败
     * @return 统一无权限响应
     */
    @ResponseStatus(HttpStatus.FORBIDDEN)
    @ExceptionHandler({WorkspaceAccessDeniedException.class, ModelAccessDeniedException.class})
    public R<Void> handleWorkspaceAccessDenied(RuntimeException exception) {
        return R.failed(ResultCode.NOT_PERMISSION);
    }

    /**
     * 返回不泄露阈值的登录频控结果。
     *
     * @param exception 登录频控异常
     * @return 统一限流响应
     */
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    @ExceptionHandler(AuthenticationRateLimitedException.class)
    public R<Void> handleRateLimited(AuthenticationRateLimitedException exception) {
        return R.failed(ResultCode.TOO_MANY_REQUEST);
    }

    /**
     * 收敛资料白名单与请求体格式错误。
     *
     * @param exception 参数异常
     * @return 统一参数错误响应
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler({InvalidUserProfileException.class, ModelConfigurationException.class,
            KnowledgeBaseException.class, HttpMessageNotReadableException.class})
    public R<Void> handleInvalidRequest(Exception exception) {
        return R.failed(ResultCode.PARAMS_ERROR);
    }

    /**
     * 收敛知识库乐观锁版本冲突。
     *
     * @param exception 并发冲突异常
     * @return 统一冲突响应
     */
    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler(KnowledgeBaseConflictException.class)
    public R<Void> handleKnowledgeBaseConflict(KnowledgeBaseConflictException exception) {
        return R.failed(ResultCode.FAILED);
    }

    /**
     * 兜底收敛未预期的运行时异常，避免返回实现细节或堆栈。
     *
     * @param exception 未预期运行时异常
     * @return 统一系统错误响应
     */
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(RuntimeException.class)
    public R<Void> handleSystemFailure(RuntimeException exception) {
        log.error("系统内部错误（T031 PATCH 诊断）", exception);
        return R.failed(ResultCode.SYSTEM_INTERVAL_ERROR);
    }
}
