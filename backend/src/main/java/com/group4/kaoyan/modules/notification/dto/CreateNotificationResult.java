package com.group4.kaoyan.modules.notification.dto;

public record CreateNotificationResult(
        boolean created,
        NotificationVO notification,
        long unreadCount) {
}
