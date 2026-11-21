package com.group4.kaoyan.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P2 任务 1 的守卫测试：权限码清单和角色矩阵必须自洽。
 *
 * <p>角色码、授权数量以 GitHub main 分支 {@code V6__seed_rbac_dictionary.sql} 为准：
 * SYSTEM_ADMIN=11（扩到 20）、CONTENT_OPERATOR=6、DATA_AUDITOR=3、STUDENT=1。</p>
 *
 * <p>这些断言不依赖 Spring 容器和数据库，{@code mvn -Dtest=PermissionCatalogTest test} 秒级跑完。</p>
 */
@DisplayName("P2-权限码清单与角色权限矩阵")
class PermissionCatalogTest {

    /** 权限码总数：11 条 V6 已有 + 9 条业务域新增。 */
    private static final int EXPECTED_PERMISSION_COUNT = 20;

    /** 权限码命名规范：module:action，小写、中划线分段，可多级（admin:user:list）。 */
    private static final Pattern CODE_PATTERN = Pattern.compile("^[a-z][a-z0-9-]*(:[a-z][a-z0-9-]*)+$");

    @Test
    @DisplayName("权限码共 20 条：11 条 V6 沿用 + 9 条新增")
    void catalogHasExpectedSize() {
        assertEquals(EXPECTED_PERMISSION_COUNT, PermissionConstants.CATALOG.size(),
                "权限码数量应为 11 条 V6 基线 + 9 条业务域新增");
        assertEquals(EXPECTED_PERMISSION_COUNT, PermissionConstants.ALL_CODES.size(),
                "ALL_CODES 必须与 CATALOG 条数一致");
    }

    @Test
    @DisplayName("main 分支 V6 已上线的 11 条权限码一个都不能改")
    void v6PermissionsArePreserved() {
        List<String> v6 = List.of(
                "kb:view", "kb:create", "kb:delete",
                "doc:upload", "doc:delete",
                "chat:send",
                "admin:user:list", "admin:user:update",
                "admin:role:list", "admin:role:update",
                "admin:vector:manage");
        for (String code : v6) {
            assertTrue(PermissionConstants.isDefined(code),
                    "V6 已上线的权限码被误删或改名：" + code);
        }
    }

    @Test
    @DisplayName("权限码不重复，且符合 module:action 命名规范")
    void codesAreUniqueAndWellFormed() {
        List<String> codes = new ArrayList<>(PermissionConstants.ALL_CODES);
        Set<String> distinct = new HashSet<>(codes);
        assertEquals(codes.size(), distinct.size(), "存在重复权限码：" + codes);

        for (String code : codes) {
            assertNotNull(code, "权限码不能为 null");
            assertTrue(CODE_PATTERN.matcher(code).matches(),
                    "权限码格式不合法（应形如 study-plan:view）：" + code);
        }
    }

    @Test
    @DisplayName("扩展的 9 条权限码：学习计划 4 + 题库 1 + 文件 4")
    void extendedCodesAreTheExpectedNine() {
        List<String> v6 = List.of(
                "kb:view", "kb:create", "kb:delete",
                "doc:upload", "doc:delete",
                "chat:send",
                "admin:user:list", "admin:user:update",
                "admin:role:list", "admin:role:update",
                "admin:vector:manage");
        List<String> extended = PermissionConstants.ALL_CODES.stream()
                .filter(code -> !v6.contains(code))
                .toList();
        assertEquals(List.of(
                "study-plan:view", "study-plan:create", "study-plan:update", "study-task:update",
                "question:view",
                "file:view", "file:upload", "file:download", "file:delete"), extended,
                "新增的 9 条业务权限码必须是这 9 条，顺序与字典一致");
    }

    @Test
    @DisplayName("每个权限码都有中文名和所属模块")
    void metadataIsComplete() {
        for (PermissionConstants.PermissionDef def : PermissionConstants.CATALOG) {
            assertFalse(def.name() == null || def.name().isBlank(), "权限名不能为空：" + def.code());
            assertFalse(def.module() == null || def.module().isBlank(), "模块名不能为空：" + def.code());
            assertTrue(def.sort() > 0, "排序号必须为正数：" + def.code());
            assertEquals(def.module(), PermissionConstants.moduleOf(def.code()),
                    "moduleOf 查询结果应与元数据一致：" + def.code());
        }
        assertFalse(PermissionConstants.isDefined("kb:veiw"), "拼错的权限码不应被识别");
    }

    @Test
    @DisplayName("内置角色是官方 4 个，且不含已废弃的旧角色码")
    void builtinRolesMatchOfficialDefinition() {
        Set<String> roleCodes = new HashSet<>();
        for (RoleConstants.RoleDef def : RoleConstants.BUILTIN_ROLES) {
            assertTrue(roleCodes.add(def.code()), "角色码重复：" + def.code());
            assertNotNull(def.defaultDataScope(), "角色默认数据范围不能为空：" + def.code());
            assertTrue(def.name() != null && !def.name().isBlank(), "角色中文名不能为空：" + def.code());
        }
        assertEquals(4, RoleConstants.BUILTIN_ROLES.size(), "官方内置角色为 4 个");
        assertTrue(roleCodes.containsAll(List.of(
                "SYSTEM_ADMIN", "STUDENT", "CONTENT_OPERATOR", "DATA_AUDITOR")), "缺少官方角色码");

        assertFalse(RoleConstants.isBuiltin("ADMIN"), "ADMIN 已被 V6 迁移为 SYSTEM_ADMIN，不能再当内置角色");
        assertFalse(RoleConstants.isBuiltin("KB_ADMIN"), "KB_ADMIN 是自拟角色码，应使用 CONTENT_OPERATOR");
        assertFalse(RoleConstants.isBuiltin("DATA_VIEWER"), "DATA_VIEWER 是自拟角色码，应使用 DATA_AUDITOR");
    }

