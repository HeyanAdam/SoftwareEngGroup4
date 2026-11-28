package com.group4.kaoyan.modules.notification.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.group4.kaoyan.common.R;
import com.group4.kaoyan.common.security.PermissionConstants;
import com.group4.kaoyan.common.security.SecurityUtils;
import com.group4.kaoyan.modules.notification.dto.NotificationReadAllResult;
import com.group4.kaoyan.modules.notification.dto.NotificationReadResult;
import com.group4.kaoyan.modules.notification.dto.NotificationVO;
import com.group4.kaoyan.modules.notification.dto.UnreadCountVO;
import com.group4.kaoyan.modules.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @SaCheckPermission(PermissionConstants.NOTIFICATION_VIEW)
    @GetMapping("/notifications/unread-count")
    public R<UnreadCountVO> unreadCount() {
        return R.ok(new UnreadCountVO(
                notificationService.unreadCount(SecurityUtils.currentUserId())));
    }

    @SaCheckPermission(PermissionConstants.NOTIFICATION_VIEW)
    @GetMapping("/notifications")
    public R<R.PageData<NotificationVO>> list(
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(name = "page_num", defaultValue = "1") long pageNum,
            @RequestParam(name = "page_size", defaultValue = "20") long pageSize) {
        return R.ok(notificationService.list(
                SecurityUtils.currentUserId(), status, pageNum, pageSize));
    }

    @SaCheckPermission(PermissionConstants.NOTIFICATION_UPDATE)
    @PutMapping("/notifications/{id}/read")
    public R<NotificationReadResult> markRead(@PathVariable Long id) {
        return R.ok(notificationService.markRead(SecurityUtils.currentUserId(), id));
    }

    @SaCheckPermission(PermissionConstants.NOTIFICATION_UPDATE)
    @PutMapping("/notifications/read-all")
    public R<NotificationReadAllResult> markAllRead() {
        return R.ok(notificationService.markAllRead(SecurityUtils.currentUserId()));
    }
}
