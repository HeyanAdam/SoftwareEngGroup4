package com.group4.kaoyan.modules.studyreport.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record WeeklyStudyReportSummaryVO(
        Long reportId,
        LocalDate weekStart,
        LocalDate weekEnd,
        BigDecimal completionRate,
        int studyMinutes,
        int chatQuestions,
        List<String> weakSubjects,
        String summary,
        List<String> nextActions,
        OffsetDateTime generatedAt) {
}
