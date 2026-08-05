package com.fons.cloud.ai.rag2okf.common.response;

/**
 * 登录成功后的最小会话响应。
 *
 * @param token 客户端后续以 Authentication: Bearer 形式携带的令牌
 * @author hongqy
 */
public record LoginResponse(String token) {
}
