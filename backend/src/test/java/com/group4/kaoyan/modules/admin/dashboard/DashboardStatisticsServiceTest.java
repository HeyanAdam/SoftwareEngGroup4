package com.group4.kaoyan.modules.admin.dashboard;

import com.group4.kaoyan.common.BizException;
import com.group4.kaoyan.common.ErrorCode;
import com.group4.kaoyan.modules.admin.dashboard.dto.AdminDashboardSummaryVO;
import com.group4.kaoyan.modules.admin.dashboard.dto.DailyCountDTO;
import com.group4.kaoyan.modules.admin.dashboard.dto.DashboardTrendVO;
import com.group4.kaoyan.modules.admin.dashboard.mapper.DashboardMetricsMapper;
import com.group4.kaoyan.modules.admin.dashboard.service.DashboardStatisticsService;
import com.group4.kaoyan.modules.admin.dashboard.service.DashboardSystemMetricsProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 大屏统计服务单元测试（不依赖数据库）。
 *
 * <p>固定时钟为 {@code 2026-11-21T14:00+08:00}，因此日期边界断言完全可重复。</p>
 */
@DisplayName("大屏统计服务")
class DashboardStatisticsServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-21T06:00:00Z"), ZONE);

    private final DashboardMetricsMapper metricsMapper = mock(DashboardMetricsMapper.class);
    private final DashboardSystemMetricsProvider systemProvider = mock(DashboardSystemMetricsProvider.class);
    private final DashboardStatisticsService service =
            new DashboardStatisticsService(metricsMapper, systemProvider, CLOCK);

    @BeforeEach
    void stubEmptyDatabase() {
        when(metricsMapper.countUsers()).thenReturn(0L);
        when(metricsMapper.countEnabledUsers()).thenReturn(0L);
        when(metricsMapper.countLogins(any(), any(), anyInt())).thenReturn(0L);
        when(metricsMapper.countFiles()).thenReturn(0L);
        when(metricsMapper.sumFileSize()).thenReturn(0L);
        when(metricsMapper.countDocuments()).thenReturn(0L);
        when(metricsMapper.countFailedDocuments()).thenReturn(0L);
        when(metricsMapper.countConversations()).thenReturn(0L);
        when(metricsMapper.countMessages()).thenReturn(0L);
        when(metricsMapper.countMessagesBetween(any(), any())).thenReturn(0L);
        when(metricsMapper.dailyLogins(any(), any())).thenReturn(List.of());
        when(metricsMapper.dailyUploads(any(), any())).thenReturn(List.of());
        when(metricsMapper.dailyChatMessages(any(), any())).thenReturn(List.of());
        when(systemProvider.current()).thenReturn(new com.group4.kaoyan.modules.admin.dashboard.dto.DashboardSystemVO());
    }

    private DailyCountDTO row(String date, long total) {
        DailyCountDTO dto = new DailyCountDTO();
        dto.setStatDate(date);
        dto.setTotal(total);
        return dto;
    }

    @Test
    @DisplayName("7 天趋势日期连续且数组等长")
    void shouldBuildSevenDayTrend() {
        AdminDashboardSummaryVO summary = service.build(7);

        DashboardTrendVO trend = summary.getTrend();
        assertEquals(7, trend.getDates().size());
        assertEquals("11-15", trend.getDates().get(0));
        assertEquals("11-21", trend.getDates().get(6));
        assertEquals(7, trend.getLogins().size());
        assertEquals(7, trend.getUploads().size());
        assertEquals(7, trend.getChatMessages().size());
    }

    @Test
    @DisplayName("14 / 30 天数组长度正确")
    void shouldBuildFourteenAndThirtyDayTrend() {
        assertEquals(14, service.build(14).getTrend().getDates().size());
        assertEquals(30, service.build(30).getTrend().getDates().size());
    }

    @Test
    @DisplayName("缺失日期补 0")
    void shouldFillMissingDatesWithZero() {
        when(metricsMapper.dailyLogins(any(), any()))
                .thenReturn(List.of(row("11-19", 12L), row("11-21", 18L)));

        DashboardTrendVO trend = service.build(7).getTrend();

        assertEquals(List.of(0L, 0L, 0L, 0L, 12L, 0L, 18L), trend.getLogins());
    }

    @Test
    @DisplayName("空库时所有计数为 0 且不抛异常")
    void shouldReturnZerosOnEmptyDatabase() {
        AdminDashboardSummaryVO summary = service.build(7);

        assertEquals(0L, summary.getCards().getTotalUsers());
        assertEquals(0L, summary.getCards().getStorageBytes());
        assertEquals(0L, summary.getCards().getTodayChatMessages());
        assertEquals(List.of(0L, 0L, 0L, 0L, 0L, 0L, 0L), summary.getTrend().getChatMessages());
    }

    @Test
    @DisplayName("SUM(file_size) 为 0 时 storageBytes 为 0")
    void shouldKeepZeroStorageWhenSumIsZero() {
        when(metricsMapper.sumFileSize()).thenReturn(0L);

        assertEquals(0L, service.build(7).getCards().getStorageBytes());
    }

    @Test
    @DisplayName("失败文档计数原样透传")
    void shouldPassThroughFailedDocuments() {
        when(metricsMapper.countFailedDocuments()).thenReturn(3L);

        assertEquals(3L, service.build(7).getCards().getFailedDocuments());
    }

    @Test
    @DisplayName("今日登录查询使用 Asia/Shanghai 的 [今日 00:00, 明日 00:00)")
    void shouldUseShanghaiTodayBoundary() {
        when(metricsMapper.countLogins(any(), any(), eq(1))).thenReturn(45L);
        when(metricsMapper.countLogins(any(), any(), eq(0))).thenReturn(3L);

        AdminDashboardSummaryVO summary = service.build(7);

        assertEquals(45L, summary.getCards().getTodayLogins());
        assertEquals(3L, summary.getCards().getTodayLoginFailures());
        verify(metricsMapper).countLogins(
                LocalDateTime.of(2026, 11, 21, 0, 0),
                LocalDateTime.of(2026, 11, 22, 0, 0),
                1);
        verify(metricsMapper).countLogins(
                LocalDateTime.of(2026, 11, 21, 0, 0),
                LocalDateTime.of(2026, 11, 22, 0, 0),
                0);
    }

    @Test
    @DisplayName("days 只允许 7 / 14 / 30")
    void shouldRejectUnsupportedDays() {
        for (int days : new int[]{0, 8, 31}) {
            BizException e = assertThrows(BizException.class, () -> service.build(days));
            assertEquals(ErrorCode.PARAM_INVALID, e.getCode());
        }
    }

    @Test
    @DisplayName("generatedAt 为 Asia/Shanghai 的 ISO 8601 时间")
    void shouldGenerateIsoTimestamp() {
        assertEquals("2026-11-21T14:00:00+08:00", service.build(7).getGeneratedAt());
    }
}
