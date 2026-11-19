package com.group4.kaoyan.modules.audit.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作审计（sys_audit_log）。只增不改，Mapper 只有 insert 与按模块分页查询。
 */
@Data
public class AuditLogRecord {

    private Long id;
    private Long userId;
    private String module;
    private String action;
    private String resourceType;
    private String resourceId;
    private String requestId;
    private String ipAddress;
    /** JSON 列：变更前快照（本版暂不采集，保留字段） */
    private Object beforeJson;
    /** JSON 列：返回体摘要，截断 8KB */
    private Object afterJson;
    private LocalDateTime createTime;
}
