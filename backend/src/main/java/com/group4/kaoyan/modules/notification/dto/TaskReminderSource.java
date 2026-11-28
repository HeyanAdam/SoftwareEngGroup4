package com.group4.kaoyan.modules.notification.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TaskReminderSource {

    private Long id;
    private Long userId;
    private String title;
    private LocalDateTime dueAt;
}
