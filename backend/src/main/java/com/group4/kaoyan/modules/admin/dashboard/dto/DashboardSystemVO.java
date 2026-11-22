package com.group4.kaoyan.modules.admin.dashboard.dto;

import lombok.Data;

/**
 * 大屏「系统与队列」区块。
 *
 * <p>前三个字段只允许 {@code UP} / {@code DOWN}，其余为非负整数。
 * 数据来源分三类：探活（mysql / redis / rabbitmq）、RabbitMQ 队列表（docParse*）、
 * Outbox 表（outbox*）。任一项查询失败都返回安全值，不带出内部连接串或密码。</p>
 */
@Data
public class DashboardSystemVO {

    /** MySQL 探活结果：UP / DOWN。 */
    private String mysql;

    /** Redis 探活结果：UP / DOWN。 */
    private String redis;

    /** RabbitMQ 探活结果：UP / DOWN（连接或队列查询任一失败即为 DOWN）。 */
    private String rabbitmq;

    /** 文档解析队列待消费消息数。 */
    private Long docParseReady;

    /** 文档解析队列消费者数。 */
    private Long docParseConsumers;

    /** 文档解析死信队列消息数。 */
    private Long docParseDlq;

    /** Outbox 待投递（PENDING + RETRY_WAIT）。 */
    private Long outboxPending;

    /** Outbox 失败（FAILED + PUBLISHING 超过 5 分钟）。 */
    private Long outboxFailed;

    /** 中间件全 DOWN 的安全空值：只用 0 与 DOWN，不编造 UP。 */
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
