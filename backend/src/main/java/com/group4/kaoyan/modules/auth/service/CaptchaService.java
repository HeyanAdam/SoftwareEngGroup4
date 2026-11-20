package com.group4.kaoyan.modules.auth.service;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import cn.hutool.core.util.IdUtil;
import com.group4.kaoyan.modules.auth.config.AuthProperties;
import com.group4.kaoyan.modules.auth.support.AuthException;
import com.group4.kaoyan.modules.auth.vo.CaptchaVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * 图形验证码：生成、校验、失败次数控制。
 *
 * <p>Redis Key：{@code auth:captcha:{captcha_key}}，答案以明文保存在 Redis（5 分钟 TTL），
 * 三次输错即作废，登录成功后立即清理。图片只以 Base64 返回，答案永不出现在响应里。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CaptchaService {

    public static final String CAPTCHA_KEY_PREFIX = "auth:captcha:";
    private static final String FAIL_KEY_PREFIX = "auth:captcha:fail:";

    private static final int WIDTH = 130;
    private static final int HEIGHT = 44;
    private static final int CODE_COUNT = 4;
    private static final int LINE_COUNT = 30;

    private final StringRedisTemplate redisTemplate;
    private final AuthProperties authProperties;

    public CaptchaVO generate() {
        LineCaptcha captcha = CaptchaUtil.createLineCaptcha(WIDTH, HEIGHT, CODE_COUNT, LINE_COUNT);
        String captchaKey = "cap_" + IdUtil.fastSimpleUUID().substring(0, 16);

        redisTemplate.opsForValue().set(CAPTCHA_KEY_PREFIX + captchaKey,
                captcha.getCode(), authProperties.getCaptchaTtl());

        CaptchaVO vo = new CaptchaVO();
        vo.setCaptchaKey(captchaKey);
        vo.setImageBase64(captcha.getImageBase64Data());
        vo.setExpiresIn(authProperties.getCaptchaTtl().toSeconds());
        return vo;
    }

    /**
     * 校验图形验证码，失败抛 10005；校验通过后立即消费（一次性使用）。
     */
    public void verify(String captchaKey, String captchaCode) {
        if (captchaKey == null || captchaKey.isBlank() || captchaCode == null || captchaCode.isBlank()) {
            throw AuthException.codeInvalid("请输入图形验证码");
        }
        String key = CAPTCHA_KEY_PREFIX + captchaKey.trim();
        String expected = redisTemplate.opsForValue().get(key);
        if (expected == null) {
            throw AuthException.codeInvalid("图形验证码已过期，请刷新后重试");
        }
        if (!expected.equalsIgnoreCase(captchaCode.trim().toUpperCase(Locale.ROOT))) {
            onFailure(captchaKey.trim(), key, expected);
            throw AuthException.codeInvalid("图形验证码错误");
        }
        redisTemplate.delete(key);
        redisTemplate.delete(FAIL_KEY_PREFIX + captchaKey.trim());
    }

    private void onFailure(String captchaKey, String answerKey, String expected) {
        String failKey = FAIL_KEY_PREFIX + captchaKey;
        Long attempts = redisTemplate.opsForValue().increment(failKey);
        if (attempts != null && attempts == 1L) {
            // 失败计数与验证码同寿命，避免长期占用 Redis
            redisTemplate.expire(failKey, authProperties.getCaptchaTtl());
        }
        if (attempts != null && attempts >= authProperties.getCaptchaMaxAttempts()) {
            redisTemplate.delete(answerKey);
            redisTemplate.delete(failKey);
            log.info("图形验证码输错次数达到上限，已作废: captchaKey={}", captchaKey);
        }
    }
}
