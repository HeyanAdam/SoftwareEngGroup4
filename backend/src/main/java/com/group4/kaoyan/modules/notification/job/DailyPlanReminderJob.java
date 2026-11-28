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
public class DailyPlanReminderJob {

    private final ReminderService reminderService;

    @Scheduled(cron = "${kaoyan.notification.daily-plan-cron:0 0 8 * * *}",
            zone = "Asia/Shanghai")
    public void run() {
        try {
            int created = reminderService.executeDailyPlanReminders();
            if (created > 0) {
                log.info("已生成每日计划提醒 {} 条", created);
            }
        } catch (Exception e) {
            log.error("每日计划提醒任务失败", e);
        }
    }
}
