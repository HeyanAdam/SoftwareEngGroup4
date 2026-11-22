package com.group4.kaoyan.modules.admin.dashboard.service;

import com.group4.kaoyan.modules.admin.dashboard.dto.DashboardSystemVO;
import org.springframework.stereotype.Component;

/**
 * 系统指标的占位实现。
 *
 * <p>丙（可观测性负责人）未交付前，三项统一返回 {@link #DOWN}，队列与 Outbox 计数返回 0。
 * 这里刻意不返回 {@code UP}：没有做过探活就报健康属于伪造数据。</p>
 *
 * <p>丙接入真实探活后，新增一个带 {@code @Primary} 的 {@link DashboardSystemMetricsProvider}
 * 实现即可，本类与 DTO 都不需要改动。</p>
 */
@Component
public class PlaceholderDashboardSystemMetricsProvider implements DashboardSystemMetricsProvider {

    private static final String DOWN = "DOWN";

    @Override
    public DashboardSystemVO current() {
        DashboardSystemVO system = new DashboardSystemVO();
        system.setMysql(DOWN);
        system.setRedis(DOWN);
        system.setRabbitmq(DOWN);
        system.setDocParseReady(0L);
        system.setDocParseConsumers(0L);
        system.setDocParseDlq(0L);
        system.setOutboxPending(0L);
        system.setOutboxFailed(0L);
        return system;
    }
}
