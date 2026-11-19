package com.group4.kaoyan.modules.auth;

import com.group4.kaoyan.modules.auth.config.AuthProperties;
import com.group4.kaoyan.modules.auth.service.LoginAttemptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 登录失败计数与锁定规则：连续失败 5 次锁 15 分钟，登录成功清空计数。
 */
@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new LoginAttemptService(redisTemplate, new AuthProperties());
    }

    @Test
    @DisplayName("未锁定时返回 0 秒")
    void shouldReturnZeroWhenNotLocked() {
        when(redisTemplate.getExpire("auth:login:lock:kaoyan_student")).thenReturn(-2L);
        assertEquals(0L, service.lockedSeconds("kaoyan_student"));
    }

    @Test
    @DisplayName("已锁定时返回剩余秒数")
    void shouldReturnRemainingSeconds() {
        when(redisTemplate.getExpire("auth:login:lock:kaoyan_student")).thenReturn(900L);
        assertEquals(900L, service.lockedSeconds("kaoyan_student"));
    }

    @Test
    @DisplayName("第 1 次失败只设置计数窗口，不写锁定标记")
    void shouldNotLockBeforeThreshold() {
        when(valueOperations.increment(anyString())).thenReturn(1L);

        int failures = service.onFailure("kaoyan_student", "10.0.0.1");

        assertEquals(1, failures);
        verify(redisTemplate).expire(eq("auth:login:fail:kaoyan_student"), any(Duration.class));
        verify(valueOperations, org.mockito.Mockito.never())
                .set(eq("auth:login:lock:kaoyan_student"), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("第 4 次失败仍不锁定")
    void shouldStillNotLockAtFourthFailure() {
        when(valueOperations.increment(anyString())).thenReturn(4L);

        assertEquals(4, service.onFailure("kaoyan_student", "10.0.0.1"));
        verify(valueOperations, org.mockito.Mockito.never())
                .set(eq("auth:login:lock:kaoyan_student"), anyString(), any(Duration.class));
        verify(redisTemplate, org.mockito.Mockito.never()).delete("auth:login:fail:kaoyan_student");
    }

    @Test
    @DisplayName("第 5 次失败写入 15 分钟锁定标记")
    void shouldLockOnFifthFailure() {
        when(valueOperations.increment(anyString())).thenReturn(5L);

        int failures = service.onFailure("kaoyan_student", "10.0.0.1");

        assertEquals(5, failures);
        verify(valueOperations).set(eq("auth:login:lock:kaoyan_student"), eq("1"), eq(Duration.ofMinutes(15)));
        verify(redisTemplate).delete("auth:login:fail:kaoyan_student");
    }

    @Test
    @DisplayName("登录成功清空失败计数与锁定标记")
    void shouldClearOnSuccess() {
        service.onSuccess("kaoyan_student", "10.0.0.1");

        verify(redisTemplate).delete("auth:login:fail:kaoyan_student");
        verify(redisTemplate).delete("auth:login:lock:kaoyan_student");
        verify(redisTemplate).delete("auth:login:fail:ip:10.0.0.1");
    }

    @Test
    @DisplayName("账号维度大小写不敏感")
    void shouldNormalizeAccountKey() {
        when(redisTemplate.getExpire("auth:login:lock:kaoyan_student")).thenReturn(60L);
        assertEquals(60L, service.lockedSeconds("  KaoYan_Student "));
    }
}
