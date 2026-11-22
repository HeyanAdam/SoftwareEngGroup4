package com.group4.kaoyan.platform.realtime.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 连接注册表单测（B-1 验收清单第 3 条：多标签页同时在线、关闭后无残留）。
 *
 * <p>纯内存 + Mockito 假 Session，不启动容器。</p>
 */
class RealtimeConnectionRegistryTest {

    @Test
    @DisplayName("同一用户两个标签页：都登记成功，互不覆盖")
    void sameUserMultipleTabsShouldCoexist() {
        RealtimeConnectionRegistry registry = new RealtimeConnectionRegistry(3);
        WebSocketSession tab1 = session("tab-1");
        WebSocketSession tab2 = session("tab-2");

        assertTrue(registry.register(7L, tab1, List.of("chat")));
        assertTrue(registry.register(7L, tab2, List.of("chat")));

        assertEquals(2, registry.connectionCount(7L));
        assertEquals(2, registry.totalConnections());
        assertEquals(2, registry.connectionsOf(7L).size());
        assertNotNull(registry.find(7L, "tab-1"));
        assertNotNull(registry.find(7L, "tab-2"));
    }

    @Test
    @DisplayName("超过单用户上限：拒绝，且不会留下空桶")
    void exceedingLimitShouldBeRejected() {
        RealtimeConnectionRegistry registry = new RealtimeConnectionRegistry(2);

        assertTrue(registry.register(7L, session("a"), List.of("chat")));
        assertTrue(registry.register(7L, session("b"), List.of("chat")));
        assertFalse(registry.register(7L, session("c"), List.of("chat")));

        assertEquals(2, registry.connectionCount(7L));
        assertEquals(2, registry.totalConnections());
        assertNull(registry.findBySessionId("c"));
    }

    @Test
    @DisplayName("关闭一条后剩余另一条继续在线；最后一条关掉后用户桶被清空")
    void unregisterShouldNotLeak() {
        RealtimeConnectionRegistry registry = new RealtimeConnectionRegistry(3);
        registry.register(7L, session("tab-1"), List.of("chat"));
        registry.register(7L, session("tab-2"), List.of("chat"));

        assertTrue(registry.unregister(7L, "tab-1"));
        assertEquals(1, registry.connectionCount(7L));
        assertNotNull(registry.find(7L, "tab-2"));

        assertTrue(registry.unregister(7L, "tab-2"));
        assertEquals(0, registry.connectionCount(7L));
        assertEquals(0, registry.totalConnections());
        assertTrue(registry.connectionsOf(7L).isEmpty());
    }

    @Test
    @DisplayName("兜底清理：已关闭的 Session 不留在注册表里")
    void pruneClosedShouldRemoveDeadSessions() {
        RealtimeConnectionRegistry registry = new RealtimeConnectionRegistry(3);
        WebSocketSession alive = session("alive");
        WebSocketSession dead = session("dead");
        registry.register(7L, alive, List.of("chat"));
        registry.register(7L, dead, List.of("chat"));

        when(dead.isOpen()).thenReturn(false);

        assertEquals(1, registry.pruneClosed());
        assertEquals(1, registry.connectionCount(7L));
        assertNull(registry.findBySessionId("dead"));
    }

    @Test
    @DisplayName("心跳刷新最后活动时间")
    void touchShouldUpdateLastActivity() {
        RealtimeConnectionRegistry registry = new RealtimeConnectionRegistry(3);
        WebSocketSession session = session("s-1");
        registry.register(7L, session, List.of("chat"));

        RealtimeConnection connection = registry.findBySessionId("s-1");
        long before = connection.lastActivity();
        registry.touch("s-1", before + 30_000);

        assertEquals(before + 30_000, connection.lastActivity());
    }

    private WebSocketSession session(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        return session;
    }
}
