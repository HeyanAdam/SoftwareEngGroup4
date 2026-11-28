package com.group4.kaoyan.modules.notification.job;

import com.group4.kaoyan.modules.notification.service.ReminderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class TaskDueReminderJob {

    private final ReminderService reminderService;

    @Scheduled(fixedDelayString = "${kaoyan.notification.task-due-delay-ms:60000}",
            initialDelayString = "${kaoyan.notification.task-due-initial-delay-ms:30000}")
    public void run() {
        try {
            int created = reminderService.executeDueTaskReminders();
            if (created > 0) {
                log.info("已生成任务截止提醒 {} 条", created);
            }
        } catch (Exception e) {
            log.error("任务截止提醒任务失败", e);
        }
    }
}
