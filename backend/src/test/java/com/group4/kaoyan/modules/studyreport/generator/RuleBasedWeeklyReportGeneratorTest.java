package com.group4.kaoyan.modules.studyreport.generator;

import com.group4.kaoyan.modules.studyreport.dto.SubjectTaskMetric;
import com.group4.kaoyan.modules.studyreport.dto.WeeklyMetrics;
import com.group4.kaoyan.modules.studyreport.dto.WeeklyReportContent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("规则式周报生成器")
class RuleBasedWeeklyReportGeneratorTest {

    private static final LocalDate WEEK_START = LocalDate.of(2026, 11, 23);
    private final RuleBasedWeeklyReportGenerator generator =
            new RuleBasedWeeklyReportGenerator();

    @Test
    @DisplayName("空数据完成率为 0，并给出启动行动")
    void handlesEmptyWeek() {
        WeeklyMetrics metrics = metrics(0, 0, 0, 0, List.of(), List.of());

        WeeklyReportContent content = generator.generate(metrics);

        assertEquals(0, metrics.completionRate().compareTo(java.math.BigDecimal.ZERO));
        assertTrue(content.summary().contains("暂无已完成的学习任务"));
        assertTrue(content.nextActions().contains("先制定下周学习计划并设置每日任务"));
    }

    @Test
    @DisplayName("低于 60% 时减少任务量，薄弱科目最多 3 个")
    void appliesLowCompletionRules() {
        WeeklyMetrics metrics = metrics(
                5, 2, 100, 4, List.of("数学", "英语", "政治", "专业课"),
                List.of(subject("数学", 3, 0, 2), subject("英语", 2, 2, 0)));

        WeeklyReportContent content = generator.generate(metrics);

        assertEquals(3, content.weakSubjects().size());
        assertEquals("数学", content.weakSubjects().get(0));
        assertTrue(content.nextActions().contains("优先补完本周逾期任务"));
        assertTrue(content.nextActions().contains("下周适当减少任务量，先保证核心任务完成"));
        assertTrue(content.nextActions().size() <= 3);
    }

    private WeeklyMetrics metrics(int total, int completed, int minutes, int questions,
                                  List<String> profileWeaknesses,
                                  List<SubjectTaskMetric> subjects) {
        return new WeeklyMetrics(
                1L, WEEK_START, WEEK_START.plusDays(6), total, completed, minutes, questions,
                subjects.stream().mapToInt(SubjectTaskMetric::getOverdueTasks).sum(),
                subjects, profileWeaknesses);
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
