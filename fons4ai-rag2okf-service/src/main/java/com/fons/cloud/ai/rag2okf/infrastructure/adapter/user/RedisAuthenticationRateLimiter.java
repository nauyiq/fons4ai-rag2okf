package com.fons.cloud.ai.rag2okf.infrastructure.adapter.user;

import com.fons.cloud.ai.rag2okf.common.exception.user.AuthenticationRateLimitedException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;

/**
 * 使用 Redis 保存登录失败窗口的频控适配器。
 *
 * @author hongqy
 */
@Component
@RequiredArgsConstructor
public class RedisAuthenticationRateLimiter {

    private static final Duration EMAIL_WINDOW = Duration.ofMinutes(15);
    private static final Duration IP_WINDOW = Duration.ofMinutes(10);
    private static final int MAX_EMAIL_FAILURES = 5;
    private static final int MAX_IP_FAILURES = 20;


    private final StringRedisTemplate redisTemplate;

    @Value("${sys.security.auth-rate-limit-salt:rag2okf}")
    private String rateLimitSalt;

    /**
     * 在密码比对前检查邮箱和来源地址的频控窗口。
     *
     * @param normalizedEmail 规范化邮箱
     * @param clientIp 客户端来源地址
     */
    public void checkLoginAllowed(String normalizedEmail, String clientIp) {
        if (currentCount(emailKey(normalizedEmail)) >= MAX_EMAIL_FAILURES
                || currentCount(ipKey(clientIp)) >= MAX_IP_FAILURES) {
            throw new AuthenticationRateLimitedException();
        }
    }

    /**
     * 记录一次统一的登录失败结果。
     *
     * @param normalizedEmail 规范化邮箱
     * @param clientIp 客户端来源地址
     */
    public void recordLoginFailure(String normalizedEmail, String clientIp) {
        incrementInWindow(emailKey(normalizedEmail), EMAIL_WINDOW);
        incrementInWindow(ipKey(clientIp), IP_WINDOW);
    }

    /**
     * 清除登录成功后的失败计数。
     *
     * @param normalizedEmail 规范化邮箱
     * @param clientIp 客户端来源地址
     */
    public void clearLoginFailures(String normalizedEmail, String clientIp) {
        redisTemplate.delete(emailKey(normalizedEmail));
        redisTemplate.delete(ipKey(clientIp));
    }

    private long currentCount(String key) {
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return 0;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new AuthenticationRateLimitedException();
        }
    }

    private void incrementInWindow(String key, Duration window) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count == null) {
            throw new AuthenticationRateLimitedException();
        }
        if (count == 1L) {
            redisTemplate.expire(key, window);
        }
    }

    private String emailKey(String normalizedEmail) {
        return "rag2okf:auth:login:email:" + saltedHash(normalizedEmail);
    }

    private String ipKey(String clientIp) {
        return "rag2okf:auth:login:ip:" + saltedHash(clientIp == null ? "unknown" : clientIp);
    }

    private String saltedHash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((rateLimitSalt + ':' + value).getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new AuthenticationRateLimitedException(exception);
        }
    }
}
