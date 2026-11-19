package com.group4.kaoyan.common.security;

import com.group4.kaoyan.common.BizException;
import com.group4.kaoyan.common.ErrorCode;
import com.group4.kaoyan.modules.user.entity.SysUser;
import com.group4.kaoyan.modules.user.mapper.UserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P2 任务：鉴权链上的账号状态兜底（F 项）单元测试。
 *
 * <p>三个约定：禁用 → 403+10013；正常 → 放行；查库异常 → 放行（fail-open）。</p>
 */
@DisplayName("P2-鉴权链账号状态兜底")
class AccountStatusGuardTest {

    private final UserMapper userMapper = mock(UserMapper.class);
    private final AccountStatusGuard guard = new AccountStatusGuard(userMapper);

    private SysUser user(Integer status) {
        SysUser u = new SysUser();
        u.setId(1L);
        u.setStatus(status);
        return u;
    }

    @Test
    @DisplayName("status=0：拒绝请求，错误码 10013（HTTP 403）")
    void disabledUserIsRejected() {
        when(userMapper.selectById(1L)).thenReturn(user(0));

        BizException e = assertThrows(BizException.class, () -> guard.ensureActive(1L));
        assertEquals(ErrorCode.ACCOUNT_DISABLED, e.getCode());
    }

    @Test
    @DisplayName("status=1：正常放行")
    void activeUserPasses() {
        when(userMapper.selectById(1L)).thenReturn(user(1));
        assertDoesNotThrow(() -> guard.ensureActive(1L));
    }

    @Test
    @DisplayName("账号已被删除：按禁用处理，不放行")
    void missingUserIsTreatedAsDisabled() {
        when(userMapper.selectById(1L)).thenReturn(null);
        assertThrows(BizException.class, () -> guard.ensureActive(1L));
    }

    @Test
    @DisplayName("查库异常：放行（fail-open），不能因一次抖动挡掉全站")
    void dbFailureFailsOpen() {
        when(userMapper.selectById(1L)).thenThrow(new RuntimeException("db down"));
        assertDoesNotThrow(() -> guard.ensureActive(1L));
    }

    @Test
    @DisplayName("缓存：TTL 内不重复查库，evict 后重新查")
    void cacheAvoidsRepeatedQueries() {
        when(userMapper.selectById(1L)).thenReturn(user(1));

        guard.ensureActive(1L);
        guard.ensureActive(1L);
        verify(userMapper, times(1)).selectById(1L);

        guard.evict(1L);
        guard.ensureActive(1L);
        verify(userMapper, times(2)).selectById(1L);
    }

    @Test
    @DisplayName("userId 为 null：直接返回，不查库（未登录由 checkLogin 拦）")
    void nullUserIdSkipsQuery() {
        assertDoesNotThrow(() -> guard.ensureActive(null));
        verify(userMapper, times(0)).selectById(1L);
    }
}
