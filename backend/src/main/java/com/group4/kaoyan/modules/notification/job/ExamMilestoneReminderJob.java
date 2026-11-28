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
public class ExamMilestoneReminderJob {

    private final ReminderService reminderService;

    @Scheduled(cron = "${kaoyan.notification.exam-milestone-cron:0 0 9 * * *}",
            zone = "Asia/Shanghai")
    public void run() {
        try {
            int created = reminderService.executeExamMilestoneReminders();
            if (created > 0) {
                log.info("已生成考试节点提醒 {} 条", created);
            }
        } catch (Exception e) {
            log.error("考试节点提醒任务失败", e);
        }
    }
}
