package com.group4.kaoyan.modules.studyreport.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

public record WeeklyMetrics(
        Long userId,
        LocalDate weekStart,
        LocalDate weekEnd,
        int totalTasks,
        int completedTasks,
        int studyMinutes,
        int chatQuestions,
        int overdueTasks,
        List<SubjectTaskMetric> subjects,
        List<String> profileWeaknesses) {

    public static final BigDecimal ZERO_RATE = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

    public BigDecimal completionRate() {
        if (totalTasks <= 0) {
            return ZERO_RATE;
        }
        return BigDecimal.valueOf(completedTasks)
                .divide(BigDecimal.valueOf(totalTasks), 4, RoundingMode.HALF_UP);
    }
}
