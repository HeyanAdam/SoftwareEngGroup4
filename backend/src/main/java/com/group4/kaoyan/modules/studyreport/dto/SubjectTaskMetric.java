package com.group4.kaoyan.modules.studyreport.dto;

import lombok.Data;

@Data
public class SubjectTaskMetric {

    private String subject;
    private int totalTasks;
    private int completedTasks;
    private int overdueTasks;
}
