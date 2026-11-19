package com.group4.kaoyan.modules.auth.controller;

import com.group4.kaoyan.common.R;
import com.group4.kaoyan.modules.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    public record RegisterReq(String username, String password, String nickname) {
    }

    public record LoginReq(String username, String password) {
    }

    @PostMapping("/register")
    public R<Void> register(@RequestBody RegisterReq req) {
        authService.register(req.username(), req.password(), req.nickname());
        return R.ok();
    }

    @PostMapping("/login")
    public R<AuthService.LoginResult> login(@RequestBody LoginReq req) {
        return R.ok(authService.login(req.username(), req.password()));
    }

    @GetMapping("/me")
    public R<AuthService.UserInfo> me() {
        return R.ok(authService.currentUser());
    }

    @PostMapping("/logout")
    public R<Void> logout() {
        authService.logout();
        return R.ok();
    }
}
