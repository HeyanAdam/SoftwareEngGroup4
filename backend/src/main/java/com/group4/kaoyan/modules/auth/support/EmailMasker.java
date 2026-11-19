package com.group4.kaoyan.modules.auth.support;

/**
 * 邮箱脱敏：接口只返回 email_masked，禁止把完整邮箱回显给前端。
 * 示例：student@example.com -> stu***@example.com
 */
public final class EmailMasker {

    private static final int VISIBLE_PREFIX = 3;

    private EmailMasker() {
    }

    public static String mask(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 0 || atIndex == email.length() - 1) {
            return "***";
        }
        String local = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        if (local.length() > VISIBLE_PREFIX) {
            return local.substring(0, VISIBLE_PREFIX) + "***" + domain;
        }
        return local.charAt(0) + "***" + domain;
    }
}
