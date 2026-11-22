package com.group4.kaoyan.platform.realtime.delivery;

/**
 * 推送端口（B-1 第 10 步）。
 *
 * <p>这是连接层对外的唯一出口：B-2 的聊天事件、后续的任务进度/通知/告警都通过这个接口推，
 * 不允许业务代码直接摸 {@code WebSocketSession}。好处是以后换成 Redis 跨实例转发时，
 * 只要换实现，业务侧一行不用改。</p>
 */
public interface RealtimeDeliveryPort {

    /**
     * 推给某个用户的全部在线连接（多标签页/多设备同时收到）。
     *
     * @return 实际送达的连接数；发送失败的连接会被顺手摘掉
     */
    int pushToUser(Long userId, RealtimeOutboundMessage message);

    /**
     * 只推给某一条连接（例如"这条请求的 ack 只回给发起端"）。
     *
     * @return 是否送达
     */
    boolean pushToConnection(Long userId, String connectionId, RealtimeOutboundMessage message);
}
