package com.group4.kaoyan.modules.auth.service;

import cn.dev33.satoken.stp.StpInterface;
import com.group4.kaoyan.common.security.RbacSnapshotCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Sa-Token 权限数据源（P2 任务 2）。
 *
 * <p>登录时 {@code StpUtil.login(userId)} 把<b>用户主键</b>写进会话，之后每个
 * {@code @SaCheckPermission} / {@code @SaCheckRole} 都会回调本类的两个方法：</p>
 *
 * <pre>
 * 请求 → Sa-Token 取 loginId(=userId) → getPermissionList() → RbacSnapshotCache
 *      → SELECT role_code FROM sys_role  JOIN sys_user_role
 *      → SELECT perm_code FROM sys_permission JOIN sys_role_permission JOIN sys_user_role
 *      → 命中则放行，否则抛 NotPermissionException → 全局异常处理返回 403 + 10008
 * </pre>
 *
 * <p>三个健壮性约定：</p>
 * <ol>
 *   <li><b>loginId 必须是用户主键</b>：用 username 当 loginId 会查不到权限，全站 403；
 *       非数字的 loginId 直接按"无权限"处理，不抛异常污染日志。</li>
 *   <li><b>查库异常时降级为"无权限"（fail-closed）</b>：宁可 403 也不能放行，安全优先。</li>
 *   <li><b>只返回非空权限码</b>：权限码统一来自 {@code PermissionConstants}，
 *       拼错或空串一律过滤，避免和 {@code @SaCheckPermission} 的字符串对不上。</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StpInterfaceImpl implements StpInterface {

    private final RbacSnapshotCache snapshotCache;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        Long userId = parseUserId(loginId);
        if (userId == null) {
            return List.of();
        }
        try {
            return snapshotCache.get(userId).permissions();
        } catch (RuntimeException e) {
            log.error("查询用户 {} 的权限码失败，按无权限处理：{}", userId, e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        Long userId = parseUserId(loginId);
        if (userId == null) {
            return List.of();
        }
        try {
            return snapshotCache.get(userId).roles();
        } catch (RuntimeException e) {
            log.error("查询用户 {} 的角色码失败，按无角色处理：{}", userId, e.getMessage());
            return List.of();
        }
    }

    /**
     * 把 Sa-Token 的 loginId 解析成用户主键。
     *
     * <p>Sa-Token 可能传来 Long / String / 其他包装类型，统一在这里收敛；
     * 解析失败返回 null，调用方据此返回空权限（fail-closed）。</p>
     */
    Long parseUserId(Object loginId) {
        if (loginId == null) {
            return null;
        }
        if (loginId instanceof Number number) {
            return number.longValue();
        }
        String text = loginId.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(text);
        } catch (NumberFormatException e) {
            log.warn("loginId 不是合法的用户主键，无法查询权限：{}", text);
            return null;
        }
    }
}
