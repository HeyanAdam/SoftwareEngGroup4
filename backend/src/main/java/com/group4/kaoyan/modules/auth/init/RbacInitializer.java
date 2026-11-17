package com.group4.kaoyan.modules.auth.init;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 启动时初始化 RBAC 基础数据（权限字典 + 两个内置角色）。
 *
 * 两条原则：
 * 1) 只补“缺的东西”：权限字典缺哪个补哪个，角色只在首次创建时写入默认权限；
 *    已存在的角色/用户一律不动，管理员在控制台改的权限、提权记录不会因重启被覆盖。
 * 2) 不预置管理员账号：系统里第一个注册的用户自动成为管理员，之后靠后台提权。
 */
@Slf4j
//@Component
@RequiredArgsConstructor
public class RbacInitializer implements ApplicationRunner {

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_STUDENT = "STUDENT";

    private static final String[][] PERMISSIONS = {
            {"kb:view", "查看知识库"}, {"kb:create", "创建知识库"}, {"kb:delete", "删除知识库"},
            {"doc:upload", "上传文档"}, {"doc:delete", "删除文档"}, {"chat:send", "智能问答"},
            {"admin:user:list", "用户列表"}, {"admin:user:update", "用户管理"},
            {"admin:role:list", "角色列表"}, {"admin:role:update", "角色权限管理"},
            {"admin:vector:manage", "向量库管理"}
    };

    /** STUDENT 首次创建时写入的默认权限：只能通过问答使用知识库数据 */
    private static final List<String> STUDENT_DEFAULT_PERMS = Arrays.asList("chat:send");

    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // 1) 权限字典：只补缺的权限码，不触碰任何角色与权限的绑定关系
        Map<String, Long> permIds = new LinkedHashMap<>();
        for (String[] p : PERMISSIONS) {
            permIds.put(p[0], ensurePermission(p[0], p[1]));
        }

        // 2) 内置角色：不存在才创建并写默认权限；已存在就完全沿用数据库里的配置
        ensureRole(ROLE_ADMIN, "管理员", "拥有全部权限", permIds.keySet(), permIds);
        ensureRole(ROLE_STUDENT, "学生", "默认注册角色，只能问答", STUDENT_DEFAULT_PERMS, permIds);

        log.info("RBAC 初始化检查完成：权限字典 {} 条；管理员由首个注册用户产生", permIds.size());
    }

    /**
     * @return true 表示本次新建了角色并写入了默认权限
     */
    private boolean ensureRole(String code, String name, String desc,
                               java.util.Collection<String> defaultPerms, Map<String, Long> permIds) {
        SysRole exist = roleMapper.selectOne(Wrappers.<SysRole>lambdaQuery().eq(SysRole::getRoleCode, code));
        if (exist != null) {
            log.info("角色 {} 已存在，沿用数据库里的权限配置（可在后台修改）", code);
            return false;
        }
        SysRole role = new SysRole();
        role.setRoleCode(code);
        role.setRoleName(name);
        role.setDescription(desc);
        role.setStatus(1);
        role.setCreateTime(LocalDateTime.now());
        roleMapper.insert(role);
        for (String permCode : defaultPerms) {
            Long pid = permIds.get(permCode);
            if (pid != null) {
                permissionMapper.bindRolePermission(role.getId(), pid);
            }
        }
        log.info("角色 {} 首次创建，已写入 {} 条默认权限", code, defaultPerms.size());
        return true;
    }

    private Long ensurePermission(String code, String name) {
        SysPermission perm = permissionMapper.selectOne(Wrappers.<SysPermission>lambdaQuery()
                .eq(SysPermission::getPermCode, code));
        if (perm != null) {
            return perm.getId();
        }
        perm = new SysPermission();
        perm.setPermCode(code);
        perm.setPermName(name);
        perm.setPermType("API");
        perm.setParentId(0L);
        perm.setSort(0);
        permissionMapper.insert(perm);
        return perm.getId();
    }
}
