package com.group4.kaoyan.modules.user.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.group4.kaoyan.common.BizException;
import com.group4.kaoyan.modules.user.entity.SysUser;
import com.group4.kaoyan.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 用户资料：本人的昵称、邮箱、头像与密码维护。
 * 账号本身（登录名、角色、启停）由 auth / admin 模块负责。
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;

    public SysUser profile() {
        SysUser user = userMapper.selectById(StpUtil.getLoginIdAsLong());
        if (user == null) {
            throw new BizException(404, "用户不存在");
        }
        return user;
    }

    public SysUser updateProfile(String nickname, String email, String avatar) {
        Long userId = StpUtil.getLoginIdAsLong();
        SysUser update = new SysUser();
        update.setId(userId);
        update.setNickname(nickname);
        update.setEmail(email);
        update.setAvatar(avatar);
        update.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(update);
        return userMapper.selectById(userId);
    }

    public void changePassword(String oldPassword, String newPassword) {
        if (newPassword == null || newPassword.length() < 6) {
            throw new BizException("新密码至少 6 位");
        }
        SysUser user = profile();
        if (!BCrypt.checkpw(oldPassword, user.getPasswordHash())) {
            throw new BizException("原密码不正确");
        }
        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setPasswordHash(BCrypt.hashpw(newPassword));
        update.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(update);
    }

    /** 供其他模块按登录名查用户，避免各处重复拼条件 */
    public SysUser findByUsername(String username) {
        return userMapper.selectOne(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username));
    }
}
