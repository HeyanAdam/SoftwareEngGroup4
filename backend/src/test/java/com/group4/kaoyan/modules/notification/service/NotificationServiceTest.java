package com.group4.kaoyan.modules.notification.service;

import com.group4.kaoyan.common.BizException;
import com.group4.kaoyan.common.ErrorCode;
import com.group4.kaoyan.common.R;
import com.group4.kaoyan.modules.notification.dto.CreateNotificationCommand;
import com.group4.kaoyan.modules.notification.dto.CreateNotificationResult;
import com.group4.kaoyan.modules.notification.dto.NotificationReadResult;
import com.group4.kaoyan.modules.notification.dto.NotificationVO;
import com.group4.kaoyan.modules.notification.entity.UserNotification;
import com.group4.kaoyan.modules.notification.mapper.NotificationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("通知 Service")
class NotificationServiceTest {

    private static final Long USER_ID = 10001L;
    private static final Long OTHER_USER = 20002L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 28, 10, 0);
    private static final Clock CLOCK =
            Clock.fixed(NOW.toInstant(ZoneOffset.ofHours(8)), ZoneOffset.ofHours(8));

    private NotificationMapper mapper;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        mapper = mock(NotificationMapper.class);
        service = new NotificationService(mapper, CLOCK);
    }

    @Test
    @DisplayName("首次 createIfAbsent 返回 created=true")
    void createsFirstNotification() {
        UserNotification created = notification(1L, USER_ID, "key-1");
        when(mapper.selectByDedupKey("key-1")).thenReturn(null, created);
        when(mapper.insert(any(UserNotification.class))).thenReturn(1);
        when(mapper.countUnread(USER_ID)).thenReturn(3L);

        CreateNotificationResult result = service.createIfAbsent(command("key-1"));

        assertTrue(result.created());
        assertEquals(1L, result.notification().id());
        assertEquals(3L, result.unreadCount());
        verify(mapper).insert(argThat(item ->
                USER_ID.equals(item.getUserId())
                        && Boolean.FALSE.equals(item.getRead())
                        && "key-1".equals(item.getDedupKey())));
    }

    @Test
    @DisplayName("相同 dedupKey 第二次返回 created=false 且不重复插入")
    void deduplicatesByKey() {
        UserNotification existing = notification(1L, USER_ID, "key-1");
        when(mapper.selectByDedupKey("key-1")).thenReturn(existing);
        when(mapper.countUnread(USER_ID)).thenReturn(1L);

        CreateNotificationResult result = service.createIfAbsent(command("key-1"));

        assertFalse(result.created());
        assertEquals(1L, result.notification().id());
        verify(mapper, never()).insert(any());
    }

    @Test
    @DisplayName("列表分页字段使用 R.PageData 且只查询当前用户")
    void listsCurrentUserNotifications() {
        UserNotification item = notification(8L, USER_ID, "key-8");
        when(mapper.countByStatus(USER_ID, false)).thenReturn(1L);
        when(mapper.selectPage(USER_ID, false, 0, 20)).thenReturn(List.of(item));

        R.PageData<NotificationVO> page = service.list(USER_ID, "ALL", 1, 20);

        assertEquals(1L, page.getTotal());
        assertEquals(8L, page.getList().get(0).id());
        assertEquals(1L, page.getPageNum());
        assertEquals(20L, page.getPageSize());
        verify(mapper).selectPage(USER_ID, false, 0, 20);
    }

    @Test
    @DisplayName("单条已读返回最新未读数")
    void markReadReturnsLatestUnreadCount() {
        UserNotification item = notification(3L, USER_ID, "key-3");
        when(mapper.selectById(3L)).thenReturn(item);
        when(mapper.markRead(eq(3L), eq(USER_ID), any())).thenReturn(1);
        when(mapper.countUnread(USER_ID)).thenReturn(2L);

        NotificationReadResult result = service.markRead(USER_ID, 3L);

        assertTrue(result.read());
        assertEquals(2L, result.unreadCount());
    }

    @Test
    @DisplayName("他人通知返回 10009")
    void rejectsOtherUsersNotification() {
        when(mapper.selectById(3L)).thenReturn(notification(3L, OTHER_USER, "key-3"));

        BizException error = assertThrows(BizException.class,
                () -> service.markRead(USER_ID, 3L));

        assertEquals(ErrorCode.NO_DATA_ACCESS, error.getCode());
        verify(mapper, never()).markRead(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("全部已读返回更新数量与 0 未读")
    void marksAllRead() {
        when(mapper.markAllRead(eq(USER_ID), any())).thenReturn(3);
        when(mapper.countUnread(USER_ID)).thenReturn(0L);

        var result = service.markAllRead(USER_ID);

        assertEquals(3, result.updated());
        assertEquals(0L, result.unreadCount());
    }

    @Test
    @DisplayName("非法 actionPath 被拒绝")
    void rejectsUnsafeActionPath() {
        CreateNotificationCommand unsafe = new CreateNotificationCommand(
                USER_ID, "TASK_DUE", "WARNING", "标题", "正文",
                "//evil.example/path", "unsafe");

        BizException error = assertThrows(BizException.class,
                () -> service.createIfAbsent(unsafe));

        assertEquals(ErrorCode.PARAM_INVALID, error.getCode());
        verifyNoInteractions(mapper);
    }

    private CreateNotificationCommand command(String key) {
        return new CreateNotificationCommand(
                USER_ID, "TASK_DUE", "WARNING", "任务即将截止",
                "任务将在 30 分钟后截止。", "/study-plans", key);
    }

    private UserNotification notification(Long id, Long userId, String key) {
        UserNotification item = new UserNotification();
        item.setId(id);
        item.setUserId(userId);
        item.setType("TASK_DUE");
        item.setSeverity("WARNING");
        item.setTitle("任务即将截止");
        item.setContent("任务将在 30 分钟后截止。");
        item.setActionPath("/study-plans");
        item.setRead(false);
        item.setDedupKey(key);
        item.setCreateTime(NOW);
        item.setUpdateTime(NOW);
        return item;
    }
}
