package com.group4.kaoyan.modules.notification.service;

import com.group4.kaoyan.modules.notification.dto.CreateNotificationCommand;
import com.group4.kaoyan.modules.notification.dto.CreateNotificationResult;
import com.group4.kaoyan.modules.notification.dto.NotificationVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@DisplayName("通知落库后推送边界")
class NotificationCreationServiceTest {

    @Test
    @DisplayName("created=true 才推送")
    void pushesOnlyCreatedNotification() {
        NotificationService persistence = mock(NotificationService.class);
        NotificationPushService push = mock(NotificationPushService.class);
        NotificationCreationService service = new NotificationCreationService(persistence, push);
        CreateNotificationCommand command = new CreateNotificationCommand(
                1L, "TASK_DUE", "WARNING", "标题", "正文", "/study-plans", "key");
        CreateNotificationResult result = result(true);
        when(persistence.createIfAbsent(command)).thenReturn(result);

        service.createAndPush(command);

        verify(push).pushCreated(1L, result.notification(), 2L);
    }

    @Test
    @DisplayName("created=false 不重复推送")
    void skipsDuplicateNotificationPush() {
        NotificationService persistence = mock(NotificationService.class);
        NotificationPushService push = mock(NotificationPushService.class);
        NotificationCreationService service = new NotificationCreationService(persistence, push);
        CreateNotificationCommand command = new CreateNotificationCommand(
                1L, "TASK_DUE", "WARNING", "标题", "正文", "/study-plans", "key");
        when(persistence.createIfAbsent(command)).thenReturn(result(false));

        service.createAndPush(command);

        verifyNoInteractions(push);
    }

    private CreateNotificationResult result(boolean created) {
        NotificationVO vo = new NotificationVO(
                1L, "TASK_DUE", "WARNING", "标题", "正文", "/study-plans", false,
                OffsetDateTime.now(ZoneOffset.ofHours(8)), null);
        return new CreateNotificationResult(created, vo, 2L);
    }
}
