package com.group4.kaoyan.modules.auth;

import com.group4.kaoyan.modules.auth.support.AuthException;
import com.group4.kaoyan.modules.auth.support.AuthTexts;
import com.group4.kaoyan.modules.auth.support.EmailMasker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 账号/邮箱规范化与邮箱脱敏。 */
class AuthTextsTest {

    @Test
    @DisplayName("邮箱去空格并转小写")
    void shouldNormalizeEmail() {
        assertEquals("student@example.com", AuthTexts.normalizeEmail("  Student@Example.COM "));
    }

    @Test
    @DisplayName("非法邮箱返回 10000")
    void shouldRejectInvalidEmail() {
        AuthException e = assertThrows(AuthException.class, () -> AuthTexts.normalizeEmail("student.example.com"));
        assertEquals(10000, e.getCode());
    }

    @Test
    @DisplayName("用户名只允许字母数字下划线，3-32 位")
    void shouldValidateUsername() {
        assertEquals("kaoyan_student", AuthTexts.normalizeUsername(" kaoyan_student "));
        assertThrows(AuthException.class, () -> AuthTexts.normalizeUsername("考研小白"));
        assertThrows(AuthException.class, () -> AuthTexts.normalizeUsername("ab"));
    }

    @Test
    @DisplayName("邮箱脱敏只保留前 3 位")
    void shouldMaskEmail() {
        assertEquals("stu***@example.com", EmailMasker.mask("student@example.com"));
        assertEquals("a***@example.com", EmailMasker.mask("a@example.com"));
        assertNull(EmailMasker.mask(null));
    }
}
