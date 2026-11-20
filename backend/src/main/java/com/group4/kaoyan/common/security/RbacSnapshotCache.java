package com.group4.kaoyan.common.security;

import com.group4.kaoyan.modules.auth.mapper.PermissionMapper;
import com.group4.kaoyan.modules.auth.mapper.RoleMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户角色/权限快照缓存（P2 任务 2 的性能与一致性层）。
 *
 * <p>为什么需要它：Sa-Token 的 {@code @SaCheckPermission} 是"每次请求都回调一次"，
 * 如果每次回调都去联查 sys_user_role / sys_role_permission / sys_permission 三张表，
 * 一个页面十几个接口就能打出上百条 SQL。这里做一层带 TTL 的进程内缓存。</p>
 *
 * <p>一致性怎么保证（答辩容易问）：</p>
 * <ul>
 *   <li>默认 TTL 5 分钟，过期自动重查；</li>
 *   <li>后台改授权时<b>主动失效</b>：{@code AdminService.assignRoles} / {@code assignPermissions}
 *       改完立刻调用 {@link #evict(Long)} / {@link #evictByRoleId(Long)}，
 *       所以"刚改完权限、刷新页面就生效"，不用等 TTL。</li>
 * </ul>
 *
 * <p>注意：这是单机进程内缓存，多实例部署时各节点 TTL 内可能有短暂不一致；
 * 规模上来后换成 Redis 缓存即可，接口签名不用变（本项目已经在用 Redis）。</p>
 */
@Component
public class RbacSnapshotCache {

    /** 默认快照有效期：5 分钟。 */
    private static final long DEFAULT_TTL_MILLIS = 5 * 60 * 1000L;

    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final long ttlMillis;
    private final Map<Long, Entry> cache = new ConcurrentHashMap<>();

    /**
     * 生产环境用的构造函数：Spring 注入两个 Mapper。
     *
     * <p>类里还有一个"带 TTL"的包可见构造函数供单测使用，所以这里必须显式标
     * {@code @Autowired}，否则 Spring 在多个构造函数之间找不到该用哪个，
     * 启动时会抛 {@code NoSuchMethodException}（No default constructor found）。</p>
     */
    @Autowired
    public RbacSnapshotCache(RoleMapper roleMapper, PermissionMapper permissionMapper) {
        this(roleMapper, permissionMapper, DEFAULT_TTL_MILLIS);
    }

    /** 供测试注入短 TTL。 */
    RbacSnapshotCache(RoleMapper roleMapper, PermissionMapper permissionMapper, long ttlMillis) {
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.ttlMillis = ttlMillis;
    }

    /**
     * 用户权限快照（角色码 + 权限码）。
     *
     * @param userId 用户主键；为 null 时返回空快照，不查库
     */
    public Snapshot get(Long userId) {
        if (userId == null) {
            return Snapshot.EMPTY;
        }
        long now = System.currentTimeMillis();
        Entry cached = cache.get(userId);
        if (cached != null && cached.expireAt() > now) {
            return cached.snapshot();
        }
        Snapshot fresh = load(userId);
        cache.put(userId, new Entry(fresh, now + ttlMillis));
        return fresh;
    }

    /** 授权变更后失效单个用户。 */
    public void evict(Long userId) {
        if (userId != null) {
            cache.remove(userId);
        }
    }

    /** 角色授权变更后，失效该角色下所有用户。 */
    public void evictByRoleId(Long roleId) {
        if (roleId == null) {
            return;
        }
        List<Long> userIds;
        try {
            userIds = roleMapper.selectUserIdsByRoleId(roleId);
        } catch (RuntimeException e) {
            cache.clear();
            return;
        }
        userIds.forEach(cache::remove);
    }

    /** 全量失效（初始化、批量导入等场景）。 */
    public void evictAll() {
        cache.clear();
    }

    private Snapshot load(Long userId) {
        List<String> roles = roleMapper.selectRoleCodesByUserId(userId);
        List<String> perms = permissionMapper.selectPermCodesByUserId(userId);
        return new Snapshot(roles, perms);
    }

    /**
     * 用户权限快照，不可变。
     *
     * @param roles       角色码，如 ["STUDENT"]
     * @param permissions 权限码，如 ["chat:send"]
     */
    public record Snapshot(List<String> roles, List<String> permissions) {

        public static final Snapshot EMPTY = new Snapshot(List.of(), List.of());

        public Snapshot {
            roles = roles == null ? List.of() : List.copyOf(roles);
            permissions = permissions == null ? List.of() : List.copyOf(permissions);
        }
    }

    private record Entry(Snapshot snapshot, long expireAt) {
    }
}
