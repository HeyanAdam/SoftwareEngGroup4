package com.group4.kaoyan.common.security;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpUtil;
import com.group4.kaoyan.common.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 当前登录用户的身份与权限查询工具（P2 任务 2 的对外门面）。
 *
 * <p>业务代码（P3 的接口层、P1 的登录态判断）不要自己拼 SQL 查 RBAC，
 * 也不要直接用 {@code StpUtil.getLoginIdAsLong()}：统一走这里，登录态、userId
 * 解析、权限快照缓存的口径就只有一个。</p>
 *
 * <pre>
 * 注解式（推荐，声明在 Controller 方法上）：
 *   &#64;SaCheckPermission("kb:view")
 *
 * 代码式（分支逻辑里判断，如"本人才能改"）：
 *   if (!SecurityUtils.hasPermission("study-plan:update")) { ... }
 * </pre>
 *
 * <p>设计说明：本类所有方法都是静态的，但依赖由 Spring 在启动时注入一次
 * （{@link #SecurityUtils(RbacSnapshotCache)}），静态方法里拿的是同一个缓存实例，
 * 保证和 {@code StpInterfaceImpl} 看到的是同一份快照，不会出现"注解放行、代码判不过"。</p>
 */
@Slf4j
@Component
public class SecurityUtils {

    /** Spring 启动时注入一次，静态方法共用 */
    private static RbacSnapshotCache snapshotCache;

    public SecurityUtils(RbacSnapshotCache snapshotCache) {
        SecurityUtils.snapshotCache = snapshotCache;
    }

    private SecurityUtils() {
    }

    /**
     * 当前登录用户主键；未登录返回 null，不抛异常。
     *
     * <p>loginId 不是数字（历史上有人用 username 当 loginId）时同样返回 null，
     * 让调用方按"未登录/无权限"处理，避免登录态异常扩散成 500。</p>
     */
    public static Long currentUserIdOrNull() {
        try {
            if (!StpUtil.isLogin()) {
                return null;
            }
            return StpUtil.getLoginIdAsLong();
        } catch (NotLoginException e) {
            return null;
        } catch (RuntimeException e) {
            log.warn("当前 loginId 无法解析为用户主键，按未登录处理：{}", e.getMessage());
            return null;
        }
    }

    /** 当前登录用户主键；未登录抛 401 业务异常。 */
    public static Long currentUserId() {
        Long userId = currentUserIdOrNull();
        if (userId == null) {
            throw new BizException(401, "未登录或登录已失效");
        }
        return userId;
    }

    /** 指定用户的角色码与权限码快照（走缓存，TTL 5 分钟，改授权会主动失效）。 */
    public static RbacSnapshotCache.Snapshot snapshotOf(Long userId) {
        if (userId == null || snapshotCache == null) {
            return RbacSnapshotCache.Snapshot.EMPTY;
        }
        return snapshotCache.get(userId);
    }

    /** 指定用户的全部权限码。 */
    public static List<String> permissionsOf(Long userId) {
        return snapshotOf(userId).permissions();
    }

    /** 当前用户的全部权限码；未登录返回空集合。 */
    public static List<String> currentPermissions() {
        return permissionsOf(currentUserIdOrNull());
    }

    /** 当前用户的全部角色码；未登录返回空集合。 */
    public static List<String> currentRoles() {
        return snapshotOf(currentUserIdOrNull()).roles();
    }

    /** 当前用户是否拥有某个权限码。 */
    public static boolean hasPermission(String permissionCode) {
        return permissionCode != null && !permissionCode.isBlank()
                && currentPermissions().contains(permissionCode);
    }

    /** 当前用户是否拥有其中任意一个权限码。 */
    public static boolean hasAnyPermission(String... permissionCodes) {
        if (permissionCodes == null || permissionCodes.length == 0) {
            return false;
        }
        List<String> owned = currentPermissions();
        return Arrays.stream(permissionCodes)
                .filter(code -> code != null && !code.isBlank())
                .anyMatch(owned::contains);
    }

    /** 当前用户是否拥有某个角色码。 */
    public static boolean hasRole(String roleCode) {
        return roleCode != null && !roleCode.isBlank() && currentRoles().contains(roleCode);
    }

    /** 当前用户是否为系统管理员。 */
    public static boolean isSystemAdmin() {
        return hasRole(RoleConstants.SYSTEM_ADMIN);
    }

    /**
     * 代码式鉴权：没有该权限直接抛 403 业务异常。
     *
     * <p>与 {@code @SaCheckPermission} 等价，仅用于注解表达不了的场景（如权限码是动态拼出来的）。</p>
     */
    public static void requirePermission(String permissionCode) {
        if (!hasPermission(permissionCode)) {
            throw new BizException(403, "缺少权限：" + permissionCode);
        }
    }

    /** 代码式鉴权：没有该角色直接抛 403 业务异常。 */
    public static void requireRole(String roleCode) {
        if (!hasRole(roleCode)) {
            throw new BizException(403, "缺少角色：" + roleCode);
        }
    }
}
