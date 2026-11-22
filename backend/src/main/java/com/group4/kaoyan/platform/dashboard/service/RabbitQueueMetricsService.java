package com.group4.kaoyan.platform.dashboard.service;

import com.group4.kaoyan.platform.messaging.RabbitTopologyConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * 文档解析队列指标（步骤 E2）。
 *
 * <p>只用 Spring {@code RabbitAdmin#getQueueInfo} 在服务端读取，前端永远不直连 RabbitMQ，
 * 管理端口与账号密码不会出现在浏览器侧。</p>
 *
 * <p>口径：</p>
 * <ul>
 *   <li>{@code docParseReady} = 主队列 {@code queueCount}（就绪待消费消息数）；</li>
 *   <li>{@code docParseConsumers} = 主队列 {@code consumerCount}（在线消费者数）；</li>
 *   <li>{@code docParseDlq} = 死信队列 {@code queueCount}（已进死信的消息数）。</li>
 * </ul>
 *
 * <p>队列不存在、broker 不可用或任何 AMQP 异常：返回 {@code up=false} 与全 0 安全值，
 * 由上层把 {@code rabbitmq} 置为 DOWN；不重试、不缓存异常、不记录管理密码。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RabbitQueueMetricsService {

    /** 主队列名。 */
    public static final String MAIN_QUEUE = RabbitTopologyConfig.DOC_PARSE_QUEUE;

    /** 死信队列名。 */
    public static final String DLQ_QUEUE = RabbitTopologyConfig.DLQ_QUEUE;

    /** RabbitAdmin 由 Spring Boot 的 RabbitAutoConfiguration 提供；不可用时降级。 */
    private final ObjectProvider<RabbitAdmin> rabbitAdminProvider;

    /**
     * 队列指标快照。
     *
     * @param up                队列查询是否成功
     * @param docParseReady     主队列就绪消息数
     * @param docParseConsumers 主队列消费者数
     * @param docParseDlq       死信队列消息数
     */
    public record QueueMetrics(boolean up,
                               long docParseReady,
                               long docParseConsumers,
                               long docParseDlq) {

        /** 查询失败的安全值：up=false、全部 0。 */
        public static QueueMetrics down() {
            return new QueueMetrics(false, 0L, 0L, 0L);
        }
    }

    /**
     * 读取主队列与死信队列指标。
     */
    public QueueMetrics probe() {
        RabbitAdmin rabbitAdmin = rabbitAdminProvider.getIfAvailable();
        if (rabbitAdmin == null) {
            log.warn("RabbitAdmin 不可用，队列指标跳过（rabbitmq 判定 DOWN）");
            return QueueMetrics.down();
        }
        try {
            QueueInformation main = rabbitAdmin.getQueueInfo(MAIN_QUEUE);
            QueueInformation dlq = rabbitAdmin.getQueueInfo(DLQ_QUEUE);
            if (main == null || dlq == null) {
                log.warn("队列不存在或不可读，队列指标跳过：main={} dlq={}",
                        main != null, dlq != null);
                return QueueMetrics.down();
            }
            return new QueueMetrics(true,
                    nonNegative(main.getMessageCount()),
                    nonNegative(main.getConsumerCount()),
                    nonNegative(dlq.getMessageCount()));
        } catch (Exception e) {
            // 只记异常摘要，AMQP 异常可能带 broker 地址，不做全量堆栈输出
            log.warn("RabbitMQ 队列指标读取失败，判定 DOWN：{}", e.getMessage());
            return QueueMetrics.down();
        }
    }

    private static long nonNegative(long value) {
        return Math.max(value, 0L);
    }
}
