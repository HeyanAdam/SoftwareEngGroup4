package com.group4.kaoyan.modules.notification.dto;

/**
 * 后端内部创建通知的统一命令。
 */
public record CreateNotificationCommand(
        Long userId,
        String type,
        String severity,
        String title,
        String content,
        String actionPath,
        String dedupKey) {
}
