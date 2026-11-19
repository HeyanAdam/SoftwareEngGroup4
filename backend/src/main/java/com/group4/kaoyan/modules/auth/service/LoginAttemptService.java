package com.group4.kaoyan.modules.auth.service;

import com.group4.kaoyan.modules.auth.config.AuthProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * 登录失败计数与临时锁定。
 *
 * <p>Redis Key（与分工文档一致）：</p>
 * <pre>
 * auth:login:fail:{account}     连续失败次数，30 分钟窗口
 * auth:login:lock:{account}     锁定标记，15 分钟
 * auth:login:fail:ip:{ip}       同 IP 失败次数（仅统计，按窗口过期）
 * </pre>
 * 规则：连续失败 5 次锁定 15 分钟；登录成功清空计数与锁定标记。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private static final String FAIL_KEY = "auth:login:fail:%s";
    private static final String LOCK_KEY = "auth:login:lock:%s";
    private static final String IP_FAIL_KEY = "auth:login:fail:ip:%s";

    private final StringRedisTemplate redisTemplate;
    private final AuthProperties authProperties;

    /** 剩余锁定秒数，0 表示未锁定。 */
    public long lockedSeconds(String account) {
        Long ttl = redisTemplate.getExpire(LOCK_KEY.formatted(accountKey(account)));
        return ttl == null || ttl <= 0 ? 0L : ttl;
    }

    /** 记录一次失败，返回当前累计失败次数。 */
    public int onFailure(String account, String ip) {
        String failKey = FAIL_KEY.formatted(accountKey(account));
        Long count = redisTemplate.opsForValue().increment(failKey);
        if (count != null && count == 1L) {
            redisTemplate.expire(failKey, authProperties.getLoginFailureWindow());
        }

        if (ip != null && !ip.isBlank()) {
            String ipFailKey = IP_FAIL_KEY.formatted(ip.toLowerCase(Locale.ROOT));
            Long ipCount = redisTemplate.opsForValue().increment(ipFailKey);
            if (ipCount != null && ipCount == 1L) {
                redisTemplate.expire(ipFailKey, authProperties.getLoginFailureWindow());
            }
        }

        int failures = count == null ? 1 : count.intValue();
        if (failures >= authProperties.getLoginMaxFailures()) {
            redisTemplate.opsForValue().set(LOCK_KEY.formatted(accountKey(account)), "1",
                    authProperties.getLoginLockDuration());
            // 计数清零，解锁后重新开始统计，避免解锁瞬间又被锁
            redisTemplate.delete(failKey);
            log.warn("账号连续登录失败 {} 次，已锁定 {} 分钟: account={}",
                    failures, authProperties.getLoginLockDuration().toMinutes(), account);
        }
        return failures;
    }

    /** 登录成功：清空失败计数与锁定标记。 */
    public void onSuccess(String account, String ip) {
        redisTemplate.delete(FAIL_KEY.formatted(accountKey(account)));
        redisTemplate.delete(LOCK_KEY.formatted(accountKey(account)));
        if (ip != null && !ip.isBlank()) {
            redisTemplate.delete(IP_FAIL_KEY.formatted(ip.toLowerCase(Locale.ROOT)));
        }
    }

    private String accountKey(String account) {
        return account == null ? "unknown" : account.trim().toLowerCase(Locale.ROOT);
    }
}
