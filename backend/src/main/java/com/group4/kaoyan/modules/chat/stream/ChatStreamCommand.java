package com.group4.kaoyan.modules.chat.stream;

/**
 * 流式问答的业务入参（不含任何 HTTP/SSE 对象，后台任务可以直接构造）。
 *
 * <p>{@code requestId} 是链路追踪号；{@code clientMessageId} 是幂等键，
 * 两者不可混用（A-2 的唯一约束建在 {@code clientMessageId} 上）。</p>
 */
public record ChatStreamCommand(
        Long userId,
        Long conversationId,
        Long kbId,
        String question,
        String requestId,
        String clientMessageId) {

    public ChatStreamCommand {
        question = question == null ? null : question.trim();
    }
}
