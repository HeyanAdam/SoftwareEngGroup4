package com.group4.kaoyan.modules.admin.dashboard.service;

import com.group4.kaoyan.modules.admin.dashboard.dto.AdminDashboardSummaryVO;

/**
 * 运营大屏聚合数据提供者。
 *
 * <p>抽出接口的目的：后续接入 Redis 缓存时可包一层代理，测试时也可直接打桩，
 * 不必依赖真实数据库。</p>
 */
public interface DashboardSummaryProvider {

    /**
     * 构建大屏聚合数据。
     *
     * @param days 趋势天数，只允许 7 / 14 / 30
     * @return 卡片、趋势、系统指标与生成时间
     */
    AdminDashboardSummaryVO build(int days);
}
