package com.group4.kaoyan.modules.auth.service;

import com.group4.kaoyan.modules.auth.entity.LoginLog;
import com.group4.kaoyan.modules.auth.mapper.LoginLogMapper;
import com.group4.kaoyan.modules.auth.support.AuthTexts;
import com.group4.kaoyan.modules.auth.support.ClientRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 登录结果落库（sys_login_log）。
 * 写日志失败不能影响登录主流程，因此这里吞掉异常并降级为 warn 日志。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginLogService {

    private final LoginLogMapper loginLogMapper;

    public void record(Long userId, String account, boolean success, ClientRequest client, String failureReason) {
        try {
            LoginLog entry = new LoginLog();
            entry.setUserId(userId);
            entry.setAccount(AuthTexts.limit(account == null ? "" : account, 128));
            entry.setSuccess(success ? 1 : 0);
            entry.setIpAddress(client == null ? null : AuthTexts.limit(client.ip(), 64));
            entry.setUserAgent(client == null ? null : AuthTexts.limit(client.userAgent(), 512));
            entry.setFailureReason(AuthTexts.limit(failureReason, 255));
            entry.setCreateTime(LocalDateTime.now());
            loginLogMapper.insert(entry);
        } catch (Exception e) {
            log.warn("登录日志写入失败: account={}, error={}", account, e.getMessage());
        }
    }
}
