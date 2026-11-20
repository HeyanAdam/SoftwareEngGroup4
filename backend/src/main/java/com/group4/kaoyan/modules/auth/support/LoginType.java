package com.group4.kaoyan.modules.auth.support;

/**
 * 登录方式。取值必须与接口文档 3.3 节一致：PASSWORD、EMAIL_CODE。
 */
public enum LoginType {

    PASSWORD,
    EMAIL_CODE;

    public static LoginType parse(String value) {
        if (value == null || value.isBlank()) {
            throw AuthException.paramInvalid("login_type 不能为空");
        }
        for (LoginType type : values()) {
            if (type.name().equalsIgnoreCase(value.trim())) {
                return type;
            }
        }
        throw AuthException.paramInvalid("login_type 仅支持 PASSWORD、EMAIL_CODE");
    }
}
