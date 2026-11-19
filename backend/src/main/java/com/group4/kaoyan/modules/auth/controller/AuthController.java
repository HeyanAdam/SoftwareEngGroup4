package com.group4.kaoyan.modules.auth.controller;

import com.group4.kaoyan.common.R;
import com.group4.kaoyan.modules.auth.dto.LoginRequest;
import com.group4.kaoyan.modules.auth.dto.PasswordResetRequest;
import com.group4.kaoyan.modules.auth.dto.RegisterRequest;
import com.group4.kaoyan.modules.auth.dto.SendCodeRequest;
import com.group4.kaoyan.modules.auth.service.AuthRateLimitService;
import com.group4.kaoyan.modules.auth.service.AuthService;
import com.group4.kaoyan.modules.auth.service.CaptchaService;
import com.group4.kaoyan.modules.auth.service.EmailCodeService;
import com.group4.kaoyan.modules.auth.support.AuthTexts;
import com.group4.kaoyan.modules.auth.support.ClientRequest;
import com.group4.kaoyan.modules.auth.support.EmailCodePurpose;
import com.group4.kaoyan.modules.auth.vo.CaptchaVO;
import com.group4.kaoyan.modules.auth.vo.CurrentUserVO;
import com.group4.kaoyan.modules.auth.vo.LoginVO;
import com.group4.kaoyan.modules.auth.vo.RegisterVO;
import com.group4.kaoyan.modules.auth.vo.SendCodeVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * 认证接口（接口文档第 3 章），统一前缀 /api/v1/auth。
 *
 * <p>公开接口：captcha、verification-codes、register、login、password-reset；
 * 需要登录：me、logout（由 SaInterceptor 统一校验，见 WebConfig）。</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final Duration ONE_MINUTE = Duration.ofMinutes(1);
    private static final Duration ONE_HOUR = Duration.ofHours(1);

    private final AuthService authService;
    private final CaptchaService captchaService;
    private final EmailCodeService emailCodeService;
    private final AuthRateLimitService authRateLimitService;

    /** 图形验证码：同 IP 每分钟最多 30 次 */
    @GetMapping("/captcha")
    public R<CaptchaVO> captcha(HttpServletRequest request) {
        authRateLimitService.require("captcha-ip", ClientRequest.of(request).ipKey(),
                30, ONE_MINUTE, "图形验证码请求过于频繁");
        return R.ok(captchaService.generate());
    }

    /** 发送邮箱验证码：purpose 支持 REGISTER、EMAIL_LOGIN、RESET_PASSWORD */
    @PostMapping("/verification-codes")
    public R<SendCodeVO> sendVerificationCode(@Valid @RequestBody SendCodeRequest body,
                                              HttpServletRequest request) {
        ClientRequest client = ClientRequest.of(request);
        EmailCodePurpose purpose = EmailCodePurpose.parse(body.getPurpose());
        SendCodeVO vo = emailCodeService.send(AuthTexts.normalizeEmail(body.getEmail()), purpose, client);
        return R.ok("验证码已发送", vo);
    }

    /** 注册：要求 Idempotency-Key，成功返回 201 */
    @PostMapping("/register")
    public ResponseEntity<R<RegisterVO>> register(@Valid @RequestBody RegisterRequest body,
                                                  @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                  HttpServletRequest request) {
        ClientRequest client = ClientRequest.of(request);
        authRateLimitService.require("register-ip", client.ipKey(), 20, ONE_HOUR, "注册请求过于频繁");
        RegisterVO vo = authService.register(body, idempotencyKey, client);
        return ResponseEntity.status(HttpStatus.CREATED).body(R.ok("注册成功", vo));
    }

    /** 登录：支持 PASSWORD 与 EMAIL_CODE 两种方式 */
    @PostMapping("/login")
    public R<LoginVO> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
        ClientRequest client = ClientRequest.of(request);
        authRateLimitService.require("login-ip", client.ipKey(), 20, ONE_MINUTE, "登录请求过于频繁");
        return R.ok(authService.login(body, client));
    }

    /** 当前用户：账号、角色与权限快照 */
    @GetMapping("/me")
    public R<CurrentUserVO> me() {
        return R.ok(authService.currentUser());
    }

    /** 退出登录：吊销当前 Token */
    @PostMapping("/logout")
    public R<Void> logout() {
        authService.logout();
        return R.ok("退出成功", null);
    }

    /** 重置密码：要求 Idempotency-Key，成功后该用户全部旧 Token 失效 */
    @PostMapping("/password-reset")
    public R<Void> resetPassword(@Valid @RequestBody PasswordResetRequest body,
                                 @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                 HttpServletRequest request) {
        ClientRequest client = ClientRequest.of(request);
        authRateLimitService.require("password-reset-ip", client.ipKey(), 10, ONE_HOUR, "重置密码请求过于频繁");
        authService.resetPassword(body, idempotencyKey, client);
        return R.ok("密码已重置，请重新登录", null);
    }
}
