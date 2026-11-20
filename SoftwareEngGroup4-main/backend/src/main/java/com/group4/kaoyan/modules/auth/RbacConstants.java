package com.group4.kaoyan.modules.auth;

import java.util.List;

/**
 * RBAC 角色码、权限码与角色权限矩阵的唯一代码来源。
 *
 * <p>口径对齐 {@code docs/权限表.md}（V2.1）：4 个官方角色、20 条权限码，
 * 其中 V6 已落库的 11 条一个不改，新增的 9 条业务权限码按 {@code module:action} 规则追加。
 * 本类只描述"应该是什么"，落库由 {@code RbacInitializer} 启动时幂等补齐。</p>
 */
public final class RbacConstants {

    /* ========== 角色码（与 Flyway V6 一致） ========== */

    public static final String ROLE_STUDENT = "STUDENT";
    public static final String ROLE_CONTENT_OPERATOR = "CONTENT_OPERATOR";
    public static final String ROLE_DATA_AUDITOR = "DATA_AUDITOR";
    public static final String ROLE_SYSTEM_ADMIN = "SYSTEM_ADMIN";

    /** 历史角色码，仅用于旧库兼容迁移：ADMIN → SYSTEM_ADMIN。 */
    public static final String LEGACY_ADMIN = "ADMIN";
    /** 历史角色码，仅用于旧库兼容迁移：KB_ADMIN → CONTENT_OPERATOR。 */
    public static final String LEGACY_KB_ADMIN = "KB_ADMIN";
    /** 历史角色码，仅用于旧库兼容迁移：DATA_VIEWER → DATA_AUDITOR。 */
    public static final String LEGACY_DATA_VIEWER = "DATA_VIEWER";

    /* ========== 权限码（11 条 V6 基线 + 9 条扩展） ========== */

    // 督学陪伴
    public static final String STUDY_PLAN_VIEW = "study-plan:view";
    public static final String STUDY_PLAN_CREATE = "study-plan:create";
    public static final String STUDY_PLAN_UPDATE = "study-plan:update";
    public static final String STUDY_TASK_UPDATE = "study-task:update";
    // 刷题训练
    public static final String QUESTION_VIEW = "question:view";
    // 文件存储
    public static final String FILE_VIEW = "file:view";
    public static final String FILE_UPLOAD = "file:upload";
    public static final String FILE_DOWNLOAD = "file:download";
    public static final String FILE_DELETE = "file:delete";
    // 知识库
    public static final String KB_VIEW = "kb:view";
    public static final String KB_CREATE = "kb:create";
    public static final String KB_DELETE = "kb:delete";
    public static final String DOC_UPLOAD = "doc:upload";
    public static final String DOC_DELETE = "doc:delete";
    // AI 对话
    public static final String CHAT_SEND = "chat:send";
    // 后台管理
    public static final String ADMIN_USER_LIST = "admin:user:list";
    public static final String ADMIN_USER_UPDATE = "admin:user:update";
    public static final String ADMIN_ROLE_LIST = "admin:role:list";
    public static final String ADMIN_ROLE_UPDATE = "admin:role:update";
    public static final String ADMIN_VECTOR_MANAGE = "admin:vector:manage";

    /**
     * 权限元数据。
     *
     * @param code 权限码，写入 {@code sys_permission.perm_code}
     * @param name 中文名
     * @param sort 排序号，与 V6 的 10~110 顺延
     */
    public record PermissionDef(String code, String name, int sort) {
    }

    /** 20 条权限字典，顺序即 {@code sort} 顺序。 */
    public static final List<PermissionDef> PERMISSION_CATALOG = List.of(
            new PermissionDef(KB_VIEW, "查看知识库", 10),
            new PermissionDef(KB_CREATE, "创建知识库", 20),
            new PermissionDef(KB_DELETE, "删除知识库", 30),
            new PermissionDef(DOC_UPLOAD, "上传文档", 40),
            new PermissionDef(DOC_DELETE, "删除文档", 50),
            new PermissionDef(CHAT_SEND, "智能问答", 60),
            new PermissionDef(ADMIN_USER_LIST, "用户列表", 70),
            new PermissionDef(ADMIN_USER_UPDATE, "用户管理", 80),
            new PermissionDef(ADMIN_ROLE_LIST, "角色列表", 90),
            new PermissionDef(ADMIN_ROLE_UPDATE, "角色权限管理", 100),
            new PermissionDef(ADMIN_VECTOR_MANAGE, "向量库管理", 110),
            new PermissionDef(STUDY_PLAN_VIEW, "查看学习计划", 120),
            new PermissionDef(STUDY_PLAN_CREATE, "生成学习计划", 130),
            new PermissionDef(STUDY_PLAN_UPDATE, "调整学习计划", 140),
            new PermissionDef(STUDY_TASK_UPDATE, "更新任务状态", 150),
            new PermissionDef(QUESTION_VIEW, "浏览题库", 160),
            new PermissionDef(FILE_VIEW, "文件列表", 170),
            new PermissionDef(FILE_UPLOAD, "上传文件", 180),
            new PermissionDef(FILE_DOWNLOAD, "下载文件", 190),
            new PermissionDef(FILE_DELETE, "删除文件", 200)
    );

    /**
     * 角色元数据。
     *
     * @param code               角色码
     * @param name               中文名
     * @param description        说明
     * @param grantAllPermissions true = 内置全权角色，每次启动补齐全部权限码（仅 SYSTEM_ADMIN）
     * @param defaultPermissions 首次创建角色时写入的默认授权
     * @param dataScope          数据范围：ALL / SELF / MASKED
     */
    public record RoleDef(String code,
                          String name,
                          String description,
                          boolean grantAllPermissions,
                          List<String> defaultPermissions,
                          String dataScope) {
    }

    /**
     * 4 个内置角色及默认授权，与 {@code docs/权限表.md} 第 4 节矩阵一致。
     *
     * <p>新增的 9 条业务权限码（学习计划 / 题库 / 文件）只给 SYSTEM_ADMIN，
     * 其余角色的授权在权限表中仍是"建议授权（◇，待评审）"，评审通过后再补本类。</p>
     */
    public static final List<RoleDef> BUILTIN_ROLES = List.of(
            new RoleDef(ROLE_SYSTEM_ADMIN, "系统管理员", "拥有全部系统权限",
                    true, List.of(), "ALL"),
            new RoleDef(ROLE_STUDENT, "学生", "默认注册角色，只能问答",
                    false, List.of(CHAT_SEND), "SELF"),
            new RoleDef(ROLE_CONTENT_OPERATOR, "内容运营", "维护知识库、文档和向量索引",
                    false,
                    List.of(KB_VIEW, KB_CREATE, KB_DELETE, DOC_UPLOAD, DOC_DELETE, ADMIN_VECTOR_MANAGE),
                    "ALL"),
            new RoleDef(ROLE_DATA_AUDITOR, "数据审计员", "只读查看用户、角色和知识库概览",
                    false,
                    List.of(KB_VIEW, ADMIN_USER_LIST, ADMIN_ROLE_LIST),
                    "MASKED")
    );

    private RbacConstants() {
    }

    /** 全部权限码，供全权角色补齐使用。 */
    public static List<String> allPermissionCodes() {
        return PERMISSION_CATALOG.stream().map(PermissionDef::code).toList();
    }
}
