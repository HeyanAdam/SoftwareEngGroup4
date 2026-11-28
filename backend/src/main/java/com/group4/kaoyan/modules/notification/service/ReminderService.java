package com.group4.kaoyan.modules.notification.service;

import com.group4.kaoyan.modules.notification.dto.CreateNotificationCommand;
import com.group4.kaoyan.modules.notification.dto.DailyPlanReminderSource;
import com.group4.kaoyan.modules.notification.dto.ExamReminderSource;
import com.group4.kaoyan.modules.notification.dto.TaskReminderSource;
import com.group4.kaoyan.modules.notification.mapper.ReminderSourceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

/**
 * 每日计划、任务截止/逾期和考试节点提醒。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderService {

    private static final int SCAN_LIMIT = 1000;
    private static final Set<Integer> EXAM_MILESTONES = Set.of(30, 14, 7, 1);

    private final ReminderSourceMapper reminderSourceMapper;
    private final NotificationCreationService notificationCreationService;
    private final Clock studyClock;

    public int executeDailyPlanReminders() {
        LocalDate today = LocalDate.now(studyClock);
        int created = 0;
        for (DailyPlanReminderSource source : reminderSourceMapper.selectDailyPlanSources(today)) {
            try {
                if (create(new CreateNotificationCommand(
                        source.getUserId(),
                        "DAILY_PLAN",
                        "INFO",
                        "今日学习计划",
                        "今天还有 %d 项任务，预计需要 %d 分钟。".formatted(
                                source.getTaskCount(), source.getEstimatedMinutes()),
                        "/study-plans",
                        "DAILY_PLAN:%d:%s".formatted(source.getUserId(), source.getPlanDate())))) {
                    created++;
                }
            } catch (Exception e) {
                log.warn("每日计划提醒生成失败 userId={} planId={}",
                        source.getUserId(), source.getPlanId(), e);
            }
        }
        return created;
    }

    public int executeDueTaskReminders() {
        LocalDateTime now = LocalDateTime.now(studyClock);
        int created = 0;
        for (TaskReminderSource source : reminderSourceMapper.selectDueTaskSources(now, SCAN_LIMIT)) {
            try {
                long minutes = Math.max(1L,
                        (Duration.between(now, source.getDueAt()).getSeconds() + 59) / 60);
                if (create(new CreateNotificationCommand(
                        source.getUserId(),
                        "TASK_DUE",
                        "WARNING",
                        "任务即将截止",
                        "“%s”将在 %d 分钟后截止。".formatted(source.getTitle(), minutes),
                        "/study-plans",
                        taskDedupKey("TASK_DUE", source)))) {
                    created++;
                }
            } catch (Exception e) {
                log.warn("任务截止提醒生成失败 taskId={} userId={}",
                        source.getId(), source.getUserId(), e);
            }
        }
        return created;
    }

    public int executeOverdueTaskReminders() {
        LocalDateTime now = LocalDateTime.now(studyClock);
        int created = 0;
        for (TaskReminderSource source : reminderSourceMapper.selectOverdueTaskSources(now, SCAN_LIMIT)) {
            try {
                if (create(new CreateNotificationCommand(
                        source.getUserId(),
                        "TASK_OVERDUE",
                        "URGENT",
                        "学习任务已逾期",
                        "“%s”已超过截止时间，请尽快完成。".formatted(source.getTitle()),
                        "/study-plans",
                        taskDedupKey("TASK_OVERDUE", source)))) {
                    created++;
                }
            } catch (Exception e) {
                log.warn("任务逾期提醒生成失败 taskId={} userId={}",
                        source.getId(), source.getUserId(), e);
            }
        }
        return created;
    }

    public int executeExamMilestoneReminders() {
        LocalDate today = LocalDate.now(studyClock);
        int created = 0;
        for (ExamReminderSource source : reminderSourceMapper.selectExamSources(today)) {
            long days = ChronoUnit.DAYS.between(today, source.getExamDate());
            if (!EXAM_MILESTONES.contains((int) days)) {
                continue;
            }
            try {
                if (create(new CreateNotificationCommand(
                        source.getUserId(),
                        "EXAM_MILESTONE",
                        days == 1 ? "URGENT" : "WARNING",
                        "考试节点提醒",
                        "距离考试还有 %d 天，请按计划完成冲刺任务。".formatted(days),
                        "/study-plans",
                        "EXAM:%d:%s:%d".formatted(
                                source.getUserId(), source.getExamDate(), days)))) {
                    created++;
                }
            } catch (Exception e) {
                log.warn("考试节点提醒生成失败 userId={} examDate={}",
                        source.getUserId(), source.getExamDate(), e);
            }
        }
        return created;
    }

    private String taskDedupKey(String type, TaskReminderSource source) {
        long dueEpochSecond = source.getDueAt()
                .atZone(studyClock.getZone())
                .toEpochSecond();
        return "%s:%d:%d".formatted(type, source.getId(), dueEpochSecond);
    }

    private boolean create(CreateNotificationCommand command) {
        return notificationCreationService.createAndPush(command).created();
    }
}
