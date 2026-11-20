package com.group4.kaoyan.common.security;

import java.util.List;

/**
 * 全系统权限码唯一定义处（P2 任务 1：整理权限码）。
 *
 * <p>三条约定，答辩时可以直接讲：</p>
 * <ol>
 *   <li><b>命名</b>：统一为 {@code module:action}，资源段与接口路径命名保持一致
 *       （全小写、中划线），例如 {@code /api/v1/study-plans} 对应 {@code study-plan:view}。
 *       好处是权限码和接口能一一对上，前端菜单、后端注解、数据库种子用的是同一套字符串。</li>
 *   <li><b>单一来源</b>：只允许在本类新增/修改权限码。Controller 用
 *       {@code @SaCheckPermission(PermissionConstants.KB_VIEW)} 引用，
 *       避免把字符串散落在各处、拼错一个字母就变成"永远 403"。</li>
 *   <li><b>字典即种子</b>：{@link #CATALOG} 同时是 {@code sys_permission} 表的种子，
 *       启动时由 {@code RbacInitializer} 遍历补齐（只增不删，幂等）。</li>
 * </ol>
 *
 * <p>权限码清单的文档版本见 {@code docs/00_项目管理/角色权限矩阵.md}
 * 与 {@code docs/00_项目管理/权限码清单.csv}（交给数据库同学做种子迁移）。</p>
 */
public final class PermissionConstants {

    private PermissionConstants() {
        // 常量类，禁止实例化
    }

    // ==================== 学习计划与任务（20xxx） ====================

    /** 查看学习计划：GET /api/v1/study-plans、/{plan_id}、/today */
    public static final String STUDY_PLAN_VIEW = "study-plan:view";

    /** 生成学习计划：POST /api/v1/study-plans */
    public static final String STUDY_PLAN_CREATE = "study-plan:create";

    /** 调整学习计划：POST /api/v1/study-plans/{plan_id}/adjust */
    public static final String STUDY_PLAN_UPDATE = "study-plan:update";

    /** 更新任务状态：PATCH /api/v1/study-tasks/{task_id} */
    public static final String STUDY_TASK_UPDATE = "study-task:update";

    // ==================== 题库（21xxx） ====================

    /** 浏览题库：GET /api/v1/questions、/{question_id}、/random */
    public static final String QUESTION_VIEW = "question:view";

    // ==================== 文件与存储（30xxx） ====================

    /** 文件列表：GET /api/v1/files */
    public static final String FILE_VIEW = "file:view";

    /** 上传文件：POST /api/v1/files/batch、/upload-sessions 等 */
    public static final String FILE_UPLOAD = "file:upload";

    /** 下载文件：GET /api/v1/files/{file_id}/content */
    public static final String FILE_DOWNLOAD = "file:download";

    /** 删除文件：DELETE /api/v1/files/{file_id} */
    public static final String FILE_DELETE = "file:delete";

    // ==================== 知识库与 RAG（31xxx） ====================

    /** 查看知识库：GET /api/v1/knowledge-bases、/{kb_id}、/{kb_id}/documents */
    public static final String KB_VIEW = "kb:view";

    /** 新建知识库：POST /api/v1/knowledge-bases */
    public static final String KB_CREATE = "kb:create";

    /** 删除知识库：DELETE /api/v1/knowledge-bases/{kb_id} */
    public static final String KB_DELETE = "kb:delete";

    /** 上传知识文档：POST /api/v1/knowledge-bases/{kb_id}/documents、reparse */
    public static final String DOC_UPLOAD = "doc:upload";

    /** 删除知识文档：DELETE /api/v1/knowledge-documents/{document_id} */
    public static final String DOC_DELETE = "doc:delete";

    // ==================== AI 对话（32xxx） ====================

    /** AI 对话答疑：POST /api/v1/chat/conversations/...（含 SSE 流式） */
    public static final String CHAT_SEND = "chat:send";

    // ==================== 后台管理（50xxx） ====================

    /** 后台用户列表：GET /api/v1/admin/users、/{user_id}、/{user_id}/roles */
    public static final String ADMIN_USER_LIST = "admin:user:list";

