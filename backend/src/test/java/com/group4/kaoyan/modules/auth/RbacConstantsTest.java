package com.group4.kaoyan.modules.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 权限字典与角色权限矩阵的回归测试，口径对齐 {@code docs/权限表.md}。
 * 纯常量断言，不依赖 Spring 上下文与数据库。
 */
class RbacConstantsTest {

    private List<String> defaultsOf(String roleCode) {
        return RbacConstants.BUILTIN_ROLES.stream()
                .filter(def -> def.code().equals(roleCode))
                .findFirst()
                .orElseThrow()
                .defaultPermissions();
    }

    @Test
    @DisplayName("权限字典共 20 条，权限码不重复，排序号唯一")
    void permissionCatalogHas20UniqueCodes() {
        List<RbacConstants.PermissionDef> catalog = RbacConstants.PERMISSION_CATALOG;
        assertEquals(20, catalog.size());
        assertEquals(20, catalog.stream().map(RbacConstants.PermissionDef::code).distinct().count());
        assertEquals(20, catalog.stream().map(RbacConstants.PermissionDef::sort).distinct().count());
    }

    @Test
    @DisplayName("V6 已上线的 11 条权限码一个不少")
    void baselinePermissionCodesAreKept() {
        List<String> codes = RbacConstants.allPermissionCodes();
        assertTrue(codes.containsAll(List.of(
                "kb:view", "kb:create", "kb:delete",
                "doc:upload", "doc:delete", "chat:send",
                "admin:user:list", "admin:user:update",
                "admin:role:list", "admin:role:update",
                "admin:vector:manage")));
    }

    @Test
    @DisplayName("扩展的 9 条业务权限码按 module:action 命名")
    void extendedPermissionCodesFollowNamingRule() {
        List<String> extended = RbacConstants.allPermissionCodes().subList(11, 20);
        assertEquals(List.of(
                "study-plan:view", "study-plan:create", "study-plan:update", "study-task:update",
                "question:view",
                "file:view", "file:upload", "file:download", "file:delete"), extended);
    }

    @Test
    @DisplayName("内置角色 4 个，默认授权数量 1 / 6 / 3")
    void builtinRolesMatchMatrix() {
        assertEquals(4, RbacConstants.BUILTIN_ROLES.size());

        assertEquals(List.of(RbacConstants.CHAT_SEND), defaultsOf(RbacConstants.ROLE_STUDENT));

        assertEquals(List.of(
                "kb:view", "kb:create", "kb:delete",
                "doc:upload", "doc:delete", "admin:vector:manage"),
                defaultsOf(RbacConstants.ROLE_CONTENT_OPERATOR));

        assertEquals(List.of("kb:view", "admin:user:list", "admin:role:list"),
                defaultsOf(RbacConstants.ROLE_DATA_AUDITOR));
    }

    @Test
    @DisplayName("SYSTEM_ADMIN 为全权角色，静态断言其独占新增业务权限")
    void systemAdminHoldsAllPermissions() {
        RbacConstants.RoleDef admin = RbacConstants.BUILTIN_ROLES.stream()
                .filter(def -> def.code().equals(RbacConstants.ROLE_SYSTEM_ADMIN))
                .findFirst()
                .orElseThrow();
        assertTrue(admin.grantAllPermissions());
        assertTrue(admin.defaultPermissions().isEmpty());

        // 学生只能问答：不得持有知识库与文件权限
        List<String> student = defaultsOf(RbacConstants.ROLE_STUDENT);
        assertFalse(student.contains(RbacConstants.KB_VIEW));
        assertFalse(student.contains(RbacConstants.FILE_UPLOAD));
    }
}
