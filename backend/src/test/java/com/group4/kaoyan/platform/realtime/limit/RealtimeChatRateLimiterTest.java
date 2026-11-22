package com.group4.kaoyan.platform.realtime.limit;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RealtimeChatRateLimiterTest {

    @Test
    @SuppressWarnings("unchecked")
    void firstRequestShouldSetWindowAndRequestsOverLimitShouldFail() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.increment("realtime:chat:rate:7")).thenReturn(1L, 7L);

        RealtimeChatRateLimiter limiter = new RealtimeChatRateLimiter(redis);
        assertTrue(limiter.allow(7L, 6, Duration.ofSeconds(10)));
        verify(redis).expire("realtime:chat:rate:7", Duration.ofSeconds(10));
        assertFalse(limiter.allow(7L, 6, Duration.ofSeconds(10)));
    }

    @Test
    void disabledLimitShouldSkipRedis() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        RealtimeChatRateLimiter limiter = new RealtimeChatRateLimiter(redis);

        assertTrue(limiter.allow(7L, 0, Duration.ofSeconds(10)));
        verify(redis, never()).opsForValue();
    }
}
