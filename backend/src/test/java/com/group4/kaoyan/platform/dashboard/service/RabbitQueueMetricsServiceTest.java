package com.group4.kaoyan.platform.dashboard.service;

import com.group4.kaoyan.platform.messaging.RabbitTopologyConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 步骤 E2 的单元测试：主队列 / 死信队列指标口径与异常降级。
 */
@DisplayName("RabbitMQ 队列指标（E2）")
class RabbitQueueMetricsServiceTest {

    @SuppressWarnings("unchecked")
    private final ObjectProvider<RabbitAdmin> adminProvider = mock(ObjectProvider.class);
    private final RabbitAdmin rabbitAdmin = mock(RabbitAdmin.class);
    private final RabbitQueueMetricsService service =
            new RabbitQueueMetricsService(adminProvider);

    private QueueInformation queueInfo(long messages, long consumers) {
        return new QueueInformation("queue", (int) messages, (int) consumers);
    }

    @Test
    @DisplayName("正常：主队列 ready/consumers 与死信消息数分别取数")
    void probeReadsMainAndDlqCounts() {
        when(adminProvider.getIfAvailable()).thenReturn(rabbitAdmin);
        when(rabbitAdmin.getQueueInfo(RabbitTopologyConfig.DOC_PARSE_QUEUE))
                .thenReturn(queueInfo(35L, 2L));
        when(rabbitAdmin.getQueueInfo(RabbitTopologyConfig.DLQ_QUEUE))
                .thenReturn(queueInfo(7L, 0L));

        RabbitQueueMetricsService.QueueMetrics metrics = service.probe();

        assertTrue(metrics.up());
        assertEquals(35L, metrics.docParseReady());
        assertEquals(2L, metrics.docParseConsumers());
        assertEquals(7L, metrics.docParseDlq());
    }

    @Test
    @DisplayName("队列查询异常：rabbitmq 判定 DOWN，数字全部安全归 0")
    void probeFailureReturnsSafeZeros() {
        when(adminProvider.getIfAvailable()).thenReturn(rabbitAdmin);
        when(rabbitAdmin.getQueueInfo(RabbitTopologyConfig.DOC_PARSE_QUEUE))
                .thenThrow(new IllegalStateException("broker 不可用"));

        RabbitQueueMetricsService.QueueMetrics metrics = service.probe();

        assertFalse(metrics.up());
        assertEquals(0L, metrics.docParseReady());
        assertEquals(0L, metrics.docParseConsumers());
        assertEquals(0L, metrics.docParseDlq());
    }

    @Test
    @DisplayName("队列不存在（返回 null）：同样降级为 DOWN + 0")
    void missingQueueDegrades() {
        when(adminProvider.getIfAvailable()).thenReturn(rabbitAdmin);
        when(rabbitAdmin.getQueueInfo(RabbitTopologyConfig.DOC_PARSE_QUEUE)).thenReturn(null);

        assertFalse(service.probe().up());
    }

    @Test
    @DisplayName("RabbitAdmin 未装配：不抛异常，直接安全降级")
    void missingRabbitAdminDegrades() {
        when(adminProvider.getIfAvailable()).thenReturn(null);

        RabbitQueueMetricsService.QueueMetrics metrics = service.probe();

        assertFalse(metrics.up());
        assertEquals(0L, metrics.docParseReady());
    }
}
