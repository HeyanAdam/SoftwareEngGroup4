package com.group4.kaoyan.modules.auth.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.group4.kaoyan.common.BizException;
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

    public static final String ROLE_STUDENT = "STUDENT";

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;

    @Transactional
    public void register(String username, String rawPassword, String nickname) {
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

        SysRole student = roleMapper.selectOne(Wrappers.<SysRole>lambdaQuery()
                .eq(SysRole::getRoleCode, ROLE_STUDENT));
        if (student != null) {
            roleMapper.bindUserRole(user.getId(), student.getId());
        }
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
