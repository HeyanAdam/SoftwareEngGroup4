package com.group4.kaoyan.modules.notification.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户站内通知。
 */
@Data
public class UserNotification {

    private Long id;
    private Long userId;
    private String type;
    private String severity;
    private String title;
    private String content;
    private String actionPath;
    private Boolean read;
    private LocalDateTime readAt;
    private String dedupKey;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
