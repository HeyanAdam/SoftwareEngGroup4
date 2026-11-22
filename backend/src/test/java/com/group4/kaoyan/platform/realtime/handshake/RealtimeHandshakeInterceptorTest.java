package com.group4.kaoyan.platform.realtime.handshake;

import com.group4.kaoyan.common.BizException;
import com.group4.kaoyan.common.security.AccountStatusGuard;
import com.group4.kaoyan.common.security.RbacSnapshotCache;
import com.group4.kaoyan.platform.realtime.config.RealtimeProperties;
import com.group4.kaoyan.platform.realtime.session.RealtimeSessionAttributes;
import com.group4.kaoyan.platform.realtime.ticket.RealtimeTicket;
import com.group4.kaoyan.platform.realtime.ticket.RealtimeTicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * 握手鉴权单测（B-1 验收清单第 2 条）。
 *
 * <p>覆盖：有效票据放行、无效/缺失票据 401、越权 403、Origin 不符 403、账号被禁用 403。
 * 用 Servlet 的 Mock 请求/响应模拟容器握手（{@link org.springframework.web.socket.server.HandshakeInterceptor}
 * 接收的是 Servlet 版 {@link ServerHttpRequest}），不启动 Spring。</p>
 */
@ExtendWith(MockitoExtension.class)
class RealtimeHandshakeInterceptorTest {

    @Mock
    private RealtimeTicketService ticketService;

    @Mock
    private AccountStatusGuard accountStatusGuard;

    @Mock
    private RbacSnapshotCache rbacSnapshotCache;

    private RealtimeProperties properties;
    private RealtimeHandshakeInterceptor interceptor;
    private MockHttpServletResponse servletResponse;

    @BeforeEach
    void setUp() {
        properties = new RealtimeProperties();
        interceptor = new RealtimeHandshakeInterceptor(
                ticketService, accountStatusGuard, rbacSnapshotCache, properties);
        servletResponse = new MockHttpServletResponse();
    }

    @Test
    @DisplayName("有效票据 + 合法 Origin + 有 chat:send：放行并写入 userId")
    void validTicketShouldPass() {
        when(ticketService.consume("t-1")).thenReturn(Optional.of(ticket(7L)));
        when(rbacSnapshotCache.get(7L)).thenReturn(snapshotWithChatPermission());

        Map<String, Object> attributes = new HashMap<>();
        boolean passed = interceptor.beforeHandshake(
                request("/ws/chat", "ticket=t-1", "http://localhost:5173"), response(), null, attributes);

        assertTrue(passed);
        assertEquals(7L, attributes.get(RealtimeSessionAttributes.USER_ID));
        assertEquals(List.of("chat"), attributes.get(RealtimeSessionAttributes.CHANNELS));
    }

    @Test
    @DisplayName("没带 ticket：401，且 attributes 里不留下任何身份")
    void missingTicketShouldBeUnauthorized() {
        when(ticketService.consume(null)).thenReturn(Optional.empty());

        Map<String, Object> attributes = new HashMap<>();
        ServerHttpResponse response = response();
        assertFalse(interceptor.beforeHandshake(
                request("/ws/chat", null, "http://localhost:5173"), response, null, attributes));

        assertEquals(HttpStatus.UNAUTHORIZED.value(), servletResponse.getStatus());
        // 响应头在 flush 前只存在于 ServerHttpResponse 自己的 headers 里
        assertEquals("UNAUTHORIZED", response.getHeaders().getFirst("X-Realtime-Error"));
        assertNull(attributes.get(RealtimeSessionAttributes.USER_ID));
    }

    @Test
    @DisplayName("票据无效或已被消费：也是 401（不区分原因，避免泄露票据是否存在）")
    void invalidTicketShouldBeUnauthorized() {
        when(ticketService.consume("t-reused")).thenReturn(Optional.empty());

        assertFalse(interceptor.beforeHandshake(
                request("/ws/chat", "ticket=t-reused", null), response(), null, new HashMap<>()));
        assertEquals(HttpStatus.UNAUTHORIZED.value(), servletResponse.getStatus());
    }