    @Test
    @DisplayName("角色默认授权的权限码都已在字典登记")
    void roleGrantsOnlyDefinedPermissions() {
        for (RoleConstants.RoleDef def : RoleConstants.BUILTIN_ROLES) {
            for (String permCode : def.defaultPermissions()) {
                assertTrue(PermissionConstants.isDefined(permCode),
                        "角色 " + def.code() + " 的默认授权未登记：" + permCode);
            }
        }
    }

    @Test
    @DisplayName("SYSTEM_ADMIN 是内置全权角色，数据范围 ALL")
    void systemAdminHasAllPermissions() {
        RoleConstants.RoleDef admin = RoleConstants.find(RoleConstants.SYSTEM_ADMIN).orElseThrow();
        assertTrue(admin.grantAllPermissions(), "SYSTEM_ADMIN 必须是全权角色");
        assertEquals(DataScope.ALL, admin.defaultDataScope(), "SYSTEM_ADMIN 数据范围应为 ALL");
    }

    @Test
    @DisplayName("STUDENT 拥有问答、文件与学习计划权限：不得直接浏览知识库（接口文档 11.5）")
    void studentHasExpectedDefaultPermissions() {
        RoleConstants.RoleDef student = RoleConstants.find(RoleConstants.STUDENT).orElseThrow();
        assertEquals(List.of(PermissionConstants.CHAT_SEND,
                        PermissionConstants.FILE_VIEW, PermissionConstants.FILE_UPLOAD,
                        PermissionConstants.FILE_DOWNLOAD, PermissionConstants.FILE_DELETE,
                        PermissionConstants.STUDY_PLAN_VIEW, PermissionConstants.STUDY_PLAN_CREATE,
                        PermissionConstants.STUDY_PLAN_UPDATE, PermissionConstants.STUDY_TASK_UPDATE),
                student.defaultPermissions(),
                "STUDENT 的官方授权应包含问答、文件和学习计划权限");
        assertEquals(DataScope.SELF, student.defaultDataScope(), "学生数据范围应为 SELF");
        assertFalse(student.defaultPermissions().contains(PermissionConstants.KB_VIEW),
                "普通学生不得直接浏览知识库");
        assertFalse(student.defaultPermissions().contains(PermissionConstants.DOC_UPLOAD),
                "普通学生不得直接上传知识文档");
        assertFalse(student.defaultPermissions().contains(PermissionConstants.ADMIN_USER_LIST),
                "普通学生不得进入后台");
    }

    @Test
    @DisplayName("CONTENT_OPERATOR 与 DATA_AUDITOR 的授权数量与 V6 一致")
    void operatorAndAuditorGrantsMatchV6() {
        RoleConstants.RoleDef operator = RoleConstants.find(RoleConstants.CONTENT_OPERATOR).orElseThrow();
        assertEquals(6, operator.defaultPermissions().size(), "内容运营应为 6 条权限");
        assertTrue(operator.defaultPermissions().containsAll(List.of(
                PermissionConstants.KB_VIEW, PermissionConstants.KB_CREATE, PermissionConstants.KB_DELETE,
                PermissionConstants.DOC_UPLOAD, PermissionConstants.DOC_DELETE,
                PermissionConstants.ADMIN_VECTOR_MANAGE)), "内容运营授权应符合 V6");
        assertEquals(DataScope.ALL, operator.defaultDataScope());

        RoleConstants.RoleDef auditor = RoleConstants.find(RoleConstants.DATA_AUDITOR).orElseThrow();
        assertEquals(3, auditor.defaultPermissions().size(), "数据审计员应为 3 条权限");
        assertTrue(auditor.defaultPermissions().containsAll(List.of(
                PermissionConstants.KB_VIEW,
                PermissionConstants.ADMIN_USER_LIST,
                PermissionConstants.ADMIN_ROLE_LIST)), "数据审计员授权应符合 V6");
        assertEquals(DataScope.MASKED, auditor.defaultDataScope(), "审计员只能看脱敏数据");
    }

    @Test
    @DisplayName("数据范围枚举：非法值降级为 SELF，不放大权限")
    void dataScopeDegradesSafely() {
        assertEquals(DataScope.ALL, DataScope.of(" all "));
        assertEquals(DataScope.MASKED, DataScope.of("MASKED"));
        assertEquals(DataScope.SELF, DataScope.of(null));
        assertEquals(DataScope.SELF, DataScope.of("GLOBAL"), "未知取值必须降级为 SELF");
        assertTrue(DataScope.ALL.canReadAll());
        assertFalse(DataScope.MASKED.canReadAll());
    }
}
