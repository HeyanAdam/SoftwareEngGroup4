package com.group4.kaoyan.modules.auth.support;

/**
 * 邮箱验证码用途。取值必须与接口文档 3.1 节一致：REGISTER、EMAIL_LOGIN、RESET_PASSWORD。
 */
public enum EmailCodePurpose {

    REGISTER("注册验证", "注册"),
    EMAIL_LOGIN("邮箱验证码登录", "登录"),
    RESET_PASSWORD("重置密码", "重置密码");

    private final String subjectAction;
    private final String description;

    EmailCodePurpose(String subjectAction, String description) {
        this.subjectAction = subjectAction;
        this.description = description;
    }

    public String subjectAction() {
        return subjectAction;
    }

    public String description() {
        return description;
    }

    public static EmailCodePurpose parse(String value) {
        if (value == null || value.isBlank()) {
            throw AuthException.paramInvalid("purpose 不能为空");
        }
        for (EmailCodePurpose purpose : values()) {
            if (purpose.name().equalsIgnoreCase(value.trim())) {
                return purpose;
            }
        }
        throw AuthException.paramInvalid("purpose 仅支持 REGISTER、EMAIL_LOGIN、RESET_PASSWORD");
    }
}
