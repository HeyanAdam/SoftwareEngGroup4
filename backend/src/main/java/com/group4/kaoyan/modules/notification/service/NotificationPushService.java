package com.group4.kaoyan.modules.notification.service;

import com.group4.kaoyan.modules.notification.dto.NotificationCreatedPayload;
import com.group4.kaoyan.modules.notification.dto.NotificationVO;
import com.group4.kaoyan.platform.realtime.delivery.RealtimeDeliveryPort;
import com.group4.kaoyan.platform.realtime.delivery.RealtimeOutboundMessage;
import com.group4.kaoyan.platform.realtime.protocol.RealtimeProtocol;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 通知实时推送。在线连接采用最多一次投递，数据库始终是真实来源。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationPushService {

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private final RealtimeDeliveryPort deliveryPort;
    private final Clock studyClock;

    public boolean pushCreated(Long userId, NotificationVO notification, long unreadCount) {
        String eventId = UUID.randomUUID().toString().replace("-", "");
        String requestId = "push-notice-" + eventId.substring(0, 8);
        String serverTime = OffsetDateTime.now(studyClock).format(TIMESTAMP);
        NotificationCreatedPayload payload =
                new NotificationCreatedPayload(eventId, notification, unreadCount);
        RealtimeOutboundMessage message = new RealtimeOutboundMessage(
                RealtimeProtocol.TYPE_NOTIFICATION_CREATED,
                requestId,
                null,
                null,
                serverTime,
                payload);
        try {
            int delivered = deliveryPort.pushToUser(userId, message);
            if (delivered == 0) {
                log.debug("用户离线，通知仅落库 userId={} notificationId={}",
                        userId, notification.id());
            }
            return delivered > 0;
        } catch (RuntimeException e) {
            log.warn("通知实时推送失败，数据库记录保留 userId={} notificationId={}",
                    userId, notification.id(), e);
            return false;
        }
    }
}
