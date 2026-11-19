package com.group4.kaoyan.modules.auth.support;

/**
 * 认证与账号错误码。
 * 取值必须与《考研AI导学系统接口设计文档 V1.1》第 2.1 节「通用与认证错误码」保持一致，
 * 不允许在本类之外硬编码认证错误码。
 */
public final class AuthErrorCode {

    /** 参数校验失败（400） */
    public static final int PARAM_INVALID = 10000;
    /** 未登录或登录已失效（401） */
    public static final int NOT_LOGIN = 10001;
    /** 账号或密码错误（401） */
    public static final int BAD_CREDENTIALS = 10002;
    /** 账号已被锁定（423） */
    public static final int ACCOUNT_LOCKED = 10003;
    /** 账号或邮箱已存在（409） */
    public static final int ACCOUNT_EXISTS = 10004;
    /** 验证码错误或已过期（400） */
    public static final int CODE_INVALID = 10005;
    /** 两次输入的密码不一致（400） */
    public static final int PASSWORD_MISMATCH = 10006;
    /** 用户不存在（404） */
    public static final int USER_NOT_FOUND = 10007;
    /** 无接口访问权限（403） */
    public static final int FORBIDDEN = 10008;
    /** 操作过于频繁（429） */
    public static final int TOO_MANY_REQUESTS = 10010;
    /** 幂等键对应请求仍在处理中（409） */
    public static final int IDEMPOTENCY_IN_PROGRESS = 10011;
    /** 请求类型不受支持（405） */
    public static final int UNSUPPORTED_REQUEST = 10012;
    /** 服务器内部异常（500） */
    public static final int SERVER_ERROR = 90000;

    private AuthErrorCode() {
    }
}
