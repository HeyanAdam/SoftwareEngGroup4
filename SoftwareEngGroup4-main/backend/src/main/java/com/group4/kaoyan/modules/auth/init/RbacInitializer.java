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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 启动时校验 Flyway V6 写入的 RBAC 字典。
 *
 * Flyway 是 RBAC 角色、权限和绑定关系的唯一写入来源。本类不再写数据库，
 * 只在种子缺失时快速失败，避免应用在权限不完整的状态下继续启动。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RbacInitializer implements ApplicationRunner {

    private static final List<String> REQUIRED_PERMISSIONS = List.of(
            "kb:view", "kb:create", "kb:delete",
            "doc:upload", "doc:delete", "chat:send",
            "admin:user:list", "admin:user:update",
            "admin:role:list", "admin:role:update",
            "admin:vector:manage"
    );

    private static final Map<String, List<String>> REQUIRED_ROLE_PERMISSIONS = expectedRolePermissions();

    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;

    @Override
    public void run(ApplicationArguments args) {
        List<String> issues = new ArrayList<>();

        for (String permCode : REQUIRED_PERMISSIONS) {
            long count = permissionMapper.selectCount(Wrappers.<SysPermission>lambdaQuery()
                    .eq(SysPermission::getPermCode, permCode));
            if (count == 0) {
                issues.add("缺少权限 " + permCode);
            }
        }

        Map<String, SysRole> roles = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : REQUIRED_ROLE_PERMISSIONS.entrySet()) {
            SysRole role = roleMapper.selectOne(Wrappers.<SysRole>lambdaQuery()
                    .eq(SysRole::getRoleCode, entry.getKey()));
            if (role == null) {
                issues.add("缺少角色 " + entry.getKey());
                continue;
            }
            roles.put(entry.getKey(), role);
            if (role.getStatus() == null || role.getStatus() != 1) {
                issues.add("角色 " + entry.getKey() + " 未启用");
            }
            List<String> actualPermissions = permissionMapper.selectPermCodesByRoleId(role.getId());
            for (String permCode : entry.getValue()) {
                if (!actualPermissions.contains(permCode)) {
                    issues.add("角色 " + entry.getKey() + " 缺少权限 " + permCode);
                }
            }
        }

        if (!issues.isEmpty()) {
            throw new IllegalStateException("RBAC V6 种子校验失败：" + String.join("；", issues));
        }

        log.info("RBAC V6 种子校验通过：权限 {} 条，角色 {} 个；管理员账号仍需首次注册或后台提权",
                REQUIRED_PERMISSIONS.size(), roles.size());
    }

    private static Map<String, List<String>> expectedRolePermissions() {
        Map<String, List<String>> expected = new LinkedHashMap<>();
        expected.put(RbacConstants.ROLE_SYSTEM_ADMIN, REQUIRED_PERMISSIONS);
        expected.put(RbacConstants.ROLE_STUDENT, List.of("chat:send"));
        expected.put(RbacConstants.ROLE_CONTENT_OPERATOR, List.of(
                "kb:view", "kb:create", "kb:delete",
                "doc:upload", "doc:delete", "admin:vector:manage"
        ));
        expected.put(RbacConstants.ROLE_DATA_AUDITOR, List.of(
                "kb:view", "admin:user:list", "admin:role:list"
        ));
        return Map.copyOf(expected);
    }
}
