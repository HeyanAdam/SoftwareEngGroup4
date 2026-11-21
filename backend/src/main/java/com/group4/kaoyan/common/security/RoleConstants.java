package com.group4.kaoyan.common.security;

import java.util.List;
import java.util.Optional;

import static com.group4.kaoyan.common.security.PermissionConstants.*;

/**
 * 内置角色定义（P2 任务 1：角色权限矩阵的代码版本）。
 *
 * <p>角色码与 GitHub main 分支 {@code V6__seed_rbac_dictionary.sql} 完全一致，
 * 不要再用自拟的 {@code ADMIN} / {@code KB_ADMIN} / {@code DATA_VIEWER}：</p>
 *
 * <table border="1">
 *   <tr><th>角色码</th><th>中文名</th><th>数据范围</th><th>V6 授权数</th></tr>
 *   <tr><td>SYSTEM_ADMIN</td><td>系统管理员</td><td>ALL</td><td>11（全权，扩到 20）</td></tr>
 *   <tr><td>CONTENT_OPERATOR</td><td>内容运营</td><td>ALL</td><td>6</td></tr>
 *   <tr><td>DATA_AUDITOR</td><td>数据审计员</td><td>MASKED</td><td>3</td></tr>
 *   <tr><td>STUDENT</td><td>学生</td><td>SELF</td><td>1（chat:send）</td></tr>
 * </table>
 *
 * <p>默认授权的口径以 V6 为准；新增的 9 条业务权限码（学习计划、题库、文件）
 * 属于待评审的"建议授权"，见 {@code docs/00_项目管理/角色权限矩阵.md} ◇ 列，
 * 评审通过后再补进本类并走 V7 迁移，代码和数据库不能各写各的。</p>
 */
public final class RoleConstants {

    private RoleConstants() {
    }

    /** 系统管理员：内置全权角色。 */
    public static final String SYSTEM_ADMIN = "SYSTEM_ADMIN";

    /** 学生：注册默认角色。 */
    public static final String STUDENT = "STUDENT";

    /** 内容运营：维护知识库、文档与向量索引。 */
    public static final String CONTENT_OPERATOR = "CONTENT_OPERATOR";

    /** 数据审计员：只读查看用户、角色和知识库概览。 */
    public static final String DATA_AUDITOR = "DATA_AUDITOR";

    /** 历史角色码：V6 已迁移为 SYSTEM_ADMIN 并删除，仅用于旧库兼容迁移。 */
    public static final String LEGACY_ADMIN = "ADMIN";
    /** 历史角色码：对应 CONTENT_OPERATOR。 */
    public static final String LEGACY_KB_ADMIN = "KB_ADMIN";
    /** 历史角色码：对应 DATA_AUDITOR。 */
    public static final String LEGACY_DATA_VIEWER = "DATA_VIEWER";

    /**
     * 角色元数据。
     *
     * @param code                 角色码（存 sys_role.role_code）
     * @param name                 中文名称
     * @param description          说明
     * @param grantAllPermissions  true = 全权角色，启动时补齐全部权限码（只有 SYSTEM_ADMIN）
     * @param defaultPermissions   首次创建角色时写入的默认权限
     * @param defaultDataScope     默认数据范围，供 DataScopeService 按角色码映射
     */
    public record RoleDef(String code,
                          String name,
                          String description,
                          boolean grantAllPermissions,
                          List<String> defaultPermissions,
                          DataScope defaultDataScope) {
    }

    /**
     * 内置角色矩阵（与 main 分支 V6 种子一致）。
     *
     * <p>STUDENT 给 {@code chat:send} 与 4 条 {@code file:*}：接口文档第 10 章把上传/下载/删除文件
     * 定义为学生可用功能（10.1 权限列为 file:upload / file:view / file:download / file:delete），
     * 归属范围仍由 DataScope SELF 与 FileService 的 userId 校验兜住，学生只能动自己的文件。
     * 知识库原文与向量不给学生（文档 11.5 规定普通学生不得直接浏览知识库原文、切片与向量）。</p>
     */
    public static final List<RoleDef> BUILTIN_ROLES = List.of(
            new RoleDef(SYSTEM_ADMIN, "系统管理员",
                    "拥有全部系统权限",
                    true, List.of(), DataScope.ALL),

            new RoleDef(STUDENT, "学生",
                    "默认注册角色，可问答、可管理本人文件",
                    false,
                    List.of(CHAT_SEND, FILE_VIEW, FILE_UPLOAD, FILE_DOWNLOAD, FILE_DELETE,
                            STUDY_PLAN_VIEW, STUDY_PLAN_CREATE, STUDY_PLAN_UPDATE, STUDY_TASK_UPDATE),
                    DataScope.SELF),

            new RoleDef(CONTENT_OPERATOR, "内容运营",
                    "维护知识库、文档和向量索引",
                    false,
                    List.of(KB_VIEW, KB_CREATE, KB_DELETE, DOC_UPLOAD, DOC_DELETE, ADMIN_VECTOR_MANAGE),
                    DataScope.ALL),

            new RoleDef(DATA_AUDITOR, "数据审计员",
                    "只读查看用户、角色和知识库概览",
                    false,
                    List.of(KB_VIEW, ADMIN_USER_LIST, ADMIN_ROLE_LIST),
                    DataScope.MASKED)
    );

    /** 按角色码查内置角色定义。 */
    public static Optional<RoleDef> find(String roleCode) {
        return BUILTIN_ROLES.stream()
                .filter(def -> def.code().equals(roleCode))
                .findFirst();
    }

    /** 是否为内置角色（后台不允许删除内置角色）。 */
    public static boolean isBuiltin(String roleCode) {
        return find(roleCode).isPresent();
    }

    /** 角色默认数据范围；未知角色降级为 SELF。 */
    public static DataScope defaultDataScopeOf(String roleCode) {
        return find(roleCode).map(RoleDef::defaultDataScope).orElse(DataScope.SELF);
    }
}
