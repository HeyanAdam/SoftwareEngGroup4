package com.group4.kaoyan.common;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理：业务异常 / 参数校验 / 鉴权异常 / 兜底。
 *
 * <p>鉴权三兄弟的口径按《接口设计文档 V1.1》2.1 与 P2 交付说明对齐：</p>
 * <ul>
 *   <li>未登录 / Token 失效 → HTTP 401 + 10001；</li>
 *   <li>缺少角色或权限码 → HTTP 403 + 10008；</li>
 *   <li>越权访问他人数据 → HTTP 403 + 10009（由 P2 的 DataScope 校验抛出）。</li>
 * </ul>
 *
 * <p>说明：这段属于 P2 任务 4 的全局 401/403 映射，P1 在集成分支上先把状态行补齐，
 * 保证前端拿到的是真 HTTP 状态而不是 200 包一个 code；P2 后续重写请保留该口径。</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public R<Void> handleBiz(BizException e) {
        return R.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public R<Void> handleValid(Exception e) {
        FieldError fe = e instanceof MethodArgumentNotValidException m
                ? m.getBindingResult().getFieldError()
                : ((BindException) e).getBindingResult().getFieldError();
        String msg = fe == null ? "参数校验失败" : fe.getField() + " " + fe.getDefaultMessage();
        return R.fail(10000, msg);
    }

    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<R<Void>> handleNotLogin(NotLoginException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(R.fail(10001, "未登录或登录已失效"));
    }

    @ExceptionHandler(NotRoleException.class)
    public ResponseEntity<R<Void>> handleNotRole(NotRoleException e) {
        log.warn("缺少角色: {}", e.getRole());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(R.fail(10008, "无接口访问权限"));
    }

    @ExceptionHandler(NotPermissionException.class)
    public ResponseEntity<R<Void>> handleNotPermission(NotPermissionException e) {
        log.warn("缺少权限码: {}", e.getPermission());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(R.fail(10008, "无接口访问权限"));
    }

    @ExceptionHandler(Exception.class)
    public R<Void> handleOther(Exception e) {
        log.error("未处理异常", e);
        return R.fail(90000, "服务器内部异常");
    }
}
