package com.group4.kaoyan.modules.studyreport.service;

import com.group4.kaoyan.modules.notification.dto.CreateNotificationCommand;
import com.group4.kaoyan.modules.notification.service.NotificationCreationService;
import com.group4.kaoyan.modules.studyreport.dto.WeeklyStudyReportSummaryVO;
import com.group4.kaoyan.modules.studyreport.mapper.WeeklyReportMetricsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 每周批处理：逐用户独立生成，单个用户失败不阻塞其余用户。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeeklyReportBatchService {

    private final WeeklyReportService weeklyReportService;
    private final WeeklyReportMetricsMapper metricsMapper;
    private final NotificationCreationService notificationCreationService;

    public int executeWeeklyReports() {
        LocalDate weekStart = weeklyReportService.currentWeekStart();
        LocalDate weekEnd = weekStart.plusDays(6);
        LocalDateTime rangeStart = weekStart.atStartOfDay();
        LocalDateTime rangeEnd = weekEnd.plusDays(1).atStartOfDay();
        List<Long> userIds = metricsMapper.selectActiveUserIds(
                weekStart, weekEnd, rangeStart, rangeEnd);

        int notified = 0;
        for (Long userId : userIds) {
            try {
                WeeklyStudyReportSummaryVO report =
                        weeklyReportService.generateForUser(userId, weekStart);
                boolean created = notificationCreationService.createAndPush(
                        new CreateNotificationCommand(
                                userId,
                                "WEEKLY_REPORT",
                                "INFO",
                                "本周学习报告已生成",
                                "本周学习完成率 %s%%，学习 %d 分钟，提出 %d 个问题。"
                                        .formatted(percent(report), report.studyMinutes(),
                                                report.chatQuestions()),
                                "/study-plans",
                                "WEEKLY_REPORT:%d:%s".formatted(userId, weekStart)))
                        .created();
                if (created) {
                    notified++;
                }
            } catch (Exception e) {
                log.warn("周报生成失败 userId={} weekStart={}", userId, weekStart, e);
            }
        }
        return notified;
    }

    private String percent(WeeklyStudyReportSummaryVO report) {
        return report.completionRate()
                .multiply(java.math.BigDecimal.valueOf(100))
                .setScale(0, java.math.RoundingMode.HALF_UP)
                .toPlainString();
    }
}
