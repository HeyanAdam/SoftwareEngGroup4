package com.group4.kaoyan.modules.audit.service;

import com.group4.kaoyan.common.web.RequestContext;
import com.group4.kaoyan.modules.audit.entity.LoginLog;
import com.group4.kaoyan.modules.audit.mapper.LoginLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 登录日志写入。
 *
 * <p>REQUIRES_NEW：登录失败/回滚不能把日志一起带走；
 * try/catch：日志写失败也绝不能阻断登录。所以这里两层都做了。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginLogService {

    private static final int ACCOUNT_MAX = 128;
    private static final int UA_MAX = 512;
    private static final int REASON_MAX = 255;

    private final LoginLogMapper loginLogMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void record(Long userId, String account, boolean success, String failureReason) {
        try {
            LoginLog entry = new LoginLog();
            entry.setUserId(userId);
            entry.setAccount(RequestContext.truncate(account == null ? "" : account.trim(), ACCOUNT_MAX));
            entry.setSuccess(success ? 1 : 0);
            entry.setIpAddress(RequestContext.clientIp());
            entry.setUserAgent(RequestContext.userAgent(UA_MAX));
            entry.setFailureReason(RequestContext.truncate(failureReason, REASON_MAX));
            entry.setCreateTime(LocalDateTime.now());
            loginLogMapper.insert(entry);
        } catch (Exception e) {
            // 日志属于旁路，失败只告警
            log.warn("写入登录日志失败，已忽略：{}", e.getMessage());
        }
    }

    public List<LoginLog> recent(String account, int limit) {
        int size = Math.min(Math.max(limit, 1), 100);
        return loginLogMapper.selectRecent(account, size);
    }
}