    /** 后台用户管理：PUT /api/v1/admin/users/{user_id}/status、/roles */
    public static final String ADMIN_USER_UPDATE = "admin:user:update";

    /** 后台角色与权限查看：GET /api/v1/admin/roles、/permissions */
    public static final String ADMIN_ROLE_LIST = "admin:role:list";

    /** 后台角色与授权维护：POST /api/v1/admin/roles、PUT /api/v1/admin/roles/{role_id}/permissions */
    public static final String ADMIN_ROLE_UPDATE = "admin:role:update";

    /** 向量库重建：POST /api/v1/admin/knowledge-bases/{kb_id}/vectors/rebuild */
    public static final String ADMIN_VECTOR_MANAGE = "admin:vector:manage";

    /**
     * 权限码元数据：一条记录 = sys_permission 表的一行。
     *
     * @param code   权限码（唯一）
     * @param name   中文名称（后台授权界面展示）
     * @param module 所属业务模块（分组展示用）
     * @param sort   排序号，同模块内递增
     */
    public record PermissionDef(String code, String name, String module, int sort) {
    }

    /** 权限字典：全量 20 个权限码，顺序即后台展示顺序。 */
    public static final List<PermissionDef> CATALOG = List.of(
            new PermissionDef(STUDY_PLAN_VIEW, "查看学习计划", "督学陪伴", 1),
            new PermissionDef(STUDY_PLAN_CREATE, "生成学习计划", "督学陪伴", 2),
            new PermissionDef(STUDY_PLAN_UPDATE, "调整学习计划", "督学陪伴", 3),
            new PermissionDef(STUDY_TASK_UPDATE, "更新任务状态", "督学陪伴", 4),

            new PermissionDef(QUESTION_VIEW, "浏览题库", "刷题训练", 10),

            new PermissionDef(FILE_VIEW, "文件列表", "文件存储", 20),
            new PermissionDef(FILE_UPLOAD, "上传文件", "文件存储", 21),
            new PermissionDef(FILE_DOWNLOAD, "下载文件", "文件存储", 22),
            new PermissionDef(FILE_DELETE, "删除文件", "文件存储", 23),

            new PermissionDef(KB_VIEW, "查看知识库", "知识库", 30),
            new PermissionDef(KB_CREATE, "新建知识库", "知识库", 31),
            new PermissionDef(KB_DELETE, "删除知识库", "知识库", 32),
            new PermissionDef(DOC_UPLOAD, "上传知识文档", "知识库", 33),
            new PermissionDef(DOC_DELETE, "删除知识文档", "知识库", 34),

            new PermissionDef(CHAT_SEND, "AI 对话答疑", "AI 对话", 40),

            new PermissionDef(ADMIN_USER_LIST, "后台用户列表", "后台管理", 50),
            new PermissionDef(ADMIN_USER_UPDATE, "后台用户管理", "后台管理", 51),
            new PermissionDef(ADMIN_ROLE_LIST, "后台角色与权限查看", "后台管理", 52),
            new PermissionDef(ADMIN_ROLE_UPDATE, "后台角色与授权维护", "后台管理", 53),
            new PermissionDef(ADMIN_VECTOR_MANAGE, "向量库重建", "后台管理", 54)
    );

    /** 全部权限码，按字典顺序。 */
    public static final List<String> ALL_CODES = CATALOG.stream()
            .map(PermissionDef::code)
            .toList();

    /**
     * 权限码是否已在字典中登记。
     * 用于启动种子、配置校验，防止把拼错的权限码写进角色默认授权。
     */
    public static boolean isDefined(String code) {
        return code != null && ALL_CODES.contains(code);
    }

    /** 取权限码所属模块，未登记返回"未分类"（仅用于日志/文档，不参与鉴权）。 */
    public static String moduleOf(String code) {
        return CATALOG.stream()
                .filter(def -> def.code().equals(code))
                .map(PermissionDef::module)
                .findFirst()
                .orElse("未分类");
    }
}
