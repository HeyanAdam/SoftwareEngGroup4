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
 * 全局异常处理：业务异常 / 参数校验 / 鉴权异常 / 兜底（P2 任务 4）。
 *
 * <p>契约见《接口设计文档 V1.1》第 1.5、1.6、2.1 节：</p>
 * <pre>
 * 未登录 / Token 失效   HTTP 401 + 10001
 * 无接口权限            HTTP 403 + 10008
 * 访问他人数据          HTTP 403 + 10009
 * 参数错误              HTTP 400 + 10000
 * 频率超限              HTTP 429 + 10010
 * 未处理异常            HTTP 500 + 90000
 * </pre>
 *
 * <p>两点约定：</p>
 * <ul>
 *   <li>HTTP 状态码与业务码同时给出，前端既可以按状态码做统一拦截，也可以按 code 做精细提示；</li>
 *   <li>不把异常堆栈或 SQL 细节回给前端，只回可读提示，细节进日志。</li>
 * </ul>
 *
 * <p>分工说明：认证接口（{@code AuthController}）的 400 / 401 / 409 / 423 / 429 由
 * {@code AuthExceptionHandler}（assignableTypes 限定）就地处理；本类负责全局兜底，
 * 认证接口之外抛出的业务异常、Sa-Token 鉴权异常统一走这里。</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：code 由调用方指定（权限 10008、数据归属 10009 等走这里）。 */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<R<Void>> handleBiz(BizException e) {
        return build(e.getCode(), e.getMessage());
    }

    /** 参数校验失败 → 400 + 10000。 */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<R<Void>> handleValid(Exception e) {
        FieldError fe = e instanceof MethodArgumentNotValidException m
                ? m.getBindingResult().getFieldError()
                : ((BindException) e).getBindingResult().getFieldError();
        String msg = fe == null ? "参数校验失败" : fe.getField() + " " + fe.getDefaultMessage();
        return build(ErrorCode.PARAM_INVALID, msg);
    }

    /** 未登录、Token 缺失或过期 → 401 + 10001。 */
    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<R<Void>> handleNotLogin(NotLoginException e) {
        log.info("鉴权失败：{}", e.getType());
        return build(ErrorCode.NOT_LOGIN, "未登录或登录已失效");
    }

    /** 缺少角色 → 403 + 10008（接口权限问题，不是数据问题）。 */
    @ExceptionHandler(NotRoleException.class)
    public ResponseEntity<R<Void>> handleNotRole(NotRoleException e) {
        log.info("缺少角色：{}", e.getRole());
        return build(ErrorCode.NO_PERMISSION, "无接口访问权限");
    }

    /** 缺少权限码 → 403 + 10008。 */
    @ExceptionHandler(NotPermissionException.class)
    public ResponseEntity<R<Void>> handleNotPermission(NotPermissionException e) {
        log.info("缺少权限码：{}", e.getPermission());
        return build(ErrorCode.NO_PERMISSION, "无接口访问权限");
    }

    /** 兜底 → 500 + 90000，细节只进日志。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<R<Void>> handleOther(Exception e) {
        log.error("未处理异常", e);
        return build(ErrorCode.SERVER_ERROR, "服务端异常，请联系管理员");
    }

    /** 统一构造：HTTP 状态码取自 {@link ErrorCode#httpStatusOf(int)}，响应体 code 保持业务码。 */
    private ResponseEntity<R<Void>> build(int code, String message) {
        int http = ErrorCode.httpStatusOf(code);
        HttpStatus status = HttpStatus.resolve(http);
        return ResponseEntity.status(status == null ? HttpStatus.BAD_REQUEST : status)
                .body(R.fail(code, message));
    }
}
