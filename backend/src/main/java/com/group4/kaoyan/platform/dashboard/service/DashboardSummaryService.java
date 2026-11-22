package com.group4.kaoyan.platform.dashboard.service;

import com.group4.kaoyan.common.BizException;
import com.group4.kaoyan.common.ErrorCode;
import com.group4.kaoyan.modules.admin.dashboard.dto.AdminDashboardSummaryVO;
import com.group4.kaoyan.modules.admin.dashboard.dto.DashboardCardsVO;
import com.group4.kaoyan.modules.admin.dashboard.dto.DashboardSystemVO;
import com.group4.kaoyan.modules.admin.dashboard.dto.DashboardTrendVO;
import com.group4.kaoyan.modules.admin.dashboard.service.DashboardStatisticsService;
import com.group4.kaoyan.modules.admin.dashboard.service.DashboardSummaryProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * 大屏最终聚合服务（步骤 E5）。
 *
 * <p>固定流水线：</p>
 * <pre>
 * 校验 days → 缓存读取 → 业务统计（Provider）→ 系统/队列/Outbox 指标 → DTO 组装 → 缓存写入
 * </pre>
 *
 * <p>两条硬要求：</p>
 * <ol>
 *   <li><b>故障不拖垮整页</b>：Redis 读写、RabbitMQ 队列查询、Outbox 计数全部在各自服务内降级，
 *       这里不会因为中间件故障抛 500；MySQL 业务统计失败才允许向上抛出（那才是真的查不到数据）。</li>
 *   <li><b>不编造数据</b>：业务 Provider 尚未接入时（甲的分支未合并），返回全 0 的合法空结果，
 *       系统指标仍按真实探活输出，绝不返回假 UP。</li>
 * </ol>
 *
 * <p>缓存存的是「业务数据 + 真实指标」整体快照，命中时不再触发任何 SQL 或 AMQP 调用，
 * 这样 15 秒轮询对数据库和 broker 的压力是常数级。</p>
 */
@Slf4j
@Service
@Primary
@RequiredArgsConstructor
public class DashboardSummaryService implements DashboardSummaryProvider {

    /** 只允许的趋势天数。 */
    public static final Set<Integer> ALLOWED_DAYS = Set.of(7, 14, 30);

    /** 统一时区：大屏所有时间口径按 Asia/Shanghai。 */
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    /** 趋势日期标签格式。 */
    private static final DateTimeFormatter DATE_LABEL = DateTimeFormatter.ofPattern("MM-dd");

    /** 生成时间固定到秒，与甲的分册契约样例一致（如 2026-11-22T14:50:44+08:00）。 */
    private static final DateTimeFormatter GENERATED_AT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private final DashboardCacheService cacheService;
    /** 业务统计提供者（甲的实现）：Bean 缺失时不报错，退化为全 0 空结果。 */
    private final ObjectProvider<DashboardStatisticsService> businessProviders;
    private final SystemHealthMetricsService systemHealthMetricsService;
    private final RabbitQueueMetricsService rabbitQueueMetricsService;
    private final OutboxMetricsService outboxMetricsService;

    /**
     * 获取大屏聚合数据。
     *
     * @param days 7 / 14 / 30
     * @return 缓存命中直接返回快照；未命中则重新聚合后回填缓存
     */
    /** 接口入口：Controller 注入的是 DashboardSummaryProvider，这里转给带缓存的 summary。 */
    @Override
    public AdminDashboardSummaryVO build(int days) {
        return summary(days);
    }

    public AdminDashboardSummaryVO summary(int days) {
        requireAllowedDays(days);

        Optional<AdminDashboardSummaryVO> cached = cacheService.get(days);
        if (cached.isPresent()) {
            return cached.get();
        }

        AdminDashboardSummaryVO summary = assemble(days);
        cacheService.put(days, summary);
        return summary;
    }

