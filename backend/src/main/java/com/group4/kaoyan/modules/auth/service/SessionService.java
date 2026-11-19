package com.group4.kaoyan.modules.auth.service;

import cn.dev33.satoken.stp.StpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 会话吊销工具：密码重置、管理员禁用用户、管理员重置用户都调用同一套方法。
 *
 * <p>Sa-Token 配置为 {@code is-concurrent=true, is-share=false}，同一账号可能有多个 Token，
 * 所以「吊销该用户」必须把所有 Token 全部注销，而不仅是当前这一个。</p>
 */
@Slf4j
@Service
public class SessionService {

    /** 退出登录：只吊销当前请求携带的这个 Token。 */
    public void logoutCurrentSession() {
        StpUtil.logout();
    }

    /**
     * 吊销指定用户的全部登录态（强制下线）。密码重置后必须调用，保证旧 Token 立即失效。
     */
    public void logoutUser(Long userId) {
        if (userId == null) {
            return;
        }
        List<String> tokens = StpUtil.getTokenValueListByLoginId(userId);
        for (String token : tokens) {
            StpUtil.logoutByTokenValue(token);
        }
        // 账号维度再注销一次，覆盖 token 列表读取之后新产生的会话
        StpUtil.logout(userId);
        log.info("已吊销用户全部会话: userId={}, tokenCount={}", userId, tokens.size());
    }

    /** 该用户当前是否还有有效会话。 */
    public boolean isUserLoggedIn(Long userId) {
        if (userId == null) {
            return false;
        }
        return !StpUtil.getTokenValueListByLoginId(userId).isEmpty();
    }
}
