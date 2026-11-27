package com.group4.kaoyan.modules.studyplan.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.util.List;

/**
 * 今日学习计划返回 VO。对应契约 §4.2 TodayStudyPlan.
 */
public record StudyPlanVO(
        Long planId,

        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate planDate,

        String title,

        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate examDate,

        Integer daysUntilExam,
        TodayStudySummary summary,
        List<StudyTaskVO> tasks) {
}
