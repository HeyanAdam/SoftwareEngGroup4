package com.group4.kaoyan.platform.realtime.delivery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group4.kaoyan.platform.realtime.protocol.RealtimeMessageWriter;
import com.group4.kaoyan.platform.realtime.protocol.RealtimeProtocol;
import com.group4.kaoyan.platform.realtime.session.RealtimeConnectionRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 推送端口单测（B-1 验收清单第 8 条：连接层可以被其他业务复用）。
 *
 * <p>重点：多端广播命中每一条连接；写失败的连接会被立即摘除，不留幽灵 Session。</p>
 */
class RealtimeDeliveryServiceTest {

    @Test
    @DisplayName("推给一个用户的两条连接：两条都收到 delta")
    void pushToUserShouldFanOutToAllTabs() throws Exception {
        RealtimeConnectionRegistry registry = new RealtimeConnectionRegistry(3);
        WebSocketSession tab1 = openSession("tab-1");
        WebSocketSession tab2 = openSession("tab-2");
        registry.register(7L, tab1, List.of("chat"));
        registry.register(7L, tab2, List.of("chat"));

        RealtimeDeliveryPort delivery = deliveryService(registry);
        int delivered = delivery.pushToUser(7L, RealtimeOutboundMessage.of(
                RealtimeProtocol.TYPE_DELTA, "r-1", Map.of("content", "选 B")));

        assertEquals(2, delivered);
        verify(tab1).sendMessage(any(TextMessage.class));
        verify(tab2).sendMessage(any(TextMessage.class));
    }

    @Test
    @DisplayName("其中一条已断开：只算送达一条，并把断开的连接摘掉")
    void failedConnectionShouldBeRemoved() throws Exception {
        RealtimeConnectionRegistry registry = new RealtimeConnectionRegistry(3);
        WebSocketSession alive = openSession("alive");
        WebSocketSession dead = openSession("dead");
        registry.register(7L, alive, List.of("chat"));
        registry.register(7L, dead, List.of("chat"));
        when(dead.isOpen()).thenReturn(false);

        RealtimeDeliveryPort delivery = deliveryService(registry);
        int delivered = delivery.pushToUser(7L, RealtimeOutboundMessage.of(
                RealtimeProtocol.TYPE_DONE, "r-1", Map.of()));

        assertEquals(1, delivered);
        assertEquals(1, registry.totalConnections());
    }

    @Test
    @DisplayName("定向推给某条连接：只发那一根")
    void pushToConnectionShouldTargetOneTab() throws Exception {
        RealtimeConnectionRegistry registry = new RealtimeConnectionRegistry(3);
        WebSocketSession tab1 = openSession("tab-1");
        WebSocketSession tab2 = openSession("tab-2");
        registry.register(7L, tab1, List.of("chat"));
        registry.register(7L, tab2, List.of("chat"));

        RealtimeDeliveryPort delivery = deliveryService(registry);
        boolean sent = delivery.pushToConnection(7L, "tab-1", RealtimeOutboundMessage.of(
                RealtimeProtocol.TYPE_ACK, "r-1", Map.of()));

        assertEquals(true, sent);
        verify(tab1).sendMessage(any(TextMessage.class));
        verify(tab2, org.mockito.Mockito.never()).sendMessage(any(TextMessage.class));

        assertFalse(delivery.pushToConnection(7L, "not-exist", RealtimeOutboundMessage.of(
                RealtimeProtocol.TYPE_ACK, "r-2", Map.of())));
    }

    private RealtimeDeliveryService deliveryService(RealtimeConnectionRegistry registry) {
        return new RealtimeDeliveryService(registry, new RealtimeMessageWriter(new ObjectMapper()));
    }

    private WebSocketSession openSession(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        return session;
    }
}
