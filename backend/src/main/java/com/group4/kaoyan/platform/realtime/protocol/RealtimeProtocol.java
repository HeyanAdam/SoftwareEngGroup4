package com.group4.kaoyan.platform.realtime.protocol;

/**
 * 连接层协议常量：版本号、消息类型、通道名。
 *
 * <p>协议是 B 和 C 之间唯一的契约，字符串只允许在这里改，避免前后端各写一份、
 * 拼错一个字母就变成"消息被静默丢弃"。C 前端照这个表接即可。</p>
 */
public final class RealtimeProtocol {

    private RealtimeProtocol() {
    }

    /** 协议版本。客户端每条消息必须带 {@code "version": 1}。 */
    public static final int VERSION = 1;

    /** 聊天通道：{@code /ws/chat} 对应 {@code chat}。 */
    public static final String CHANNEL_CHAT = "chat";

    // ==================== 客户端 → 服务端 ====================

    /** 应用层心跳。 */
    public static final String TYPE_PING = "ping";

    /** 发一条聊天消息（B-2 实现业务处理，B-1 只做协议校验）。 */
    public static final String TYPE_CHAT_SEND = "chat.send";

    // ==================== 服务端 → 客户端 ====================

    /** 心跳应答。 */
    public static final String TYPE_PONG = "pong";

    /** 协议/业务错误统一用这个类型，{@code data} 里是 {@code code + reason + retryable}。 */
    public static final String TYPE_ERROR = "error";

    /** 聊天事件类型：业务层直接复用 A 定义的 {@code ChatStreamEvent} 外层结构。 */
    public static final String TYPE_ACK = "ack";
    public static final String TYPE_META = "meta";
    public static final String TYPE_REFERENCES = "references";
    public static final String TYPE_DELTA = "delta";
    public static final String TYPE_DONE = "done";
}
