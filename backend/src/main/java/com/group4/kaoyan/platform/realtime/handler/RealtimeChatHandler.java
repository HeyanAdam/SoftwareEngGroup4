package com.group4.kaoyan.platform.realtime.handler;

import com.group4.kaoyan.platform.realtime.command.RealtimeCommandContext;
import com.group4.kaoyan.platform.realtime.command.RealtimeCommandHandler;
import com.group4.kaoyan.platform.realtime.config.RealtimeProperties;
import com.group4.kaoyan.platform.realtime.delivery.RealtimeDeliveryPort;
import com.group4.kaoyan.platform.realtime.delivery.RealtimeOutboundMessage;
import com.group4.kaoyan.platform.realtime.protocol.RealtimeEnvelope;
import com.group4.kaoyan.platform.realtime.protocol.RealtimeErrorCode;
import com.group4.kaoyan.platform.realtime.protocol.RealtimeMessageParser;
import com.group4.kaoyan.platform.realtime.protocol.RealtimeMessageWriter;
import com.group4.kaoyan.platform.realtime.protocol.RealtimeProtocol;
import com.group4.kaoyan.platform.realtime.protocol.RealtimeProtocolException;
import com.group4.kaoyan.platform.realtime.session.RealtimeConnection;
import com.group4.kaoyan.platform.realtime.session.RealtimeConnectionRegistry;
import com.group4.kaoyan.platform.realtime.session.RealtimeSessionAttributes;
import com.group4.kaoyan.platform.realtime.session.RealtimeSessionSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * {@code /ws/chat} 的端点（B-1 收口）：建连登记、收帧校验、心跳应答、命令分发、断开清理。
 *
 * <p>职责边界很重要：本类<b>不实现任何聊天业务</b>。收到 {@code chat.send} 时，
 * 它只在所有 {@link RealtimeCommandHandler} 里找能处理该类型的 Bean；B-1 阶段一个都没有，
 * 所以回 {@code error 4404}。B-2 加上实现类后自动生效，本类不需要改。</p>
 *
 * <p>线程安全：{@link WebSocketSession#sendMessage} 非线程安全，统一走
 * {@link RealtimeSessionSender}（内部对 session 加锁）。</p>
 */
@Slf4j
@Component
public class RealtimeChatHandler extends TextWebSocketHandler {

    private final RealtimeConnectionRegistry registry;
    private final RealtimeMessageParser parser;
    private final RealtimeMessageWriter writer;
    private final RealtimeDeliveryPort delivery;
    private final RealtimeProperties properties;
    private final List<RealtimeCommandHandler> commandHandlers;

    public RealtimeChatHandler(RealtimeConnectionRegistry registry,
                               RealtimeMessageParser parser,
                               RealtimeMessageWriter writer,
                               RealtimeDeliveryPort delivery,
                               RealtimeProperties properties,
                               ObjectProvider<RealtimeCommandHandler> commandHandlers) {
        this.registry = registry;
        this.parser = parser;
        this.writer = writer;
        this.delivery = delivery;
        this.properties = properties;
        this.commandHandlers = commandHandlers.orderedStream().toList();
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = userIdOf(session);
        List<String> channels = channelsOf(session);
        if (userId == null) {
            // 理论上到不了这里（握手已鉴权），防御性处理：没有身份就不允许占用连接
            closeQuietly(session, RealtimeErrorCode.UNAUTHORIZED);
            return;
        }
        // 容器层也卡一次消息大小，超过直接按 1009 断，不再进业务解析
        session.setTextMessageSizeLimit(properties.getMaxMessageBytes());

        if (!registry.register(userId, session, channels)) {
            closeQuietly(session, RealtimeErrorCode.TOO_MANY_CONNECTIONS);
            return;
        }
        log.info("WebSocket 已连接：用户 {}，连接 {}，当前该用户在线 {} 条",
                userId, session.getId(), registry.connectionCount(userId));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Long userId = userIdOf(session);
        RealtimeConnection connection = registry.findBySessionId(session.getId());
        if (userId == null || connection == null) {
            closeQuietly(session, RealtimeErrorCode.UNAUTHORIZED);
            return;
        }
        // 任意一帧（包括非法 JSON）都算"活着"，先刷新活动时间再校验内容
        connection.touch(System.currentTimeMillis());

        RealtimeEnvelope envelope;
        try {
            envelope = parser.parse(message.getPayload());
        } catch (RealtimeProtocolException e) {
            sendError(session, e.getErrorCode(), null, e.getMessage());
            if (e.getErrorCode() == RealtimeErrorCode.MESSAGE_TOO_LARGE) {
                closeQuietly(session, RealtimeErrorCode.MESSAGE_TOO_LARGE);
            }
            return;
        } catch (Exception e) {
            log.warn("解析 WebSocket 消息失败：{}", e.getMessage());
            sendError(session, RealtimeErrorCode.BAD_MESSAGE, null, "消息解析失败");
            return;
        }

        // 应用层心跳：回 pong 就完事，不进业务
        if (RealtimeProtocol.TYPE_PING.equals(envelope.type())) {
            send(session, writer.pong(envelope.requestId()));
            return;
        }

        RealtimeCommandHandler handler = findHandler(envelope.type());
        if (handler == null) {
            sendError(session, RealtimeErrorCode.UNSUPPORTED_TYPE, envelope.requestId(),
                    "暂不支持的消息类型：" + envelope.type());
            return;
        }
        try {
            handler.handle(contextOf(userId, connection, session), envelope);
        } catch (RealtimeProtocolException e) {
            sendError(session, e.getErrorCode(), envelope.requestId(), e.getMessage());
        } catch (Exception e) {
            log.error("处理 WebSocket 消息 {} 失败", envelope.type(), e);
            sendError(session, RealtimeErrorCode.INTERNAL_ERROR, envelope.requestId(), null);
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        // 协议只走文本帧；二进制一律拒绝，避免有人拿它绕开 JSON 校验
        sendError(session, RealtimeErrorCode.BAD_MESSAGE, null, "只支持文本消息");
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.debug("WebSocket 传输异常，清理连接 {}：{}", session.getId(), exception.getMessage());
        registry.unregisterBySessionId(session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = userIdOf(session);
        if (userId != null) {
            registry.unregister(userId, session.getId());
            log.info("WebSocket 已断开：用户 {}，连接 {}，关闭码 {}，该用户剩余 {} 条",
                    userId, session.getId(), status.getCode(), registry.connectionCount(userId));
        } else {
            registry.unregisterBySessionId(session.getId());
        }
    }

    // ==================== 内部工具 ====================

    private RealtimeCommandHandler findHandler(String type) {
        for (RealtimeCommandHandler handler : commandHandlers) {
            if (handler.supportedTypes().contains(type)) {
                return handler;
            }
        }
        return null;
    }

    private RealtimeCommandContext contextOf(Long userId, RealtimeConnection connection, WebSocketSession session) {
        return new RealtimeCommandContext(
                userId,
                connection.sessionId(),
                RealtimeProtocol.CHANNEL_CHAT,
                delivery,
                outbound -> send(session, writer.toJson(
                        outbound.type(), outbound.requestId(), outbound.data())));
    }

    private void send(WebSocketSession session, String payload) {
        if (!RealtimeSessionSender.send(session, payload)) {
            registry.unregisterBySessionId(session.getId());
        }
    }

    private void sendError(WebSocketSession session, RealtimeErrorCode code, String requestId, String detail) {
        send(session, writer.error(code, requestId, detail));
    }

    private void closeQuietly(WebSocketSession session, RealtimeErrorCode code) {
        registry.unregisterBySessionId(session.getId());
        try {
            if (session.isOpen()) {
                session.close(new CloseStatus(code.getCode(), code.name()));
            }
        } catch (IOException | IllegalStateException e) {
            log.debug("关闭连接 {} 失败：{}", session.getId(), e.getMessage());
        }
    }

    private Long userIdOf(WebSocketSession session) {
        Map<String, Object> attributes = session.getAttributes();
        Object value = attributes == null ? null : attributes.get(RealtimeSessionAttributes.USER_ID);
        return value instanceof Long id ? id : null;
    }

    @SuppressWarnings("unchecked")
    private List<String> channelsOf(WebSocketSession session) {
        Map<String, Object> attributes = session.getAttributes();
        Object value = attributes == null ? null : attributes.get(RealtimeSessionAttributes.CHANNELS);
        return value instanceof List<?> list ? (List<String>) list : List.of();
    }
}
