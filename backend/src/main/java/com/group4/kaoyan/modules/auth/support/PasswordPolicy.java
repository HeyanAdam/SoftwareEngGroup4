package com.group4.kaoyan.modules.auth.support;

/**
 * 密码强度规则（注册与重置密码共用）。
 * 规则：8-32 位，必须同时包含字母和数字，不允许空白字符。
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 32;

    private PasswordPolicy() {
    }

    public static void validate(String rawPassword) {
        String password = AuthTexts.requireText(rawPassword, "密码不能为空");
        if (password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            throw AuthException.paramInvalid("密码长度需为 " + MIN_LENGTH + "-" + MAX_LENGTH + " 位");
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (char c : password.toCharArray()) {
            if (Character.isWhitespace(c)) {
                throw AuthException.paramInvalid("密码不能包含空格");
            }
            if (Character.isLetter(c)) {
                hasLetter = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            }
        }
        if (!hasLetter || !hasDigit) {
            throw AuthException.paramInvalid("密码必须同时包含字母和数字");
        }
    }

    public static void validateMatch(String rawPassword, String confirmPassword) {
        validate(rawPassword);
        if (confirmPassword == null || !rawPassword.trim().equals(confirmPassword.trim())) {
            throw AuthException.passwordMismatch();
        }
    }
}
