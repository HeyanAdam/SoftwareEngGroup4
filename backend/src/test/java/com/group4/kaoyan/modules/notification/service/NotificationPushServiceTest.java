package com.group4.kaoyan.modules.notification.service;

import com.group4.kaoyan.modules.notification.dto.NotificationCreatedPayload;
import com.group4.kaoyan.modules.notification.dto.NotificationVO;
import com.group4.kaoyan.platform.realtime.delivery.RealtimeDeliveryPort;
import com.group4.kaoyan.platform.realtime.delivery.RealtimeOutboundMessage;
import com.group4.kaoyan.platform.realtime.protocol.RealtimeProtocol;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("通知 WebSocket 推送")
class NotificationPushServiceTest {

    private static final Long USER_ID = 10001L;

    @Test
    @DisplayName("只推送 notification.created，payload 复用 REST VO")
    void pushesNotificationCreatedPayload() {
        RealtimeDeliveryPort delivery = mock(RealtimeDeliveryPort.class);
        when(delivery.pushToUser(eq(USER_ID), any())).thenReturn(1);
        Clock clock = Clock.fixed(
                OffsetDateTime.of(2026, 11, 28, 10, 0, 0, 0, ZoneOffset.ofHours(8)).toInstant(),
                ZoneOffset.ofHours(8));
        NotificationPushService service = new NotificationPushService(delivery, clock);
        NotificationVO vo = notification();

        assertTrue(service.pushCreated(USER_ID, vo, 3L));

        ArgumentCaptor<RealtimeOutboundMessage> captor =
                ArgumentCaptor.forClass(RealtimeOutboundMessage.class);
        verify(delivery).pushToUser(eq(USER_ID), captor.capture());
        assertEquals(RealtimeProtocol.TYPE_NOTIFICATION_CREATED, captor.getValue().type());
        NotificationCreatedPayload payload =
                assertInstanceOf(NotificationCreatedPayload.class, captor.getValue().data());
        assertSame(vo, payload.notification());
        assertEquals(3L, payload.unreadCount());
        assertEquals(32, payload.eventId().length());
        assertEquals("2026-11-28T10:00:00+08:00", captor.getValue().serverTime());
    }

    @Test
    @DisplayName("推送异常不回滚且不向调用方抛出")
    void swallowsPushFailure() {
        RealtimeDeliveryPort delivery = mock(RealtimeDeliveryPort.class);
        when(delivery.pushToUser(eq(USER_ID), any()))
                .thenThrow(new IllegalStateException("connection lost"));
        NotificationPushService service = new NotificationPushService(delivery, Clock.systemUTC());

        assertFalse(service.pushCreated(USER_ID, notification(), 1L));
    }

    private NotificationVO notification() {
        OffsetDateTime now = OffsetDateTime.of(
                2026, 11, 28, 10, 0, 0, 0, ZoneOffset.ofHours(8));
        return new NotificationVO(
                1L, "TASK_DUE", "WARNING", "任务即将截止",
                "任务将在 30 分钟后截止。", "/study-plans", false, now, null);
    }
}
