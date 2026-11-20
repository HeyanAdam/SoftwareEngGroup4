package com.group4.kaoyan.modules.auth;

import com.group4.kaoyan.modules.auth.support.AuthException;
import com.group4.kaoyan.modules.auth.support.PasswordPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 密码强度规则：8-32 位、字母 + 数字、无空格、两次输入一致。 */
class PasswordPolicyTest {

    @Test
    @DisplayName("合法密码通过校验")
    void shouldAcceptValidPassword() {
        assertDoesNotThrow(() -> PasswordPolicy.validate("KaoYan@2026"));
    }

    @Test
    @DisplayName("长度不足 8 位被拒绝")
    void shouldRejectShortPassword() {
        AuthException e = assertThrows(AuthException.class, () -> PasswordPolicy.validate("Ka1@"));
        assertEquals(10000, e.getCode());
    }

    @Test
    @DisplayName("纯字母或纯数字被拒绝")
    void shouldRequireLetterAndDigit() {
        assertThrows(AuthException.class, () -> PasswordPolicy.validate("kaoyankaoyan"));
        assertThrows(AuthException.class, () -> PasswordPolicy.validate("2026202620"));
    }

    @Test
    @DisplayName("包含空格被拒绝")
    void shouldRejectWhitespace() {
        assertThrows(AuthException.class, () -> PasswordPolicy.validate("Kao Yan 2026"));
    }

    @Test
    @DisplayName("两次输入不一致返回 10006")
    void shouldRejectMismatch() {
        AuthException e = assertThrows(AuthException.class,
                () -> PasswordPolicy.validateMatch("KaoYan@2026", "KaoYan@2027"));
        assertEquals(10006, e.getCode());
    }
}
