package com.group4.kaoyan.modules.auth.init;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.group4.kaoyan.common.config.AiProperties;
import com.group4.kaoyan.modules.auth.entity.SysPermission;
import com.group4.kaoyan.modules.auth.entity.SysRole;
import com.group4.kaoyan.modules.auth.mapper.PermissionMapper;
import com.group4.kaoyan.modules.auth.mapper.RoleMapper;
import com.group4.kaoyan.modules.user.entity.SysUser;
import com.group4.kaoyan.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 启动时初始化 RBAC 基础数据（角色、权限、管理员账号），已有数据不重复插入。
 * 这样换一台机器、换一个库，跑起来就自带可用的权限体系。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RbacInitializer implements ApplicationRunner {

    private static final String[][] PERMISSIONS = {
            {"kb:view", "查看知识库"}, {"kb:create", "创建知识库"}, {"kb:delete", "删除知识库"},
            {"doc:upload", "上传文档"}, {"doc:delete", "删除文档"}, {"chat:send", "智能问答"},
            {"admin:user:list", "用户列表"}, {"admin:user:update", "用户管理"},
            {"admin:role:list", "角色列表"}, {"admin:role:update", "角色权限管理"},
            {"admin:vector:manage", "向量库管理"}
    };

    /**
     * 普通用户只有 chat:send：只能通过问答“使用”知识库里的数据，
     * 不能浏览知识库 / 文档列表 / 原文全文 —— kb:view 属于后台管理权限。
     */
    private static final List<String> STUDENT_PERMS = Arrays.asList("chat:send");

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final AiProperties props;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        SysRole admin = ensureRole("ADMIN", "管理员", "拥有全部权限");
        SysRole student = ensureRole("STUDENT", "学生", "默认注册角色，只读");

        // STUDENT 是内置只读角色：每次启动按固定集合同步一次，避免历史库里残留旧的写权限
        permissionMapper.unbindRolePermissions(student.getId());

        for (String[] p : PERMISSIONS) {
            SysPermission perm = ensurePermission(p[0], p[1]);
            permissionMapper.bindRolePermission(admin.getId(), perm.getId());
            if (STUDENT_PERMS.contains(p[0])) {
                permissionMapper.bindRolePermission(student.getId(), perm.getId());
            }
        }

        String username = props.getSecurity().getInitAdminUsername();
        if (userMapper.selectCount(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username)) == 0) {
            SysUser user = new SysUser();
            user.setUsername(username);
            user.setPasswordHash(BCrypt.hashpw(props.getSecurity().getInitAdminPassword()));
            user.setNickname("超级管理员");
            user.setStatus(1);
            user.setCreateTime(LocalDateTime.now());
            user.setUpdateTime(LocalDateTime.now());
            userMapper.insert(user);
            roleMapper.bindUserRole(user.getId(), admin.getId());
            log.info("已初始化管理员账号：{}", username);
        }
    }

    private SysRole ensureRole(String code, String name, String desc) {
        SysRole role = roleMapper.selectOne(Wrappers.<SysRole>lambdaQuery().eq(SysRole::getRoleCode, code));
        if (role != null) {
            return role;
        }
        role = new SysRole();
        role.setRoleCode(code);
        role.setRoleName(name);
        role.setDescription(desc);
        role.setStatus(1);
        role.setCreateTime(LocalDateTime.now());
        roleMapper.insert(role);
        return role;
    }

    private SysPermission ensurePermission(String code, String name) {
        SysPermission perm = permissionMapper.selectOne(Wrappers.<SysPermission>lambdaQuery()
                .eq(SysPermission::getPermCode, code));
        if (perm != null) {
            return perm;
        }
        perm = new SysPermission();
        perm.setPermCode(code);
        perm.setPermName(name);
        perm.setPermType("API");
        perm.setParentId(0L);
        perm.setSort(0);
        permissionMapper.insert(perm);
        return perm;
    }
}
