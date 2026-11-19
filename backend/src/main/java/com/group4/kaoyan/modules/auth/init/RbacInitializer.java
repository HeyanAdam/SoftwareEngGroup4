package com.group4.kaoyan.modules.auth.init;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.group4.kaoyan.common.security.PermissionConstants;
import com.group4.kaoyan.common.security.RoleConstants;
import com.group4.kaoyan.modules.auth.entity.SysPermission;
import com.group4.kaoyan.modules.auth.entity.SysRole;
import com.group4.kaoyan.modules.auth.mapper.PermissionMapper;
import com.group4.kaoyan.modules.auth.mapper.RoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 启动时初始化 RBAC 基础数据（权限字典 + 4 个内置角色 + 旧角色码迁移）。
 *
 * <p>数据来源只有两处：{@link PermissionConstants#CATALOG}（20 条权限码）和
 * {@link RoleConstants#BUILTIN_ROLES}（官方 4 个角色），本类只负责把它们幂等地写进 MySQL。</p>
 *
 * <p>写入策略（P2 任务 1 的核心，答辩容易问）：</p>
 * <ol>
 *   <li><b>权限字典只补缺的</b>：sys_permission 里没有的权限码才插入，已存在的不改名、不改排序。</li>
 *   <li><b>普通角色只在首次创建时写默认授权</b>：已存在就完全沿用数据库里的 sys_role_permission，
 *       后台删掉的授权重启不会长回来。</li>
 *   <li><b>SYSTEM_ADMIN 例外：每次启动补齐全部权限码</b>（INSERT IGNORE，只增不删）。
 *       它是"内置全权角色"，新增权限码上线后老库的管理员不该突然 403。</li>
 *   <li><b>旧角色码自动迁移</b>：ADMIN → SYSTEM_ADMIN、KB_ADMIN → CONTENT_OPERATOR、
 *       DATA_VIEWER → DATA_AUDITOR，用户绑定随角色一起搬过去再删旧角色，
 *       保证升级后管理员不失权；权限按官方矩阵重建，不带入过渡期的临时授权。</li>
 *   <li><b>不创建管理员账号</b>：账号由注册/受控初始化流程产生。</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RbacInitializer implements ApplicationRunner {

    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // 1) 权限字典：以 PermissionConstants.CATALOG 为准，缺哪个补哪个（幂等）
        Map<String, Long> permIds = new LinkedHashMap<>();
        int createdPerms = 0;
        for (PermissionConstants.PermissionDef def : PermissionConstants.CATALOG) {
            Ensured ensured = ensurePermission(def);
            permIds.put(def.code(), ensured.id());
            if (ensured.created()) {
                createdPerms++;
            }
        }

        // 2) 内置角色：不存在才创建并写默认授权；SYSTEM_ADMIN 每次补齐全部权限码
        int createdRoles = 0;
        for (RoleConstants.RoleDef def : RoleConstants.BUILTIN_ROLES) {
            if (ensureRole(def, permIds)) {
                createdRoles++;
            }
        }

        // 3) 旧角色码迁移（必须放在内置角色创建之后，否则目标角色还不存在）
        int migrated = 0;
        if (migrateLegacyRole(RoleConstants.LEGACY_ADMIN, RoleConstants.SYSTEM_ADMIN)) {
            migrated++;
        }
        if (migrateLegacyRole(RoleConstants.LEGACY_KB_ADMIN, RoleConstants.CONTENT_OPERATOR)) {
            migrated++;
        }
        if (migrateLegacyRole(RoleConstants.LEGACY_DATA_VIEWER, RoleConstants.DATA_AUDITOR)) {
            migrated++;
        }

        log.info("RBAC 初始化检查完成：权限字典共 {} 条（本次新增 {} 条），内置角色共 {} 个（本次新建 {} 个），旧角色迁移 {} 个",
                permIds.size(), createdPerms, RoleConstants.BUILTIN_ROLES.size(), createdRoles, migrated);
    }

    /** 权限补齐结果：主键 + 本次是否新建（供启动日志统计用）。 */
    private record Ensured(Long id, boolean created) {
    }

    /**
     * 补齐一个权限码：库里没有就插入，有就原样返回。
     */
    private Ensured ensurePermission(PermissionConstants.PermissionDef def) {
        SysPermission exist = permissionMapper.selectOne(Wrappers.<SysPermission>lambdaQuery()
                .eq(SysPermission::getPermCode, def.code()));
        if (exist != null) {
            return new Ensured(exist.getId(), false);
        }
        SysPermission perm = new SysPermission();
        perm.setPermCode(def.code());
        perm.setPermName(def.name());
        perm.setPermType("API");
        perm.setParentId(0L);
        perm.setSort(def.sort());
        permissionMapper.insert(perm);
        return new Ensured(perm.getId(), true);
    }

    /**
     * 补齐一个内置角色。
     *
     * @return true 表示本次新建了角色
     */
    private boolean ensureRole(RoleConstants.RoleDef def, Map<String, Long> permIds) {
        SysRole exist = roleMapper.selectOne(Wrappers.<SysRole>lambdaQuery()
                .eq(SysRole::getRoleCode, def.code()));

        if (exist == null) {
            SysRole role = new SysRole();
            role.setRoleCode(def.code());
            role.setRoleName(def.name());
            role.setDescription(def.description());
            role.setStatus(1);
            role.setCreateTime(LocalDateTime.now());
            roleMapper.insert(role);

            List<String> grants = def.grantAllPermissions()
                    ? PermissionConstants.ALL_CODES
                    : def.defaultPermissions();
            int bound = 0;
            for (String permCode : grants) {
                Long pid = permIds.get(permCode);
                if (pid == null) {
                    log.warn("角色 {} 的默认权限 {} 未在权限字典中登记，已跳过", def.code(), permCode);
                    continue;
                }
                bound += permissionMapper.bindRolePermission(role.getId(), pid);
            }
            log.info("角色 {} 首次创建，写入默认授权 {} 条（数据范围 {}）",
                    def.code(), bound, def.defaultDataScope());
            return true;
        }

        if (!Integer.valueOf(1).equals(exist.getStatus())) {
            log.warn("内置角色 {} 当前为禁用状态（status={}），其权限不会生效，请到后台确认",
                    def.code(), exist.getStatus());
        }
        if (def.grantAllPermissions()) {
            int added = 0;
            for (Map.Entry<String, Long> entry : permIds.entrySet()) {
                added += permissionMapper.bindRolePermission(exist.getId(), entry.getValue());
            }
            if (added > 0) {
                log.info("内置全权角色 {} 补齐了 {} 个新增权限码（只增不删，保留后台自定义授权）",
                        def.code(), added);
            }
        }
        return false;
    }

    /**
     * 旧角色码迁移：把用户绑定搬到目标角色，再删除旧角色。
     *
     * <p>只搬用户、不搬权限绑定：ADMIN / KB_ADMIN / DATA_VIEWER 是本组过渡期的自拟角色码，
     * 权限一律按官方矩阵重建（SYSTEM_ADMIN 全量补齐，CONTENT_OPERATOR 6 条，DATA_AUDITOR 3 条），
     * 避免把过渡期的临时授权带进正式基线。</p>
     *
     * <p>幂等：旧角色不存在时直接返回 false；重复执行不会重复搬数据。</p>
     *
     * @return true 表示本次执行了迁移
     */
    private boolean migrateLegacyRole(String legacyCode, String targetCode) {
        SysRole legacy = roleMapper.selectOne(Wrappers.<SysRole>lambdaQuery()
                .eq(SysRole::getRoleCode, legacyCode));
        if (legacy == null) {
            return false;
        }
        SysRole target = roleMapper.selectOne(Wrappers.<SysRole>lambdaQuery()
                .eq(SysRole::getRoleCode, targetCode));
        if (target == null) {
            log.warn("旧角色 {} 存在但目标角色 {} 不存在，跳过迁移", legacyCode, targetCode);
            return false;
        }

        List<Long> userIds = roleMapper.selectUserIdsByRoleId(legacy.getId());
        for (Long userId : userIds) {
            roleMapper.bindUserRole(userId, target.getId());
        }

        roleMapper.unbindUsersByRoleId(legacy.getId());
        permissionMapper.unbindRolePermissions(legacy.getId());
        roleMapper.deleteById(legacy.getId());

        log.info("旧角色 {} 已迁移到 {}：搬移用户 {} 个，旧角色已删除（权限按官方矩阵重建）",
                legacyCode, targetCode, userIds.size());
        return true;
    }
}
