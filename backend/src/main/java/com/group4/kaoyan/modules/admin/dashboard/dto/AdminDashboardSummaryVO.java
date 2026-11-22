package com.group4.kaoyan.modules.admin.dashboard.dto;

import lombok.Data;

/**
 * 管理员运营大屏聚合响应。
 *
 * <p>字段名与前端 {@code frontend/src/features/admin/dashboard/types.ts} 一一对应，
 * 改动前必须先与前端负责人对齐。</p>
 */
@Data
public class AdminDashboardSummaryVO {

    private DashboardCardsVO cards;
    private DashboardTrendVO trend;
    private DashboardSystemVO system;
    /** ISO 8601 带时区偏移，例如 {@code 2026-11-21T14:00:00+08:00}。 */
    private String generatedAt;
}
