package com.group4.kaoyan.platform.realtime.protocol;

import lombok.Getter;

/**
 * WebSocket 错误码与关闭原因（B-1 第 9 步）。
 *
 * <p>统一取 4000-4999 私有区间，和 HTTP 业务码（{@code ErrorCode}，10000+）分开，
 * 前端看到 {@code data.code} 就知道是连接层的问题还是业务问题，不用猜。</p>
 *
 * <p>为什么不塞进全局 {@code ErrorCode}：那张表是 HTTP 接口契约的一部分，
 * WebSocket 关闭码是另一套语义，混在一起以后谁都看不懂。</p>
 */
@Getter
public enum RealtimeErrorCode {

    /** JSON 坏了、字段缺失、类型不对。 */
    BAD_MESSAGE(4400, "消息格式不合法"),

    /** 票据缺失、已过期、已被消费，或握手时账号已失效。 */
    UNAUTHORIZED(4401, "票据无效或已过期"),

    /** 登录了但没有 chat:send，或没有该通道的访问范围。 */
    FORBIDDEN(4403, "没有访问该通道的权限"),

    /** 协议里没定义的消息类型。 */
    UNSUPPORTED_TYPE(4404, "暂不支持的消息类型"),

    /** version 不是 1。 */
    UNSUPPORTED_VERSION(4426, "协议版本不支持"),

    /** 单条消息超过 32 KB。 */
    MESSAGE_TOO_LARGE(4413, "消息体过大"),

    /** 同一用户连接数超过上限。 */
    TOO_MANY_CONNECTIONS(4429, "在线连接数超过上限"),

    /** 发送太频繁（B-2 的限流会用到）。 */
    RATE_LIMITED(4429, "请求过于频繁"),

    /** 90 秒没有任何消息，服务端主动断开。 */
    IDLE_TIMEOUT(4408, "连接空闲超时"),

    /** 兜底：handler 里没预料到的异常。 */
    INTERNAL_ERROR(4500, "服务端内部错误");

    /** WebSocket 关闭码 / 错误响应里的业务码。 */
    private final int code;

    /** 给前端看的中文原因，不要带内部细节。 */
    private final String reason;

    RealtimeErrorCode(int code, String reason) {
        this.code = code;
        this.reason = reason;
    }
}
