package com.group4.kaoyan.modules.auth.support;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 认证模块异常：同时携带业务码和 HTTP 状态码。
 *
 * <p>接口文档要求「HTTP 状态码表达传输和资源语义，code 表达业务结果」，
 * 因此认证接口不能只改响应体里的 code，必须把 401/403/409/423/429 一并写回状态行。
 * 该异常由 {@link AuthExceptionHandler} 统一转换成 {@code {code,message,data}}。</p>
 */
@Getter
public class AuthException extends RuntimeException {

    private final int code;
    private final HttpStatus status;
    /** 需要回写 Retry-After 头的秒数，可为空 */
    private final Long retryAfterSeconds;

    public AuthException(int code, HttpStatus status, String message) {
        this(code, status, message, null);
    }

    public AuthException(int code, HttpStatus status, String message, Long retryAfterSeconds) {
        super(message);
        this.code = code;
        this.status = status;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public static AuthException paramInvalid(String message) {
        return new AuthException(AuthErrorCode.PARAM_INVALID, HttpStatus.BAD_REQUEST, message);
    }

    /** 统一登录失败提示：不区分「账号不存在」和「密码错误」。 */
    public static AuthException badCredentials() {
        return new AuthException(AuthErrorCode.BAD_CREDENTIALS, HttpStatus.UNAUTHORIZED, "账号或密码错误");
    }

    public static AuthException codeInvalid(String message) {
        return new AuthException(AuthErrorCode.CODE_INVALID, HttpStatus.BAD_REQUEST, message);
    }

    public static AuthException passwordMismatch() {
        return new AuthException(AuthErrorCode.PASSWORD_MISMATCH, HttpStatus.BAD_REQUEST, "两次输入的密码不一致");
    }

    public static AuthException accountExists(String message) {
        return new AuthException(AuthErrorCode.ACCOUNT_EXISTS, HttpStatus.CONFLICT, message);
    }

    public static AuthException userNotFound(String message) {
        return new AuthException(AuthErrorCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND, message);
    }

    public static AuthException disabled() {
        return new AuthException(AuthErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "账号已被禁用，请联系管理员");
    }

    public static AuthException locked(long retryAfterSeconds) {
        return new AuthException(AuthErrorCode.ACCOUNT_LOCKED, HttpStatus.LOCKED,
                "账号已被锁定，请 " + hoursAndMinutes(retryAfterSeconds) + "后再试", retryAfterSeconds);
    }

    public static AuthException tooManyRequests(String message, long retryAfterSeconds) {
        return new AuthException(AuthErrorCode.TOO_MANY_REQUESTS, HttpStatus.TOO_MANY_REQUESTS,
                message, retryAfterSeconds);
    }

    public static AuthException idempotencyInProgress() {
        return new AuthException(AuthErrorCode.IDEMPOTENCY_IN_PROGRESS, HttpStatus.CONFLICT,
                "幂等键对应请求仍在处理中，请稍后重试", 1L);
    }

    public static AuthException serviceUnavailable(String message) {
        return new AuthException(AuthErrorCode.SERVER_ERROR, HttpStatus.SERVICE_UNAVAILABLE, message);
    }

    private static String hoursAndMinutes(long seconds) {
        long safe = Math.max(seconds, 0);
        if (safe < 60) {
            return safe + " 秒";
        }
        long minutes = (safe + 59) / 60;
        if (minutes < 60) {
            return minutes + " 分钟";
        }
        return (minutes / 60) + " 小时 " + (minutes % 60) + " 分钟";
    }
}
