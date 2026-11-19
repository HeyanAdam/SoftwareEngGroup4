package com.group4.kaoyan.modules.user.controller;

import com.group4.kaoyan.common.R;
import com.group4.kaoyan.modules.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户账号辅助接口（需登录，不做角色限制）。
 *
 * <p>资料读写（GET/PUT /profile）已并入 {@link UserProfileController}，
 * 这里只保留改密 —— 该接口《接口设计文档 V1.1 适配版》未登记，
 * 属于实现侧扩展：文档口径的“重置密码”走 3.6 节 /api/v1/auth/password-reset，
 * 本接口是“已登录状态下修改本人密码”，两者并存不冲突。</p>
 */
@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    public record PasswordReq(String oldPassword, String newPassword) {
    }

    @PutMapping("/password")
    public R<Void> changePassword(@RequestBody PasswordReq req) {
        userService.changePassword(req.oldPassword(), req.newPassword());
        return R.ok();
    }
}
