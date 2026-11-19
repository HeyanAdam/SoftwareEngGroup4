package com.group4.kaoyan.modules.auth.init;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.group4.kaoyan.modules.auth.RbacConstants;
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
 * 启动时初始化 RBAC 基础数据：权限字典（20 条）+ 4 个内置角色 + 旧角色码迁移。
 *
 * <p>数据来源只有 {@link RbacConstants}（{@code docs/权限表.md} 的代码版本），本类只负责幂等落库：</p>
 * <ol>
 *   <li><b>权限字典只补缺</b>：库里没有的权限码才插入，已存在的不改名、不改排序。</li>
 *   <li><b>普通角色只在首次创建时写默认授权</b>：角色已存在就沿用库里的授权，
 *       后台删掉的授权重启不会长回来。</li>
 *   <li><b>SYSTEM_ADMIN 每次启动补齐全部权限码</b>（INSERT IGNORE，只增不删），
 *       避免新增权限上线后老库管理员突然 403。</li>
 *   <li><b>旧角色码迁移</b>：ADMIN → SYSTEM_ADMIN、KB_ADMIN → CONTENT_OPERATOR、
 *       DATA_VIEWER → DATA_AUDITOR；只搬用户绑定，权限一律按官方矩阵重建。</li>
 * </ol>
 *
 * <p>不再做"V6 校验式"启动器：权限码从 11 条扩到 20 条后，校验式会让老库直接启动失败。</p>
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
        Map<String, Long> permIds = ensurePermissions();

        int createdRoles = 0;
        for (RbacConstants.RoleDef def : RbacConstants.BUILTIN_ROLES) {
            if (ensureRole(def, permIds)) {
                createdRoles++;
            }
        }

        // 必须在内置角色创建之后执行，否则目标角色还不存在
        int migrated = 0;
        for (String[] pair : List.of(
                new String[]{RbacConstants.LEGACY_ADMIN, RbacConstants.ROLE_SYSTEM_ADMIN},
                new String[]{RbacConstants.LEGACY_KB_ADMIN, RbacConstants.ROLE_CONTENT_OPERATOR},
                new String[]{RbacConstants.LEGACY_DATA_VIEWER, RbacConstants.ROLE_DATA_AUDITOR})) {
            if (migrateLegacyRole(pair[0], pair[1])) {
                migrated++;
            }
        }

        log.info("RBAC 初始化完成：权限字典 {} 条，内置角色 {} 个（本次新建 {} 个），旧角色迁移 {} 个",
                permIds.size(), RbacConstants.BUILTIN_ROLES.size(), createdRoles, migrated);
    }

    /** 补齐权限字典，返回 权限码 → 主键。 */
    private Map<String, Long> ensurePermissions() {
        Map<String, Long> permIds = new LinkedHashMap<>();
        int created = 0;
        for (RbacConstants.PermissionDef def : RbacConstants.PERMISSION_CATALOG) {
            SysPermission exist = permissionMapper.selectOne(Wrappers.<SysPermission>lambdaQuery()
                    .eq(SysPermission::getPermCode, def.code()));
            if (exist != null) {
                permIds.put(def.code(), exist.getId());
                continue;
            }
            SysPermission perm = new SysPermission();
            perm.setPermCode(def.code());
            perm.setPermName(def.name());
            perm.setPermType("API");
            perm.setParentId(0L);
            perm.setSort(def.sort());
            permissionMapper.insert(perm);
            permIds.put(def.code(), perm.getId());
            created++;
        }
        if (created > 0) {
            log.info("权限字典补齐了 {} 个新权限码", created);
        }
        return permIds;
    }

    /** @return true 表示本次新建了该角色 */
    private boolean ensureRole(RbacConstants.RoleDef def, Map<String, Long> permIds) {
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
                    ? RbacConstants.allPermissionCodes()
                    : def.defaultPermissions();
            int bound = 0;
            for (String permCode : grants) {
                Long permId = permIds.get(permCode);
                if (permId == null) {
                    log.warn("角色 {} 的默认权限 {} 不在权限字典中，已跳过", def.code(), permCode);
                    continue;
                }
                bound += permissionMapper.bindRolePermission(role.getId(), permId);
            }
            log.info("角色 {} 首次创建，写入默认授权 {} 条（数据范围 {}）", def.code(), bound, def.dataScope());
            return true;
        }

        if (!Integer.valueOf(1).equals(exist.getStatus())) {
            log.warn("内置角色 {} 当前为禁用状态（status={}），其权限不会生效，请到后台确认",
                    def.code(), exist.getStatus());
        }
        if (def.grantAllPermissions()) {
            int added = 0;
            for (Long permId : permIds.values()) {
                added += permissionMapper.bindRolePermission(exist.getId(), permId);
            }
            if (added > 0) {
                log.info("内置全权角色 {} 补齐了 {} 个权限码（只增不删，保留后台自定义授权）", def.code(), added);
            }
        }
        return false;
    }

    /**
     * 旧角色码迁移：把用户绑定搬到目标角色后删除旧角色。
     *
     * <p>只搬用户、不搬权限绑定——旧码是本组过渡期的自拟角色，权限按官方矩阵重建，
     * 避免把临时授权带进正式基线。幂等：旧角色不存在直接返回 false。</p>
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

        log.info("旧角色 {} 已迁移到 {}：搬移用户 {} 个，旧角色及其授权已删除",
                legacyCode, targetCode, userIds.size());
        return true;
    }
}
