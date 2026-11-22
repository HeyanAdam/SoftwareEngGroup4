package com.group4.kaoyan.platform.realtime.delivery;

import com.group4.kaoyan.platform.realtime.protocol.RealtimeMessageWriter;
import com.group4.kaoyan.platform.realtime.session.RealtimeConnection;
import com.group4.kaoyan.platform.realtime.session.RealtimeConnectionRegistry;
import com.group4.kaoyan.platform.realtime.session.RealtimeSessionSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * {@link RealtimeDeliveryPort} 的单机实现：查注册表 → 序列化 → 逐条发送。
 *
 * <p>发送失败（连接已断/写了一半）就立刻把这条连接从注册表摘掉，
 * 不指望 {@code afterConnectionClosed} 一定会来——那是最容易留下"幽灵 Session"的地方。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RealtimeDeliveryService implements RealtimeDeliveryPort {

    private final RealtimeConnectionRegistry registry;
    private final RealtimeMessageWriter writer;

    @Override
    public int pushToUser(Long userId, RealtimeOutboundMessage message) {
        if (userId == null || message == null) {
            return 0;
        }
        String payload = writer.toJson(message.type(), message.requestId(), message.data());
        int delivered = 0;
        for (RealtimeConnection connection : registry.connectionsOf(userId)) {
            if (RealtimeSessionSender.send(connection.session(), payload)) {
                delivered++;
            } else {
                registry.unregister(connection.userId(), connection.sessionId());
            }
        }
        return delivered;
    }

    @Override
    public boolean pushToConnection(Long userId, String connectionId, RealtimeOutboundMessage message) {
        if (userId == null || connectionId == null || message == null) {
            return false;
        }
        RealtimeConnection connection = registry.find(userId, connectionId);
        if (connection == null) {
            return false;
        }
        String payload = writer.toJson(message.type(), message.requestId(), message.data());
        if (RealtimeSessionSender.send(connection.session(), payload)) {
            return true;
        }
        registry.unregister(connection.userId(), connection.sessionId());
        return false;
    }
}
