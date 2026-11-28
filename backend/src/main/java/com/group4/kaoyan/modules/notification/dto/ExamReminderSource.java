package com.group4.kaoyan.modules.notification.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ExamReminderSource {

    private Long userId;
    private LocalDate examDate;
}
