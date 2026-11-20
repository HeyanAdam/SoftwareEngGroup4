package com.group4.kaoyan.modules.auth.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.group4.kaoyan.common.BizException;
import com.group4.kaoyan.modules.auth.RbacConstants;
import com.group4.kaoyan.modules.auth.entity.SysRole;
import com.group4.kaoyan.modules.user.entity.SysUser;
import com.group4.kaoyan.modules.auth.mapper.PermissionMapper;
import com.group4.kaoyan.modules.auth.mapper.RoleMapper;
import com.group4.kaoyan.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;

    /**
     * 注册即分配角色：库里还没有任何管理员时，第一个注册的人直接成为管理员（只发生一次）；
     * 之后注册的一律是 STUDENT，要当管理员由已有管理员在后台提权。
     *
     * synchronized 是为了让“判断有没有管理员 + 绑定角色”这两步不被并发注册穿插，
     * 避免前两个并发注册的人同时成为管理员。
     */
    @Transactional
    public synchronized void register(String username, String rawPassword, String nickname) {
        if (username == null || username.isBlank() || rawPassword == null || rawPassword.isBlank()) {
            throw new BizException("用户名和密码不能为空");
        }
        if (userMapper.selectCount(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username)) > 0) {
            throw new BizException("用户名已存在");
        }
        SysUser user = new SysUser();
        user.setUsername(username.trim());
        user.setPasswordHash(BCrypt.hashpw(rawPassword));
        user.setNickname(nickname == null || nickname.isBlank() ? username : nickname);
        user.setStatus(1);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        userMapper.insert(user);

        SysRole role = resolveRegisterRole();
        if (role == null) {
            log.warn("注册时没找到可用角色，用户 {} 未绑定角色，请检查 RBAC 初始化", user.getUsername());
            return;
        }
        roleMapper.bindUserRole(user.getId(), role.getId());
        if (RbacConstants.ROLE_SYSTEM_ADMIN.equals(role.getRoleCode())) {
            log.info("系统内暂无管理员，首个注册用户 {} 已自动成为管理员", user.getUsername());
        }
    }

    private SysRole resolveRegisterRole() {
        if (roleMapper.countUsersWithRoleCode(RbacConstants.ROLE_SYSTEM_ADMIN) == 0) {
            SysRole admin = findRole(RbacConstants.ROLE_SYSTEM_ADMIN);
            if (admin != null) {
                return admin;
            }
        }
        return findRole(RbacConstants.ROLE_STUDENT);
    }

    private SysRole findRole(String roleCode) {
        return roleMapper.selectOne(Wrappers.<SysRole>lambdaQuery().eq(SysRole::getRoleCode, roleCode));
    }

    public LoginResult login(String username, String rawPassword) {
        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, username));
        if (user == null || !BCrypt.checkpw(rawPassword, user.getPasswordHash())) {
            throw new BizException(401, "用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BizException(403, "账号已被禁用");
        }
        StpUtil.login(user.getId());
        String token = StpUtil.getTokenValue();
        List<String> roles = roleMapper.selectRoleCodesByUserId(user.getId());
        List<String> perms = permissionMapper.selectPermCodesByUserId(user.getId());
        return new LoginResult(token, user.getId(), user.getUsername(), user.getNickname(), roles, perms);
    }

    public UserInfo currentUser() {
        Long userId = StpUtil.getLoginIdAsLong();
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(401, "用户不存在");
        }
        return new UserInfo(user,
                roleMapper.selectRoleCodesByUserId(userId),
                permissionMapper.selectPermCodesByUserId(userId));
    }

    public void logout() {
        StpUtil.logout();
    }

    public record LoginResult(String token, Long userId, String username,
                              String nickname, List<String> roles, List<String> permissions) {
    }

    public record UserInfo(SysUser user, List<String> roles, List<String> permissions) {
    }
}
