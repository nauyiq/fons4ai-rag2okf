package com.fons.cloud.ai.rag2okf.infrastructure.identity;

/**
 * 邮箱密码登录频控的领域抽象。
 *
 * @author hongqy
 */
public interface AuthenticationRateLimiter {

    /**
     * 在密码比对前检查邮箱和来源地址的频控窗口。
     *
     * @param normalizedEmail 规范化邮箱
     * @param clientIp 客户端来源地址
     */
    void checkLoginAllowed(String normalizedEmail, String clientIp);

    /**
     * 记录一次统一的登录失败结果。
     *
     * @param normalizedEmail 规范化邮箱
     * @param clientIp 客户端来源地址
     */
    void recordLoginFailure(String normalizedEmail, String clientIp);

    /**
     * 清除登录成功后的失败计数。
     *
     * @param normalizedEmail 规范化邮箱
     * @param clientIp 客户端来源地址
     */
    void clearLoginFailures(String normalizedEmail, String clientIp);
}
