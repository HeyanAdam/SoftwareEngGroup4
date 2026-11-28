package com.group4.kaoyan.modules.notification.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class DailyPlanReminderSource {

    private Long userId;
    private Long planId;
    private LocalDate planDate;
    private int taskCount;
    private int estimatedMinutes;
}
