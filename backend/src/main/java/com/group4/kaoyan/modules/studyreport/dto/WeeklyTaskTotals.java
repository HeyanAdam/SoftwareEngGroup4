package com.group4.kaoyan.modules.studyreport.dto;

import lombok.Data;

@Data
public class WeeklyTaskTotals {

    private int totalTasks;
    private int completedTasks;
    private int studyMinutes;
    private int overdueTasks;
}
