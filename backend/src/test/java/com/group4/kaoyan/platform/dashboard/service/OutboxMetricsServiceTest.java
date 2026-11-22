package com.group4.kaoyan.platform.dashboard.service;

import com.group4.kaoyan.platform.messaging.entity.MessageOutbox;
import com.group4.kaoyan.platform.messaging.mapper.OutboxMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 步骤 E3 的单元测试：Outbox 统计口径 = PENDING+RETRY_WAIT / FAILED+卡住 PUBLISHING。
 */
@DisplayName("Outbox 指标（E3）")
class OutboxMetricsServiceTest {

    private final OutboxMapper outboxMapper = mock(OutboxMapper.class);
    private final OutboxMetricsService service = new OutboxMetricsService(outboxMapper);

    @Test
    @DisplayName("pending = PENDING + RETRY_WAIT；failed = FAILED + 卡住 PUBLISHING")
    void probeUsesAggregatedSemantics() {
        when(outboxMapper.countByStatus(MessageOutbox.STATUS_PENDING)).thenReturn(12L);
        when(outboxMapper.countByStatus(MessageOutbox.STATUS_RETRY_WAIT)).thenReturn(6L);
        when(outboxMapper.countByStatus(MessageOutbox.STATUS_FAILED)).thenReturn(3L);
        when(outboxMapper.countStuckPublishing(any(LocalDateTime.class))).thenReturn(2L);

        OutboxMetricsService.OutboxMetrics metrics = service.probe();

        assertTrue(metrics.up());
        assertEquals(18L, metrics.pending());
        assertEquals(5L, metrics.failed());
    }

    @Test
    @DisplayName("卡住阈值使用 5 分钟：staleBefore = now - 5m")
    void stuckThresholdIsFiveMinutes() {
        when(outboxMapper.countByStatus(anyString())).thenReturn(0L);
        when(outboxMapper.countStuckPublishing(any(LocalDateTime.class))).thenReturn(0L);

        service.probe();

        org.mockito.ArgumentCaptor<LocalDateTime> captor =
                org.mockito.ArgumentCaptor.forClass(LocalDateTime.class);
        org.mockito.Mockito.verify(outboxMapper).countStuckPublishing(captor.capture());

        long minutes = java.time.Duration.between(captor.getValue(), LocalDateTime.now()).toMinutes();
        assertTrue(minutes >= 5 && minutes <= 6, "卡住阈值应约为 5 分钟，实际：" + minutes);
    }

    @Test
    @DisplayName("计数异常：返回 down + 0，不抛异常")
    void probeFailureReturnsSafeZeros() {
        when(outboxMapper.countByStatus(anyString()))
                .thenThrow(new RuntimeException("查询失败"));

        OutboxMetricsService.OutboxMetrics metrics = service.probe();

        assertFalse(metrics.up());
        assertEquals(0L, metrics.pending());
        assertEquals(0L, metrics.failed());
    }
}
