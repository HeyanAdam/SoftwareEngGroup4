package com.group4.kaoyan.modules.studyreport.generator;

import com.group4.kaoyan.modules.studyreport.dto.SubjectTaskMetric;
import com.group4.kaoyan.modules.studyreport.dto.WeeklyMetrics;
import com.group4.kaoyan.modules.studyreport.dto.WeeklyReportContent;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 第一版规则式周报生成器，不调用 LLM。
 */
@Component
public class RuleBasedWeeklyReportGenerator implements WeeklyReportGenerator {

    private static final int MAX_ITEMS = 3;

    @Override
    public WeeklyReportContent generate(WeeklyMetrics metrics) {
        List<String> weakSubjects = selectWeakSubjects(metrics);
        List<String> nextActions = selectNextActions(metrics, weakSubjects);
        return new WeeklyReportContent(weakSubjects, buildSummary(metrics), nextActions);
    }

    private List<String> selectWeakSubjects(WeeklyMetrics metrics) {
        Set<String> result = new LinkedHashSet<>();

        metrics.subjects().stream()
                .filter(subject -> subject.getTotalTasks() > subject.getCompletedTasks())
                .max(Comparator
                        .comparingDouble(this::incompleteRate)
                        .thenComparing(SubjectTaskMetric::getSubject))
                .map(SubjectTaskMetric::getSubject)
                .ifPresent(result::add);

        metrics.subjects().stream()
                .filter(subject -> subject.getOverdueTasks() > 0)
                .sorted(Comparator.comparingInt(SubjectTaskMetric::getOverdueTasks).reversed()
                        .thenComparing(SubjectTaskMetric::getSubject))
                .map(SubjectTaskMetric::getSubject)
                .forEach(result::add);

        result.addAll(metrics.profileWeaknesses());
        return result.stream().limit(MAX_ITEMS).toList();
    }

    private List<String> selectNextActions(WeeklyMetrics metrics, List<String> weakSubjects) {
        Set<String> actions = new LinkedHashSet<>();
        BigDecimal rate = metrics.completionRate();

        if (metrics.overdueTasks() > 0) {
            actions.add("优先补完本周逾期任务");
        }
        if (rate.compareTo(new BigDecimal("0.60")) < 0) {
            actions.add("下周适当减少任务量，先保证核心任务完成");
        } else if (rate.compareTo(new BigDecimal("0.80")) > 0) {
            actions.add("完成率良好，下周逐步增加专项任务");
        }
        if (!weakSubjects.isEmpty()) {
            actions.add("针对“%s”安排一次专项复习".formatted(weakSubjects.get(0)));
        }
        if (metrics.totalTasks() == 0) {
            actions.add("先制定下周学习计划并设置每日任务");
        }
        return actions.stream().limit(MAX_ITEMS).toList();
    }

    private String buildSummary(WeeklyMetrics metrics) {
        String rate = metrics.completionRate()
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .toPlainString();
        List<String> parts = new ArrayList<>();
        parts.add("本周任务完成率 %s%%，完成学习 %d 分钟，提出 %d 个问题。"
                .formatted(rate, metrics.studyMinutes(), metrics.chatQuestions()));
        if (metrics.overdueTasks() > 0) {
            parts.add("仍有 %d 项逾期任务需要优先处理。".formatted(metrics.overdueTasks()));
        }
        if (metrics.totalTasks() == 0) {
            parts.add("本周暂无已完成的学习任务，建议从设置少量明确任务开始。");
        }
        return String.join("", parts);
    }

    private double incompleteRate(SubjectTaskMetric subject) {
        if (subject.getTotalTasks() <= 0) {
            return 0;
        }
        return (double) (subject.getTotalTasks() - subject.getCompletedTasks())
                / subject.getTotalTasks();
    }
}
