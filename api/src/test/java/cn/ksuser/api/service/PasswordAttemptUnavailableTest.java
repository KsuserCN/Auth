package cn.ksuser.api.service;

import cn.ksuser.api.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import static cn.ksuser.api.service.RateLimitService.PasswordAttemptLimit.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PasswordAttemptUnavailableTest {
    @Test @SuppressWarnings("unchecked") void nullOrUnknownLuaResultDoesNotFallBackToNonAtomicReads() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(null, -1L, 99L);
        RateLimitService limits = new RateLimitService(redis, new AppProperties());
        for (int i = 0; i < 3; i++) assertEquals(UNAVAILABLE, limits.acquirePasswordAttempt("user:7", "192.0.2.1", null));
        verify(redis, never()).opsForValue();
    }

    @Test @SuppressWarnings("unchecked") void redisOutageDoesNotFallBackToCounterReads() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
            .thenThrow(new RedisConnectionFailureException("fixture unavailable"));
        RateLimitService limits = new RateLimitService(redis, new AppProperties());
        assertEquals(UNAVAILABLE, limits.acquirePasswordAttempt("user:7", "192.0.2.1", null));
        verify(redis, never()).opsForValue();
    }
}
