package com.group4.kaoyan.modules.auth;

import com.group4.kaoyan.modules.auth.config.AuthProperties;
import com.group4.kaoyan.modules.auth.service.AuthRateLimitService;
import com.group4.kaoyan.modules.auth.service.EmailCodeService;
import com.group4.kaoyan.modules.auth.service.EmailSender;
import com.group4.kaoyan.modules.auth.support.AuthException;
import com.group4.kaoyan.modules.auth.support.ClientRequest;
import com.group4.kaoyan.modules.auth.support.EmailCodePurpose;
import com.group4.kaoyan.modules.auth.vo.SendCodeVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 邮箱验证码：6 位数字、60 秒重发间隔、一次性消费、输错上限作废。 */
@ExtendWith(MockitoExtension.class)
class EmailCodeServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private EmailSender emailSender;

    @Mock
    private AuthRateLimitService rateLimitService;

    private EmailCodeService service;

    private static final ClientRequest CLIENT = new ClientRequest("10.0.0.1", "junit");

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        lenient().when(redisTemplate.getExpire(anyString())).thenReturn(60L);
        service = new EmailCodeService(redisTemplate, new AuthProperties(), emailSender, rateLimitService);
    }

    @Test
    @DisplayName("发送验证码：写入 5 分钟 TTL 的 6 位数字并调用发信")
    void shouldSendSixDigitCode() {
        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);

        SendCodeVO vo = service.send("Student@Example.com", EmailCodePurpose.REGISTER, CLIENT);

        verify(valueOperations).set(eq("auth:code:REGISTER:student@example.com"), codeCaptor.capture(),
                eq(Duration.ofMinutes(5)));
        assertTrue(codeCaptor.getValue().matches("\\d{6}"));
        verify(emailSender).sendVerificationCode("student@example.com", codeCaptor.getValue(),
                EmailCodePurpose.REGISTER);
        assertEquals(300L, vo.getExpiresIn());
        assertEquals(60L, vo.getRetryAfter());
    }

    @Test
    @DisplayName("60 秒内重复发送返回 429，并带剩余等待秒数")
    void shouldRejectFrequentSend() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);
        when(redisTemplate.getExpire(anyString())).thenReturn(45L);

        AuthException e = assertThrows(AuthException.class,
                () -> service.send("student@example.com", EmailCodePurpose.REGISTER, CLIENT));
        assertEquals(10010, e.getCode());
        assertEquals(45L, e.getRetryAfterSeconds());
        verify(emailSender, never()).sendVerificationCode(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("验证码正确时一次性消费成功")
    void shouldConsumeOnce() {
        when(valueOperations.get("auth:code:REGISTER:student@example.com")).thenReturn("482913");

        assertDoesNotThrow(() -> service.consume("student@example.com", EmailCodePurpose.REGISTER, "482913"));
        verify(redisTemplate).delete("auth:code:REGISTER:student@example.com");
    }

    @Test
    @DisplayName("验证码错误返回 10005")
    void shouldRejectWrongCode() {
        when(valueOperations.get("auth:code:REGISTER:student@example.com")).thenReturn("482913");
        when(valueOperations.increment("auth:code:fail:REGISTER:student@example.com")).thenReturn(1L);

        AuthException e = assertThrows(AuthException.class,
                () -> service.consume("student@example.com", EmailCodePurpose.REGISTER, "111111"));
        assertEquals(10005, e.getCode());
    }

    @Test
    @DisplayName("验证码过期返回 10005")
    void shouldRejectExpiredCode() {
        when(valueOperations.get("auth:code:REGISTER:student@example.com")).thenReturn(null);

        AuthException e = assertThrows(AuthException.class,
                () -> service.consume("student@example.com", EmailCodePurpose.REGISTER, "482913"));
        assertEquals(10005, e.getCode());
    }

    @Test
    @DisplayName("输错 5 次后验证码作废")
    void shouldInvalidateAfterMaxAttempts() {
        when(valueOperations.get("auth:code:REGISTER:student@example.com")).thenReturn("482913");
        when(valueOperations.increment(anyString())).thenReturn(5L);

        assertThrows(AuthException.class,
                () -> service.consume("student@example.com", EmailCodePurpose.REGISTER, "111111"));
        verify(redisTemplate).delete("auth:code:REGISTER:student@example.com");
    }

    @Test
    @DisplayName("发信失败时释放邮箱维度重发间隔")
    void shouldReleaseIntervalWhenMailFails() {
        doThrow(new AuthException(10001, org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "邮件服务不可用"))
                .when(emailSender).sendVerificationCode(anyString(), anyString(), any(EmailCodePurpose.class));

        assertThrows(AuthException.class,
                () -> service.send("student@example.com", EmailCodePurpose.RESET_PASSWORD, CLIENT));
        verify(redisTemplate).delete("auth:code:send:email:RESET_PASSWORD:student@example.com");
    }
}
