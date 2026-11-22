package com.group4.kaoyan.platform.dashboard.service;

import com.group4.kaoyan.common.BizException;
import com.group4.kaoyan.common.ErrorCode;
import com.group4.kaoyan.modules.admin.dashboard.dto.AdminDashboardSummaryVO;
import com.group4.kaoyan.modules.admin.dashboard.dto.DashboardCardsVO;
import com.group4.kaoyan.modules.admin.dashboard.dto.DashboardTrendVO;
import com.group4.kaoyan.modules.admin.dashboard.service.DashboardSummaryProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 步骤 E5 的单元测试：检查顺序（缓存 → 业务 → 指标 → 组装 → 回填）与故障降级。
 */
@DisplayName("大屏聚合服务（E5）")
class DashboardSummaryServiceTest {

    @SuppressWarnings("unchecked")
    private final ObjectProvider<DashboardSummaryProvider> providers = mock(ObjectProvider.class);
    private final DashboardCacheService cache = mock(DashboardCacheService.class);
    private final SystemHealthMetricsService health = mock(SystemHealthMetricsService.class);
    private final RabbitQueueMetricsService queues = mock(RabbitQueueMetricsService.class);
    private final OutboxMetricsService outbox = mock(OutboxMetricsService.class);

    private final DashboardSummaryService service =
            new DashboardSummaryService(cache, providers, health, queues, outbox);

    @BeforeEach
    void stubMetrics() {
        when(health.probe()).thenReturn(new SystemHealthMetricsService.SystemHealth("UP", "UP", "UP"));
        when(queues.probe()).thenReturn(new RabbitQueueMetricsService.QueueMetrics(true, 35L, 2L, 7L));
        when(outbox.probe()).thenReturn(new OutboxMetricsService.OutboxMetrics(true, 12L, 5L));
    }

    private AdminDashboardSummaryVO business(int days, long totalUsers) {
        AdminDashboardSummaryVO vo = new AdminDashboardSummaryVO();
        DashboardCardsVO cards = DashboardCardsVO.empty();
        cards.setTotalUsers(totalUsers);
        DashboardTrendVO trend = new DashboardTrendVO();
        List<String> dates = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            dates.add("11-" + (5 + i));
        }
        trend.setDates(dates);
        trend.setLogins(new ArrayList<>(java.util.Collections.nCopies(days, 1L)));
        trend.setUploads(new ArrayList<>(java.util.Collections.nCopies(days, 2L)));
        trend.setChatMessages(new ArrayList<>(java.util.Collections.nCopies(days, 3L)));
        vo.setCards(cards);
        vo.setTrend(trend);
        return vo;
    }

    @Test
    @DisplayName("缓存命中：不调用业务统计、不重复写缓存")
    void cacheHitSkipsStatistics() {
        AdminDashboardSummaryVO cached = business(7, 120L);
        cached.setSystem(com.group4.kaoyan.modules.admin.dashboard.dto.DashboardSystemVO.down());
        cached.setGeneratedAt("2026-11-22T10:00:00+08:00");
        when(cache.get(7)).thenReturn(Optional.of(cached));

        AdminDashboardSummaryVO result = service.summary(7);

        assertEquals(120L, result.getCards().getTotalUsers());
        verify(providers, never()).getIfAvailable();
        verify(cache, never()).put(anyInt(), any());
    }

    @Test
    @DisplayName("缓存未命中：调用 Provider、拼装指标并回填缓存")
    void cacheMissRebuildsAndStores() {
        when(cache.get(14)).thenReturn(Optional.empty());
        DashboardSummaryProvider provider = mock(DashboardSummaryProvider.class);
        when(providers.getIfAvailable()).thenReturn(provider);
        when(provider.buildBusiness(14)).thenReturn(business(14, 500L));

        AdminDashboardSummaryVO result = service.summary(14);

        assertEquals(500L, result.getCards().getTotalUsers());
        assertEquals(14, result.getTrend().getDates().size());
        assertEquals("UP", result.getSystem().getMysql());
        assertEquals(35L, result.getSystem().getDocParseReady());
        assertEquals(12L, result.getSystem().getOutboxPending());
        verify(cache, times(1)).put(14, result);
    }

    @Test
    @DisplayName("生成时间使用 Asia/Shanghai（+08:00）")
    void generatedAtUsesShanghaiZone() {
        when(cache.get(7)).thenReturn(Optional.empty());
        when(providers.getIfAvailable()).thenReturn(null);

        AdminDashboardSummaryVO result = service.summary(7);

        OffsetDateTime parsed = OffsetDateTime.parse(result.getGeneratedAt(),
                DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        assertEquals(ZoneOffset.ofHours(8), parsed.getOffset());
    }

    @Test
    @DisplayName("Provider 未接入：返回全 0 的合法空结果，日期仍完整")
    void missingProviderDegradesToZeros() {
        when(cache.get(30)).thenReturn(Optional.empty());
        when(providers.getIfAvailable()).thenReturn(null);

        AdminDashboardSummaryVO result = service.summary(30);

        assertEquals(0L, result.getCards().getTotalUsers());
        assertEquals(30, result.getTrend().getDates().size());
        assertEquals(30, result.getTrend().getLogins().size());
        assertEquals(0L, result.getTrend().getLogins().get(0));
        assertNotNull(result.getGeneratedAt());
    }

    @Test
    @DisplayName("RabbitMQ 队列不可读：rabbitmq 置 DOWN，其余指标照常返回")
    void queueDownMarksRabbitmqDown() {
        when(cache.get(7)).thenReturn(Optional.empty());
        when(providers.getIfAvailable()).thenReturn(null);
        when(queues.probe()).thenReturn(RabbitQueueMetricsService.QueueMetrics.down());

        AdminDashboardSummaryVO result = service.summary(7);

        assertEquals("DOWN", result.getSystem().getRabbitmq());
        assertEquals("UP", result.getSystem().getMysql());
        assertEquals(0L, result.getSystem().getDocParseReady());
    }

    @Test
    @DisplayName("days 非法：抛 400 + 10000，不触发任何统计与缓存")
    void invalidDaysRejected() {
        BizException e = assertThrows(BizException.class, () -> service.summary(8));

        assertEquals(ErrorCode.PARAM_INVALID, e.getCode());
        assertTrue(e.getMessage().contains("7、14、30"));
        verify(cache, never()).get(anyInt());
        verify(providers, never()).getIfAvailable();
    }
}
