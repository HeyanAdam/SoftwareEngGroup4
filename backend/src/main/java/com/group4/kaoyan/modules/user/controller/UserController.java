package com.group4.kaoyan.modules.user.controller;

import com.group4.kaoyan.common.R;
import com.group4.kaoyan.modules.user.entity.SysUser;
import com.group4.kaoyan.modules.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户资料（需登录，不做角色限制）。
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    public record ProfileReq(String nickname, String email, String avatar) {
    }

    public record PasswordReq(String oldPassword, String newPassword) {
    }

    @GetMapping("/profile")
    public R<SysUser> profile() {
        return R.ok(userService.profile());
    }

    @PutMapping("/profile")
    public R<SysUser> update(@RequestBody ProfileReq req) {
        return R.ok(userService.updateProfile(req.nickname(), req.email(), req.avatar()));
    }

    @PutMapping("/password")
    public R<Void> changePassword(@RequestBody PasswordReq req) {
        userService.changePassword(req.oldPassword(), req.newPassword());
        return R.ok();
    }
}
