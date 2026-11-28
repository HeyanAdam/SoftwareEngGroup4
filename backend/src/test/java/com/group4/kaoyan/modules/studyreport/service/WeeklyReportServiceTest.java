package com.group4.kaoyan.modules.studyreport.service;

import com.group4.kaoyan.modules.studyreport.dto.SubjectTaskMetric;
import com.group4.kaoyan.modules.studyreport.dto.WeeklyStudyReportSummaryVO;
import com.group4.kaoyan.modules.studyreport.dto.WeeklyTaskTotals;
import com.group4.kaoyan.modules.studyreport.entity.StudyWeeklyReport;
import com.group4.kaoyan.modules.studyreport.generator.RuleBasedWeeklyReportGenerator;
import com.group4.kaoyan.modules.studyreport.mapper.WeeklyReportMapper;
import com.group4.kaoyan.modules.studyreport.mapper.WeeklyReportMetricsMapper;
import com.group4.kaoyan.modules.user.mapper.UserProfileMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("每周学习报告 Service")
class WeeklyReportServiceTest {

    private static final Long USER_ID = 10001L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 28, 20, 0);
    private static final Clock CLOCK =
            Clock.fixed(NOW.toInstant(ZoneOffset.ofHours(8)), ZoneOffset.ofHours(8));
    private static final LocalDate WEEK_START = LocalDate.of(2026, 11, 23);

    private WeeklyReportMapper reportMapper;
    private WeeklyReportMetricsMapper metricsMapper;
    private WeeklyReportService service;

    @BeforeEach
    void setUp() {
        reportMapper = mock(WeeklyReportMapper.class);
        metricsMapper = mock(WeeklyReportMetricsMapper.class);
        UserProfileMapper profileMapper = mock(UserProfileMapper.class);
        service = new WeeklyReportService(
                reportMapper, metricsMapper, profileMapper,
                new RuleBasedWeeklyReportGenerator(), CLOCK);
    }

    @Test
    @DisplayName("当前周未生成报告返回空结构而不是 404")
    void currentReturnsEmptyStructure() {
        when(reportMapper.selectByUserAndWeek(USER_ID, WEEK_START)).thenReturn(null);

        WeeklyStudyReportSummaryVO result = service.current(USER_ID);

        assertNull(result.reportId());
        assertEquals(WEEK_START, result.weekStart());
        assertEquals(WEEK_START.plusDays(6), result.weekEnd());
        assertEquals(0, result.completionRate().compareTo(BigDecimal.ZERO));
        assertEquals(List.of(), result.weakSubjects());
        assertEquals(List.of(), result.nextActions());
    }

    @Test
    @DisplayName("完成任务数与分钟数、提问数统计正确")
    void calculatesMetrics() {
        WeeklyTaskTotals totals = totals(4, 3, 75, 1);
        when(metricsMapper.selectTaskTotals(eq(USER_ID), eq(WEEK_START), any(), any()))
                .thenReturn(totals);
        when(metricsMapper.selectSubjectMetrics(eq(USER_ID), eq(WEEK_START), any(), any()))
                .thenReturn(List.of(subject("数学", 4, 3, 1)));
        when(metricsMapper.countUserQuestions(eq(USER_ID), any(), any())).thenReturn(8);
        when(reportMapper.selectByUserAndWeek(USER_ID, WEEK_START)).thenReturn(null);
        AtomicReference<StudyWeeklyReport> inserted = new AtomicReference<>();
        when(reportMapper.insert(any())).thenAnswer(invocation -> {
            StudyWeeklyReport report = invocation.getArgument(0);
            report.setId(30L);
            inserted.set(report);
            return 1;
        });
        when(reportMapper.selectByUserAndWeek(USER_ID, WEEK_START))
                .thenReturn(null)
                .thenAnswer(invocation -> inserted.get());

        WeeklyStudyReportSummaryVO result = service.generateForUser(USER_ID, WEEK_START);

        assertEquals(new BigDecimal("0.7500"), result.completionRate());
        assertEquals(75, result.studyMinutes());
        assertEquals(8, result.chatQuestions());
        assertEquals(30L, result.reportId());
        assertTrue(result.summary().contains("75%"));
    }

    @Test
    @DisplayName("同周重复生成只更新，不插入第二行")
    void repeatedGenerationUpdatesExistingRow() {
        StudyWeeklyReport existing = new StudyWeeklyReport();
        existing.setId(31L);
        existing.setUserId(USER_ID);
        existing.setWeekStart(WEEK_START);
        existing.setWeekEnd(WEEK_START.plusDays(6));
        existing.setCreateTime(NOW.minusDays(1));
        when(metricsMapper.selectTaskTotals(eq(USER_ID), eq(WEEK_START), any(), any()))
                .thenReturn(totals(0, 0, 0, 0));
        when(metricsMapper.selectSubjectMetrics(eq(USER_ID), eq(WEEK_START), any(), any()))
                .thenReturn(List.of());
        when(reportMapper.selectByUserAndWeek(USER_ID, WEEK_START)).thenReturn(existing);

        service.generateForUser(USER_ID, WEEK_START);

        ArgumentCaptor<StudyWeeklyReport> captor = ArgumentCaptor.forClass(StudyWeeklyReport.class);
        verify(reportMapper).update(captor.capture());
        assertEquals(31L, captor.getValue().getId());
        verify(reportMapper, never()).insert(any());
    }

    private WeeklyTaskTotals totals(int total, int completed, int minutes, int overdue) {
        WeeklyTaskTotals totals = new WeeklyTaskTotals();
        totals.setTotalTasks(total);
        totals.setCompletedTasks(completed);
        totals.setStudyMinutes(minutes);
        totals.setOverdueTasks(overdue);
        return totals;
    }

    private SubjectTaskMetric subject(String name, int total, int completed, int overdue) {
        SubjectTaskMetric metric = new SubjectTaskMetric();
        metric.setSubject(name);
        metric.setTotalTasks(total);
        metric.setCompletedTasks(completed);
        metric.setOverdueTasks(overdue);
        return metric;
    }
}
