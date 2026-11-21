package com.group4.kaoyan.modules.auth.support;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 认证模块的文本规范化与格式校验：账号、邮箱统一在这里处理，
 * 保证「查询用户」「Redis Key」「登录日志」使用同一份规范化结果。
 */
public final class AuthTexts {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    public static final int USERNAME_MAX_LENGTH = 32;
    public static final int EMAIL_MAX_LENGTH = 128;
    public static final int NICKNAME_MAX_LENGTH = 64;

    private AuthTexts() {
    }

    public static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw AuthException.paramInvalid(message);
        }
        return value.trim();
    }

    /**
     * 用户名只做「非空 + 长度上限」校验，不再限制字符集：
     * 中文、字母、数字、下划线等都可以作为登录名。
     */
    public static String normalizeUsername(String username) {
        String value = requireText(username, "用户名不能为空");
        if (value.length() > USERNAME_MAX_LENGTH) {
            throw AuthException.paramInvalid("用户名最多 32 个字符");
        }
        return value;
    }

    public static String normalizeEmail(String email) {
        String value = requireText(email, "邮箱不能为空").toLowerCase(Locale.ROOT);
        if (value.length() > EMAIL_MAX_LENGTH || !EMAIL_PATTERN.matcher(value).matches()) {
            throw AuthException.paramInvalid("邮箱格式不正确");
        }
        return value;
    }

    public static String normalizeNickname(String nickname, String fallback) {
        String value = nickname == null || nickname.isBlank() ? fallback : nickname.trim();
        if (value == null || value.isBlank()) {
            throw AuthException.paramInvalid("昵称不能为空");
        }
        return value.length() <= NICKNAME_MAX_LENGTH ? value : value.substring(0, NICKNAME_MAX_LENGTH);
    }

    public static String limit(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
