package com.group4.kaoyan.modules.admin.dashboard.service;

import com.group4.kaoyan.common.BizException;
import com.group4.kaoyan.common.ErrorCode;
import com.group4.kaoyan.modules.admin.dashboard.dto.AdminDashboardSummaryVO;
import com.group4.kaoyan.modules.admin.dashboard.dto.DailyCountDTO;
import com.group4.kaoyan.modules.admin.dashboard.dto.DashboardCardsVO;
import com.group4.kaoyan.modules.admin.dashboard.dto.DashboardSystemVO;
import com.group4.kaoyan.modules.admin.dashboard.dto.DashboardTrendVO;
import com.group4.kaoyan.modules.admin.dashboard.mapper.DashboardMetricsMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 运营大屏统计服务：一次请求组装卡片、趋势、系统指标与生成时间。
 *
 * <p>三条硬约束：</p>
 * <ol>
 *   <li>时间统一按 {@code Asia/Shanghai} 计算，不依赖 JVM 默认时区；</li>
 *   <li>趋势登录口径为<b>成功登录</b>，与卡片 {@code todayLogins} 保持一致；</li>
 *   <li>数据库只返回有数据的日期，缺失日期在 Java 端补 {@code 0L}，保证四个数组等长。</li>
 * </ol>
 */
@Service
public class DashboardStatisticsService implements DashboardSummaryProvider {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_LABEL = DateTimeFormatter.ofPattern("MM-dd");
    /** 固定到秒，输出形如 2026-11-21T14:00:00+08:00，与接口契约样例一致。 */
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
    private static final Set<Integer> ALLOWED_DAYS = Set.of(7, 14, 30);

    private final DashboardMetricsMapper metricsMapper;
    private final DashboardSystemMetricsProvider systemMetricsProvider;
    private final Clock clock;

    @Autowired
    public DashboardStatisticsService(DashboardMetricsMapper metricsMapper,
                                      DashboardSystemMetricsProvider systemMetricsProvider) {
        this(metricsMapper, systemMetricsProvider, Clock.system(ZONE));
    }

    /** 供测试注入固定时钟，保证日期边界断言可重复。 */
    public DashboardStatisticsService(DashboardMetricsMapper metricsMapper,
                                      DashboardSystemMetricsProvider systemMetricsProvider,
                                      Clock clock) {
        this.metricsMapper = metricsMapper;
        this.systemMetricsProvider = systemMetricsProvider;
        this.clock = clock;
    }

    @Override
    public AdminDashboardSummaryVO build(int days) {
        validateDays(days);

        LocalDate today = LocalDate.now(clock);
        LocalDate startDate = today.minusDays(days - 1L);
        LocalDateTime rangeStart = startDate.atStartOfDay();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime tomorrowStart = today.plusDays(1).atStartOfDay();

        AdminDashboardSummaryVO summary = new AdminDashboardSummaryVO();
        summary.setCards(buildCards(todayStart, tomorrowStart));
        summary.setTrend(buildTrend(days, startDate, rangeStart, tomorrowStart));
        summary.setSystem(systemMetricsProvider.current());
        summary.setGeneratedAt(OffsetDateTime.now(clock).format(TIMESTAMP));
        return summary;
    }

    private void validateDays(int days) {
        if (!ALLOWED_DAYS.contains(days)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "days 只允许 7、14、30");
        }
    }

    private DashboardCardsVO buildCards(LocalDateTime todayStart, LocalDateTime tomorrowStart) {
        DashboardCardsVO cards = new DashboardCardsVO();
        cards.setTotalUsers(metricsMapper.countUsers());
        cards.setEnabledUsers(metricsMapper.countEnabledUsers());
        cards.setTodayLogins(metricsMapper.countLogins(todayStart, tomorrowStart, 1));
        cards.setTodayLoginFailures(metricsMapper.countLogins(todayStart, tomorrowStart, 0));
        cards.setFiles(metricsMapper.countFiles());
        cards.setStorageBytes(metricsMapper.sumFileSize());
        cards.setDocuments(metricsMapper.countDocuments());
        cards.setFailedDocuments(metricsMapper.countFailedDocuments());
        cards.setConversations(metricsMapper.countConversations());
        cards.setChatMessages(metricsMapper.countMessages());
        cards.setTodayChatMessages(metricsMapper.countMessagesBetween(todayStart, tomorrowStart));
        return cards;
    }

    private DashboardTrendVO buildTrend(int days,
                                        LocalDate startDate,
                                        LocalDateTime rangeStart,
                                        LocalDateTime tomorrowStart) {
        List<LocalDate> dateList = IntStream.range(0, days)
                .mapToObj(offset -> startDate.plusDays(offset))
                .toList();

        DashboardTrendVO trend = new DashboardTrendVO();
        trend.setDates(dateList.stream().map(DATE_LABEL::format).toList());
        trend.setLogins(fillMissing(dateList, metricsMapper.dailyLogins(rangeStart, tomorrowStart)));
        trend.setUploads(fillMissing(dateList, metricsMapper.dailyUploads(rangeStart, tomorrowStart)));
        trend.setChatMessages(fillMissing(dateList, metricsMapper.dailyChatMessages(rangeStart, tomorrowStart)));
        return trend;
    }

    /** 把“只有有数据日期”的聚合结果展开成与 dates 等长的数组，缺失日期补 0。 */
    private List<Long> fillMissing(List<LocalDate> dateList, List<DailyCountDTO> rows) {
        Map<String, Long> counts = rows == null ? Map.of() : rows.stream()
                .filter(row -> row != null && row.getStatDate() != null)
                .collect(Collectors.toMap(DailyCountDTO::getStatDate,
                        row -> row.getTotal() == null ? 0L : row.getTotal(),
                        (left, right) -> left));
        return dateList.stream()
                .map(date -> counts.getOrDefault(DATE_LABEL.format(date), 0L))
                .toList();
    }
}
