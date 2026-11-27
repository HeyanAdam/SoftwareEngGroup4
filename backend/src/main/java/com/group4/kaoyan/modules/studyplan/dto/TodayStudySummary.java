package com.group4.kaoyan.modules.studyplan.dto;

/**
 * 今日学习汇总统计。与契约 §4.2 TodayStudySummary 对齐。
 */
public record TodayStudySummary(
        int completedTasks,
        int totalTasks,
        int studyMinutes,
        int streakDays) {

    public static TodayStudySummary empty() {
        return new TodayStudySummary(0, 0, 0, 0);
    }
}
