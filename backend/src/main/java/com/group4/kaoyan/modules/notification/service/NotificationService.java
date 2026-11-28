package com.group4.kaoyan.modules.notification.service;

import com.group4.kaoyan.common.BizException;
import com.group4.kaoyan.common.ErrorCode;
import com.group4.kaoyan.common.R;
import com.group4.kaoyan.common.StudyTimeUtils;
import com.group4.kaoyan.common.mybatis.PageQuery;
import com.group4.kaoyan.modules.notification.dto.CreateNotificationCommand;
import com.group4.kaoyan.modules.notification.dto.CreateNotificationResult;
import com.group4.kaoyan.modules.notification.dto.NotificationReadAllResult;
import com.group4.kaoyan.modules.notification.dto.NotificationReadResult;
import com.group4.kaoyan.modules.notification.dto.NotificationVO;
import com.group4.kaoyan.modules.notification.entity.UserNotification;
import com.group4.kaoyan.modules.notification.mapper.NotificationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 通知查询、已读状态和幂等创建。
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final Set<String> TYPES = Set.of(
            "TASK_DUE", "TASK_OVERDUE", "DAILY_PLAN", "WEEKLY_REPORT", "EXAM_MILESTONE");
    private static final Set<String> SEVERITIES = Set.of("INFO", "WARNING", "URGENT");

    private final NotificationMapper notificationMapper;
    private final Clock studyClock;

    public long unreadCount(Long userId) {
        return notificationMapper.countUnread(userId);
    }

    public R.PageData<NotificationVO> list(Long userId, String status, long pageNum, long pageSize) {
        boolean unreadOnly = parseUnreadOnly(status);
        PageQuery page = PageQuery.of(pageNum, pageSize);
        long total = notificationMapper.countByStatus(userId, unreadOnly);
        List<NotificationVO> list = notificationMapper.selectPage(
                        userId, unreadOnly, page.getOffset(), page.getLimit())
                .stream()
                .map(this::toVO)
                .toList();
        return R.PageData.of(total, list, page.getPageNum(), page.getPageSize());
    }

    /**
     * 相同 dedupKey 只落一条通知。调用方在事务返回后再推送实时消息。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CreateNotificationResult createIfAbsent(CreateNotificationCommand command) {
        validateCommand(command);
        UserNotification existing = notificationMapper.selectByDedupKey(command.dedupKey());
        if (existing != null) {
            assertSameOwner(existing, command.userId());
            return new CreateNotificationResult(
                    false, toVO(existing), notificationMapper.countUnread(command.userId()));
        }

        LocalDateTime now = LocalDateTime.now(studyClock);
        UserNotification notification = new UserNotification();
        notification.setUserId(command.userId());
        notification.setType(command.type());
        notification.setSeverity(command.severity());
        notification.setTitle(command.title().trim());
        notification.setContent(command.content());
        notification.setActionPath(normalizeActionPath(command.actionPath()));
        notification.setRead(false);
        notification.setDedupKey(command.dedupKey());
        notification.setCreateTime(now);
        notification.setUpdateTime(now);

        try {
            notificationMapper.insert(notification);
        } catch (DuplicateKeyException duplicate) {
            UserNotification concurrent = notificationMapper.selectByDedupKey(command.dedupKey());
            if (concurrent == null) {
                throw duplicate;
            }
            assertSameOwner(concurrent, command.userId());
            return new CreateNotificationResult(false, toVO(concurrent),
                    notificationMapper.countUnread(command.userId()));
        }

        UserNotification created = notificationMapper.selectByDedupKey(command.dedupKey());
        if (created == null) {
            throw new IllegalStateException("通知已写入但无法重新读取");
        }
        return new CreateNotificationResult(true, toVO(created),
                notificationMapper.countUnread(command.userId()));
    }

    @Transactional
    public NotificationReadResult markRead(Long userId, Long id) {
        UserNotification notification = notificationMapper.selectById(id);
        if (notification == null) {
            throw new BizException(404, "通知不存在");
        }
        if (!userId.equals(notification.getUserId())) {
            throw new BizException(ErrorCode.NO_DATA_ACCESS, "无权访问该通知");
        }

        if (!Boolean.TRUE.equals(notification.getRead())) {
            notificationMapper.markRead(id, userId, LocalDateTime.now(studyClock));
        }
        return new NotificationReadResult(id, true, notificationMapper.countUnread(userId));
    }

    @Transactional
    public NotificationReadAllResult markAllRead(Long userId) {
        int updated = notificationMapper.markAllRead(userId, LocalDateTime.now(studyClock));
        return new NotificationReadAllResult(updated, notificationMapper.countUnread(userId));
    }

    public NotificationVO toVO(UserNotification notification) {
        return new NotificationVO(
                notification.getId(),
                notification.getType(),
                notification.getSeverity(),
                notification.getTitle(),
                notification.getContent(),
                notification.getActionPath(),
                Boolean.TRUE.equals(notification.getRead()),
                StudyTimeUtils.toOffset(notification.getCreateTime()),
                StudyTimeUtils.toOffset(notification.getReadAt()));
    }

    private boolean parseUnreadOnly(String status) {
        String normalized = status == null || status.isBlank() ? "ALL" : status.trim().toUpperCase();
        if ("UNREAD".equals(normalized)) {
            return true;
        }
        if ("ALL".equals(normalized)) {
            return false;
        }
        throw new BizException(ErrorCode.PARAM_INVALID, "status 必须是 UNREAD 或 ALL");
    }

    private void validateCommand(CreateNotificationCommand command) {
        if (command == null || command.userId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "通知用户不能为空");
        }
        requireText(command.type(), 32, "通知类型不能为空");
        if (!TYPES.contains(command.type())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "通知类型不合法");
        }
        requireText(command.severity(), 20, "通知级别不能为空");
        if (!SEVERITIES.contains(command.severity())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "通知级别不合法");
        }
        requireText(command.title(), 200, "通知标题不能为空");
        requireText(command.content(), 2000, "通知正文不能为空");
        requireText(command.dedupKey(), 191, "通知去重键不能为空");
        normalizeActionPath(command.actionPath());
    }

    private void requireText(String value, int maxLength, String emptyMessage) {
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, emptyMessage);
        }
        if (value.length() > maxLength) {
            throw new BizException(ErrorCode.PARAM_INVALID, "通知字段超过最大长度");
        }
    }

    private String normalizeActionPath(String actionPath) {
        if (actionPath == null || actionPath.isBlank()) {
            return null;
        }
        if (actionPath.length() > 500
                || !actionPath.startsWith("/")
                || actionPath.startsWith("//")
                || actionPath.contains("\r")
                || actionPath.contains("\n")) {
            throw new BizException(ErrorCode.PARAM_INVALID, "actionPath 必须是安全的站内路径");
        }
        return actionPath;
    }

    private void assertSameOwner(UserNotification notification, Long userId) {
        if (!userId.equals(notification.getUserId())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "通知去重键已被其他用户占用");
        }
    }
}
