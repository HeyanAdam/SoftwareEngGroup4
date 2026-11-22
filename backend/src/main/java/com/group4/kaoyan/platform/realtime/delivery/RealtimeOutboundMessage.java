package com.group4.kaoyan.platform.realtime.delivery;

/**
 * 出站消息的业务载荷（不含 version，version 由 {@code RealtimeMessageWriter} 统一补）。
 *
 * @param type       消息类型：pong / error / ack / meta / references / delta / done
 * @param requestId  对应客户端请求 ID；主动推送可为空
 * @param streamId   聊天流 ID；传输层 ack/pong/error 可为空
 * @param sequence   聊天事件序号；传输层消息可为空
 * @param serverTime 服务端事件时间；传输层消息可为空
 * @param data       事件内容
 */
public record RealtimeOutboundMessage(
        String type,
        String requestId,
        String streamId,
        Long sequence,
        String serverTime,
        Object data) {

    public static RealtimeOutboundMessage of(String type, String requestId, Object data) {
        return new RealtimeOutboundMessage(type, requestId, null, null, null, data);
    }

    public static RealtimeOutboundMessage stream(
            String type,
            String requestId,
            String streamId,
            long sequence,
            String serverTime,
            Object data) {
        return new RealtimeOutboundMessage(type, requestId, streamId, sequence, serverTime, data);
    }
}
