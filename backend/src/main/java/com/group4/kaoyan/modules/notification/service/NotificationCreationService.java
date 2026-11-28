package com.group4.kaoyan.modules.notification.service;

import com.group4.kaoyan.modules.notification.dto.CreateNotificationCommand;
import com.group4.kaoyan.modules.notification.dto.CreateNotificationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 先通过独立事务服务落库，事务提交返回后再尝试实时推送。
 */
@Service
@RequiredArgsConstructor
public class NotificationCreationService {

    private final NotificationService notificationService;
    private final NotificationPushService notificationPushService;

    public CreateNotificationResult createAndPush(CreateNotificationCommand command) {
        CreateNotificationResult result = notificationService.createIfAbsent(command);
        if (result.created()) {
            notificationPushService.pushCreated(
                    command.userId(), result.notification(), result.unreadCount());
        }
        return result;
    }
}
