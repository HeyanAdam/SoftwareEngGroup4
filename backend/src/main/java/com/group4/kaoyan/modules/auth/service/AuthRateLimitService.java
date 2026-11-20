package com.group4.kaoyan.modules.auth.service;

import com.group4.kaoyan.modules.auth.support.AuthException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 认证接口限流（固定窗口计数）。
 *
 * <p>Redis Key：{@code auth:rate:{action}:{identity}}，identity 一般是 IP。
 * 超过阈值抛 10010，并把剩余等待秒数写进 Retry-After 响应头。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthRateLimitService {

    private static final String KEY_PREFIX = "auth:rate:";

    private final StringRedisTemplate redisTemplate;

    public void require(String action, String identity, int limit, Duration window, String message) {
        if (limit <= 0) {
            return;
        }
        String key = KEY_PREFIX + action + ":" + (identity == null ? "unknown" : identity);
        Long count = redisTemplate.opsForValue().increment(key);
        if (count == null) {
            return;
        }
        if (count == 1L) {
            redisTemplate.expire(key, window);
        }
        if (count > limit) {
            long retryAfter = remainingSeconds(key, window);
            log.warn("认证接口触发限流: action={}, identity={}, count={}, limit={}", action, identity, count, limit);
            throw AuthException.tooManyRequests(message, retryAfter);
        }
    }

    private long remainingSeconds(String key, Duration window) {
        Long ttl = redisTemplate.getExpire(key);
        return ttl == null || ttl <= 0 ? Math.max(window.toSeconds(), 1) : ttl;
    }
}