    @Test
    @DisplayName("Origin 不在白名单：403")
    void foreignOriginShouldBeForbidden() {
        when(ticketService.consume("t-1")).thenReturn(Optional.of(ticket(7L)));

        assertFalse(interceptor.beforeHandshake(
                request("/ws/chat", "ticket=t-1", "http://evil.example.com"),
                response(), null, new HashMap<>()));
        assertEquals(HttpStatus.FORBIDDEN.value(), servletResponse.getStatus());
    }

    @Test
    @DisplayName("没有 chat:send 权限：403")
    void missingPermissionShouldBeForbidden() {
        when(ticketService.consume("t-1")).thenReturn(Optional.of(ticket(7L)));
        when(rbacSnapshotCache.get(7L))
                .thenReturn(new RbacSnapshotCache.Snapshot(List.of("STUDENT"), List.of()));

        assertFalse(interceptor.beforeHandshake(
                request("/ws/chat", "ticket=t-1", null), response(), null, new HashMap<>()));
        assertEquals(HttpStatus.FORBIDDEN.value(), servletResponse.getStatus());
    }

    @Test
    @DisplayName("账号被禁用：403（复用 AccountStatusGuard 的判定）")
    void disabledAccountShouldBeForbidden() {
        when(ticketService.consume("t-1")).thenReturn(Optional.of(ticket(7L)));
        doThrow(new BizException(403, "账号已被禁用")).when(accountStatusGuard).ensureActive(7L);

        assertFalse(interceptor.beforeHandshake(
                request("/ws/chat", "ticket=t-1", null), response(), null, new HashMap<>()));
        assertEquals(HttpStatus.FORBIDDEN.value(), servletResponse.getStatus());
    }

    @Test
    @DisplayName("票据里的通道不含 chat：403")
    void ticketWithoutChatChannelShouldBeForbidden() {
        long now = System.currentTimeMillis();
        when(ticketService.consume("t-2"))
                .thenReturn(Optional.of(new RealtimeTicket(7L, List.of("notice"), now, now + 60_000)));

        assertFalse(interceptor.beforeHandshake(
                request("/ws/chat", "ticket=t-2", null), response(), null, new HashMap<>()));
        assertEquals(HttpStatus.FORBIDDEN.value(), servletResponse.getStatus());
    }

    @Test
    @DisplayName("Origin 规则：为空放行、精确匹配、端口通配、非法拒绝")
    void originMatchingRules() {
        assertTrue(interceptor.originAllowed(null), "非浏览器客户端没有 Origin，放行");
        assertTrue(interceptor.originAllowed("http://localhost:5173"));
        assertFalse(interceptor.originAllowed("http://localhost:5174"));
        assertFalse(interceptor.originAllowed("https://kaoyan.example.com"));

        properties.setAllowedOrigins(List.of("http://localhost:*"));
        assertTrue(interceptor.originAllowed("http://localhost:8080"));
    }

    // ==================== 测试夹具 ====================

    private RealtimeTicket ticket(Long userId) {
        long now = System.currentTimeMillis();
        return new RealtimeTicket(userId, List.of("chat"), now, now + 60_000);
    }

    private RbacSnapshotCache.Snapshot snapshotWithChatPermission() {
        return new RbacSnapshotCache.Snapshot(List.of("STUDENT"), List.of("chat:send"));
    }

    private ServerHttpRequest request(String path, String queryString, String origin) {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", path);
        servletRequest.setScheme("http");
        servletRequest.setServerName("localhost");
        servletRequest.setServerPort(8080);
        if (queryString != null) {
            servletRequest.setQueryString(queryString);
        }
        if (origin != null) {
            servletRequest.addHeader("Origin", origin);
        }
        return new ServletServerHttpRequest(servletRequest);
    }

    private ServerHttpResponse response() {
        return new ServletServerHttpResponse(servletResponse);
    }
}
