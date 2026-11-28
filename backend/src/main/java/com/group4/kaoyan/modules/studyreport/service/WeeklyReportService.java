package com.group4.kaoyan.modules.studyreport.service;

import com.group4.kaoyan.common.StudyTimeUtils;
import com.group4.kaoyan.modules.studyreport.dto.SubjectTaskMetric;
import com.group4.kaoyan.modules.studyreport.dto.WeeklyMetrics;
import com.group4.kaoyan.modules.studyreport.dto.WeeklyReportContent;
import com.group4.kaoyan.modules.studyreport.dto.WeeklyStudyReportSummaryVO;
import com.group4.kaoyan.modules.studyreport.dto.WeeklyTaskTotals;
import com.group4.kaoyan.modules.studyreport.entity.StudyWeeklyReport;
import com.group4.kaoyan.modules.studyreport.generator.WeeklyReportGenerator;
import com.group4.kaoyan.modules.studyreport.mapper.WeeklyReportMapper;
import com.group4.kaoyan.modules.studyreport.mapper.WeeklyReportMetricsMapper;
import com.group4.kaoyan.modules.user.entity.UserProfile;
import com.group4.kaoyan.modules.user.mapper.UserProfileMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 周报查询和单用户生成。当前周按 Asia/Shanghai 计算。
 */
@Service
@RequiredArgsConstructor
public class WeeklyReportService {

    private final WeeklyReportMapper weeklyReportMapper;
    private final WeeklyReportMetricsMapper metricsMapper;
    private final UserProfileMapper userProfileMapper;
    private final WeeklyReportGenerator weeklyReportGenerator;
    private final Clock studyClock;

    public WeeklyStudyReportSummaryVO current(Long userId) {
        LocalDate weekStart = currentWeekStart();
        LocalDate weekEnd = weekStart.plusDays(6);
        StudyWeeklyReport report = weeklyReportMapper.selectByUserAndWeek(userId, weekStart);
        return report == null
                ? emptyReport(weekStart, weekEnd)
                : toVO(report);
    }

    /**
     * 生成或更新单个用户报告。重复执行只更新同一行，不创建重复周报。
     */
    @Transactional
    public WeeklyStudyReportSummaryVO generateForUser(Long userId, LocalDate weekStart) {
        LocalDate normalizedWeekStart =
                weekStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = normalizedWeekStart.plusDays(6);
        LocalDateTime rangeStart = normalizedWeekStart.atStartOfDay();
        LocalDateTime rangeEnd = weekEnd.plusDays(1).atStartOfDay();
        LocalDateTime now = LocalDateTime.now(studyClock);

        WeeklyTaskTotals totals = metricsMapper.selectTaskTotals(
                userId, normalizedWeekStart, weekEnd, now);
        List<SubjectTaskMetric> subjects = metricsMapper.selectSubjectMetrics(
                userId, normalizedWeekStart, weekEnd, now);
        int chatQuestions = metricsMapper.countUserQuestions(
                userId, rangeStart, rangeEnd);
        List<String> profileWeaknesses = profileWeaknesses(userId);

        WeeklyMetrics metrics = new WeeklyMetrics(
                userId,
                normalizedWeekStart,
                weekEnd,
                totals.getTotalTasks(),
                totals.getCompletedTasks(),
                totals.getStudyMinutes(),
                chatQuestions,
                totals.getOverdueTasks(),
                subjects,
                profileWeaknesses);
        WeeklyReportContent content = weeklyReportGenerator.generate(metrics);

        StudyWeeklyReport existing =
                weeklyReportMapper.selectByUserAndWeek(userId, normalizedWeekStart);
        StudyWeeklyReport report = existing == null ? new StudyWeeklyReport() : existing;
        applyContent(report, userId, metrics, content, now, existing == null);

        if (existing == null) {
            try {
                weeklyReportMapper.insert(report);
            } catch (DuplicateKeyException concurrent) {
                existing = weeklyReportMapper.selectByUserAndWeek(userId, normalizedWeekStart);
                if (existing == null) {
                    throw concurrent;
                }
                report = existing;
                applyContent(report, userId, metrics, content, now, false);
                weeklyReportMapper.update(report);
            }
        } else {
            weeklyReportMapper.update(report);
        }

        StudyWeeklyReport persisted =
                weeklyReportMapper.selectByUserAndWeek(userId, normalizedWeekStart);
        return toVO(persisted == null ? report : persisted);
    }

    public LocalDate currentWeekStart() {
        return LocalDate.now(studyClock)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private void applyContent(StudyWeeklyReport report,
                              Long userId,
                              WeeklyMetrics metrics,
                              WeeklyReportContent content,
                              LocalDateTime now,
                              boolean creating) {
        report.setUserId(userId);
        report.setWeekStart(metrics.weekStart());
        report.setWeekEnd(metrics.weekEnd());
        report.setCompletionRate(metrics.completionRate());
        report.setStudyMinutes(metrics.studyMinutes());
        report.setChatQuestions(metrics.chatQuestions());
        report.setWeakSubjects(content.weakSubjects());
        report.setNextActions(content.nextActions());
        report.setSummary(content.summary());
        report.setGeneratedAt(now);
        report.setUpdateTime(now);
        if (creating) {
            report.setCreateTime(now);
        }
    }

    private List<String> profileWeaknesses(Long userId) {
        UserProfile profile = userProfileMapper.selectByUserId(userId);
        if (profile == null || profile.getWeakPointsJson() == null) {
            return List.of();
        }
        Object value = profile.getWeakPointsJson();
        List<String> result = new ArrayList<>();
        if (value instanceof Collection<?> collection) {
            collection.stream()
                    .map(Object::toString)
                    .map(String::trim)
                    .filter(text -> !text.isBlank())
                    .forEach(result::add);
        } else if (value instanceof Map<?, ?> map) {
            map.values().stream()
                    .filter(item -> item != null)
                    .map(Object::toString)
                    .map(String::trim)
                    .filter(text -> !text.isBlank())
                    .forEach(result::add);
        } else {
            String text = value.toString().trim();
            if (!text.isBlank()) {
                result.add(text);
            }
        }
        return result.stream().distinct().limit(3).toList();
    }

    private WeeklyStudyReportSummaryVO emptyReport(LocalDate weekStart, LocalDate weekEnd) {
        return new WeeklyStudyReportSummaryVO(
                null,
                weekStart,
                weekEnd,
                WeeklyMetrics.ZERO_RATE,
                0,
                0,
                List.of(),
                "",
                List.of(),
                null);
    }

    private WeeklyStudyReportSummaryVO toVO(StudyWeeklyReport report) {
        return new WeeklyStudyReportSummaryVO(
                report.getId(),
                report.getWeekStart(),
                report.getWeekEnd(),
                report.getCompletionRate(),
                report.getStudyMinutes() == null ? 0 : report.getStudyMinutes(),
                report.getChatQuestions() == null ? 0 : report.getChatQuestions(),
                report.getWeakSubjects() == null ? List.of() : report.getWeakSubjects(),
                report.getSummary() == null ? "" : report.getSummary(),
                report.getNextActions() == null ? List.of() : report.getNextActions(),
                StudyTimeUtils.toOffset(report.getGeneratedAt()));
    }
}