    /** days 只允许 7 / 14 / 30，其余返回 400 + 10000。 */
    private void requireAllowedDays(int days) {
        if (!ALLOWED_DAYS.contains(days)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "days 只允许 7、14、30");
        }
    }

    /** 组装一次完整响应。 */
    private AdminDashboardSummaryVO assemble(int days) {
        AdminDashboardSummaryVO business = buildBusiness(days);

        AdminDashboardSummaryVO summary = new AdminDashboardSummaryVO();
        summary.setCards(business.getCards() == null ? DashboardCardsVO.empty() : business.getCards());
        summary.setTrend(normalizeTrend(business.getTrend(), days));
        summary.setSystem(buildSystem());
        summary.setGeneratedAt(OffsetDateTime.now(ZONE).format(GENERATED_AT));
        return summary;
    }

    /**
     * 业务统计：有甲的实现就调用，没有就返回全 0 的合法空结果。
     * 这里不让 Bean 缺失变成启动失败，也不假装有数据。
     */
    private AdminDashboardSummaryVO buildBusiness(int days) {
        DashboardStatisticsService provider = businessProviders.getIfAvailable();
        if (provider == null) {
            AdminDashboardSummaryVO empty = new AdminDashboardSummaryVO();
            empty.setCards(DashboardCardsVO.empty());
            empty.setTrend(emptyTrend(days));
            return empty;
        }
        return provider.build(days);
    }

    /**
     * 系统指标：探活三项 + 队列三项 + Outbox 两项。
     *
     * <p>RabbitMQ 判定：连接探活 UP 且队列可读才算 UP；队列不可读时同时把队列数字归 0，
     * 避免"状态红着、数字还是旧的"这种自相矛盾的展示。</p>
     */
    private DashboardSystemVO buildSystem() {
        SystemHealthMetricsService.SystemHealth health = systemHealthMetricsService.probe();
        RabbitQueueMetricsService.QueueMetrics queues = rabbitQueueMetricsService.probe();
        OutboxMetricsService.OutboxMetrics outbox = outboxMetricsService.probe();

        DashboardSystemVO system = new DashboardSystemVO();
        system.setMysql(health.mysql());
        system.setRedis(health.redis());
        boolean rabbitUp = SystemHealthMetricsService.UP.equals(health.rabbitmq()) && queues.up();
        system.setRabbitmq(rabbitUp ? SystemHealthMetricsService.UP : SystemHealthMetricsService.DOWN);

        system.setDocParseReady(queues.docParseReady());
        system.setDocParseConsumers(queues.docParseConsumers());
        system.setDocParseDlq(queues.docParseDlq());

        system.setOutboxPending(outbox.pending());
        system.setOutboxFailed(outbox.failed());
        return system;
    }

    /**
     * 趋势兜底：保证 dates 长度等于 days，且三条数列与之等长。
     * Provider 返回残缺结构时补 0，不让前端数组越界。
     */
    private DashboardTrendVO normalizeTrend(DashboardTrendVO trend, int days) {
        if (trend == null || trend.getDates() == null || trend.getDates().size() != days) {
            log.warn("趋势数据结构不完整（期望 {} 天），按空趋势补零", days);
            return emptyTrend(days);
        }
        trend.setLogins(pad(trend.getLogins(), days));
        trend.setUploads(pad(trend.getUploads(), days));
        trend.setChatMessages(pad(trend.getChatMessages(), days));
        return trend;
    }

    /** 最近 days 天的空趋势：日期连续、数值全 0。 */
    private DashboardTrendVO emptyTrend(int days) {
        LocalDate today = LocalDate.now(ZONE);
        List<String> dates = IntStream.range(0, days)
                .mapToObj(offset -> today.minusDays(days - 1L - offset))
                .map(date -> date.format(DATE_LABEL))
                .toList();

        DashboardTrendVO trend = new DashboardTrendVO();
        trend.setDates(new ArrayList<>(dates));
        trend.setLogins(zeros(days));
        trend.setUploads(zeros(days));
        trend.setChatMessages(zeros(days));
        return trend;
    }

    /** 补齐/截断到 days 长度。 */
    private static List<Long> pad(List<Long> values, int days) {
        List<Long> result = new ArrayList<>(days);
        for (int i = 0; i < days; i++) {
            Long value = values != null && i < values.size() ? values.get(i) : null;
            result.add(value == null ? 0L : value);
        }
        return result;
    }

    private static List<Long> zeros(int days) {
        return new ArrayList<>(java.util.Collections.nCopies(days, 0L));
    }
}
