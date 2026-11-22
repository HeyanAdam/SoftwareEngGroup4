package com.group4.kaoyan.platform.realtime.command;

import com.group4.kaoyan.platform.realtime.delivery.RealtimeDeliveryPort;
import com.group4.kaoyan.platform.realtime.delivery.RealtimeOutboundMessage;

import java.util.function.Consumer;

/**
 * 交给业务处理器的上下文：我是谁、在哪个连接、怎么回包。
 *
 * @param userId           已鉴权的用户主键
 * @param connectionId     当前连接 ID（只回给发起端时用）
 * @param channel          当前通道，{@code /ws/chat} 固定为 {@code chat}
 * @param delivery         推给该用户全部在线端的端口（B-2 的 ack/delta/done 用它广播）
 * @param connectionSender 只回当前连接的发送函数（错误包、pong 这类点对点回包用它）
 */
public record RealtimeCommandContext(
        Long userId,
        String connectionId,
        String channel,
        RealtimeDeliveryPort delivery,
        Consumer<RealtimeOutboundMessage> connectionSender
) {

    /** 只回当前连接。 */
    public void reply(String type, String requestId, Object data) {
        connectionSender.accept(RealtimeOutboundMessage.of(type, requestId, data));
    }
}
