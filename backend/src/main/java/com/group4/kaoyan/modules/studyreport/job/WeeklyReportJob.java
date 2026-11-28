package com.group4.kaoyan.modules.studyreport.job;

import com.group4.kaoyan.modules.studyreport.service.WeeklyReportBatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class WeeklyReportJob {

    private final WeeklyReportBatchService weeklyReportBatchService;

    @Scheduled(cron = "${kaoyan.notification.weekly-report-cron:0 0 20 * * SUN}",
            zone = "Asia/Shanghai")
    public void run() {
        try {
            int notified = weeklyReportBatchService.executeWeeklyReports();
            log.info("周报任务完成，生成新通知 {} 条", notified);
        } catch (Exception e) {
            log.error("周报生成任务失败", e);
        }
    }
}
