package com.group4.kaoyan.modules.chat.stream;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一流式事件契约（A-1 冻结口径，B/C 只允许依赖本类，不得依赖 SSE 细节）。
 *
 * <p>外层固定为 {@code version/type/requestId/streamId/sequence/serverTime/data}：</p>
 * <ul>
 *   <li>{@code streamId}：一次回答的稳定标识，断线重连、补发都以它为准；</li>
 *   <li>{@code sequence}：从 1 开始连续递增，客户端据此判定丢包并请求补发；</li>
 *   <li>{@code requestId}：本次请求链路标识，和业务幂等键 {@code clientMessageId} 分离；</li>
 *   <li>{@code data}：业务负载，类型由 {@code type} 决定。</li>
 * </ul>
 *
 * <p>业务事件：{@code meta}(conversationId, model)、{@code references}(items)、
 * {@code delta}(content)、{@code done}(messageId, usage)、{@code error}(code, message, retryable)。</p>
 */
public record ChatStreamEvent(
        int version,
        String type,
        String requestId,
        String streamId,
        long sequence,
        String serverTime,
        Map<String, Object> data) {

    public static final int VERSION = 1;

    public static final String TYPE_META = "meta";
    public static final String TYPE_REFERENCES = "references";
    public static final String TYPE_DELTA = "delta";
    public static final String TYPE_DONE = "done";
    public static final String TYPE_ERROR = "error";

    private static final ZoneOffset SERVER_OFFSET = ZoneOffset.ofHours(8);

    public static ChatStreamEvent of(String type, String requestId, String streamId,
                                     long sequence, Map<String, Object> data) {
        return new ChatStreamEvent(
                VERSION,
                type,
                requestId,
                streamId,
                sequence,
                OffsetDateTime.now(SERVER_OFFSET).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                data == null ? Map.of() : data);
    }

    /** 便于构造可空字段的业务负载（{@link Map#of} 不接受 null）。 */
    public static Map<String, Object> payload(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return map;
    }
}
