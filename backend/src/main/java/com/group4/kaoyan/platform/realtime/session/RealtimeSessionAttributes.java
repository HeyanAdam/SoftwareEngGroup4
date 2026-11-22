package com.group4.kaoyan.platform.realtime.session;

/**
 * 握手成功后写进 {@code WebSocketSession} attributes 的键名。
 *
 * <p>握手拦截器和消息处理器之间只通过 attributes 传值，不额外加 ThreadLocal，
 * 这样 Spring 每来一帧消息都能从会话本身拿到身份，不依赖线程上下文。</p>
 */
public final class RealtimeSessionAttributes {

    private RealtimeSessionAttributes() {
    }

    /** 已鉴权的用户主键（Long）。 */
    public static final String USER_ID = "realtime.userId";

    /** 票据里允许的通道（List&lt;String&gt;）。 */
    public static final String CHANNELS = "realtime.channels";

    /** 建连时间戳（Long）。 */
    public static final String CONNECTED_AT = "realtime.connectedAt";
}
