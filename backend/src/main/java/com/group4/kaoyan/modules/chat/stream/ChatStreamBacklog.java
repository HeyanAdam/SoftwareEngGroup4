package com.group4.kaoyan.modules.chat.stream;

import java.util.List;

/**
 * 断线恢复结果：服务端状态 + 最后序号 + 从 {@code lastSequence} 之后的所有事件。
 */
public record ChatStreamBacklog(
        String streamId,
        String status,
        long lastSequence,
        Long assistantMessageId,
        List<ChatStreamEvent> events) {

    public static final String STATUS_STREAMING = "STREAMING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";
}
