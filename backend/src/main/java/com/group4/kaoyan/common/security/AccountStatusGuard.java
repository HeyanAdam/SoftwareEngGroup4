package com.group4.kaoyan.common.security;

import com.group4.kaoyan.common.BizException;
import com.group4.kaoyan.common.ErrorCode;
import com.group4.kaoyan.modules.user.entity.SysUser;
import com.group4.kaoyan.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 账号状态兜底（P2 任务 2 的鉴权链补齐）。
 *
 * <p><b>要解决什么问题：</b>Sa-Token 的已登录态存在 Redis 里，登录之后<b>不再查库</b>。
 * 所以「管理员把某账号禁用」这件事，光靠 {@code AdminService.updateStatus} 里的
 * {@code StpUtil.kickout} 是不够的：kickout 只处理<b>当前这一个会话</b>，
 * 多端登录的其他会话、或 kickout 本身失败（异常被 catch 掉只记日志）时，
 * 被禁用的账号仍然能拿旧 Token 继续调接口 —— 这是"已禁用但还能用"的越权漏洞。</p>
 *
 * <p><b>兜底策略：</b>在鉴权链里（{@code WebConfig} 的 SaInterceptor，登录校验通过之后）
 * 每次请求确认一次账号状态，{@code status == 0} 直接抛
 * {@link ErrorCode#ACCOUNT_DISABLED}（HTTP 403 + 10013），与登录入口的错误码保持一致。</p>
 *
 * <p><b>性能：</b>不直接每请求查库，进程内缓存 60 秒；后台改状态时
 * {@code AdminService.updateStatus} 会主动 {@link #evict(Long)}，所以"刚禁用就立刻生效"，
 * 不依赖 TTL。查库异常按<b>放行</b>处理（fail-open）：状态未知不该把全站用户挡在门外，
 * 真正的权限校验仍然在 {@code @SaCheckPermission} 那层把关。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountStatusGuard {

    /** 状态缓存有效期：60 秒（后台改状态会主动失效，这个 TTL 只是兜底）。 */
    private static final long TTL_MILLIS = 60 * 1000L;

    /** 数据库异常时用于标记"状态未知"，避免把一次抖动缓存成禁用。 */
    private static final int UNKNOWN = -1;

    private final UserMapper userMapper;
    private final Map<Long, Entry> cache = new ConcurrentHashMap<>();

    /**
     * 确认账号处于启用状态，否则抛 403 + 10013。
     *
     * @param userId 当前登录用户主键；为 null 时直接返回（未登录由 checkLogin 拦截）
     */
    public void ensureActive(Long userId) {
        if (userId == null) {
            return;
        }
        int status = statusOf(userId);
        if (status == 0) {
            log.info("审计：账号 {} 已被禁用，拒绝本次请求", userId);
            throw new BizException(ErrorCode.ACCOUNT_DISABLED, "账号已被禁用，请联系管理员");
        }
    }

    /** 账号状态变更后立即失效缓存（由 AdminService.updateStatus 调用）。 */
    public void evict(Long userId) {
        if (userId != null) {
            cache.remove(userId);
        }
    }

    private int statusOf(Long userId) {
        long now = System.currentTimeMillis();
        Entry cached = cache.get(userId);
        if (cached != null && cached.expireAt() > now) {
            return cached.status();
        }
        int fresh = load(userId);
        cache.put(userId, new Entry(fresh, now + TTL_MILLIS));
        return fresh;
    }

    private int load(Long userId) {
        try {
            SysUser user = userMapper.selectById(userId);
            if (user == null || user.getStatus() == null) {
                // 账号已被物理删除：按禁用处理，宁可不放行
                return 0;
            }
            return user.getStatus();
        } catch (RuntimeException e) {
            log.error("查询用户 {} 的账号状态失败，本次按放行处理：{}", userId, e.getMessage());
            return UNKNOWN;
        }
    }

    private record Entry(int status, long expireAt) {
    }
}
