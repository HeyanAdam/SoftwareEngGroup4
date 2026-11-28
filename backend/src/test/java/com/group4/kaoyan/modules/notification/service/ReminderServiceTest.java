package com.group4.kaoyan.modules.notification.service;

import com.group4.kaoyan.modules.notification.dto.CreateNotificationCommand;
import com.group4.kaoyan.modules.notification.dto.CreateNotificationResult;
import com.group4.kaoyan.modules.notification.dto.NotificationVO;
import com.group4.kaoyan.modules.notification.dto.TaskReminderSource;
import com.group4.kaoyan.modules.notification.mapper.ReminderSourceMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@DisplayName("定时提醒幂等键")
class ReminderServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 28, 10, 0);
    private static final Clock CLOCK =
            Clock.fixed(NOW.toInstant(ZoneOffset.ofHours(8)), ZoneOffset.ofHours(8));

    @Test
    @DisplayName("同一任务连续扫描两次使用相同 dedupKey")
    void repeatedScanUsesSameDedupKey() {
        ReminderSourceMapper sources = mock(ReminderSourceMapper.class);
        NotificationCreationService creation = mock(NotificationCreationService.class);
        TaskReminderSource task = task(NOW.plusMinutes(30));
        when(sources.selectDueTaskSources(any(), anyInt())).thenReturn(List.of(task));
        when(creation.createAndPush(any())).thenReturn(result(true), result(false));
        ReminderService service = new ReminderService(sources, creation, CLOCK);

        assertEquals(1, service.executeDueTaskReminders());
        assertEquals(0, service.executeDueTaskReminders());

        ArgumentCaptor<CreateNotificationCommand> captor =
                ArgumentCaptor.forClass(CreateNotificationCommand.class);
        verify(creation, times(2)).createAndPush(captor.capture());
        assertEquals(captor.getAllValues().get(0).dedupKey(),
                captor.getAllValues().get(1).dedupKey());
    }

    @Test
    @DisplayName("修改截止时间后 dedupKey 改变")
    void newDueTimeProducesNewDedupKey() {
        ReminderSourceMapper sources = mock(ReminderSourceMapper.class);
        NotificationCreationService creation = mock(NotificationCreationService.class);
        when(sources.selectDueTaskSources(any(), anyInt()))
                .thenReturn(List.of(task(NOW.plusMinutes(30))))
                .thenReturn(List.of(task(NOW.plusMinutes(20))));
        when(creation.createAndPush(any())).thenReturn(result(true));
        ReminderService service = new ReminderService(sources, creation, CLOCK);

        service.executeDueTaskReminders();
        service.executeDueTaskReminders();

        ArgumentCaptor<CreateNotificationCommand> captor =
                ArgumentCaptor.forClass(CreateNotificationCommand.class);
        verify(creation, times(2)).createAndPush(captor.capture());
        assertNotEquals(captor.getAllValues().get(0).dedupKey(),
                captor.getAllValues().get(1).dedupKey());
    }

    private TaskReminderSource task(LocalDateTime dueAt) {
        TaskReminderSource task = new TaskReminderSource();
        task.setId(10L);
        task.setUserId(1L);
        task.setTitle("复习概率论");
        task.setDueAt(dueAt);
        return task;
    }

    private CreateNotificationResult result(boolean created) {
        NotificationVO vo = new NotificationVO(
                1L, "TASK_DUE", "WARNING", "任务即将截止", "正文", "/study-plans", false,
                OffsetDateTime.now(ZoneOffset.ofHours(8)), null);
        return new CreateNotificationResult(created, vo, 1L);
    }
}
