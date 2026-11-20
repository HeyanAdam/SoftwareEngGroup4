package com.group4.kaoyan.common.security;

import com.group4.kaoyan.common.BizException;
import com.group4.kaoyan.common.ErrorCode;
import com.group4.kaoyan.modules.auth.mapper.RoleMapper;
import com.group4.kaoyan.modules.auth.mapper.PermissionMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 数据范围服务（P2 任务 3）。
 *
 * <p>权限码回答"能不能进这个接口"，数据范围回答"进了以后能看到谁的数据"，
 * 两者缺一不可——只做权限码的话，学生拿到自己的会话 ID 一改就能看别人的记录。</p>
 *
 * <p>规则（与《接口设计文档 V1.1》1.4 节、角色矩阵一致）：</p>
 * <ul>
 *   <li>{@link DataScope#SELF}：只能看/改本人数据（学生默认）；</li>
 *   <li>{@link DataScope#ALL}：可看全部数据（系统管理员、内容运营）；</li>
 *   <li>{@link DataScope#MASKED}：可看汇总或脱敏结果，不含敏感明细（数据审计员）。</li>
 * </ul>
 *
 * <p>一个用户可能绑多个角色，取<b>最宽</b>的范围（ALL &gt; MASKED &gt; SELF）；
 * 角色列表为空或角色码不认识时降级 SELF，绝不放大权限。</p>
 */
@Slf4j
@Service
public class DataScopeService {

    private final RbacSnapshotCache snapshotCache;

    /**
     * 生产环境构造函数。
     *
     * <p>显式 {@code @Autowired}：本类还有一个供单测注入的构造函数，
     * 不标注的话 Spring 在多个构造函数之间无法选择，启动会报
     * {@code No default constructor found}。</p>
     */
    @Autowired
    public DataScopeService(RbacSnapshotCache snapshotCache, RoleMapper roleMapper,
                            PermissionMapper permissionMapper) {
        this.snapshotCache = snapshotCache;
    }

    /** 供单测使用的最小依赖构造函数。 */
    DataScopeService(RbacSnapshotCache snapshotCache) {
        this.snapshotCache = snapshotCache;
    }

    // ==================== 身份与权限 ====================

    /** 当前登录用户主键；未登录抛 10001。 */
    public Long currentUserId() {
        return SecurityUtils.currentUserId();
    }

    /** 当前用户是否拥有某个权限码（走同一份权限快照缓存）。 */
    public boolean hasPermission(String permissionCode) {
        return SecurityUtils.hasPermission(permissionCode);
    }

    // ==================== 数据范围 ====================

    /** 当前用户的数据范围。 */
    public DataScope currentScope() {
        return scopeOf(currentUserId());
    }

    /** 指定用户的数据范围（多角色取最宽，未知角色按 SELF）。 */
    public DataScope scopeOf(Long userId) {
        if (userId == null) {
            return DataScope.SELF;
        }
        List<String> roles = snapshotCache.get(userId).roles();
        if (roles == null || roles.isEmpty()) {
            return DataScope.SELF;
        }
        DataScope widest = DataScope.SELF;
        for (String roleCode : roles) {
            DataScope scope = RoleConstants.defaultDataScopeOf(roleCode);
            if (rank(scope) > rank(widest)) {
                widest = scope;
            }
        }
        return widest;
    }

    /** 当前用户是否满足某数据范围要求（SELF 是任何人的下限，恒成立）。 */
    public boolean hasDataScope(DataScope required) {
        return hasDataScopeOf(currentUserId(), required);
    }

    /** 指定用户是否满足某数据范围要求；写成可传 userId 的形式，便于单测与后台批量判断。 */
    public boolean hasDataScopeOf(Long userId, DataScope required) {
        if (required == null || required == DataScope.SELF) {
            return true;
        }
        DataScope current = scopeOf(userId);
        if (required == DataScope.ALL) {
            return current == DataScope.ALL;
        }
        // required == MASKED：ALL 权限更高，MASKED 自身也满足
        return current == DataScope.ALL || current == DataScope.MASKED;
    }

    /** 当前用户能否读全部数据（管理员口径）。 */
    public boolean canReadAll() {
        return currentScope() == DataScope.ALL;
    }

    /** 当前用户能否读脱敏/汇总数据（管理员与数据审计员）。 */
    public boolean canReadMasked() {
        DataScope current = currentScope();
        return current == DataScope.ALL || current == DataScope.MASKED;
    }

    /**
     * 资源归属校验：当前用户必须是资源所有者，或拥有 ALL 数据范围。
     *
     * <p>越权时抛 10009（"无权访问该数据"），与 10008（"无接口权限"）区分开：
     * 前者是"你连接口都进不去"，后者是"接口进得去，但这条数据不是你的"。</p>
     *
     * @param ownerUserId 资源所有者主键
     */
    public void requireSelf(Long ownerUserId) {
        Long currentUserId = currentUserId();
        if (ownerUserId != null && ownerUserId.equals(currentUserId)) {
            return;
        }
        if (canReadAll()) {
            return;
        }
        log.info("越权访问被拦截：当前用户 {} 试图访问属于 {} 的数据", currentUserId, ownerUserId);
        throw new BizException(ErrorCode.NO_DATA_ACCESS, "无权访问该数据");
    }

    /** 不做抛出、只做判断的版本，供分支逻辑使用。 */
    public boolean canAccess(Long ownerUserId) {
        Long currentUserId = SecurityUtils.currentUserIdOrNull();
        if (currentUserId == null) {
            return false;
        }
        return (ownerUserId != null && ownerUserId.equals(currentUserId)) || canReadAll();
    }

    /**
     * 把权限码集合按"当前用户是否拥有"过滤一遍，便于前端菜单/按钮下发给 P3。
     * 前端只用来做显隐，真正的拦截仍在后端注解。
     */
    public Set<String> filterOwned(Set<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return Set.of();
        }
        List<String> owned = SecurityUtils.currentPermissions();
        return codes.stream().filter(owned::contains).collect(Collectors.toSet());
    }

    private static int rank(DataScope scope) {
        return switch (scope) {
            case ALL -> 3;
            case MASKED -> 2;
            case SELF -> 1;
        };
    }
}
