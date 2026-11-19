package com.group4.kaoyan.common;

import java.util.Map;

/**
 * 全局业务码（与《接口设计文档 V1.1》第 2.1 节逐条对齐）。
 *
 * <p>文档规定：HTTP 状态码表达传输与资源语义，{@code code} 表达业务结果。
 * 之前项目里直接拿 HTTP 序号当业务码（401/403/500），前端没法区分
 * "登录过期"和"密码错误"，也没法区分"无接口权限"和"无权访问该数据"，
 * 所以统一收敛到本类。</p>
 */
public final class ErrorCode {

    private ErrorCode() {
    }

    /** 参数校验失败（HTTP 400）。 */
    public static final int PARAM_INVALID = 10000;
    /** 未登录、Token 缺失/无效/过期（HTTP 401）。 */
    public static final int NOT_LOGIN = 10001;
    /** 账号或密码错误（HTTP 401）。 */
    public static final int BAD_CREDENTIALS = 10002;
    /** 账号已被锁定（HTTP 423）。 */
    public static final int ACCOUNT_LOCKED = 10003;
    /** 账号或邮箱已存在（HTTP 409）。 */
    public static final int ACCOUNT_EXISTS = 10004;
    /** 验证码错误或已过期（HTTP 400）。 */
    public static final int VERIFY_CODE_INVALID = 10005;
    /** 两次输入的密码不一致（HTTP 400）。 */
    public static final int PASSWORD_MISMATCH = 10006;
    /** 用户不存在（HTTP 404）。 */
    public static final int USER_NOT_FOUND = 10007;
    /** 无接口访问权限：缺少权限码或角色（HTTP 403）。 */
    public static final int NO_PERMISSION = 10008;
    /** 无权访问该数据：访问他人资源或数据范围不符（HTTP 403）。 */
    public static final int NO_DATA_ACCESS = 10009;
    /** 操作过于频繁（HTTP 429）。 */
    public static final int TOO_MANY_REQUESTS = 10010;
    /** 账号已被禁用（HTTP 403）：登录入口与鉴权链（AccountStatusGuard）共用同一码。 */
    public static final int ACCOUNT_DISABLED = 10013;
    /** 未分类服务器异常（HTTP 500）。 */
    public static final int SERVER_ERROR = 90000;

    /** 业务码 → HTTP 状态码。 */
    private static final Map<Integer, Integer> HTTP_STATUS = Map.ofEntries(
            Map.entry(PARAM_INVALID, 400),
            Map.entry(NOT_LOGIN, 401),
            Map.entry(BAD_CREDENTIALS, 401),
            Map.entry(ACCOUNT_LOCKED, 423),
            Map.entry(ACCOUNT_EXISTS, 409),
            Map.entry(VERIFY_CODE_INVALID, 400),
            Map.entry(PASSWORD_MISMATCH, 400),
            Map.entry(USER_NOT_FOUND, 404),
            Map.entry(NO_PERMISSION, 403),
            Map.entry(NO_DATA_ACCESS, 403),
            Map.entry(TOO_MANY_REQUESTS, 429),
            // 与 P1 的 AuthErrorCode 对齐（10011 幂等键处理中 / 10012 请求类型不支持），
            // 这些码由 AuthExceptionHandler 就地给出状态，这里补映射避免走兜底落到 400。
            Map.entry(10011, 409),
            Map.entry(10012, 400),
            Map.entry(ACCOUNT_DISABLED, 403),
            Map.entry(SERVER_ERROR, 500)
    );

    /**
     * 业务码对应的 HTTP 状态码。
     *
     * <p>兼容旧代码：本项目历史上有直接抛 {@code new BizException(401, ...)} 的写法，
     * 这类 4xx/5xx 序号按 HTTP 语义原样使用，不强行改写，避免旧调用点集体变 400。</p>
     */
    public static int httpStatusOf(int code) {
        Integer mapped = HTTP_STATUS.get(code);
        if (mapped != null) {
            return mapped;
        }
        if (code >= 400 && code <= 599) {
            return code;
        }
        return 400;
    }
}
