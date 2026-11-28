package com.group4.kaoyan.modules.notification.dto;

import java.time.OffsetDateTime;

/**
 * 通知 REST 与 WebSocket 共用的返回对象。
 */
public record NotificationVO(
        Long id,
        String type,
        String severity,
        String title,
        String content,
        String actionPath,
        boolean read,
        OffsetDateTime createdAt,
        OffsetDateTime readAt) {
}
