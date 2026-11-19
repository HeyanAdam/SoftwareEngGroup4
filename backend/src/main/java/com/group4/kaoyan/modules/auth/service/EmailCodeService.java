package com.group4.kaoyan.modules.auth.service;

import com.group4.kaoyan.modules.auth.config.AuthProperties;
import com.group4.kaoyan.modules.auth.support.AuthException;
import com.group4.kaoyan.modules.auth.support.AuthTexts;
import com.group4.kaoyan.modules.auth.support.ClientRequest;
import com.group4.kaoyan.modules.auth.support.EmailCodePurpose;
import com.group4.kaoyan.modules.auth.vo.SendCodeVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;

/**
 * 邮箱验证码：生成 6 位数字码、发信、频率控制、一次性消费。
 *
 * <p>Redis Key（与分工文档一致）：</p>
 * <pre>
 * auth:code:{purpose}:{email}              验证码本体，5 分钟 TTL
 * auth:code:send:email:{purpose}:{email}   同邮箱同用途重发间隔，60 秒
 * auth:code:send:ip:{ip}                   同 IP 重发间隔，60 秒
 * auth:code:fail:{purpose}:{email}         输错次数，超过上限作废
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailCodeService {

    private static final String CODE_KEY = "auth:code:%s:%s";
    private static final String EMAIL_INTERVAL_KEY = "auth:code:send:email:%s:%s";
    private static final String IP_INTERVAL_KEY = "auth:code:send:ip:%s";
    private static final String FAIL_KEY = "auth:code:fail:%s:%s";

    private static final SecureRandom RANDOM = new SecureRandom();
    /** 同 IP 每小时最多发送多少封验证码邮件 */
    private static final int IP_HOURLY_LIMIT = 10;

    private final StringRedisTemplate redisTemplate;
    private final AuthProperties authProperties;
    private final EmailSender emailSender;
    private final AuthRateLimitService rateLimitService;

    public SendCodeVO send(String rawEmail, EmailCodePurpose purpose, ClientRequest client) {
        String email = AuthTexts.normalizeEmail(rawEmail);
        String ip = client.ipKey();

        requireInterval(EMAIL_INTERVAL_KEY.formatted(purpose.name(), email),
                authProperties.getEmailCodeInterval(),
                "验证码发送过于频繁，请 %d 秒后再试");
        requireInterval(IP_INTERVAL_KEY.formatted(ip),
                authProperties.getEmailCodeIpInterval(),
                "验证码发送过于频繁，请 %d 秒后再试");
        rateLimitService.require("email-code-ip", ip, IP_HOURLY_LIMIT, Duration.ofHours(1),
                "该网络今日验证码请求过多");

        String code = generateCode();
        String codeKey = CODE_KEY.formatted(purpose.name(), email);
        redisTemplate.opsForValue().set(codeKey, code, authProperties.getEmailCodeTtl());
        redisTemplate.delete(FAIL_KEY.formatted(purpose.name(), email));

        try {
            emailSender.sendVerificationCode(email, code, purpose);
        } catch (RuntimeException e) {
            // 发信失败时释放邮箱维度的重发间隔，让用户可以立刻重试
            redisTemplate.delete(EMAIL_INTERVAL_KEY.formatted(purpose.name(), email));
            throw e;
        }

        SendCodeVO vo = new SendCodeVO();
        vo.setExpiresIn(authProperties.getEmailCodeTtl().toSeconds());
        vo.setRetryAfter(authProperties.getEmailCodeInterval().toSeconds());
        return vo;
    }

    /**
     * 一次性消费验证码：校验通过即删除，失败累计次数。
     */
    public void consume(String rawEmail, EmailCodePurpose purpose, String code) {
        String email = AuthTexts.normalizeEmail(rawEmail);
        if (code == null || code.isBlank()) {
            throw AuthException.codeInvalid("验证码不能为空");
        }
        String codeKey = CODE_KEY.formatted(purpose.name(), email);
        String failKey = FAIL_KEY.formatted(purpose.name(), email);
        String expected = redisTemplate.opsForValue().get(codeKey);
        if (expected == null) {
            throw AuthException.codeInvalid("验证码错误或已过期，请重新获取");
        }
        if (!expected.equals(code.trim())) {
            Long attempts = redisTemplate.opsForValue().increment(failKey);
            if (attempts != null && attempts == 1L) {
                redisTemplate.expire(failKey, authProperties.getEmailCodeTtl());
            }
            if (attempts != null && attempts >= authProperties.getEmailCodeMaxAttempts()) {
                redisTemplate.delete(codeKey);
                redisTemplate.delete(failKey);
                throw AuthException.codeInvalid("验证码错误次数过多，请重新获取");
            }
            throw AuthException.codeInvalid("验证码错误");
        }
        redisTemplate.delete(codeKey);
        redisTemplate.delete(failKey);
    }

    private String generateCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    private void requireInterval(String key, Duration interval, String messageTemplate) {
        Boolean absent = redisTemplate.opsForValue().setIfAbsent(key, "1", interval);
        if (Boolean.FALSE.equals(absent)) {
            Long ttl = redisTemplate.getExpire(key);
            long wait = ttl == null || ttl <= 0 ? Math.max(interval.toSeconds(), 1) : ttl;
            throw AuthException.tooManyRequests(String.format(messageTemplate, wait), wait);
        }
    }
}
