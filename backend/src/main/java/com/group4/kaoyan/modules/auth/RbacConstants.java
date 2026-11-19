package com.group4.kaoyan.modules.auth;

/**
 * RBAC 角色编码。常量值必须与 Flyway V6 种子保持一致。
 */
public final class RbacConstants {

    public static final String ROLE_STUDENT = "STUDENT";
    public static final String ROLE_CONTENT_OPERATOR = "CONTENT_OPERATOR";
    public static final String ROLE_DATA_AUDITOR = "DATA_AUDITOR";
    public static final String ROLE_SYSTEM_ADMIN = "SYSTEM_ADMIN";

    private RbacConstants() {
    }
}
