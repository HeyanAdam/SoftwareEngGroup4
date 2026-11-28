package com.group4.kaoyan.modules.notification.dto;

public record NotificationCreatedPayload(
        String eventId,
        NotificationVO notification,
        long unreadCount) {
}
