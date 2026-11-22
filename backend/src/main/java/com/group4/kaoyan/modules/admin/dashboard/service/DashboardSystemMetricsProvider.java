package com.group4.kaoyan.modules.admin.dashboard.service;

import com.group4.kaoyan.modules.admin.dashboard.dto.DashboardSystemVO;

/**
 * 系统与队列指标的提供者。
 *
 * <p>业务统计（甲）只负责卡片与趋势，探活与队列指标由可观测性负责人（丙）实现。
 * 在丙的实现落地前，由 {@link PlaceholderDashboardSystemMetricsProvider} 返回
 * {@code DOWN} 与 0，避免把未探活的服务伪装成 {@code UP}。</p>
 *
 * <p>丙实现自己的 Bean 时加 {@code @Primary} 即可覆盖默认占位实现。</p>
 */
public interface DashboardSystemMetricsProvider {

    /** 当前系统与队列指标。 */
    DashboardSystemVO current();
}
