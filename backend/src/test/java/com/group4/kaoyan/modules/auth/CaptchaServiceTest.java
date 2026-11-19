package com.group4.kaoyan.modules.auth;

import com.group4.kaoyan.modules.auth.config.AuthProperties;
import com.group4.kaoyan.modules.auth.service.CaptchaService;
import com.group4.kaoyan.modules.auth.support.AuthException;
import com.group4.kaoyan.modules.auth.vo.CaptchaVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 图形验证码：生成、成功校验、错误校验、输错上限作废。 */
@ExtendWith(MockitoExtension.class)
class CaptchaServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private CaptchaService service;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new CaptchaService(redisTemplate, new AuthProperties());
    }

    @Test
    @DisplayName("生成验证码返回 captcha_key、Base64 图片和 5 分钟有效期")
    void shouldGenerateCaptcha() {
        CaptchaVO vo = service.generate();

        assertTrue(vo.getCaptchaKey().startsWith("cap_"));
        assertTrue(vo.getImageBase64().startsWith("data:image/png;base64,"));
        assertEquals(300L, vo.getExpiresIn());
        verify(valueOperations).set(startsWith("auth:captcha:"), anyString(), eq(Duration.ofMinutes(5)));
    }

    @Test
    @DisplayName("答案正确（大小写不敏感）时校验通过并清理 Key")
    void shouldVerifyCaseInsensitive() {
        when(valueOperations.get("auth:captcha:cap_x")).thenReturn("7K9P");

        assertDoesNotThrow(() -> service.verify("cap_x", "7k9p"));
        verify(redisTemplate).delete("auth:captcha:cap_x");
    }

    @Test
    @DisplayName("验证码过期返回 10005")
    void shouldRejectExpiredCaptcha() {
        when(valueOperations.get("auth:captcha:cap_x")).thenReturn(null);

        AuthException e = assertThrows(AuthException.class, () -> service.verify("cap_x", "7k9p"));
        assertEquals(10005, e.getCode());
    }

    @Test
    @DisplayName("答案错误返回 10005，并累加失败次数")
    void shouldRejectWrongCode() {
        when(valueOperations.get("auth:captcha:cap_x")).thenReturn("7K9P");
        when(valueOperations.increment("auth:captcha:fail:cap_x")).thenReturn(1L);

        AuthException e = assertThrows(AuthException.class, () -> service.verify("cap_x", "0000"));
        assertEquals(10005, e.getCode());
        verify(redisTemplate).expire(eq("auth:captcha:fail:cap_x"), any(Duration.class));
    }

    @Test
    @DisplayName("输错达到 3 次作废验证码")
    void shouldInvalidateAfterMaxAttempts() {
        when(valueOperations.get("auth:captcha:cap_x")).thenReturn("7K9P");
        when(valueOperations.increment("auth:captcha:fail:cap_x")).thenReturn(3L);

        assertThrows(AuthException.class, () -> service.verify("cap_x", "0000"));
        verify(redisTemplate).delete("auth:captcha:cap_x");
        verify(redisTemplate).delete("auth:captcha:fail:cap_x");
    }
}
