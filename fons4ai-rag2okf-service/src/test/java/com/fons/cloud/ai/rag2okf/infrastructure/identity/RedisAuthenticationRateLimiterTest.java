package com.fons.cloud.ai.rag2okf.infrastructure.identity;

import com.fons.cloud.ai.rag2okf.common.exeception.AuthenticationRateLimitedException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Redis 登录频控适配器的行为测试。
 *
 * @author hongqy
 */
class RedisAuthenticationRateLimiterTest {

    @Test
    void shouldSetTheEmailFailureWindowWhenTheFirstFailureIsRecorded() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(1L);
        RedisAuthenticationRateLimiter limiter = limiter(redisTemplate);

        limiter.recordLoginFailure("hongqy@example.com", "127.0.0.1");

        verify(redisTemplate).expire(anyString(), eq(Duration.ofMinutes(15)));
        verify(redisTemplate).expire(anyString(), eq(Duration.ofMinutes(10)));
        verify(valueOperations, times(2)).increment(org.mockito.ArgumentMatchers.argThat(
                key -> !key.contains("hongqy@example.com") && !key.contains("127.0.0.1")
        ));
    }

    @Test
    void shouldRejectBeforePasswordComparisonWhenEmailFailureLimitIsReached() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("5");
        RedisAuthenticationRateLimiter limiter = limiter(redisTemplate);

        assertThatThrownBy(() -> limiter.checkLoginAllowed("hongqy@example.com", "127.0.0.1"))
                .isInstanceOf(AuthenticationRateLimitedException.class);

        verify(valueOperations, never()).increment(anyString());
    }

    private RedisAuthenticationRateLimiter limiter(StringRedisTemplate redisTemplate) {
        RedisAuthenticationRateLimiter limiter = new RedisAuthenticationRateLimiter(redisTemplate);
        ReflectionTestUtils.setField(limiter, "rateLimitSalt", "test-rate-limit-salt");
        return limiter;
    }
}
