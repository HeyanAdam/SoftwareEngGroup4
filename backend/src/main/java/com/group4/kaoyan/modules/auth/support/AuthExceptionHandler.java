package com.group4.kaoyan.modules.auth.support;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.group4.kaoyan.common.R;
import com.group4.kaoyan.modules.auth.controller.AuthController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 认证接口专用异常处理。
 *
 * <p>只作用于 {@code AuthController}（assignableTypes 限定），避免和 P2 负责的全局
 * {@code GlobalExceptionHandler} 抢同一个 {@code BizException} 处理器：P2 继续统一
 * 401/403/10008/10009 的全局映射，认证接口的 400/401/409/423/429 由本类就地处理。</p>
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = AuthController.class)
public class AuthExceptionHandler {

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<R<Void>> handleAuth(AuthException e) {
        log.warn("认证接口返回异常: code={}, status={}, message={}", e.getCode(), e.getStatus().value(), e.getMessage());
        return build(e.getStatus(), R.fail(e.getCode(), e.getMessage()), e.getRetryAfterSeconds());
    }

    /** 参数校验（@Valid）失败：400 + 10000。 */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<R<Void>> handleValidation(Exception e) {
        FieldError fieldError = e instanceof MethodArgumentNotValidException m
                ? m.getBindingResult().getFieldError()
                : ((BindException) e).getBindingResult().getFieldError();
        String message = fieldError == null
                ? "参数校验失败"
                : fieldError.getField() + " " + fieldError.getDefaultMessage();
        return build(HttpStatus.BAD_REQUEST, R.fail(AuthErrorCode.PARAM_INVALID, message), null);
    }

    /** 请求体不是合法 JSON 或字段类型错误：400 + 10000。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<R<Void>> handleUnreadable(HttpMessageNotReadableException e) {
        return build(HttpStatus.BAD_REQUEST, R.fail(AuthErrorCode.PARAM_INVALID, "请求体格式不正确"), null);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<R<Void>> handleMissingHeader(MissingRequestHeaderException e) {
        return build(HttpStatus.BAD_REQUEST, R.fail(AuthErrorCode.PARAM_INVALID, "缺少请求头 " + e.getHeaderName()), null);
    }

    /** HTTP 方法不支持：405 + 10012。 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<R<Void>> handleMethod(HttpRequestMethodNotSupportedException e) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, R.fail(AuthErrorCode.UNSUPPORTED_REQUEST, "请求类型不受支持"), null);
    }

    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<R<Void>> handleNotLogin(NotLoginException e) {
        return build(HttpStatus.UNAUTHORIZED, R.fail(AuthErrorCode.NOT_LOGIN, "未登录或登录已失效"), null);
    }

    @ExceptionHandler({NotPermissionException.class, NotRoleException.class})
    public ResponseEntity<R<Void>> handleForbidden(Exception e) {
        return build(HttpStatus.FORBIDDEN, R.fail(AuthErrorCode.FORBIDDEN, "无接口访问权限"), null);
    }

    /** 唯一键冲突（并发注册同名/同邮箱）：409 + 10004。 */
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<R<Void>> handleDuplicate(DuplicateKeyException e) {
        log.warn("认证接口唯一键冲突: {}", e.getMessage());
        return build(HttpStatus.CONFLICT, R.fail(AuthErrorCode.ACCOUNT_EXISTS, "账号或邮箱已存在"), null);
    }

    private ResponseEntity<R<Void>> build(HttpStatus status, R<Void> body, Long retryAfterSeconds) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status);
        if (retryAfterSeconds != null) {
            builder.header(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(retryAfterSeconds, 1L)));
        }
        return builder.body(body);
    }
}
