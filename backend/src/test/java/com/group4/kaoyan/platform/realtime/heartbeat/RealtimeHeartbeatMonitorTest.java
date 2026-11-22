package com.group4.kaoyan.platform.realtime.heartbeat;

import com.group4.kaoyan.platform.realtime.config.RealtimeProperties;
import com.group4.kaoyan.platform.realtime.session.RealtimeConnectionRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 心跳与空闲清理单测（B-1 验收清单第 4 条）。
 */
class RealtimeHeartbeatMonitorTest {

    @Test
    @DisplayName("超过 90 秒没消息：关闭连接并从注册表摘掉")
    void idleConnectionShouldBeClosed() throws Exception {
        RealtimeConnectionRegistry registry = new RealtimeConnectionRegistry(3);
        WebSocketSession session = openSession("s-1");
        RealtimeProperties properties = new RealtimeProperties();
        RealtimeHeartbeatMonitor monitor = new RealtimeHeartbeatMonitor(registry, properties);
        registry.register(7L, session, List.of("chat"));

        long now = System.currentTimeMillis();
        int closed = monitor.sweep(now + properties.getIdleTimeout().toMillis() + 5_000);

        assertEquals(1, closed);
        verify(session).close(any(CloseStatus.class));
        assertEquals(0, registry.totalConnections());
    }

    @Test
    @DisplayName("刚 ping 过的连接不会被误杀")
    void activeConnectionShouldStay() throws Exception {
        RealtimeConnectionRegistry registry = new RealtimeConnectionRegistry(3);
        WebSocketSession session = openSession("s-1");
        RealtimeProperties properties = new RealtimeProperties();
        RealtimeHeartbeatMonitor monitor = new RealtimeHeartbeatMonitor(registry, properties);
        registry.register(7L, session, List.of("chat"));

        assertEquals(0, monitor.sweep(System.currentTimeMillis()));
        verify(session, never()).close(any(CloseStatus.class));
        assertEquals(1, registry.totalConnections());
    }

    @Test
    @DisplayName("已经断开的连接：顺手清掉，但不算'因空闲关闭'")
    void closedConnectionShouldBePruned() {
        RealtimeConnectionRegistry registry = new RealtimeConnectionRegistry(3);
        WebSocketSession session = openSession("s-dead");
        RealtimeProperties properties = new RealtimeProperties();
        RealtimeHeartbeatMonitor monitor = new RealtimeHeartbeatMonitor(registry, properties);
        registry.register(7L, session, List.of("chat"));

        when(session.isOpen()).thenReturn(false);

        assertEquals(0, monitor.sweep(System.currentTimeMillis()));
        assertEquals(0, registry.totalConnections());
    }

    private WebSocketSession openSession(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        return session;
    }
}
