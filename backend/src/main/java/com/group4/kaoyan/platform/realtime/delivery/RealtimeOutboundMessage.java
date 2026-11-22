package com.group4.kaoyan.platform.realtime.delivery;

/**
 * 出站消息的业务载荷（不含 version，version 由 {@code RealtimeMessageWriter} 统一补）。
 *
 * @param type      消息类型：pong / error / ack / meta / references / delta / done
 * @param requestId 对应客户端请求 ID；主动推送可为空
 * @param data      事件内容，B-2 直接放 A 定义的 {@code ChatStreamEvent}
 */
public record RealtimeOutboundMessage(String type, String requestId, Object data) {

    public static RealtimeOutboundMessage of(String type, String requestId, Object data) {
        return new RealtimeOutboundMessage(type, requestId, data);
    }
}
