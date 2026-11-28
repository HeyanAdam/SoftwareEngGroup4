package com.group4.kaoyan.modules.studyreport.dto;

import java.util.List;

public record WeeklyReportContent(
        List<String> weakSubjects,
        String summary,
        List<String> nextActions) {
}
