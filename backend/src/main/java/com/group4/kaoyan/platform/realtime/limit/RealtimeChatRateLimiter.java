package com.group4.kaoyan.platform.realtime.limit;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 聊天入站固定窗口限流。
 */
@Component
@RequiredArgsConstructor
public class RealtimeChatRateLimiter {

    private static final String KEY_PREFIX = "realtime:chat:rate:";

    private final StringRedisTemplate redisTemplate;

    public boolean allow(Long userId, int limit, Duration window) {
        if (limit <= 0) {
            return true;
        }
        String key = KEY_PREFIX + userId;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count == null) {
            return false;
        }
        if (count == 1L) {
            redisTemplate.expire(key, window);
        }
        return count <= limit;
    }
}
