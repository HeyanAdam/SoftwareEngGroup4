package com.group4.kaoyan.modules.admin.dashboard.dto;

import lombok.Data;

/**
 * 管理员运营大屏聚合响应（GET /api/v1/admin/dashboard/summary）。
 *
 * <p>字段名与前端 {@code types.ts} 的 {@code AdminDashboardSummary} 完全一致，
 * 冻结后不要单边改名：前端、缓存 JSON、契约测试三处都按这个结构对齐。</p>
 *
 * <p>不允许出现聊天正文、邮箱明文、对象存储 key、密码哈希等敏感字段——
 * 本 DTO 只装计数与状态字符串。</p>
 */
@Data
public class AdminDashboardSummaryVO {

    /** KPI 卡片。 */
    private DashboardCardsVO cards;

    /** 7/14/30 天趋势。 */
    private DashboardTrendVO trend;

    /** 系统与队列指标。 */
    private DashboardSystemVO system;

    /** 后端生成时间，ISO 8601，Asia/Shanghai（如 2026-11-21T14:00:00+08:00）。 */
    private String generatedAt;
}
