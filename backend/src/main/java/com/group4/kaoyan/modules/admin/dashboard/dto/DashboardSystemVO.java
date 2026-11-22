package com.group4.kaoyan.modules.admin.dashboard.dto;

import lombok.Data;

/**
 * 运营大屏系统与队列指标。
 *
 * <p>前三个探活字段只允许 {@code UP} / {@code DOWN}，其余为非负整数。
 * 当前由 {@code PlaceholderDashboardSystemMetricsProvider} 返回 {@code DOWN} 与 0，
 * 等待可观测性负责人接入真实探活后替换实现，禁止在这里硬编码 {@code UP}。</p>
 */
@Data
public class DashboardSystemVO {

    private String mysql;
    private String redis;
    private String rabbitmq;
    private Long docParseReady;
    private Long docParseConsumers;
    private Long docParseDlq;
    private Long outboxPending;
    private Long outboxFailed;

    /** 全 DOWN、计数全 0 的占位结构，仅用于兜底，不用于真实探活结果。 */
    public static DashboardSystemVO down() {
        DashboardSystemVO system = new DashboardSystemVO();
        system.setMysql("DOWN");
        system.setRedis("DOWN");
        system.setRabbitmq("DOWN");
        system.setDocParseReady(0L);
        system.setDocParseConsumers(0L);
        system.setDocParseDlq(0L);
        system.setOutboxPending(0L);
        system.setOutboxFailed(0L);
        return system;
    }
}
